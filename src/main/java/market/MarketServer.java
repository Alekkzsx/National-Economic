package market;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Servidor TCP concorrente que publica cotações definidas na inicialização.
 *
 * <p>O protocolo aceita somente {@code GET_QUOTES}; conexões de clientes são
 * processadas em um pool limitado e expiram após um período sem atividade.</p>
 */
public final class MarketServer implements AutoCloseable {
    /** Tempo máximo sem atividade de cada cliente conectado. */
    private static final int CLIENT_TIMEOUT_MILLIS = 5_000;
    /** Número limitado de conexões tratadas simultaneamente. */
    private static final int WORKER_COUNT = Math.max(2, Math.min(16, Runtime.getRuntime().availableProcessors()));
    /** Limite da fila de conexões aguardando processamento. */
    private static final int MAX_QUEUED_CLIENTS = 64;

    /** Socket de escuta mantido durante a vida do servidor. */
    private final ServerSocket serverSocket;
    /** Snapshot imutável das cotações que serão respondidas. */
    private final Map<String, BigDecimal> quotes;
    /** Pool limitado para evitar criar threads sem limite por conexão. */
    private final ThreadPoolExecutor workers;
    /** Conexões ativas, fechadas durante o encerramento do servidor. */
    private final Set<Socket> clientSockets = ConcurrentHashMap.newKeySet();
    /** Sinaliza tanto o laço de aceitação quanto os trabalhadores para encerrar. */
    private volatile boolean closed;
    /** Thread dedicada à aceitação de novas conexões. */
    private Thread acceptThread;

    /**
     * Cria o servidor na porta informada; use zero para solicitar uma porta livre do sistema.
     * O mapa deve conter exatamente SELICRA, CDIR, USD e EUR.
     */
    public MarketServer(int port, Map<String, BigDecimal> quotes) throws IOException {
        if (port < 0 || port > 65_535) {
            throw new IllegalArgumentException("A porta deve estar entre 0 e 65535.");
        }
        this.quotes = QuoteProtocol.validateQuotes(quotes);
        serverSocket = new ServerSocket(port);
        workers = new ThreadPoolExecutor(
                WORKER_COUNT, WORKER_COUNT, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(MAX_QUEUED_CLIENTS),
                runnable -> {
                    Thread thread = new Thread(runnable, "market-client");
                    thread.setDaemon(false);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy());
    }

    /** Sobrecarga que recebe cada taxa como argumento individual. */
    public MarketServer(int port, BigDecimal selicra, BigDecimal cdir,
                        BigDecimal usd, BigDecimal eur) throws IOException {
        this(port, quoteMap(selicra, cdir, usd, eur));
    }

    /** Inicia o laço de aceitação em uma thread dedicada; só pode ser chamado uma vez. */
    public synchronized void start() {
        if (closed) {
            throw new IllegalStateException("O servidor já foi fechado.");
        }
        if (acceptThread != null) {
            throw new IllegalStateException("O servidor já foi iniciado.");
        }
        acceptThread = new Thread(this::acceptClients, "market-accept");
        acceptThread.setDaemon(false);
        acceptThread.start();
    }

    /** Retorna a porta efetiva, inclusive quando o servidor foi criado com porta zero. */
    public int getPort() {
        return serverSocket.getLocalPort();
    }

    /** Aceita conexões até o fechamento e delega cada cliente ao pool. */
    private void acceptClients() {
        while (!closed) {
            try {
                Socket client = serverSocket.accept();
                clientSockets.add(client);
                try {
                    workers.execute(() -> handleClient(client));
                } catch (RuntimeException rejected) {
                    // Pool cheio ou encerrado: fecha o socket em vez de acumular conexões.
                    clientSockets.remove(client);
                    closeQuietly(client);
                }
            } catch (IOException exception) {
                if (!closed) {
                    try {
                        close();
                    } catch (IOException ignored) {
                        // O laço de aceitação já está encerrando após a falha do socket servidor.
                    }
                }
                return;
            }
        }
    }

    /** Lê uma única solicitação, responde pelo protocolo e fecha a conexão. */
    private void handleClient(Socket client) {
        try (client) {
            client.setSoTimeout(CLIENT_TIMEOUT_MILLIS);
            String request = QuoteProtocol.readLine(client.getInputStream());
            // Nunca encaminha comandos recebidos para execução genérica no servidor.
            String response = "GET_QUOTES".equals(request)
                    ? QuoteProtocol.formatQuotes(quotes)
                    : "ERR|INVALID_REQUEST";
            BufferedOutputStream output = new BufferedOutputStream(client.getOutputStream());
            output.write((response + "\n").getBytes(StandardCharsets.US_ASCII));
            output.flush();
        } catch (IOException ignored) {
            try {
                client.getOutputStream().write("ERR|INVALID_REQUEST\n".getBytes(StandardCharsets.US_ASCII));
                client.getOutputStream().flush();
            } catch (IOException ignoredAgain) {
                // A conexão pode já ter sido fechada pelo cliente ou pelo encerramento do servidor.
            }
        } finally {
            clientSockets.remove(client);
        }
    }

    /** Fecha uma conexão durante o encerramento sem interromper os demais clientes. */
    private static void closeQuietly(Socket socket) {
        try {
            socket.close();
        } catch (IOException ignored) {
            // A conexão já pode ter sido encerrada pelo cliente.
        }
    }

    /** Constrói o mapa canônico de cotações na mesma ordem do protocolo. */
    private static Map<String, BigDecimal> quoteMap(BigDecimal selicra, BigDecimal cdir,
                                                     BigDecimal usd, BigDecimal eur) {
        Map<String, BigDecimal> values = new LinkedHashMap<>();
        values.put("SELICRA", selicra);
        values.put("CDIR", cdir);
        values.put("USD", usd);
        values.put("EUR", eur);
        return values;
    }

    /**
     * Fecha sockets e aguarda o encerramento do aceitador e do pool de clientes.
     * A operação é idempotente; falhas de encerramento são propagadas ao chamador.
     */
    @Override
    public synchronized void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        IOException closeFailure = null;
        try {
            serverSocket.close();
        } catch (IOException exception) {
            closeFailure = exception;
        }
        for (Socket client : clientSockets) {
            closeQuietly(client);
        }
        workers.shutdownNow();

        Thread acceptor = acceptThread;
        if (acceptor != null && acceptor != Thread.currentThread()) {
            try {
                acceptor.join(CLIENT_TIMEOUT_MILLIS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                if (closeFailure == null) {
                    closeFailure = new IOException("Interrompido ao encerrar o servidor.", exception);
                }
            }
        }
        try {
            if (!workers.awaitTermination(CLIENT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                workers.shutdownNow();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            if (closeFailure == null) {
                closeFailure = new IOException("Interrompido ao encerrar as threads do servidor.", exception);
            }
        }
        if (closeFailure != null) {
            throw closeFailure;
        }
    }
}

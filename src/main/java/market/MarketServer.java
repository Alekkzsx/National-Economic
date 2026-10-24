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
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Servidor TCP de cotações estáticas, com capacidade de atendimento limitada. */
public final class MarketServer implements AutoCloseable {
    private static final int CLIENT_TIMEOUT_MILLIS = 5_000;
    private static final int WORKER_COUNT =
            Math.max(2, Math.min(16, Runtime.getRuntime().availableProcessors()));
    private static final int MAX_QUEUED_CLIENTS = 64;

    private final ServerSocket serverSocket;
    private final Map<String, BigDecimal> quotes;
    private final ThreadPoolExecutor workers;
    private final Set<Socket> clientSockets = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private volatile boolean closed;
    private Thread acceptThread;

    /**
     * Cria o servidor. A porta zero solicita ao sistema uma porta TCP disponível.
     *
     * @param port porta entre zero e 65535
     * @param quotes mapa com SELICRA, CDIR, USD e EUR
     */
    public MarketServer(int port, Map<String, BigDecimal> quotes) throws IOException {
        if (port < 0 || port > 65_535) {
            throw new IllegalArgumentException("A porta deve estar entre 0 e 65535.");
        }
        this.quotes = QuoteProtocol.validateQuotes(quotes);
        serverSocket = new ServerSocket(port);
        workers = new ThreadPoolExecutor(
                WORKER_COUNT,
                WORKER_COUNT,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(MAX_QUEUED_CLIENTS),
                task -> new Thread(task, "market-client"),
                new ThreadPoolExecutor.AbortPolicy());
    }

    /** Cria o servidor a partir das quatro taxas, na ordem definida pelo protocolo. */
    public MarketServer(int port, BigDecimal selicra, BigDecimal cdir,
                        BigDecimal usd, BigDecimal eur) throws IOException {
        this(port, quoteMap(selicra, cdir, usd, eur));
    }

    /** Inicia o único laço de aceitação deste servidor. */
    public synchronized void start() {
        if (closed) {
            throw new IllegalStateException("O servidor já foi fechado.");
        }
        if (acceptThread != null) {
            throw new IllegalStateException("O servidor já foi iniciado.");
        }
        acceptThread = new Thread(this::acceptClients, "market-accept");
        acceptThread.start();
    }

    /** Retorna a porta efetiva, útil quando o servidor foi criado com porta zero. */
    public int getPort() {
        return serverSocket.getLocalPort();
    }

    /** Aceita conexões e entrega cada uma ao pool limitado de trabalhadores. */
    private void acceptClients() {
        while (!closed) {
            try {
                Socket client = serverSocket.accept();
                clientSockets.add(client);
                try {
                    workers.execute(() -> handleClient(client));
                } catch (java.util.concurrent.RejectedExecutionException exception) {
                    // Sobrecarga: não manter conexões abertas esperando por uma vaga.
                    clientSockets.remove(client);
                    closeQuietly(client);
                }
            } catch (IOException exception) {
                if (!closed) {
                    System.err.println("Falha ao aceitar conexão: " + exception.getMessage());
                    closeAfterAcceptFailure();
                }
                return;
            }
        }
    }

    /** Processa exatamente uma linha e encerra a conexão, inclusive em caso de erro. */
    private void handleClient(Socket client) {
        try (client) {
            client.setSoTimeout(CLIENT_TIMEOUT_MILLIS);
            String request;
            try {
                request = QuoteProtocol.readLine(client.getInputStream());
            } catch (IOException exception) {
                writeResponse(client, QuoteProtocol.INVALID_REQUEST);
                return;
            }

            String response = QuoteProtocol.REQUEST.equals(request)
                    ? QuoteProtocol.formatQuotes(quotes)
                    : QuoteProtocol.INVALID_REQUEST;
            writeResponse(client, response);
        } catch (IOException exception) {
            // A conexão pode ter sido encerrada pelo cliente ou pelo fechamento do servidor.
        } finally {
            clientSockets.remove(client);
        }
    }

    private static void writeResponse(Socket client, String response) throws IOException {
        BufferedOutputStream output = new BufferedOutputStream(client.getOutputStream());
        output.write((response + "\n").getBytes(StandardCharsets.US_ASCII));
        output.flush();
    }

    /** Encerra os recursos quando o socket de escuta falha fora de um fechamento normal. */
    private void closeAfterAcceptFailure() {
        closed = true;
        closeQuietly(serverSocket);
        for (Socket client : clientSockets) {
            closeQuietly(client);
        }
        workers.shutdownNow();
    }

    private static void closeQuietly(Socket socket) {
        try {
            socket.close();
        } catch (IOException ignored) {
            // O socket já pode ter sido fechado pela outra ponta.
        }
    }

    private static void closeQuietly(ServerSocket socket) {
        try {
            socket.close();
        } catch (IOException ignored) {
            // O socket de escuta já pode estar fechado.
        }
    }

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
     * Fecha conexões e aguarda o encerramento das threads, propagando interrupções e falhas.
     * Chamadas repetidas são seguras.
     */
    @Override
    public void close() throws IOException {
        synchronized (this) {
            if (closed) {
                return;
            }
            closed = true;
        }

        IOException failure = null;
        try {
            serverSocket.close();
        } catch (IOException exception) {
            failure = exception;
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
                failure = appendFailure(failure,
                        new IOException("Interrompido ao encerrar o servidor.", exception));
            }
        }
        try {
            if (!workers.awaitTermination(CLIENT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                failure = appendFailure(failure,
                        new IOException("O pool de clientes não encerrou dentro do prazo."));
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            failure = appendFailure(failure,
                    new IOException("Interrompido ao encerrar as threads do servidor.", exception));
        }
        if (failure != null) {
            throw failure;
        }
    }

    private static IOException appendFailure(IOException first, IOException next) {
        if (first == null) {
            return next;
        }
        first.addSuppressed(next);
        return first;
    }
}

package mercado;

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
public final class ServidorCotacoes implements AutoCloseable {
    private static final int TEMPO_LIMITE_CLIENTE_MILISSEGUNDOS = 5_000;
    private static final int QUANTIDADE_TRABALHADORES =
            Math.max(2, Math.min(16, Runtime.getRuntime().availableProcessors()));
    private static final int MAXIMO_CLIENTES_NA_FILA = 64;

    private final ServerSocket socketServidor;
    private final Map<String, BigDecimal> cotacoes;
    private final ThreadPoolExecutor trabalhadores;
    private final Set<Socket> socketsClientes = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private volatile boolean encerrado;
    private Thread threadAceitacao;

    /**
     * Cria o servidor. A porta zero solicita ao sistema uma porta TCP disponível.
     *
     * @param porta porta entre zero e 65535
     * @param cotacoes mapa com SELICRA, CDIR, USD e EUR
     */
    public ServidorCotacoes(int porta, Map<String, BigDecimal> cotacoes) throws IOException {
        if (porta < 0 || porta > 65_535) {
            throw new IllegalArgumentException("A porta deve estar entre 0 e 65535.");
        }
        this.cotacoes = ProtocoloCotacoes.validateQuotes(cotacoes);
        socketServidor = new ServerSocket(porta);
        trabalhadores = new ThreadPoolExecutor(
                QUANTIDADE_TRABALHADORES,
                QUANTIDADE_TRABALHADORES,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(MAXIMO_CLIENTES_NA_FILA),
                task -> new Thread(task, "mercado-cliente"),
                new ThreadPoolExecutor.AbortPolicy());
    }

    /** Cria o servidor a partir das quatro taxas, na ordem definida pelo protocolo. */
    public ServidorCotacoes(int porta, BigDecimal selicra, BigDecimal cdir,
                        BigDecimal usd, BigDecimal eur) throws IOException {
        this(porta, mapaCotacoes(selicra, cdir, usd, eur));
    }

    /** Inicia o único laço de aceitação deste servidor. */
    public synchronized void start() {
        if (encerrado) {
            throw new IllegalStateException("O servidor já foi fechado.");
        }
        if (threadAceitacao != null) {
            throw new IllegalStateException("O servidor já foi iniciado.");
        }
        threadAceitacao = new Thread(this::aceitarClientes, "mercado-accept");
        threadAceitacao.start();
    }

    /** Retorna a porta efetiva, útil quando o servidor foi criado com porta zero. */
    public int getPorta() {
        return socketServidor.getLocalPort();
    }

    /** Aceita conexões e entrega cada uma ao pool limitado de trabalhadores. */
    private void aceitarClientes() {
        while (!encerrado) {
            try {
                Socket cliente = socketServidor.accept();
                socketsClientes.add(cliente);
                try {
                    trabalhadores.execute(() -> processarCliente(cliente));
                } catch (java.util.concurrent.RejectedExecutionException excecao) {
                    // Sobrecarga: não manter conexões abertas esperando por uma vaga.
                    socketsClientes.remove(cliente);
                    encerrarSilenciosamente(cliente);
                }
            } catch (IOException excecao) {
                if (!encerrado) {
                    System.err.println("Falha ao aceitar conexão: " + excecao.getMessage());
                    encerrarAposFalhaDeAceitacao();
                }
                return;
            }
        }
    }

    /** Processa exatamente uma linha e encerra a conexão, inclusive em caso de erro. */
    private void processarCliente(Socket cliente) {
        try (cliente) {
            cliente.setSoTimeout(TEMPO_LIMITE_CLIENTE_MILISSEGUNDOS);
            String requisicao;
            try {
                requisicao = ProtocoloCotacoes.lerLinha(cliente.getInputStream());
            } catch (IOException excecao) {
                gravarResposta(cliente, ProtocoloCotacoes.INVALID_REQUEST);
                return;
            }

            String resposta = ProtocoloCotacoes.REQUEST.equals(requisicao)
                    ? ProtocoloCotacoes.formatQuotes(cotacoes)
                    : ProtocoloCotacoes.INVALID_REQUEST;
            gravarResposta(cliente, resposta);
        } catch (IOException excecao) {
            // A conexão pode ter sido encerrada pelo cliente ou pelo fechamento do servidor.
        } finally {
            socketsClientes.remove(cliente);
        }
    }

    private static void gravarResposta(Socket cliente, String resposta) throws IOException {
        BufferedOutputStream output = new BufferedOutputStream(cliente.getOutputStream());
        output.write((resposta + "\n").getBytes(StandardCharsets.US_ASCII));
        output.flush();
    }

    /** Encerra os recursos quando o socket de escuta falha fora de um fechamento normal. */
    private void encerrarAposFalhaDeAceitacao() {
        encerrado = true;
        encerrarSilenciosamente(socketServidor);
        for (Socket cliente : socketsClientes) {
            encerrarSilenciosamente(cliente);
        }
        trabalhadores.shutdownNow();
    }

    private static void encerrarSilenciosamente(Socket socket) {
        try {
            socket.close();
        } catch (IOException ignored) {
            // O socket já pode ter sido fechado pela outra ponta.
        }
    }

    private static void encerrarSilenciosamente(ServerSocket socket) {
        try {
            socket.close();
        } catch (IOException ignored) {
            // O socket de escuta já pode estar fechado.
        }
    }

    private static Map<String, BigDecimal> mapaCotacoes(BigDecimal selicra, BigDecimal cdir,
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
            if (encerrado) {
                return;
            }
            encerrado = true;
        }

        IOException falha = null;
        try {
            socketServidor.close();
        } catch (IOException excecao) {
            falha = excecao;
        }
        for (Socket cliente : socketsClientes) {
            encerrarSilenciosamente(cliente);
        }
        trabalhadores.shutdownNow();

        Thread acceptor = threadAceitacao;
        if (acceptor != null && acceptor != Thread.currentThread()) {
            try {
                acceptor.join(TEMPO_LIMITE_CLIENTE_MILISSEGUNDOS);
            } catch (InterruptedException excecao) {
                Thread.currentThread().interrupt();
                falha = acrescentarFalha(falha,
                        new IOException("Interrompido ao encerrar o servidor.", excecao));
            }
        }
        try {
            if (!trabalhadores.awaitTermination(TEMPO_LIMITE_CLIENTE_MILISSEGUNDOS, TimeUnit.MILLISECONDS)) {
                falha = acrescentarFalha(falha,
                        new IOException("O pool de clientes não encerrou dentro do prazo."));
            }
        } catch (InterruptedException excecao) {
            Thread.currentThread().interrupt();
            falha = acrescentarFalha(falha,
                    new IOException("Interrompido ao encerrar as threads do servidor.", excecao));
        }
        if (falha != null) {
            throw falha;
        }
    }

    private static IOException acrescentarFalha(IOException first, IOException next) {
        if (first == null) {
            return next;
        }
        first.addSuppressed(next);
        return first;
    }
}

package mercado.servidor;

import mercado.util.ProtocoloCotacoes;

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
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Item 9 — Servidor TCP multithread de cotações ({@code SELICRA}, {@code CDIR}, {@code USD} e {@code EUR}).
 *
 * <p>Protocolo ASCII por linha ({@code \n}, limite 512 bytes): recebe {@code GET_QUOTES\n} e responde
 * {@code OK|SELICRA=...|CDIR=...|USD=...|EUR=...\n}; entradas inválidas recebem {@code ERR|INVALID_REQUEST\n}.</p>
 *
 * <p>Modelo de concorrência: uma thread {@code mercado-accept} aceita sockets e os despacha para um
 * pool fixo limitado por {@link ArrayBlockingQueue} com {@link ThreadPoolExecutor.AbortPolicy}. A flag
 * {@code volatile encerrado} e o conjunto concorrente {@code socketsClientes} permitem fechamento
 * imediato de todas as conexões ativas em {@link #close()}.</p>
 */
public final class ServidorCotacoes implements AutoCloseable {

    private static final int TEMPO_LIMITE_MS = 5_000;
    private static final int QUANTIDADE_TRABALHADORES =
            Math.max(2, Math.min(16, Runtime.getRuntime().availableProcessors()));
    private static final int MAXIMO_FILA = 64;

    private final ServerSocket socketServidor;
    private final Map<String, BigDecimal> cotacoes;
    private final ThreadPoolExecutor trabalhadores;
    private final Set<Socket> socketsClientes = ConcurrentHashMap.newKeySet();

    private volatile boolean encerrado;
    private Thread threadAceitacao;

    public ServidorCotacoes(int porta, Map<String, BigDecimal> cotacoes) throws IOException {
        if (porta < 0 || porta > 65_535) {
            throw new IllegalArgumentException("A porta deve estar entre 0 e 65535.");
        }
        this.cotacoes = ProtocoloCotacoes.validarCotacoes(cotacoes);
        this.socketServidor = new ServerSocket(porta);
        this.trabalhadores = criarPoolTrabalhadores();
    }

    private static ThreadPoolExecutor criarPoolTrabalhadores() {
        return new ThreadPoolExecutor(
                QUANTIDADE_TRABALHADORES,
                QUANTIDADE_TRABALHADORES,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(MAXIMO_FILA),
                task -> new Thread(task, "mercado-cliente"),
                new ThreadPoolExecutor.AbortPolicy());
    }

    public ServidorCotacoes(int porta, BigDecimal selicra, BigDecimal cdir,
                            BigDecimal usd, BigDecimal eur) throws IOException {
        this(porta, criarMapa(selicra, cdir, usd, eur));
    }

    /** Inicia a thread de aceitação de conexões TCP. */
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

    public int getPorta() {
        return socketServidor.getLocalPort();
    }

    private void aceitarClientes() {
        while (!encerrado) {
            try {
                Socket cliente = socketServidor.accept();
                socketsClientes.add(cliente);
                try {
                    trabalhadores.execute(() -> processarCliente(cliente));
                } catch (RejectedExecutionException excecao) {
                    socketsClientes.remove(cliente);
                    ProtocoloCotacoes.encerrarSilenciosamente(cliente);
                }
            } catch (IOException excecao) {
                if (!encerrado) {
                    System.err.println("Falha ao aceitar conexão: " + excecao.getMessage());
                    encerrarAposFalha();
                }
                return;
            }
        }
    }

    private void processarCliente(Socket cliente) {
        try (cliente) {
            cliente.setSoTimeout(TEMPO_LIMITE_MS);
            String requisicao;
            try {
                requisicao = ProtocoloCotacoes.lerLinha(cliente.getInputStream());
            } catch (IOException excecao) {
                gravarResposta(cliente, ProtocoloCotacoes.REQUISICAO_INVALIDA);
                return;
            }
            String resposta = ProtocoloCotacoes.REQUISICAO.equals(requisicao)
                    ? ProtocoloCotacoes.formatarCotacoes(cotacoes)
                    : ProtocoloCotacoes.REQUISICAO_INVALIDA;
            gravarResposta(cliente, resposta);
        } catch (IOException ignored) {
            // Conexão encerrada pelo cliente ou durante fechamento do servidor
        } finally {
            socketsClientes.remove(cliente);
        }
    }

    private static void gravarResposta(Socket cliente, String resposta) throws IOException {
        BufferedOutputStream saida = new BufferedOutputStream(cliente.getOutputStream());
        saida.write((resposta + "\n").getBytes(StandardCharsets.US_ASCII));
        saida.flush();
    }

    private void encerrarAposFalha() {
        encerrado = true;
        ProtocoloCotacoes.encerrarSilenciosamente(socketServidor);
        socketsClientes.forEach(ProtocoloCotacoes::encerrarSilenciosamente);
        trabalhadores.shutdownNow();
    }

    private static Map<String, BigDecimal> criarMapa(BigDecimal selicra, BigDecimal cdir,
                                                     BigDecimal usd, BigDecimal eur) {
        Map<String, BigDecimal> mapa = new LinkedHashMap<>();
        mapa.put("SELICRA", selicra);
        mapa.put("CDIR", cdir);
        mapa.put("USD", usd);
        mapa.put("EUR", eur);
        return mapa;
    }

    /** Encerra de forma idempotente o socket servidor, conexões ativas e threads de atendimento. */
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
        socketsClientes.forEach(ProtocoloCotacoes::encerrarSilenciosamente);
        trabalhadores.shutdownNow();

        falha = aguardarThreadAceitacao(falha);
        falha = aguardarPoolTrabalhadores(falha);
        if (falha != null) {
            throw falha;
        }
    }

    private IOException aguardarThreadAceitacao(IOException falhaAnterior) {
        Thread acceptor = threadAceitacao;
        if (acceptor == null || acceptor == Thread.currentThread()) {
            return falhaAnterior;
        }
        try {
            acceptor.join(TEMPO_LIMITE_MS);
            return falhaAnterior;
        } catch (InterruptedException excecao) {
            Thread.currentThread().interrupt();
            return acrescentarFalha(falhaAnterior,
                    new IOException("Interrompido ao encerrar o servidor.", excecao));
        }
    }

    private IOException aguardarPoolTrabalhadores(IOException falhaAnterior) {
        try {
            if (!trabalhadores.awaitTermination(TEMPO_LIMITE_MS, TimeUnit.MILLISECONDS)) {
                return acrescentarFalha(falhaAnterior,
                        new IOException("O pool de clientes não encerrou dentro do prazo."));
            }
            return falhaAnterior;
        } catch (InterruptedException excecao) {
            Thread.currentThread().interrupt();
            return acrescentarFalha(falhaAnterior,
                    new IOException("Interrompido ao encerrar as threads do servidor.", excecao));
        }
    }

    private static IOException acrescentarFalha(IOException primeira, IOException proxima) {
        if (primeira == null) {
            return proxima;
        }
        primeira.addSuppressed(proxima);
        return primeira;
    }
}

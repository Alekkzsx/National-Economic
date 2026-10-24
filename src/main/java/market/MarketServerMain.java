package market;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;

/**
 * Inicializador de terminal do servidor de cotações.
 * Os valores default são placeholders explícitos: não representam cotação ao vivo.
 */
public final class MarketServerMain {
    /** Porta TCP usada quando nenhum argumento é informado. */
    private static final int DEFAULT_PORT = 5050;
    /** Default de configuração da taxa SELICRA. */
    private static final BigDecimal DEFAULT_SELICRA = new BigDecimal("0.00");
    /** Default de configuração da taxa CDIR. */
    private static final BigDecimal DEFAULT_CDIR = new BigDecimal("0.00");
    /** Default manual para o dólar; não há consulta externa de mercado. */
    private static final BigDecimal DEFAULT_USD = new BigDecimal("0.00");
    /** Default manual para o euro; não há consulta externa de mercado. */
    private static final BigDecimal DEFAULT_EUR = new BigDecimal("0.00");

    /** Impede a instanciação: esta classe fornece somente o método main. */
    private MarketServerMain() {}

    /**
     * Uso: MarketServerMain [porta [SELICRA CDIR USD EUR]].
     * Os defaults são valores explícitos de configuração, não cotações em tempo real.
     * USD e EUR não têm integração de mercado: informe seus valores por argumentos.
     *
     * <p>O processo permanece ativo até receber um sinal de encerramento; o hook
     * fecha o socket e o pool de threads antes da saída.</p>
     */
    public static void main(String[] args) throws IOException, InterruptedException {
        if (args.length != 0 && args.length != 1 && args.length != 5) {
            throw new IllegalArgumentException(
                    "Uso: MarketServerMain [porta [SELICRA CDIR USD EUR]]");
        }

        int port = args.length == 0 ? DEFAULT_PORT : Integer.parseInt(args[0]);
        BigDecimal selicra = args.length == 5 ? new BigDecimal(args[1]) : DEFAULT_SELICRA;
        BigDecimal cdir = args.length == 5 ? new BigDecimal(args[2]) : DEFAULT_CDIR;
        BigDecimal usd = args.length == 5 ? new BigDecimal(args[3]) : DEFAULT_USD;
        BigDecimal eur = args.length == 5 ? new BigDecimal(args[4]) : DEFAULT_EUR;

        MarketServer server = new MarketServer(port, selicra, cdir, usd, eur);
        server.start();
        System.out.println("Servidor de cotações ativo na porta " + server.getPort() + ".");
        System.out.println("Configuração estática: SELICRA=" + selicra.toPlainString()
                + ", CDIR=" + cdir.toPlainString()
                + ", USD=" + usd.toPlainString()
                + ", EUR=" + eur.toPlainString() + ".");
        if (args.length < 5) {
            System.out.println("Defaults em 0.00; USD/EUR são configuração manual, sem dados ao vivo.");
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                server.close();
            } catch (IOException exception) {
                System.err.println("Falha ao encerrar o servidor: " + exception.getMessage());
            }
        }, "market-shutdown"));
        try {
            new CountDownLatch(1).await();
        } finally {
            server.close();
        }
    }
}

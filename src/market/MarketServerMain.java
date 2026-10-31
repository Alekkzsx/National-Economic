package market;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;

/** Ponto de entrada do servidor; os valores padrão são configuração, não dados ao vivo. */
public final class MarketServerMain {
    private static final int DEFAULT_PORT = 5050;
    private static final String DEFAULT_QUOTE = "0.00";

    private MarketServerMain() {}

    /**
     * Uso: {@code MarketServerMain [porta [SELICRA CDIR USD EUR]]}.
     * USD e EUR são informados manualmente; não há integração com dados de mercado.
     */
    public static void main(String[] args) throws IOException, InterruptedException {
        if (args.length != 0 && args.length != 1 && args.length != 5) {
            throw new IllegalArgumentException(
                    "Uso: MarketServerMain [porta [SELICRA CDIR USD EUR]]");
        }

        int port = args.length == 0 ? DEFAULT_PORT : Integer.parseInt(args[0]);
        BigDecimal[] quotes = parseQuotes(args);
        MarketServer server = new MarketServer(port, quotes[0], quotes[1], quotes[2], quotes[3]);
        server.start();

        System.out.println("Servidor de cotações ativo na porta " + server.getPort() + ".");
        System.out.println("Configuração estática: SELICRA=" + quotes[0].toPlainString()
                + ", CDIR=" + quotes[1].toPlainString()
                + ", USD=" + quotes[2].toPlainString()
                + ", EUR=" + quotes[3].toPlainString() + ".");
        if (args.length < 5) {
            System.out.println("Defaults em 0.00; USD/EUR são configuração manual, sem dados ao vivo.");
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> closeServer(server),
                "market-shutdown"));
        try {
            new CountDownLatch(1).await();
        } finally {
            server.close();
        }
    }

    private static BigDecimal[] parseQuotes(String[] args) {
        BigDecimal[] quotes = new BigDecimal[4];
        for (int i = 0; i < quotes.length; i++) {
            quotes[i] = new BigDecimal(args.length == 5 ? args[i + 1] : DEFAULT_QUOTE);
        }
        return quotes;
    }

    private static void closeServer(MarketServer server) {
        try {
            server.close();
        } catch (IOException exception) {
            System.err.println("Falha ao encerrar o servidor: " + exception.getMessage());
        }
    }
}

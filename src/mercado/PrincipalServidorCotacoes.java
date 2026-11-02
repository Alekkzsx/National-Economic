package mercado;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

/** Inicia o servidor com taxas locais e cotações cambiais oficiais do Banco Central. */
public final class PrincipalServidorCotacoes {
    private static final int PORTA_PADRAO = 5050;

    private PrincipalServidorCotacoes() {}

    /**
     * Uso: {@code PrincipalServidorCotacoes [porta] SELICRA CDIR}.
     *
     * <p>As taxas SELICRA e CDIR são informadas na inicialização; USD e EUR
     * são obtidos do serviço PTAX oficial do Banco Central.</p>
     */
    public static void main(String[] args) throws IOException, InterruptedException {
        if (args.length != 2 && args.length != 3) {
            throw new IllegalArgumentException(
                    "Uso: PrincipalServidorCotacoes [porta] SELICRA CDIR");
        }

        int deslocamentoTaxas = args.length == 3 ? 1 : 0;
        int porta = args.length == 3 ? Integer.parseInt(args[0]) : PORTA_PADRAO;
        BigDecimal selicra = new BigDecimal(args[deslocamentoTaxas]);
        BigDecimal cdir = new BigDecimal(args[deslocamentoTaxas + 1]);
        Map<String, BigDecimal> cotacoes = new LinkedHashMap<>(
                new ProvedorCotacoesBancoCentral().obterCotacoes()
        );
        cotacoes.put("SELICRA", selicra);
        cotacoes.put("CDIR", cdir);

        ServidorCotacoes server = new ServidorCotacoes(porta, cotacoes);
        server.start();

        System.out.println("Servidor de cotações ativo na porta " + server.getPorta() + ".");
        System.out.println("Cotações ativas: SELICRA=" + selicra.toPlainString()
                + ", CDIR=" + cdir.toPlainString()
                + ", USD=" + cotacoes.get("USD").toPlainString()
                + ", EUR=" + cotacoes.get("EUR").toPlainString() + ".");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> encerrarServidor(server),
                "mercado-shutdown"));
        try {
            new CountDownLatch(1).await();
        } finally {
            server.close();
        }
    }

    private static void encerrarServidor(ServidorCotacoes server) {
        try {
            server.close();
        } catch (IOException excecao) {
            System.err.println("Falha ao encerrar o servidor: " + excecao.getMessage());
        }
    }
}

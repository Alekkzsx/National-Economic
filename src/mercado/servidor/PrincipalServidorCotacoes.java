package mercado.servidor;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import mercado.provedores.ProvedorCotacoesBancoCentral;

/**
 * Item 9 — Ponto de entrada do servidor de cotações: recebe {@code [porta] SELICRA CDIR}
 * (taxas anuais em % a.a.) e consulta USD e EUR oficiais na API PTAX do Banco Central.
 */
public final class PrincipalServidorCotacoes {

    private static final int PORTA_PADRAO = 5050;

    private PrincipalServidorCotacoes() {
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        if (args.length != 2 && args.length != 3) {
            throw new IllegalArgumentException(
                    "Uso: PrincipalServidorCotacoes [porta] SELICRA CDIR (taxas anuais em % a.a.)");
        }

        int deslocamento = args.length == 3 ? 1 : 0;
        int porta = args.length == 3 ? Integer.parseInt(args[0]) : PORTA_PADRAO;
        BigDecimal selicra = new BigDecimal(args[deslocamento].trim().replace(',', '.'));
        BigDecimal cdir = new BigDecimal(args[deslocamento + 1].trim().replace(',', '.'));

        Map<String, BigDecimal> cotacoes = new LinkedHashMap<>(
                new ProvedorCotacoesBancoCentral().obterCotacoes());
        cotacoes.put("SELICRA", selicra);
        cotacoes.put("CDIR", cdir);

        ServidorCotacoes servidor = new ServidorCotacoes(porta, cotacoes);
        servidor.start();

        System.out.println("Servidor de cotações ativo na porta " + servidor.getPorta() + ".");
        System.out.println("Taxas (% a.a.): SELICRA=" + selicra.toPlainString() + "%"
                + ", CDIR=" + cdir.toPlainString() + "%"
                + " | Câmbio PTAX (BCB): USD=R$ " + cotacoes.get("USD").toPlainString()
                + ", EUR=R$ " + cotacoes.get("EUR").toPlainString() + ".");

        Runtime.getRuntime().addShutdownHook(
                new Thread(() -> encerrarServidor(servidor), "mercado-shutdown"));
        try {
            new CountDownLatch(1).await();
        } finally {
            servidor.close();
        }
    }

    private static void encerrarServidor(ServidorCotacoes servidor) {
        try {
            servidor.close();
        } catch (IOException excecao) {
            System.err.println("Falha ao encerrar o servidor: " + excecao.getMessage());
        }
    }
}

package mercado;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Map;

/**
 * Verifica a leitura dos campos da resposta PTAX sem depender de acesso à rede.
 */
public final class TesteProvedorCotacoesBancoCentral {
    private TesteProvedorCotacoesBancoCentral() {}

    /** Executa verificações determinísticas do parser do serviço do Banco Central. */
    public static void main(String[] args) throws Exception {
        String respostaDolar = """
                {"value":[
                  {"cotacaoVenda":5.20,"dataHoraCotacao":"2026-02-10 13:10:27.358"},
                  {"cotacaoVenda":5.18,"dataHoraCotacao":"2026-02-11 13:03:28.474"}
                ]}
                """;
        BigDecimal dolar = ProvedorCotacoesBancoCentral.interpretarCotacaoMaisRecente(respostaDolar, false);
        verificar(dolar.compareTo(new BigDecimal("5.18")) == 0,
                "o dólar deve usar a observação com data e hora mais recentes");

        String respostaEuro = """
                {"value":[
                  {"cotacaoVenda":6.10,"dataHoraCotacao":"2026-02-11 13:10:00.000","tipoBoletim":"Intermediário"},
                  {"cotacaoVenda":6.14,"dataHoraCotacao":"2026-02-11 13:03:28.474","tipoBoletim":"Fechamento"}
                ]}
                """;
        BigDecimal euro = ProvedorCotacoesBancoCentral.interpretarCotacaoMaisRecente(respostaEuro, true);
        verificar(euro.compareTo(new BigDecimal("6.14")) == 0,
                "o euro deve ignorar boletins intermediários e selecionar o fechamento");

        boolean respostaVaziaRejeitada = false;
        try {
            ProvedorCotacoesBancoCentral.interpretarCotacaoMaisRecente("{\"value\":[]}", false);
        } catch (IOException esperada) {
            respostaVaziaRejeitada = true;
        }
        verificar(respostaVaziaRejeitada, "uma resposta sem cotação deve gerar erro explícito");

        Map<String, BigDecimal> cotacoesServidor = Map.of(
                "SELICRA", new BigDecimal("10.50"),
                "CDIR", new BigDecimal("1.25"),
                "USD", new BigDecimal("5.18"),
                "EUR", new BigDecimal("6.14")
        );
        try (ServidorCotacoes servidor = new ServidorCotacoes(0, cotacoesServidor)) {
            servidor.start();
            Map<String, BigDecimal> recebidas =
                    new ClienteCotacoes("127.0.0.1", servidor.getPorta()).obterCotacoes();
            verificar(recebidas.equals(cotacoesServidor),
                    "o protocolo TCP deve entregar todas as taxas e cotações configuradas");
        }
    }

    private static void verificar(boolean condicao, String mensagem) {
        if (!condicao) {
            throw new AssertionError(mensagem);
        }
    }
}

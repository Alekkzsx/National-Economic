package test.mercado.provedores;

import static test.util.Afirmacao.*;

import mercado.provedores.ClienteCotacoes;
import mercado.provedores.ProvedorCotacoesBancoCentral;
import mercado.servidor.ServidorCotacoes;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Map;

/**
 * Testes unitários e de fluxo TCP do provedor PTAX do Banco Central (Item 9),
 * executados offline sobre payloads simulados e servidor TCP local.
 */
public final class TesteProvedorCotacoesBancoCentral {

    private TesteProvedorCotacoesBancoCentral() {
    }

    public static void main(String[] args) throws Exception {
        testarParsingDolarPtax();
        testarParsingEuroFechamento();
        testarRespostaVaziaEFluxoTcp();
    }

    private static void testarParsingDolarPtax() throws IOException {
        String respostaDolar = """
                {"value":[
                  {"cotacaoVenda":5.20,"dataHoraCotacao":"2026-02-10 13:10:27.358"},
                  {"cotacaoVenda":5.18,"dataHoraCotacao":"2026-02-11 13:03:28.474"}
                ]}
                """;
        BigDecimal dolar = ProvedorCotacoesBancoCentral.interpretarCotacaoMaisRecente(respostaDolar, false);
        afirmarIgual(new BigDecimal("5.18"), dolar,
                "o dólar deve usar a observação com data e hora mais recentes");
    }

    private static void testarParsingEuroFechamento() throws IOException {
        String respostaEuro = """
                {"value":[
                  {"cotacaoVenda":6.10,"dataHoraCotacao":"2026-02-11 13:10:00.000","tipoBoletim":"Intermediário"},
                  {"cotacaoVenda":6.14,"dataHoraCotacao":"2026-02-11 13:03:28.474","tipoBoletim":"Fechamento"}
                ]}
                """;
        BigDecimal euro = ProvedorCotacoesBancoCentral.interpretarCotacaoMaisRecente(respostaEuro, true);
        afirmarIgual(new BigDecimal("6.14"), euro,
                "o euro deve ignorar boletins intermediários e selecionar o fechamento");
    }

    private static void testarRespostaVaziaEFluxoTcp() throws Exception {
        afirmarRejeicao(
                () -> ProvedorCotacoesBancoCentral.interpretarCotacaoMaisRecente("{\"value\":[]}", false),
                IOException.class,
                "uma resposta sem cotação deve gerar erro explícito");

        Map<String, BigDecimal> cotacoesServidor = Map.of(
                "SELICRA", new BigDecimal("10.50"),
                "CDIR", new BigDecimal("10.40"),
                "USD", new BigDecimal("5.18"),
                "EUR", new BigDecimal("6.14")
        );
        try (ServidorCotacoes servidor = new ServidorCotacoes(0, cotacoesServidor)) {
            servidor.start();
            Map<String, BigDecimal> recebidas =
                    new ClienteCotacoes("127.0.0.1", servidor.getPorta()).obterCotacoes();
            afirmarIgual(cotacoesServidor, recebidas,
                    "o protocolo TCP deve entregar todas as taxas e cotações configuradas");
        }
    }
}

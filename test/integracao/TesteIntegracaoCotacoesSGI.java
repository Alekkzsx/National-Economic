package test.integracao;

import static test.util.Afirmacao.*;

import dominio.aplicacao.AplicacaoFinanceira;
import dominio.operacao.OperacaoFinanceira;
import dominio.operacao.TipoOperacao;
import mercado.provedores.ClienteCotacoes;
import mercado.servidor.ServidorCotacoes;
import servico.sistema.SGI;
import terminal.util.AuxiliarCotacoes;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Map;

/**
 * Teste de integração ponta a ponta (Item 9): ServidorCotacoes TCP -> ClienteCotacoes ->
 * SGI.atualizarCotacoes -> uso de SELICRA, CDIR, USD e EUR em aplicações, operações e saldos.
 */
public final class TesteIntegracaoCotacoesSGI {

    private TesteIntegracaoCotacoesSGI() {
    }

    public static void main(String[] args) throws Exception {
        testarFluxoCompletoServidorCotacoesNoSgi();
        testarFalhaDeServidorNaoCorrompeEstadoDoSgi();
    }

    private static void testarFluxoCompletoServidorCotacoesNoSgi() throws Exception {
        try (ServidorCotacoes servidor = new ServidorCotacoes(
                0,
                new BigDecimal("12.00"),  // SELICRA 12% a.a. -> 0.01/mês
                new BigDecimal("10.80"),  // CDIR 10,80% a.a. -> 0.009/mês
                new BigDecimal("5.00"),   // USD = R$ 5,00
                new BigDecimal("6.00")    // EUR = R$ 6,00
        )) {
            servidor.start();

            SGI sgi = new SGI();
            Map<String, BigDecimal> cotacoes =
                    new ClienteCotacoes("127.0.0.1", servidor.getPorta()).obterCotacoes();
            sgi.atualizarCotacoes(cotacoes);

            // Espelhamento bidirecional garantido no SGI
            afirmarIgual(new BigDecimal("5.00"), sgi.getCotacao("DOLAR").orElse(null), "DOLAR espelha USD");
            afirmarIgual(new BigDecimal("6.00"), sgi.getCotacao("EURO").orElse(null), "EURO espelha EUR");

            // Cadastro usando SELICRA como taxa mensal e aporte convertido de USD 200 -> R$ 1000,00
            String cpf = "52998224725";
            sgi.setCadastro("Cliente Rapidópolis", cpf, "11999998888", 10, 2000L);

            BigDecimal taxaSelicraMensal = AuxiliarCotacoes.taxaMensalDeAnualPercentual(
                    sgi.getCotacao("SELICRA").orElseThrow());
            BigDecimal aporteEmReal = AuxiliarCotacoes.converterParaReal(
                    new BigDecimal("200.00"), sgi.getCotacao("USD").orElseThrow());

            LocalDate inicio = LocalDate.of(2026, 8, 3); // segunda-feira
            AplicacaoFinanceira app = new AplicacaoFinanceira(
                    "Tesouro SELICRA", "SEL-1", aporteEmReal, taxaSelicraMensal,
                    0, 0, 0, 0, 0, 0, inicio);
            sgi.cadastrarAplicacao(cpf, app);

            // Depósito em EUR 100 -> R$ 600,00
            BigDecimal depositoEmReal = AuxiliarCotacoes.converterParaReal(
                    new BigDecimal("100.00"), sgi.getCotacao("EUR").orElseThrow());
            sgi.cadastrarOperacao(cpf, "SEL-1",
                    new OperacaoFinanceira(inicio, depositoEmReal, "Aporte em EUR", TipoOperacao.DEPOSITO));
            afirmarIgual(new BigDecimal("1600.00"), sgi.getSaldo(cpf, "SEL-1"), "saldo após aporte em USD+EUR");

            // Atualiza em dia útil (terça-feira) e verifica rendimento positivo
            sgi.atualizarAplicacao(inicio.plusDays(1));
            afirmarVerdadeiro(sgi.getSaldo(cpf, "SEL-1").compareTo(new BigDecimal("1600.00")) > 0,
                    "aplicação rendeu com taxa derivada da SELICRA");

            // Consulta de equivalência cambial do saldo
            BigDecimal emDolar = AuxiliarCotacoes.converterDeReal(
                    sgi.getSaldo(cpf), sgi.getCotacao("USD").orElseThrow());
            afirmarVerdadeiro(emDolar.compareTo(new BigDecimal("320.00")) > 0,
                    "saldo equivalente em USD reflete o rendimento");

            // Persistência e recarga preservam o saldo
            Path arquivo = Files.createTempFile("sgi-cotacoes-", ".db");
            try {
                sgi.arquivoSalvarDados(arquivo);
                SGI restaurado = SGI.arquivoLerDados(arquivo);
                afirmarIgual(sgi.getSaldo(cpf, "SEL-1"), restaurado.getSaldo(cpf, "SEL-1"),
                        "saldo mantido após salvar e recarregar");
            } finally {
                Files.deleteIfExists(arquivo);
            }
        }
    }

    private static void testarFalhaDeServidorNaoCorrompeEstadoDoSgi() throws Exception {
        SGI sgi = new SGI();
        sgi.atualizarCotacoes(Map.of("SELICRA", new BigDecimal("10.50"), "USD", new BigDecimal("5.10")));

        int portaFechada;
        try (ServidorCotacoes temp = new ServidorCotacoes(
                0, BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE)) {
            portaFechada = temp.getPorta();
        }

        afirmarRejeicao(
                () -> new ClienteCotacoes("127.0.0.1", portaFechada).obterCotacoes(),
                IOException.class,
                "conexão em porta fechada falha com IOException");

        afirmarIgual(new BigDecimal("10.50"), sgi.getCotacao("SELICRA").orElse(null),
                "cotações anteriores permanecem intactas após falha de rede");
    }
}

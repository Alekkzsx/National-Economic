package terminal;

import modelo.OpcaoIOF;
import modelo.OpcaoIR;
import modelo.AplicacaoFinanceira;
import modelo.FrequenciaResgate;
import modelo.PeriodicidadeRendimento;

import java.util.Arrays;

/** Configura os termos de rendimento, resgate e tributação de uma aplicação. */
public final class TermosInvestimento {
    private TermosInvestimento() { }

    /** Solicita prazos e opções de rendimento para a aplicação. */
    public static void configurar(AplicacaoFinanceira aplicacao, EntradaTerminal entrada) {
        int diasVencimento = entrada.lerInteiro("Dias até o vencimento (0 = sem vencimento): ",
                0, Integer.MAX_VALUE);
        aplicacao.setDiasVencimento(diasVencimento);

        int maximumStay = diasVencimento == 0 ? Integer.MAX_VALUE : diasVencimento;
        int minimumStay = entrada.lerInteiro("Permanência mínima em dias: ", 0, maximumStay);
        aplicacao.setDiasPermanenciaMinima(minimumStay);

        PeriodicidadeRendimento[] yieldOptions = diasVencimento == 0
                ? Arrays.stream(PeriodicidadeRendimento.values())
                        .filter(period -> period != PeriodicidadeRendimento.AT_MATURITY)
                        .toArray(PeriodicidadeRendimento[]::new)
                : PeriodicidadeRendimento.values();
        aplicacao.setPeriodicidadeRendimento(entrada.lerOpcaoEnum("Periodicidade de rendimento", yieldOptions,
                TermosInvestimento::rotuloPeriodicidadeRendimento));
        aplicacao.setFrequenciaResgate(entrada.lerOpcaoEnum("Frequência de resgate",
                FrequenciaResgate.values(), TermosInvestimento::rotuloFrequenciaResgate));
        aplicacao.setOpcaoIOF(entrada.lerOpcaoEnum("Opção de IOF", OpcaoIOF.values(),
                TermosInvestimento::rotuloOpcaoIOF));
        aplicacao.setOpcaoIR(entrada.lerOpcaoEnum("Opção de IR", OpcaoIR.values(),
                TermosInvestimento::rotuloOpcaoIR));
    }

    /** Descreve a periodicidade de rendimento para o menu. */
    private static String rotuloPeriodicidadeRendimento(PeriodicidadeRendimento period) {
        return switch (period) {
            case DAILY -> "diário";
            case MONTHLY_30_DAYS -> "a cada 30 dias";
            case QUARTERLY_90_DAYS -> "a cada 90 dias";
            case HALF_YEARLY_180_DAYS -> "a cada 180 dias";
            case YEARLY_365_DAYS -> "a cada 365 dias";
            case AT_MATURITY -> "no vencimento";
        };
    }

    /** Descreve a frequência de resgate para o menu. */
    private static String rotuloFrequenciaResgate(FrequenciaResgate frequency) {
        return switch (frequency) {
            case DAILY -> "diária";
            case MONTHLY -> "mensal (30 dias)";
            case QUARTERLY -> "trimestral (90 dias)";
            case HALF_YEARLY -> "semestral (180 dias)";
            case YEARLY -> "anual (365 dias)";
        };
    }

    /** Descreve as opções de IOF e IR para o menu. */
    private static String rotuloOpcaoIOF(OpcaoIOF opcaoMenu) {
        return switch (opcaoMenu) {
            case EXEMPT -> "isento";
            case THREE_POINT_FIVE_PERCENT_BEFORE_30_DAYS -> "3,5% nos primeiros 29 dias";
            case THREE_POINT_FIVE_PERCENT_ALWAYS -> "3,5% durante todo o prazo";
        };
    }

    private static String rotuloOpcaoIR(OpcaoIR opcaoMenu) {
        return switch (opcaoMenu) {
            case EXEMPT -> "isento";
            case REGRESSIVE -> "regressivo por prazo";
        };
    }
}

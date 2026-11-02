package terminal;

import modelo.OpcaoIOF;
import modelo.OpcaoIR;
import modelo.FrequenciaResgate;
import modelo.PeriodicidadeRendimento;

import java.util.Arrays;

/** Configura os termos de rendimento, resgate e tributação de uma aplicação. */
public final class TermosInvestimento {
    private TermosInvestimento() { }

    /** Configura os termos equivalentes no modelo acadêmico usado pela aplicação principal. */
    public static void configurar(dominio.AplicacaoFinanceira aplicacao, EntradaTerminal entrada) {
        int diasVencimento = entrada.lerInteiro("Dias até o vencimento (0 = sem vencimento): ",
                0, Integer.MAX_VALUE);
        aplicacao.setTempoFinalVencimento(diasVencimento);

        int permanenciaMaxima = diasVencimento == 0 ? Integer.MAX_VALUE : diasVencimento;
        aplicacao.setTempoMinimoPermanencia(
                entrada.lerInteiro("Permanência mínima em dias: ", 0, permanenciaMaxima)
        );

        PeriodicidadeRendimento[] periodicidades = diasVencimento == 0
                ? Arrays.stream(PeriodicidadeRendimento.values())
                        .filter(periodicidade -> periodicidade != PeriodicidadeRendimento.AT_MATURITY)
                        .toArray(PeriodicidadeRendimento[]::new)
                : PeriodicidadeRendimento.values();
        PeriodicidadeRendimento periodicidade = entrada.lerOpcaoEnum(
                "Periodicidade de rendimento", periodicidades,
                TermosInvestimento::rotuloPeriodicidadeRendimento
        );
        aplicacao.setTipoRendimento(switch (periodicidade) {
            case DAILY -> 0;
            case MONTHLY_30_DAYS -> 1;
            case QUARTERLY_90_DAYS -> 2;
            case HALF_YEARLY_180_DAYS -> 3;
            case YEARLY_365_DAYS -> 4;
            case AT_MATURITY -> 5;
        });

        FrequenciaResgate frequencia = entrada.lerOpcaoEnum(
                "Frequência de resgate", FrequenciaResgate.values(),
                TermosInvestimento::rotuloFrequenciaResgate
        );
        aplicacao.setTipoResgate(frequencia.ordinal());
        aplicacao.setIof(entrada.lerOpcaoEnum(
                "Opção de IOF", OpcaoIOF.values(), TermosInvestimento::rotuloOpcaoIOF
        ).ordinal());
        aplicacao.setTipoImpostoRenda(entrada.lerOpcaoEnum(
                "Opção de IR", OpcaoIR.values(), TermosInvestimento::rotuloOpcaoIR
        ).ordinal());
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

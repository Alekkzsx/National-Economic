package app;

import domain.IofOption;
import domain.IrOption;
import domain.Investment;
import domain.RedemptionFrequency;
import domain.YieldPeriod;

import java.util.Arrays;

/** Configura os termos de rendimento, resgate e tributação de uma aplicação. */
public final class InvestmentTerms {
    private InvestmentTerms() { }

    /** Solicita prazos e opções de rendimento para a aplicação. */
    public static void configure(Investment investment, ConsoleInput input) {
        int maturityDays = input.readInt("Dias até o vencimento (0 = sem vencimento): ",
                0, Integer.MAX_VALUE);
        investment.setMaturityDays(maturityDays);

        int maximumStay = maturityDays == 0 ? Integer.MAX_VALUE : maturityDays;
        int minimumStay = input.readInt("Permanência mínima em dias: ", 0, maximumStay);
        investment.setMinimumStayDays(minimumStay);

        YieldPeriod[] yieldOptions = maturityDays == 0
                ? Arrays.stream(YieldPeriod.values())
                        .filter(period -> period != YieldPeriod.AT_MATURITY)
                        .toArray(YieldPeriod[]::new)
                : YieldPeriod.values();
        investment.setYieldPeriod(input.readEnumChoice("Periodicidade de rendimento", yieldOptions,
                InvestmentTerms::yieldPeriodLabel));
        investment.setRedemptionFrequency(input.readEnumChoice("Frequência de resgate",
                RedemptionFrequency.values(), InvestmentTerms::redemptionFrequencyLabel));
        investment.setIofOption(input.readEnumChoice("Opção de IOF", IofOption.values(),
                InvestmentTerms::iofOptionLabel));
        investment.setIrOption(input.readEnumChoice("Opção de IR", IrOption.values(),
                InvestmentTerms::irOptionLabel));
    }

    /** Descreve a periodicidade de rendimento para o menu. */
    private static String yieldPeriodLabel(YieldPeriod period) {
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
    private static String redemptionFrequencyLabel(RedemptionFrequency frequency) {
        return switch (frequency) {
            case DAILY -> "diária";
            case MONTHLY -> "mensal (30 dias)";
            case QUARTERLY -> "trimestral (90 dias)";
            case HALF_YEARLY -> "semestral (180 dias)";
            case YEARLY -> "anual (365 dias)";
        };
    }

    /** Descreve as opções de IOF e IR para o menu. */
    private static String iofOptionLabel(IofOption option) {
        return switch (option) {
            case EXEMPT -> "isento";
            case THREE_POINT_FIVE_PERCENT_BEFORE_30_DAYS -> "3,5% nos primeiros 29 dias";
            case THREE_POINT_FIVE_PERCENT_ALWAYS -> "3,5% durante todo o prazo";
        };
    }

    private static String irOptionLabel(IrOption option) {
        return switch (option) {
            case EXEMPT -> "isento";
            case REGRESSIVE -> "regressivo por prazo";
        };
    }
}

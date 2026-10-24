package domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Centraliza o cálculo de IOF e IR para uma aplicação financeira. */
final class InvestmentTaxCalculator {
    private static final BigDecimal IOF_RATE = new BigDecimal("0.035");
    private static final BigDecimal IR_UP_TO_180_DAYS = new BigDecimal("0.225");
    private static final BigDecimal IR_UP_TO_360_DAYS = new BigDecimal("0.20");
    private static final BigDecimal IR_UP_TO_720_DAYS = new BigDecimal("0.175");
    private static final BigDecimal IR_AFTER_720_DAYS = new BigDecimal("0.15");

    private InvestmentTaxCalculator() {
    }

    /** Calcula IOF sobre o rendimento bruto conforme opção e prazo. */
    static BigDecimal calculateIof(
            BigDecimal grossEarnings,
            int elapsedDays,
            IofOption option,
            TaxRegime legacyRegime
    ) {
        if (legacyRegime == TaxRegime.TAX_FREE || option == IofOption.EXEMPT) {
            return Money.ZERO;
        }

        boolean chargeIof = option == IofOption.THREE_POINT_FIVE_PERCENT_ALWAYS
                || elapsedDays < 30;
        return chargeIof ? percentage(grossEarnings, IOF_RATE) : Money.ZERO;
    }

    /** Calcula IR regressivo sobre o rendimento bruto depois do IOF. */
    static BigDecimal calculateIr(
            BigDecimal grossEarnings,
            BigDecimal iof,
            int elapsedDays,
            IrOption option,
            TaxRegime legacyRegime
    ) {
        if (legacyRegime == TaxRegime.TAX_FREE || option == IrOption.EXEMPT) {
            return Money.ZERO;
        }

        BigDecimal taxableEarnings = grossEarnings.subtract(iof).max(BigDecimal.ZERO);
        return percentage(taxableEarnings, irRate(elapsedDays));
    }

    private static BigDecimal irRate(int elapsedDays) {
        if (elapsedDays <= 180) {
            return IR_UP_TO_180_DAYS;
        }
        if (elapsedDays <= 360) {
            return IR_UP_TO_360_DAYS;
        }
        if (elapsedDays <= 720) {
            return IR_UP_TO_720_DAYS;
        }
        return IR_AFTER_720_DAYS;
    }

    private static BigDecimal percentage(BigDecimal amount, BigDecimal rate) {
        return amount.multiply(rate).setScale(Money.SCALE, RoundingMode.HALF_UP);
    }
}

package modelo;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Centraliza o cálculo de IOF e IR para uma aplicação financeira. */
final class CalculadoraTributaria {
    private static final BigDecimal IOF_RATE = new BigDecimal("0.035");
    private static final BigDecimal IR_UP_TO_180_DAYS = new BigDecimal("0.225");
    private static final BigDecimal IR_UP_TO_360_DAYS = new BigDecimal("0.20");
    private static final BigDecimal IR_UP_TO_720_DAYS = new BigDecimal("0.175");
    private static final BigDecimal IR_AFTER_720_DAYS = new BigDecimal("0.15");

    private CalculadoraTributaria() {
    }

    /** Calcula IOF sobre o rendimento bruto conforme opção e prazo. */
    static BigDecimal calculateIof(
            BigDecimal grossEarnings,
            int elapsedDays,
            OpcaoIOF option,
            RegimeTributario legacyRegime
    ) {
        if (legacyRegime == RegimeTributario.TAX_FREE || option == OpcaoIOF.EXEMPT) {
            return Moeda.ZERO;
        }

        boolean chargeIof = option == OpcaoIOF.THREE_POINT_FIVE_PERCENT_ALWAYS
                || elapsedDays < 30;
        return chargeIof ? percentual(grossEarnings, IOF_RATE) : Moeda.ZERO;
    }

    /** Calcula IR regressivo sobre o rendimento bruto depois do IOF. */
    static BigDecimal calculateIr(
            BigDecimal grossEarnings,
            BigDecimal iof,
            int elapsedDays,
            OpcaoIR option,
            RegimeTributario legacyRegime
    ) {
        if (legacyRegime == RegimeTributario.TAX_FREE || option == OpcaoIR.EXEMPT) {
            return Moeda.ZERO;
        }

        BigDecimal taxableEarnings = grossEarnings.subtract(iof).max(BigDecimal.ZERO);
        return percentual(taxableEarnings, aliquotaIR(elapsedDays));
    }

    private static BigDecimal aliquotaIR(int elapsedDays) {
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

    private static BigDecimal percentual(BigDecimal amount, BigDecimal rate) {
        return amount.multiply(rate).setScale(Moeda.SCALE, RoundingMode.HALF_UP);
    }
}

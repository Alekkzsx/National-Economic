package legado.modelo.tributacao;

import java.math.BigDecimal;
import java.math.RoundingMode;
import legado.util.Moeda;

/** Centraliza o cálculo de IOF e IR (uso interno da camada {@code legado/}). */
public final class CalculadoraTributaria {
    private static final BigDecimal ALIQUOTA_IOF = new BigDecimal("0.035");
    private static final BigDecimal ALIQUOTA_IR_ATE_180_DIAS = new BigDecimal("0.225");
    private static final BigDecimal ALIQUOTA_IR_ATE_360_DIAS = new BigDecimal("0.20");
    private static final BigDecimal ALIQUOTA_IR_ATE_720_DIAS = new BigDecimal("0.175");
    private static final BigDecimal ALIQUOTA_IR_ACIMA_720_DIAS = new BigDecimal("0.15");

    private CalculadoraTributaria() {
    }

    /** Calcula IOF sobre o rendimento bruto conforme opção e prazo. */
    public static BigDecimal calcularIOF(
            BigDecimal rendimentoBruto,
            int diasCorridos,
            OpcaoIOF opcaoMenu,
            RegimeTributario regimeLegado
    ) {
        if (regimeLegado == RegimeTributario.TAX_FREE || opcaoMenu == OpcaoIOF.EXEMPT) {
            return Moeda.ZERO;
        }

        boolean cobrarIOF = opcaoMenu == OpcaoIOF.THREE_POINT_FIVE_PERCENT_ALWAYS
                || diasCorridos < 30;
        return cobrarIOF ? percentual(rendimentoBruto, ALIQUOTA_IOF) : Moeda.ZERO;
    }

    /** Calcula IR regressivo sobre o rendimento bruto depois do IOF. */
    public static BigDecimal calcularIR(
            BigDecimal rendimentoBruto,
            BigDecimal valorIOF,
            int diasCorridos,
            OpcaoIR opcaoMenu,
            RegimeTributario regimeLegado
    ) {
        if (regimeLegado == RegimeTributario.TAX_FREE || opcaoMenu == OpcaoIR.EXEMPT) {
            return Moeda.ZERO;
        }

        BigDecimal rendimentoTributavel = rendimentoBruto.subtract(valorIOF).max(BigDecimal.ZERO);
        return percentual(rendimentoTributavel, aliquotaIR(diasCorridos));
    }

    private static BigDecimal aliquotaIR(int diasCorridos) {
        if (diasCorridos <= 180) {
            return ALIQUOTA_IR_ATE_180_DIAS;
        }
        if (diasCorridos <= 360) {
            return ALIQUOTA_IR_ATE_360_DIAS;
        }
        if (diasCorridos <= 720) {
            return ALIQUOTA_IR_ATE_720_DIAS;
        }
        return ALIQUOTA_IR_ACIMA_720_DIAS;
    }

    private static BigDecimal percentual(BigDecimal valor, BigDecimal rate) {
        return valor.multiply(rate).setScale(Moeda.CASAS_DECIMAIS, RoundingMode.HALF_UP);
    }
}

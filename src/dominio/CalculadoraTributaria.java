package dominio;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Regras puras de apuração de IOF e Imposto de Renda Regressivo sobre rendimentos (Item 1).
 *
 * <ul>
 *   <li>IOF: 0 = Isento; 1 = 3,5% até 29 dias corridos (isento a partir de 30); 2 = 3,5% sempre.</li>
 *   <li>IR (sobre rendimento bruto menos IOF): 0 = Isento; 1 = Regressivo (22,5% até 180d, 20% até 360d,
 *       17,5% até 720d, 15% acima de 720d).</li>
 * </ul>
 */
public final class CalculadoraTributaria {

    public static final BigDecimal ALIQUOTA_IOF = new BigDecimal("0.035");
    public static final BigDecimal ALIQUOTA_IR_ATE_180_DIAS = new BigDecimal("0.225");
    public static final BigDecimal ALIQUOTA_IR_181_A_360_DIAS = new BigDecimal("0.20");
    public static final BigDecimal ALIQUOTA_IR_361_A_720_DIAS = new BigDecimal("0.175");
    public static final BigDecimal ALIQUOTA_IR_ACIMA_720_DIAS = new BigDecimal("0.15");

    private CalculadoraTributaria() {
    }

    /** Calcula o IOF devido conforme o código (0=Isento, 1=Carência 30d, 2=Irrestrito). */
    public static BigDecimal calcularIof(BigDecimal rendimentoBruto, int diasCorridos, int opcaoIof) {
        if (diasCorridos < 0) {
            throw new IllegalArgumentException("Dias corridos não podem ser negativos: " + diasCorridos);
        }
        if (rendimentoBruto == null || rendimentoBruto.signum() <= 0) {
            return Moeda.ZERO;
        }
        return switch (opcaoIof) {
            case 0 -> Moeda.ZERO;
            case 1 -> diasCorridos < 30 ? Moeda.arredondar(rendimentoBruto.multiply(ALIQUOTA_IOF)) : Moeda.ZERO;
            case 2 -> Moeda.arredondar(rendimentoBruto.multiply(ALIQUOTA_IOF));
            default -> throw new IllegalArgumentException(
                    "Opção de IOF inválida: " + opcaoIof + ". Use 0 (Isento), 1 (Carência 30d) ou 2 (Irrestrito).");
        };
    }

    public static BigDecimal calcularIof(BigDecimal rendimentoBruto, int diasCorridos, OpcaoIOF opcao) {
        Objects.requireNonNull(opcao, "Opção de IOF não pode ser nula.");
        return calcularIof(rendimentoBruto, diasCorridos, opcao.getCodigo());
    }

    public static BigDecimal calcularIof(BigDecimal rendimentoBruto, int diasCorridos,
                                         OpcaoIOF opcao, RegimeTributario regime) {
        return (regime != null && regime.ehIsento())
                ? Moeda.ZERO
                : calcularIof(rendimentoBruto, diasCorridos, opcao);
    }

    /** Calcula o IR devido sobre a base líquida de IOF conforme o código (0=Isento, 1=Regressivo). */
    public static BigDecimal calcularIr(BigDecimal rendimentoBruto, BigDecimal iof, int diasCorridos, int opcaoIr) {
        if (diasCorridos < 0) {
            throw new IllegalArgumentException("Dias corridos não podem ser negativos: " + diasCorridos);
        }
        if (rendimentoBruto == null || rendimentoBruto.signum() <= 0 || opcaoIr == 0) {
            return Moeda.ZERO;
        }
        if (opcaoIr != 1) {
            throw new IllegalArgumentException(
                    "Opção de IR inválida: " + opcaoIr + ". Use 0 (Isento) ou 1 (Tabela Regressiva).");
        }
        BigDecimal baseTributavel = rendimentoBruto.subtract(iof == null ? Moeda.ZERO : iof);
        if (baseTributavel.signum() <= 0) {
            return Moeda.ZERO;
        }
        return Moeda.arredondar(baseTributavel.multiply(obterAliquotaIr(diasCorridos)));
    }

    public static BigDecimal calcularIr(BigDecimal rendimentoBruto, BigDecimal iof, int diasCorridos, OpcaoIR opcao) {
        Objects.requireNonNull(opcao, "Opção de IR não pode ser nula.");
        return calcularIr(rendimentoBruto, iof, diasCorridos, opcao.getCodigo());
    }

    public static BigDecimal calcularIr(BigDecimal rendimentoBruto, BigDecimal iof, int diasCorridos,
                                        OpcaoIR opcao, RegimeTributario regime) {
        return (regime != null && regime.ehIsento())
                ? Moeda.ZERO
                : calcularIr(rendimentoBruto, iof, diasCorridos, opcao);
    }

    /** Retorna a alíquota da tabela regressiva para o prazo em dias corridos. */
    public static BigDecimal obterAliquotaIr(int diasCorridos) {
        if (diasCorridos < 0) {
            throw new IllegalArgumentException("Dias corridos não podem ser negativos: " + diasCorridos);
        }
        if (diasCorridos <= 180) return ALIQUOTA_IR_ATE_180_DIAS;
        if (diasCorridos <= 360) return ALIQUOTA_IR_181_A_360_DIAS;
        if (diasCorridos <= 720) return ALIQUOTA_IR_361_A_720_DIAS;
        return ALIQUOTA_IR_ACIMA_720_DIAS;
    }

    /** Calcula o total de tributos devidos (IOF + IR). */
    public static BigDecimal calcularTotalTributos(BigDecimal rendimentoBruto, int diasCorridos,
                                                   int opcaoIof, int opcaoIr) {
        BigDecimal iof = calcularIof(rendimentoBruto, diasCorridos, opcaoIof);
        BigDecimal ir = calcularIr(rendimentoBruto, iof, diasCorridos, opcaoIr);
        return iof.add(ir);
    }

    public static BigDecimal calcularImpostoTotal(BigDecimal rendimentoBruto, int diasCorridos,
                                                  OpcaoIOF opcaoIof, OpcaoIR opcaoIr) {
        Objects.requireNonNull(opcaoIof, "Opção de IOF não pode ser nula.");
        Objects.requireNonNull(opcaoIr, "Opção de IR não pode ser nula.");
        return calcularTotalTributos(rendimentoBruto, diasCorridos, opcaoIof.getCodigo(), opcaoIr.getCodigo());
    }

    public static BigDecimal calcularImpostoTotal(BigDecimal rendimentoBruto, int diasCorridos,
                                                  int codigoIof, int codigoIr) {
        return calcularTotalTributos(rendimentoBruto, diasCorridos, codigoIof, codigoIr);
    }

    /** Apura o rendimento líquido após dedução de IOF e IR (mínimo zero). */
    public static BigDecimal calcularRendimentoLiquido(BigDecimal rendimentoBruto, int diasCorridos,
                                                       OpcaoIOF opcaoIof, OpcaoIR opcaoIr) {
        if (rendimentoBruto == null || rendimentoBruto.signum() <= 0) {
            return Moeda.ZERO;
        }
        BigDecimal tributos = calcularImpostoTotal(rendimentoBruto, diasCorridos, opcaoIof, opcaoIr);
        return rendimentoBruto.subtract(tributos).max(Moeda.ZERO);
    }
}

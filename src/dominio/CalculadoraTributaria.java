package dominio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Calculadora utilitária responsável pela apuração de tributos (IOF e IR) incidentes
 * sobre rendimentos auferidos em aplicações financeiras.
 *
 * <p>Esta classe implementa as regras tributárias da atividade acadêmica e da legislação brasileira:</p>
 * <ul>
 *   <li><b>IOF</b> (Item 1, R1.1):
 *     <ul>
 *       <li>0 - Isento: 0,00% de retenção.</li>
 *       <li>1 - 3,5% com carência de 30 dias: alíquota de 3,5% para resgates ou apurações até o 29º dia;
 *           isento a partir de 30 dias corridos.</li>
 *       <li>2 - 3,5% irrestrito: alíquota fixa de 3,5% durante todo o prazo da aplicação.</li>
 *     </ul>
 *   </li>
 *   <li><b>Imposto de Renda Regressivo</b> (Item 1, R1.1):
 *     <ul>
 *       <li>0 - Isento: 0,00% de retenção.</li>
 *       <li>1 - Tabela Regressiva sobre a base líquida de IOF:
 *         <ul>
 *           <li>De 0 a 180 dias: 22,50%</li>
 *           <li>De 181 a 360 dias: 20,00%</li>
 *           <li>De 361 a 720 dias: 17,50%</li>
 *           <li>Acima de 720 dias: 15,00%</li>
 *         </ul>
 *       </li>
 *     </ul>
 *   </li>
 * </ul>
 */
public final class CalculadoraTributaria {

    /** Alíquota padrão do IOF: 3,5% (0.035). */
    public static final BigDecimal ALIQUOTA_IOF = new BigDecimal("0.035");

    /** Alíquota de Imposto de Renda para aplicações de até 180 dias corridos: 22,50% (0.225). */
    public static final BigDecimal ALIQUOTA_IR_ATE_180_DIAS = new BigDecimal("0.225");

    /** Alíquota de Imposto de Renda para aplicações de 181 até 360 dias corridos: 20,00% (0.20). */
    public static final BigDecimal ALIQUOTA_IR_181_A_360_DIAS = new BigDecimal("0.20");

    /** Alíquota de Imposto de Renda para aplicações de 361 até 720 dias corridos: 17,50% (0.175). */
    public static final BigDecimal ALIQUOTA_IR_361_A_720_DIAS = new BigDecimal("0.175");

    /** Alíquota de Imposto de Renda para aplicações acima de 720 dias corridos: 15,00% (0.15). */
    public static final BigDecimal ALIQUOTA_IR_ACIMA_720_DIAS = new BigDecimal("0.15");

    private CalculadoraTributaria() {
        // Construtor privado para classe utilitária
    }

    /**
     * Calcula o IOF devido a partir do código numérico didático da opção (0, 1 ou 2).
     *
     * @param rendimentoBruto o ganho bruto auferido pela aplicação
     * @param diasCorridos a quantidade de dias corridos transcorridos desde a aplicação
     * @param opcaoIof o código da política de IOF (0 - Isento; 1 - 3,5% carência 30d; 2 - 3,5% irrestrito)
     * @return o valor monetário do IOF devido com 2 casas decimais
     * @throws IllegalArgumentException se opcaoIof não for 0, 1 ou 2, ou se diasCorridos for negativo
     */
    public static BigDecimal calcularIof(BigDecimal rendimentoBruto, int diasCorridos, int opcaoIof) {
        if (diasCorridos < 0) {
            throw new IllegalArgumentException("Dias corridos não podem ser negativos: " + diasCorridos);
        }
        if (rendimentoBruto == null || rendimentoBruto.signum() <= 0) {
            return Moeda.ZERO;
        }
        return switch (opcaoIof) {
            case 0 -> Moeda.ZERO;
            case 1 -> (diasCorridos < 30)
                    ? Moeda.arredondar(rendimentoBruto.multiply(ALIQUOTA_IOF))
                    : Moeda.ZERO;
            case 2 -> Moeda.arredondar(rendimentoBruto.multiply(ALIQUOTA_IOF));
            default -> throw new IllegalArgumentException(
                    "Opção de IOF inválida: " + opcaoIof + ". Opções válidas: 0 (Isento), 1 (Carência 30d) ou 2 (Irrestrito).");
        };
    }

    /**
     * Calcula o IOF devido utilizando a constante tipada {@link OpcaoIOF}.
     *
     * @param rendimentoBruto o ganho bruto auferido
     * @param diasCorridos dias corridos transcorridos
     * @param opcao a política selecionada de IOF
     * @return o montante de IOF devido
     * @throws NullPointerException se opcao for nula
     * @throws IllegalArgumentException se diasCorridos for negativo
     */
    public static BigDecimal calcularIof(BigDecimal rendimentoBruto, int diasCorridos, OpcaoIOF opcao) {
        Objects.requireNonNull(opcao, "Opção de IOF não pode ser nula.");
        return calcularIof(rendimentoBruto, diasCorridos, opcao.getCodigo());
    }

    /**
     * Calcula o IOF considerando adicionalmente o regime tributário geral da aplicação.
     *
     * @param rendimentoBruto o ganho bruto auferido
     * @param diasCorridos dias corridos transcorridos
     * @param opcao a opção de IOF configurada
     * @param regime o regime tributário da aplicação
     * @return o IOF devido (retorna zero se o regime for {@link RegimeTributario#ISENTO})
     */
    public static BigDecimal calcularIof(BigDecimal rendimentoBruto, int diasCorridos, OpcaoIOF opcao, RegimeTributario regime) {
        if (regime != null && regime.ehIsento()) {
            return Moeda.ZERO;
        }
        return calcularIof(rendimentoBruto, diasCorridos, opcao);
    }

    /**
     * Calcula o Imposto de Renda devido a partir do código numérico didático da opção (0 ou 1).
     *
     * @param rendimentoBruto o ganho bruto auferido
     * @param iof o valor do IOF já retido para dedução da base de cálculo
     * @param diasCorridos a quantidade de dias corridos transcorridos
     * @param opcaoIr o código do regime de IR (0 - Isento; 1 - Tabela Regressiva)
     * @return o montante monetário de IR devido com 2 casas decimais
     * @throws IllegalArgumentException se opcaoIr não for 0 ou 1, ou se diasCorridos for negativo
     */
    public static BigDecimal calcularIr(BigDecimal rendimentoBruto, BigDecimal iof, int diasCorridos, int opcaoIr) {
        if (diasCorridos < 0) {
            throw new IllegalArgumentException("Dias corridos não podem ser negativos: " + diasCorridos);
        }
        if (rendimentoBruto == null || rendimentoBruto.signum() <= 0) {
            return Moeda.ZERO;
        }
        if (opcaoIr == 0) {
            return Moeda.ZERO;
        }
        if (opcaoIr != 1) {
            throw new IllegalArgumentException(
                    "Opção de IR inválida: " + opcaoIr + ". Opções válidas: 0 (Isento) ou 1 (Tabela Regressiva).");
        }
        BigDecimal iofDevido = (iof == null) ? Moeda.ZERO : iof;
        BigDecimal baseTributavel = rendimentoBruto.subtract(iofDevido);
        if (baseTributavel.signum() <= 0) {
            return Moeda.ZERO;
        }
        BigDecimal aliquota = obterAliquotaIr(diasCorridos);
        return Moeda.arredondar(baseTributavel.multiply(aliquota));
    }

    /**
     * Calcula o Imposto de Renda devido utilizando a constante tipada {@link OpcaoIR}.
     *
     * @param rendimentoBruto ganho bruto da aplicação
     * @param iof montante de IOF retido
     * @param diasCorridos dias corridos transcorridos
     * @param opcao opção tipada de IR
     * @return valor de IR apurado
     */
    public static BigDecimal calcularIr(BigDecimal rendimentoBruto, BigDecimal iof, int diasCorridos, OpcaoIR opcao) {
        Objects.requireNonNull(opcao, "Opção de IR não pode ser nula.");
        return calcularIr(rendimentoBruto, iof, diasCorridos, opcao.getCodigo());
    }

    /**
     * Calcula o IR considerando adicionalmente o regime tributário geral da aplicação.
     *
     * @param rendimentoBruto ganho bruto da aplicação
     * @param iof montante de IOF retido
     * @param diasCorridos dias corridos transcorridos
     * @param opcao opção tipada de IR
     * @param regime regime tributário da aplicação
     * @return valor de IR apurado (zero se o regime for isento)
     */
    public static BigDecimal calcularIr(BigDecimal rendimentoBruto, BigDecimal iof, int diasCorridos, OpcaoIR opcao, RegimeTributario regime) {
        if (regime != null && regime.ehIsento()) {
            return Moeda.ZERO;
        }
        return calcularIr(rendimentoBruto, iof, diasCorridos, opcao);
    }

    /**
     * Retorna a alíquota da Tabela Regressiva de Imposto de Renda correspondente ao prazo em dias corridos.
     *
     * @param diasCorridos quantidade de dias corridos desde a aplicação
     * @return alíquota em fração decimal (0.225, 0.20, 0.175 ou 0.15)
     * @throws IllegalArgumentException se diasCorridos for negativo
     */
    public static BigDecimal obterAliquotaIr(int diasCorridos) {
        if (diasCorridos < 0) {
            throw new IllegalArgumentException("Dias corridos não podem ser negativos: " + diasCorridos);
        }
        if (diasCorridos <= 180) {
            return ALIQUOTA_IR_ATE_180_DIAS;
        }
        if (diasCorridos <= 360) {
            return ALIQUOTA_IR_181_A_360_DIAS;
        }
        if (diasCorridos <= 720) {
            return ALIQUOTA_IR_361_A_720_DIAS;
        }
        return ALIQUOTA_IR_ACIMA_720_DIAS;
    }

    /**
     * Calcula o total consolidado de tributos devidos (IOF + IR).
     *
     * @param rendimentoBruto ganho bruto auferido
     * @param diasCorridos dias corridos transcorridos
     * @param opcaoIof código da opção de IOF (0 a 2)
     * @param opcaoIr código da opção de IR (0 ou 1)
     * @return a soma dos tributos devidos (IOF + IR)
     */
    public static BigDecimal calcularTotalTributos(BigDecimal rendimentoBruto, int diasCorridos, int opcaoIof, int opcaoIr) {
        BigDecimal iof = calcularIof(rendimentoBruto, diasCorridos, opcaoIof);
        BigDecimal ir = calcularIr(rendimentoBruto, iof, diasCorridos, opcaoIr);
        return iof.add(ir);
    }

    /**
     * Alias polimórfico para cálculo do imposto total com tipos enumerados.
     *
     * @param rendimentoBruto ganho bruto auferido
     * @param diasCorridos dias corridos transcorridos
     * @param opcaoIof opção de IOF
     * @param opcaoIr opção de IR
     * @return a soma de IOF e IR
     */
    public static BigDecimal calcularImpostoTotal(BigDecimal rendimentoBruto, int diasCorridos, OpcaoIOF opcaoIof, OpcaoIR opcaoIr) {
        Objects.requireNonNull(opcaoIof, "Opção de IOF não pode ser nula.");
        Objects.requireNonNull(opcaoIr, "Opção de IR não pode ser nula.");
        return calcularTotalTributos(rendimentoBruto, diasCorridos, opcaoIof.getCodigo(), opcaoIr.getCodigo());
    }

    /**
     * Sobrecarga de imposto total a partir de inteiros.
     *
     * @param rendimentoBruto ganho bruto auferido
     * @param diasCorridos dias corridos transcorridos
     * @param codigoIof código de IOF (0 a 2)
     * @param codigoIr código de IR (0 ou 1)
     * @return a soma de IOF e IR
     */
    public static BigDecimal calcularImpostoTotal(BigDecimal rendimentoBruto, int diasCorridos, int codigoIof, int codigoIr) {
        return calcularTotalTributos(rendimentoBruto, diasCorridos, codigoIof, codigoIr);
    }

    /**
     * Apura o rendimento líquido resultante após dedução de todos os tributos devidos.
     *
     * @param rendimentoBruto ganho bruto auferido
     * @param diasCorridos dias corridos transcorridos
     * @param opcaoIof opção de IOF
     * @param opcaoIr opção de IR
     * @return rendimento líquido (mínimo de zero)
     */
    public static BigDecimal calcularRendimentoLiquido(BigDecimal rendimentoBruto, int diasCorridos, OpcaoIOF opcaoIof, OpcaoIR opcaoIr) {
        if (rendimentoBruto == null || rendimentoBruto.signum() <= 0) {
            return Moeda.ZERO;
        }
        BigDecimal totalTributos = calcularImpostoTotal(rendimentoBruto, diasCorridos, opcaoIof, opcaoIr);
        return rendimentoBruto.subtract(totalTributos).max(Moeda.ZERO);
    }
}

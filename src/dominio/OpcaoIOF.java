package dominio;

import java.util.Objects;

/**
 * Define as políticas de incidência do Imposto sobre Operações Financeiras (IOF)
 * sobre os rendimentos de aplicações financeiras.
 *
 * <p>Conforme especificação da atividade acadêmica:</p>
 * <ul>
 *   <li>{@link #ISENTO} (0): Isenção total de IOF.</li>
 *   <li>{@link #COM_CARENCIA_30_DIAS} (1): Alíquota de 3,5% incidente nos primeiros 29 dias corridos,
 *       tornando-se isento a partir de 30 dias.</li>
 *   <li>{@link #IRRESTRITO} (2): Alíquota de 3,5% incidente continuamente durante todo o período.</li>
 * </ul>
 */
public enum OpcaoIOF {

    /**
     * Aplicação isenta de retenção de IOF.
     */
    ISENTO(0, "Isento", "Não desconta IOF sobre os rendimentos auferidos."),

    /**
     * Alíquota de 3,5% com carência de 30 dias corridos.
     */
    COM_CARENCIA_30_DIAS(1, "3,5% com carência de 30 dias",
            "Alíquota de 3,5% sobre rendimentos até o 29º dia; isento a partir de 30 dias corridos."),

    /**
     * Alíquota contínua de 3,5% durante todo o prazo da aplicação.
     */
    IRRESTRITO(2, "3,5% irrestrito",
            "Alíquota contínua de 3,5% sobre rendimentos durante todo o prazo da aplicação.");

    private final int codigo;
    private final String descricao;
    private final String detalhe;

    /**
     * Construtor da constante de opção de IOF.
     *
     * @param codigo o código numérico (0, 1 ou 2)
     * @param descricao nome descritivo amigável em português
     * @param detalhe regra detalhada de tributação
     */
    OpcaoIOF(int codigo, String descricao, String detalhe) {
        this.codigo = codigo;
        this.descricao = descricao;
        this.detalhe = detalhe;
    }

    /**
     * Retorna o código numérico da opção de IOF.
     *
     * @return 0 para Isento, 1 para Carência 30d, 2 para Irrestrito
     */
    public int getCodigo() {
        return codigo;
    }

    /**
     * Retorna a descrição amigável da opção em português.
     *
     * @return descrição textual
     */
    public String getDescricao() {
        return descricao;
    }

    /**
     * Retorna a explicação detalhada da regra fiscal.
     *
     * @return detalhamento operacional
     */
    public String getDetalhe() {
        return detalhe;
    }

    /**
     * Converte o código numérico da especificação didática no respectivo tipo enumerado {@link OpcaoIOF}.
     *
     * @param codigo o código inteiro (0, 1 ou 2)
     * @return a constante enumerada correspondente
     * @throws IllegalArgumentException se o código for diferente de 0, 1 e 2
     */
    public static OpcaoIOF doCodigo(int codigo) {
        for (OpcaoIOF opcao : values()) {
            if (opcao.codigo == codigo) {
                return opcao;
            }
        }
        throw new IllegalArgumentException(
                "Código de IOF inválido: " + codigo + ". Os valores válidos são 0 (Isento), 1 (Carência 30d) ou 2 (Irrestrito).");
    }

    /**
     * Converte uma representação textual no respectivo {@link OpcaoIOF}, com tolerância a termos legados.
     *
     * @param texto o identificador textual a ser interpretado
     * @return a constante enumerada correspondente
     * @throws NullPointerException se o texto for nulo
     * @throws IllegalArgumentException se o texto não corresponder a uma opção válida
     */
    public static OpcaoIOF deTexto(String texto) {
        Objects.requireNonNull(texto, "Texto da opção de IOF não pode ser nulo.");
        String normalizado = texto.trim().toUpperCase();
        return switch (normalizado) {
            case "ISENTO", "EXEMPT", "0" -> ISENTO;
            case "COM_CARENCIA_30_DIAS", "THREE_POINT_FIVE_PERCENT_BEFORE_30_DAYS", "CARENCIA", "1" -> COM_CARENCIA_30_DIAS;
            case "IRRESTRITO", "THREE_POINT_FIVE_PERCENT_ALWAYS", "SEMPRE", "2" -> IRRESTRITO;
            default -> throw new IllegalArgumentException(
                    "Opção de IOF não reconhecida para o texto: '" + texto + "'.");
        };
    }

    @Override
    public String toString() {
        return descricao;
    }
}

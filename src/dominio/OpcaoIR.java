package dominio;

import java.util.Objects;

/**
 * Define as políticas de incidência do Imposto de Renda (IR) sobre os rendimentos
 * de aplicações financeiras.
 *
 * <p>Conforme especificação da atividade acadêmica:</p>
 * <ul>
 *   <li>{@link #ISENTO} (0): Isenção total de Imposto de Renda.</li>
 *   <li>{@link #TABELA_REGRESSIVA} (1): Incidência escalonada regressiva conforme prazo de permanência em dias corridos:
 *     <ul>
 *       <li>Até 180 dias: 22,50%</li>
 *       <li>De 181 a 360 dias: 20,00%</li>
 *       <li>De 361 a 720 dias: 17,50%</li>
 *       <li>Acima de 720 dias: 15,00%</li>
 *     </ul>
 *   </li>
 * </ul>
 */
public enum OpcaoIR {

    /**
     * Aplicação isenta de retenção de Imposto de Renda.
     */
    ISENTO(0, "Isento", "Não desconta Imposto de Renda sobre os rendimentos auferidos."),

    /**
     * Aplicação sujeita à tabela regressiva padrão conforme dias corridos de permanência.
     */
    TABELA_REGRESSIVA(1, "Tabela Regressiva",
            "Aplica alíquotas regressivas por tempo de permanência: 22,5% (até 180d), 20% (até 360d), 17,5% (até 720d) e 15% (>720d).");

    private final int codigo;
    private final String descricao;
    private final String detalhe;

    /**
     * Construtor da constante de opção de IR.
     *
     * @param codigo o código numérico (0 ou 1)
     * @param descricao nome descritivo amigável em português
     * @param detalhe detalhamento da tabela fiscal
     */
    OpcaoIR(int codigo, String descricao, String detalhe) {
        this.codigo = codigo;
        this.descricao = descricao;
        this.detalhe = detalhe;
    }

    /**
     * Retorna o código numérico da opção de IR.
     *
     * @return 0 para Isento, 1 para Tabela Regressiva
     */
    public int getCodigo() {
        return codigo;
    }

    /**
     * Retorna a descrição legível da opção em português.
     *
     * @return texto descritivo
     */
    public String getDescricao() {
        return descricao;
    }

    /**
     * Retorna o detalhamento operacional da alíquota regressiva.
     *
     * @return texto com as faixas de tributação
     */
    public String getDetalhe() {
        return detalhe;
    }

    /**
     * Converte o código numérico da especificação didática no respectivo tipo enumerado {@link OpcaoIR}.
     *
     * @param codigo o código numérico (0 ou 1)
     * @return a constante enumerada correspondente
     * @throws IllegalArgumentException se o código for diferente de 0 e 1
     */
    public static OpcaoIR doCodigo(int codigo) {
        for (OpcaoIR opcao : values()) {
            if (opcao.codigo == codigo) {
                return opcao;
            }
        }
        throw new IllegalArgumentException(
                "Código de IR inválido: " + codigo + ". Os valores válidos são 0 (Isento) ou 1 (Tabela Regressiva).");
    }

    /**
     * Converte uma representação textual no respectivo {@link OpcaoIR}, com suporte a termos legados em inglês.
     *
     * @param texto o identificador textual a ser interpretado
     * @return a constante enumerada correspondente
     * @throws NullPointerException se o texto for nulo
     * @throws IllegalArgumentException se o texto não corresponder a uma opção válida
     */
    public static OpcaoIR deTexto(String texto) {
        Objects.requireNonNull(texto, "Texto da opção de IR não pode ser nulo.");
        String normalizado = texto.trim().toUpperCase();
        return switch (normalizado) {
            case "ISENTO", "EXEMPT", "0" -> ISENTO;
            case "TABELA_REGRESSIVA", "REGRESSIVE", "REGRESSIVO", "1" -> TABELA_REGRESSIVA;
            default -> throw new IllegalArgumentException(
                    "Opção de IR não reconhecida para o texto: '" + texto + "'.");
        };
    }

    @Override
    public String toString() {
        return descricao;
    }
}

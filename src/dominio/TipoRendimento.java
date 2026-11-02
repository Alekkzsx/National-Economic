package dominio;

import java.util.Objects;

/**
 * Define a periodicidade de crédito dos rendimentos acumulados na aplicação financeira.
 *
 * <p>Conforme o Item 1 da atividade prática acadêmica, contempla os 6 tipos:</p>
 * <ul>
 *   <li>0 = {@link #DIARIO}: Crédito diário em dias úteis de atualização.</li>
 *   <li>1 = {@link #MENSAL}: Crédito a cada 30 dias corridos.</li>
 *   <li>2 = {@link #TRIMESTRAL}: Crédito a cada 90 dias corridos.</li>
 *   <li>3 = {@link #SEMESTRAL}: Crédito a cada 180 dias corridos.</li>
 *   <li>4 = {@link #ANUAL}: Crédito a cada 365 dias corridos.</li>
 *   <li>5 = {@link #NO_PRAZO}: Crédito acumulado apenas no vencimento final da aplicação.</li>
 * </ul>
 */
public enum TipoRendimento {

    /** Rendimento creditado diariamente a cada dia útil. */
    DIARIO(0, 1, "Diário", "Credita o rendimento em cada atualização de dia útil."),

    /** Rendimento creditado mensalmente a cada ciclo de 30 dias corridos. */
    MENSAL(1, 30, "Mensal", "Credita o rendimento a cada ciclo de 30 dias corridos."),

    /** Rendimento creditado trimestralmente a cada ciclo de 90 dias corridos. */
    TRIMESTRAL(2, 90, "Trimestral", "Credita o rendimento a cada ciclo de 90 dias corridos."),

    /** Rendimento creditado semestralmente a cada ciclo de 180 dias corridos. */
    SEMESTRAL(3, 180, "Semestral", "Credita o rendimento a cada ciclo de 180 dias corridos."),

    /** Rendimento creditado anualmente a cada ciclo de 365 dias corridos. */
    ANUAL(4, 365, "Anual", "Credita o rendimento a cada ciclo de 365 dias corridos."),

    /** Rendimento acumulado e creditado apenas no término do prazo de vencimento. */
    NO_PRAZO(5, 0, "No Prazo", "Credita o rendimento acumulado apenas no vencimento final da aplicação.");

    private final int codigo;
    private final int intervaloDias;
    private final String descricao;
    private final String detalhe;

    /**
     * Construtor da constante de periodicidade de rendimento.
     *
     * @param codigo o código inteiro da especificação didática (0 a 5)
     * @param intervaloDias o intervalo base em dias corridos (ou 0 para no prazo)
     * @param descricao nome legível em português do Brasil
     * @param detalhe regra de crédito dos rendimentos
     */
    TipoRendimento(int codigo, int intervaloDias, String descricao, String detalhe) {
        this.codigo = codigo;
        this.intervaloDias = intervaloDias;
        this.descricao = descricao;
        this.detalhe = detalhe;
    }

    /**
     * Retorna o código numérico da periodicidade de rendimento (0 a 5).
     *
     * @return o código de 0 a 5
     */
    public int getCodigo() {
        return codigo;
    }

    /**
     * Retorna o intervalo em dias corridos para incorporação de rendimentos.
     *
     * @return quantidade de dias corridos do ciclo, ou 0 se for no vencimento
     */
    public int getIntervaloDias() {
        return intervaloDias;
    }

    /**
     * Alias de compatibilidade com classes legadas.
     *
     * @return quantidade de dias corridos do ciclo
     */
    public int days() {
        return intervaloDias;
    }

    /**
     * Retorna a descrição legível da periodicidade.
     *
     * @return nome descritivo
     */
    public String getDescricao() {
        return descricao;
    }

    /**
     * Retorna o detalhamento operacional da periodicidade de crédito.
     *
     * @return explicação detalhada
     */
    public String getDetalhe() {
        return detalhe;
    }

    /**
     * Converte o código numérico no respectivo tipo enumerado {@link TipoRendimento}.
     *
     * @param codigo o código inteiro de 0 a 5
     * @return a constante enumerada correspondente
     * @throws IllegalArgumentException se o código estiver fora do intervalo 0 a 5
     */
    public static TipoRendimento doCodigo(int codigo) {
        for (TipoRendimento tipo : values()) {
            if (tipo.codigo == codigo) {
                return tipo;
            }
        }
        throw new IllegalArgumentException(
                "Código de tipo de rendimento inválido: " + codigo + ". Deve ser um inteiro de 0 a 5.");
    }

    /**
     * Converte uma representação textual no respectivo {@link TipoRendimento}, com suporte a termos legados em inglês.
     *
     * @param texto o identificador textual a ser interpretado
     * @return a constante enumerada correspondente
     * @throws NullPointerException se o texto for nulo
     * @throws IllegalArgumentException se o texto não corresponder a um tipo válido
     */
    public static TipoRendimento deTexto(String texto) {
        Objects.requireNonNull(texto, "Texto do tipo de rendimento não pode ser nulo.");
        String normalizado = texto.trim().toUpperCase();
        return switch (normalizado) {
            case "DIARIO", "DIÁRIO", "DAILY", "0" -> DIARIO;
            case "MENSAL", "MONTHLY", "MONTHLY_30_DAYS", "1" -> MENSAL;
            case "TRIMESTRAL", "QUARTERLY", "QUARTERLY_90_DAYS", "2" -> TRIMESTRAL;
            case "SEMESTRAL", "HALF_YEARLY", "HALF_YEARLY_180_DAYS", "3" -> SEMESTRAL;
            case "ANUAL", "YEARLY", "YEARLY_365_DAYS", "4" -> ANUAL;
            case "NO_PRAZO", "NO_VENCIMENTO", "AT_MATURITY", "5" -> NO_PRAZO;
            default -> throw new IllegalArgumentException(
                    "Tipo de rendimento não reconhecido para o texto: '" + texto + "'.");
        };
    }

    @Override
    public String toString() {
        return descricao;
    }
}

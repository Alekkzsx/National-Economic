package dominio;

import java.util.Objects;

/**
 * Define a frequência mínima de intervalo entre resgates autorizados em uma aplicação financeira.
 *
 * <p>Conforme o Item 1 da especificação acadêmica, contempla os 5 tipos:</p>
 * <ul>
 *   <li>0 = {@link #DIARIO}: Resgate diário (intervalo de 1 dia).</li>
 *   <li>1 = {@link #MENSAL}: Resgates em intervalos mínimos de 30 dias corridos.</li>
 *   <li>2 = {@link #TRIMESTRAL}: Resgates em intervalos mínimos de 90 dias corridos.</li>
 *   <li>3 = {@link #SEMESTRAL}: Resgates em intervalos mínimos de 180 dias corridos.</li>
 *   <li>4 = {@link #ANUAL}: Resgates em intervalos mínimos de 365 dias corridos.</li>
 * </ul>
 */
public enum TipoResgate {

    /** Resgates permitidos diariamente, respeitado o prazo de carência mínima. */
    DIARIO(0, 1, "Diário", "Resgates permitidos diariamente, respeitado o prazo de permanência mínima."),

    /** Resgates autorizados em intervalos mínimos de 30 dias corridos. */
    MENSAL(1, 30, "Mensal", "Resgates autorizados em intervalos mínimos de 30 dias corridos."),

    /** Resgates autorizados em intervalos mínimos de 90 dias corridos. */
    TRIMESTRAL(2, 90, "Trimestral", "Resgates autorizados em intervalos mínimos de 90 dias corridos."),

    /** Resgates autorizados em intervalos mínimos de 180 dias corridos. */
    SEMESTRAL(3, 180, "Semestral", "Resgates autorizados em intervalos mínimos de 180 dias corridos."),

    /** Resgates autorizados em intervalos mínimos de 365 dias corridos. */
    ANUAL(4, 365, "Anual", "Resgates autorizados em intervalos mínimos de 365 dias corridos.");

    private final int codigo;
    private final int intervaloDias;
    private final String descricao;
    private final String detalhe;

    /**
     * Construtor da constante de intervalo de resgate.
     *
     * @param codigo o código da especificação didática (0 a 4)
     * @param intervaloDias o intervalo em dias corridos
     * @param descricao nome amigável em português do Brasil
     * @param detalhe regra de restrição de resgate
     */
    TipoResgate(int codigo, int intervaloDias, String descricao, String detalhe) {
        this.codigo = codigo;
        this.intervaloDias = intervaloDias;
        this.descricao = descricao;
        this.detalhe = detalhe;
    }

    /**
     * Retorna o código numérico do tipo de resgate (0 a 4).
     *
     * @return código de 0 a 4
     */
    public int getCodigo() {
        return codigo;
    }

    /**
     * Retorna a quantidade de dias corridos entre datas autorizadas de resgate.
     *
     * @return intervalo de dias
     */
    public int getIntervaloDias() {
        return intervaloDias;
    }

    /**
     * Alias de compatibilidade com classes legadas.
     *
     * @return intervalo em dias corridos
     */
    public int days() {
        return intervaloDias;
    }

    /**
     * Retorna a descrição legível do tipo de resgate.
     *
     * @return nome textual
     */
    public String getDescricao() {
        return descricao;
    }

    /**
     * Retorna o detalhamento operacional da periodicidade de resgate.
     *
     * @return detalhe explicativo
     */
    public String getDetalhe() {
        return detalhe;
    }

    /**
     * Converte o código numérico no respectivo tipo enumerado {@link TipoResgate}.
     *
     * @param codigo o código de 0 a 4
     * @return a constante enumerada correspondente
     * @throws IllegalArgumentException se o código for menor que 0 ou maior que 4
     */
    public static TipoResgate doCodigo(int codigo) {
        for (TipoResgate tipo : values()) {
            if (tipo.codigo == codigo) {
                return tipo;
            }
        }
        throw new IllegalArgumentException(
                "Código de tipo de resgate inválido: " + codigo + ". Deve ser um inteiro de 0 a 4.");
    }

    /**
     * Converte uma representação textual no respectivo {@link TipoResgate}, com suporte a termos legados em inglês.
     *
     * @param texto o identificador textual a ser interpretado
     * @return a constante enumerada correspondente
     * @throws NullPointerException se o texto for nulo
     * @throws IllegalArgumentException se o texto não corresponder a um tipo válido
     */
    public static TipoResgate deTexto(String texto) {
        Objects.requireNonNull(texto, "Texto do tipo de resgate não pode ser nulo.");
        String normalizado = texto.trim().toUpperCase();
        return switch (normalizado) {
            case "DIARIO", "DIÁRIO", "DAILY", "0" -> DIARIO;
            case "MENSAL", "MONTHLY", "1" -> MENSAL;
            case "TRIMESTRAL", "QUARTERLY", "2" -> TRIMESTRAL;
            case "SEMESTRAL", "HALF_YEARLY", "3" -> SEMESTRAL;
            case "ANUAL", "YEARLY", "4" -> ANUAL;
            default -> throw new IllegalArgumentException(
                    "Tipo de resgate não reconhecido para o texto: '" + texto + "'.");
        };
    }

    @Override
    public String toString() {
        return descricao;
    }
}

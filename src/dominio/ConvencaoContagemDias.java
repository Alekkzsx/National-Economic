package dominio;

import java.util.Objects;

/**
 * Define a convenção de contagem de dias utilizada no cálculo proporcional de taxas de juros.
 *
 * <p>Convenções disponíveis:</p>
 * <ul>
 *   <li>{@link #COMERCIAL_30_360}: Ano comercial de 360 dias com meses de 30 dias (padrão da atividade didática).</li>
 *   <li>{@link #CIVIL_365}: Ano civil exato de 365 dias.</li>
 *   <li>{@link #EFETIVO_360}: Dias corridos efetivos sobre base anual de 360 dias.</li>
 * </ul>
 */
public enum ConvencaoContagemDias {

    /** Ano comercial de 360 dias com meses comerciais fixos de 30 dias. */
    COMERCIAL_30_360(360, "Comercial 30/360", "Ano comercial com 360 dias e meses comerciais de 30 dias."),

    /** Ano civil de 365 dias corridos. */
    CIVIL_365(365, "Civil / 365", "Ano civil de 365 dias, sem compensação de anos bissextos."),

    /** Contagem efetiva de dias sobre base de 360 dias. */
    EFETIVO_360(360, "Efetivo / 360", "Contagem de dias reais corridos projetada sobre base de 360 dias anuais.");

    private final int diasPorAno;
    private final String descricao;
    private final String detalhe;

    /**
     * Construtor da convenção de contagem de dias.
     *
     * @param diasPorAno base anual em dias
     * @param descricao nome amigável da convenção
     * @param detalhe regra de cálculo de dias
     */
    ConvencaoContagemDias(int diasPorAno, String descricao, String detalhe) {
        this.diasPorAno = diasPorAno;
        this.descricao = descricao;
        this.detalhe = detalhe;
    }

    /**
     * Retorna a quantidade de dias considerados no ano financeiro.
     *
     * @return base anual de dias (360 ou 365)
     */
    public int getDiasPorAno() {
        return diasPorAno;
    }

    /**
     * Alias de compatibilidade com classes legadas.
     *
     * @return base anual de dias
     */
    public int daysPerYear() {
        return diasPorAno;
    }

    /**
     * Retorna a descrição legível da convenção.
     *
     * @return nome da convenção
     */
    public String getDescricao() {
        return descricao;
    }

    /**
     * Retorna o detalhamento operacional da convenção.
     *
     * @return texto descritivo
     */
    public String getDetalhe() {
        return detalhe;
    }

    /**
     * Converte uma representação textual no respectivo {@link ConvencaoContagemDias}.
     *
     * @param texto o identificador textual a ser interpretado
     * @return a constante enumerada correspondente
     * @throws NullPointerException se o texto for nulo
     * @throws IllegalArgumentException se o texto não corresponder a uma convenção válida
     */
    public static ConvencaoContagemDias deTexto(String texto) {
        Objects.requireNonNull(texto, "Texto da convenção não pode ser nulo.");
        String normalizado = texto.trim().toUpperCase();
        return switch (normalizado) {
            case "COMERCIAL_30_360", "THIRTY_360", "30/360", "COMERCIAL" -> COMERCIAL_30_360;
            case "CIVIL_365", "ACTUAL_365", "365", "CIVIL" -> CIVIL_365;
            case "EFETIVO_360", "ACTUAL_360", "EFETIVO" -> EFETIVO_360;
            default -> throw new IllegalArgumentException(
                    "Convenção de contagem de dias não reconhecida para o texto: '" + texto + "'.");
        };
    }

    @Override
    public String toString() {
        return descricao;
    }
}

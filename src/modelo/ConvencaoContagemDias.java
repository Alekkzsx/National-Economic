package modelo;

/**
 * Convenções de dias usadas ao converter a taxa mensal em taxa proporcional
 * diária. O valor indica a quantidade de dias que representa um ano financeiro.
 */
public enum ConvencaoContagemDias {
    /** Ano comercial de 360 dias e meses de 30 dias. */
    THIRTY_360(360),
    /** Ano civil de 365 dias, sem ajuste para ano bissexto. */
    ACTUAL_365(365),
    /** Dias corridos reais sobre ano financeiro de 360 dias. */
    ACTUAL_360(360);

    private final int daysPerYear;

    /** Associa a convenção à sua base anual de cálculo. */
    ConvencaoContagemDias(int daysPerYear) { this.daysPerYear = daysPerYear; }

    /** Retorna a base anual empregada no cálculo da taxa diária. */
    public int diasPorAno() { return daysPerYear; }
}

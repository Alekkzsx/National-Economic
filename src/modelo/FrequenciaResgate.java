package modelo;

/**
 * Intervalo mínimo entre datas de resgate, medido em dias corridos desde a
 * aplicação. Não representa dias úteis nem feriados bancários.
 */
public enum FrequenciaResgate {
    /** Resgates permitidos diariamente, desde que a carência esteja cumprida. */
    DAILY(1),
    /** Resgates em intervalos de 30 dias. */
    MONTHLY(30),
    /** Resgates em intervalos de 90 dias. */
    QUARTERLY(90),
    /** Resgates em intervalos de 180 dias. */
    HALF_YEARLY(180),
    /** Resgates em intervalos de 365 dias. */
    YEARLY(365);

    private final int diasIntervalo;

    /** Associa a frequência ao intervalo que ela representa. */
    FrequenciaResgate(int diasIntervalo) {
        this.diasIntervalo = diasIntervalo;
    }

    /** Retorna o intervalo em dias corridos. */
    public int dias() {
        return diasIntervalo;
    }
}

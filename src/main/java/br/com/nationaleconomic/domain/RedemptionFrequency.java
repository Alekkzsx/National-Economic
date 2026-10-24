package br.com.nationaleconomic.domain;

/** Frequência mínima entre datas permitidas para resgate. */
public enum RedemptionFrequency {
    DAILY(1),
    MONTHLY(30),
    QUARTERLY(90),
    HALF_YEARLY(180),
    YEARLY(365);

    private final int days;

    RedemptionFrequency(int days) {
        this.days = days;
    }

    public int days() {
        return days;
    }
}

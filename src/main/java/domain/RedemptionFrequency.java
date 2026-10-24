package domain;

/** Frequência entre datas permitidas para resgate, medida em dias corridos. */
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

package domain;

/** Periodicidade de crédito dos rendimentos, medida em dias corridos desde a aplicação. */
public enum YieldPeriod {
    DAILY(1),
    MONTHLY_30_DAYS(30),
    QUARTERLY_90_DAYS(90),
    HALF_YEARLY_180_DAYS(180),
    YEARLY_365_DAYS(365),
    AT_MATURITY(0);

    private final int days;

    YieldPeriod(int days) {
        this.days = days;
    }

    public int days() {
        return days;
    }
}

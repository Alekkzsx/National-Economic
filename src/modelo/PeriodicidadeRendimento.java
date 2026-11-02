package modelo;

/**
 * Periodicidade para creditar os rendimentos pendentes no saldo da aplicação.
 * Os intervalos são dias corridos; o crédito efetivo só ocorre durante a
 * atualização processada em dia útil.
 */
public enum PeriodicidadeRendimento {
    /** Credita o rendimento acumulado em cada dia de atualização útil. */
    DAILY(1),
    /** Credita o rendimento a cada 30 dias corridos. */
    MONTHLY_30_DAYS(30),
    /** Credita o rendimento a cada 90 dias corridos. */
    QUARTERLY_90_DAYS(90),
    /** Credita o rendimento a cada 180 dias corridos. */
    HALF_YEARLY_180_DAYS(180),
    /** Credita o rendimento a cada 365 dias corridos. */
    YEARLY_365_DAYS(365),
    /** Credita o rendimento somente ao alcançar o vencimento configurado. */
    AT_MATURITY(0);

    private final int days;

    /** Associa o período ao intervalo em dias; vencimento usa sua data configurada. */
    PeriodicidadeRendimento(int days) {
        this.days = days;
    }

    /** Retorna os dias do intervalo, ou zero para {@link #AT_MATURITY}. */
    public int dias() {
        return days;
    }
}

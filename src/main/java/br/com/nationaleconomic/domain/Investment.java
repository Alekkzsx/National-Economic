package br.com.nationaleconomic.domain;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

public class Investment {
    private static final int MONEY_SCALE = 2;
    private static final MathContext RATE_CONTEXT = MathContext.DECIMAL128;
    private String name;
    private String number;
    private BigDecimal initialAmount;
    private BigDecimal balance;
    private BigDecimal monthlyRate;
    private BigDecimal accruedEarnings = BigDecimal.ZERO.setScale(MONEY_SCALE);
    private BigDecimal pendingEarnings = BigDecimal.ZERO.setScale(MONEY_SCALE);
    private DayCountConvention dayCountConvention = DayCountConvention.THIRTY_360;
    private TaxRegime taxRegime = TaxRegime.STANDARD;
    private YieldPeriod yieldPeriod = YieldPeriod.DAILY;
    private int maturityDays;
    private int minimumStayDays;
    private RedemptionFrequency redemptionFrequency = RedemptionFrequency.DAILY;
    private IofOption iofOption = IofOption.THREE_POINT_FIVE_PERCENT_BEFORE_30_DAYS;
    private IrOption irOption = IrOption.REGRESSIVE;
    private LocalDate applicationDate;
    private LocalDate lastUpdateDate;
    private int elapsedDays;
    private int applicationDays;
    private int daysSinceYield;
    private int operationCount;

    public Investment(String name, String number, BigDecimal initialAmount, BigDecimal monthlyRate) {
        this(name, number, initialAmount, monthlyRate, LocalDate.now());
    }

    public Investment(String name, String number, BigDecimal initialAmount, BigDecimal monthlyRate,
                      LocalDate applicationDate) {
        setName(name);
        setNumber(number);
        setInitialAmount(initialAmount);
        setMonthlyRate(monthlyRate);
        setApplicationDate(applicationDate);
        this.balance = this.initialAmount;
    }

    public String getName() { return name; }
    public String getNumber() { return number; }
    public BigDecimal getInitialAmount() { return initialAmount; }
    public BigDecimal getMonthlyRate() { return monthlyRate; }
    public BigDecimal getBalance() { return getGrossBalance(); }
    public BigDecimal getCreditedBalance() { return balance; }
    public BigDecimal getGrossBalance() { return balance.add(pendingEarnings).setScale(MONEY_SCALE, RoundingMode.HALF_UP); }
    public BigDecimal getNetBalance() { return getGrossBalance().subtract(getTaxes()).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP); }
    public BigDecimal getGrossEarnings() { return accruedEarnings.add(pendingEarnings).setScale(MONEY_SCALE, RoundingMode.HALF_UP); }
    public BigDecimal getCreditedEarnings() { return accruedEarnings; }
    public BigDecimal getNetEarnings() { return getGrossEarnings().subtract(getTaxes()).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP); }
    public BigDecimal getIof() { return taxRegime == TaxRegime.TAX_FREE ? moneyZero() : getGrossEarnings().multiply(iofRate()).setScale(2, RoundingMode.HALF_UP); }
    public BigDecimal getIr() { return taxRegime == TaxRegime.TAX_FREE ? moneyZero() : getGrossEarnings().subtract(getIof()).multiply(irRate()).setScale(2, RoundingMode.HALF_UP); }
    public BigDecimal getTaxes() { return getIof().add(getIr()).setScale(2, RoundingMode.HALF_UP); }
    public int getElapsedDays() { return elapsedDays; }
    public int getApplicationDays() { return applicationDays; }
    public int getOperationCount() { return operationCount; }
    public BigDecimal getPendingEarnings() { return pendingEarnings; }
    public int getDaysSinceYield() { return daysSinceYield; }
    public LocalDate getApplicationDate() { return applicationDate; }
    public LocalDate getLastUpdateDate() { return lastUpdateDate; }
    public DayCountConvention getDayCountConvention() { return dayCountConvention; }
    public TaxRegime getTaxRegime() { return taxRegime; }
    public YieldPeriod getYieldPeriod() { return yieldPeriod; }
    public int getMaturityDays() { return maturityDays; }
    public int getMinimumStayDays() { return minimumStayDays; }
    public RedemptionFrequency getRedemptionFrequency() { return redemptionFrequency; }
    public IofOption getIofOption() { return iofOption; }
    public IrOption getIrOption() { return irOption; }

    public void setName(String name) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Nome do investimento obrigatório.");
        this.name = name.trim();
    }

    public void setNumber(String number) {
        if (number == null || number.isBlank()) throw new IllegalArgumentException("Número do investimento obrigatório.");
        this.number = number.trim();
    }

    public void setInitialAmount(BigDecimal amount) {
        BigDecimal value = positiveMoney(amount, "Valor inicial");
        if (balance != null) {
            if (operationCount > 0 || accruedEarnings.signum() > 0 || pendingEarnings.signum() > 0) {
                throw new IllegalStateException("Não é possível alterar o aporte inicial após movimentação ou rendimento.");
            }
            balance = balance.add(value.subtract(initialAmount));
        } else {
            balance = value;
        }
        this.initialAmount = value;
    }

    public void setMonthlyRate(BigDecimal monthlyRate) {
        Objects.requireNonNull(monthlyRate, "Taxa mensal obrigatória.");
        if (monthlyRate.signum() < 0 || monthlyRate.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("A taxa mensal deve estar entre 0 e 1 (inclusive).");
        }
        this.monthlyRate = monthlyRate;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = nonNegativeMoney(balance, "Saldo");
    }

    public void setDayCountConvention(DayCountConvention convention) {
        this.dayCountConvention = Objects.requireNonNull(convention, "Convenção de dias obrigatória.");
    }

    public void setTaxRegime(TaxRegime regime) {
        this.taxRegime = Objects.requireNonNull(regime, "Regime tributário obrigatório.");
        if (regime == TaxRegime.TAX_FREE) {
            iofOption = IofOption.EXEMPT;
            irOption = IrOption.EXEMPT;
        } else {
            iofOption = IofOption.THREE_POINT_FIVE_PERCENT_BEFORE_30_DAYS;
            irOption = IrOption.REGRESSIVE;
        }
    }

    public void setYieldPeriod(YieldPeriod period) {
        Objects.requireNonNull(period, "Periodicidade de rendimento obrigatória.");
        if (period == YieldPeriod.AT_MATURITY && maturityDays <= 0) {
            throw new IllegalArgumentException("Configure os dias até o vencimento antes de usar rendimento no vencimento.");
        }
        this.yieldPeriod = period;
    }

    public void setMaturityDays(int days) {
        if (days < 0) throw new IllegalArgumentException("Dias até o vencimento não podem ser negativos.");
        if (minimumStayDays > days && days > 0) {
            throw new IllegalArgumentException("Vencimento não pode ser anterior à permanência mínima.");
        }
        if (yieldPeriod == YieldPeriod.AT_MATURITY && days == 0) {
            throw new IllegalArgumentException("Dias até o vencimento devem ser positivos para rendimento no vencimento.");
        }
        maturityDays = days;
    }

    public void setMinimumStayDays(int days) {
        if (days < 0) throw new IllegalArgumentException("Permanência mínima não pode ser negativa.");
        if (maturityDays > 0 && days > maturityDays) {
            throw new IllegalArgumentException("Permanência mínima não pode exceder o vencimento.");
        }
        minimumStayDays = days;
    }

    public void setRedemptionFrequency(RedemptionFrequency frequency) {
        this.redemptionFrequency = Objects.requireNonNull(frequency, "Frequência de resgate obrigatória.");
    }

    public void setIofOption(IofOption option) {
        this.iofOption = Objects.requireNonNull(option, "Opção de IOF obrigatória.");
        syncLegacyTaxRegime();
    }

    public void setIrOption(IrOption option) {
        this.irOption = Objects.requireNonNull(option, "Opção de IR obrigatória.");
        syncLegacyTaxRegime();
    }

    public void setApplicationDate(LocalDate date) {
        this.applicationDate = Objects.requireNonNull(date, "Data de aplicação obrigatória.");
        if (lastUpdateDate == null) lastUpdateDate = date;
    }

    /** Acrescenta um dia corrido ao contador do investimento. */
    public void incrementarDia() {
        elapsedDays++;
        daysSinceYield++;
    }

    /** Acrescenta um dia de aplicação; usado apenas para dias úteis. */
    public void incrementarDiaAplicacao() { applicationDays++; }

    public void incrementDay() { incrementarDia(); }
    public void incrementApplicationDay() { incrementarDiaAplicacao(); }

    public boolean isMatured() {
        return maturityDays > 0 && elapsedDays >= maturityDays;
    }

    /** Verifica carência e frequência de resgate na posição atual da aplicação. */
    public boolean canRedeem() {
        return elapsedDays >= minimumStayDays && isRedemptionDate();
    }

    /** Indica quando o rendimento acumulado é creditado e passa a compor o saldo. */
    public boolean isYieldDate() {
        return switch (yieldPeriod) {
            case DAILY -> daysSinceYield >= 1;
            case MONTHLY_30_DAYS, QUARTERLY_90_DAYS, HALF_YEARLY_180_DAYS, YEARLY_365_DAYS ->
                    daysSinceYield >= yieldPeriod.days();
            case AT_MATURITY -> isMatured();
        };
    }

    private boolean isYieldDue() {
        return yieldPeriod == YieldPeriod.AT_MATURITY ? isMatured() : isYieldDate();
    }

    private void creditPendingEarnings() {
        if (pendingEarnings.signum() > 0) {
            balance = balance.add(pendingEarnings).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            accruedEarnings = accruedEarnings.add(pendingEarnings).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            pendingEarnings = moneyZero();
        }
        if (yieldPeriod != YieldPeriod.AT_MATURITY) {
            daysSinceYield %= yieldPeriod.days();
        }
    }

    private boolean isRedemptionDate() {
        int interval = redemptionFrequency.days();
        return interval == 1 || (elapsedDays > 0 && elapsedDays % interval == 0);
    }

    private boolean canRedeemOn(LocalDate date) {
        long days = ChronoUnit.DAYS.between(applicationDate, date);
        int interval = redemptionFrequency.days();
        return days >= minimumStayDays && (interval == 1 || (days > 0 && days % interval == 0));
    }

    /**
     * Processa dias corridos e acumula juros em dias úteis. O rendimento pendente
     * só é incorporado ao saldo conforme a periodicidade de crédito configurada.
     */
    public void update(LocalDate date) {
        Objects.requireNonNull(date, "Data obrigatória.");
        if (date.isBefore(applicationDate)) throw new IllegalArgumentException("Data anterior à aplicação.");
        if (!isBusinessDay(date)) return;
        if (lastUpdateDate != null && !date.isAfter(lastUpdateDate)) return;

        LocalDate day = lastUpdateDate == null ? date : lastUpdateDate.plusDays(1);
        while (!day.isAfter(date)) {
            incrementarDia();
            if (isBusinessDay(day)) {
                incrementarDiaAplicacao();
                if (!day.isBefore(applicationDate)) {
                    if (maturityDays == 0 || elapsedDays <= maturityDays) accrueOneBusinessDay();
                    if (isYieldDue()) creditPendingEarnings();
                }
            }
            day = day.plusDays(1);
        }
        lastUpdateDate = date;
    }

    public void apply(FinancialOperation operation) {
        Objects.requireNonNull(operation, "Operação obrigatória.");
        if (operationCount >= 100) throw new IllegalStateException("Limite de 100 operações por aplicação atingido.");
        if (operation.date().isBefore(applicationDate)) throw new IllegalArgumentException("Operação anterior à aplicação.");
        BigDecimal amount = operation.amount().setScale(2, RoundingMode.HALF_UP);
        if (operation.type() == OperationType.DEPOSIT) {
            balance = balance.add(amount).setScale(2, RoundingMode.HALF_UP);
        } else {
            if (!canRedeemOn(operation.date())) {
                throw new IllegalArgumentException("Resgate fora da carência ou da frequência permitida.");
            }
            if (amount.compareTo(balance) > 0) throw new IllegalArgumentException("Saldo insuficiente.");
            balance = balance.subtract(amount).setScale(2, RoundingMode.HALF_UP);
        }
        operationCount++;
    }

    public void restoreState(BigDecimal balance, BigDecimal accruedEarnings, LocalDate lastUpdateDate,
                             int elapsedDays, int applicationDays, int operationCount) {
        restoreState(balance, accruedEarnings, lastUpdateDate, elapsedDays, applicationDays, operationCount,
                YieldPeriod.DAILY, 0, 0, RedemptionFrequency.DAILY, iofOption, irOption);
    }

    public void restoreState(BigDecimal balance, BigDecimal accruedEarnings, LocalDate lastUpdateDate,
                             int elapsedDays, int applicationDays, int operationCount, YieldPeriod yieldPeriod,
                             int maturityDays, int minimumStayDays, RedemptionFrequency redemptionFrequency,
                             IofOption iofOption, IrOption irOption) {
        restoreState(balance, accruedEarnings, lastUpdateDate, elapsedDays, applicationDays, operationCount,
                yieldPeriod, maturityDays, minimumStayDays, redemptionFrequency, iofOption, irOption,
                BigDecimal.ZERO, elapsedDays % (yieldPeriod == YieldPeriod.AT_MATURITY
                        ? Math.max(1, elapsedDays + 1) : Math.max(1, yieldPeriod.days())));
    }

    public void restoreState(BigDecimal balance, BigDecimal accruedEarnings, LocalDate lastUpdateDate,
                             int elapsedDays, int applicationDays, int operationCount, YieldPeriod yieldPeriod,
                             int maturityDays, int minimumStayDays, RedemptionFrequency redemptionFrequency,
                             IofOption iofOption, IrOption irOption, BigDecimal pendingEarnings,
                             int daysSinceYield) {
        this.balance = nonNegativeMoney(balance, "Saldo");
        this.accruedEarnings = nonNegativeMoney(accruedEarnings, "Rendimento");
        this.pendingEarnings = nonNegativeMoney(pendingEarnings, "Rendimento pendente");
        this.lastUpdateDate = Objects.requireNonNull(lastUpdateDate, "Data de atualização obrigatória.");
        if (lastUpdateDate.isBefore(applicationDate)) throw new IllegalArgumentException("Atualização anterior à aplicação.");
        if (elapsedDays < 0 || applicationDays < 0 || operationCount < 0 || operationCount > 100
                || daysSinceYield < 0) {
            throw new IllegalArgumentException("Contadores inválidos.");
        }
        this.elapsedDays = elapsedDays;
        this.applicationDays = applicationDays;
        this.operationCount = operationCount;
        this.daysSinceYield = daysSinceYield;
        this.maturityDays = 0;
        this.minimumStayDays = 0;
        this.yieldPeriod = YieldPeriod.DAILY;
        this.redemptionFrequency = RedemptionFrequency.DAILY;
        this.iofOption = IofOption.THREE_POINT_FIVE_PERCENT_BEFORE_30_DAYS;
        this.irOption = IrOption.REGRESSIVE;
        setMaturityDays(maturityDays);
        setMinimumStayDays(minimumStayDays);
        setYieldPeriod(yieldPeriod);
        setRedemptionFrequency(redemptionFrequency);
        setIofOption(iofOption);
        setIrOption(irOption);
        if (this.yieldPeriod != YieldPeriod.AT_MATURITY && this.daysSinceYield >= this.yieldPeriod.days()) {
            throw new IllegalArgumentException("Contador de periodicidade inválido.");
        }
    }

    public boolean isBusinessDay(LocalDate date) {
        return date.getDayOfWeek() != DayOfWeek.SATURDAY && date.getDayOfWeek() != DayOfWeek.SUNDAY;
    }

    /** Mapeamento pedido: sábado=0, domingo=1, segunda=2, ... sexta=6. */
    public static int calendarDayIndex(LocalDate date) {
        return switch (Objects.requireNonNull(date).getDayOfWeek()) {
            case SATURDAY -> 0;
            case SUNDAY -> 1;
            case MONDAY -> 2;
            case TUESDAY -> 3;
            case WEDNESDAY -> 4;
            case THURSDAY -> 5;
            case FRIDAY -> 6;
        };
    }

    private void accrueOneBusinessDay() {
        if (balance.signum() == 0) return;
        BigDecimal annualizedRate = monthlyRate.multiply(BigDecimal.valueOf(12), RATE_CONTEXT);
        BigDecimal dailyRate = annualizedRate.divide(
                BigDecimal.valueOf(dayCountConvention.daysPerYear()), RATE_CONTEXT);
        BigDecimal earnings = balance.multiply(dailyRate, RATE_CONTEXT).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        if (earnings.signum() > 0) {
            pendingEarnings = pendingEarnings.add(earnings).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        }
    }

    private BigDecimal iofRate() {
        return switch (iofOption) {
            case EXEMPT -> BigDecimal.ZERO;
            case THREE_POINT_FIVE_PERCENT_BEFORE_30_DAYS ->
                    elapsedDays < 30 ? new BigDecimal("0.035") : BigDecimal.ZERO;
            case THREE_POINT_FIVE_PERCENT_ALWAYS -> new BigDecimal("0.035");
        };
    }

    private BigDecimal irRate() {
        if (irOption == IrOption.EXEMPT) return BigDecimal.ZERO;
        if (elapsedDays <= 180) return new BigDecimal("0.225");
        if (elapsedDays <= 360) return new BigDecimal("0.20");
        if (elapsedDays <= 720) return new BigDecimal("0.175");
        return new BigDecimal("0.15");
    }

    private static BigDecimal positiveMoney(BigDecimal value, String field) {
        Objects.requireNonNull(value, field + " obrigatório.");
        BigDecimal money = value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        if (money.signum() <= 0) throw new IllegalArgumentException(field + " deve ser positivo.");
        return money;
    }

    private static BigDecimal nonNegativeMoney(BigDecimal value, String field) {
        Objects.requireNonNull(value, field + " obrigatório.");
        BigDecimal money = value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        if (money.signum() < 0) throw new IllegalArgumentException(field + " não pode ser negativo.");
        return money;
    }

    private static BigDecimal moneyZero() { return BigDecimal.ZERO.setScale(MONEY_SCALE); }

    private void syncLegacyTaxRegime() {
        taxRegime = iofOption == IofOption.EXEMPT && irOption == IrOption.EXEMPT
                ? TaxRegime.TAX_FREE : TaxRegime.STANDARD;
    }
}

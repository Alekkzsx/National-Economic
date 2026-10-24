package br.com.nationaleconomic.domain;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Objects;

public class Investment {
    private static final int MONEY_SCALE = 2;
    private static final MathContext RATE_CONTEXT = MathContext.DECIMAL128;
    private static final BigDecimal THIRTY = BigDecimal.valueOf(30);

    private String name;
    private String number;
    private BigDecimal initialAmount;
    private BigDecimal balance;
    private BigDecimal monthlyRate;
    private BigDecimal accruedEarnings = BigDecimal.ZERO.setScale(MONEY_SCALE);
    private DayCountConvention dayCountConvention = DayCountConvention.THIRTY_360;
    private TaxRegime taxRegime = TaxRegime.STANDARD;
    private LocalDate applicationDate;
    private LocalDate lastUpdateDate;
    private int elapsedDays;
    private int applicationDays;
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
    public BigDecimal getBalance() { return balance; }
    public BigDecimal getGrossBalance() { return balance; }
    public BigDecimal getNetBalance() { return balance.subtract(getTaxes()).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP); }
    public BigDecimal getGrossEarnings() { return accruedEarnings; }
    public BigDecimal getNetEarnings() { return accruedEarnings.subtract(getTaxes()).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP); }
    public BigDecimal getIof() { return taxRegime == TaxRegime.TAX_FREE ? moneyZero() : accruedEarnings.multiply(iofRate()).setScale(2, RoundingMode.HALF_UP); }
    public BigDecimal getIr() { return taxRegime == TaxRegime.TAX_FREE ? moneyZero() : accruedEarnings.subtract(getIof()).multiply(irRate()).setScale(2, RoundingMode.HALF_UP); }
    public BigDecimal getTaxes() { return getIof().add(getIr()).setScale(2, RoundingMode.HALF_UP); }
    public int getElapsedDays() { return elapsedDays; }
    public int getApplicationDays() { return applicationDays; }
    public int getOperationCount() { return operationCount; }
    public LocalDate getApplicationDate() { return applicationDate; }
    public LocalDate getLastUpdateDate() { return lastUpdateDate; }
    public DayCountConvention getDayCountConvention() { return dayCountConvention; }
    public TaxRegime getTaxRegime() { return taxRegime; }

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
            if (operationCount > 0 || accruedEarnings.signum() > 0) {
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
    }

    public void setApplicationDate(LocalDate date) {
        this.applicationDate = Objects.requireNonNull(date, "Data de aplicação obrigatória.");
        if (lastUpdateDate == null) lastUpdateDate = date;
    }

    /** Acrescenta um dia corrido ao contador do investimento. */
    public void incrementarDia() { elapsedDays++; }

    /** Acrescenta um dia de aplicação; usado apenas para dias úteis. */
    public void incrementarDiaAplicacao() { applicationDays++; }

    public void incrementDay() { incrementarDia(); }
    public void incrementApplicationDay() { incrementarDiaAplicacao(); }

    /**
     * Processa o dia informado. A taxa mensal é nominal sobre base 30/360:
     * taxa diária = taxa mensal / 30, capitalizada diariamente em dias úteis.
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
                if (!day.isBefore(applicationDate)) accrueOneBusinessDay();
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
            if (amount.compareTo(balance) > 0) throw new IllegalArgumentException("Saldo insuficiente.");
            balance = balance.subtract(amount).setScale(2, RoundingMode.HALF_UP);
        }
        operationCount++;
    }

    public void restoreState(BigDecimal balance, BigDecimal accruedEarnings, LocalDate lastUpdateDate,
                             int elapsedDays, int applicationDays, int operationCount) {
        this.balance = nonNegativeMoney(balance, "Saldo");
        this.accruedEarnings = nonNegativeMoney(accruedEarnings, "Rendimento");
        this.lastUpdateDate = Objects.requireNonNull(lastUpdateDate, "Data de atualização obrigatória.");
        if (lastUpdateDate.isBefore(applicationDate)) throw new IllegalArgumentException("Atualização anterior à aplicação.");
        if (elapsedDays < 0 || applicationDays < 0 || operationCount < 0 || operationCount > 100) {
            throw new IllegalArgumentException("Contadores inválidos.");
        }
        this.elapsedDays = elapsedDays;
        this.applicationDays = applicationDays;
        this.operationCount = operationCount;
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
            balance = balance.add(earnings).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            accruedEarnings = accruedEarnings.add(earnings).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        }
    }

    private BigDecimal iofRate() {
        int days = Math.max(1, elapsedDays);
        if (days >= 30) return BigDecimal.ZERO;
        // Tabela regressiva oficial expressa em pontos percentuais inteiros: 96, 93, ..., 3, 0.
        int percentage = BigDecimal.valueOf(30L - days).multiply(BigDecimal.valueOf(100))
                .divide(THIRTY, 0, RoundingMode.DOWN).intValueExact();
        return BigDecimal.valueOf(percentage).movePointLeft(2);
    }

    private BigDecimal irRate() {
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
}

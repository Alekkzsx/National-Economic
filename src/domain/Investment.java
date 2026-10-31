package domain;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * Reúne os dados e as regras de cálculo de uma aplicação financeira.
 *
 * <p>Os valores monetários usam {@link BigDecimal}. Dias corridos controlam
 * carência, vencimento, periodicidade e tributação; a apuração de juros ocorre
 * em atualizações de dias úteis.</p>
 */
public class Investment {
    /** Precisão intermediária para os cálculos de taxa e juros. */
    private static final MathContext RATE_CONTEXT = MathContext.DECIMAL128;
    /** Nome legível da aplicação. */
    private String name;
    /** Identificador único da aplicação no sistema. */
    private String number;
    /** Valor originalmente aplicado. */
    private BigDecimal initialAmount;
    /** Saldo que já recebeu os rendimentos periódicos. */
    private BigDecimal balance;
    /** Taxa mensal representada como fração decimal. */
    private BigDecimal monthlyRate;
    /** Rendimentos já incorporados ao saldo. */
    private BigDecimal accruedEarnings = Money.ZERO;
    /** Rendimentos apurados que aguardam a data de crédito configurada. */
    private BigDecimal pendingEarnings = Money.ZERO;
    /** Convenção usada para converter a taxa mensal em taxa diária. */
    private DayCountConvention dayCountConvention = DayCountConvention.THIRTY_360;
    /** Regime agregado, mantido em conjunto com as opções específicas de imposto. */
    private TaxRegime taxRegime = TaxRegime.STANDARD;
    /** Periodicidade de crédito dos rendimentos pendentes. */
    private YieldPeriod yieldPeriod = YieldPeriod.DAILY;
    /** Prazo até o vencimento em dias corridos; zero significa sem vencimento. */
    private int maturityDays;
    /** Período mínimo em dias corridos antes de permitir resgate. */
    private int minimumStayDays;
    /** Frequência mínima entre datas em que o resgate é permitido. */
    private RedemptionFrequency redemptionFrequency = RedemptionFrequency.DAILY;
    /** Política de cálculo do IOF. */
    private IofOption iofOption = IofOption.THREE_POINT_FIVE_PERCENT_BEFORE_30_DAYS;
    /** Política de cálculo do Imposto de Renda. */
    private IrOption irOption = IrOption.REGRESSIVE;
    /** Data em que a aplicação foi iniciada. */
    private LocalDate applicationDate;
    /** Última data processada, para não atualizar o mesmo intervalo novamente. */
    private LocalDate lastUpdateDate;
    /** Total de dias corridos desde a aplicação. */
    private int elapsedDays;
    /** Total de dias úteis considerados para correção financeira. */
    private int applicationDays;
    /** Dias corridos desde o último crédito de rendimento. */
    private int daysSinceYield;
    /** Quantidade de operações já registradas na aplicação. */
    private int operationCount;

    /** Cria uma aplicação com início na data corrente. */
    public Investment(String name, String number, BigDecimal initialAmount, BigDecimal monthlyRate) {
        this(name, number, initialAmount, monthlyRate, LocalDate.now());
    }

    /** Cria uma aplicação com a data inicial informada pelo chamador. */
    public Investment(String name, String number, BigDecimal initialAmount, BigDecimal monthlyRate,
                      LocalDate applicationDate) {
        this.name = requiredText(name, "Nome do investimento obrigatório.");
        this.number = requiredText(number, "Número do investimento obrigatório.");
        this.initialAmount = Money.positive(initialAmount, "Valor inicial");
        this.monthlyRate = validateMonthlyRate(monthlyRate);
        this.applicationDate = Objects.requireNonNull(applicationDate, "Data de aplicação obrigatória.");
        this.lastUpdateDate = applicationDate;
        this.balance = this.initialAmount;
    }

    /** Retorna o nome cadastrado da aplicação. */
    public String getName() { return name; }
    /** Retorna o identificador da aplicação. */
    public String getNumber() { return number; }
    /** Retorna o aporte inicial. */
    public BigDecimal getInitialAmount() { return initialAmount; }
    /** Retorna a taxa mensal fracionária. */
    public BigDecimal getMonthlyRate() { return monthlyRate; }
    /** Retorna o saldo bruto, incluindo rendimentos pendentes. */
    public BigDecimal getBalance() { return getGrossBalance(); }
    /** Retorna o saldo que já recebeu rendimentos creditados. */
    public BigDecimal getCreditedBalance() { return balance; }
    /** Soma o saldo creditado aos rendimentos que aguardam crédito. */
    public BigDecimal getGrossBalance() { return Money.round(balance.add(pendingEarnings)); }
    /** Retorna o saldo bruto descontados os impostos devidos. */
    public BigDecimal getNetBalance() { return Money.round(getGrossBalance().subtract(getTaxes()).max(BigDecimal.ZERO)); }
    /** Soma os rendimentos creditados aos rendimentos pendentes. */
    public BigDecimal getGrossEarnings() { return Money.round(accruedEarnings.add(pendingEarnings)); }
    /** Retorna somente os rendimentos já incorporados ao saldo. */
    public BigDecimal getCreditedEarnings() { return accruedEarnings; }
    /** Retorna os rendimentos após deduzir os tributos atuais. */
    public BigDecimal getNetEarnings() { return Money.round(getGrossEarnings().subtract(getTaxes()).max(BigDecimal.ZERO)); }
    /** Calcula o IOF sobre os rendimentos conforme a opção configurada. */
    public BigDecimal getIof() {
        return InvestmentTaxCalculator.calculateIof(getGrossEarnings(), elapsedDays, iofOption, taxRegime);
    }
    /** Calcula o IR sobre os rendimentos restantes depois do IOF. */
    public BigDecimal getIr() {
        return InvestmentTaxCalculator.calculateIr(getGrossEarnings(), getIof(), elapsedDays, irOption, taxRegime);
    }
    /** Soma os impostos calculados para a posição atual. */
    public BigDecimal getTaxes() { return Money.round(getIof().add(getIr())); }
    /** Retorna a contagem de dias corridos desde o início. */
    public int getElapsedDays() { return elapsedDays; }
    /** Retorna a contagem de dias úteis considerados na correção. */
    public int getApplicationDays() { return applicationDays; }
    /** Retorna a quantidade de operações registradas. */
    public int getOperationCount() { return operationCount; }
    /** Retorna os rendimentos que ainda aguardam crédito. */
    public BigDecimal getPendingEarnings() { return pendingEarnings; }
    /** Retorna os dias acumulados desde o último crédito de rendimento. */
    public int getDaysSinceYield() { return daysSinceYield; }
    /** Retorna a data de início da aplicação. */
    public LocalDate getApplicationDate() { return applicationDate; }
    /** Retorna a data da última atualização processada. */
    public LocalDate getLastUpdateDate() { return lastUpdateDate; }
    /** Retorna a convenção de dias usada na taxa diária. */
    public DayCountConvention getDayCountConvention() { return dayCountConvention; }
    /** Retorna o regime tributário geral compatível. */
    public TaxRegime getTaxRegime() { return taxRegime; }
    /** Retorna a periodicidade de crédito dos rendimentos. */
    public YieldPeriod getYieldPeriod() { return yieldPeriod; }
    /** Retorna o vencimento em dias corridos, ou zero quando não configurado. */
    public int getMaturityDays() { return maturityDays; }
    /** Retorna a carência em dias corridos. */
    public int getMinimumStayDays() { return minimumStayDays; }
    /** Retorna a frequência permitida para resgate. */
    public RedemptionFrequency getRedemptionFrequency() { return redemptionFrequency; }
    /** Retorna a política de IOF configurada. */
    public IofOption getIofOption() { return iofOption; }
    /** Retorna a política de IR configurada. */
    public IrOption getIrOption() { return irOption; }

    /** Define um nome obrigatório, removendo espaços externos. */
    public void setName(String name) {
        this.name = requiredText(name, "Nome do investimento obrigatório.");
    }

    /** Define um identificador obrigatório, removendo espaços externos. */
    public void setNumber(String number) {
        this.number = requiredText(number, "Número do investimento obrigatório.");
    }

    /** Permite alterar o aporte somente antes de operações ou rendimentos. */
    public void setInitialAmount(BigDecimal amount) {
        BigDecimal value = Money.positive(amount, "Valor inicial");
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

    /** Define a taxa mensal como fração entre zero e um, inclusive. */
    public void setMonthlyRate(BigDecimal monthlyRate) {
        this.monthlyRate = validateMonthlyRate(monthlyRate);
    }

    /** Define diretamente um saldo não negativo. */
    public void setBalance(BigDecimal balance) {
        this.balance = Money.nonNegative(balance, "Saldo");
    }

    /** Define a base anual usada para converter taxa mensal em diária. */
    public void setDayCountConvention(DayCountConvention convention) {
        this.dayCountConvention = Objects.requireNonNull(convention, "Convenção de dias obrigatória.");
    }

    /** Define o regime tributário geral e sincroniza as opções de IOF e IR. */
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

    /** Define a periodicidade; rendimento no vencimento exige vencimento positivo. */
    public void setYieldPeriod(YieldPeriod period) {
        Objects.requireNonNull(period, "Periodicidade de rendimento obrigatória.");
        if (period == YieldPeriod.AT_MATURITY && maturityDays <= 0) {
            throw new IllegalArgumentException("Configure os dias até o vencimento antes de usar rendimento no vencimento.");
        }
        this.yieldPeriod = period;
    }

    /** Define o vencimento e verifica sua coerência com carência e rendimento. */
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

    /** Define o prazo mínimo de permanência, sem ultrapassar o vencimento. */
    public void setMinimumStayDays(int days) {
        if (days < 0) throw new IllegalArgumentException("Permanência mínima não pode ser negativa.");
        if (maturityDays > 0 && days > maturityDays) {
            throw new IllegalArgumentException("Permanência mínima não pode exceder o vencimento.");
        }
        minimumStayDays = days;
    }

    /** Define a frequência mínima permitida entre retiradas. */
    public void setRedemptionFrequency(RedemptionFrequency frequency) {
        this.redemptionFrequency = Objects.requireNonNull(frequency, "Frequência de resgate obrigatória.");
    }

    /** Define a opção de IOF e atualiza o regime geral correspondente. */
    public void setIofOption(IofOption option) {
        this.iofOption = Objects.requireNonNull(option, "Opção de IOF obrigatória.");
        syncLegacyTaxRegime();
    }

    /** Define a opção de IR e atualiza o regime geral correspondente. */
    public void setIrOption(IrOption option) {
        this.irOption = Objects.requireNonNull(option, "Opção de IR obrigatória.");
        syncLegacyTaxRegime();
    }

    /** Define a data inicial e a usa como referência da primeira atualização. */
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

    /** Alias em inglês para incrementar o contador de dias corridos. */
    public void incrementDay() { incrementarDia(); }
    /** Alias em inglês para incrementar o contador de dias de aplicação. */
    public void incrementApplicationDay() { incrementarDiaAplicacao(); }

    /** Informa se o prazo configurado de vencimento já foi alcançado. */
    public boolean isMatured() {
        return maturityDays > 0 && elapsedDays >= maturityDays;
    }

    /**
     * Informa se, na posição atual do contador, carência e frequência permitem
     * resgate. Operações datadas validam a data do lançamento separadamente.
     */
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
        // Crédito em data de vencimento só é avaliado contra a maturidade configurada.
        return yieldPeriod == YieldPeriod.AT_MATURITY ? isMatured() : isYieldDate();
    }

    private void creditPendingEarnings() {
        if (pendingEarnings.signum() > 0) {
            balance = Money.round(balance.add(pendingEarnings));
            accruedEarnings = Money.round(accruedEarnings.add(pendingEarnings));
            pendingEarnings = Money.ZERO;
        }
        // Mantém apenas dias excedentes ao período para o próximo ciclo de crédito.
        if (yieldPeriod != YieldPeriod.AT_MATURITY) {
            daysSinceYield %= yieldPeriod.days();
        }
    }

    private boolean isRedemptionDate() {
        int interval = redemptionFrequency.days();
        return interval == 1 || (elapsedDays > 0 && elapsedDays % interval == 0);
    }

    private boolean canRedeemOn(LocalDate date) {
        // A operação usa a data informada, não o contador do último updateAll.
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

        // Percorre todos os dias não processados para manter os contadores corretos,
        // ainda que a aplicação seja atualizada depois de vários dias sem uso.
        LocalDate day = lastUpdateDate == null ? date : lastUpdateDate.plusDays(1);
        while (!day.isAfter(date)) {
            incrementarDia();
            if (isBusinessDay(day)) {
                incrementarDiaAplicacao();
                if (!day.isBefore(applicationDate)) {
                    // Após o vencimento, não se acumulam novos rendimentos.
                    if (maturityDays == 0 || elapsedDays <= maturityDays) accrueOneBusinessDay();
                    if (isYieldDue()) creditPendingEarnings();
                }
            }
            day = day.plusDays(1);
        }
        lastUpdateDate = date;
    }

    /** Aplica o lançamento ao saldo, validando limite, data e regra de resgate. */
    public void apply(FinancialOperation operation) {
        Objects.requireNonNull(operation, "Operação obrigatória.");
        if (operationCount >= 100) throw new IllegalStateException("Limite de 100 operações por aplicação atingido.");
        if (operation.date().isBefore(applicationDate)) throw new IllegalArgumentException("Operação anterior à aplicação.");
        BigDecimal amount = Money.round(operation.amount());
        if (operation.type() == OperationType.DEPOSIT) {
            balance = Money.round(balance.add(amount));
        } else {
            if (!canRedeemOn(operation.date())) {
                throw new IllegalArgumentException("Resgate fora da carência ou da frequência permitida.");
            }
            if (amount.compareTo(balance) > 0) throw new IllegalArgumentException("Saldo insuficiente.");
            balance = Money.round(balance.subtract(amount));
        }
        operationCount++;
    }

    /**
     * Restaura os dados do formato mais antigo, que não persistia termos
     * avançados nem rendimentos pendentes.
     */
    public void restoreState(BigDecimal balance, BigDecimal accruedEarnings, LocalDate lastUpdateDate,
                             int elapsedDays, int applicationDays, int operationCount) {
        restoreState(balance, accruedEarnings, lastUpdateDate, elapsedDays, applicationDays, operationCount,
                YieldPeriod.DAILY, 0, 0, RedemptionFrequency.DAILY, iofOption, irOption);
    }

    /**
     * Restaura os termos avançados de uma versão que ainda não salvava o
     * rendimento pendente nem o contador do ciclo atual.
     */
    public void restoreState(BigDecimal balance, BigDecimal accruedEarnings, LocalDate lastUpdateDate,
                             int elapsedDays, int applicationDays, int operationCount, YieldPeriod yieldPeriod,
                             int maturityDays, int minimumStayDays, RedemptionFrequency redemptionFrequency,
                             IofOption iofOption, IrOption irOption) {
        restoreState(balance, accruedEarnings, lastUpdateDate, elapsedDays, applicationDays, operationCount,
                yieldPeriod, maturityDays, minimumStayDays, redemptionFrequency, iofOption, irOption,
                BigDecimal.ZERO, elapsedDays % (yieldPeriod == YieldPeriod.AT_MATURITY
                        ? Math.max(1, elapsedDays + 1) : Math.max(1, yieldPeriod.days())));
    }

    /**
     * Restaura o estado completo da aplicação e valida os dados recuperados
     * antes de permitir novas operações.
     */
    public void restoreState(BigDecimal balance, BigDecimal accruedEarnings, LocalDate lastUpdateDate,
                             int elapsedDays, int applicationDays, int operationCount, YieldPeriod yieldPeriod,
                             int maturityDays, int minimumStayDays, RedemptionFrequency redemptionFrequency,
                             IofOption iofOption, IrOption irOption, BigDecimal pendingEarnings,
                             int daysSinceYield) {
        this.balance = Money.nonNegative(balance, "Saldo");
        this.accruedEarnings = Money.nonNegative(accruedEarnings, "Rendimento");
        this.pendingEarnings = Money.nonNegative(pendingEarnings, "Rendimento pendente");
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

    /** Informa se a data não cai em sábado ou domingo. */
    public boolean isBusinessDay(LocalDate date) {
        return date.getDayOfWeek() != DayOfWeek.SATURDAY && date.getDayOfWeek() != DayOfWeek.SUNDAY;
    }

    /** Mapeia o dia conforme o enunciado: sábado=0, domingo=1, segunda=2... */
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
        // Converte a taxa mensal em taxa diária pela convenção selecionada.
        BigDecimal annualizedRate = monthlyRate.multiply(BigDecimal.valueOf(12), RATE_CONTEXT);
        BigDecimal dailyRate = annualizedRate.divide(
                BigDecimal.valueOf(dayCountConvention.daysPerYear()), RATE_CONTEXT);
        BigDecimal earnings = Money.round(balance.multiply(dailyRate, RATE_CONTEXT));
        if (earnings.signum() > 0) {
            pendingEarnings = Money.round(pendingEarnings.add(earnings));
        }
    }

    private void syncLegacyTaxRegime() {
        // Mantém o enum legado em sincronia com as duas políticas independentes.
        taxRegime = iofOption == IofOption.EXEMPT && irOption == IrOption.EXEMPT
                ? TaxRegime.TAX_FREE : TaxRegime.STANDARD;
    }

    private static String requiredText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private static BigDecimal validateMonthlyRate(BigDecimal monthlyRate) {
        Objects.requireNonNull(monthlyRate, "Taxa mensal obrigatória.");
        if (monthlyRate.signum() < 0 || monthlyRate.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("A taxa mensal deve estar entre 0 e 1 (inclusive).");
        }
        return monthlyRate;
    }
}

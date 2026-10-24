
import domain.FinancialOperation;
import domain.CalendarDate;
import domain.IofOption;
import domain.IrOption;
import domain.Investment;
import domain.OperationType;
import domain.PersonalData;
import domain.RedemptionFrequency;
import service.InvestmentSystem;
import domain.YieldPeriod;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

/** Testes executáveis sem dependência externa: javac + java com assertions habilitadas. */
public final class InvestmentSystemSelfTest {
    private InvestmentSystemSelfTest() { }

    public static void main(String[] args) throws Exception {
        CalendarDate calendarDate = new CalendarDate(24, 10, 2026);
        check(calendarDate.dayOfWeek() == 0, "data sábado por extenso numérico");
        check(calendarDate.dayOfWeekName().equals("sábado"), "nome do dia em pt-BR");
        check(calendarDate.dayOfWeek(25, 10, 2026) == 1, "overload numérico da data");
        check(calendarDate.dayOfWeekName(26, 10, 2026).equals("segunda-feira"),
                "overload por extenso da data");
        expectRejected(() -> calendarDate.setCadastro(31, 2, 2026), "data inválida");
        calendarDate.setCadastro(LocalDate.of(2026, 10, 26));
        check(calendarDate.getDay() == 26 && calendarDate.getMonth() == 10
                && calendarDate.getYear() == 2026 && calendarDate.toString().contains("segunda-feira"),
                "cadastro e getters de data");

        PersonalData alternateData = new PersonalData("Pessoa", "52998224725");
        check(alternateData.getMobile().isEmpty(), "overload de cadastro sem celular");
        alternateData.alterarDados("Pessoa Atualizada", "(11) 98765-4321");
        check(alternateData.getName().equals("Pessoa Atualizada") && alternateData.getMobile().equals("11987654321"),
                "sobrecarga de alteração de dados pessoais");
        class SpecializedOperation extends FinancialOperation {
            SpecializedOperation() {
                super(LocalDate.of(2026, 1, 1), new BigDecimal("2"), "Inicial", OperationType.DEPOSIT);
            }
        }
        FinancialOperation mutableOperation = new SpecializedOperation();
        mutableOperation.setCadastro(2, 1, 2026, new BigDecimal("3.456"), "  Atualizada  ", OperationType.WITHDRAWAL);
        check(mutableOperation.date().equals(LocalDate.of(2026, 1, 2))
                && mutableOperation.getAmount().compareTo(new BigDecimal("3.46")) == 0
                && mutableOperation.description().equals("Atualizada")
                && mutableOperation.getType() == OperationType.WITHDRAWAL,
                "operação extensível, accessors compatíveis e cadastro sobrecarregado");
        expectRejected(() -> mutableOperation.setAmount(BigDecimal.ZERO), "valor inválido da operação");

        PersonalData person = new PersonalData("Ana Silva", "529.982.247-25", "(11) 98765-4321");
        check(!person.toString().contains("52998224725"), "CPF completo não pode aparecer no toString");
        check(Investment.calendarDayIndex(LocalDate.of(2026, 10, 24)) == 0, "sábado deve mapear para zero");
        check(Investment.calendarDayIndex(LocalDate.of(2026, 10, 25)) == 1, "domingo deve mapear para um");

        InvestmentSystem system = new InvestmentSystem();
        system.registerClient(person, 12, 3456);
        Investment investment = new Investment("Reserva", "INV-1", new BigDecimal("100.00"),
                new BigDecimal("0.03"), LocalDate.of(2026, 1, 1));
        investment.setYieldPeriod(YieldPeriod.QUARTERLY_90_DAYS);
        investment.setMaturityDays(365);
        investment.setMinimumStayDays(30);
        investment.setRedemptionFrequency(RedemptionFrequency.MONTHLY);
        investment.setIofOption(IofOption.THREE_POINT_FIVE_PERCENT_ALWAYS);
        investment.setIrOption(IrOption.EXEMPT);
        system.registerInvestment(person.getCpf(), investment);
        system.updateAll(LocalDate.of(2026, 1, 3)); // sábado: não atualiza
        check(investment.getBalance().compareTo(new BigDecimal("100.00")) == 0, "fim de semana sem rendimento");
        system.updateAll(LocalDate.of(2026, 1, 2));
        check(investment.getBalance().compareTo(new BigDecimal("100.10")) == 0, "rendimento diário arredondado");
        check(!investment.isYieldDate() && !investment.canRedeem(), "periodicidade e carência antes do prazo");
        system.registerOperation(person.getCpf(), "INV-1",
                new FinancialOperation(LocalDate.of(2026, 1, 2), new BigDecimal("5"), "Aporte", OperationType.DEPOSIT));
        check(system.getStatement(person.getCpf(), "INV-1", LocalDate.of(2026, 1, 2),
                LocalDate.of(2026, 1, 2)).size() == 1, "extrato inclusivo");

        Path file = Path.of(".investment-system-self-test.tsv");
        try {
            system.save(file);
            InvestmentSystem restored = InvestmentSystem.load(file);
            check(restored.getInvestments(person.getCpf()).size() == 1, "investimento restaurado");
            check(restored.getBalance(person.getCpf(), "INV-1").compareTo(system.getBalance(person.getCpf(), "INV-1")) == 0,
                    "saldo restaurado");
            check(restored.getStatement(person.getCpf(), "INV-1", LocalDate.of(2026, 1, 2),
                    LocalDate.of(2026, 1, 2)).size() == 1, "operação restaurada");
            Investment restoredInvestment = restored.getInvestments(person.getCpf()).getFirst();
            check(restoredInvestment.getYieldPeriod() == YieldPeriod.QUARTERLY_90_DAYS, "periodicidade restaurada");
            check(restoredInvestment.getMaturityDays() == 365, "vencimento restaurado");
            check(restoredInvestment.getMinimumStayDays() == 30, "permanência mínima restaurada");
            check(restoredInvestment.getRedemptionFrequency() == RedemptionFrequency.MONTHLY,
                    "frequência de resgate restaurada");
            check(restoredInvestment.getIofOption() == IofOption.THREE_POINT_FIVE_PERCENT_ALWAYS,
                    "opção IOF restaurada");
            check(restoredInvestment.getIrOption() == IrOption.EXEMPT, "opção IR restaurada");
            check(restoredInvestment.getPendingEarnings().compareTo(investment.getPendingEarnings()) == 0,
                    "rendimento pendente restaurado");
            check(restoredInvestment.getDaysSinceYield() == investment.getDaysSinceYield(),
                    "contador de periodicidade restaurado");
            system.updateAll(LocalDate.of(2026, 4, 1));
            restored.updateAll(LocalDate.of(2026, 4, 1));
            check(restored.getBalance(person.getCpf(), "INV-1").compareTo(
                    system.getBalance(person.getCpf(), "INV-1")) == 0, "capitalização após restauração");
            check(investment.getPendingEarnings().signum() == 0, "rendimento trimestral capitalizado no dia 90");
        } finally {
            Files.deleteIfExists(file);
        }

        Investment periodic = new Investment("Mensal", "INV-MONTHLY", new BigDecimal("100000.00"),
                new BigDecimal("0.03"), LocalDate.of(2026, 1, 1));
        periodic.setYieldPeriod(YieldPeriod.MONTHLY_30_DAYS);
        periodic.update(LocalDate.of(2026, 1, 30)); // 29 dias corridos
        check(periodic.getCreditedBalance().compareTo(new BigDecimal("100000.00")) == 0,
                "rendimento mensal ainda não capitalizado");
        check(periodic.getPendingEarnings().signum() > 0, "rendimento mensal acumulado como pendente");
        periodic.update(LocalDate.of(2026, 2, 2)); // dia 30 caiu no sábado, crédito no próximo dia útil
        check(periodic.getPendingEarnings().signum() == 0, "rendimento mensal creditado");
        check(periodic.getCreditedBalance().compareTo(new BigDecimal("100000.00")) > 0,
                "periodicidade mensal altera a capitalização");
        Investment daily = new Investment("Diário", "INV-DAILY", new BigDecimal("100000.00"),
                new BigDecimal("0.03"), LocalDate.of(2026, 1, 1));
        daily.update(LocalDate.of(2026, 1, 30));
        daily.update(LocalDate.of(2026, 2, 2));
        check(daily.getCreditedBalance().compareTo(periodic.getCreditedBalance()) > 0,
                "capitalização diária difere da mensal");
        Investment atMaturity = new Investment("Vencimento", "INV-MATURITY", new BigDecimal("100000.00"),
                new BigDecimal("0.03"), LocalDate.of(2026, 1, 1));
        atMaturity.setMaturityDays(30);
        atMaturity.setYieldPeriod(YieldPeriod.AT_MATURITY);
        atMaturity.update(LocalDate.of(2026, 1, 30));
        check(atMaturity.getCreditedBalance().compareTo(new BigDecimal("100000.00")) == 0,
                "rendimento de vencimento aguarda a maturidade");
        atMaturity.update(LocalDate.of(2026, 2, 2));
        check(atMaturity.isMatured() && atMaturity.getPendingEarnings().signum() == 0
                        && atMaturity.getCreditedBalance().compareTo(new BigDecimal("100000.00")) > 0,
                "rendimento creditado ao atingir maturidade");

        Investment redemption = new Investment("Resgate", "INV-REDEEM", new BigDecimal("100.00"),
                new BigDecimal("0"), LocalDate.of(2026, 1, 1));
        redemption.setMinimumStayDays(10);
        redemption.setRedemptionFrequency(RedemptionFrequency.MONTHLY);
        check(!redemption.canRedeem(), "canRedeem preserva carência antes do vencimento");
        expectRejected(() -> redemption.apply(new FinancialOperation(LocalDate.of(2026, 1, 10),
                new BigDecimal("1"), "Resgate antecipado", OperationType.WITHDRAWAL)), "carência");
        expectRejected(() -> redemption.apply(new FinancialOperation(LocalDate.of(2026, 1, 30),
                new BigDecimal("1"), "Resgate fora da frequência", OperationType.WITHDRAWAL)), "frequência mensal");
        redemption.apply(new FinancialOperation(LocalDate.of(2026, 1, 31),
                new BigDecimal("1"), "Resgate mensal permitido", OperationType.WITHDRAWAL));
        expectRejected(() -> redemption.apply(new FinancialOperation(LocalDate.of(2026, 2, 1),
                new BigDecimal("1"), "Segundo resgate fora da frequência", OperationType.WITHDRAWAL)),
                "intervalo de resgate");

        boolean insufficientFundsRejected = false;
        try {
            system.registerOperation(person.getCpf(), "INV-1", new FinancialOperation(LocalDate.of(2026, 4, 1),
                    new BigDecimal("9999"), "Retirada", OperationType.WITHDRAWAL));
        } catch (IllegalArgumentException expected) {
            insufficientFundsRejected = true;
        }
        check(insufficientFundsRejected, "saque sem saldo deve ser rejeitado");

        Investment taxed = new Investment("Tributável", "INV-TAX", new BigDecimal("100000.00"),
                new BigDecimal("0.03"), LocalDate.of(2026, 1, 1));
        taxed.setIrOption(IrOption.EXEMPT);
        taxed.setIofOption(IofOption.THREE_POINT_FIVE_PERCENT_ALWAYS);
        taxed.update(LocalDate.of(2026, 1, 2));
        check(taxed.getGrossEarnings().compareTo(new BigDecimal("100.00")) == 0, "rendimento bruto diário");
        check(taxed.getIof().compareTo(new BigDecimal("3.50")) == 0, "IOF sempre de 3,5%");
        check(taxed.getIr().signum() == 0, "IR isento");

        taxed.setIofOption(IofOption.THREE_POINT_FIVE_PERCENT_BEFORE_30_DAYS);
        check(taxed.getIof().compareTo(new BigDecimal("3.50")) == 0, "IOF de 3,5% antes de 30 dias");
        taxed.update(LocalDate.of(2026, 2, 2));
        check(taxed.getIof().signum() == 0, "IOF de 3,5% apenas antes de 30 dias");
        taxed.setIrOption(IrOption.REGRESSIVE);
        check(taxed.getIr().signum() > 0, "IR regressivo tributável");

        boolean invalidMaturityRejected = false;
        try {
            taxed.setMinimumStayDays(50);
            taxed.setMaturityDays(40);
        } catch (IllegalArgumentException expected) {
            invalidMaturityRejected = true;
        }
        check(invalidMaturityRejected, "permanência mínima não pode exceder vencimento");

        Investment bands = new Investment("Faixas IR", "INV-IR", new BigDecimal("100000.00"),
                new BigDecimal("0.03"), LocalDate.of(2026, 1, 1));
        bands.setIofOption(IofOption.EXEMPT);
        bands.update(LocalDate.of(2026, 1, 2));
        for (int day = 1; day < 180; day++) bands.incrementarDia();
        check(bands.getIr().compareTo(new BigDecimal("22.50")) == 0, "IR 22,5% até 180 dias");
        bands.incrementarDia();
        check(bands.getIr().compareTo(new BigDecimal("20.00")) == 0, "IR 20% de 181 a 360 dias");
        for (int day = 181; day < 360; day++) bands.incrementarDia();
        check(bands.getIr().compareTo(new BigDecimal("20.00")) == 0, "limite de 360 dias");
        bands.incrementarDia();
        check(bands.getIr().compareTo(new BigDecimal("17.50")) == 0, "IR 17,5% de 361 a 720 dias");
        for (int day = 361; day < 720; day++) bands.incrementarDia();
        check(bands.getIr().compareTo(new BigDecimal("17.50")) == 0, "limite de 720 dias");
        bands.incrementarDia();
        check(bands.getIr().compareTo(new BigDecimal("15.00")) == 0, "IR 15% após 720 dias");

        Path legacyFile = Path.of(".investment-system-v1-self-test.tsv");
        try {
            Files.write(legacyFile, List.of(
                    "NATIONAL-ECONOMIC\t1",
                    "C\t52998224725\tAna Silva\t11987654321\t12\t3456",
                    "I\t52998224725\tOLD-1\tLegado\t100.00\t0.03\t2026-01-01\t100.00\t0.00\t2026-01-01\t0\t0\t0\tTHIRTY_360\tSTANDARD"
            ));
            InvestmentSystem legacy = InvestmentSystem.load(legacyFile);
            Investment oldInvestment = legacy.getInvestments("52998224725").getFirst();
            check(oldInvestment.getYieldPeriod() == YieldPeriod.DAILY, "defaults aplicados ao formato v1");
            check(oldInvestment.getIofOption() == IofOption.THREE_POINT_FIVE_PERCENT_BEFORE_30_DAYS,
                    "opção IOF padrão do formato v1");
        } finally {
            Files.deleteIfExists(legacyFile);
        }

        Path version2File = Path.of(".investment-system-v2-self-test.tsv");
        try {
            Files.write(version2File, List.of(
                    "NATIONAL-ECONOMIC\t2",
                    "C\t52998224725\tAna Silva\t11987654321\t12\t3456",
                    "I\t52998224725\tOLD-2\tFormato v2\t100.00\t0.03\t2026-01-01\t100.00\t0.00\t2026-01-01\t0\t0\t0\tTHIRTY_360\tSTANDARD\tMONTHLY_30_DAYS\t180\t30\tMONTHLY\tEXEMPT\tREGRESSIVE"
            ));
            InvestmentSystem version2 = InvestmentSystem.load(version2File);
            Investment oldInvestment = version2.getInvestments("52998224725").getFirst();
            check(oldInvestment.getYieldPeriod() == YieldPeriod.MONTHLY_30_DAYS, "termos v2 preservados");
            check(oldInvestment.getDaysSinceYield() == 0, "contador de rendimento v2 derivado");
        } finally {
            Files.deleteIfExists(version2File);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void expectRejected(Runnable action, String message) {
        boolean rejected = false;
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        check(rejected, message + " deve ser rejeitado");
    }
}

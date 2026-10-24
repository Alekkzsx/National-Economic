package br.com.nationaleconomic;

import br.com.nationaleconomic.domain.FinancialOperation;
import br.com.nationaleconomic.domain.IofOption;
import br.com.nationaleconomic.domain.IrOption;
import br.com.nationaleconomic.domain.Investment;
import br.com.nationaleconomic.domain.OperationType;
import br.com.nationaleconomic.domain.PersonalData;
import br.com.nationaleconomic.domain.RedemptionFrequency;
import br.com.nationaleconomic.service.InvestmentSystem;
import br.com.nationaleconomic.domain.YieldPeriod;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

/** Testes executáveis sem dependência externa: javac + java com assertions habilitadas. */
public final class InvestmentSystemSelfTest {
    private InvestmentSystemSelfTest() { }

    public static void main(String[] args) throws Exception {
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
        for (int day = 0; day < 89; day++) investment.incrementarDia();
        check(investment.isYieldDate() && investment.canRedeem(), "rendimento trimestral aos 90 dias");
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
        } finally {
            Files.deleteIfExists(file);
        }

        boolean insufficientFundsRejected = false;
        try {
            system.registerOperation(person.getCpf(), "INV-1", new FinancialOperation(LocalDate.of(2026, 1, 2),
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
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}

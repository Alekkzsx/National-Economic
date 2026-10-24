package br.com.nationaleconomic;

import br.com.nationaleconomic.domain.FinancialOperation;
import br.com.nationaleconomic.domain.Investment;
import br.com.nationaleconomic.domain.OperationType;
import br.com.nationaleconomic.domain.PersonalData;
import br.com.nationaleconomic.service.InvestmentSystem;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

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
        system.registerInvestment(person.getCpf(), investment);
        system.updateAll(LocalDate.of(2026, 1, 3)); // sábado: não atualiza
        check(investment.getBalance().compareTo(new BigDecimal("100.00")) == 0, "fim de semana sem rendimento");
        system.updateAll(LocalDate.of(2026, 1, 2));
        check(investment.getBalance().compareTo(new BigDecimal("100.10")) == 0, "rendimento diário arredondado");
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
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}

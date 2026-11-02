
import modelo.OperacaoFinanceira;
import modelo.DataCalendario;
import modelo.OpcaoIOF;
import modelo.OpcaoIR;
import modelo.AplicacaoFinanceira;
import modelo.TipoOperacao;
import modelo.DadosPessoais;
import modelo.FrequenciaResgate;
import servicos.SistemaInvestimentos;
import modelo.PeriodicidadeRendimento;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

/**
 * Testes de integração executáveis sem biblioteca externa.
 *
 * <p>Compile o código principal junto com este arquivo e rode com
 * {@code java -ea}; cada {@code check} transforma uma regressão em falha.</p>
 */
public final class TesteSistemaInvestimentos {
    /** Impede a instanciação da classe de teste executável. */
    private TesteSistemaInvestimentos() { }

    /** Executa as verificações de domínio, cálculo, extrato e persistência. */
    public static void main(String[] args) throws Exception {
        // Confere o calendário didático e a validação de datas civis.
        DataCalendario calendarDate = new DataCalendario(24, 10, 2026);
        check(calendarDate.diaDaSemana() == 0, "data sábado por extenso numérico");
        check(calendarDate.nomeDoDiaDaSemana().equals("sábado"), "nome do dia em pt-BR");
        check(calendarDate.diaDaSemana(25, 10, 2026) == 1, "overload numérico da data");
        check(calendarDate.nomeDoDiaDaSemana(26, 10, 2026).equals("segunda-feira"),
                "overload por extenso da data");
        expectRejected(() -> calendarDate.setCadastro(31, 2, 2026), "data inválida");
        calendarDate.setCadastro(LocalDate.of(2026, 10, 26));
        check(calendarDate.getDia() == 26 && calendarDate.getMes() == 10
                && calendarDate.getAno() == 2026 && calendarDate.toString().contains("segunda-feira"),
                "cadastro e getters de data");

        // Verifica sobrecargas e validações das classes com cadastro mutável.
        DadosPessoais alternateData = new DadosPessoais("Pessoa", "52998224725");
        check(alternateData.getCelular().isEmpty(), "overload de cadastro sem celular");
        alternateData.alterarDados("Pessoa Atualizada", "(11) 98765-4321");
        check(alternateData.getNome().equals("Pessoa Atualizada") && alternateData.getCelular().equals("11987654321"),
                "sobrecarga de alteração de dados pessoais");
        class SpecializedOperation extends OperacaoFinanceira {
            SpecializedOperation() {
                super(LocalDate.of(2026, 1, 1), new BigDecimal("2"), "Inicial", TipoOperacao.DEPOSIT);
            }
        }
        class OperationWithOverriddenSetup extends OperacaoFinanceira {
            OperationWithOverriddenSetup() {
                super(LocalDate.of(2026, 1, 1), new BigDecimal("2"), "Inicial", TipoOperacao.DEPOSIT);
            }

            @Override
            public void setCadastro(LocalDate date, BigDecimal amount, String description, TipoOperacao type) {
                throw new AssertionError("O construtor não deve despachar para métodos sobrescrevíveis.");
            }
        }
        OperacaoFinanceira mutableOperation = new SpecializedOperation();
        check(new OperationWithOverriddenSetup().valor().compareTo(new BigDecimal("2.00")) == 0,
                "construtor de operação não chama método sobrescrevível");
        mutableOperation.setCadastro(2, 1, 2026, new BigDecimal("3.456"), "  Atualizada  ", TipoOperacao.WITHDRAWAL);
        check(mutableOperation.data().equals(LocalDate.of(2026, 1, 2))
                && mutableOperation.getValor().compareTo(new BigDecimal("3.46")) == 0
                && mutableOperation.descricao().equals("Atualizada")
                && mutableOperation.getTipo() == TipoOperacao.WITHDRAWAL,
                "operação extensível, accessors compatíveis e cadastro sobrecarregado");
        expectRejected(() -> mutableOperation.setValor(BigDecimal.ZERO), "valor inválido da operação");

        // CPF é validado, mas não deve ser exposto integralmente na representação.
        DadosPessoais person = new DadosPessoais("Ana Silva", "529.982.247-25", "(11) 98765-4321");
        check(!person.toString().contains("52998224725"), "CPF completo não pode aparecer no toString");
        check(AplicacaoFinanceira.indiceDiaSemana(LocalDate.of(2026, 10, 24)) == 0, "sábado deve mapear para zero");
        check(AplicacaoFinanceira.indiceDiaSemana(LocalDate.of(2026, 10, 25)) == 1, "domingo deve mapear para um");

        // Configura uma aplicação com termos não padrão e testa o fluxo de operações.
        SistemaInvestimentos system = new SistemaInvestimentos();
        system.cadastrarCliente(person, 12, 3456);
        AplicacaoFinanceira investment = new AplicacaoFinanceira("Reserva", "INV-1", new BigDecimal("100.00"),
                new BigDecimal("0.03"), LocalDate.of(2026, 1, 1));
        investment.setPeriodicidadeRendimento(PeriodicidadeRendimento.QUARTERLY_90_DAYS);
        investment.setDiasVencimento(365);
        investment.setDiasPermanenciaMinima(30);
        investment.setFrequenciaResgate(FrequenciaResgate.MONTHLY);
        investment.setOpcaoIOF(OpcaoIOF.THREE_POINT_FIVE_PERCENT_ALWAYS);
        investment.setOpcaoIR(OpcaoIR.EXEMPT);
        system.cadastrarAplicacao(person.getCpf(), investment);
        system.atualizarTodos(LocalDate.of(2026, 1, 3)); // sábado: não atualiza
        check(investment.getSaldo().compareTo(new BigDecimal("100.00")) == 0, "fim de semana sem rendimento");
        system.atualizarTodos(LocalDate.of(2026, 1, 2));
        check(investment.getSaldo().compareTo(new BigDecimal("100.10")) == 0, "rendimento diário arredondado");
        check(!investment.ehDataDeRendimento() && !investment.podeResgatar(), "periodicidade e carência antes do prazo");
        system.registrarOperacao(person.getCpf(), "INV-1",
                new OperacaoFinanceira(LocalDate.of(2026, 1, 2), new BigDecimal("5"), "Aporte", TipoOperacao.DEPOSIT));
        check(system.obterExtrato(person.getCpf(), "INV-1", LocalDate.of(2026, 1, 2),
                LocalDate.of(2026, 1, 2)).size() == 1, "extrato inclusivo");

        // Salva e restaura todos os dados, inclusive o estado do ciclo de rendimento.
        Path file = Path.of(".investment-system-self-test.tsv");
        try {
            system.salvar(file);
            SistemaInvestimentos restored = SistemaInvestimentos.carregar(file);
            check(restored.listarAplicacoes(person.getCpf()).size() == 1, "investimento restaurado");
            check(restored.getSaldo(person.getCpf(), "INV-1").compareTo(system.getSaldo(person.getCpf(), "INV-1")) == 0,
                    "saldo restaurado");
            check(restored.obterExtrato(person.getCpf(), "INV-1", LocalDate.of(2026, 1, 2),
                    LocalDate.of(2026, 1, 2)).size() == 1, "operação restaurada");
            AplicacaoFinanceira restoredInvestment = restored.listarAplicacoes(person.getCpf()).getFirst();
            check(restoredInvestment.getPeriodicidadeRendimento() == PeriodicidadeRendimento.QUARTERLY_90_DAYS, "periodicidade restaurada");
            check(restoredInvestment.getDiasVencimento() == 365, "vencimento restaurado");
            check(restoredInvestment.getDiasPermanenciaMinima() == 30, "permanência mínima restaurada");
            check(restoredInvestment.getFrequenciaResgate() == FrequenciaResgate.MONTHLY,
                    "frequência de resgate restaurada");
            check(restoredInvestment.getOpcaoIOF() == OpcaoIOF.THREE_POINT_FIVE_PERCENT_ALWAYS,
                    "opção IOF restaurada");
            check(restoredInvestment.getOpcaoIR() == OpcaoIR.EXEMPT, "opção IR restaurada");
            check(restoredInvestment.getRendimentosPendentes().compareTo(investment.getRendimentosPendentes()) == 0,
                    "rendimento pendente restaurado");
            check(restoredInvestment.getDiasDesdeRendimento() == investment.getDiasDesdeRendimento(),
                    "contador de periodicidade restaurado");
            system.atualizarTodos(LocalDate.of(2026, 4, 1));
            restored.atualizarTodos(LocalDate.of(2026, 4, 1));
            check(restored.getSaldo(person.getCpf(), "INV-1").compareTo(
                    system.getSaldo(person.getCpf(), "INV-1")) == 0, "capitalização após restauração");
            check(investment.getRendimentosPendentes().signum() == 0, "rendimento trimestral capitalizado no dia 90");
        } finally {
            Files.deleteIfExists(file);
        }

        // Rendimentos periódicos ficam pendentes até o crédito configurado.
        AplicacaoFinanceira periodic = new AplicacaoFinanceira("Mensal", "INV-MONTHLY", new BigDecimal("100000.00"),
                new BigDecimal("0.03"), LocalDate.of(2026, 1, 1));
        periodic.setPeriodicidadeRendimento(PeriodicidadeRendimento.MONTHLY_30_DAYS);
        periodic.atualizar(LocalDate.of(2026, 1, 30)); // 29 dias corridos
        check(periodic.getSaldoCreditado().compareTo(new BigDecimal("100000.00")) == 0,
                "rendimento mensal ainda não capitalizado");
        check(periodic.getRendimentosPendentes().signum() > 0, "rendimento mensal acumulado como pendente");
        periodic.atualizar(LocalDate.of(2026, 2, 2)); // dia 30 caiu no sábado, crédito no próximo dia útil
        check(periodic.getRendimentosPendentes().signum() == 0, "rendimento mensal creditado");
        check(periodic.getSaldoCreditado().compareTo(new BigDecimal("100000.00")) > 0,
                "periodicidade mensal altera a capitalização");
        AplicacaoFinanceira daily = new AplicacaoFinanceira("Diário", "INV-DAILY", new BigDecimal("100000.00"),
                new BigDecimal("0.03"), LocalDate.of(2026, 1, 1));
        daily.atualizar(LocalDate.of(2026, 1, 30));
        daily.atualizar(LocalDate.of(2026, 2, 2));
        check(daily.getSaldoCreditado().compareTo(periodic.getSaldoCreditado()) > 0,
                "capitalização diária difere da mensal");
        // A periodicidade de vencimento só credita o rendimento ao atingir o prazo.
        AplicacaoFinanceira atMaturity = new AplicacaoFinanceira("Vencimento", "INV-MATURITY", new BigDecimal("100000.00"),
                new BigDecimal("0.03"), LocalDate.of(2026, 1, 1));
        atMaturity.setDiasVencimento(30);
        atMaturity.setPeriodicidadeRendimento(PeriodicidadeRendimento.AT_MATURITY);
        atMaturity.atualizar(LocalDate.of(2026, 1, 30));
        check(atMaturity.getSaldoCreditado().compareTo(new BigDecimal("100000.00")) == 0,
                "rendimento de vencimento aguarda a maturidade");
        atMaturity.atualizar(LocalDate.of(2026, 2, 2));
        check(atMaturity.venceu() && atMaturity.getRendimentosPendentes().signum() == 0
                        && atMaturity.getSaldoCreditado().compareTo(new BigDecimal("100000.00")) > 0,
                "rendimento creditado ao atingir maturidade");

        // Resgates respeitam carência, frequência e saldo disponível.
        AplicacaoFinanceira redemption = new AplicacaoFinanceira("Resgate", "INV-REDEEM", new BigDecimal("100.00"),
                new BigDecimal("0"), LocalDate.of(2026, 1, 1));
        redemption.setDiasPermanenciaMinima(10);
        redemption.setFrequenciaResgate(FrequenciaResgate.MONTHLY);
        check(!redemption.podeResgatar(), "canRedeem preserva carência antes do vencimento");
        expectRejected(() -> redemption.aplicar(new OperacaoFinanceira(LocalDate.of(2026, 1, 10),
                new BigDecimal("1"), "Resgate antecipado", TipoOperacao.WITHDRAWAL)), "carência");
        expectRejected(() -> redemption.aplicar(new OperacaoFinanceira(LocalDate.of(2026, 1, 30),
                new BigDecimal("1"), "Resgate fora da frequência", TipoOperacao.WITHDRAWAL)), "frequência mensal");
        redemption.aplicar(new OperacaoFinanceira(LocalDate.of(2026, 1, 31),
                new BigDecimal("1"), "Resgate mensal permitido", TipoOperacao.WITHDRAWAL));
        expectRejected(() -> redemption.aplicar(new OperacaoFinanceira(LocalDate.of(2026, 2, 1),
                new BigDecimal("1"), "Segundo resgate fora da frequência", TipoOperacao.WITHDRAWAL)),
                "intervalo de resgate");

        boolean insufficientFundsRejected = false;
        try {
            system.registrarOperacao(person.getCpf(), "INV-1", new OperacaoFinanceira(LocalDate.of(2026, 4, 1),
                    new BigDecimal("9999"), "Retirada", TipoOperacao.WITHDRAWAL));
        } catch (IllegalArgumentException expected) {
            insufficientFundsRejected = true;
        }
        check(insufficientFundsRejected, "saque sem saldo deve ser rejeitado");

        // Exercita as duas opções de IOF e a progressão das faixas de IR.
        AplicacaoFinanceira taxed = new AplicacaoFinanceira("Tributável", "INV-TAX", new BigDecimal("100000.00"),
                new BigDecimal("0.03"), LocalDate.of(2026, 1, 1));
        taxed.setOpcaoIR(OpcaoIR.EXEMPT);
        taxed.setOpcaoIOF(OpcaoIOF.THREE_POINT_FIVE_PERCENT_ALWAYS);
        taxed.atualizar(LocalDate.of(2026, 1, 2));
        check(taxed.getRendimentoBruto().compareTo(new BigDecimal("100.00")) == 0, "rendimento bruto diário");
        check(taxed.getIOF().compareTo(new BigDecimal("3.50")) == 0, "IOF sempre de 3,5%");
        check(taxed.getIR().signum() == 0, "IR isento");

        taxed.setOpcaoIOF(OpcaoIOF.THREE_POINT_FIVE_PERCENT_BEFORE_30_DAYS);
        check(taxed.getIOF().compareTo(new BigDecimal("3.50")) == 0, "IOF de 3,5% antes de 30 dias");
        taxed.atualizar(LocalDate.of(2026, 2, 2));
        check(taxed.getIOF().signum() == 0, "IOF de 3,5% apenas antes de 30 dias");
        taxed.setOpcaoIR(OpcaoIR.REGRESSIVE);
        check(taxed.getIR().signum() > 0, "IR regressivo tributável");

        boolean invalidMaturityRejected = false;
        try {
            taxed.setDiasPermanenciaMinima(50);
            taxed.setDiasVencimento(40);
        } catch (IllegalArgumentException expected) {
            invalidMaturityRejected = true;
        }
        check(invalidMaturityRejected, "permanência mínima não pode exceder vencimento");

        AplicacaoFinanceira bands = new AplicacaoFinanceira("Faixas IR", "INV-IR", new BigDecimal("100000.00"),
                new BigDecimal("0.03"), LocalDate.of(2026, 1, 1));
        bands.setOpcaoIOF(OpcaoIOF.EXEMPT);
        bands.atualizar(LocalDate.of(2026, 1, 2));
        for (int day = 1; day < 180; day++) bands.incrementarDia();
        check(bands.getIR().compareTo(new BigDecimal("22.50")) == 0, "IR 22,5% até 180 dias");
        bands.incrementarDia();
        check(bands.getIR().compareTo(new BigDecimal("20.00")) == 0, "IR 20% de 181 a 360 dias");
        for (int day = 181; day < 360; day++) bands.incrementarDia();
        check(bands.getIR().compareTo(new BigDecimal("20.00")) == 0, "limite de 360 dias");
        bands.incrementarDia();
        check(bands.getIR().compareTo(new BigDecimal("17.50")) == 0, "IR 17,5% de 361 a 720 dias");
        for (int day = 361; day < 720; day++) bands.incrementarDia();
        check(bands.getIR().compareTo(new BigDecimal("17.50")) == 0, "limite de 720 dias");
        bands.incrementarDia();
        check(bands.getIR().compareTo(new BigDecimal("15.00")) == 0, "IR 15% após 720 dias");

        // Formatos antigos continuam legíveis e recebem defaults documentados.
        Path legacyFile = Path.of(".investment-system-v1-self-test.tsv");
        try {
            Files.write(legacyFile, List.of(
                    "NATIONAL-ECONOMIC\t1",
                    "C\t52998224725\tAna Silva\t11987654321\t12\t3456",
                    "I\t52998224725\tOLD-1\tLegado\t100.00\t0.03\t2026-01-01\t100.00\t0.00\t2026-01-01\t0\t0\t0\tTHIRTY_360\tSTANDARD"
            ));
            SistemaInvestimentos legacy = SistemaInvestimentos.carregar(legacyFile);
            AplicacaoFinanceira oldInvestment = legacy.listarAplicacoes("52998224725").getFirst();
            check(oldInvestment.getPeriodicidadeRendimento() == PeriodicidadeRendimento.DAILY, "defaults aplicados ao formato v1");
            check(oldInvestment.getOpcaoIOF() == OpcaoIOF.THREE_POINT_FIVE_PERCENT_BEFORE_30_DAYS,
                    "opção IOF padrão do formato v1");
        } finally {
            Files.deleteIfExists(legacyFile);
        }

        // O formato intermediário mantém termos, ainda que derive o contador pendente.
        Path version2File = Path.of(".investment-system-v2-self-test.tsv");
        try {
            Files.write(version2File, List.of(
                    "NATIONAL-ECONOMIC\t2",
                    "C\t52998224725\tAna Silva\t11987654321\t12\t3456",
                    "I\t52998224725\tOLD-2\tFormato v2\t100.00\t0.03\t2026-01-01\t100.00\t0.00\t2026-01-01\t0\t0\t0\tTHIRTY_360\tSTANDARD\tMONTHLY_30_DAYS\t180\t30\tMONTHLY\tEXEMPT\tREGRESSIVE"
            ));
            SistemaInvestimentos version2 = SistemaInvestimentos.carregar(version2File);
            AplicacaoFinanceira oldInvestment = version2.listarAplicacoes("52998224725").getFirst();
            check(oldInvestment.getPeriodicidadeRendimento() == PeriodicidadeRendimento.MONTHLY_30_DAYS, "termos v2 preservados");
            check(oldInvestment.getDiasDesdeRendimento() == 0, "contador de rendimento v2 derivado");
        } finally {
            Files.deleteIfExists(version2File);
        }

        // Arquivos corrompidos não podem incluir operação anterior à aplicação.
        Path invalidHistoryFile = Path.of(".investment-system-invalid-history-self-test.tsv");
        try {
            Files.write(invalidHistoryFile, List.of(
                    "NATIONAL-ECONOMIC\t3",
                    "C\t52998224725\tAna Silva\t11987654321\t12\t3456",
                    "I\t52998224725\tINV-INVALID\tConta\t100.00\t0.03\t2026-01-02\t100.00\t0.00\t"
                            + "2026-01-02\t0\t0\t1\tTHIRTY_360\tSTANDARD\tDAILY\t0\t0\tDAILY\t"
                            + "THREE_POINT_FIVE_PERCENT_BEFORE_30_DAYS\tREGRESSIVE\t0.00\t0",
                    "O\t52998224725\tINV-INVALID\t2026-01-01\t1.00\tOperação inválida\tDEPOSIT"
            ));
            boolean invalidHistoryRejected = false;
            try {
                SistemaInvestimentos.carregar(invalidHistoryFile);
            } catch (IOException expected) {
                invalidHistoryRejected = expected.getMessage().contains("linha 4");
            }
            check(invalidHistoryRejected, "operação persistida anterior à aplicação deve ser rejeitada");
        } finally {
            Files.deleteIfExists(invalidHistoryFile);
        }
    }

    /** Falha imediatamente com uma mensagem curta quando uma expectativa não se cumpre. */
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    /** Confirma que a ação fornecida rejeita dados inválidos com erro de validação. */
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

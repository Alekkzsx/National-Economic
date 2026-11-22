package test.integracao;

import static test.util.Afirmacao.*;

import legado.abstracoes.RegistroDatadoLegado;
import legado.modelo.aplicacao.AplicacaoFinanceira;
import legado.modelo.aplicacao.ConvencaoContagemDias;
import legado.modelo.cliente.DadosPessoais;
import legado.modelo.calendario.DataCalendario;
import legado.modelo.aplicacao.FrequenciaResgate;
import legado.modelo.tributacao.OpcaoIOF;
import legado.modelo.tributacao.OpcaoIR;
import legado.modelo.operacao.OperacaoFinanceira;
import legado.modelo.aplicacao.PeriodicidadeRendimento;
import legado.modelo.operacao.TipoOperacao;
import legado.servicos.MigradorSistemaInvestimentos;
import legado.servicos.SistemaInvestimentos;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

/**
 * Testes de integração da camada {@code legado/} e sua migração automática para o
 * {@link servico.sistema.SGI}, além da paridade com o CRUD acadêmico (Itens 7 e 8).
 */
public final class TesteSistemaInvestimentos {

    private TesteSistemaInvestimentos() {
    }

    public static void main(String[] args) throws Exception {
        testarCalendarioEDadosPessoais();
        testarOperacaoExtensivel();

        SistemaInvestimentos sistema = new SistemaInvestimentos();
        DadosPessoais titular = new DadosPessoais("Ana Silva", "529.982.247-25", "(11) 98765-4321");
        AplicacaoFinanceira aplicacao = testarCicloVidaSistemaInvestimentos(sistema, titular);

        testarOperacoesCrud();
        testarMigracaoParaSgiAcademico();
        testarCrudDoSgiAcademico();

        testarPersistenciaSistemaLegado(sistema, titular, aplicacao);
        testarRendimentosPeriodicosENoPrazo();
        testarResgatesETributos(sistema, titular);
        testarLeituraFormatosAntigosEHistoricoInvalido();
    }

    private static void testarCalendarioEDadosPessoais() {
        DataCalendario calendarDate = new DataCalendario(24, 10, 2026);
        afirmarVerdadeiro(calendarDate instanceof RegistroDatadoLegado,
                "DataCalendario estende RegistroDatadoLegado");
        afirmarIgual(0, calendarDate.diaDaSemana(), "data sábado por extenso numérico");
        afirmarIgual("sábado", calendarDate.nomeDoDiaDaSemana(), "nome do dia em pt-BR");
        afirmarIgual(1, calendarDate.diaDaSemana(25, 10, 2026), "overload numérico da data");
        afirmarIgual("segunda-feira", calendarDate.nomeDoDiaDaSemana(26, 10, 2026),
                "overload por extenso da data");
        afirmarRejeicao(() -> calendarDate.setCadastro(31, 2, 2026),
                IllegalArgumentException.class, "data inválida deve ser rejeitada");

        calendarDate.setCadastro(LocalDate.of(2026, 10, 26));
        afirmarVerdadeiro(calendarDate.getDia() == 26 && calendarDate.getMes() == 10
                        && calendarDate.getAno() == 2026 && calendarDate.toString().contains("segunda-feira"),
                "cadastro e getters de data");

        DadosPessoais alternateData = new DadosPessoais("Pessoa", "52998224725");
        afirmarVerdadeiro(alternateData.getCelular().isEmpty(), "overload de cadastro sem celular");
        alternateData.alterarDados("Pessoa Atualizada", "(11) 98765-4321");
        afirmarVerdadeiro("Pessoa Atualizada".equals(alternateData.getNome())
                        && "11987654321".equals(alternateData.getCelular()),
                "sobrecarga de alteração de dados pessoais");

        DadosPessoais person = new DadosPessoais("Ana Silva", "529.982.247-25", "(11) 98765-4321");
        afirmarFalso(person.toString().contains("52998224725"),
                "CPF completo não pode aparecer no toString");
        afirmarIgual(0, AplicacaoFinanceira.indiceDiaSemana(LocalDate.of(2026, 10, 24)),
                "sábado deve mapear para zero");
        afirmarIgual(1, AplicacaoFinanceira.indiceDiaSemana(LocalDate.of(2026, 10, 25)),
                "domingo deve mapear para um");
    }

    private static void testarOperacaoExtensivel() {
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
            public void setCadastro(LocalDate data, BigDecimal valor, String descricao, TipoOperacao tipo) {
                throw new AssertionError("O construtor não deve despachar para métodos sobrescrevíveis.");
            }
        }

        OperacaoFinanceira mutableOperation = new SpecializedOperation();
        afirmarIgual(new BigDecimal("2.00"), new OperationWithOverriddenSetup().valor(),
                "construtor de operação não chama método sobrescrevível");

        mutableOperation.setCadastro(2, 1, 2026, new BigDecimal("3.456"), "  Atualizada  ", TipoOperacao.WITHDRAWAL);
        afirmarVerdadeiro(mutableOperation.data().equals(LocalDate.of(2026, 1, 2))
                        && mutableOperation.getValor().compareTo(new BigDecimal("3.46")) == 0
                        && "Atualizada".equals(mutableOperation.descricao())
                        && mutableOperation.getTipo() == TipoOperacao.WITHDRAWAL,
                "operação extensível, accessors compatíveis e cadastro sobrecarregado");
        afirmarRejeicao(() -> mutableOperation.setValor(BigDecimal.ZERO),
                IllegalArgumentException.class, "valor inválido da operação deve ser rejeitado");
    }

    private static AplicacaoFinanceira testarCicloVidaSistemaInvestimentos(
            SistemaInvestimentos sistema, DadosPessoais person) {
        sistema.cadastrarCliente(person, 12, 3456);
        AplicacaoFinanceira aplicacao = new AplicacaoFinanceira("Reserva", "INV-1", new BigDecimal("100.00"),
                new BigDecimal("0.03"), LocalDate.of(2026, 1, 1));
        aplicacao.setPeriodicidadeRendimento(PeriodicidadeRendimento.QUARTERLY_90_DAYS);
        aplicacao.setDiasVencimento(365);
        aplicacao.setDiasPermanenciaMinima(30);
        aplicacao.setFrequenciaResgate(FrequenciaResgate.MONTHLY);
        aplicacao.setOpcaoIOF(OpcaoIOF.THREE_POINT_FIVE_PERCENT_ALWAYS);
        aplicacao.setOpcaoIR(OpcaoIR.EXEMPT);
        sistema.cadastrarAplicacao(person.getCpf(), aplicacao);

        sistema.atualizarTodos(LocalDate.of(2026, 1, 3)); // sábado: não atualiza
        afirmarIgual(new BigDecimal("100.00"), aplicacao.getSaldo(), "fim de semana sem rendimento");
        sistema.atualizarTodos(LocalDate.of(2026, 1, 2));
        afirmarIgual(new BigDecimal("100.10"), aplicacao.getSaldo(), "rendimento diário arredondado");
        afirmarVerdadeiro(!aplicacao.ehDataDeRendimento() && !aplicacao.podeResgatar(),
                "periodicidade e carência antes do prazo");

        sistema.registrarOperacao(person.getCpf(), "INV-1",
                new OperacaoFinanceira(LocalDate.of(2026, 1, 2), new BigDecimal("5"), "Aporte", TipoOperacao.DEPOSIT));
        afirmarIgual(1, sistema.obterExtrato(person.getCpf(), "INV-1", LocalDate.of(2026, 1, 2),
                LocalDate.of(2026, 1, 2)).size(), "extrato inclusivo");
        return aplicacao;
    }

    private static void testarPersistenciaSistemaLegado(
            SistemaInvestimentos sistema, DadosPessoais person, AplicacaoFinanceira aplicacao) throws IOException {
        Path arquivo = Files.createTempFile("legado-self-test-", ".tsv");
        try {
            sistema.salvar(arquivo);
            SistemaInvestimentos restored = SistemaInvestimentos.carregar(arquivo);
            afirmarIgual(1, restored.listarAplicacoes(person.getCpf()).size(), "investimento restaurado");
            afirmarIgual(sistema.getSaldo(person.getCpf(), "INV-1"), restored.getSaldo(person.getCpf(), "INV-1"),
                    "saldo restaurado");
            afirmarIgual(1, restored.obterExtrato(person.getCpf(), "INV-1", LocalDate.of(2026, 1, 2),
                    LocalDate.of(2026, 1, 2)).size(), "operação restaurada");

            AplicacaoFinanceira restoredInvestment = restored.listarAplicacoes(person.getCpf()).getFirst();
            afirmarIgual(PeriodicidadeRendimento.QUARTERLY_90_DAYS, restoredInvestment.getPeriodicidadeRendimento(),
                    "periodicidade restaurada");
            afirmarIgual(365, restoredInvestment.getDiasVencimento(), "vencimento restaurado");
            afirmarIgual(30, restoredInvestment.getDiasPermanenciaMinima(), "permanência mínima restaurada");
            afirmarIgual(FrequenciaResgate.MONTHLY, restoredInvestment.getFrequenciaResgate(),
                    "frequência de resgate restaurada");
            afirmarIgual(OpcaoIOF.THREE_POINT_FIVE_PERCENT_ALWAYS, restoredInvestment.getOpcaoIOF(),
                    "opção IOF restaurada");
            afirmarIgual(OpcaoIR.EXEMPT, restoredInvestment.getOpcaoIR(), "opção IR restaurada");
            afirmarIgual(aplicacao.getValorResgatado(), restoredInvestment.getValorResgatado(),
                    "total resgatado restaurado");
            afirmarIgual(aplicacao.getRendimentosPendentes(), restoredInvestment.getRendimentosPendentes(),
                    "rendimento pendente restaurado");
            afirmarIgual(aplicacao.getDiasDesdeRendimento(), restoredInvestment.getDiasDesdeRendimento(),
                    "contador de periodicidade restaurado");

            sistema.atualizarTodos(LocalDate.of(2026, 4, 1));
            restored.atualizarTodos(LocalDate.of(2026, 4, 1));
            afirmarIgual(sistema.getSaldo(person.getCpf(), "INV-1"), restored.getSaldo(person.getCpf(), "INV-1"),
                    "capitalização após restauração");
            afirmarIgual(0, aplicacao.getRendimentosPendentes().signum(),
                    "rendimento trimestral capitalizado no dia 90");
        } finally {
            Files.deleteIfExists(arquivo);
        }
    }

    private static void testarRendimentosPeriodicosENoPrazo() {
        AplicacaoFinanceira periodic = new AplicacaoFinanceira("Mensal", "INV-MONTHLY", new BigDecimal("100000.00"),
                new BigDecimal("0.03"), LocalDate.of(2026, 1, 1));
        periodic.setPeriodicidadeRendimento(PeriodicidadeRendimento.MONTHLY_30_DAYS);
        periodic.atualizar(LocalDate.of(2026, 1, 30)); // 29 dias corridos
        afirmarIgual(new BigDecimal("100000.00"), periodic.getSaldoCreditado(),
                "rendimento mensal ainda não capitalizado");
        afirmarVerdadeiro(periodic.getRendimentosPendentes().signum() > 0,
                "rendimento mensal acumulado como pendente");

        periodic.atualizar(LocalDate.of(2026, 2, 2)); // dia 30 caiu no sábado, crédito no próximo dia útil
        afirmarIgual(0, periodic.getRendimentosPendentes().signum(), "rendimento mensal creditado");
        afirmarVerdadeiro(periodic.getSaldoCreditado().compareTo(new BigDecimal("100000.00")) > 0,
                "periodicidade mensal altera a capitalização");

        AplicacaoFinanceira daily = new AplicacaoFinanceira("Diário", "INV-DAILY", new BigDecimal("100000.00"),
                new BigDecimal("0.03"), LocalDate.of(2026, 1, 1));
        daily.atualizar(LocalDate.of(2026, 1, 30));
        daily.atualizar(LocalDate.of(2026, 2, 2));
        afirmarVerdadeiro(daily.getSaldoCreditado().compareTo(periodic.getSaldoCreditado()) > 0,
                "capitalização diária difere da mensal");

        AplicacaoFinanceira atMaturity = new AplicacaoFinanceira("Vencimento", "INV-MATURITY", new BigDecimal("100000.00"),
                new BigDecimal("0.03"), LocalDate.of(2026, 1, 1));
        atMaturity.setDiasVencimento(30);
        atMaturity.setPeriodicidadeRendimento(PeriodicidadeRendimento.AT_MATURITY);
        atMaturity.atualizar(LocalDate.of(2026, 1, 30));
        afirmarIgual(new BigDecimal("100000.00"), atMaturity.getSaldoCreditado(),
                "rendimento de vencimento aguarda a maturidade");

        atMaturity.atualizar(LocalDate.of(2026, 2, 2));
        afirmarVerdadeiro(atMaturity.venceu() && atMaturity.getRendimentosPendentes().signum() == 0
                        && atMaturity.getSaldoCreditado().compareTo(new BigDecimal("100000.00")) > 0,
                "rendimento creditado ao atingir maturidade");
    }

    private static void testarResgatesETributos(SistemaInvestimentos sistema, DadosPessoais person) {
        AplicacaoFinanceira redemption = new AplicacaoFinanceira("Resgate", "INV-REDEEM", new BigDecimal("100.00"),
                BigDecimal.ZERO, LocalDate.of(2026, 1, 1));
        redemption.setDiasPermanenciaMinima(10);
        redemption.setFrequenciaResgate(FrequenciaResgate.MONTHLY);
        afirmarFalso(redemption.podeResgatar(), "canRedeem preserva carência antes do vencimento");
        afirmarRejeicao(() -> redemption.aplicar(new OperacaoFinanceira(LocalDate.of(2026, 1, 10),
                new BigDecimal("1"), "Resgate antecipado", TipoOperacao.WITHDRAWAL)),
                IllegalArgumentException.class, "carência deve ser rejeitada");
        afirmarRejeicao(() -> redemption.aplicar(new OperacaoFinanceira(LocalDate.of(2026, 1, 30),
                new BigDecimal("1"), "Resgate fora da frequência", TipoOperacao.WITHDRAWAL)),
                IllegalArgumentException.class, "frequência mensal deve ser rejeitada");
        redemption.aplicar(new OperacaoFinanceira(LocalDate.of(2026, 1, 31),
                new BigDecimal("1"), "Resgate mensal permitido", TipoOperacao.WITHDRAWAL));
        afirmarRejeicao(() -> redemption.aplicar(new OperacaoFinanceira(LocalDate.of(2026, 2, 1),
                new BigDecimal("1"), "Segundo resgate fora da frequência", TipoOperacao.WITHDRAWAL)),
                IllegalArgumentException.class, "intervalo de resgate deve ser rejeitado");

        afirmarRejeicao(() -> sistema.registrarOperacao(person.getCpf(), "INV-1",
                new OperacaoFinanceira(LocalDate.of(2026, 4, 1),
                        new BigDecimal("9999"), "Retirada", TipoOperacao.WITHDRAWAL)),
                IllegalArgumentException.class, "saque sem saldo deve ser rejeitado");

        // Exercita as duas opções de IOF e a progressão das faixas de IR
        AplicacaoFinanceira taxed = new AplicacaoFinanceira("Tributável", "INV-TAX", new BigDecimal("100000.00"),
                new BigDecimal("0.03"), LocalDate.of(2026, 1, 1));
        taxed.setOpcaoIR(OpcaoIR.EXEMPT);
        taxed.setOpcaoIOF(OpcaoIOF.THREE_POINT_FIVE_PERCENT_ALWAYS);
        taxed.atualizar(LocalDate.of(2026, 1, 2));
        afirmarIgual(new BigDecimal("100.00"), taxed.getRendimentoBruto(), "rendimento bruto diário");
        afirmarIgual(new BigDecimal("3.50"), taxed.getIOF(), "IOF sempre de 3,5%");
        afirmarIgual(0, taxed.getIR().signum(), "IR isento");

        taxed.setOpcaoIOF(OpcaoIOF.THREE_POINT_FIVE_PERCENT_BEFORE_30_DAYS);
        afirmarIgual(new BigDecimal("3.50"), taxed.getIOF(), "IOF de 3,5% antes de 30 dias");
        taxed.atualizar(LocalDate.of(2026, 2, 2));
        afirmarIgual(0, taxed.getIOF().signum(), "IOF de 3,5% apenas antes de 30 dias");
        taxed.setOpcaoIR(OpcaoIR.REGRESSIVE);
        afirmarVerdadeiro(taxed.getIR().signum() > 0, "IR regressivo tributável");

        afirmarRejeicao(() -> {
            taxed.setDiasPermanenciaMinima(50);
            taxed.setDiasVencimento(40);
        }, IllegalArgumentException.class, "permanência mínima não pode exceder vencimento");

        AplicacaoFinanceira bands = new AplicacaoFinanceira("Faixas IR", "INV-IR", new BigDecimal("100000.00"),
                new BigDecimal("0.03"), LocalDate.of(2026, 1, 1));
        bands.setOpcaoIOF(OpcaoIOF.EXEMPT);
        bands.atualizar(LocalDate.of(2026, 1, 2));
        for (int day = 1; day < 180; day++) bands.incrementarDia();
        afirmarIgual(new BigDecimal("22.50"), bands.getIR(), "IR 22,5% até 180 dias");
        bands.incrementarDia();
        afirmarIgual(new BigDecimal("20.00"), bands.getIR(), "IR 20% de 181 a 360 dias");
        for (int day = 181; day < 360; day++) bands.incrementarDia();
        afirmarIgual(new BigDecimal("20.00"), bands.getIR(), "limite de 360 dias");
        bands.incrementarDia();
        afirmarIgual(new BigDecimal("17.50"), bands.getIR(), "IR 17,5% de 361 a 720 dias");
        for (int day = 361; day < 720; day++) bands.incrementarDia();
        afirmarIgual(new BigDecimal("17.50"), bands.getIR(), "limite de 720 dias");
        bands.incrementarDia();
        afirmarIgual(new BigDecimal("15.00"), bands.getIR(), "IR 15% após 720 dias");
    }

    private static void testarLeituraFormatosAntigosEHistoricoInvalido() throws IOException {
        Path legacyFile = Files.createTempFile("legado-v1-", ".tsv");
        Path version2File = Files.createTempFile("legado-v2-", ".tsv");
        Path invalidHistoryFile = Files.createTempFile("legado-invalid-", ".tsv");
        try {
            Files.write(legacyFile, List.of(
                    "NATIONAL-ECONOMIC\t1",
                    "C\t52998224725\tAna Silva\t11987654321\t12\t3456",
                    "I\t52998224725\tOLD-1\tLegado\t100.00\t0.03\t2026-01-01\t100.00\t0.00\t2026-01-01\t0\t0\t0\tTHIRTY_360\tSTANDARD"
            ));
            SistemaInvestimentos legacy = SistemaInvestimentos.carregar(legacyFile);
            AplicacaoFinanceira oldV1 = legacy.listarAplicacoes("52998224725").getFirst();
            afirmarIgual(PeriodicidadeRendimento.DAILY, oldV1.getPeriodicidadeRendimento(),
                    "defaults aplicados ao formato v1");
            afirmarIgual(OpcaoIOF.THREE_POINT_FIVE_PERCENT_BEFORE_30_DAYS, oldV1.getOpcaoIOF(),
                    "opção IOF padrão do formato v1");

            Files.write(version2File, List.of(
                    "NATIONAL-ECONOMIC\t2",
                    "C\t52998224725\tAna Silva\t11987654321\t12\t3456",
                    "I\t52998224725\tOLD-2\tFormato v2\t100.00\t0.03\t2026-01-01\t100.00\t0.00\t2026-01-01\t0\t0\t0\tTHIRTY_360\tSTANDARD\tMONTHLY_30_DAYS\t180\t30\tMONTHLY\tEXEMPT\tREGRESSIVE"
            ));
            SistemaInvestimentos version2 = SistemaInvestimentos.carregar(version2File);
            AplicacaoFinanceira oldV2 = version2.listarAplicacoes("52998224725").getFirst();
            afirmarIgual(PeriodicidadeRendimento.MONTHLY_30_DAYS, oldV2.getPeriodicidadeRendimento(),
                    "termos v2 preservados");
            afirmarIgual(0, oldV2.getDiasDesdeRendimento(), "contador de rendimento v2 derivado");

            Files.write(invalidHistoryFile, List.of(
                    "NATIONAL-ECONOMIC\t3",
                    "C\t52998224725\tAna Silva\t11987654321\t12\t3456",
                    "I\t52998224725\tINV-INVALID\tConta\t100.00\t0.03\t2026-01-02\t100.00\t0.00\t"
                            + "2026-01-02\t0\t0\t1\tTHIRTY_360\tSTANDARD\tDAILY\t0\t0\tDAILY\t"
                            + "THREE_POINT_FIVE_PERCENT_BEFORE_30_DAYS\tREGRESSIVE\t0.00\t0",
                    "O\t52998224725\tINV-INVALID\t2026-01-01\t1.00\tOperação inválida\tDEPOSIT"
            ));
            try {
                SistemaInvestimentos.carregar(invalidHistoryFile);
                throw new AssertionError("Esperava IOException para operação anterior à aplicação.");
            } catch (IOException esperado) {
                afirmarVerdadeiro(esperado.getMessage().contains("linha 4"),
                        "operação persistida anterior à aplicação deve ser rejeitada indicando a linha 4");
            }
        } finally {
            Files.deleteIfExists(legacyFile);
            Files.deleteIfExists(version2File);
            Files.deleteIfExists(invalidHistoryFile);
        }
    }

    /** Valida atualização e exclusão de clientes, aplicações e lançamentos financeiros. */
    private static void testarOperacoesCrud() {
        SistemaInvestimentos sistema = new SistemaInvestimentos();
        DadosPessoais dados = new DadosPessoais("Cliente CRUD", "52998224725", "11987654321");
        sistema.cadastrarCliente(dados, 12, 3456);
        AplicacaoFinanceira aplicacao = new AplicacaoFinanceira(
                "Reserva", "CRUD-1", new BigDecimal("100.00"), BigDecimal.ZERO,
                LocalDate.of(2026, 1, 1));
        sistema.cadastrarAplicacao(dados.getCpf(), aplicacao);

        sistema.alterarDadosCliente(dados.getCpf(), "Cliente Atualizado", "11999998888");
        afirmarIgual("Cliente Atualizado", dados.getNome(), "dados pessoais do cliente atualizados");

        sistema.alterarAplicacao(dados.getCpf(), "CRUD-1", "Reserva Atualizada", new BigDecimal("0.01"));
        afirmarIgual("Reserva Atualizada", aplicacao.getNome(), "nome da aplicação atualizado");
        afirmarIgual(new BigDecimal("0.01"), aplicacao.getTaxaMensal(), "taxa mensal da aplicação atualizada");

        sistema.registrarOperacao(dados.getCpf(), "CRUD-1", new OperacaoFinanceira(
                LocalDate.of(2026, 1, 2), new BigDecimal("20.00"), "Depósito", TipoOperacao.DEPOSIT));
        sistema.registrarOperacao(dados.getCpf(), "CRUD-1", new OperacaoFinanceira(
                LocalDate.of(2026, 1, 2), new BigDecimal("5.00"), "Retirada", TipoOperacao.WITHDRAWAL));
        afirmarIgual(new BigDecimal("115.00"), aplicacao.getSaldoCreditado(),
                "operações alteram o saldo e registram retiradas");
        afirmarIgual(new BigDecimal("5.00"), aplicacao.getValorResgatado(),
                "total de retiradas é contabilizado");

        afirmarRejeicao(() -> sistema.alterarOperacao(dados.getCpf(), "CRUD-1", 0, new OperacaoFinanceira(
                LocalDate.of(2026, 1, 2), new BigDecimal("1000.00"), "Retirada inválida",
                TipoOperacao.WITHDRAWAL)),
                IllegalArgumentException.class, "edição financeira inválida é rejeitada");
        afirmarIgual("Depósito", sistema.listarOperacoes(dados.getCpf(), "CRUD-1").getFirst().descricao(),
                "edição inválida preserva o lançamento original");
        afirmarIgual(new BigDecimal("115.00"), aplicacao.getSaldoCreditado(),
                "edição inválida restaura o saldo original");

        sistema.alterarOperacao(dados.getCpf(), "CRUD-1", 0, new OperacaoFinanceira(
                LocalDate.of(2026, 1, 2), new BigDecimal("30.00"), "Depósito corrigido", TipoOperacao.DEPOSIT));
        afirmarIgual(new BigDecimal("125.00"), aplicacao.getSaldoCreditado(),
                "edição válida recalcula o efeito do lançamento");
        sistema.excluirOperacao(dados.getCpf(), "CRUD-1", 1);
        afirmarIgual(new BigDecimal("130.00"), aplicacao.getSaldoCreditado(),
                "exclusão da retirada recompõe o saldo");
        afirmarIgual(0, aplicacao.getValorResgatado().signum(), "exclusão da retirada ajusta o total resgatado");
        sistema.excluirOperacao(dados.getCpf(), "CRUD-1", 0);
        afirmarIgual(new BigDecimal("100.00"), aplicacao.getSaldoCreditado(),
                "exclusão do depósito reverte seu efeito financeiro");

        sistema.excluirAplicacao(dados.getCpf(), "CRUD-1");
        afirmarVerdadeiro(sistema.listarAplicacoes(dados.getCpf()).isEmpty(), "aplicação excluída da carteira");
        AplicacaoFinanceira novaAplicacao = new AplicacaoFinanceira(
                "Nova reserva", "CRUD-1", new BigDecimal("50.00"), BigDecimal.ZERO,
                LocalDate.of(2026, 1, 1));
        sistema.cadastrarAplicacao(dados.getCpf(), novaAplicacao);
        afirmarVerdadeiro(sistema.listarOperacoes(dados.getCpf(), "CRUD-1").isEmpty(),
                "nova aplicação não herda operações da aplicação excluída");
        sistema.excluirCliente(dados.getCpf());
        afirmarVerdadeiro(sistema.listarClientes().isEmpty(), "cliente e carteira removidos do SGI");
    }

    /** Confere a conversão integral do armazenamento anterior para o SGI acadêmico. */
    private static void testarMigracaoParaSgiAcademico() throws IOException {
        DadosPessoais dados = new DadosPessoais("Migração", "52998224725", "11987654321");
        SistemaInvestimentos legado = new SistemaInvestimentos();
        legado.cadastrarCliente(dados, 12, 3456);
        AplicacaoFinanceira aplicacao = new AplicacaoFinanceira(
                "Reserva migrada", "MIG-1", new BigDecimal("100.00"),
                new BigDecimal("0.03"), LocalDate.of(2026, 1, 1));
        aplicacao.setConvencaoContagemDias(ConvencaoContagemDias.ACTUAL_365);
        aplicacao.setPeriodicidadeRendimento(PeriodicidadeRendimento.MONTHLY_30_DAYS);
        aplicacao.setFrequenciaResgate(FrequenciaResgate.MONTHLY);
        aplicacao.setOpcaoIOF(OpcaoIOF.THREE_POINT_FIVE_PERCENT_ALWAYS);
        aplicacao.setOpcaoIR(OpcaoIR.EXEMPT);
        legado.cadastrarAplicacao(dados.getCpf(), aplicacao);
        legado.atualizarTodos(LocalDate.of(2026, 1, 2));
        legado.registrarOperacao(dados.getCpf(), "MIG-1", new OperacaoFinanceira(
                LocalDate.of(2026, 1, 2), new BigDecimal("25.00"), "Aporte migrado", TipoOperacao.DEPOSIT));

        Path arquivoAntigo = Files.createTempFile("sgi-legado-", ".tsv");
        Path arquivoNovo = Files.createTempFile("sgi-migrado-", ".tsv");
        Path backup = arquivoAntigo.resolveSibling(
                arquivoAntigo.getFileName() + ".antes-da-migracao.bak");
        try {
            legado.salvar(arquivoAntigo);
            byte[] conteudoAntigo = Files.readAllBytes(arquivoAntigo);
            servico.sistema.SGI migrado = MigradorSistemaInvestimentos.carregarComMigracao(arquivoAntigo);
            afirmarVerdadeiro(Arrays.equals(conteudoAntigo, Files.readAllBytes(backup)),
                    "migração preserva uma cópia byte a byte do arquivo anterior");
            afirmarIgual("NATIONAL-ECONOMIC\t3", Files.readAllLines(arquivoAntigo).getFirst(),
                    "arquivo principal foi convertido para o formato SGI");
            dominio.operacao.MultiplasOperacoesFinanceiras carteira =
                    migrado.requererCliente(dados.getCpf()).requererAplicacao("MIG-1");

            afirmarIgual(1, migrado.getQuantidadeClientes(), "migração preserva o cliente");
            afirmarIgual(dominio.aplicacao.ConvencaoContagemDias.CIVIL_365, carteira.getAplicacao().getConvencaoContagemDias(),
                    "migração converte a convenção de dias");
            afirmarVerdadeiro(carteira.getAplicacao().getTipoRendimento() == 1
                            && carteira.getAplicacao().getTipoResgate() == 1,
                    "migração preserva periodicidades");
            afirmarVerdadeiro(carteira.getAplicacao().getIof() == 2
                            && carteira.getAplicacao().getTipoImpostoRenda() == 0,
                    "migração preserva as políticas tributárias");
            afirmarIgual(aplicacao.getRendimentosPendentes(), carteira.getAplicacao().getRendimentosPendentes(),
                    "migração preserva rendimentos pendentes");
            afirmarIgual(aplicacao.getSaldo(), carteira.getSaldo(),
                    "migração preserva o saldo já contabilizado");
            afirmarIgual(1, carteira.getOperacoes().size(), "migração preserva o histórico de operações");

            migrado.arquivoSalvarDados(arquivoNovo);
            servico.sistema.SGI restaurado = servico.sistema.SGI.arquivoLerDados(arquivoNovo);
            afirmarIgual(migrado.getSaldo(dados.getCpf(), "MIG-1"), restaurado.getSaldo(dados.getCpf(), "MIG-1"),
                    "estado migrado permanece íntegro após salvar e carregar");
        } finally {
            Files.deleteIfExists(arquivoAntigo);
            Files.deleteIfExists(arquivoNovo);
            Files.deleteIfExists(backup);
        }
    }

    /** Valida que o CRUD do terminal opera diretamente sobre a fachada acadêmica SGI. */
    private static void testarCrudDoSgiAcademico() {
        servico.sistema.SGI sgi = new servico.sistema.SGI();
        dominio.cliente.Cliente cliente = new dominio.cliente.Cliente(
                "Cliente SGI", "52998224725", "11987654321", 12, 3456);
        sgi.cadastrarCliente(cliente);
        dominio.aplicacao.AplicacaoFinanceira aplicacao = new dominio.aplicacao.AplicacaoFinanceira(
                "Reserva", "SGI-CRUD", new BigDecimal("100.00"), BigDecimal.ZERO,
                LocalDate.of(2026, 1, 1));
        sgi.cadastrarAplicacao(cliente.getDadosPessoais().getCpf(), aplicacao);

        sgi.alterarCliente(cliente.getDadosPessoais().getCpf(), "Cliente Atualizado", "11999998888");
        sgi.alterarAplicacao(cliente.getDadosPessoais().getCpf(), "SGI-CRUD",
                "Reserva Atualizada", new BigDecimal("0.01"));
        afirmarIgual("Cliente Atualizado", cliente.getDadosPessoais().getNome(),
                "SGI atualiza os dados do cliente");
        afirmarIgual("Reserva Atualizada", aplicacao.getNomeAplicacao(),
                "SGI atualiza os dados descritivos da aplicação");

        sgi.cadastrarOperacao(cliente.getDadosPessoais().getCpf(), "SGI-CRUD",
                new dominio.operacao.OperacaoFinanceira(LocalDate.of(2026, 1, 2), new BigDecimal("20.00"),
                        "Aporte", dominio.operacao.TipoOperacao.DEPOSITO));
        sgi.cadastrarOperacao(cliente.getDadosPessoais().getCpf(), "SGI-CRUD",
                new dominio.operacao.OperacaoFinanceira(LocalDate.of(2026, 1, 2), new BigDecimal("5.00"),
                        "Resgate", dominio.operacao.TipoOperacao.RETIRADA));
        sgi.alterarOperacao(cliente.getDadosPessoais().getCpf(), "SGI-CRUD", 0,
                new dominio.operacao.OperacaoFinanceira(LocalDate.of(2026, 1, 2), new BigDecimal("30.00"),
                        "Aporte corrigido", dominio.operacao.TipoOperacao.DEPOSITO));
        afirmarIgual(new BigDecimal("125.00"), sgi.getSaldo(cliente.getDadosPessoais().getCpf(), "SGI-CRUD"),
                "SGI recalcula o saldo após edição de operação");

        sgi.excluirOperacao(cliente.getDadosPessoais().getCpf(), "SGI-CRUD", 1);
        afirmarIgual(new BigDecimal("130.00"), sgi.getSaldo(cliente.getDadosPessoais().getCpf(), "SGI-CRUD"),
                "SGI reverte o efeito da operação excluída");
        sgi.atualizarAplicacoes(LocalDate.of(2026, 1, 5));
        afirmarRejeicao(() -> sgi.alterarOperacao(cliente.getDadosPessoais().getCpf(), "SGI-CRUD", 0,
                new dominio.operacao.OperacaoFinanceira(LocalDate.of(2026, 1, 2),
                        new BigDecimal("40.00"), "Alteração tardia", dominio.operacao.TipoOperacao.DEPOSITO)),
                IllegalStateException.class, "SGI bloqueia edição depois da apuração de rendimentos");

        sgi.excluirAplicacao(cliente.getDadosPessoais().getCpf(), "SGI-CRUD");
        afirmarVerdadeiro(sgi.getAplicacoes(cliente.getDadosPessoais().getCpf()).isEmpty(),
                "SGI remove a aplicação da carteira");
        sgi.excluirCliente(cliente.getDadosPessoais().getCpf());
        afirmarIgual(0, sgi.getQuantidadeClientes(), "SGI remove o cliente e seus dados vinculados");
    }
}

package test.dominio;

import static test.Afirmacao.*;

import dominio.AplicacaoFinanceira;
import dominio.ConvencaoContagemDias;
import dominio.DiaSemana;
import dominio.MultiplasOperacoesFinanceiras;
import dominio.OperacaoFinanceira;
import dominio.RegimeTributario;
import dominio.TipoOperacao;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

/**
 * Suíte de testes autônomos para {@link MultiplasOperacoesFinanceiras} (Item 4, Requisito R2.1).
 *
 * <p>Valida a agregação de aplicação, calendário e até 100 operações financeiras,
 * a restrição de atualização em dias úteis (segunda a sexta-feira), extratos polimórficos,
 * consultas de saldo e a persistência em disco via arquivoSalvarDados e arquivoLerDados.</p>
 */
public final class TesteMultiplasOperacoes {

    public static void main(String[] args) {
        System.out.println(">>> Executando TesteMultiplasOperacoes (Item 4 - Múltiplas Operações Financeiras)...");
        long inicio = System.currentTimeMillis();
        int testesExecutados = 0;

        testarInstanciacaoEGetters(); testesExecutados++;
        testarRejeicaoInstanciacaoNula(); testesExecutados++;
        testarAdicionarOperacaoDeposito(); testesExecutados++;
        testarAdicionarOperacaoRetiradaValida(); testesExecutados++;
        testarRejeicaoRetiradaSaldoInsuficiente(); testesExecutados++;
        testarRejeicaoRetiradaAntesDaCarencia(); testesExecutados++;
        testarRejeicaoRetiradaForaFrequencia(); testesExecutados++;
        testarLimiteMaximo100Operacoes(); testesExecutados++;
        testarRejeicaoOperacaoDataAnteriorAplicacao(); testesExecutados++;
        testarAtualizarAplicacaoFimDeSemanaNoOp(); testesExecutados++;
        testarAtualizarAplicacaoDiaUtilEvolucao(); testesExecutados++;
        testarExtrato30Dias(); testesExecutados++;
        testarJanelaDeExatamenteTrintaDias(); testesExecutados++;
        testarExtratoPeriodoPolimorfico(); testesExecutados++;
        testarPersistenciaArquivoSalvarELerDados(); testesExecutados++;
        testarPersistenciaPreservaCicloDeRendimento(); testesExecutados++;
        testarHerancaSeguraESobrescritaToString(); testesExecutados++;

        long duracao = System.currentTimeMillis() - inicio;
        System.out.printf(">>> TesteMultiplasOperacoes CONCLUÍDO com SUCESSO: %d métodos verificados (%d ms)%n%n",
                testesExecutados, duracao);
    }

    private static AplicacaoFinanceira criarAplicacaoPadrao(LocalDate dataInicio) {
        return new AplicacaoFinanceira(
                "CDB Flex Premium", "APP-101",
                new BigDecimal("1000.00"), new BigDecimal("0.0300"),
                0, 0, 0, 0, 1, 1, dataInicio
        );
    }

    public static void testarInstanciacaoEGetters() {
        LocalDate data = LocalDate.of(2026, 8, 3); // Segunda-feira
        AplicacaoFinanceira terminal = criarAplicacaoPadrao(data);
        MultiplasOperacoesFinanceiras mof = new MultiplasOperacoesFinanceiras(terminal);

        afirmarIgual("APP-101", mof.getNumeroAplicacao(), "número da aplicação coincide");
        afirmarIgual("CDB Flex Premium", mof.getNomeAplicacao(), "nome da aplicação coincide");
        afirmarIgual(0, mof.getQuantidadeOperacoes(), "inicia com 0 operações");
        afirmarVerdadeiro(mof.getOperacoes().isEmpty(), "lista de operações vazia");
        afirmarNaoNulo(mof.getCalendario(), "calendário não pode ser nulo");
        afirmarIgual(terminal.getValorAtualLiquido(), mof.getSaldo(), "saldo coincide com aplicação");
    }

    public static void testarRejeicaoInstanciacaoNula() {
        boolean lancou = false;
        try {
            new MultiplasOperacoesFinanceiras(null);
        } catch (IllegalArgumentException e) {
            lancou = true;
        }
        afirmarVerdadeiro(lancou, "deve lançar exceção ao instanciar com aplicação nula");
    }

    public static void testarAdicionarOperacaoDeposito() {
        LocalDate data = LocalDate.of(2026, 8, 3);
        AplicacaoFinanceira terminal = criarAplicacaoPadrao(data);
        MultiplasOperacoesFinanceiras mof = new MultiplasOperacoesFinanceiras(terminal);

        BigDecimal saldoInicial = mof.getSaldo();
        OperacaoFinanceira dep = new OperacaoFinanceira(data, new BigDecimal("500.00"), "Aporte Inicial", TipoOperacao.DEPOSITO);
        mof.adicionarOperacao(dep);

        afirmarIgual(1, mof.getQuantidadeOperacoes(), "quantidade de operações deve ser 1");
        afirmarIgual(1, terminal.getQuantidadeOperacoes(), "quantidade de operações na aplicação deve ser 1");
        afirmarIgual(saldoInicial.add(new BigDecimal("500.00")), mof.getSaldo(), "saldo deve aumentar em 500");
    }

    public static void testarAdicionarOperacaoRetiradaValida() {
        LocalDate data = LocalDate.of(2026, 8, 3);
        AplicacaoFinanceira terminal = criarAplicacaoPadrao(data);
        MultiplasOperacoesFinanceiras mof = new MultiplasOperacoesFinanceiras(terminal);

        OperacaoFinanceira ret = new OperacaoFinanceira(data, new BigDecimal("300.00"), "Resgate Parcial", TipoOperacao.RETIRADA);
        mof.adicionarOperacao(ret);

        afirmarIgual(1, mof.getQuantidadeOperacoes(), "quantidade de operações é 1");
        afirmarIgual(new BigDecimal("700.00"), mof.getSaldo(), "saldo deve reduzir para 700.00");
        afirmarIgual(new BigDecimal("300.00"), mof.getValorResgate(), "valor resgate deve ser 300.00");
    }

    public static void testarRejeicaoRetiradaSaldoInsuficiente() {
        LocalDate data = LocalDate.of(2026, 8, 3);
        AplicacaoFinanceira terminal = criarAplicacaoPadrao(data);
        MultiplasOperacoesFinanceiras mof = new MultiplasOperacoesFinanceiras(terminal);

        OperacaoFinanceira retExcessiva = new OperacaoFinanceira(data, new BigDecimal("1500.00"), "Resgate Excessivo", TipoOperacao.RETIRADA);
        boolean lancou = false;
        try {
            mof.adicionarOperacao(retExcessiva);
        } catch (IllegalArgumentException e) {
            lancou = true;
        }
        afirmarVerdadeiro(lancou, "deve rejeitar retirada com saldo insuficiente");
        afirmarIgual(0, mof.getQuantidadeOperacoes(), "operação não deve ter sido inserida");
        afirmarIgual(new BigDecimal("1000.00"), mof.getSaldo(), "saldo permanece íntegro");
    }

    public static void testarRejeicaoRetiradaAntesDaCarencia() {
        LocalDate dataInicio = LocalDate.of(2026, 8, 3);
        AplicacaoFinanceira terminal = new AplicacaoFinanceira(
                "LCI 90 Dias", "APP-202",
                new BigDecimal("5000.00"), new BigDecimal("0.025"),
                0, 180, 90, 0, 0, 0, dataInicio
        );
        MultiplasOperacoesFinanceiras mof = new MultiplasOperacoesFinanceiras(terminal);

        // Tentativa de resgate no dia 30 (carência é de 90 dias)
        LocalDate dataPrecoce = dataInicio.plusDays(30);
        OperacaoFinanceira retPrecoce = new OperacaoFinanceira(dataPrecoce, new BigDecimal("1000.00"), "Resgate Antecipado", TipoOperacao.RETIRADA);

        boolean lancou = false;
        try {
            mof.adicionarOperacao(retPrecoce);
        } catch (IllegalArgumentException e) {
            lancou = true;
        }
        afirmarVerdadeiro(lancou, "deve rejeitar resgate antes da permanência mínima");
        afirmarIgual(0, mof.getQuantidadeOperacoes(), "operação antes da carência não é registrada");
    }

    public static void testarRejeicaoRetiradaForaFrequencia() {
        LocalDate dataInicio = LocalDate.of(2026, 8, 3);
        // tipoResgate = 1 (Mensal, a cada 30 dias)
        AplicacaoFinanceira terminal = new AplicacaoFinanceira(
                "CDB Mensal", "APP-303",
                new BigDecimal("2000.00"), new BigDecimal("0.020"),
                0, 0, 0, 1, 0, 0, dataInicio
        );
        MultiplasOperacoesFinanceiras mof = new MultiplasOperacoesFinanceiras(terminal);

        // Dia 15 não é múltiplo de 30
        LocalDate dataInvalida = dataInicio.plusDays(15);
        OperacaoFinanceira retForaJanela = new OperacaoFinanceira(dataInvalida, new BigDecimal("500.00"), "Resgate Dia 15", TipoOperacao.RETIRADA);

        boolean lancou = false;
        try {
            mof.adicionarOperacao(retForaJanela);
        } catch (IllegalArgumentException e) {
            lancou = true;
        }
        afirmarVerdadeiro(lancou, "deve rejeitar resgate fora da frequência configurada");
    }

    public static void testarLimiteMaximo100Operacoes() {
        LocalDate data = LocalDate.of(2026, 8, 3);
        AplicacaoFinanceira terminal = criarAplicacaoPadrao(data);
        MultiplasOperacoesFinanceiras mof = new MultiplasOperacoesFinanceiras(terminal);

        for (int i = 1; i <= 100; i++) {
            OperacaoFinanceira op = new OperacaoFinanceira(data, new BigDecimal("1.00"), "Depósito #" + i, TipoOperacao.DEPOSITO);
            mof.adicionarOperacao(op);
        }
        afirmarIgual(100, mof.getQuantidadeOperacoes(), "atingiu 100 operações com sucesso");

        // 101ª operação deve falhar
        OperacaoFinanceira op101 = new OperacaoFinanceira(data, new BigDecimal("1.00"), "Depósito 101", TipoOperacao.DEPOSITO);
        boolean lancou = false;
        try {
            mof.adicionarOperacao(op101);
        } catch (IllegalStateException e) {
            lancou = true;
        }
        afirmarVerdadeiro(lancou, "101ª operação deve lançar IllegalStateException");
    }

    public static void testarRejeicaoOperacaoDataAnteriorAplicacao() {
        LocalDate dataInicio = LocalDate.of(2026, 8, 10);
        AplicacaoFinanceira terminal = criarAplicacaoPadrao(dataInicio);
        MultiplasOperacoesFinanceiras mof = new MultiplasOperacoesFinanceiras(terminal);

        LocalDate dataAnterior = LocalDate.of(2026, 8, 9);
        OperacaoFinanceira opPassada = new OperacaoFinanceira(dataAnterior, new BigDecimal("100.00"), "Depósito Passado", TipoOperacao.DEPOSITO);

        boolean lancou = false;
        try {
            mof.adicionarOperacao(opPassada);
        } catch (IllegalArgumentException e) {
            lancou = true;
        }
        afirmarVerdadeiro(lancou, "deve rejeitar operação com data anterior ao início da aplicação");
    }

    public static void testarAtualizarAplicacaoFimDeSemanaNoOp() {
        LocalDate dataInicio = LocalDate.of(2026, 7, 31); // Sexta-feira
        AplicacaoFinanceira terminal = criarAplicacaoPadrao(dataInicio);
        MultiplasOperacoesFinanceiras mof = new MultiplasOperacoesFinanceiras(terminal);

        BigDecimal saldoAntes = mof.getSaldo();

        // Sábado 01/08/2026 e Domingo 02/08/2026
        mof.atualizarAplicacao(LocalDate.of(2026, 8, 1));
        afirmarIgual(saldoAntes, mof.getSaldo(), "atualização em sábado não altera saldo");
        afirmarIgual(0, terminal.getDiasCorridos(), "dias corridos não são avançados indevidamente em sábado isolado");

        mof.atualizarAplicacao(LocalDate.of(2026, 8, 2));
        afirmarIgual(saldoAntes, mof.getSaldo(), "atualização em domingo não altera saldo");
    }

    public static void testarAtualizarAplicacaoDiaUtilEvolucao() {
        LocalDate dataInicio = LocalDate.of(2026, 8, 3); // Segunda-feira
        AplicacaoFinanceira terminal = criarAplicacaoPadrao(dataInicio);
        MultiplasOperacoesFinanceiras mof = new MultiplasOperacoesFinanceiras(terminal);

        BigDecimal saldoInicial = mof.getSaldo();

        // Atualiza para Terça-feira (2026-08-04) - dia útil
        mof.atualizarAplicacao(LocalDate.of(2026, 8, 4));
        afirmarVerdadeiro(mof.getSaldoBruto().compareTo(saldoInicial) > 0, "saldo bruto evoluiu em dia útil");
        afirmarIgual(1, terminal.getDiasEfetivosCalculo(),
                "somente a terça-feira é dia útil efetivo; o cadastro é dia zero");
    }

    public static void testarExtrato30Dias() {
        LocalDate dataRef = LocalDate.of(2026, 8, 31);
        AplicacaoFinanceira terminal = criarAplicacaoPadrao(LocalDate.of(2026, 6, 1));
        MultiplasOperacoesFinanceiras mof = new MultiplasOperacoesFinanceiras(terminal, new DiaSemana(dataRef));

        // Operação recente (15 dias atrás)
        OperacaoFinanceira opRecente = new OperacaoFinanceira(LocalDate.of(2026, 8, 16), new BigDecimal("100.00"), "Recente", TipoOperacao.DEPOSITO);
        // Operação antiga (60 dias atrás)
        OperacaoFinanceira opAntiga = new OperacaoFinanceira(LocalDate.of(2026, 7, 1), new BigDecimal("200.00"), "Antiga", TipoOperacao.DEPOSITO);

        mof.adicionarOperacao(opAntiga);
        mof.adicionarOperacao(opRecente);

        List<OperacaoFinanceira> extrato30d = mof.getExtrato();
        afirmarIgual(1, extrato30d.size(), "extrato 30 dias contém apenas a operação recente");
        afirmarIgual("Recente", extrato30d.get(0).getDescricao(), "descrição da operação recente bate");
    }

    /** Confere as duas extremidades inclusivas de uma janela de trinta datas civis. */
    public static void testarJanelaDeExatamenteTrintaDias() {
        LocalDate dataFinal = LocalDate.of(2026, 8, 31);
        AplicacaoFinanceira aplicacao = criarAplicacaoPadrao(LocalDate.of(2026, 6, 1));
        MultiplasOperacoesFinanceiras agregacao =
                new MultiplasOperacoesFinanceiras(aplicacao, new DiaSemana(dataFinal));
        agregacao.adicionarOperacao(new OperacaoFinanceira(
                dataFinal.minusDays(30), new BigDecimal("1.00"), "Fora da janela", TipoOperacao.DEPOSITO));
        agregacao.adicionarOperacao(new OperacaoFinanceira(
                dataFinal.minusDays(29), new BigDecimal("1.00"), "Primeiro dia", TipoOperacao.DEPOSITO));
        agregacao.adicionarOperacao(new OperacaoFinanceira(
                dataFinal, new BigDecimal("1.00"), "Último dia", TipoOperacao.DEPOSITO));

        List<OperacaoFinanceira> extrato = agregacao.getExtrato(dataFinal);
        afirmarIgual(2, extrato.size(), "a janela inclui exatamente trinta datas civis");
        afirmarIgual("Primeiro dia", extrato.getFirst().getDescricao(),
                "a data inicial inclusiva é exatamente 29 dias antes da final");
    }

    public static void testarExtratoPeriodoPolimorfico() {
        LocalDate dataInicio = LocalDate.of(2026, 8, 1);
        AplicacaoFinanceira terminal = criarAplicacaoPadrao(dataInicio);
        MultiplasOperacoesFinanceiras mof = new MultiplasOperacoesFinanceiras(terminal);

        mof.adicionarOperacao(new OperacaoFinanceira(LocalDate.of(2026, 8, 5), new BigDecimal("10.00"), "Op 5", TipoOperacao.DEPOSITO));
        mof.adicionarOperacao(new OperacaoFinanceira(LocalDate.of(2026, 8, 15), new BigDecimal("20.00"), "Op 15", TipoOperacao.DEPOSITO));
        mof.adicionarOperacao(new OperacaoFinanceira(LocalDate.of(2026, 8, 25), new BigDecimal("30.00"), "Op 25", TipoOperacao.DEPOSITO));

        List<OperacaoFinanceira> extratoMeio = mof.getExtrato(LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 20));
        afirmarIgual(1, extratoMeio.size(), "apenas Op 15 está entre dias 10 e 20");
        afirmarIgual("Op 15", extratoMeio.get(0).getDescricao(), "descrição correta");

        // Teste de sobrecarga polimórfica inteira
        List<OperacaoFinanceira> extratoPolimorfico = mof.getExtrato(1, 8, 2026, 31, 8, 2026);
        afirmarIgual(3, extratoPolimorfico.size(), "extrato inteiro recupera todas as 3 operações");

        // Rejeição de período invertido
        boolean lancou = false;
        try {
            mof.getExtrato(LocalDate.of(2026, 8, 20), LocalDate.of(2026, 8, 10));
        } catch (IllegalArgumentException e) {
            lancou = true;
        }
        afirmarVerdadeiro(lancou, "deve rejeitar extrato com data final anterior à inicial");
    }

    public static void testarPersistenciaArquivoSalvarELerDados() {
        LocalDate dataInicio = LocalDate.of(2026, 8, 3);
        AplicacaoFinanceira terminal = criarAplicacaoPadrao(dataInicio);
        MultiplasOperacoesFinanceiras mof = new MultiplasOperacoesFinanceiras(terminal);

        mof.adicionarOperacao(new OperacaoFinanceira(dataInicio, new BigDecimal("250.00"), "Aporte #1", TipoOperacao.DEPOSITO));
        mof.adicionarOperacao(new OperacaoFinanceira(dataInicio.plusDays(1), new BigDecimal("50.00"), "Resgate #1", TipoOperacao.RETIRADA));

        Path tempFile = Path.of("data/teste_mof_temp.db");
        try {
            mof.arquivoSalvarDados(tempFile);
            afirmarVerdadeiro(Files.exists(tempFile), "arquivo de persistência foi gravado");

            MultiplasOperacoesFinanceiras restaurado = MultiplasOperacoesFinanceiras.arquivoLerDados(tempFile);
            afirmarIgual(mof.getNumeroAplicacao(), restaurado.getNumeroAplicacao(), "número da aplicação restaurado");
            afirmarIgual(mof.getSaldo(), restaurado.getSaldo(), "saldo líquido restaurado com precisão");
            afirmarIgual(2, restaurado.getQuantidadeOperacoes(), "quantidade de operações preservada");
            afirmarIgual("Aporte #1", restaurado.getOperacoes().get(0).getDescricao(), "primeira operação restaurada");
            afirmarIgual("Resgate #1", restaurado.getOperacoes().get(1).getDescricao(), "segunda operação restaurada");
        } catch (IOException e) {
            throw new AssertionError("Falha na persistência de arquivo: " + e.getMessage(), e);
        } finally {
            try {
                Files.deleteIfExists(tempFile);
            } catch (IOException ignored) {}
        }

    }

    /** Confere que a persistência mantém o ciclo de rendimento para atualizações futuras. */
    public static void testarPersistenciaPreservaCicloDeRendimento() {
        LocalDate inicio = LocalDate.of(2026, 8, 3);
        AplicacaoFinanceira aplicacao = new AplicacaoFinanceira(
                "Aplicação mensal", "APP-CICLO",
                new BigDecimal("1000.00"), new BigDecimal("0.03"),
                1, 0, 0, 0, 0, 0, inicio
        );
        aplicacao.setConvencaoContagemDias(ConvencaoContagemDias.CIVIL_365);
        aplicacao.setRegimeTributario(RegimeTributario.ISENTO);
        MultiplasOperacoesFinanceiras original = new MultiplasOperacoesFinanceiras(aplicacao);
        original.atualizarAplicacao(inicio.plusDays(3));

        Path arquivo = Path.of("data/teste_mof_ciclo_temp.db");
        try {
            original.arquivoSalvarDados(arquivo);
            MultiplasOperacoesFinanceiras restaurada =
                    MultiplasOperacoesFinanceiras.arquivoLerDados(arquivo);
            afirmarIgual(aplicacao.getRendimentosCreditados(),
                    restaurada.getAplicacao().getRendimentosCreditados(),
                    "rendimentos creditados são restaurados");
            afirmarIgual(aplicacao.getRendimentosPendentes(),
                    restaurada.getAplicacao().getRendimentosPendentes(),
                    "rendimentos pendentes são restaurados");
            afirmarIgual(aplicacao.getDiasDesdeUltimoRendimento(),
                    restaurada.getAplicacao().getDiasDesdeUltimoRendimento(),
                    "contador do ciclo de rendimento é restaurado");
            afirmarIgual(ConvencaoContagemDias.CIVIL_365,
                    restaurada.getAplicacao().getConvencaoContagemDias(),
                    "convenção de dias é restaurada");
            afirmarIgual(RegimeTributario.ISENTO,
                    restaurada.getAplicacao().getRegimeTributario(),
                    "regime tributário é restaurado");

            LocalDate proximaAtualizacao = inicio.plusDays(4);
            original.atualizarAplicacao(proximaAtualizacao);
            restaurada.atualizarAplicacao(proximaAtualizacao);
            afirmarIgual(original.getSaldoBruto(), restaurada.getSaldoBruto(),
                    "as duas instâncias evoluem com o mesmo saldo após a restauração");
            afirmarIgual(original.getAplicacao().getRendimentosPendentes(),
                    restaurada.getAplicacao().getRendimentosPendentes(),
                    "as duas instâncias mantêm o mesmo rendimento pendente após atualizar");
        } catch (IOException excecao) {
            throw new AssertionError("Falha ao validar a restauração do ciclo de rendimento.", excecao);
        } finally {
            try {
                Files.deleteIfExists(arquivo);
            } catch (IOException excecao) {
                throw new AssertionError("Falha ao remover o arquivo de teste.", excecao);
            }
        }
    }

    public static void testarHerancaSeguraESobrescritaToString() {
        AplicacaoFinanceira terminal = criarAplicacaoPadrao(LocalDate.of(2026, 8, 3));

        // Subclasse para validar herança segura e construtores
        class SubMultiplasOperacoes extends MultiplasOperacoesFinanceiras {
            final String marca = "ESPECIALIZADA";
            SubMultiplasOperacoes(AplicacaoFinanceira a) {
                super(a);
            }
        }

        SubMultiplasOperacoes sub = new SubMultiplasOperacoes(terminal);
        afirmarIgual("ESPECIALIZADA", sub.marca, "subclasse inicializada sem corrupção");

        String str = sub.toString();
        afirmarVerdadeiro(str.contains("APP-101"), "toString contém o identificador da aplicação");
        afirmarVerdadeiro(str.contains("operacoes="), "toString contém contagem de operações");
    }
}

package test.dominio;

import static test.Afirmacao.*;

import dominio.AplicacaoFinanceira;
import dominio.Cliente;
import dominio.DADOSPESSOAIS;
import dominio.MultiplasOperacoesFinanceiras;
import dominio.OperacaoFinanceira;
import dominio.TipoOperacao;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

/**
 * Suíte de testes autônomos para a classe {@link Cliente} (Item 6, Requisito R2.3).
 *
 * <p>Cobre exaustivamente os 5 Tiers de qualidade definidos em TEST_INFRA.md:
 * contratos básicos, limites regulamentares (máx. 50 aplicações), unicidade de identificadores,
 * atualização em dias úteis, apuração de saldos, geração polimórfica de extratos ordenados,
 * persistência em arquivo atômica TSV v3 e herança segura.</p>
 */
public final class TesteCliente {

    public static void main(String[] args) {
        System.out.println(">>> Executando TesteCliente (Item 6 - Gestão de Contas, Extratos e Persistência)...");
        long inicio = System.currentTimeMillis();
        int testesExecutados = 0;

        // Tier 1: Happy Path e Contratos Básicos
        testarInstanciacaoEGettersSetters(); testesExecutados++;
        testarAdicionarAplicacaoUnica(); testesExecutados++;
        testarAdicionarMultiplasAplicacoes(); testesExecutados++;
        testarConsultaSaldoIndividual(); testesExecutados++;
        testarConsultaSaldoConsolidado(); testesExecutados++;
        testarAtualizarAplicacoesDiaUtil(); testesExecutados++;

        // Tier 2: Casos de Fronteira e Canto (Boundary / Edge Cases)
        testarLimiteMaximoCinquentaAplicacoes(); testesExecutados++;
        testarRejeicaoCinquentaEPrimeiraAplicacao(); testesExecutados++;
        testarRejeicaoAplicacaoDuplicada(); testesExecutados++;
        testarSaldoConsolidadoClienteSemAplicacoes(); testesExecutados++;
        testarConsultaSaldoAplicacaoInexistente(); testesExecutados++;
        testarRejeicaoAgenciaEContaZeroOuNegativas(); testesExecutados++;
        testarRejeicaoDadosPessoaisNulo(); testesExecutados++;
        testarRejeicaoAdicionarAplicacaoNula(); testesExecutados++;
        testarImutabilidadeColecaoAplicacoes(); testesExecutados++;

        // Tier 3: Integração Par a Par (Cross-Feature Pairwise)
        testarIntegracaoComDadosPessoaisEAlteracao(); testesExecutados++;
        testarIntegracaoComDiaSemanaAtualizacaoFimDeSemana(); testesExecutados++;
        testarExtratoIndividualUltimos30Dias(); testesExecutados++;
        testarExtratoIndividualPorIntervaloPolimorfico(); testesExecutados++;
        testarExtratoConsolidadoUltimos30Dias(); testesExecutados++;
        testarExtratoConsolidadoPorIntervaloPolimorfico(); testesExecutados++;
        testarRejeicaoIntervaloDatasInvertidoOuNulo(); testesExecutados++;

        // Tier 4: Cenários do Mundo Real e Aceitação Ponta a Ponta
        testarCenarioCarteiraDiversificadaCompleta(); testesExecutados++;
        testarPersistenciaArquivoSalvarELerDados(); testesExecutados++;

        // Tier 5: Robustez, Adversarial e Herança Segura
        testarHerancaSegura(); testesExecutados++;
        testarRejeicaoLeituraArquivoInvalidoOuCorrompido(); testesExecutados++;
        testarRejeicaoRegistrosTruncados(); testesExecutados++;
        testarSobrescritaToStringComCpfMascarado(); testesExecutados++;

        long duracao = System.currentTimeMillis() - inicio;
        System.out.printf(">>> TesteCliente CONCLUÍDO com SUCESSO: %d métodos verificados (%d ms)%n%n",
                testesExecutados, duracao);
    }

    // =========================================================================
    // Tier 1: Happy Path e Contratos Básicos
    // =========================================================================

    public static void testarInstanciacaoEGettersSetters() {
        DADOSPESSOAIS dados = new DADOSPESSOAIS("Carlos Drummond", "52998224725", "11999998888");
        Cliente cliente = new Cliente(dados, 1001, 20005L);

        afirmarIgual(1001, cliente.getNumeroAgencia(), "número da agência deve ser 1001");
        afirmarIgual(20005L, cliente.getNumeroConta(), "número da conta deve ser 20005");
        afirmarIgual(dados, cliente.getDadosPessoais(), "dados pessoais devem corresponder");
        afirmarIgual(0, cliente.getQuantidadeAplicacoes(), "inicia com 0 aplicações");

        cliente.setNumeroAgencia(2002);
        cliente.setNumeroConta(30006L);
        afirmarIgual(2002, cliente.getNumeroAgencia(), "agência atualizada");
        afirmarIgual(30006L, cliente.getNumeroConta(), "conta atualizada");
    }

    public static void testarAdicionarAplicacaoUnica() {
        Cliente cliente = criarClientePadrao();
        AplicacaoFinanceira af = new AplicacaoFinanceira("CDB Flex", "101", new BigDecimal("1000.00"), new BigDecimal("0.01"));
        MultiplasOperacoesFinanceiras multi = new MultiplasOperacoesFinanceiras(af);

        cliente.adicionarAplicacao(multi);
        afirmarIgual(1, cliente.getQuantidadeAplicacoes(), "deve conter 1 aplicação");
        afirmarVerdadeiro(cliente.buscarAplicacao("101").isPresent(), "deve localizar aplicação 101");
    }

    public static void testarAdicionarMultiplasAplicacoes() {
        Cliente cliente = criarClientePadrao();
        cliente.adicionarAplicacao(new MultiplasOperacoesFinanceiras(
                new AplicacaoFinanceira("CDB", "1", new BigDecimal("1000.00"), new BigDecimal("0.01"))));
        cliente.adicionarAplicacao(new MultiplasOperacoesFinanceiras(
                new AplicacaoFinanceira("LCI", "2", new BigDecimal("2000.00"), new BigDecimal("0.012"))));
        cliente.adicionarAplicacao(new MultiplasOperacoesFinanceiras(
                new AplicacaoFinanceira("Poupança", "3", new BigDecimal("500.00"), new BigDecimal("0.005"))));

        afirmarIgual(3, cliente.getQuantidadeAplicacoes(), "deve conter 3 aplicações");
        afirmarVerdadeiro(cliente.buscarAplicacao("2").isPresent(), "LCI localizada");
    }

    public static void testarConsultaSaldoIndividual() {
        Cliente cliente = criarClientePadrao();
        AplicacaoFinanceira af = new AplicacaoFinanceira("CDB", "101", new BigDecimal("5000.00"), new BigDecimal("0.01"));
        cliente.adicionarAplicacao(new MultiplasOperacoesFinanceiras(af));

        afirmarIgual(af.getValorAtualLiquido(), cliente.getSaldo("101"), "saldo individual deve bater com a aplicação");
    }

    public static void testarConsultaSaldoConsolidado() {
        Cliente cliente = criarClientePadrao();
        AplicacaoFinanceira af1 = new AplicacaoFinanceira("CDB 1", "1", new BigDecimal("1000.00"), new BigDecimal("0.01"));
        AplicacaoFinanceira af2 = new AplicacaoFinanceira("CDB 2", "2", new BigDecimal("2500.00"), new BigDecimal("0.01"));
        AplicacaoFinanceira af3 = new AplicacaoFinanceira("CDB 3", "3", new BigDecimal("1500.00"), new BigDecimal("0.01"));

        cliente.adicionarAplicacao(new MultiplasOperacoesFinanceiras(af1));
        cliente.adicionarAplicacao(new MultiplasOperacoesFinanceiras(af2));
        cliente.adicionarAplicacao(new MultiplasOperacoesFinanceiras(af3));

        BigDecimal esperado = af1.getValorAtualLiquido().add(af2.getValorAtualLiquido()).add(af3.getValorAtualLiquido());
        afirmarIgual(esperado, cliente.getSaldoConsolidado(), "saldo consolidado é a soma exata dos saldos individuais");
    }

    public static void testarAtualizarAplicacoesDiaUtil() {
        Cliente cliente = criarClientePadrao();
        LocalDate dataInicio = LocalDate.of(2026, 11, 2); // Segunda-feira
        AplicacaoFinanceira af1 = new AplicacaoFinanceira("CDB", "1", new BigDecimal("10000.00"), new BigDecimal("0.03"), dataInicio);
        AplicacaoFinanceira af2 = new AplicacaoFinanceira("LCI", "2", new BigDecimal("10000.00"), new BigDecimal("0.03"), dataInicio);

        cliente.adicionarAplicacao(new MultiplasOperacoesFinanceiras(af1));
        cliente.adicionarAplicacao(new MultiplasOperacoesFinanceiras(af2));

        LocalDate dataProximoDia = LocalDate.of(2026, 11, 3); // Terça-feira
        cliente.atualizarAplicacoes(dataProximoDia);

        afirmarVerdadeiro(af1.getDiasEfetivosCalculo() > 0, "af1 deve ter dias de cálculo incrementados");
        afirmarVerdadeiro(af2.getDiasEfetivosCalculo() > 0, "af2 deve ter dias de cálculo incrementados");
    }

    // =========================================================================
    // Tier 2: Fronteira, Limites e Casos de Canto
    // =========================================================================

    public static void testarLimiteMaximoCinquentaAplicacoes() {
        Cliente cliente = criarClientePadrao();
        for (int i = 1; i <= 50; i++) {
            AplicacaoFinanceira af = new AplicacaoFinanceira("App " + i, String.valueOf(i), new BigDecimal("100.00"), new BigDecimal("0.01"));
            cliente.adicionarAplicacao(new MultiplasOperacoesFinanceiras(af));
        }
        afirmarIgual(50, cliente.getQuantidadeAplicacoes(), "deve aceitar exatamente 50 aplicações");
    }

    public static void testarRejeicaoCinquentaEPrimeiraAplicacao() {
        Cliente cliente = criarClientePadrao();
        for (int i = 1; i <= 50; i++) {
            AplicacaoFinanceira af = new AplicacaoFinanceira("App " + i, String.valueOf(i), new BigDecimal("100.00"), new BigDecimal("0.01"));
            cliente.adicionarAplicacao(new MultiplasOperacoesFinanceiras(af));
        }
        AplicacaoFinanceira app51 = new AplicacaoFinanceira("App 51", "51", new BigDecimal("100.00"), new BigDecimal("0.01"));
        afirmarRejeicao(() -> cliente.adicionarAplicacao(new MultiplasOperacoesFinanceiras(app51)),
                IllegalStateException.class, "51ª aplicação deve ser rejeitada com IllegalStateException");
    }

    public static void testarRejeicaoAplicacaoDuplicada() {
        Cliente cliente = criarClientePadrao();
        cliente.adicionarAplicacao(new MultiplasOperacoesFinanceiras(
                new AplicacaoFinanceira("CDB", "ABC", new BigDecimal("1000.00"), new BigDecimal("0.01"))));

        afirmarRejeicao(() -> cliente.adicionarAplicacao(new MultiplasOperacoesFinanceiras(
                new AplicacaoFinanceira("Outro CDB", "ABC", new BigDecimal("500.00"), new BigDecimal("0.01")))),
                IllegalArgumentException.class, "número de aplicação duplicado deve ser rejeitado");
    }

    public static void testarSaldoConsolidadoClienteSemAplicacoes() {
        Cliente cliente = criarClientePadrao();
        afirmarIgual(BigDecimal.ZERO, cliente.getSaldoConsolidado(), "cliente sem aplicações retorna saldo 0.00");
    }

    public static void testarConsultaSaldoAplicacaoInexistente() {
        Cliente cliente = criarClientePadrao();
        afirmarRejeicao(() -> cliente.getSaldo("999"), IllegalArgumentException.class,
                "consultar saldo de aplicação inexistente deve lançar exceção");
    }

    public static void testarRejeicaoAgenciaEContaZeroOuNegativas() {
        DADOSPESSOAIS dados = new DADOSPESSOAIS("Maria Silva", "52998224725", "11987654321");
        afirmarRejeicao(() -> new Cliente(dados, 0, 100L), IllegalArgumentException.class, "agência 0 rejeitada");
        afirmarRejeicao(() -> new Cliente(dados, -1, 100L), IllegalArgumentException.class, "agência negativa rejeitada");
        afirmarRejeicao(() -> new Cliente(dados, 1, 0L), IllegalArgumentException.class, "conta 0 rejeitada");
        afirmarRejeicao(() -> new Cliente(dados, 1, -50L), IllegalArgumentException.class, "conta negativa rejeitada");
    }

    public static void testarRejeicaoDadosPessoaisNulo() {
        afirmarRejeicao(() -> new Cliente(null, 1, 100L), NullPointerException.class, "dados pessoais nulos rejeitados");
    }

    public static void testarRejeicaoAdicionarAplicacaoNula() {
        Cliente cliente = criarClientePadrao();
        afirmarRejeicao(() -> cliente.adicionarAplicacao((MultiplasOperacoesFinanceiras) null),
                NullPointerException.class, "aplicação nula rejeitada");
    }

    public static void testarImutabilidadeColecaoAplicacoes() {
        Cliente cliente = criarClientePadrao();
        cliente.adicionarAplicacao(new MultiplasOperacoesFinanceiras(
                new AplicacaoFinanceira("CDB", "1", new BigDecimal("1000.00"), new BigDecimal("0.01"))));

        List<MultiplasOperacoesFinanceiras> lista = cliente.getAplicacoes();
        afirmarRejeicao(() -> lista.clear(), UnsupportedOperationException.class, "lista externa deve ser imutável");
    }

    // =========================================================================
    // Tier 3: Integração Par a Par (Cross-Feature Pairwise)
    // =========================================================================

    public static void testarIntegracaoComDadosPessoaisEAlteracao() {
        Cliente cliente = criarClientePadrao();
        cliente.getDadosPessoais().alterarDados("Maria Santos Atualizada", "11911112222");

        afirmarIgual("Maria Santos Atualizada", cliente.getDadosPessoais().getNome(), "nome do titular refletido");
        afirmarIgual("11911112222", cliente.getDadosPessoais().getCelular(), "celular do titular refletido");
    }

    public static void testarIntegracaoComDiaSemanaAtualizacaoFimDeSemana() {
        Cliente cliente = criarClientePadrao();
        LocalDate sexta = LocalDate.of(2026, 11, 6);
        AplicacaoFinanceira af = new AplicacaoFinanceira("CDB", "1", new BigDecimal("10000.00"), new BigDecimal("0.03"), sexta);
        cliente.adicionarAplicacao(new MultiplasOperacoesFinanceiras(af));

        // Atualização em um Sábado (07/11/2026)
        LocalDate sabado = LocalDate.of(2026, 11, 7);
        cliente.atualizarAplicacoes(sabado);

        // Em finais de semana não há computação de rendimento útil
        afirmarIgual(0, af.getDiasEfetivosCalculo(), "dias de cálculo não devem incrementar no sábado");
    }

    public static void testarExtratoIndividualUltimos30Dias() {
        Cliente cliente = criarClientePadrao();
        LocalDate hoje = LocalDate.of(2026, 11, 1);
        AplicacaoFinanceira af = new AplicacaoFinanceira("CDB", "1", new BigDecimal("1000.00"), new BigDecimal("0.01"), hoje.minusDays(60));
        MultiplasOperacoesFinanceiras multi = new MultiplasOperacoesFinanceiras(af);

        // Operação fora da janela de 30 dias (40 dias atrás)
        multi.adicionarOperacao(new OperacaoFinanceira(hoje.minusDays(40), new BigDecimal("100.00"), "Antiga", TipoOperacao.DEPOSITO));
        // Operações dentro da janela (15 dias e 5 dias atrás)
        multi.adicionarOperacao(new OperacaoFinanceira(hoje.minusDays(15), new BigDecimal("200.00"), "Recente 1", TipoOperacao.DEPOSITO));
        multi.adicionarOperacao(new OperacaoFinanceira(hoje.minusDays(5), new BigDecimal("50.00"), "Recente 2", TipoOperacao.RETIRADA));

        cliente.adicionarAplicacao(multi);
        cliente.atualizarAplicacoes(hoje);

        List<OperacaoFinanceira> extrato30d = cliente.getExtrato("1");
        afirmarIgual(2, extrato30d.size(), "apenas operações nos últimos 30 dias devem retornar");
        afirmarIgual("Recente 1", extrato30d.get(0).getDescricao(), "primeira recente");
        afirmarIgual("Recente 2", extrato30d.get(1).getDescricao(), "segunda recente");
    }

    public static void testarExtratoIndividualPorIntervaloPolimorfico() {
        Cliente cliente = criarClientePadrao();
        AplicacaoFinanceira af = new AplicacaoFinanceira("CDB", "1", new BigDecimal("1000.00"), new BigDecimal("0.01"), LocalDate.of(2026, 10, 1));
        MultiplasOperacoesFinanceiras multi = new MultiplasOperacoesFinanceiras(af);

        multi.adicionarOperacao(new OperacaoFinanceira(LocalDate.of(2026, 10, 10), new BigDecimal("100.00"), "Op 1", TipoOperacao.DEPOSITO));
        multi.adicionarOperacao(new OperacaoFinanceira(LocalDate.of(2026, 10, 20), new BigDecimal("200.00"), "Op 2", TipoOperacao.DEPOSITO));
        multi.adicionarOperacao(new OperacaoFinanceira(LocalDate.of(2026, 10, 30), new BigDecimal("300.00"), "Op 3", TipoOperacao.DEPOSITO));

        cliente.adicionarAplicacao(multi);

        List<OperacaoFinanceira> intervalo = cliente.getExtrato("1", LocalDate.of(2026, 10, 15), LocalDate.of(2026, 10, 25));
        afirmarIgual(1, intervalo.size(), "somente Op 2 cai no intervalo");
        afirmarIgual("Op 2", intervalo.get(0).getDescricao(), "descrição de Op 2");
    }

    public static void testarExtratoConsolidadoUltimos30Dias() {
        Cliente cliente = criarClientePadrao();
        LocalDate base = LocalDate.of(2026, 11, 1);

        MultiplasOperacoesFinanceiras app1 = new MultiplasOperacoesFinanceiras(
                new AplicacaoFinanceira("CDB", "1", new BigDecimal("1000.00"), new BigDecimal("0.01"), base.minusDays(60)));
        app1.adicionarOperacao(new OperacaoFinanceira(base.minusDays(10), new BigDecimal("100.00"), "App1-Op", TipoOperacao.DEPOSITO));

        MultiplasOperacoesFinanceiras app2 = new MultiplasOperacoesFinanceiras(
                new AplicacaoFinanceira("LCI", "2", new BigDecimal("2000.00"), new BigDecimal("0.01"), base.minusDays(60)));
        app2.adicionarOperacao(new OperacaoFinanceira(base.minusDays(5), new BigDecimal("200.00"), "App2-Op", TipoOperacao.DEPOSITO));

        cliente.adicionarAplicacao(app1);
        cliente.adicionarAplicacao(app2);
        cliente.atualizarAplicacoes(base);

        List<OperacaoFinanceira> consolidado = cliente.getExtratoConsolidado();
        afirmarIgual(2, consolidado.size(), "consolidado reúne as operações de ambas as aplicações");
        afirmarIgual("App1-Op", consolidado.get(0).getDescricao(), "ordenação cronológica: 10 dias atrás antes de 5 dias");
        afirmarIgual("App2-Op", consolidado.get(1).getDescricao(), "ordenação cronológica mantida");
    }

    public static void testarExtratoConsolidadoPorIntervaloPolimorfico() {
        Cliente cliente = criarClientePadrao();

        MultiplasOperacoesFinanceiras app1 = new MultiplasOperacoesFinanceiras(
                new AplicacaoFinanceira("CDB", "1", new BigDecimal("1000.00"), new BigDecimal("0.01"), LocalDate.of(2026, 9, 1)));
        app1.adicionarOperacao(new OperacaoFinanceira(LocalDate.of(2026, 9, 1), new BigDecimal("10.00"), "Fora", TipoOperacao.DEPOSITO));
        app1.adicionarOperacao(new OperacaoFinanceira(LocalDate.of(2026, 10, 15), new BigDecimal("20.00"), "No Prazo A", TipoOperacao.DEPOSITO));

        MultiplasOperacoesFinanceiras app2 = new MultiplasOperacoesFinanceiras(
                new AplicacaoFinanceira("LCI", "2", new BigDecimal("1000.00"), new BigDecimal("0.01"), LocalDate.of(2026, 9, 1)));
        app2.adicionarOperacao(new OperacaoFinanceira(LocalDate.of(2026, 10, 10), new BigDecimal("30.00"), "No Prazo B", TipoOperacao.DEPOSITO));

        cliente.adicionarAplicacao(app1);
        cliente.adicionarAplicacao(app2);

        List<OperacaoFinanceira> consolidado = cliente.getExtratoConsolidado(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));
        afirmarIgual(2, consolidado.size(), "2 operações no intervalo");
        afirmarIgual("No Prazo B", consolidado.get(0).getDescricao(), "10 de outubro antecede 15 de outubro");
        afirmarIgual("No Prazo A", consolidado.get(1).getDescricao(), "segunda operação cronológica");
    }

    public static void testarRejeicaoIntervaloDatasInvertidoOuNulo() {
        Cliente cliente = criarClientePadrao();
        afirmarRejeicao(() -> cliente.getExtratoConsolidado(null, LocalDate.now()), NullPointerException.class, "início nulo rejeitado");
        afirmarRejeicao(() -> cliente.getExtratoConsolidado(LocalDate.now(), null), NullPointerException.class, "fim nulo rejeitado");
        afirmarRejeicao(() -> cliente.getExtratoConsolidado(LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 10)),
                IllegalArgumentException.class, "intervalo invertido rejeitado");
    }

    // =========================================================================
    // Tier 4: Cenários do Mundo Real e Aceitação Ponta a Ponta
    // =========================================================================

    public static void testarCenarioCarteiraDiversificadaCompleta() {
        Cliente cliente = criarClientePadrao();
        LocalDate dataInicial = LocalDate.of(2026, 8, 3); // Segunda-feira

        // 1. Renda Fixa Liquidez Diária
        AplicacaoFinanceira cdb = new AplicacaoFinanceira("CDB DI", "1", new BigDecimal("10000.00"), new BigDecimal("0.01"), dataInicial);
        MultiplasOperacoesFinanceiras multiCdb = new MultiplasOperacoesFinanceiras(cdb);
        multiCdb.adicionarOperacao(new OperacaoFinanceira(dataInicial, new BigDecimal("10000.00"), "Aporte Inicial CDB", TipoOperacao.DEPOSITO));

        // 2. LCI Isenta de IR
        AplicacaoFinanceira lci = new AplicacaoFinanceira("LCI 90d", "2", new BigDecimal("20000.00"), new BigDecimal("0.012"), dataInicial);
        lci.setTipoImpostoRenda(0); // Isento
        MultiplasOperacoesFinanceiras multiLci = new MultiplasOperacoesFinanceiras(lci);
        multiLci.adicionarOperacao(new OperacaoFinanceira(dataInicial, new BigDecimal("20000.00"), "Aporte Inicial LCI", TipoOperacao.DEPOSITO));

        cliente.adicionarAplicacao(multiCdb);
        cliente.adicionarAplicacao(multiLci);

        // Simulação de avanço de 1 mês útil (30 dias corridos)
        LocalDate dataAtual = dataInicial;
        for (int i = 0; i < 30; i++) {
            dataAtual = dataAtual.plusDays(1);
            cliente.atualizarAplicacoes(dataAtual);
        }

        BigDecimal saldoConsolidado = cliente.getSaldoConsolidado();
        afirmarVerdadeiro(saldoConsolidado.compareTo(new BigDecimal("30000.00")) > 0,
                "saldo consolidado após 30 dias deve ser superior aos aportes iniciais");
    }

    public static void testarPersistenciaArquivoSalvarELerDados() {
        try {
            Cliente cliente = criarClientePadrao();
            LocalDate data = LocalDate.of(2026, 10, 1);
            AplicacaoFinanceira af = new AplicacaoFinanceira("CDB Ouro", "101", new BigDecimal("15000.00"), new BigDecimal("0.015"), data);
            MultiplasOperacoesFinanceiras multi = new MultiplasOperacoesFinanceiras(af);
            multi.adicionarOperacao(new OperacaoFinanceira(data, new BigDecimal("15000.00"), "Aporte Inicial", TipoOperacao.DEPOSITO));
            multi.adicionarOperacao(new OperacaoFinanceira(data.plusDays(5), new BigDecimal("1000.00"), "Depósito Extra", TipoOperacao.DEPOSITO));

            cliente.adicionarAplicacao(multi);

            Path tempFile = Files.createTempFile("teste_cliente_", ".tsv");
            try {
                // Gravação
                cliente.arquivoSalvarDados(tempFile);
                afirmarVerdadeiro(Files.exists(tempFile), "arquivo gravado deve existir");
                afirmarVerdadeiro(Files.size(tempFile) > 0, "arquivo não deve estar vazio");

                // Leitura e reconstrução
                Cliente restaurado = Cliente.arquivoLerDados(tempFile);

                afirmarIgual(cliente.getNumeroAgencia(), restaurado.getNumeroAgencia(), "agência conferida");
                afirmarIgual(cliente.getNumeroConta(), restaurado.getNumeroConta(), "conta conferida");
                afirmarIgual(cliente.getDadosPessoais().getNome(), restaurado.getDadosPessoais().getNome(), "nome conferido");
                afirmarIgual(cliente.getDadosPessoais().getCpf(), restaurado.getDadosPessoais().getCpf(), "CPF conferido");
                afirmarIgual(1, restaurado.getQuantidadeAplicacoes(), "1 aplicação restaurada");

                MultiplasOperacoesFinanceiras appRestaurada = restaurado.requererAplicacao("101");
                afirmarIgual("CDB Ouro", appRestaurada.getAplicacao().getNomeAplicacao(), "nome da aplicação preservado");
                afirmarIgual(2, appRestaurada.getOperacoes().size(), "2 operações restauradas");
            } finally {
                Files.deleteIfExists(tempFile);
            }
        } catch (IOException e) {
            throw new AssertionError("Falha de E/S na persistência de Cliente: " + e.getMessage(), e);
        }
    }

    // =========================================================================
    // Tier 5: Robustez, Adversarial e Herança Segura
    // =========================================================================

    public static void testarHerancaSegura() {
        class ClientePrivateBanking extends Cliente {
            private final String segmento;

            ClientePrivateBanking(DADOSPESSOAIS dados, int agencia, long conta, String segmento) {
                super(dados, agencia, conta);
                this.segmento = segmento;
            }

            public String getSegmento() { return segmento; }
        }

        DADOSPESSOAIS dados = new DADOSPESSOAIS("Investidor Qualificado", "52998224725", "11988887777");
        ClientePrivateBanking sub = new ClientePrivateBanking(dados, 5001, 88888L, "Ultra High Net Worth");

        afirmarIgual("Ultra High Net Worth", sub.getSegmento(), "subclasse inicializada sem efeitos colaterais");
        afirmarIgual(5001, sub.getNumeroAgencia(), "agência preservada na subclasse");
    }

    public static void testarRejeicaoLeituraArquivoInvalidoOuCorrompido() {
        try {
            Path tempCorrompido = Files.createTempFile("teste_corrompido_", ".tsv");
            try {
                Files.writeString(tempCorrompido, "CABECALHO_DESCONHECIDO\t1\n");
                afirmarRejeicao(() -> {
                    try {
                        Cliente.arquivoLerDados(tempCorrompido);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }, IllegalArgumentException.class, "cabeçalho inválido deve ser rejeitado");
            } finally {
                Files.deleteIfExists(tempCorrompido);
            }
        } catch (IOException e) {
            throw new AssertionError("Erro inesperado no teste de corrupção: " + e.getMessage(), e);
        }
    }

    public static void testarRejeicaoRegistrosTruncados() {
        try {
            Path tempTruncado = Files.createTempFile("teste_truncado_cli_", ".tsv");
            try {
                // Linha I truncada (< 23 colunas, ex: 4 colunas)
                Files.writeString(tempTruncado,
                        "NATIONAL-ECONOMIC\t3\n" +
                        "C\t52998224725\tNome\t11999998888\t1001\t20002\n" +
                        "I\t52998224725\tAPP-1\tCDB\n");
                afirmarRejeicao(() -> {
                    try {
                        Cliente.arquivoLerDados(tempTruncado);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }, IllegalArgumentException.class, "registro I com menos de 23 colunas deve lançar IllegalArgumentException");

                // Linha O truncada (< 7 colunas, ex: 4 colunas)
                Files.writeString(tempTruncado,
                        "NATIONAL-ECONOMIC\t3\n" +
                        "C\t52998224725\tNome\t11999998888\t1001\t20002\n" +
                        "I\t52998224725\t101\tCDB\t1000.00\t0.01\t2026-01-01\t1000.00\t1000.00\t2026-01-01\t0\t0\t0\tCOMERCIAL_30_360\tPADRAO\t0\t0\t0\t0\t0\t0\t0.00\t1000.00\n" +
                        "O\t52998224725\t101\t2026-01-01\n");
                afirmarRejeicao(() -> {
                    try {
                        Cliente.arquivoLerDados(tempTruncado);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }, IllegalArgumentException.class, "registro O com menos de 7 colunas deve lançar IllegalArgumentException");
            } finally {
                Files.deleteIfExists(tempTruncado);
            }
        } catch (IOException e) {
            throw new AssertionError("Erro inesperado no teste de registros truncados: " + e.getMessage(), e);
        }
    }

    public static void testarSobrescritaToStringComCpfMascarado() {
        Cliente cliente = criarClientePadrao();
        String str = cliente.toString();
        afirmarVerdadeiro(str.contains("Cliente{"), "formato de classe");
        afirmarVerdadeiro(str.contains("***.***.***-"), "CPF deve estar mascarado no toString()");
    }

    // =========================================================================
    // Métodos Utilitários Internos
    // =========================================================================

    private static Cliente criarClientePadrao() {
        DADOSPESSOAIS dados = new DADOSPESSOAIS("Ana Carolina", "52998224725", "11987654321");
        return new Cliente(dados, 1001, 55555L);
    }
}

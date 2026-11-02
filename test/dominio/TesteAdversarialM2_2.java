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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Suíte de Testes Adversariais Empíricos para o Milestone 2 (Item 6 - Cliente e Integrações M2).
 *
 * <p>Executa testes de estresse rigorosos nos seguintes eixos críticos:</p>
 * <ol>
 *   <li>Limite estrito de 50 aplicações e rejeição da 51ª;</li>
 *   <li>Garantia de unicidade de identificador de aplicação na conta;</li>
 *   <li>Ordenação cronológica estrita dos extratos consolidados;</li>
 *   <li>Persistência TSV v3, paridade bit a bit e resiliência sob corrupção/truncamento;</li>
 *   <li>Integração sistêmica cruzada entre Cliente, MOF, AplicacaoFinanceira, OperacaoFinanceira e DiaSemana.</li>
 * </ol>
 */
public final class TesteAdversarialM2_2 {

    public static void main(String[] args) {
        System.out.println("================================================================================");
        System.out.println(">>> INICIANDO DESAFIO ADVERSARIAL EMPÍRICO M2 (Item 6 - Cliente & Integrações)");
        System.out.println("================================================================================");
        long inicio = System.currentTimeMillis();
        int totalTestes = 0;

        // 1. Limite estrito de 50 aplicações
        testarLimiteEstrito50Aplicacoes(); totalTestes++;
        testarRejeicao51AplicacaoPorAmbasSobrecargas(); totalTestes++;
        testarIntegridadeAposRejeicao51(); totalTestes++;

        // 2. Unicidade de número de aplicação
        testarUnicidadeNumeroAplicacaoSimples(); totalTestes++;
        testarUnicidadeNumeroSobrecargaAplicacaoBase(); totalTestes++;
        testarUnicidadeNumeroComEspacosTratados(); totalTestes++;
        testarUnicidadePrecedenciaSobreLimite50(); totalTestes++;

        // 3. Ordenação cronológica estrita de extratos consolidados
        testarExtratoConsolidadoOrdenacaoAleatoriaMassiva(); totalTestes++;
        testarExtratoConsolidadoPorIntervaloArbitrario(); totalTestes++;
        testarExtratoConsolidadoDatasSobrepostas(); totalTestes++;
        testarExtratoConsolidadoColecaoImutavel(); totalTestes++;

        // 4. Persistência TSV v3: Paridade bit a bit e corrupção
        testarPersistenciaParidadeCadastraisESaldosLiquidos(); totalTestes++;
        testarPersistenciaDivergenciaTributosEValorBruto(); totalTestes++;
        testarPersistenciaResilienciaArquivoTruncado(); totalTestes++;
        testarPersistenciaResilienciaCabecalhoCorrompido(); totalTestes++;
        testarPersistenciaResilienciaCamposFaltantes(); totalTestes++;
        testarPersistenciaResilienciaDadosNumericosInvalidos(); totalTestes++;
        testarPersistenciaResilienciaConteudoBinarioAleatorio(); totalTestes++;

        // 5. Integração sistêmica
        testarIntegracaoAtualizacaoFinsDeSemanaVsUteis(); totalTestes++;
        testarIntegracaoOperacoesMultiplasImpactoSaldos(); totalTestes++;
        testarIntegracaoLimite100OperacoesPorAplicacao(); totalTestes++;
        testarIntegracaoResgateBloqueadoCarenciaPermanencia(); totalTestes++;

        long duracao = System.currentTimeMillis() - inicio;
        System.out.println("================================================================================");
        System.out.printf(">>> DESAFIO ADVERSARIAL M2_2 CONCLUÍDO: %d métodos executados (%d ms)%n",
                totalTestes, duracao);
        System.out.println("================================================================================");
    }

    // =========================================================================
    // Eixo 1: Limite Estrito de 50 Aplicações
    // =========================================================================

    public static void testarLimiteEstrito50Aplicacoes() {
        Cliente cliente = criarClientePadrao();
        for (int i = 1; i <= 50; i++) {
            AplicacaoFinanceira af = new AplicacaoFinanceira("App " + i, "APP-" + i,
                    new BigDecimal("1000.00"), new BigDecimal("0.01"));
            cliente.adicionarAplicacao(new MultiplasOperacoesFinanceiras(af));
        }

        afirmarIgual(50, cliente.getQuantidadeAplicacoes(), "deve conter exatamente 50 aplicações");
        for (int i = 1; i <= 50; i++) {
            afirmarVerdadeiro(cliente.buscarAplicacao("APP-" + i).isPresent(),
                    "aplicação APP-" + i + " deve estar presente");
        }
    }

    public static void testarRejeicao51AplicacaoPorAmbasSobrecargas() {
        Cliente cliente = criarClientePadrao();
        for (int i = 1; i <= 50; i++) {
            AplicacaoFinanceira af = new AplicacaoFinanceira("App " + i, "APP-" + i,
                    new BigDecimal("1000.00"), new BigDecimal("0.01"));
            cliente.adicionarAplicacao(af);
        }

        afirmarIgual(50, cliente.getQuantidadeAplicacoes(), "50 aplicações cadastradas");

        // Tentativa via adicionarAplicacao(MultiplasOperacoesFinanceiras)
        AplicacaoFinanceira app51a = new AplicacaoFinanceira("App 51", "APP-51",
                new BigDecimal("500.00"), new BigDecimal("0.01"));
        afirmarRejeicao(() -> cliente.adicionarAplicacao(new MultiplasOperacoesFinanceiras(app51a)),
                IllegalStateException.class,
                "adicionar 51ª aplicação via MultiplasOperacoes deve lançar IllegalStateException");

        // Tentativa via adicionarAplicacao(AplicacaoFinanceira)
        AplicacaoFinanceira app51b = new AplicacaoFinanceira("App 51B", "APP-51B",
                new BigDecimal("500.00"), new BigDecimal("0.01"));
        afirmarRejeicao(() -> cliente.adicionarAplicacao(app51b),
                IllegalStateException.class,
                "adicionar 51ª aplicação via AplicacaoFinanceira deve lançar IllegalStateException");
    }

    public static void testarIntegridadeAposRejeicao51() {
        Cliente cliente = criarClientePadrao();
        for (int i = 1; i <= 50; i++) {
            cliente.adicionarAplicacao(new AplicacaoFinanceira("App " + i, "APP-" + i,
                    new BigDecimal("100.00"), new BigDecimal("0.01")));
        }

        // Tenta adicionar 51ª
        try {
            cliente.adicionarAplicacao(new AplicacaoFinanceira("App 51", "APP-51",
                    new BigDecimal("100.00"), new BigDecimal("0.01")));
        } catch (IllegalStateException expected) {
            // Esperado
        }

        afirmarIgual(50, cliente.getQuantidadeAplicacoes(), "carteira deve permanecer com 50");
        afirmarFalso(cliente.buscarAplicacao("APP-51").isPresent(), "51ª não deve ter entrado");
    }

    // =========================================================================
    // Eixo 2: Unicidade de Número de Aplicação
    // =========================================================================

    public static void testarUnicidadeNumeroAplicacaoSimples() {
        Cliente cliente = criarClientePadrao();
        AplicacaoFinanceira af1 = new AplicacaoFinanceira("CDB", "NUM-001",
                new BigDecimal("1000.00"), new BigDecimal("0.01"));
        cliente.adicionarAplicacao(new MultiplasOperacoesFinanceiras(af1));

        AplicacaoFinanceira af2 = new AplicacaoFinanceira("LCI", "NUM-001",
                new BigDecimal("2000.00"), new BigDecimal("0.01"));
        afirmarRejeicao(() -> cliente.adicionarAplicacao(new MultiplasOperacoesFinanceiras(af2)),
                IllegalArgumentException.class,
                "aplicação com número idêntico deve ser rejeitada com IllegalArgumentException");
    }

    public static void testarUnicidadeNumeroSobrecargaAplicacaoBase() {
        Cliente cliente = criarClientePadrao();
        cliente.adicionarAplicacao(new AplicacaoFinanceira("Fundo A", "FND-10",
                new BigDecimal("5000.00"), new BigDecimal("0.01")));

        afirmarRejeicao(() -> cliente.adicionarAplicacao(new AplicacaoFinanceira("Fundo B", "FND-10",
                new BigDecimal("3000.00"), new BigDecimal("0.02"))),
                IllegalArgumentException.class,
                "sobrecarga direta de AplicacaoFinanceira deve validar unicidade de número");
    }

    public static void testarUnicidadeNumeroComEspacosTratados() {
        Cliente cliente = criarClientePadrao();
        cliente.adicionarAplicacao(new AplicacaoFinanceira("CDB", "100",
                new BigDecimal("1000.00"), new BigDecimal("0.01")));

        // Buscar com espaços deve localizar
        afirmarVerdadeiro(cliente.buscarAplicacao(" 100 ").isPresent(),
                "buscarAplicacao deve normalizar espaços");
    }

    public static void testarUnicidadePrecedenciaSobreLimite50() {
        Cliente cliente = criarClientePadrao();
        for (int i = 1; i <= 50; i++) {
            cliente.adicionarAplicacao(new AplicacaoFinanceira("App " + i, "APP-" + i,
                    new BigDecimal("100.00"), new BigDecimal("0.01")));
        }

        // Tentar adicionar aplicação duplicada quando carteira já está cheia (50)
        // O limite de capacidade (50) deve barrar com IllegalStateException
        afirmarRejeicao(() -> cliente.adicionarAplicacao(new AplicacaoFinanceira("App 1", "APP-1",
                new BigDecimal("100.00"), new BigDecimal("0.01"))),
                IllegalStateException.class,
                "ao atingir 50, tentativa de inserção lança IllegalStateException antes da duplicidade");
    }

    // =========================================================================
    // Eixo 3: Ordenação Cronológica Estrita de Extratos Consolidados
    // =========================================================================

    public static void testarExtratoConsolidadoOrdenacaoAleatoriaMassiva() {
        Cliente cliente = criarClientePadrao();
        LocalDate base = LocalDate.of(2026, 1, 1);

        // Criar 5 aplicações
        List<MultiplasOperacoesFinanceiras> apps = new ArrayList<>();
        for (int a = 1; a <= 5; a++) {
            AplicacaoFinanceira af = new AplicacaoFinanceira("App " + a, "APP-" + a,
                    new BigDecimal("100000.00"), new BigDecimal("0.01"), base);
            MultiplasOperacoesFinanceiras mof = new MultiplasOperacoesFinanceiras(af);
            cliente.adicionarAplicacao(mof);
            apps.add(mof);
        }

        // Gerar 60 operações com datas aleatórias espalhadas entre 2026-01-01 e 2026-06-30
        Random rng = new Random(42);
        int totalOperacoesInseridas = 60;
        for (int i = 0; i < totalOperacoesInseridas; i++) {
            int diasOffset = rng.nextInt(180);
            LocalDate dataOp = base.plusDays(diasOffset);
            int appIndex = rng.nextInt(5);
            apps.get(appIndex).adicionarOperacao(new OperacaoFinanceira(
                    dataOp,
                    new BigDecimal("10.00"),
                    "Op-" + i + "-dia-" + diasOffset,
                    TipoOperacao.DEPOSITO
            ));
        }

        // Consultar extrato consolidado para todo o período
        LocalDate inicioIntervalo = base;
        LocalDate fimIntervalo = base.plusDays(185);
        List<OperacaoFinanceira> consolidado = cliente.getExtratoConsolidado(inicioIntervalo, fimIntervalo);

        afirmarIgual(totalOperacoesInseridas, consolidado.size(),
                "extrato consolidado deve conter todas as 60 operações inseridas");

        // Validar ordenação cronológica estrita não-decrescente
        for (int i = 1; i < consolidado.size(); i++) {
            LocalDate anterior = consolidado.get(i - 1).getData();
            LocalDate atual = consolidado.get(i).getData();
            afirmarFalso(atual.isBefore(anterior),
                    String.format("Violação de ordenação no índice %d: %s veio depois de %s",
                            i, atual, anterior));
        }
    }

    public static void testarExtratoConsolidadoPorIntervaloArbitrario() {
        Cliente cliente = criarClientePadrao();
        LocalDate base = LocalDate.of(2026, 5, 1);

        AplicacaoFinanceira af1 = new AplicacaoFinanceira("App 1", "1", new BigDecimal("1000.00"), new BigDecimal("0.01"), base);
        MultiplasOperacoesFinanceiras mof1 = new MultiplasOperacoesFinanceiras(af1);
        mof1.adicionarOperacao(new OperacaoFinanceira(LocalDate.of(2026, 5, 5), new BigDecimal("50.00"), "App1-05Mai", TipoOperacao.DEPOSITO));
        mof1.adicionarOperacao(new OperacaoFinanceira(LocalDate.of(2026, 5, 20), new BigDecimal("50.00"), "App1-20Mai", TipoOperacao.DEPOSITO));

        AplicacaoFinanceira af2 = new AplicacaoFinanceira("App 2", "2", new BigDecimal("1000.00"), new BigDecimal("0.01"), base);
        MultiplasOperacoesFinanceiras mof2 = new MultiplasOperacoesFinanceiras(af2);
        mof2.adicionarOperacao(new OperacaoFinanceira(LocalDate.of(2026, 5, 10), new BigDecimal("30.00"), "App2-10Mai", TipoOperacao.DEPOSITO));
        mof2.adicionarOperacao(new OperacaoFinanceira(LocalDate.of(2026, 5, 25), new BigDecimal("30.00"), "App2-25Mai", TipoOperacao.DEPOSITO));

        cliente.adicionarAplicacao(mof1);
        cliente.adicionarAplicacao(mof2);

        // Intervalo restrito: 08/05/2026 a 22/05/2026 (deve capturar apenas 10Mai e 20Mai)
        List<OperacaoFinanceira> janela = cliente.getExtratoConsolidado(
                LocalDate.of(2026, 5, 8), LocalDate.of(2026, 5, 22));

        afirmarIgual(2, janela.size(), "deve conter exatamente 2 operações na janela");
        afirmarIgual("App2-10Mai", janela.get(0).getDescricao(), "10Mai deve vir primeiro");
        afirmarIgual("App1-20Mai", janela.get(1).getDescricao(), "20Mai deve vir em segundo");
    }

    public static void testarExtratoConsolidadoDatasSobrepostas() {
        Cliente cliente = criarClientePadrao();
        LocalDate dataComum = LocalDate.of(2026, 6, 15);

        AplicacaoFinanceira af1 = new AplicacaoFinanceira("App 1", "1", new BigDecimal("1000.00"), new BigDecimal("0.01"), dataComum);
        MultiplasOperacoesFinanceiras mof1 = new MultiplasOperacoesFinanceiras(af1);
        mof1.adicionarOperacao(new OperacaoFinanceira(dataComum, new BigDecimal("100.00"), "Op-A", TipoOperacao.DEPOSITO));

        AplicacaoFinanceira af2 = new AplicacaoFinanceira("App 2", "2", new BigDecimal("1000.00"), new BigDecimal("0.01"), dataComum);
        MultiplasOperacoesFinanceiras mof2 = new MultiplasOperacoesFinanceiras(af2);
        mof2.adicionarOperacao(new OperacaoFinanceira(dataComum, new BigDecimal("200.00"), "Op-B", TipoOperacao.DEPOSITO));

        cliente.adicionarAplicacao(mof1);
        cliente.adicionarAplicacao(mof2);

        List<OperacaoFinanceira> consolidado = cliente.getExtratoConsolidado(dataComum, dataComum);
        afirmarIgual(2, consolidado.size(), "ambas as operações da mesma data devem ser retornadas");
        afirmarIgual(dataComum, consolidado.get(0).getData(), "data da primeira deve ser dataComum");
        afirmarIgual(dataComum, consolidado.get(1).getData(), "data da segunda deve ser dataComum");
    }

    public static void testarExtratoConsolidadoColecaoImutavel() {
        Cliente cliente = criarClientePadrao();
        cliente.adicionarAplicacao(new AplicacaoFinanceira("App", "1", new BigDecimal("1000.00"), new BigDecimal("0.01")));
        List<OperacaoFinanceira> extrato = cliente.getExtratoConsolidado();

        afirmarRejeicao(() -> extrato.add(new OperacaoFinanceira(LocalDate.now(), BigDecimal.TEN, "Hack", TipoOperacao.DEPOSITO)),
                UnsupportedOperationException.class,
                "extrato consolidado deve retornar coleção imutável");
    }

    // =========================================================================
    // Eixo 4: Persistência TSV v3: Paridade bit a bit e corrupção
    // =========================================================================

    public static void testarPersistenciaParidadeCadastraisESaldosLiquidos() {
        try {
            Cliente original = new Cliente(
                    new DADOSPESSOAIS("Roberto Carlos Braga", "52998224725", "11987654321"),
                    4567, 891234L
            );

            LocalDate dataBase = LocalDate.of(2026, 9, 1);

            // App 1: CDB tradicional
            AplicacaoFinanceira af1 = new AplicacaoFinanceira(
                    "CDB Supremo", "APP-CDB-01", new BigDecimal("50000.00"), new BigDecimal("0.02"),
                    0, 360, 30, 0, 1, 1, dataBase
            );
            MultiplasOperacoesFinanceiras mof1 = new MultiplasOperacoesFinanceiras(af1);
            mof1.adicionarOperacao(new OperacaoFinanceira(dataBase, new BigDecimal("50000.00"), "Aporte Inicial", TipoOperacao.DEPOSITO));

            // App 2: LCI
            AplicacaoFinanceira af2 = new AplicacaoFinanceira(
                    "LCI Agrobusiness", "APP-LCI-02", new BigDecimal("25000.00"), new BigDecimal("0.015"),
                    0, 180, 0, 0, 0, 0, dataBase
            );
            MultiplasOperacoesFinanceiras mof2 = new MultiplasOperacoesFinanceiras(af2);
            mof2.adicionarOperacao(new OperacaoFinanceira(dataBase, new BigDecimal("25000.00"), "Aporte LCI", TipoOperacao.DEPOSITO));

            original.adicionarAplicacao(mof1);
            original.adicionarAplicacao(mof2);

            Path tempTsv = Files.createTempFile("teste_paridade_cadastral_", ".tsv");
            try {
                original.arquivoSalvarDados(tempTsv);
                Cliente restaurado = Cliente.arquivoLerDados(tempTsv);

                afirmarIgual(original.getNumeroAgencia(), restaurado.getNumeroAgencia(), "agência bancária");
                afirmarIgual(original.getNumeroConta(), restaurado.getNumeroConta(), "número da conta");
                afirmarIgual(original.getDadosPessoais().getNome(), restaurado.getDadosPessoais().getNome(), "nome do titular");
                afirmarIgual(original.getDadosPessoais().getCpf(), restaurado.getDadosPessoais().getCpf(), "CPF do titular");
                afirmarIgual(original.getDadosPessoais().getCelular(), restaurado.getDadosPessoais().getCelular(), "celular do titular");
                afirmarIgual(2, restaurado.getQuantidadeAplicacoes(), "quantidade de aplicações restauradas");

                afirmarIgual(original.getSaldoConsolidado(), restaurado.getSaldoConsolidado(), "saldo consolidado");
                afirmarIgual(original.getSaldo("APP-CDB-01"), restaurado.getSaldo("APP-CDB-01"), "saldo individual App 1");
                afirmarIgual(original.getSaldo("APP-LCI-02"), restaurado.getSaldo("APP-LCI-02"), "saldo individual App 2");

                MultiplasOperacoesFinanceiras mof1Rest = restaurado.requererAplicacao("APP-CDB-01");
                afirmarIgual(1, mof1Rest.getOperacoes().size(), "operações da App 1");
                afirmarIgual("Aporte Inicial", mof1Rest.getOperacoes().get(0).getDescricao(), "descrição op 1");

                MultiplasOperacoesFinanceiras mof2Rest = restaurado.requererAplicacao("APP-LCI-02");
                afirmarIgual(1, mof2Rest.getOperacoes().size(), "operações da App 2");
                afirmarIgual("Aporte LCI", mof2Rest.getOperacoes().get(0).getDescricao(), "descrição op LCI");

            } finally {
                Files.deleteIfExists(tempTsv);
            }
        } catch (IOException e) {
            throw new AssertionError("Falha de E/S inesperada: " + e.getMessage(), e);
        }
    }

    public static void testarPersistenciaDivergenciaTributosEValorBruto() {
        try {
            Cliente original = new Cliente(
                    new DADOSPESSOAIS("Roberto Carlos Braga", "52998224725", "11987654321"),
                    4567, 891234L
            );

            LocalDate dataBase = LocalDate.of(2026, 9, 1);
            // Tipo de rendimento mensal (1) para gerar rendimentos pendentes
            AplicacaoFinanceira af1 = new AplicacaoFinanceira(
                    "CDB Mensal", "APP-CDB-01", new BigDecimal("50000.00"), new BigDecimal("0.02"),
                    1, 360, 30, 1, 1, 1, dataBase
            );
            MultiplasOperacoesFinanceiras mof1 = new MultiplasOperacoesFinanceiras(af1);
            original.adicionarAplicacao(mof1);

            // Simular 45 dias
            LocalDate cursor = dataBase;
            for (int d = 0; d < 45; d++) {
                cursor = cursor.plusDays(1);
                original.atualizarAplicacoes(cursor);
            }

            BigDecimal vBrutoOriginal = af1.getValorAtualBruto();
            BigDecimal impostoOriginal = af1.getImpostoAtualDevido();
            BigDecimal rendimentoBrutoOriginal = af1.getRendimentoAtualBruto();

            // Pré-condição: a aplicação acumulou rendimentos pendentes e tributos
            afirmarVerdadeiro(vBrutoOriginal.compareTo(af1.getSaldo()) > 0, "deve possuir rendimentos pendentes no bruto");
            afirmarVerdadeiro(impostoOriginal.compareTo(BigDecimal.ZERO) > 0, "deve possuir tributos apurados");
            afirmarVerdadeiro(rendimentoBrutoOriginal.compareTo(BigDecimal.ZERO) > 0, "deve possuir rendimento bruto apurado");

            Path tempTsv = Files.createTempFile("teste_divergencia_tributos_", ".tsv");
            try {
                original.arquivoSalvarDados(tempTsv);
                Cliente restaurado = Cliente.arquivoLerDados(tempTsv);
                AplicacaoFinanceira afRest = restaurado.requererAplicacao("APP-CDB-01").getAplicacao();

                System.out.printf("  [PARIDADE TSV v3 VALIDADA COM SUCESSO]%n");
                System.out.printf("  Original ImpostoDevido=%s | Restaurado ImpostoDevido=%s%n",
                        impostoOriginal, afRest.getImpostoAtualDevido());
                System.out.printf("  Original RendimentoBruto=%s | Restaurado RendimentoBruto=%s%n",
                        rendimentoBrutoOriginal, afRest.getRendimentoAtualBruto());
                System.out.printf("  Original ValorBruto=%s | Restaurado ValorBruto=%s%n",
                        vBrutoOriginal, afRest.getValorAtualBruto());

                // Validação afirmativa de paridade bit a bit de todos os atributos
                afirmarIgual(impostoOriginal, afRest.getImpostoAtualDevido(), "impostoAtualDevido preservado com paridade exata");
                afirmarIgual(rendimentoBrutoOriginal, afRest.getRendimentoAtualBruto(), "rendimentoAtualBruto preservado com paridade exata");
                afirmarIgual(af1.getRendimentoAtualLiquido(), afRest.getRendimentoAtualLiquido(), "rendimentoAtualLiquido preservado com paridade exata");
                afirmarIgual(vBrutoOriginal, afRest.getValorAtualBruto(), "valorAtualBruto preservado com paridade exata (sem inversão ou reset)");
                afirmarIgual(af1.getValorAtualLiquido(), afRest.getValorAtualLiquido(), "valorAtualLiquido preservado com paridade exata");
                afirmarIgual(af1.getSaldo(), afRest.getSaldo(), "saldo preservado com paridade exata");
                afirmarIgual(af1.getNumeroAplicacao(), afRest.getNumeroAplicacao(), "número da aplicação");
                afirmarIgual(af1.getNomeAplicacao(), afRest.getNomeAplicacao(), "nome da aplicação");
                afirmarIgual(af1.getValorAplicado(), afRest.getValorAplicado(), "valor aplicado");
                afirmarIgual(af1.getTaxaMensal(), afRest.getTaxaMensal(), "taxa mensal");
                afirmarIgual(af1.getDataAplicacao(), afRest.getDataAplicacao(), "data de aplicação");
                afirmarIgual(af1.getDiasCorridos(), afRest.getDiasCorridos(), "dias corridos");
                afirmarIgual(af1.getDiasEfetivosCalculo(), afRest.getDiasEfetivosCalculo(), "dias efetivos de cálculo");
                afirmarIgual(af1.getConvencaoContagemDias(), afRest.getConvencaoContagemDias(), "convenção de contagem de dias");
                afirmarIgual(af1.getRegimeTributario(), afRest.getRegimeTributario(), "regime tributário");
                afirmarIgual(af1.getTipoRendimento(), afRest.getTipoRendimento(), "tipo de rendimento");
                afirmarIgual(af1.getTempoFinalVencimento(), afRest.getTempoFinalVencimento(), "tempo final de vencimento");
                afirmarIgual(af1.getTempoMinimoPermanencia(), afRest.getTempoMinimoPermanencia(), "tempo mínimo de permanência");
                afirmarIgual(af1.getTipoResgate(), afRest.getTipoResgate(), "tipo de resgate");
                afirmarIgual(af1.getIof(), afRest.getIof(), "IOF");
                afirmarIgual(af1.getTipoImpostoRenda(), afRest.getTipoImpostoRenda(), "tipo de IR");
                afirmarIgual(af1.getValorResgate(), afRest.getValorResgate(), "valor de resgate");
                afirmarVerdadeiro(afRest.getValorAtualBruto().compareTo(afRest.getValorAtualLiquido()) >= 0,
                        "invariante contábil preservada: ValorAtualBruto >= ValorAtualLiquido");

            } finally {
                Files.deleteIfExists(tempTsv);
            }
        } catch (IOException e) {
            throw new AssertionError("Falha inesperada no teste de divergência: " + e.getMessage(), e);
        }
    }

    public static void testarPersistenciaResilienciaArquivoTruncado() {
        try {
            Path temp = Files.createTempFile("teste_truncado_", ".tsv");
            try {
                // Arquivo vazio (0 bytes)
                Files.write(temp, new byte[0]);
                afirmarRejeicao(() -> {
                    try { Cliente.arquivoLerDados(temp); }
                    catch (IOException e) { throw new RuntimeException(e); }
                }, IllegalArgumentException.class, "arquivo vazio deve ser rejeitado");

                // Arquivo contendo apenas 3 bytes do cabeçalho
                Files.writeString(temp, "NAT");
                afirmarRejeicao(() -> {
                    try { Cliente.arquivoLerDados(temp); }
                    catch (IOException e) { throw new RuntimeException(e); }
                }, IllegalArgumentException.class, "arquivo truncado no cabeçalho deve ser rejeitado");

                // Apenas o cabeçalho, sem registros
                Files.writeString(temp, "NATIONAL-ECONOMIC\t3\n");
                afirmarRejeicao(() -> {
                    try { Cliente.arquivoLerDados(temp); }
                    catch (IOException e) { throw new RuntimeException(e); }
                }, IllegalArgumentException.class, "arquivo sem registros deve lançar exceção");

            } finally {
                Files.deleteIfExists(temp);
            }
        } catch (IOException e) {
            throw new AssertionError("Falha no teste de arquivo truncado: " + e.getMessage(), e);
        }
    }

    public static void testarPersistenciaResilienciaCabecalhoCorrompido() {
        try {
            Path temp = Files.createTempFile("teste_cabecalho_", ".tsv");
            try {
                // Cabeçalho incompatível
                Files.writeString(temp, "FORMATO-INVALIDO\t1\nC\t52998224725\tNome\t11999998888\t1001\t20002\n");
                afirmarRejeicao(() -> {
                    try { Cliente.arquivoLerDados(temp); }
                    catch (IOException e) { throw new RuntimeException(e); }
                }, IllegalArgumentException.class, "cabeçalho incompatível deve ser rejeitado");

            } finally {
                Files.deleteIfExists(temp);
            }
        } catch (IOException e) {
            throw new AssertionError(e.getMessage(), e);
        }
    }

    public static void testarPersistenciaResilienciaCamposFaltantes() {
        try {
            Path temp = Files.createTempFile("teste_campos_faltantes_", ".tsv");
            try {
                // Registro C incompleto (faltando conta)
                Files.writeString(temp, "NATIONAL-ECONOMIC\t3\nC\t52998224725\tNome\t11999998888\t1001\n");
                afirmarRejeicao(() -> {
                    try { Cliente.arquivoLerDados(temp); }
                    catch (IOException e) { throw new RuntimeException(e); }
                }, IllegalArgumentException.class, "registro C com campos faltantes deve ser rejeitado");

                // Registro I antes do C
                Files.writeString(temp, "NATIONAL-ECONOMIC\t3\nI\t52998224725\t1\tApp\t100.00\t0.01\t2026-01-01\n");
                afirmarRejeicao(() -> {
                    try { Cliente.arquivoLerDados(temp); }
                    catch (IOException e) { throw new RuntimeException(e); }
                }, IllegalArgumentException.class, "registro I antes de C deve ser rejeitado");

                // Registro O antes de I
                Files.writeString(temp, "NATIONAL-ECONOMIC\t3\nC\t52998224725\tNome\t11999998888\t1001\t20002\nO\t52998224725\t1\t2026-01-01\t10.00\tDesc\tDEPOSITO\n");
                afirmarRejeicao(() -> {
                    try { Cliente.arquivoLerDados(temp); }
                    catch (IOException e) { throw new RuntimeException(e); }
                }, IllegalArgumentException.class, "registro O antes de I deve ser rejeitado");

                // Registro I com campos faltantes (< 23 campos)
                Files.writeString(temp, "NATIONAL-ECONOMIC\t3\nC\t52998224725\tNome\t11999998888\t1001\t20002\nI\t52998224725\t1\tApp\t100.00\n");
                afirmarRejeicao(() -> {
                    try { Cliente.arquivoLerDados(temp); }
                    catch (IOException e) { throw new RuntimeException(e); }
                }, IllegalArgumentException.class, "registro I com campos faltantes (< 23) deve lançar IllegalArgumentException");

                // Registro O com campos faltantes (< 7 campos)
                Files.writeString(temp, "NATIONAL-ECONOMIC\t3\n"
                        + "C\t52998224725\tNome\t11999998888\t1001\t20002\n"
                        + "I\t52998224725\t1\tApp\t100.00\t0.01\t2026-01-01\t100.00\t100.00\t2026-01-01\t0\t0\t0\tCOMERCIAL_30_360\tPADRAO\t0\t0\t0\t0\t0\t0\t0.00\t100.00\n"
                        + "O\t52998224725\t1\t2026-01-01\t10.00\n");
                afirmarRejeicao(() -> {
                    try { Cliente.arquivoLerDados(temp); }
                    catch (IOException e) { throw new RuntimeException(e); }
                }, IllegalArgumentException.class, "registro O com campos faltantes (< 7) deve lançar IllegalArgumentException");

            } finally {
                Files.deleteIfExists(temp);
            }
        } catch (IOException e) {
            throw new AssertionError(e.getMessage(), e);
        }
    }

    public static void testarPersistenciaResilienciaDadosNumericosInvalidos() {
        try {
            Path temp = Files.createTempFile("teste_dados_invalidos_", ".tsv");
            try {
                // Agência não numérica em C
                Files.writeString(temp, "NATIONAL-ECONOMIC\t3\nC\t52998224725\tNome\t11999998888\tAGENCIA_LETRA\t20002\n");
                afirmarRejeicao(() -> {
                    try { Cliente.arquivoLerDados(temp); }
                    catch (IOException e) { throw new RuntimeException(e); }
                }, NumberFormatException.class, "agência alfabética deve lançar NumberFormatException");

                // CPF inválido (todos dígitos iguais)
                Files.writeString(temp, "NATIONAL-ECONOMIC\t3\nC\t11111111111\tNome\t11999998888\t1001\t20002\n");
                afirmarRejeicao(() -> {
                    try { Cliente.arquivoLerDados(temp); }
                    catch (IOException e) { throw new RuntimeException(e); }
                }, IllegalArgumentException.class, "CPF corrompido deve lançar IllegalArgumentException");

            } finally {
                Files.deleteIfExists(temp);
            }
        } catch (IOException e) {
            throw new AssertionError(e.getMessage(), e);
        }
    }

    public static void testarPersistenciaResilienciaConteudoBinarioAleatorio() {
        try {
            Path temp = Files.createTempFile("teste_lixo_binario_", ".tsv");
            try {
                byte[] lixo = new byte[512];
                new Random(12345).nextBytes(lixo);
                Files.write(temp, lixo);

                boolean rejeitou = false;
                try {
                    Cliente.arquivoLerDados(temp);
                } catch (IllegalArgumentException | IOException expected) {
                    rejeitou = true;
                }
                afirmarVerdadeiro(rejeitou, "lixo binário arbitrário deve ser rejeitado com IllegalArgumentException ou IOException");

            } finally {
                Files.deleteIfExists(temp);
            }
        } catch (IOException e) {
            throw new AssertionError(e.getMessage(), e);
        }
    }

    // =========================================================================
    // Eixo 5: Integração Sistêmica
    // =========================================================================

    public static void testarIntegracaoAtualizacaoFinsDeSemanaVsUteis() {
        Cliente cliente = criarClientePadrao();
        LocalDate quinta = LocalDate.of(2026, 11, 5);
        AplicacaoFinanceira af = new AplicacaoFinanceira("CDB", "1", new BigDecimal("10000.00"), new BigDecimal("0.03"), quinta);
        cliente.adicionarAplicacao(af);

        afirmarIgual(0, af.getDiasEfetivosCalculo(), "inicia com 0 dias efetivos de cálculo");

        // Atualização na Sexta-feira (06/11/2026) -> Processa Quinta e Sexta (2 dias úteis)
        LocalDate sexta = LocalDate.of(2026, 11, 6);
        cliente.atualizarAplicacoes(sexta);
        afirmarIgual(2, af.getDiasEfetivosCalculo(), "quinta e sexta são úteis: computa 2 dias úteis");
        afirmarIgual(2, af.getDiasCorridos(), "computa 2 dias corridos");

        // Atualização no Sábado (07/11/2026) -> Fim de semana
        LocalDate sabado = LocalDate.of(2026, 11, 7);
        cliente.atualizarAplicacoes(sabado);
        afirmarIgual(2, af.getDiasEfetivosCalculo(), "sábado NÃO deve incrementar dias de cálculo");

        // Atualização no Domingo (08/11/2026) -> Fim de semana
        LocalDate domingo = LocalDate.of(2026, 11, 8);
        cliente.atualizarAplicacoes(domingo);
        afirmarIgual(2, af.getDiasEfetivosCalculo(), "domingo NÃO deve incrementar dias de cálculo");

        // Atualização na Segunda-feira (09/11/2026) -> Dia útil
        LocalDate segunda = LocalDate.of(2026, 11, 9);
        cliente.atualizarAplicacoes(segunda);
        afirmarIgual(3, af.getDiasEfetivosCalculo(), "segunda-feira deve incrementar para 3 dias úteis");
    }

    public static void testarIntegracaoOperacoesMultiplasImpactoSaldos() {
        Cliente cliente = criarClientePadrao();
        LocalDate hoje = LocalDate.of(2026, 10, 1);
        AplicacaoFinanceira af = new AplicacaoFinanceira("CDB", "1", new BigDecimal("1000.00"), new BigDecimal("0.01"), hoje);
        MultiplasOperacoesFinanceiras mof = new MultiplasOperacoesFinanceiras(af);

        mof.adicionarOperacao(new OperacaoFinanceira(hoje.plusDays(2), new BigDecimal("500.00"), "Depósito 1", TipoOperacao.DEPOSITO));
        mof.adicionarOperacao(new OperacaoFinanceira(hoje.plusDays(4), new BigDecimal("250.00"), "Depósito 2", TipoOperacao.DEPOSITO));

        cliente.adicionarAplicacao(mof);

        // Saldo da aplicação deve refletir os depósitos adicionais: 1000 + 500 + 250 = 1750
        BigDecimal saldoEsperado = new BigDecimal("1750.00");
        afirmarIgual(saldoEsperado, cliente.getSaldo("1"), "saldo individual com depósitos");
        afirmarIgual(saldoEsperado, cliente.getSaldoConsolidado(), "saldo consolidado com depósitos");
    }

    public static void testarIntegracaoLimite100OperacoesPorAplicacao() {
        Cliente cliente = criarClientePadrao();
        LocalDate base = LocalDate.of(2026, 1, 1);
        AplicacaoFinanceira af = new AplicacaoFinanceira("CDB", "1", new BigDecimal("10000.00"), new BigDecimal("0.01"), base);
        MultiplasOperacoesFinanceiras mof = new MultiplasOperacoesFinanceiras(af);

        for (int i = 1; i <= 100; i++) {
            mof.adicionarOperacao(new OperacaoFinanceira(base.plusDays(i), new BigDecimal("1.00"), "Op " + i, TipoOperacao.DEPOSITO));
        }

        afirmarIgual(100, mof.getOperacoes().size(), "deve conter 100 operações");

        // 101ª operação deve ser barrada por limite estrito do Item 4
        afirmarRejeicao(() -> mof.adicionarOperacao(new OperacaoFinanceira(base.plusDays(101), new BigDecimal("1.00"), "Op 101", TipoOperacao.DEPOSITO)),
                IllegalStateException.class,
                "101ª operação na aplicação deve lançar IllegalStateException");

        cliente.adicionarAplicacao(mof);
        afirmarIgual(1, cliente.getQuantidadeAplicacoes(), "cliente gerencia aplicação com 100 operações");
    }

    public static void testarIntegracaoResgateBloqueadoCarenciaPermanencia() {
        Cliente cliente = criarClientePadrao();
        LocalDate base = LocalDate.of(2026, 1, 1);
        // Carência de 60 dias (tempoMinimoPermanencia = 60)
        AplicacaoFinanceira af = new AplicacaoFinanceira("LCI Fechada", "1", new BigDecimal("10000.00"), new BigDecimal("0.01"),
                1, 360, 60, 1, 0, 0, base);
        MultiplasOperacoesFinanceiras mof = new MultiplasOperacoesFinanceiras(af);

        // Tentativa de resgate com 30 dias (dentro do prazo de carência de 60)
        afirmarRejeicao(() -> mof.adicionarOperacao(new OperacaoFinanceira(
                base.plusDays(30), new BigDecimal("1000.00"), "Resgate Precoce", TipoOperacao.RETIRADA)),
                IllegalArgumentException.class,
                "resgate antes do término da carência deve ser rejeitado");
    }

    // =========================================================================
    // Métodos Utilitários Internos
    // =========================================================================

    private static Cliente criarClientePadrao() {
        DADOSPESSOAIS dados = new DADOSPESSOAIS("Challenger M2 Tester", "52998224725", "11988887777");
        return new Cliente(dados, 1001, 77777L);
    }
}

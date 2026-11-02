package test.dominio;

import static test.Afirmacao.*;

import dominio.AplicacaoFinanceira;
import dominio.Cliente;
import dominio.ConvencaoContagemDias;
import dominio.DADOSPESSOAIS;
import dominio.MultiplasOperacoesFinanceiras;
import dominio.OperacaoFinanceira;
import dominio.RegimeTributario;
import dominio.TipoOperacao;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Suíte de Testes Adversariais e de Estresse Empírico — Challenger 1 (Gate Marco 2, Iteração 2).
 *
 * <p>Audita de forma hostil e independente a classe {@link Cliente} e suas integrações
 * com {@link MultiplasOperacoesFinanceiras}, {@link DADOSPESSOAIS} e {@link AplicacaoFinanceira}.</p>
 *
 * <p>Eixos de Desafio Hostil:</p>
 * <ol>
 *   <li><b>Eixo 1 (Fronteira 50 Aplicações)</b>: Rejeição estrita da 51ª aplicação em ambas as sobrecargas,
 *       preservação do estado após rejeição, unicidade vs limite de capacidade;</li>
 *   <li><b>Eixo 2 (Fronteira 100 Operações &amp; Saturação 5.000 Operações)</b>: Rejeição da 101ª operação por aplicação,
 *       cliente massivo saturado com 50 aplicações e 100 operações cada (5.000 lançamentos no total),
 *       extrato consolidado ordenado de 5.000 itens e imutabilidade;</li>
 *   <li><b>Eixo 3 (Anos Bissextos &amp; Extratos de 30 Dias)</b>: Extratos através de múltiplos anos bissextos
 *       (2020, 2024, 2028), inclusão precisa do 29 de fevereiro, rejeição de 29/02 em anos comuns (2021, 2022, 2023, 2100),
 *       cálculo diferencial dos últimos 30 dias na virada de março em ano bissexto vs não bissexto;</li>
 *   <li><b>Eixo 4 (Mutações Cadastrais &amp; Atomicidade)</b>: Mutações polimórficas de dados pessoais,
 *       preservação transacional sob dados inválidos (roll-back implícito/sem corrupção de estado), suporte a acentuação;</li>
 *   <li><b>Eixo 5 (Bateria Extrema de CPFs)</b>: CPFs válidos com 8 zeros à esquerda, terminação em "00", sequenciais válidos,
 *       bateria dos 10 repetidos, DVs adulterados (DV1, DV2, ambos), comprimentos anômalos;</li>
 *   <li><b>Eixo 6 (Persistência TSV v3 de Alta Complexidade)</b>: Salvar e restaurar cliente com 50 aplicações heterogêneas,
 *       múltiplas operações, tributos e rendimentos apurados, validando paridade exata bit a bit e integridade sob truncamento.</li>
 * </ol>
 */
public final class TesteAdversarialChallengerM2_Iter2 {

    public static void main(String[] args) {
        System.out.println("================================================================================");
        System.out.println(">>> INICIANDO DESAFIO ADVERSARIAL CHALLENGER 1 (M2 - ITERAÇÃO 2)");
        System.out.println("================================================================================");
        long inicioGeral = System.currentTimeMillis();
        int totalTestes = 0;

        // Eixo 1: Limite Estrito de 50 Aplicações
        testarLimiteExato50AplicacoesAmbasSobrecargas(); totalTestes++;
        testarRejeicao51AplicacaoAmbasSobrecargas(); totalTestes++;
        testarImutabilidadeCarteira50AposRejeicao51(); totalTestes++;
        testarPrecedenciaCapacidade50SobreDuplicidade(); totalTestes++;
        testarImutabilidadeColecaoAplicacoesExterna(); totalTestes++;

        // Eixo 2: Limite de 100 Operações por Aplicação e Saturação Massiva (5.000 Operações)
        testarLimite100OperacoesERejeicao101(); totalTestes++;
        testarSaturacaoMassivaCliente5000Operacoes(); totalTestes++;

        // Eixo 3: Extratos e Anos Bissextos (2020, 2024, 2028)
        testarExtratoCruzandoMultiplosAnosBissextos(); totalTestes++;
        testarExtrato30DiasFronteiraBissextoVsComum(); totalTestes++;
        testarRejeicao29FevereiroEmAnosNaoBissextos(); totalTestes++;
        testarExtratoConsolidadoDatasEmbaralhadasMultiplosAnos(); totalTestes++;
        testarExtratoConsolidadoValidacaoIntervalosInvalidos(); totalTestes++;

        // Eixo 4: Mutações Cadastrais de DADOSPESSOAIS e Atomicidade
        testarMutacoesValidasPolimorficasEDadosPessoais(); totalTestes++;
        testarAtomicidadeTransacionalSobFalhaDeMutacao(); totalTestes++;
        testarMutacaoDadosPessoaisComCaracteresAcentuados(); totalTestes++;
        testarSubstituicaoIntegralDadosPessoaisCliente(); totalTestes++;

        // Eixo 5: Bateria Extrema de CPFs Válidos e Inválidos
        testarCpfsValidosExtremos(); totalTestes++;
        testarCpfsInvalidos10CasosRepetidos(); totalTestes++;
        testarCpfsInvalidosDvsAdulterados(); totalTestes++;
        testarCpfsInvalidosTamanhosEFormatos(); totalTestes++;

        // Eixo 6: Persistência TSV v3 de Alta Complexidade e Preservação Exata
        testarPersistenciaTsvV3CarteiraComplexa50Aplicacoes(); totalTestes++;
        testarPersistenciaTsvV3ResilienciaRegistrosTruncados(); totalTestes++;

        long duracao = System.currentTimeMillis() - inicioGeral;
        System.out.println("================================================================================");
        System.out.printf(">>> DESAFIO ADVERSARIAL CHALLENGER 1 CONCLUÍDO: %d métodos verificados (%d asserções, %d ms)%n",
                totalTestes, getTotalAfirmacoes(), duracao);
        System.out.println("================================================================================");
    }

    // =========================================================================
    // Eixo 1: Limite Estrito de 50 Aplicações
    // =========================================================================

    public static void testarLimiteExato50AplicacoesAmbasSobrecargas() {
        Cliente cliente = criarClientePadrao();
        // 25 aplicações adicionadas via MultiplasOperacoesFinanceiras
        for (int i = 1; i <= 25; i++) {
            AplicacaoFinanceira af = new AplicacaoFinanceira("App MOF " + i, "APP-M-" + i,
                    new BigDecimal("1000.00"), new BigDecimal("0.01"));
            cliente.adicionarAplicacao(new MultiplasOperacoesFinanceiras(af));
        }
        // 25 aplicações adicionadas via sobrecarga direta AplicacaoFinanceira
        for (int i = 26; i <= 50; i++) {
            AplicacaoFinanceira af = new AplicacaoFinanceira("App AF " + i, "APP-A-" + i,
                    new BigDecimal("2000.00"), new BigDecimal("0.02"));
            cliente.adicionarAplicacao(af);
        }

        afirmarIgual(50, cliente.getQuantidadeAplicacoes(), "cliente deve conter exatamente 50 aplicações");
        for (int i = 1; i <= 25; i++) {
            afirmarVerdadeiro(cliente.buscarAplicacao("APP-M-" + i).isPresent(), "APP-M-" + i + " presente");
        }
        for (int i = 26; i <= 50; i++) {
            afirmarVerdadeiro(cliente.buscarAplicacao("APP-A-" + i).isPresent(), "APP-A-" + i + " presente");
        }
    }

    public static void testarRejeicao51AplicacaoAmbasSobrecargas() {
        Cliente cliente = criarClientePadrao();
        for (int i = 1; i <= 50; i++) {
            cliente.adicionarAplicacao(new AplicacaoFinanceira("App " + i, "APP-" + i,
                    new BigDecimal("500.00"), new BigDecimal("0.01")));
        }

        afirmarIgual(50, cliente.getQuantidadeAplicacoes(), "atingiu limite de 50");

        // Tentativa de 51ª via MultiplasOperacoesFinanceiras
        AplicacaoFinanceira af51a = new AplicacaoFinanceira("App 51 A", "APP-51-A",
                new BigDecimal("500.00"), new BigDecimal("0.01"));
        afirmarRejeicao(() -> cliente.adicionarAplicacao(new MultiplasOperacoesFinanceiras(af51a)),
                IllegalStateException.class, "51ª aplicação via MOF deve ser rejeitada com IllegalStateException");

        // Tentativa de 51ª via AplicacaoFinanceira direta
        AplicacaoFinanceira af51b = new AplicacaoFinanceira("App 51 B", "APP-51-B",
                new BigDecimal("500.00"), new BigDecimal("0.01"));
        afirmarRejeicao(() -> cliente.adicionarAplicacao(af51b),
                IllegalStateException.class, "51ª aplicação via AF deve ser rejeitada com IllegalStateException");
    }

    public static void testarImutabilidadeCarteira50AposRejeicao51() {
        Cliente cliente = criarClientePadrao();
        for (int i = 1; i <= 50; i++) {
            cliente.adicionarAplicacao(new AplicacaoFinanceira("App " + i, "APP-" + i,
                    new BigDecimal("1000.00"), new BigDecimal("0.01")));
        }

        // Realiza 5 tentativas frustradas de adicionar aplicações excedentes
        for (int t = 51; t <= 55; t++) {
            AplicacaoFinanceira af = new AplicacaoFinanceira("App " + t, "APP-" + t,
                    new BigDecimal("100.00"), new BigDecimal("0.01"));
            try {
                cliente.adicionarAplicacao(af);
            } catch (IllegalStateException expected) {
                // Comportamento esperado
            }
        }

        afirmarIgual(50, cliente.getQuantidadeAplicacoes(), "quantidade deve permanecer estritamente 50");
        for (int t = 51; t <= 55; t++) {
            afirmarFalso(cliente.buscarAplicacao("APP-" + t).isPresent(), "aplicação excedente APP-" + t + " não existe");
        }
    }

    public static void testarPrecedenciaCapacidade50SobreDuplicidade() {
        Cliente cliente = criarClientePadrao();
        for (int i = 1; i <= 50; i++) {
            cliente.adicionarAplicacao(new AplicacaoFinanceira("App " + i, "APP-" + i,
                    new BigDecimal("100.00"), new BigDecimal("0.01")));
        }

        // Tentar reinserir APP-1 quando a carteira já possui 50 aplicações
        // A guarda de capacidade (50) deve barrar com IllegalStateException antes da verificação de duplicidade
        afirmarRejeicao(() -> cliente.adicionarAplicacao(new AplicacaoFinanceira("App 1 duplicada", "APP-1",
                new BigDecimal("100.00"), new BigDecimal("0.01"))),
                IllegalStateException.class,
                "carteira lotada com 50 deve lançar IllegalStateException mesmo para número duplicado");
    }

    public static void testarImutabilidadeColecaoAplicacoesExterna() {
        Cliente cliente = criarClientePadrao();
        cliente.adicionarAplicacao(new AplicacaoFinanceira("App 1", "APP-1", new BigDecimal("1000.00"), new BigDecimal("0.01")));
        List<MultiplasOperacoesFinanceiras> lista = cliente.getAplicacoes();

        afirmarRejeicao(lista::clear, UnsupportedOperationException.class, "clear() deve ser rejeitado");
        afirmarRejeicao(() -> lista.remove(0), UnsupportedOperationException.class, "remove() deve ser rejeitado");
        afirmarRejeicao(() -> lista.add(new MultiplasOperacoesFinanceiras(
                new AplicacaoFinanceira("App 2", "APP-2", new BigDecimal("100.00"), new BigDecimal("0.01")))),
                UnsupportedOperationException.class, "add() na lista externa deve ser rejeitado");
    }

    // =========================================================================
    // Eixo 2: Limite de 100 Operações por Aplicação e Saturação Massiva
    // =========================================================================

    public static void testarLimite100OperacoesERejeicao101() {
        Cliente cliente = criarClientePadrao();
        LocalDate base = LocalDate.of(2026, 1, 1);
        AplicacaoFinanceira af = new AplicacaoFinanceira("CDB 100 Ops", "APP-100",
                new BigDecimal("50000.00"), new BigDecimal("0.01"), base);
        MultiplasOperacoesFinanceiras mof = new MultiplasOperacoesFinanceiras(af);

        for (int i = 1; i <= 100; i++) {
            mof.adicionarOperacao(new OperacaoFinanceira(base.plusDays(i), new BigDecimal("10.00"), "Aporte #" + i, TipoOperacao.DEPOSITO));
        }

        afirmarIgual(100, mof.getQuantidadeOperacoes(), "exatamente 100 operações");
        afirmarRejeicao(() -> mof.adicionarOperacao(new OperacaoFinanceira(
                base.plusDays(101), new BigDecimal("10.00"), "Aporte 101", TipoOperacao.DEPOSITO)),
                IllegalStateException.class, "101ª operação deve lançar IllegalStateException");

        cliente.adicionarAplicacao(mof);
        afirmarIgual(1, cliente.getQuantidadeAplicacoes(), "cliente vinculado com sucesso");
    }

    public static void testarSaturacaoMassivaCliente5000Operacoes() {
        Cliente cliente = criarClientePadrao();
        LocalDate dataBase = LocalDate.of(2026, 1, 1);
        Random rng = new Random(777);

        // Cria 50 aplicações com 100 operações cada = 5.000 lançamentos no total
        int totalApps = 50;
        int opsPorApp = 100;
        int totalOperacoesEsperado = totalApps * opsPorApp; // 5.000

        BigDecimal saldoConsolidadoEsperado = BigDecimal.ZERO;

        for (int a = 1; a <= totalApps; a++) {
            BigDecimal aporteInicial = new BigDecimal("1000.00");
            AplicacaoFinanceira af = new AplicacaoFinanceira("App Massiva " + a, "APP-MASS-" + a,
                    aporteInicial, new BigDecimal("0.01"), dataBase);
            MultiplasOperacoesFinanceiras mof = new MultiplasOperacoesFinanceiras(af);

            saldoConsolidadoEsperado = saldoConsolidadoEsperado.add(aporteInicial);

            for (int o = 1; o <= opsPorApp; o++) {
                // Gera datas distribuídas ao longo de 500 dias
                int offsetDias = rng.nextInt(500);
                LocalDate dataOp = dataBase.plusDays(offsetDias);
                BigDecimal valorOp = new BigDecimal("5.00");
                mof.adicionarOperacao(new OperacaoFinanceira(dataOp, valorOp, "Op-A" + a + "-O" + o, TipoOperacao.DEPOSITO));
                saldoConsolidadoEsperado = saldoConsolidadoEsperado.add(valorOp);
            }

            cliente.adicionarAplicacao(mof);
        }

        afirmarIgual(50, cliente.getQuantidadeAplicacoes(), "50 aplicações na carteira");
        afirmarIgual(saldoConsolidadoEsperado, cliente.getSaldoConsolidado(), "saldo consolidado de 5.000 operações");

        // Obter extrato consolidado de todo o período
        LocalDate inicioIntervalo = dataBase;
        LocalDate fimIntervalo = dataBase.plusDays(550);
        List<OperacaoFinanceira> extratoGeral = cliente.getExtratoConsolidado(inicioIntervalo, fimIntervalo);

        afirmarIgual(totalOperacoesEsperado, extratoGeral.size(),
                "extrato consolidado deve conter exatamente as 5.000 operações");

        // Validar ordenação cronológica estrita em todas as 5.000 operações
        for (int i = 1; i < extratoGeral.size(); i++) {
            LocalDate ant = extratoGeral.get(i - 1).getData();
            LocalDate at = extratoGeral.get(i).getData();
            afirmarFalso(at.isBefore(ant), String.format("Violação cronológica em %d: %s antes de %s", i, at, ant));
        }

        // Testar imutabilidade da lista retornada
        afirmarRejeicao(() -> extratoGeral.remove(0), UnsupportedOperationException.class,
                "extrato consolidado deve ser imutável");
    }

    // =========================================================================
    // Eixo 3: Anos Bissextos e Extratos de 30 Dias
    // =========================================================================

    public static void testarExtratoCruzandoMultiplosAnosBissextos() {
        Cliente cliente = criarClientePadrao();
        LocalDate base = LocalDate.of(2020, 1, 1);

        AplicacaoFinanceira af1 = new AplicacaoFinanceira("Longo Prazo", "LP-01",
                new BigDecimal("10000.00"), new BigDecimal("0.01"), base);
        MultiplasOperacoesFinanceiras mof = new MultiplasOperacoesFinanceiras(af1);

        // Operações em anos bissextos (29 de fevereiro):
        // 2020-02-29 (bissexto)
        mof.adicionarOperacao(new OperacaoFinanceira(LocalDate.of(2020, 2, 29), new BigDecimal("100.00"), "Op-2020-Biss", TipoOperacao.DEPOSITO));
        // 2021-02-28 (comum)
        mof.adicionarOperacao(new OperacaoFinanceira(LocalDate.of(2021, 2, 28), new BigDecimal("100.00"), "Op-2021-Comum", TipoOperacao.DEPOSITO));
        // 2024-02-29 (bissexto)
        mof.adicionarOperacao(new OperacaoFinanceira(LocalDate.of(2024, 2, 29), new BigDecimal("100.00"), "Op-2024-Biss", TipoOperacao.DEPOSITO));
        // 2028-02-29 (bissexto)
        mof.adicionarOperacao(new OperacaoFinanceira(LocalDate.of(2028, 2, 29), new BigDecimal("100.00"), "Op-2028-Biss", TipoOperacao.DEPOSITO));

        cliente.adicionarAplicacao(mof);

        // Janela que engloba 2020 até 2025 (deve capturar 2020, 2021, 2024, mas excluir 2028)
        List<OperacaoFinanceira> extrato = cliente.getExtrato("LP-01",
                LocalDate.of(2020, 2, 1), LocalDate.of(2025, 1, 1));

        afirmarIgual(3, extrato.size(), "deve conter 3 operações na janela 2020..2025");
        afirmarIgual("Op-2020-Biss", extrato.get(0).getDescricao(), "primeira é 2020-02-29");
        afirmarIgual("Op-2021-Comum", extrato.get(1).getDescricao(), "segunda é 2021-02-28");
        afirmarIgual("Op-2024-Biss", extrato.get(2).getDescricao(), "terceira é 2024-02-29");

        // Janela ampla englobando todos os 3 anos bissextos
        List<OperacaoFinanceira> extratoTotal = cliente.getExtrato("LP-01",
                LocalDate.of(2020, 1, 1), LocalDate.of(2028, 12, 31));
        afirmarIgual(4, extratoTotal.size(), "deve conter as 4 operações incluindo 2028-02-29");
        afirmarIgual("Op-2028-Biss", extratoTotal.get(3).getDescricao(), "quarta é 2028-02-29");
    }

    public static void testarExtrato30DiasFronteiraBissextoVsComum() {
        // Cenário A: Ano Bissexto (2024)
        // 2024-03-01 minus 30 dias = 2024-01-31 (porque fevereiro/2024 tem 29 dias: 29 em fev + 1 em mar = 30 dias)
        Cliente cliBiss = criarClientePadrao();
        LocalDate refBiss = LocalDate.of(2024, 3, 1);
        AplicacaoFinanceira afBiss = new AplicacaoFinanceira("App Biss", "BISS",
                new BigDecimal("1000.00"), new BigDecimal("0.01"), LocalDate.of(2024, 1, 1));
        MultiplasOperacoesFinanceiras mofBiss = new MultiplasOperacoesFinanceiras(afBiss);

        mofBiss.adicionarOperacao(new OperacaoFinanceira(LocalDate.of(2024, 1, 30), new BigDecimal("10.00"), "31d-atras", TipoOperacao.DEPOSITO));
        mofBiss.adicionarOperacao(new OperacaoFinanceira(LocalDate.of(2024, 1, 31), new BigDecimal("10.00"), "30d-exato", TipoOperacao.DEPOSITO));
        mofBiss.adicionarOperacao(new OperacaoFinanceira(LocalDate.of(2024, 2, 29), new BigDecimal("10.00"), "29-fev", TipoOperacao.DEPOSITO));
        mofBiss.adicionarOperacao(new OperacaoFinanceira(LocalDate.of(2024, 3, 1), new BigDecimal("10.00"), "hoje", TipoOperacao.DEPOSITO));

        cliBiss.adicionarAplicacao(mofBiss);
        cliBiss.atualizarAplicacoes(refBiss); // Configura calendário para 2024-03-01 (Sexta-feira, dia útil)

        List<OperacaoFinanceira> extratoBiss = cliBiss.getExtrato("BISS");
        afirmarIgual(3, extratoBiss.size(), "ano bissexto: 31/01, 29/02 e 01/03 caem no período de 30 dias");
        afirmarIgual("30d-exato", extratoBiss.get(0).getDescricao(), "primeira é 31/01/2024");
        afirmarIgual("29-fev", extratoBiss.get(1).getDescricao(), "segunda é 29/02/2024");
        afirmarIgual("hoje", extratoBiss.get(2).getDescricao(), "terceira é 01/03/2024");

        // Cenário B: Ano Não Bissexto (2023)
        // 2023-03-01 minus 30 dias = 2023-01-30 (porque fevereiro/2023 tem 28 dias: 28 em fev + 1 em mar + 1 em jan = 30 dias)
        Cliente cliComum = criarClientePadrao();
        LocalDate refComum = LocalDate.of(2023, 3, 1);
        AplicacaoFinanceira afComum = new AplicacaoFinanceira("App Comum", "COMUM",
                new BigDecimal("1000.00"), new BigDecimal("0.01"), LocalDate.of(2023, 1, 1));
        MultiplasOperacoesFinanceiras mofComum = new MultiplasOperacoesFinanceiras(afComum);

        mofComum.adicionarOperacao(new OperacaoFinanceira(LocalDate.of(2023, 1, 29), new BigDecimal("10.00"), "31d-atras", TipoOperacao.DEPOSITO));
        mofComum.adicionarOperacao(new OperacaoFinanceira(LocalDate.of(2023, 1, 30), new BigDecimal("10.00"), "30d-exato", TipoOperacao.DEPOSITO));
        mofComum.adicionarOperacao(new OperacaoFinanceira(LocalDate.of(2023, 2, 28), new BigDecimal("10.00"), "28-fev", TipoOperacao.DEPOSITO));
        mofComum.adicionarOperacao(new OperacaoFinanceira(LocalDate.of(2023, 3, 1), new BigDecimal("10.00"), "hoje", TipoOperacao.DEPOSITO));

        cliComum.adicionarAplicacao(mofComum);
        cliComum.atualizarAplicacoes(refComum); // 2023-03-01 (Quarta-feira, dia útil)

        List<OperacaoFinanceira> extratoComum = cliComum.getExtrato("COMUM");
        afirmarIgual(3, extratoComum.size(), "ano comum: 30/01, 28/02 e 01/03 caem no período");
        afirmarIgual("30d-exato", extratoComum.get(0).getDescricao(), "primeira é 30/01/2023");
    }

    public static void testarRejeicao29FevereiroEmAnosNaoBissextos() {
        int[] anosNaoBissextos = {2021, 2022, 2023, 2100, 1900};
        for (int ano : anosNaoBissextos) {
            afirmarRejeicao(() -> new OperacaoFinanceira(29, 2, ano, new BigDecimal("10.00"), "Invalida", TipoOperacao.DEPOSITO),
                    IllegalArgumentException.class,
                    "29/02/" + ano + " deve lançar IllegalArgumentException pois não é ano bissexto");
        }

        // Anos bissextos válidos não devem lançar exceção
        int[] anosBissextos = {2020, 2024, 2028, 2000};
        for (int ano : anosBissextos) {
            OperacaoFinanceira op = new OperacaoFinanceira(29, 2, ano, new BigDecimal("10.00"), "Valida", TipoOperacao.DEPOSITO);
            afirmarIgual(29, op.getDia(), "dia 29 aceito em " + ano);
            afirmarIgual(2, op.getMes(), "mês 2 aceito em " + ano);
        }
    }

    public static void testarExtratoConsolidadoDatasEmbaralhadasMultiplosAnos() {
        Cliente cliente = criarClientePadrao();
        LocalDate base = LocalDate.of(2020, 1, 1);

        // 5 aplicações com datas de 2020 a 2026 embaralhadas
        List<MultiplasOperacoesFinanceiras> apps = new ArrayList<>();
        for (int a = 1; a <= 5; a++) {
            AplicacaoFinanceira af = new AplicacaoFinanceira("App " + a, "A-" + a, new BigDecimal("10000.00"), new BigDecimal("0.01"), base);
            MultiplasOperacoesFinanceiras mof = new MultiplasOperacoesFinanceiras(af);
            cliente.adicionarAplicacao(mof);
            apps.add(mof);
        }

        Random rng = new Random(101);
        int totalOps = 80;
        for (int i = 0; i < totalOps; i++) {
            int appIdx = rng.nextInt(5);
            int diasOffset = rng.nextInt(2000); // Espalhado por ~5.5 anos
            LocalDate d = base.plusDays(diasOffset);
            apps.get(appIdx).adicionarOperacao(new OperacaoFinanceira(d, new BigDecimal("25.00"), "Op-" + i, TipoOperacao.DEPOSITO));
        }

        List<OperacaoFinanceira> consolidado = cliente.getExtratoConsolidado(base, base.plusDays(2500));
        afirmarIgual(totalOps, consolidado.size(), "todas as 80 operações presentes");

        // Validação da ordenação cronológica não-decrescente
        for (int i = 1; i < consolidado.size(); i++) {
            LocalDate ant = consolidado.get(i - 1).getData();
            LocalDate at = consolidado.get(i).getData();
            afirmarFalso(at.isBefore(ant), "ordenação consistente em múltiplos anos");
        }
    }

    public static void testarExtratoConsolidadoValidacaoIntervalosInvalidos() {
        Cliente cliente = criarClientePadrao();
        cliente.adicionarAplicacao(new AplicacaoFinanceira("App", "1", new BigDecimal("100.00"), new BigDecimal("0.01")));

        LocalDate data = LocalDate.of(2026, 6, 1);
        afirmarRejeicao(() -> cliente.getExtratoConsolidado(null, data), NullPointerException.class, "inicio nulo");
        afirmarRejeicao(() -> cliente.getExtratoConsolidado(data, null), NullPointerException.class, "fim nulo");
        afirmarRejeicao(() -> cliente.getExtratoConsolidado(data, data.minusDays(1)),
                IllegalArgumentException.class, "fim antes de inicio");
    }

    // =========================================================================
    // Eixo 4: Mutações Cadastrais de DADOSPESSOAIS e Atomicidade
    // =========================================================================

    public static void testarMutacoesValidasPolimorficasEDadosPessoais() {
        Cliente cliente = criarClientePadrao();
        DADOSPESSOAIS dp = cliente.getDadosPessoais();

        // Mutação 1: alterarDados(nome, celular) preservando o CPF
        String cpfOriginal = dp.getCpf();
        dp.alterarDados("Novo Nome Silva", "11999990000");
        afirmarIgual("Novo Nome Silva", dp.getNome(), "nome atualizado");
        afirmarIgual("11999990000", dp.getCelular(), "celular atualizado");
        afirmarIgual(cpfOriginal, dp.getCpf(), "CPF preservado na sobrecarga de 2 parâmetros");

        // Mutação 2: alterarDados(nome, cpf, celular) com CPF alternativo válido
        dp.alterarDados("Outro Nome Santos", "01234567890", "21988887777");
        afirmarIgual("Outro Nome Santos", dp.getNome(), "nome atualizado");
        afirmarIgual("01234567890", dp.getCpf(), "novo CPF atribuído");
        afirmarIgual("21988887777", dp.getCelular(), "novo celular atribuído");
    }

    public static void testarAtomicidadeTransacionalSobFalhaDeMutacao() {
        Cliente cliente = criarClientePadrao();
        DADOSPESSOAIS dp = cliente.getDadosPessoais();
        String nomeOriginal = dp.getNome();
        String cpfOriginal = dp.getCpf();
        String celularOriginal = dp.getCelular();

        // Falha 1: Nome vazio/nulo
        afirmarRejeicao(() -> dp.alterarDados("", "01234567890", "11999998888"),
                IllegalArgumentException.class, "nome vazio rejeitado");
        afirmarIgual(nomeOriginal, dp.getNome(), "nome inalterado após erro");
        afirmarIgual(cpfOriginal, dp.getCpf(), "CPF inalterado após erro");
        afirmarIgual(celularOriginal, dp.getCelular(), "celular inalterado após erro");

        // Falha 2: CPF matematicamente inválido
        afirmarRejeicao(() -> dp.alterarDados("Nome Tentativa", "12345678900", "11999998888"),
                IllegalArgumentException.class, "CPF inválido rejeitado");
        afirmarIgual(nomeOriginal, dp.getNome(), "nome mantido");
        afirmarIgual(cpfOriginal, dp.getCpf(), "CPF mantido");
        afirmarIgual(celularOriginal, dp.getCelular(), "celular mantido");

        // Falha 3: Celular inválido (menos de 10 dígitos)
        afirmarRejeicao(() -> dp.alterarDados("Nome Tentativa", "01234567890", "12345"),
                IllegalArgumentException.class, "celular curto rejeitado");
        afirmarIgual(nomeOriginal, dp.getNome(), "nome inalterado");
        afirmarIgual(cpfOriginal, dp.getCpf(), "CPF inalterado");
        afirmarIgual(celularOriginal, dp.getCelular(), "celular inalterado");
    }

    public static void testarMutacaoDadosPessoaisComCaracteresAcentuados() {
        Cliente cliente = criarClientePadrao();
        DADOSPESSOAIS dp = cliente.getDadosPessoais();

        String nomeAcentuado = "Édson Arantes do Nascimento & Conceição dos Santos";
        dp.setNome(nomeAcentuado);
        afirmarIgual(nomeAcentuado, dp.getNome(), "suporte integral a caracteres UTF-8 e acentos");

        String celularFixo10Digitos = "1133334444";
        dp.setCelular(celularFixo10Digitos);
        afirmarIgual(celularFixo10Digitos, dp.getCelular(), "celular/telefone de 10 dígitos aceito");
        afirmarIgual("(11) 3333-4444", dp.getCelularFormatado(), "formatação correta de 10 dígitos");
    }

    public static void testarSubstituicaoIntegralDadosPessoaisCliente() {
        Cliente cliente = criarClientePadrao();
        DADOSPESSOAIS novosDados = new DADOSPESSOAIS("Novo Titular", "00000000191", "11977778888");

        cliente.setDadosPessoais(novosDados);
        afirmarIgual(novosDados, cliente.getDadosPessoais(), "novos dados atribuídos");
        afirmarVerdadeiro(cliente.toString().contains("00000000191".substring(9)), "toString reflete novo CPF mascarado");

        afirmarRejeicao(() -> cliente.setDadosPessoais(null), NullPointerException.class, "dados pessoais nulos rejeitados");
    }

    // =========================================================================
    // Eixo 5: Bateria Extrema de CPFs Válidos e Inválidos
    // =========================================================================

    public static void testarCpfsValidosExtremos() {
        // 1. CPF iniciando com 8 zeros e válido
        String cpfZeros = "00000000191";
        afirmarVerdadeiro(DADOSPESSOAIS.validarCpf(cpfZeros), "00000000191 é matematicamente autêntico");
        DADOSPESSOAIS dp1 = new DADOSPESSOAIS("Titular Zero", cpfZeros, "11988887777");
        afirmarIgual(cpfZeros, dp1.getCpf(), "CPF preservado com zeros à esquerda");
        afirmarIgual("***.***.***-91", dp1.getCpfMascarado(), "máscara correta para CPF de zeros");

        // 2. CPF terminando em "00" (d1=0, d2=0)
        String cpfFimZero = "10000003700";
        afirmarVerdadeiro(DADOSPESSOAIS.validarCpf(cpfFimZero), "10000003700 com terminação 00 é válido");
        DADOSPESSOAIS dp2 = new DADOSPESSOAIS("Titular Fim Zero", cpfFimZero, "11988887777");
        afirmarIgual(cpfFimZero, dp2.getCpf(), "CPF terminando em 00 preservado");
        afirmarIgual("***.***.***-00", dp2.getCpfMascarado(), "máscara terminando em 00");

        // 3. CPF sequencial válido
        String cpfSeq = "01234567890";
        afirmarVerdadeiro(DADOSPESSOAIS.validarCpf(cpfSeq), "01234567890 é válido");

        // 4. CPF tradicional
        String cpfPadrao = "52998224725";
        afirmarVerdadeiro(DADOSPESSOAIS.validarCpf(cpfPadrao), "52998224725 é válido");
        afirmarIgual("529.982.247-25", DADOSPESSOAIS.formatarCpf(cpfPadrao), "formatação oficial");

        // 5. CPF informado com pontuação é normalizado com sucesso
        DADOSPESSOAIS dpPontuado = new DADOSPESSOAIS("Pontuado", "529.982.247-25", "11988887777");
        afirmarIgual("52998224725", dpPontuado.getCpf(), "normalizado para 11 dígitos numéricos");
    }

    public static void testarCpfsInvalidos10CasosRepetidos() {
        // Todos os 10 CPFs de 11 dígitos repetidos devem ser rejeitados categoricamente
        for (int d = 0; d <= 9; d++) {
            String repetido = String.valueOf(d).repeat(11);
            afirmarFalso(DADOSPESSOAIS.validarCpf(repetido), "CPF repetido " + repetido + " deve ser falso");
            afirmarRejeicao(() -> new DADOSPESSOAIS("Teste Repetido", repetido, "11988887777"),
                    IllegalArgumentException.class, "DADOSPESSOAIS deve rejeitar CPF " + repetido);
        }
    }

    public static void testarCpfsInvalidosDvsAdulterados() {
        String baseValida = "52998224725"; // d1=2, d2=5

        // Adulteração do 1º Dígito Verificador (índice 9)
        String dv1Adulterado = "52998224735";
        afirmarFalso(DADOSPESSOAIS.validarCpf(dv1Adulterado), "DV1 adulterado");
        afirmarRejeicao(() -> new DADOSPESSOAIS("Teste DV1", dv1Adulterado, "11988887777"),
                IllegalArgumentException.class, "rejeição de DV1 corrompido");

        // Adulteração do 2º Dígito Verificador (índice 10)
        String dv2Adulterado = "52998224726";
        afirmarFalso(DADOSPESSOAIS.validarCpf(dv2Adulterado), "DV2 adulterado");
        afirmarRejeicao(() -> new DADOSPESSOAIS("Teste DV2", dv2Adulterado, "11988887777"),
                IllegalArgumentException.class, "rejeição de DV2 corrompido");

        // Adulteração de ambos os DVs
        String ambosAdulterados = "52998224799";
        afirmarFalso(DADOSPESSOAIS.validarCpf(ambosAdulterados), "ambos DVs adulterados");
        afirmarRejeicao(() -> new DADOSPESSOAIS("Teste Ambos DVs", ambosAdulterados, "11988887777"),
                IllegalArgumentException.class, "rejeição de ambos DVs corrompidos");
    }

    public static void testarCpfsInvalidosTamanhosEFormatos() {
        String[] cpfsInvalidos = {
            null,
            "",
            "   ",
            "1234567890",       // 10 dígitos
            "123456789012",     // 12 dígitos
            "5299822472A",     // alfanumérico
            "ABCDEFGHIJK",     // letras
            "529-982-247/2X"   // formato bizarro com letras/símbolos errados (apenas 10 dígitos)
        };

        for (String c : cpfsInvalidos) {
            afirmarFalso(DADOSPESSOAIS.validarCpf(c), "CPF '" + c + "' deve retornar falso");
            afirmarRejeicao(() -> new DADOSPESSOAIS("Teste Formato", c, "11988887777"),
                    IllegalArgumentException.class,
                    "criação com CPF '" + c + "' deve lançar IllegalArgumentException");
        }
    }

    // =========================================================================
    // Eixo 6: Persistência TSV v3 de Alta Complexidade e Preservação Exata
    // =========================================================================

    public static void testarPersistenciaTsvV3CarteiraComplexa50Aplicacoes() {
        try {
            Cliente original = new Cliente(
                    new DADOSPESSOAIS("Dr. Henrique Meirelles Siqueira", "52998224725", "11987654321"),
                    9876, 543210L
            );

            LocalDate dataBase = LocalDate.of(2026, 6, 1);

            // Popula com exatamente 50 aplicações diversificadas
            for (int i = 1; i <= 50; i++) {
                int tipoRendimento = i % 6; // 0 a 5
                int tipoResgate = i % 5;     // 0 a 4
                int iof = i % 3;             // 0, 1, 2
                int tipoIR = i % 2;          // 0 ou 1
                BigDecimal aporte = new BigDecimal("10000.00").add(new BigDecimal(i * 100));
                BigDecimal taxa = new BigDecimal("0.015").add(new BigDecimal("0.0002").multiply(BigDecimal.valueOf(i)));

                AplicacaoFinanceira af = new AplicacaoFinanceira(
                        "Aplicação Complexa #" + i, "APP-CMP-" + String.format("%03d", i),
                        aporte, taxa, tipoRendimento, 360, 30, tipoResgate, iof, tipoIR, dataBase
                );

                if (i % 3 == 0) {
                    af.setConvencaoContagemDias(ConvencaoContagemDias.EFETIVO_360);
                    af.setRegimeTributario(RegimeTributario.ISENTO);
                } else if (i % 2 == 0) {
                    af.setConvencaoContagemDias(ConvencaoContagemDias.CIVIL_365);
                    af.setRegimeTributario(RegimeTributario.ISENTO);
                } else {
                    af.setConvencaoContagemDias(ConvencaoContagemDias.COMERCIAL_30_360);
                    af.setRegimeTributario(RegimeTributario.PADRAO);
                }

                MultiplasOperacoesFinanceiras mof = new MultiplasOperacoesFinanceiras(af);

                // Adiciona de 2 a 5 operações de fluxo por aplicação
                int numOps = 2 + (i % 4);
                for (int opIdx = 1; opIdx <= numOps; opIdx++) {
                    mof.adicionarOperacao(new OperacaoFinanceira(
                            dataBase.plusDays(opIdx * 3),
                            new BigDecimal("50.00").multiply(BigDecimal.valueOf(opIdx)),
                            "Aporte extra #" + opIdx + " para App " + i,
                            TipoOperacao.DEPOSITO
                    ));
                }

                original.adicionarAplicacao(mof);
            }

            // Simula 45 dias corridos de evolução temporal no cliente para apurar rendimentos e tributos
            LocalDate cursor = dataBase;
            for (int d = 0; d < 45; d++) {
                cursor = cursor.plusDays(1);
                original.atualizarAplicacoes(cursor);
            }

            BigDecimal saldoConsolidadoOriginal = original.getSaldoConsolidado();
            afirmarIgual(50, original.getQuantidadeAplicacoes(), "50 aplicações na carteira original");

            // Persistência em arquivo temporário TSV v3
            Path tempTsv = Files.createTempFile("teste_challenger_m2_50apps_", ".tsv");
            try {
                original.arquivoSalvarDados(tempTsv);
                afirmarVerdadeiro(Files.exists(tempTsv), "arquivo TSV gravado");
                afirmarVerdadeiro(Files.size(tempTsv) > 1000, "arquivo não-vazio com porte expressivo");

                // Restauração a partir do arquivo
                Cliente restaurado = Cliente.arquivoLerDados(tempTsv);

                // 1. Auditoria Cadastral
                afirmarIgual(original.getNumeroAgencia(), restaurado.getNumeroAgencia(), "agência bancária");
                afirmarIgual(original.getNumeroConta(), restaurado.getNumeroConta(), "número da conta");
                afirmarIgual(original.getDadosPessoais().getNome(), restaurado.getDadosPessoais().getNome(), "nome do titular");
                afirmarIgual(original.getDadosPessoais().getCpf(), restaurado.getDadosPessoais().getCpf(), "CPF do titular");
                afirmarIgual(original.getDadosPessoais().getCelular(), restaurado.getDadosPessoais().getCelular(), "celular do titular");
                afirmarIgual(50, restaurado.getQuantidadeAplicacoes(), "exatamente 50 aplicações restauradas");

                // 2. Auditoria Consolidada
                afirmarIgual(saldoConsolidadoOriginal, restaurado.getSaldoConsolidado(),
                        "saldo consolidado preservado com paridade bit a bit ao centavo");

                // 3. Auditoria Ponto a Ponto em Todas as 50 Aplicações
                for (int i = 1; i <= 50; i++) {
                    String cod = "APP-CMP-" + String.format("%03d", i);
                    MultiplasOperacoesFinanceiras mofOrig = original.requererAplicacao(cod);
                    MultiplasOperacoesFinanceiras mofRest = restaurado.requererAplicacao(cod);

                    AplicacaoFinanceira afOrig = mofOrig.getAplicacao();
                    AplicacaoFinanceira afRest = mofRest.getAplicacao();

                    // Saldos e Grandezas Fiscais
                    afirmarIgual(afOrig.getSaldo(), afRest.getSaldo(), "saldo em " + cod);
                    afirmarIgual(afOrig.getValorAtualBruto(), afRest.getValorAtualBruto(), "valorAtualBruto em " + cod);
                    afirmarIgual(afOrig.getValorAtualLiquido(), afRest.getValorAtualLiquido(), "valorAtualLiquido em " + cod);
                    afirmarIgual(afOrig.getRendimentoAtualBruto(), afRest.getRendimentoAtualBruto(), "rendimentoAtualBruto em " + cod);
                    afirmarIgual(afOrig.getRendimentoAtualLiquido(), afRest.getRendimentoAtualLiquido(), "rendimentoAtualLiquido em " + cod);
                    afirmarIgual(afOrig.getImpostoAtualDevido(), afRest.getImpostoAtualDevido(), "impostoAtualDevido em " + cod);

                    // Invariante Contábil
                    afirmarVerdadeiro(afRest.getValorAtualBruto().compareTo(afRest.getValorAtualLiquido()) >= 0,
                            "invariante contábil: ValorAtualBruto >= ValorAtualLiquido em " + cod);

                    // Parâmetros Estruturais
                    afirmarIgual(afOrig.getNomeAplicacao(), afRest.getNomeAplicacao(), "nome em " + cod);
                    afirmarIgual(afOrig.getValorAplicado(), afRest.getValorAplicado(), "valorAplicado em " + cod);
                    afirmarIgual(afOrig.getTaxaMensal(), afRest.getTaxaMensal(), "taxaMensal em " + cod);
                    afirmarIgual(afOrig.getDataAplicacao(), afRest.getDataAplicacao(), "dataAplicacao em " + cod);
                    afirmarIgual(afOrig.getDataUltimaAtualizacao(), afRest.getDataUltimaAtualizacao(), "dataUltimaAtualizacao em " + cod);
                    afirmarIgual(afOrig.getDiasCorridos(), afRest.getDiasCorridos(), "diasCorridos em " + cod);
                    afirmarIgual(afOrig.getDiasEfetivosCalculo(), afRest.getDiasEfetivosCalculo(), "diasEfetivosCalculo em " + cod);
                    afirmarIgual(afOrig.getConvencaoContagemDias(), afRest.getConvencaoContagemDias(), "convencao em " + cod);
                    afirmarIgual(afOrig.getRegimeTributario(), afRest.getRegimeTributario(), "regime em " + cod);
                    afirmarIgual(afOrig.getTipoRendimento(), afRest.getTipoRendimento(), "tipoRendimento em " + cod);
                    afirmarIgual(afOrig.getTipoResgate(), afRest.getTipoResgate(), "tipoResgate em " + cod);
                    afirmarIgual(afOrig.getIof(), afRest.getIof(), "iof em " + cod);
                    afirmarIgual(afOrig.getTipoImpostoRenda(), afRest.getTipoImpostoRenda(), "tipoIR em " + cod);

                    // Operações
                    afirmarIgual(mofOrig.getOperacoes().size(), mofRest.getOperacoes().size(), "total operações em " + cod);
                    for (int o = 0; o < mofOrig.getOperacoes().size(); o++) {
                        OperacaoFinanceira opOrig = mofOrig.getOperacoes().get(o);
                        OperacaoFinanceira opRest = mofRest.getOperacoes().get(o);
                        afirmarIgual(opOrig.getData(), opRest.getData(), "data op " + o + " em " + cod);
                        afirmarIgual(opOrig.getValor(), opRest.getValor(), "valor op " + o + " em " + cod);
                        afirmarIgual(opOrig.getDescricao(), opRest.getDescricao(), "descrição op " + o + " em " + cod);
                        afirmarIgual(opOrig.getTipo(), opRest.getTipo(), "tipo op " + o + " em " + cod);
                    }
                }

                System.out.printf("  [PARIDADE TSV v3 AUDITADA: 50 aplicações, %d asserções verificadas sem desvio]%n",
                        50 * 20);

            } finally {
                Files.deleteIfExists(tempTsv);
            }
        } catch (IOException e) {
            throw new AssertionError("Falha inesperada de E/S no teste de persistência: " + e.getMessage(), e);
        }
    }

    public static void testarPersistenciaTsvV3ResilienciaRegistrosTruncados() {
        try {
            Path temp = Files.createTempFile("teste_corrupcao_challenger_", ".tsv");
            try {
                // 1. Registro I truncado na 10ª coluna (< 23)
                Files.writeString(temp, "NATIONAL-ECONOMIC\t3\n"
                        + "C\t52998224725\tNome\t11999998888\t1001\t20002\n"
                        + "I\t52998224725\tAPP-01\tCDB\t1000.00\t0.01\t2026-01-01\t1000.00\t1000.00\t2026-01-01\n");
                afirmarRejeicao(() -> {
                    try { Cliente.arquivoLerDados(temp); }
                    catch (IOException e) { throw new RuntimeException(e); }
                }, IllegalArgumentException.class, "registro I com 10 colunas deve lançar IllegalArgumentException");

                // 2. Registro O truncado na 5ª coluna (< 7)
                Files.writeString(temp, "NATIONAL-ECONOMIC\t3\n"
                        + "C\t52998224725\tNome\t11999998888\t1001\t20002\n"
                        + "I\t52998224725\tAPP-01\tCDB\t1000.00\t0.01\t2026-01-01\t1000.00\t1000.00\t2026-01-01\t0\t0\t0\tCOMERCIAL_30_360\tPADRAO\t0\t0\t0\t0\t0\t0\t0.00\t1000.00\n"
                        + "O\t52998224725\tAPP-01\t2026-01-01\t100.00\n");
                afirmarRejeicao(() -> {
                    try { Cliente.arquivoLerDados(temp); }
                    catch (IOException e) { throw new RuntimeException(e); }
                }, IllegalArgumentException.class, "registro O com 5 colunas deve lançar IllegalArgumentException");

                // 3. Linha I contendo dados numéricos alfabéticos (corrupção de saldo)
                Files.writeString(temp, "NATIONAL-ECONOMIC\t3\n"
                        + "C\t52998224725\tNome\t11999998888\t1001\t20002\n"
                        + "I\t52998224725\tAPP-01\tCDB\tVALOR_INVALIDO\t0.01\t2026-01-01\t1000.00\t1000.00\t2026-01-01\t0\t0\t0\tCOMERCIAL_30_360\tPADRAO\t0\t0\t0\t0\t0\t0\t0.00\t1000.00\n");
                afirmarRejeicao(() -> {
                    try { Cliente.arquivoLerDados(temp); }
                    catch (IOException e) { throw new RuntimeException(e); }
                }, NumberFormatException.class, "valor monetário inválido em I deve lançar NumberFormatException");

            } finally {
                Files.deleteIfExists(temp);
            }
        } catch (IOException e) {
            throw new AssertionError(e.getMessage(), e);
        }
    }

    // =========================================================================
    // Métodos Utilitários
    // =========================================================================

    private static Cliente criarClientePadrao() {
        DADOSPESSOAIS dados = new DADOSPESSOAIS("Challenger 1 Gate M2", "52998224725", "11988887777");
        return new Cliente(dados, 1001, 77777L);
    }
}

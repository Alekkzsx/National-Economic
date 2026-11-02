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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Bateria Adversarial Hostil - Challenger 2 (Iteração 2 do Marco 2).
 *
 * <p>Audita e estressa exaustivamente os seguintes eixos:</p>
 * <ol>
 *   <li>Ciclos múltiplos sucessivos de persistência (5 round-trips consecutivos com paridade integral);</li>
 *   <li>Invariante estrita ValorBruto &gt;= ValorLiquido em matriz combinatória (regimes, IOF, IR, carências);</li>
 *   <li>Rejeição estrita com IllegalArgumentException para linhas truncadas em 'I' (1 a 22 cols), 'O' (1 a 6 cols) e 'C' (1 a 5 cols);</li>
 *   <li>Retrocompatibilidade com formato v3 de 23 colunas (sem exceção, leitura e re-gravação);</li>
 *   <li>Preservação bit a bit dos 19 atributos no formato de 26 colunas;</li>
 *   <li>Resiliência de escape para caracteres especiais (tabs, quebras de linha, barras invertidas);</li>
 *   <li>Escala limite com 50 aplicações e 1000 operações em round-trip.</li>
 * </ol>
 */
public final class TesteHostilM2Iter2_2 {

    public static void main(String[] args) {
        System.out.println("================================================================================");
        System.out.println(">>> INICIANDO BATERIA HOSTIL ADVERSARIAL M2_ITER2_2 (Challenger 2)");
        System.out.println("================================================================================");
        long inicio = System.currentTimeMillis();
        int totalTestes = 0;

        // 1. Ciclos múltiplos de salvar/ler (5 round-trips sucessivos)
        testarRoundTripSucessivo5CiclosIntegridadeTotal(); totalTestes++;

        // 2. Invariante ValorBruto >= ValorLiquido em matriz combinatória
        testarInvarianteBrutoMaiorOuIgualLiquidoCombinatoriaCompleta(); totalTestes++;

        // 3. Rejeição estrita de linhas truncadas em 'I' (1 a 22 colunas)
        testarRejeicaoEstritaLinhasTruncadasRegistroI(); totalTestes++;

        // 4. Rejeição estrita de linhas truncadas em 'O' (1 a 6 colunas)
        testarRejeicaoEstritaLinhasTruncadasRegistroO(); totalTestes++;

        // 5. Rejeição estrita de linhas truncadas em 'C' (1 a 5 colunas)
        testarRejeicaoEstritaLinhasTruncadasRegistroC(); totalTestes++;

        // 6. Retrocompatibilidade: formato v3 com 23 colunas
        testarRetrocompatibilidadeTSVv3Com23Colunas(); totalTestes++;

        // 7. Preservação bit a bit de todos os 19 atributos em 26 colunas
        testarPreservacaoBitABit19AtributosFormato26Colunas(); totalTestes++;

        // 8. Resiliência de escape e caracteres especiais
        testarResilienciaCaracteresEspeciaisEscape(); totalTestes++;

        // 9. Estresse de escala: 50 aplicações e 1000 operações em round-trip
        testarMultiplasAplicacoesEOperacoesEscala(); totalTestes++;

        long duracao = System.currentTimeMillis() - inicio;
        System.out.println("================================================================================");
        System.out.printf(">>> BATERIA HOSTIL M2_ITER2_2 CONCLUÍDA: %d métodos executados (%d ms)%n",
                totalTestes, duracao);
        System.out.println("================================================================================");
    }

    // =========================================================================
    // 1. Ciclos Múltiplos de Salvar/Ler (5 Round-Trips Consecutivos)
    // =========================================================================

    public static void testarRoundTripSucessivo5CiclosIntegridadeTotal() {
        System.out.println("[TESTE] Executando 5 round-trips sucessivos de gravação e leitura...");
        try {
            Cliente original = new Cliente(
                    new DADOSPESSOAIS("Carlos Drummond de Andrade", "52998224725", "21999887766"),
                    1234, 567890L
            );

            LocalDate dataBase = LocalDate.of(2026, 8, 1);

            // App 1: Regime PADRAO, Rendimento Mensal (tipo 1), com acumulação e impostos
            AplicacaoFinanceira af1 = new AplicacaoFinanceira(
                    "CDB DI Flex", "APP-001", new BigDecimal("50000.00"), new BigDecimal("0.02"),
                    1, 360, 30, 1, 1, 1, dataBase
            );
            MultiplasOperacoesFinanceiras mof1 = new MultiplasOperacoesFinanceiras(af1);
            mof1.adicionarOperacao(new OperacaoFinanceira(dataBase, new BigDecimal("50000.00"), "Aporte Inicial", TipoOperacao.DEPOSITO));
            mof1.adicionarOperacao(new OperacaoFinanceira(dataBase.plusDays(10), new BigDecimal("5000.00"), "Aporte Extra", TipoOperacao.DEPOSITO));
            original.adicionarAplicacao(mof1);

            // App 2: Regime ISENTO, Rendimento Diário (tipo 0)
            AplicacaoFinanceira af2 = new AplicacaoFinanceira(
                    "LCI Verde", "APP-002", new BigDecimal("80000.00"), new BigDecimal("0.015"),
                    0, 180, 90, 0, 0, 0, dataBase
            );
            af2.setRegimeTributario(RegimeTributario.ISENTO);
            MultiplasOperacoesFinanceiras mof2 = new MultiplasOperacoesFinanceiras(af2);
            mof2.adicionarOperacao(new OperacaoFinanceira(dataBase, new BigDecimal("80000.00"), "Aporte LCI", TipoOperacao.DEPOSITO));
            original.adicionarAplicacao(mof2);

            // App 3: Regime PADRAO, Rendimento Semestral (tipo 4), IOF irrestrito (2)
            AplicacaoFinanceira af3 = new AplicacaoFinanceira(
                    "LCA Master", "APP-003", new BigDecimal("30000.00"), new BigDecimal("0.025"),
                    4, 720, 180, 2, 2, 1, dataBase
            );
            MultiplasOperacoesFinanceiras mof3 = new MultiplasOperacoesFinanceiras(af3);
            mof3.adicionarOperacao(new OperacaoFinanceira(dataBase, new BigDecimal("30000.00"), "Aporte LCA", TipoOperacao.DEPOSITO));
            original.adicionarAplicacao(mof3);

            // Simular 60 dias de rentabilidade
            LocalDate cursor = dataBase;
            for (int d = 0; d < 60; d++) {
                cursor = cursor.plusDays(1);
                original.atualizarAplicacoes(cursor);
            }

            // Realizar um resgate parcial na App 1 (carência de 30 dias expirou e dia 60 é múltiplo de 30)
            mof1.adicionarOperacao(new OperacaoFinanceira(cursor, new BigDecimal("2000.00"), "Resgate Parcial", TipoOperacao.RETIRADA));

            // Salvar snapshots dos valores de referência originais
            BigDecimal brutoOrig1 = af1.getValorAtualBruto();
            BigDecimal liquidoOrig1 = af1.getValorAtualLiquido();
            BigDecimal saldoOrig1 = af1.getSaldo();
            BigDecimal impostoOrig1 = af1.getImpostoAtualDevido();
            BigDecimal rendBrutoOrig1 = af1.getRendimentoAtualBruto();
            BigDecimal rendLiqOrig1 = af1.getRendimentoAtualLiquido();
            BigDecimal resgateOrig1 = af1.getValorResgate();

            BigDecimal brutoOrig2 = af2.getValorAtualBruto();
            BigDecimal liquidoOrig2 = af2.getValorAtualLiquido();
            BigDecimal impostoOrig2 = af2.getImpostoAtualDevido();

            BigDecimal saldoConsolidadoOrig = original.getSaldoConsolidado();

            Path tempDir = Files.createTempDirectory("roundtrip_adversarial_");
            try {
                Cliente atual = original;
                String conteudoArquivoAnterior = null;

                for (int ciclo = 1; ciclo <= 5; ciclo++) {
                    Path arquivoCiclo = tempDir.resolve("cliente_ciclo_" + ciclo + ".tsv");
                    atual.arquivoSalvarDados(arquivoCiclo);

                    String conteudoAtual = Files.readString(arquivoCiclo, StandardCharsets.UTF_8);
                    if (ciclo > 2) {
                        afirmarIgual(conteudoArquivoAnterior, conteudoAtual,
                                "conteúdo TSV deve ser estritamente idêntico entre ciclos sucessivos 2..5 (idempotência)");
                    }
                    conteudoArquivoAnterior = conteudoAtual;

                    // Ler de volta
                    Cliente restaurado = Cliente.arquivoLerDados(arquivoCiclo);

                    // Validações do Cliente
                    afirmarIgual(original.getNumeroAgencia(), restaurado.getNumeroAgencia(), "ciclo " + ciclo + ": agência");
                    afirmarIgual(original.getNumeroConta(), restaurado.getNumeroConta(), "ciclo " + ciclo + ": conta");
                    afirmarIgual(original.getDadosPessoais().getNome(), restaurado.getDadosPessoais().getNome(), "ciclo " + ciclo + ": titular");
                    afirmarIgual(original.getDadosPessoais().getCpf(), restaurado.getDadosPessoais().getCpf(), "ciclo " + ciclo + ": cpf");
                    afirmarIgual(original.getDadosPessoais().getCelular(), restaurado.getDadosPessoais().getCelular(), "ciclo " + ciclo + ": celular");
                    afirmarIgual(3, restaurado.getQuantidadeAplicacoes(), "ciclo " + ciclo + ": total apps");
                    afirmarIgual(saldoConsolidadoOrig, restaurado.getSaldoConsolidado(), "ciclo " + ciclo + ": saldo consolidado");

                    // Validações da App 1
                    AplicacaoFinanceira afRest1 = restaurado.requererAplicacao("APP-001").getAplicacao();
                    afirmarIgual(brutoOrig1, afRest1.getValorAtualBruto(), "ciclo " + ciclo + " App1: valorAtualBruto");
                    afirmarIgual(liquidoOrig1, afRest1.getValorAtualLiquido(), "ciclo " + ciclo + " App1: valorAtualLiquido");
                    afirmarIgual(saldoOrig1, afRest1.getSaldo(), "ciclo " + ciclo + " App1: saldo");
                    afirmarIgual(impostoOrig1, afRest1.getImpostoAtualDevido(), "ciclo " + ciclo + " App1: impostoAtualDevido");
                    afirmarIgual(rendBrutoOrig1, afRest1.getRendimentoAtualBruto(), "ciclo " + ciclo + " App1: rendimentoAtualBruto");
                    afirmarIgual(rendLiqOrig1, afRest1.getRendimentoAtualLiquido(), "ciclo " + ciclo + " App1: rendimentoAtualLiquido");
                    afirmarIgual(resgateOrig1, afRest1.getValorResgate(), "ciclo " + ciclo + " App1: valorResgate");
                    afirmarVerdadeiro(afRest1.getValorAtualBruto().compareTo(afRest1.getValorAtualLiquido()) >= 0,
                            "ciclo " + ciclo + " App1: Bruto >= Liquido");

                    // Validações das operações da App 1 (3 operações: 2 depósitos + 1 resgate)
                    List<OperacaoFinanceira> ops1 = restaurado.requererAplicacao("APP-001").getOperacoes();
                    afirmarIgual(3, ops1.size(), "ciclo " + ciclo + " App1: 3 operações");
                    afirmarIgual("Aporte Inicial", ops1.get(0).getDescricao(), "ciclo " + ciclo + " App1: op 1");
                    afirmarIgual("Aporte Extra", ops1.get(1).getDescricao(), "ciclo " + ciclo + " App1: op 2");
                    afirmarIgual("Resgate Parcial", ops1.get(2).getDescricao(), "ciclo " + ciclo + " App1: op 3");
                    afirmarIgual(TipoOperacao.RETIRADA, ops1.get(2).getTipo(), "ciclo " + ciclo + " App1: tipo da op 3");

                    // Validações da App 2 (Isenta)
                    AplicacaoFinanceira afRest2 = restaurado.requererAplicacao("APP-002").getAplicacao();
                    afirmarIgual(brutoOrig2, afRest2.getValorAtualBruto(), "ciclo " + ciclo + " App2: valorAtualBruto");
                    afirmarIgual(liquidoOrig2, afRest2.getValorAtualLiquido(), "ciclo " + ciclo + " App2: valorAtualLiquido");
                    afirmarIgual(impostoOrig2, afRest2.getImpostoAtualDevido(), "ciclo " + ciclo + " App2: impostoAtualDevido (zero)");
                    afirmarIgual(RegimeTributario.ISENTO, afRest2.getRegimeTributario(), "ciclo " + ciclo + " App2: regime isento");
                    afirmarVerdadeiro(afRest2.getValorAtualBruto().compareTo(afRest2.getValorAtualLiquido()) >= 0,
                            "ciclo " + ciclo + " App2: Bruto >= Liquido");

                    // Para o próximo ciclo, serializar a instância recém-restaurada
                    atual = restaurado;
                }
                System.out.println("  -> [OK] 5 ciclos sucessivos concluídos com 100% de paridade e idempotência.");
            } finally {
                // Limpeza do diretório temporário
                try (var s = Files.walk(tempDir)) {
                    s.sorted((a, b) -> b.compareTo(a)).forEach(p -> {
                        try { Files.deleteIfExists(p); } catch (Exception ignored) {}
                    });
                }
            }
        } catch (IOException e) {
            throw new AssertionError("Falha em teste de 5 round-trips: " + e.getMessage(), e);
        }
    }

    // =========================================================================
    // 2. Invariante ValorBruto >= ValorLiquido em Matriz Combinatória Completa
    // =========================================================================

    public static void testarInvarianteBrutoMaiorOuIgualLiquidoCombinatoriaCompleta() {
        System.out.println("[TESTE] Executando matriz combinatória exaustiva de regimes e carências...");
        RegimeTributario[] regimes = { RegimeTributario.PADRAO, RegimeTributario.ISENTO };
        int[] iofOpcoes = { 0, 1, 2 };
        int[] irOpcoes = { 0, 1 };
        int[] tiposRendimento = { 0, 1, 2, 3, 4, 5 };
        int[] carencias = { 0, 30, 60, 180 };
        int[] prazosSimulacao = { 0, 15, 30, 45, 90, 185 };

        LocalDate dataBase = LocalDate.of(2026, 1, 5); // Segunda-feira
        int combinacoesTestadas = 0;

        for (RegimeTributario reg : regimes) {
            for (int iof : iofOpcoes) {
                for (int ir : irOpcoes) {
                    for (int rend : tiposRendimento) {
                        for (int car : carencias) {
                            for (int dias : prazosSimulacao) {
                                combinacoesTestadas++;
                                Cliente cliente = new Cliente(
                                        new DADOSPESSOAIS("Teste Combinatorio", "52998224725", "11988887777"),
                                        101, 202L
                                );

                                AplicacaoFinanceira af = new AplicacaoFinanceira(
                                        "App Comb " + combinacoesTestadas, "ID-" + combinacoesTestadas,
                                        new BigDecimal("10000.00"), new BigDecimal("0.02"),
                                        rend, 360, car, 0, iof, ir, dataBase
                                );
                                af.setRegimeTributario(reg);
                                MultiplasOperacoesFinanceiras mof = new MultiplasOperacoesFinanceiras(af);
                                mof.adicionarOperacao(new OperacaoFinanceira(
                                        dataBase, new BigDecimal("10000.00"), "Aporte", TipoOperacao.DEPOSITO));
                                cliente.adicionarAplicacao(mof);

                                // Simulação dos dias
                                LocalDate cursor = dataBase;
                                for (int d = 0; d < dias; d++) {
                                    cursor = cursor.plusDays(1);
                                    cliente.atualizarAplicacoes(cursor);
                                }

                                // Verificação pré-persistência
                                BigDecimal brutoPre = af.getValorAtualBruto();
                                BigDecimal liquidoPre = af.getValorAtualLiquido();
                                BigDecimal impostoPre = af.getImpostoAtualDevido();
                                afirmarVerdadeiro(brutoPre.compareTo(liquidoPre) >= 0,
                                        String.format("Pré-persistência falhou em Bruto >= Liquido (%s < %s) [reg=%s, iof=%d, ir=%d, rend=%d, car=%d, dias=%d]",
                                                brutoPre, liquidoPre, reg, iof, ir, rend, car, dias));

                                // Round-trip via TSV em memória usando arquivos temporários
                                try {
                                    Path temp = Files.createTempFile("comb_test_", ".tsv");
                                    try {
                                        cliente.arquivoSalvarDados(temp);
                                        Cliente rest = Cliente.arquivoLerDados(temp);
                                        AplicacaoFinanceira afRest = rest.requererAplicacao("ID-" + combinacoesTestadas).getAplicacao();

                                        BigDecimal brutoPos = afRest.getValorAtualBruto();
                                        BigDecimal liquidoPos = afRest.getValorAtualLiquido();
                                        BigDecimal impostoPos = afRest.getImpostoAtualDevido();

                                        afirmarVerdadeiro(brutoPos.compareTo(liquidoPos) >= 0,
                                                String.format("Pós-persistência falhou em Bruto >= Liquido (%s < %s) [reg=%s, iof=%d, ir=%d, rend=%d, car=%d, dias=%d]",
                                                        brutoPos, liquidoPos, reg, iof, ir, rend, car, dias));
                                        afirmarIgual(brutoPre, brutoPos, "bruto deve ser perfeitamente preservado");
                                        afirmarIgual(liquidoPre, liquidoPos, "líquido deve ser perfeitamente preservado");
                                        afirmarIgual(impostoPre, impostoPos, "imposto deve ser perfeitamente preservado");
                                        afirmarIgual(reg, afRest.getRegimeTributario(), "regime deve ser preservado");

                                    } finally {
                                        Files.deleteIfExists(temp);
                                    }
                                } catch (IOException e) {
                                    throw new AssertionError("Erro de E/S na combinação: " + e.getMessage(), e);
                                }
                            }
                        }
                    }
                }
            }
        }
        System.out.printf("  -> [OK] %d combinações verificadas: Invariante Bruto >= Liquido estritamente satisfeita em 100%% dos casos.%n",
                combinacoesTestadas);
    }

    // =========================================================================
    // 3. Rejeição Estrita de Linhas Truncadas em 'I' (1 a 22 Colunas)
    // =========================================================================

    public static void testarRejeicaoEstritaLinhasTruncadasRegistroI() {
        System.out.println("[TESTE] Validando rejeição estrita (IllegalArgumentException) de registros 'I' truncados (1 a 22 cols)...");
        // Registro 'I' canônico de 26 colunas:
        // 0: "I"
        // 1: cpf
        // 2: numero
        // 3: nome
        // 4: valorAplicado
        // 5: taxaMensal
        // 6: dataAplicacao
        // 7: saldo
        // 8: valorAtualBruto
        // 9: dataUltimaAtualizacao
        // 10: diasCorridos
        // 11: diasEfetivosCalculo
        // 12: quantidadeOperacoes
        // 13: convencaoContagemDias
        // 14: regimeTributario
        // 15: tipoRendimento
        // 16: tempoFinalVencimento
        // 17: tempoMinimoPermanencia
        // 18: tipoResgate
        // 19: iof
        // 20: tipoImpostoRenda
        // 21: valorResgate
        // 22: valorAtualLiquido
        // 23: rendimentoAtualBruto
        // 24: rendimentoAtualLiquido
        // 25: impostoAtualDevido

        String[] camposValidos = {
                "I", "52998224725", "APP-1", "App Teste", "1000.00", "0.01",
                "2026-01-01", "1000.00", "1000.00", "2026-01-01", "0", "0",
                "0", "COMERCIAL_30_360", "PADRAO", "0", "0", "0",
                "0", "0", "0", "0.00", "1000.00", "0.00", "0.00", "0.00"
        };

        for (int cols = 1; cols <= 22; cols++) {
            StringBuilder linhaI = new StringBuilder();
            for (int c = 0; c < cols; c++) {
                if (c > 0) linhaI.append("\t");
                linhaI.append(camposValidos[c]);
            }

            String conteudo = "NATIONAL-ECONOMIC\t3\n"
                    + "C\t52998224725\tNome Teste\t11999998888\t1001\t20002\n"
                    + linhaI.toString() + "\n";

            int colCountFinal = cols;
            try {
                Path temp = Files.createTempFile("trunc_i_" + cols + "_", ".tsv");
                try {
                    Files.writeString(temp, conteudo, StandardCharsets.UTF_8);

                    afirmarRejeicao(() -> {
                        try {
                            Cliente.arquivoLerDados(temp);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }, IllegalArgumentException.class,
                    "registro 'I' com " + colCountFinal + " colunas deve lançar estritamente IllegalArgumentException");

                } finally {
                    Files.deleteIfExists(temp);
                }
            } catch (IOException e) {
                throw new AssertionError("Falha ao gerar arquivo de teste: " + e.getMessage(), e);
            }
        }
        System.out.println("  -> [OK] Todas as 22 variantes truncadas de 'I' (1 a 22 colunas) rejeitadas estritamente com IllegalArgumentException.");
    }

    // =========================================================================
    // 4. Rejeição Estrita de Linhas Truncadas em 'O' (1 a 6 Colunas)
    // =========================================================================

    public static void testarRejeicaoEstritaLinhasTruncadasRegistroO() {
        System.out.println("[TESTE] Validando rejeição estrita (IllegalArgumentException) de registros 'O' truncados (1 a 6 cols)...");
        // Registro 'O' canônico de 7 colunas:
        // 0: "O"
        // 1: cpf
        // 2: numeroAplicacao
        // 3: data
        // 4: valor
        // 5: descricao
        // 6: tipo

        String[] camposOValidos = {
                "O", "52998224725", "APP-1", "2026-01-01", "100.00", "Aporte Inicial", "DEPOSITO"
        };

        String linhaC = "C\t52998224725\tNome Teste\t11999998888\t1001\t20002\n";
        String linhaIValida = "I\t52998224725\tAPP-1\tApp Teste\t1000.00\t0.01\t2026-01-01\t1000.00\t1000.00\t2026-01-01\t0\t0\t0\tCOMERCIAL_30_360\tPADRAO\t0\t0\t0\t0\t0\t0\t0.00\t1000.00\t0.00\t0.00\t0.00\n";

        for (int cols = 1; cols <= 6; cols++) {
            StringBuilder linhaO = new StringBuilder();
            for (int c = 0; c < cols; c++) {
                if (c > 0) linhaO.append("\t");
                linhaO.append(camposOValidos[c]);
            }

            String conteudo = "NATIONAL-ECONOMIC\t3\n" + linhaC + linhaIValida + linhaO.toString() + "\n";
            int colCountFinal = cols;

            try {
                Path temp = Files.createTempFile("trunc_o_" + cols + "_", ".tsv");
                try {
                    Files.writeString(temp, conteudo, StandardCharsets.UTF_8);

                    afirmarRejeicao(() -> {
                        try {
                            Cliente.arquivoLerDados(temp);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }, IllegalArgumentException.class,
                    "registro 'O' com " + colCountFinal + " colunas deve lançar estritamente IllegalArgumentException");

                } finally {
                    Files.deleteIfExists(temp);
                }
            } catch (IOException e) {
                throw new AssertionError("Falha ao gerar arquivo de teste: " + e.getMessage(), e);
            }
        }
        System.out.println("  -> [OK] Todas as 6 variantes truncadas de 'O' (1 a 6 colunas) rejeitadas estritamente com IllegalArgumentException.");
    }

    // =========================================================================
    // 5. Rejeição Estrita de Linhas Truncadas em 'C' (1 a 5 Colunas)
    // =========================================================================

    public static void testarRejeicaoEstritaLinhasTruncadasRegistroC() {
        System.out.println("[TESTE] Validando rejeição estrita (IllegalArgumentException) de registros 'C' truncados (1 a 5 cols)...");
        // Registro 'C' canônico de 6 colunas:
        // 0: "C"
        // 1: cpf
        // 2: nome
        // 3: celular
        // 4: agencia
        // 5: conta

        String[] camposCValidos = {
                "C", "52998224725", "Carlos Drummond", "11999998888", "1001", "20002"
        };

        for (int cols = 1; cols <= 5; cols++) {
            StringBuilder linhaC = new StringBuilder();
            for (int c = 0; c < cols; c++) {
                if (c > 0) linhaC.append("\t");
                linhaC.append(camposCValidos[c]);
            }

            String conteudo = "NATIONAL-ECONOMIC\t3\n" + linhaC.toString() + "\n";
            int colCountFinal = cols;

            try {
                Path temp = Files.createTempFile("trunc_c_" + cols + "_", ".tsv");
                try {
                    Files.writeString(temp, conteudo, StandardCharsets.UTF_8);

                    afirmarRejeicao(() -> {
                        try {
                            Cliente.arquivoLerDados(temp);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }, IllegalArgumentException.class,
                    "registro 'C' com " + colCountFinal + " colunas deve lançar estritamente IllegalArgumentException");

                } finally {
                    Files.deleteIfExists(temp);
                }
            } catch (IOException e) {
                throw new AssertionError("Falha ao gerar arquivo de teste: " + e.getMessage(), e);
            }
        }
        System.out.println("  -> [OK] Todas as 5 variantes truncadas de 'C' (1 a 5 colunas) rejeitadas estritamente com IllegalArgumentException.");
    }

    // =========================================================================
    // 6. Retrocompatibilidade: Formato v3 com 23 Colunas
    // =========================================================================

    public static void testarRetrocompatibilidadeTSVv3Com23Colunas() {
        System.out.println("[TESTE] Validando retrocompatibilidade com formato v3 de 23 colunas (pré-extensão)...");
        try {
            // Montar linha de 23 colunas (índices 0 a 22):
            // 0: I
            // 1: 52998224725
            // 2: APP-LEGACY-01
            // 3: Fundo Renda Fixa Antigo
            // 4: 10000.00
            // 5: 0.0150
            // 6: 2026-01-01
            // 7: 10500.00  (saldo)
            // 8: 10850.50  (valorAtualBruto)
            // 9: 2026-03-01
            // 10: 60       (diasCorridos)
            // 11: 42       (diasEfetivosCalculo)
            // 12: 1        (quantidadeOperacoes)
            // 13: COMERCIAL_30_360
            // 14: PADRAO
            // 15: 1        (tipoRendimento: mensal)
            // 16: 360      (tempoFinalVencimento)
            // 17: 30       (tempoMinimoPermanencia)
            // 18: 0        (tipoResgate)
            // 19: 1        (iof)
            // 20: 1        (tipoImpostoRenda)
            // 21: 0.00     (valorResgate)
            // 22: 10720.00 (valorAtualLiquido)

            String tsv23Colunas = "NATIONAL-ECONOMIC\t3\n"
                    + "C\t52998224725\tInvestidor Tradicional\t11988887777\t4321\t987654\n"
                    + "I\t52998224725\tAPP-LEGACY-01\tFundo Renda Fixa Antigo\t10000.00\t0.0150\t2026-01-01\t10500.00\t10850.50\t2026-03-01\t60\t42\t1\tCOMERCIAL_30_360\tPADRAO\t1\t360\t30\t0\t1\t1\t0.00\t10720.00\n"
                    + "O\t52998224725\tAPP-LEGACY-01\t2026-01-01\t10000.00\tAporte v3 Legado\tDEPOSITO\n";

            Path tempLegado = Files.createTempFile("teste_legado_23cols_", ".tsv");
            try {
                Files.writeString(tempLegado, tsv23Colunas, StandardCharsets.UTF_8);

                // Ler sem exceção
                Cliente clienteLegado = Cliente.arquivoLerDados(tempLegado);
                afirmarNaoNulo(clienteLegado, "cliente legado deve ser instanciado");
                afirmarIgual(1, clienteLegado.getQuantidadeAplicacoes(), "deve possuir 1 aplicação");

                AplicacaoFinanceira af = clienteLegado.requererAplicacao("APP-LEGACY-01").getAplicacao();
                afirmarIgual("Fundo Renda Fixa Antigo", af.getNomeAplicacao(), "nome legado");
                afirmarIgual(new BigDecimal("10500.00"), af.getSaldo(), "saldo legado");
                afirmarIgual(new BigDecimal("10850.50"), af.getValorAtualBruto(), "valorAtualBruto legado");
                afirmarIgual(new BigDecimal("10720.00"), af.getValorAtualLiquido(), "valorAtualLiquido legado");
                afirmarVerdadeiro(af.getValorAtualBruto().compareTo(af.getValorAtualLiquido()) >= 0, "Bruto >= Liquido no legado");

                // Agora re-salvar: o arquivo deve ser promovido para 26 colunas de forma transparente
                Path tempPromovido = Files.createTempFile("teste_promovido_26cols_", ".tsv");
                try {
                    clienteLegado.arquivoSalvarDados(tempPromovido);
                    List<String> linhas = Files.readAllLines(tempPromovido, StandardCharsets.UTF_8);

                    // Localizar linha 'I'
                    String linhaI = linhas.stream().filter(l -> l.startsWith("I\t")).findFirst().orElseThrow();
                    String[] colsI = linhaI.split("\t", -1);
                    afirmarIgual(26, colsI.length, "linha regravada deve possuir exatamente 26 colunas");

                    // Ler novamente o promovido
                    Cliente clientePromovido = Cliente.arquivoLerDados(tempPromovido);
                    AplicacaoFinanceira afProm = clientePromovido.requererAplicacao("APP-LEGACY-01").getAplicacao();
                    afirmarIgual(af.getValorAtualBruto(), afProm.getValorAtualBruto(), "bruto preservado após promoção");
                    afirmarIgual(af.getValorAtualLiquido(), afProm.getValorAtualLiquido(), "líquido preservado após promoção");
                    afirmarIgual(af.getSaldo(), afProm.getSaldo(), "saldo preservado após promoção");

                } finally {
                    Files.deleteIfExists(tempPromovido);
                }

            } finally {
                Files.deleteIfExists(tempLegado);
            }
            System.out.println("  -> [OK] Retrocompatibilidade comprovada: arquivos v3 de 23 colunas são lidos sem erro e promovidos com sucesso.");
        } catch (IOException e) {
            throw new AssertionError("Falha no teste de retrocompatibilidade: " + e.getMessage(), e);
        }
    }

    // =========================================================================
    // 7. Preservação Bit a Bit de Todos os 19 Atributos em 26 Colunas
    // =========================================================================

    public static void testarPreservacaoBitABit19AtributosFormato26Colunas() {
        System.out.println("[TESTE] Validando paridade bit a bit exata dos 19 atributos no formato v3 de 26 colunas...");
        try {
            Cliente original = new Cliente(
                    new DADOSPESSOAIS("Machado de Assis", "52998224725", "21988889999"),
                    7890, 112233L
            );

            LocalDate dataBase = LocalDate.of(2026, 3, 15);

            AplicacaoFinanceira af = new AplicacaoFinanceira(
                    "CDB Multi-Atributos Rigoroso", "APP-19-ATTR",
                    new BigDecimal("1234567.89"), new BigDecimal("0.0250"),
                    2, 720, 60, 1, 1, 1, dataBase
            );

            // Ajustar valores específicos não-triviais para cada atributo
            af.setSaldo(new BigDecimal("1250000.00"));
            af.setValorResgate(new BigDecimal("15000.00"));
            af.setDiasCorridos(120);
            af.setDiasEfetivosCalculo(84);
            af.setDataUltimaAtualizacao(dataBase.plusDays(120));
            af.setRendimentoAtualBruto(new BigDecimal("45678.90"));
            af.setRendimentoAtualLiquido(new BigDecimal("37685.10"));
            af.setImpostoAtualDevido(new BigDecimal("7993.80"));
            af.setValorAtualBruto(new BigDecimal("1280246.79"));
            af.setValorAtualLiquido(new BigDecimal("1272252.99"));

            MultiplasOperacoesFinanceiras mof = new MultiplasOperacoesFinanceiras(af);
            mof.adicionarOperacao(new OperacaoFinanceira(dataBase, new BigDecimal("1234567.89"), "Depósito Inicial", TipoOperacao.DEPOSITO));
            mof.adicionarOperacao(new OperacaoFinanceira(dataBase.plusDays(60), new BigDecimal("15000.00"), "Resgate Parcial", TipoOperacao.RETIRADA));
            original.adicionarAplicacao(mof);

            Path temp = Files.createTempFile("teste_19_atributos_", ".tsv");
            try {
                original.arquivoSalvarDados(temp);

                // Conferir colunas da linha I no disco
                List<String> linhas = Files.readAllLines(temp, StandardCharsets.UTF_8);
                String linhaI = linhas.stream().filter(l -> l.startsWith("I\t")).findFirst().orElseThrow();
                String[] colunas = linhaI.split("\t", -1);
                afirmarIgual(26, colunas.length, "deve conter exatamente 26 colunas");

                Cliente restaurado = Cliente.arquivoLerDados(temp);
                AplicacaoFinanceira afRest = restaurado.requererAplicacao("APP-19-ATTR").getAplicacao();

                // 1. nomeAplicacao
                afirmarIgual(af.getNomeAplicacao(), afRest.getNomeAplicacao(), "1. nomeAplicacao");
                // 2. numeroAplicacao
                afirmarIgual(af.getNumeroAplicacao(), afRest.getNumeroAplicacao(), "2. numeroAplicacao");
                // 3. valorAplicado
                afirmarIgual(af.getValorAplicado(), afRest.getValorAplicado(), "3. valorAplicado");
                // 4. rendimentoAtualBruto
                afirmarIgual(af.getRendimentoAtualBruto(), afRest.getRendimentoAtualBruto(), "4. rendimentoAtualBruto");
                // 5. rendimentoAtualLiquido
                afirmarIgual(af.getRendimentoAtualLiquido(), afRest.getRendimentoAtualLiquido(), "5. rendimentoAtualLiquido");
                // 6. valorResgate
                afirmarIgual(af.getValorResgate(), afRest.getValorResgate(), "6. valorResgate");
                // 7. valorAtualBruto
                afirmarIgual(af.getValorAtualBruto(), afRest.getValorAtualBruto(), "7. valorAtualBruto");
                // 8. valorAtualLiquido
                afirmarIgual(af.getValorAtualLiquido(), afRest.getValorAtualLiquido(), "8. valorAtualLiquido");
                // 9. taxaMensal
                afirmarIgual(af.getTaxaMensal(), afRest.getTaxaMensal(), "9. taxaMensal");
                // 10. taxaDiaria
                afirmarIgual(af.getTaxaDiaria(), afRest.getTaxaDiaria(), "10. taxaDiaria");
                // 11. tipoRendimento
                afirmarIgual(af.getTipoRendimento(), afRest.getTipoRendimento(), "11. tipoRendimento");
                // 12. tempoFinalVencimento
                afirmarIgual(af.getTempoFinalVencimento(), afRest.getTempoFinalVencimento(), "12. tempoFinalVencimento");
                // 13. tempoMinimoPermanencia
                afirmarIgual(af.getTempoMinimoPermanencia(), afRest.getTempoMinimoPermanencia(), "13. tempoMinimoPermanencia");
                // 14. tipoResgate
                afirmarIgual(af.getTipoResgate(), afRest.getTipoResgate(), "14. tipoResgate");
                // 15. iof
                afirmarIgual(af.getIof(), afRest.getIof(), "15. iof");
                // 16. tipoImpostoRenda
                afirmarIgual(af.getTipoImpostoRenda(), afRest.getTipoImpostoRenda(), "16. tipoImpostoRenda");
                // 17. diasCorridos
                afirmarIgual(af.getDiasCorridos(), afRest.getDiasCorridos(), "17. diasCorridos");
                // 18. diasEfetivosCalculo
                afirmarIgual(af.getDiasEfetivosCalculo(), afRest.getDiasEfetivosCalculo(), "18. diasEfetivosCalculo");
                // 19. impostoAtualDevido
                afirmarIgual(af.getImpostoAtualDevido(), afRest.getImpostoAtualDevido(), "19. impostoAtualDevido");

                // Outros campos operacionais
                afirmarIgual(af.getSaldo(), afRest.getSaldo(), "saldo base");
                afirmarIgual(af.getDataAplicacao(), afRest.getDataAplicacao(), "data de aplicação");
                afirmarIgual(af.getDataUltimaAtualizacao(), afRest.getDataUltimaAtualizacao(), "data de última atualização");
                afirmarIgual(af.getConvencaoContagemDias(), afRest.getConvencaoContagemDias(), "convenção");
                afirmarIgual(af.getRegimeTributario(), afRest.getRegimeTributario(), "regime tributário");

            } finally {
                Files.deleteIfExists(temp);
            }
            System.out.println("  -> [OK] Todos os 19 atributos verificados e comprovados com paridade matemática bit a bit exata.");
        } catch (IOException e) {
            throw new AssertionError("Falha no teste de 19 atributos: " + e.getMessage(), e);
        }
    }

    // =========================================================================
    // 8. Resiliência de Escape e Caracteres Especiais
    // =========================================================================

    public static void testarResilienciaCaracteresEspeciaisEscape() {
        System.out.println("[TESTE] Validando escape/desescape de tabs, quebras de linha e barras invertidas...");
        try {
            Cliente original = new Cliente(
                    new DADOSPESSOAIS("Empresa & Filhos Ltda\\Matriz\tSetor", "52998224725", "11988887777"),
                    999, 8888L
            );

            LocalDate dataBase = LocalDate.of(2026, 1, 1);
            AplicacaoFinanceira af = new AplicacaoFinanceira(
                    "CDB\tCom\tTabs\ne\r\nQuebras\\Invertidas", "APP-TAB-01",
                    new BigDecimal("1000.00"), new BigDecimal("0.01"), dataBase
            );
            MultiplasOperacoesFinanceiras mof = new MultiplasOperacoesFinanceiras(af);
            mof.adicionarOperacao(new OperacaoFinanceira(
                    dataBase,
                    new BigDecimal("1000.00"),
                    "Descrição\tcom\ttabs\ne\nlinhas\\barras",
                    TipoOperacao.DEPOSITO
            ));
            original.adicionarAplicacao(mof);

            Path temp = Files.createTempFile("teste_escape_", ".tsv");
            try {
                original.arquivoSalvarDados(temp);
                Cliente rest = Cliente.arquivoLerDados(temp);

                afirmarIgual(original.getDadosPessoais().getNome(), rest.getDadosPessoais().getNome(), "nome com caracteres de escape");
                AplicacaoFinanceira afRest = rest.requererAplicacao("APP-TAB-01").getAplicacao();
                afirmarIgual(af.getNomeAplicacao(), afRest.getNomeAplicacao(), "nome da aplicação com escape");
                afirmarIgual(1, rest.requererAplicacao("APP-TAB-01").getOperacoes().size(), "quantidade de operações com escape");
                afirmarIgual("Descrição\tcom\ttabs\ne\nlinhas\\barras",
                        rest.requererAplicacao("APP-TAB-01").getOperacoes().get(0).getDescricao(),
                        "descrição de operação com escape");

            } finally {
                Files.deleteIfExists(temp);
            }
            System.out.println("  -> [OK] Tratamento de escape/desescape verificado com sucesso sem corrupção de colunas.");
        } catch (IOException e) {
            throw new AssertionError("Falha no teste de escape: " + e.getMessage(), e);
        }
    }

    // =========================================================================
    // 9. Estresse de Escala: 50 Aplicações e 1000 Operações
    // =========================================================================

    public static void testarMultiplasAplicacoesEOperacoesEscala() {
        System.out.println("[TESTE] Executando teste de estresse de escala (50 aplicações e 1000 operações)...");
        try {
            Cliente original = new Cliente(
                    new DADOSPESSOAIS("Mega Investidor Institucional", "52998224725", "11988887777"),
                    5001, 999999L
            );

            LocalDate dataBase = LocalDate.of(2026, 1, 1);
            int totalOperacoesInseridas = 0;

            for (int a = 1; a <= 50; a++) {
                AplicacaoFinanceira af = new AplicacaoFinanceira(
                        "Fundo Indexado " + a, "APP-" + a,
                        new BigDecimal("10000.00"), new BigDecimal("0.01"), dataBase
                );
                MultiplasOperacoesFinanceiras mof = new MultiplasOperacoesFinanceiras(af);
                for (int op = 1; op <= 20; op++) {
                    mof.adicionarOperacao(new OperacaoFinanceira(
                            dataBase.plusDays(op),
                            new BigDecimal("50.00"),
                            "Aporte " + a + "." + op,
                            TipoOperacao.DEPOSITO
                    ));
                    totalOperacoesInseridas++;
                }
                original.adicionarAplicacao(mof);
            }

            afirmarIgual(50, original.getQuantidadeAplicacoes(), "deve conter 50 aplicações");
            afirmarIgual(1000, totalOperacoesInseridas, "deve conter 1000 operações");

            Path temp = Files.createTempFile("teste_escala_50_1000_", ".tsv");
            try {
                original.arquivoSalvarDados(temp);
                Cliente rest = Cliente.arquivoLerDados(temp);

                afirmarIgual(50, rest.getQuantidadeAplicacoes(), "restaurado deve conter 50 aplicações");
                afirmarIgual(original.getSaldoConsolidado(), rest.getSaldoConsolidado(), "saldo consolidado em escala");

                int totalOpsRestauradas = 0;
                for (int a = 1; a <= 50; a++) {
                    MultiplasOperacoesFinanceiras mofRest = rest.requererAplicacao("APP-" + a);
                    totalOpsRestauradas += mofRest.getOperacoes().size();
                    afirmarIgual(20, mofRest.getOperacoes().size(), "App " + a + " deve conter 20 operações");
                }
                afirmarIgual(1000, totalOpsRestauradas, "total de 1000 operações restauradas");

            } finally {
                Files.deleteIfExists(temp);
            }
            System.out.println("  -> [OK] Estresse de escala com 50 aplicações e 1000 operações executado e aprovado com sucesso.");
        } catch (IOException e) {
            throw new AssertionError("Falha no teste de escala: " + e.getMessage(), e);
        }
    }
}

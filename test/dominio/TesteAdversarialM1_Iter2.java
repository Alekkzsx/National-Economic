package test.dominio;

import static test.Afirmacao.*;

import dominio.AplicacaoFinanceira;
import dominio.CalculadoraTributaria;
import dominio.ConvencaoContagemDias;
import dominio.Moeda;
import dominio.RegimeTributario;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Suíte de testes adversariais empíricos de estresse para o Milestone 1 — Iteração 2.
 *
 * <p>Projetada pelo Challenger M1 Iter 2 para submeter as classes centrais
 * {@link AplicacaoFinanceira} e {@link Moeda} a cenários hostis de execução:</p>
 * <ul>
 *   <li>Vetor 1: Subclasses hostis e verificação completa de não-despacho de métodos virtuais em todos os construtores;</li>
 *   <li>Vetor 2: Resgates parciais e totais em todos os 6 regimes de capitalização e verificação da invariante saldo &gt;= 0;</li>
 *   <li>Vetor 3: Atualizações temporais hostis: equivalência entre processamento em lote vs dia a dia, finais de semana, anos bissextos e viradas de ano;</li>
 *   <li>Vetor 4: Bateria agressiva de strings monetárias bizarras (espaços múltiplos, NBSP, truncamento, sinais mistos, milhares e rejeições estritas);</li>
 *   <li>Vetor 5: Testes de escala extrema (R$ 1 bilhão por 1000 dias úteis) e conservação contábil rigorosa.</li>
 * </ul>
 */
public final class TesteAdversarialM1_Iter2 {

    public static void main(String[] args) {
        System.out.println("================================================================================");
        System.out.println(">>> INICIANDO SUÍTE ADVERSARIAL DE ESTRESSE AVANÇADA (CHALLENGER M1 ITERAÇÃO 2)");
        System.out.println("================================================================================");
        long inicio = System.currentTimeMillis();

        int total = 0;
        int falhasDetectadas = 0;

        // Vetor 1: Sobrescrita de métodos em subclasses com verificação de não-despacho virtual durante o construtor
        total++; if (!testarHerancaSeguraContraTodosMetodosVirtuais()) falhasDetectadas++;

        // Vetor 2: Resgates parciais e totais em múltiplos regimes de capitalização e verificação da invariante saldo >= 0
        total++; if (!testarResgatesMultiplosRegimesCapitalizacao()) falhasDetectadas++;
        total++; if (!testarResgateTotalZeraSaldoESemRendimentosFantasmas()) falhasDetectadas++;
        total++; if (!testarResgateAcimaDoSaldoLiquidoRejeitado()) falhasDetectadas++;
        total++; if (!testarBateria50ResgatesParciaisConsecutivos()) falhasDetectadas++;

        // Vetor 3: Atualizações em finais de semana, feriados, viradas de mês/ano e equivalência lote vs dia-a-dia
        total++; if (!testarEquivalenciaAtualizacaoLoteVsDiaADia()) falhasDetectadas++;
        total++; if (!testarViradaAnoBissexto()) falhasDetectadas++;
        total++; if (!testarViradaAnoCivil()) falhasDetectadas++;
        total++; if (!testarIdempotenciaAtualizarSaldo()) falhasDetectadas++;
        total++; if (!testarCessacaoRendimentoAposVencimento()) falhasDetectadas++;

        // Vetor 4: Strings monetárias bizarras (espaços múltiplos, prefixos com NBSP, múltiplos pontos, notações truncadas, sinais)
        total++; if (!testarMoedaEspacosNBSPQuebrasLinha()) falhasDetectadas++;
        total++; if (!testarMoedaNotacoesTruncadasECasosLimites()) falhasDetectadas++;
        total++; if (!testarMoedaSinaisAlgebricosECombinados()) falhasDetectadas++;
        total++; if (!testarMoedaMilharesExtremos()) falhasDetectadas++;
        total++; if (!testarMoedaRejeicoesEstritasEntradasInvalidas()) falhasDetectadas++;

        // Vetor 5: Estresse de escala extrema (R$ 1 bilhão) e conservação contábil
        total++; if (!testarEscalaExtremaUmBilhaoEConservacaoContabil()) falhasDetectadas++;

        long duracao = System.currentTimeMillis() - inicio;
        System.out.println("================================================================================");
        System.out.printf(">>> RESUMO ADVERSARIAL ITER 2: %d baterias | %d falhas | Duração: %d ms%n",
                total, falhasDetectadas, duracao);
        System.out.println("================================================================================");

        if (falhasDetectadas > 0) {
            System.err.println(">>> VEREDITO PRELIMINAR: DETECTADOS " + falhasDetectadas + " DEFEITOS!");
            System.exit(1);
        } else {
            System.out.println(">>> VEREDITO PRELIMINAR: TODOS OS 15 CENÁRIOS HOSTIS APROVADOS (0 DEFEITOS).");
        }
    }

    // =========================================================================
    // Vetor 1: Sobrescrita de Métodos em Subclasses (Herança Hostil)
    // =========================================================================

    public static boolean testarHerancaSeguraContraTodosMetodosVirtuais() {
        System.out.println("[TESTE 1] Subclasse hostil sobrescrevendo métodos virtuais para capturar escape em construtores...");

        class SubclasseHostil extends AplicacaoFinanceira {
            private final List<String> metodosChamadosAntesDeInicializar = new ArrayList<>();
            private final boolean subclassePronta;

            SubclasseHostil(int tipoConstrutor) {
                super(
                    tipoConstrutor == 1 ? "Hostil-1" : "Hostil-Full",
                    tipoConstrutor == 1 ? "H-01" : "H-FULL",
                    new BigDecimal("10000.00"),
                    new BigDecimal("0.03"),
                    tipoConstrutor == 1 ? 0 : 1,
                    tipoConstrutor == 1 ? 0 : 360,
                    tipoConstrutor == 1 ? 0 : 30,
                    tipoConstrutor == 1 ? 0 : 1,
                    tipoConstrutor == 1 ? 1 : 2,
                    tipoConstrutor == 1 ? 1 : 1,
                    LocalDate.of(2026, 1, 1)
                );
                this.subclassePronta = true;
            }

            private void auditar(String nomeMetodo) {
                if (!subclassePronta) {
                    metodosChamadosAntesDeInicializar.add(nomeMetodo);
                }
            }

            @Override public String getNomeAplicacao() { auditar("getNomeAplicacao"); return super.getNomeAplicacao(); }
            @Override public void setNomeAplicacao(String n) { auditar("setNomeAplicacao"); super.setNomeAplicacao(n); }
            @Override public String getNumeroAplicacao() { auditar("getNumeroAplicacao"); return super.getNumeroAplicacao(); }
            @Override public void setNumeroAplicacao(String n) { auditar("setNumeroAplicacao"); super.setNumeroAplicacao(n); }
            @Override public BigDecimal getValorAplicado() { auditar("getValorAplicado"); return super.getValorAplicado(); }
            @Override public void setValorAplicado(BigDecimal v) { auditar("setValorAplicado"); super.setValorAplicado(v); }
            @Override public BigDecimal getTaxaMensal() { auditar("getTaxaMensal"); return super.getTaxaMensal(); }
            @Override public void setTaxaMensal(BigDecimal t) { auditar("setTaxaMensal"); super.setTaxaMensal(t); }
            @Override public BigDecimal getTaxaDiaria() { auditar("getTaxaDiaria"); return super.getTaxaDiaria(); }
            @Override public void setTaxaDiaria(BigDecimal t) { auditar("setTaxaDiaria"); super.setTaxaDiaria(t); }
            @Override public int getTipoRendimento() { auditar("getTipoRendimento"); return super.getTipoRendimento(); }
            @Override public void setTipoRendimento(int r) { auditar("setTipoRendimento"); super.setTipoRendimento(r); }
            @Override public int getTempoFinalVencimento() { auditar("getTempoFinalVencimento"); return super.getTempoFinalVencimento(); }
            @Override public void setTempoFinalVencimento(int v) { auditar("setTempoFinalVencimento"); super.setTempoFinalVencimento(v); }
            @Override public int getTempoMinimoPermanencia() { auditar("getTempoMinimoPermanencia"); return super.getTempoMinimoPermanencia(); }
            @Override public void setTempoMinimoPermanencia(int p) { auditar("setTempoMinimoPermanencia"); super.setTempoMinimoPermanencia(p); }
            @Override public int getTipoResgate() { auditar("getTipoResgate"); return super.getTipoResgate(); }
            @Override public void setTipoResgate(int r) { auditar("setTipoResgate"); super.setTipoResgate(r); }
            @Override public int getIof() { auditar("getIof"); return super.getIof(); }
            @Override public void setIof(int i) { auditar("setIof"); super.setIof(i); }
            @Override public int getTipoImpostoRenda() { auditar("getTipoImpostoRenda"); return super.getTipoImpostoRenda(); }
            @Override public void setTipoImpostoRenda(int ir) { auditar("setTipoImpostoRenda"); super.setTipoImpostoRenda(ir); }
            @Override public int getDiasCorridos() { auditar("getDiasCorridos"); return super.getDiasCorridos(); }
            @Override public void setDiasCorridos(int d) { auditar("setDiasCorridos"); super.setDiasCorridos(d); }
            @Override public int getDiasEfetivosCalculo() { auditar("getDiasEfetivosCalculo"); return super.getDiasEfetivosCalculo(); }
            @Override public void setDiasEfetivosCalculo(int d) { auditar("setDiasEfetivosCalculo"); super.setDiasEfetivosCalculo(d); }
            @Override public BigDecimal getImpostoAtualDevido() { auditar("getImpostoAtualDevido"); return super.getImpostoAtualDevido(); }
            @Override public void setImpostoAtualDevido(BigDecimal imp) { auditar("setImpostoAtualDevido"); super.setImpostoAtualDevido(imp); }
            @Override public BigDecimal getValorIof() { auditar("getValorIof"); return super.getValorIof(); }
            @Override public BigDecimal getValorIr() { auditar("getValorIr"); return super.getValorIr(); }
            @Override public BigDecimal getSaldo() { auditar("getSaldo"); return super.getSaldo(); }
            @Override public void setSaldo(BigDecimal s) { auditar("setSaldo"); super.setSaldo(s); }
            @Override public BigDecimal getValorAtualBruto() { auditar("getValorAtualBruto"); return super.getValorAtualBruto(); }
            @Override public void setValorAtualBruto(BigDecimal b) { auditar("setValorAtualBruto"); super.setValorAtualBruto(b); }
            @Override public BigDecimal getValorAtualLiquido() { auditar("getValorAtualLiquido"); return super.getValorAtualLiquido(); }
            @Override public void setValorAtualLiquido(BigDecimal l) { auditar("setValorAtualLiquido"); super.setValorAtualLiquido(l); }
            @Override public BigDecimal getRendimentoAtualBruto() { auditar("getRendimentoAtualBruto"); return super.getRendimentoAtualBruto(); }
            @Override public void setRendimentoAtualBruto(BigDecimal r) { auditar("setRendimentoAtualBruto"); super.setRendimentoAtualBruto(r); }
            @Override public BigDecimal getRendimentoAtualLiquido() { auditar("getRendimentoAtualLiquido"); return super.getRendimentoAtualLiquido(); }
            @Override public void setRendimentoAtualLiquido(BigDecimal r) { auditar("setRendimentoAtualLiquido"); super.setRendimentoAtualLiquido(r); }
            @Override public BigDecimal getValorResgate() { auditar("getValorResgate"); return super.getValorResgate(); }
            @Override public void setValorResgate(BigDecimal r) { auditar("setValorResgate"); super.setValorResgate(r); }
            @Override public void atualizarSaldo() { auditar("atualizarSaldo"); super.atualizarSaldo(); }
            @Override public void atualizarSaldo(LocalDate d) { auditar("atualizarSaldo(LocalDate)"); super.atualizarSaldo(d); }
            @Override public void incrementarDia() { auditar("incrementarDia"); super.incrementarDia(); }
            @Override public void incrementarDiaAplicacao() { auditar("incrementarDiaAplicacao"); super.incrementarDiaAplicacao(); }
            @Override public void resgatar(BigDecimal v) { auditar("resgatar"); super.resgatar(v); }
            @Override public boolean podeResgatar() { auditar("podeResgatar"); return super.podeResgatar(); }
            @Override public boolean podeResgatar(LocalDate d) { auditar("podeResgatar(LocalDate)"); return super.podeResgatar(d); }
            @Override public void setCadastro(String n, String num, BigDecimal v, BigDecimal t, int r, int venc, int perm, int resg, int iof, int ir) {
                auditar("setCadastro(10)");
                super.setCadastro(n, num, v, t, r, venc, perm, resg, iof, ir);
            }
        }

        SubclasseHostil instancia1 = new SubclasseHostil(1);
        SubclasseHostil instanciaFull = new SubclasseHostil(2);

        if (!instancia1.metodosChamadosAntesDeInicializar.isEmpty()) {
            System.err.println("  -> [FALHA]: Construtor chamou métodos virtuais da subclasse antes de inicializada: "
                    + instancia1.metodosChamadosAntesDeInicializar);
            return false;
        }
        if (!instanciaFull.metodosChamadosAntesDeInicializar.isEmpty()) {
            System.err.println("  -> [FALHA]: Construtor completo chamou métodos virtuais da subclasse: "
                    + instanciaFull.metodosChamadosAntesDeInicializar);
            return false;
        }

        System.out.println("  -> [OK] 0 métodos virtuais despachados durante os construtores da superclasse.");
        return true;
    }

    // =========================================================================
    // Vetor 2: Resgates Parciais e Totais em Múltiplos Regimes de Capitalização
    // =========================================================================

    public static boolean testarResgatesMultiplosRegimesCapitalizacao() {
        System.out.println("[TESTE 2] Resgates parciais nos regimes 0 (Diário), 1 (Mensal), 2 (Trimestral), 3 (Semestral), 4 (Anual), 5 (No Prazo)...");

        int[] regimes = {0, 1, 2, 3, 4, 5};
        LocalDate inicio = LocalDate.of(2026, 1, 1); // Quinta-feira

        for (int regime : regimes) {
            AplicacaoFinanceira terminal = new AplicacaoFinanceira(
                    "CDB Regime " + regime, "REG-" + regime,
                    new BigDecimal("10000.00"), new BigDecimal("0.03"), // 3% a.m.
                    regime, 360, 0, 0, 0, 0, inicio // Isento de IOF/IR para isolar aritmética
            );

            // Avança 20 dias úteis (até 29 de janeiro)
            LocalDate dia29 = LocalDate.of(2026, 1, 29);
            terminal.atualizarSaldo(dia29);

            BigDecimal brutoAntes = terminal.getValorAtualBruto();
            BigDecimal liquidoAntes = terminal.getValorAtualLiquido();
            afirmarVerdadeiro(brutoAntes.compareTo(new BigDecimal("10000.00")) > 0, "Bruto deve ter rendido");

            // Resgate 1: Parcial menor que o aporte inicial (R$ 4.000,00)
            terminal.resgatar(new BigDecimal("4000.00"));
            afirmarVerdadeiro(terminal.getSaldo().signum() >= 0, "Saldo base deve ser >= 0 após resgate parcial 1");
            afirmarVerdadeiro(terminal.getValorAtualBruto().signum() >= 0, "Bruto deve ser >= 0 após resgate parcial 1");
            afirmarIgual(new BigDecimal("4000.00"), terminal.getValorResgate(), "Valor resgate deve ser 4000");

            // Resgate 2: Parcial que consome o saldo base restante de 6000 e entra nos rendimentos
            // Exemplo: se bruto era 10200, tirou 4000 -> sobram 6200. Resgata mais 6100.
            BigDecimal resgate2 = terminal.getValorAtualLiquido().subtract(new BigDecimal("50.00"));
            terminal.resgatar(resgate2);

            BigDecimal saldoApos2 = terminal.getSaldo();
            BigDecimal brutoApos2 = terminal.getValorAtualBruto();
            if (saldoApos2.signum() < 0) {
                System.err.printf("  -> [FALHA]: No regime %d, saldo ficou negativo após resgate 2: %s%n", regime, saldoApos2);
                return false;
            }
            afirmarVerdadeiro(brutoApos2.compareTo(BigDecimal.ZERO) >= 0, "Bruto restante deve ser não-negativo");

            // Avança para o dia útil seguinte (30 de janeiro)
            terminal.atualizarSaldo(LocalDate.of(2026, 1, 30));
            BigDecimal brutoDiaSeguinte = terminal.getValorAtualBruto();

            // O patrimônio NÃO pode decrescer
            if (brutoDiaSeguinte.compareTo(brutoApos2) < 0) {
                System.err.printf("  -> [FALHA]: No regime %d, patrimônio caiu no dia útil seguinte de %s para %s%n",
                        regime, brutoApos2, brutoDiaSeguinte);
                return false;
            }
        }

        System.out.println("  -> [OK] Resgates parciais preservam invariante saldo >= 0 em todos os regimes.");
        return true;
    }

    public static boolean testarResgateTotalZeraSaldoESemRendimentosFantasmas() {
        System.out.println("[TESTE 3] Resgate total (100% do líquido): verificação de saldo zerado e cessação de rendimentos...");

        for (int regime = 0; regime <= 5; regime++) {
            LocalDate inicio = LocalDate.of(2026, 1, 5);
            AplicacaoFinanceira terminal = new AplicacaoFinanceira(
                    "Total Regime " + regime, "TOT-" + regime,
                    new BigDecimal("5000.00"), new BigDecimal("0.02"),
                    regime, 0, 0, 0, 1, 1, inicio // Com IOF e IR normais
            );

            // Atualiza por 25 dias corridos
            terminal.atualizarSaldo(inicio.plusDays(25));
            BigDecimal saldoLiquidoTotal = terminal.getValorAtualLiquido();
            afirmarVerdadeiro(saldoLiquidoTotal.signum() > 0, "Saldo líquido deve ser positivo");

            // Efetua resgate total de 100% do saldo líquido disponível
            terminal.resgatar(saldoLiquidoTotal);

            afirmarIgual(BigDecimal.ZERO, terminal.getSaldo(), "Saldo base deve ser EXATAMENTE 0 após resgate total");
            afirmarIgual(BigDecimal.ZERO, terminal.getValorAtualLiquido(), "Valor líquido deve ser 0 após resgate total");

            // Avança mais 60 dias corridos
            terminal.atualizarSaldo(inicio.plusDays(85));

            if (terminal.getValorAtualLiquido().signum() != 0 || terminal.getSaldo().signum() != 0) {
                System.err.printf("  -> [FALHA]: Regime %d gerou rendimento fantasma após resgate total: saldo=%s, liquido=%s%n",
                        regime, terminal.getSaldo(), terminal.getValorAtualLiquido());
                return false;
            }
        }

        System.out.println("  -> [OK] Resgate total zera saldo perfeitamente sem gerar rendimentos fantasmas.");
        return true;
    }

    public static boolean testarResgateAcimaDoSaldoLiquidoRejeitado() {
        System.out.println("[TESTE 4] Tentativas de resgate inválidas (zero, negativo, e acima do saldo líquido por 1 centavo)...");

        AplicacaoFinanceira terminal = new AplicacaoFinanceira(
                "Teste Limite", "LIM-01",
                new BigDecimal("1000.00"), new BigDecimal("0.01"),
                LocalDate.of(2026, 1, 1)
        );
        terminal.atualizarSaldo(LocalDate.of(2026, 1, 10));

        BigDecimal liquido = terminal.getValorAtualLiquido();
        BigDecimal excesso = liquido.add(new BigDecimal("0.01"));

        afirmarRejeicao(() -> terminal.resgatar(BigDecimal.ZERO), IllegalArgumentException.class, "Resgate zero");
        afirmarRejeicao(() -> terminal.resgatar(new BigDecimal("-10.00")), IllegalArgumentException.class, "Resgate negativo");
        afirmarRejeicao(() -> terminal.resgatar(null), IllegalArgumentException.class, "Resgate nulo");
        afirmarRejeicao(() -> terminal.resgatar(excesso), IllegalArgumentException.class, "Resgate com excesso de 1 centavo");

        System.out.println("  -> [OK] Rejeições estritas de resgate com valores inválidos confirmadas.");
        return true;
    }

    public static boolean testarBateria50ResgatesParciaisConsecutivos() {
        System.out.println("[TESTE 5] Bateria de 50 resgates parciais consecutivos com avanço diário do tempo...");

        LocalDate data = LocalDate.of(2026, 1, 5);
        AplicacaoFinanceira terminal = new AplicacaoFinanceira(
                "MicroResgates", "MIC-01",
                new BigDecimal("50000.00"), new BigDecimal("0.05"), // R$ 50k a 5% a.m.
                0, 0, 0, 0, 1, 1, data
        );

        BigDecimal totalResgatadoEsperado = BigDecimal.ZERO;

        for (int i = 1; i <= 50; i++) {
            data = data.plusDays(1);
            terminal.atualizarSaldo(data);

            BigDecimal valorResgate = new BigDecimal("100.00");
            afirmarVerdadeiro(terminal.getValorAtualLiquido().compareTo(valorResgate) > 0, "Saldo líquido deve suportar resgate de 100");

            terminal.resgatar(valorResgate);
            totalResgatadoEsperado = totalResgatadoEsperado.add(valorResgate);

            afirmarVerdadeiro(terminal.getSaldo().signum() >= 0, "Saldo base nunca deve ser negativo (iteração " + i + ")");
            afirmarVerdadeiro(terminal.getValorAtualLiquido().signum() >= 0, "Líquido nunca deve ser negativo");
            afirmarIgual(totalResgatadoEsperado, terminal.getValorResgate(), "Total resgatado deve acumular com exatidão");
        }

        System.out.println("  -> [OK] 50 resgates consecutivos executados mantendo todas as invariantes financeiras.");
        return true;
    }

    // =========================================================================
    // Vetor 3: Atualizações Temporais Hostis
    // =========================================================================

    public static boolean testarEquivalenciaAtualizacaoLoteVsDiaADia() {
        System.out.println("[TESTE 6] Equivalência matemática estrita: processamento em lote vs dia a dia por 45 dias corridos...");

        LocalDate inicio = LocalDate.of(2026, 3, 2); // Segunda-feira
        LocalDate fim = inicio.plusDays(45);

        // Aplicação 1: Atualizada em lote direto do início ao fim
        AplicacaoFinanceira appLote = new AplicacaoFinanceira(
                "Lote", "LOT-01",
                new BigDecimal("25000.00"), new BigDecimal("0.025"),
                1, 180, 0, 0, 1, 1, inicio
        );
        appLote.atualizarSaldo(fim);

        // Aplicação 2: Atualizada iterativamente dia por dia
        AplicacaoFinanceira appDia = new AplicacaoFinanceira(
                "Lote", "LOT-01",
                new BigDecimal("25000.00"), new BigDecimal("0.025"),
                1, 180, 0, 0, 1, 1, inicio
        );
        LocalDate cursor = inicio;
        while (!cursor.isAfter(fim)) {
            appDia.atualizarSaldo(cursor);
            cursor = cursor.plusDays(1);
        }

        // Validação de paridade estrita
        afirmarIgual(appDia.getDiasCorridos(), appLote.getDiasCorridos(), "Dias corridos devem ser idênticos");
        afirmarIgual(appDia.getDiasEfetivosCalculo(), appLote.getDiasEfetivosCalculo(), "Dias efetivos devem ser idênticos");
        afirmarIgual(appDia.getSaldo(), appLote.getSaldo(), "Saldo creditado deve ser idêntico");
        afirmarIgual(appDia.getRendimentoAtualBruto(), appLote.getRendimentoAtualBruto(), "Rendimento bruto idêntico");
        afirmarIgual(appDia.getImpostoAtualDevido(), appLote.getImpostoAtualDevido(), "Imposto devido idêntico");
        afirmarIgual(appDia.getValorAtualLiquido(), appLote.getValorAtualLiquido(), "Valor atual líquido idêntico");

        System.out.println("  -> [OK] Paridade estrita comprovada entre atualização em lote e dia a dia (45 dias).");
        return true;
    }

    public static boolean testarViradaAnoBissexto() {
        System.out.println("[TESTE 7] Virada temporal em ano bissexto (fevereiro de 2024 tem 29 dias)...");

        // 2024-02-27 (terça-feira) até 2024-03-02 (sábado)
        // Dias: 27 (ter, útil), 28 (qua, útil), 29 (qui, útil bissexto!), 01 (sex, útil), 02 (sáb, não útil)
        // Total civis: 5 dias (27, 28, 29, 1, 2)
        // Total úteis: 4 dias
        LocalDate inicio = LocalDate.of(2024, 2, 27);
        LocalDate fim = LocalDate.of(2024, 3, 2);

        AplicacaoFinanceira terminal = new AplicacaoFinanceira(
                "Bissexto", "BIS-2024",
                new BigDecimal("10000.00"), new BigDecimal("0.03"), inicio
        );
        terminal.atualizarSaldo(fim);

        afirmarIgual(5, terminal.getDiasCorridos(), "Fevereiro bissexto até 2 de março = 5 dias civis");
        afirmarIgual(4, terminal.getDiasEfetivosCalculo(), "4 dias úteis decorridos");

        System.out.println("  -> [OK] Calendário bissexto e contagem de dias civis/úteis validados com precisão.");
        return true;
    }

    public static boolean testarViradaAnoCivil() {
        System.out.println("[TESTE 8] Virada de ano civil (2025 para 2026)...");

        // 2025-12-30 (terça) até 2026-01-05 (segunda)
        // 30/12 (ter, útil), 31/12 (qua, útil), 01/01 (qui, útil contábil no modelo), 02/01 (sex, útil), 03/01 (sáb), 04/01 (dom), 05/01 (seg, útil)
        // Civis: 7 dias corridos.
        // Úteis: 5 dias úteis (seg-sex).
        LocalDate inicio = LocalDate.of(2025, 12, 30);
        LocalDate fim = LocalDate.of(2026, 1, 5);

        AplicacaoFinanceira terminal = new AplicacaoFinanceira(
                "Virada Ano", "ANO-01",
                new BigDecimal("10000.00"), new BigDecimal("0.03"), inicio
        );
        terminal.atualizarSaldo(fim);

        afirmarIgual(7, terminal.getDiasCorridos(), "7 dias corridos na virada do ano");
        afirmarIgual(5, terminal.getDiasEfetivosCalculo(), "5 dias úteis (seg-sex)");

        System.out.println("  -> [OK] Virada de ano civil processada sem descontinuidade temporal.");
        return true;
    }

    public static boolean testarIdempotenciaAtualizarSaldo() {
        System.out.println("[TESTE 9] Idempotência de atualizarSaldo: chamadas redundantes e datas retroativas...");

        LocalDate inicio = LocalDate.of(2026, 5, 4); // Segunda-feira
        LocalDate dia1 = LocalDate.of(2026, 5, 8);  // Sexta-feira

        AplicacaoFinanceira terminal = new AplicacaoFinanceira(
                "Idemp", "IDEM-01",
                new BigDecimal("10000.00"), new BigDecimal("0.03"), inicio
        );

        terminal.atualizarSaldo(dia1);
        BigDecimal saldo1 = terminal.getValorAtualBruto();
        int diasCorridos1 = terminal.getDiasCorridos();
        int diasEfetivos1 = terminal.getDiasEfetivosCalculo();

        // Invoca 10 vezes com a mesma data dia1
        for (int i = 0; i < 10; i++) {
            terminal.atualizarSaldo(dia1);
        }

        afirmarIgual(saldo1, terminal.getValorAtualBruto(), "Saldo bruto deve permanecer idêntico");
        afirmarIgual(diasCorridos1, terminal.getDiasCorridos(), "Dias corridos inalterados");
        afirmarIgual(diasEfetivos1, terminal.getDiasEfetivosCalculo(), "Dias efetivos inalterados");

        // Invoca com data retroativa (dia 6 de maio)
        terminal.atualizarSaldo(LocalDate.of(2026, 5, 6));
        afirmarIgual(saldo1, terminal.getValorAtualBruto(), "Data retroativa não deve reverter nem alterar saldo");

        System.out.println("  -> [OK] Idempotência comprovada.");
        return true;
    }

    public static boolean testarCessacaoRendimentoAposVencimento() {
        System.out.println("[TESTE 10] Cessação de rendimento ao atingir tempoFinalVencimento...");

        LocalDate inicio = LocalDate.of(2026, 6, 1);
        int vencimentoDias = 10;

        AplicacaoFinanceira terminal = new AplicacaoFinanceira(
                "CDB Prazo Curto", "VENC-10",
                new BigDecimal("10000.00"), new BigDecimal("0.03"),
                0, vencimentoDias, 0, 0, 0, 0, inicio
        );

        // Atualiza até o vencimento exato (dia 11 de junho = 11 dias civis, diasCorridos=11 > 10)
        LocalDate diaVencimento = inicio.plusDays(vencimentoDias);
        terminal.atualizarSaldo(diaVencimento);
        BigDecimal brutoNoVencimento = terminal.getValorAtualBruto();

        // Avança mais 30 dias corridos além do vencimento
        terminal.atualizarSaldo(inicio.plusDays(40));
        BigDecimal brutoApos40Dias = terminal.getValorAtualBruto();

        afirmarIgual(brutoNoVencimento, brutoApos40Dias, "Rendimento não deve evoluir após a data de vencimento");
        afirmarIgual(41, terminal.getDiasCorridos(), "Dias civis continuam sendo contados");

        System.out.println("  -> [OK] Rendimento cessa precisamente na data de vencimento estipulada.");
        return true;
    }

    // =========================================================================
    // Vetor 4: Strings Monetárias Hostis em Moeda.de(String)
    // =========================================================================

    public static boolean testarMoedaEspacosNBSPQuebrasLinha() {
        System.out.println("[TESTE 11] Moeda.de com espaços múltiplos, NBSP (\\u00A0), Narrow NBSP (\\u202F) e quebras de linha...");

        afirmarIgual(new BigDecimal("100.50"), Moeda.de("   100.50   "), "espaços comuns");
        afirmarIgual(new BigDecimal("100.50"), Moeda.de("  \t  100,50  \n  "), "tabs e newlines nas bordas");
        afirmarIgual(new BigDecimal("50.25"), Moeda.de("R$   50.25"), "R$ com múltiplos espaços");
        afirmarIgual(new BigDecimal("99.90"), Moeda.de("r$      99,90"), "r$ minúsculo com múltiplos espaços");
        afirmarIgual(new BigDecimal("1234.56"), Moeda.de("$    1234.56"), "$ com espaços");
        afirmarIgual(new BigDecimal("1234.56"), Moeda.de("R$\u00A01.234,56"), "R$ com NBSP");
        afirmarIgual(new BigDecimal("1500.50"), Moeda.de("\u202F1500.50\u202F"), "Narrow NBSP");
        afirmarIgual(new BigDecimal("999.99"), Moeda.de("R$\u00A0\u202F999,99"), "R$ com combinação de espaços especiais");

        System.out.println("  -> [OK] Moeda.de processa com sucesso espaços múltiplos e caracteres não-quebráveis.");
        return true;
    }

    public static boolean testarMoedaNotacoesTruncadasECasosLimites() {
        System.out.println("[TESTE 12] Moeda.de com notações truncadas (.50, ,50, 0.5, 5., 5,, zeros)...");

        afirmarIgual(new BigDecimal("0.50"), Moeda.de(".50"), ".50 -> 0.50");
        afirmarIgual(new BigDecimal("0.50"), Moeda.de(",50"), ",50 -> 0.50");
        afirmarIgual(new BigDecimal("0.50"), Moeda.de(".5"), ".5 -> 0.50");
        afirmarIgual(new BigDecimal("0.50"), Moeda.de(",5"), ",5 -> 0.50");
        afirmarIgual(new BigDecimal("0.50"), Moeda.de("0.5"), "0.5 -> 0.50");
        afirmarIgual(new BigDecimal("0.50"), Moeda.de("0,5"), "0,5 -> 0.50");
        afirmarIgual(new BigDecimal("0.05"), Moeda.de("0.05"), "0.05 -> 0.05");
        afirmarIgual(new BigDecimal("0.05"), Moeda.de("0,05"), "0,05 -> 0.05");
        afirmarIgual(new BigDecimal("5.00"), Moeda.de("5."), "5. -> 5.00");
        afirmarIgual(new BigDecimal("5.00"), Moeda.de("5,"), "5, -> 5.00");
        afirmarIgual(new BigDecimal("0.00"), Moeda.de("0"), "0 -> 0.00");
        afirmarIgual(new BigDecimal("0.00"), Moeda.de("000"), "000 -> 0.00");
        afirmarIgual(new BigDecimal("0.00"), Moeda.de("0.0"), "0.0 -> 0.00");
        afirmarIgual(new BigDecimal("0.00"), Moeda.de("0,00"), "0,00 -> 0.00");

        System.out.println("  -> [OK] Notações truncadas e limites decimais validados.");
        return true;
    }

    public static boolean testarMoedaSinaisAlgebricosECombinados() {
        System.out.println("[TESTE 13] Moeda.de com sinais algébricos (+ e -) combinados com prefixos de moeda...");

        afirmarIgual(new BigDecimal("100.50"), Moeda.de("+100.50"), "+100.50");
        afirmarIgual(new BigDecimal("100.50"), Moeda.de("+ 100.50"), "+ 100.50");
        afirmarIgual(new BigDecimal("-100.50"), Moeda.de("-100.50"), "-100.50");
        afirmarIgual(new BigDecimal("-100.50"), Moeda.de("- 100.50"), "- 100.50");
        afirmarIgual(new BigDecimal("100.50"), Moeda.de("+R$ 100.50"), "+R$ 100.50");
        afirmarIgual(new BigDecimal("-100.50"), Moeda.de("-R$ 100.50"), "-R$ 100.50");
        afirmarIgual(new BigDecimal("100.50"), Moeda.de("R$ +100.50"), "R$ +100.50");
        afirmarIgual(new BigDecimal("-100.50"), Moeda.de("R$ -100.50"), "R$ -100.50");
        afirmarIgual(new BigDecimal("-1500.50"), Moeda.de("-R$\u00A01.500,50"), "-R$ com NBSP");

        System.out.println("  -> [OK] Sinais algébricos processados corretamente.");
        return true;
    }

    public static boolean testarMoedaMilharesExtremos() {
        System.out.println("[TESTE 14] Moeda.de com múltiplos separadores de milhares...");

        afirmarIgual(new BigDecimal("1000000.50"), Moeda.de("1.000.000,50"), "1.000.000,50 pt-BR");
        afirmarIgual(new BigDecimal("1000000.00"), Moeda.de("1.000.000"), "1.000.000 pt-BR sem decimal");
        afirmarIgual(new BigDecimal("10000000.00"), Moeda.de("10.000.000,00"), "10.000.000,00 pt-BR");
        afirmarIgual(new BigDecimal("1234567890.12"), Moeda.de("1.234.567.890,12"), "1.234.567.890,12 pt-BR");

        afirmarIgual(new BigDecimal("1000000.50"), Moeda.de("1,000,000.50"), "1,000,000.50 intl");
        afirmarIgual(new BigDecimal("1000000.00"), Moeda.de("1,000,000"), "1,000,000 intl sem decimal");
        afirmarIgual(new BigDecimal("10000000.00"), Moeda.de("10,000,000.00"), "10,000,000.00 intl");

        System.out.println("  -> [OK] Milhares nos padrões pt-BR e internacional validados.");
        return true;
    }

    public static boolean testarMoedaRejeicoesEstritasEntradasInvalidas() {
        System.out.println("[TESTE 15] Moeda.de rejeição estrita de strings malformadas...");

        afirmarRejeicao(() -> Moeda.de(null), NullPointerException.class, "null");
        afirmarRejeicao(() -> Moeda.de(""), IllegalArgumentException.class, "vazia");
        afirmarRejeicao(() -> Moeda.de("   "), IllegalArgumentException.class, "apenas espacos");
        afirmarRejeicao(() -> Moeda.de("\t\n"), IllegalArgumentException.class, "apenas tabs/newlines");
        afirmarRejeicao(() -> Moeda.de("abc"), IllegalArgumentException.class, "letras");
        afirmarRejeicao(() -> Moeda.de("R$"), IllegalArgumentException.class, "prefixo R$ isolado");
        afirmarRejeicao(() -> Moeda.de("r$"), IllegalArgumentException.class, "prefixo r$ isolado");
        afirmarRejeicao(() -> Moeda.de("$"), IllegalArgumentException.class, "cifrao $ isolado");
        afirmarRejeicao(() -> Moeda.de("+"), IllegalArgumentException.class, "mais isolado");
        afirmarRejeicao(() -> Moeda.de("-"), IllegalArgumentException.class, "menos isolado");
        afirmarRejeicao(() -> Moeda.de("--100"), IllegalArgumentException.class, "sinal menos duplo");
        afirmarRejeicao(() -> Moeda.de("-+100"), IllegalArgumentException.class, "sinais mistos -+");
        afirmarRejeicao(() -> Moeda.de("."), IllegalArgumentException.class, "apenas ponto");
        afirmarRejeicao(() -> Moeda.de(","), IllegalArgumentException.class, "apenas virgula");
        afirmarRejeicao(() -> Moeda.de("1.000,500.20"), IllegalArgumentException.class, "separadores misturados anomalos");

        System.out.println("  -> [OK] Todas as entradas degeneradas rejeitadas com as exceções esperadas.");
        return true;
    }

    // =========================================================================
    // Vetor 5: Escala Extrema (R$ 1 Bilhão) e Conservação Contábil
    // =========================================================================

    public static boolean testarEscalaExtremaUmBilhaoEConservacaoContabil() {
        System.out.println("[TESTE 16] Escala extrema: Aporte de R$ 1.000.000.000,00 por 1000 dias úteis e invariante contábil...");

        BigDecimal aporte = new BigDecimal("1000000000.00"); // 1 bilhão
        BigDecimal taxaMensal = new BigDecimal("0.01"); // 1% ao mês
        LocalDate inicio = LocalDate.of(2026, 1, 1);

        AplicacaoFinanceira terminal = new AplicacaoFinanceira(
                "Fundo Soberano", "BI-01",
                aporte, taxaMensal,
                0, 0, 0, 0, 1, 1, inicio
        );

        // Avança 1400 dias corridos (aproximadamente 1000 dias úteis)
        terminal.atualizarSaldo(inicio.plusDays(1400));

        BigDecimal bruto = terminal.getValorAtualBruto();
        BigDecimal liquido = terminal.getValorAtualLiquido();
        BigDecimal rendBruto = terminal.getRendimentoAtualBruto();
        BigDecimal imposto = terminal.getImpostoAtualDevido();

        // Equações contábeis fundamentais:
        // 1. valorAtualLiquido == valorAtualBruto - impostoAtualDevido
        afirmarIgual(liquido, bruto.subtract(imposto), "Equação patrimonial: Liquido == Bruto - Imposto");

        // 2. valorAtualBruto == saldoBase + rendimentosPendentes (em regime diário, pendentes=0 -> bruto == saldo)
        afirmarIgual(bruto, terminal.getSaldo(), "Em regime diário bruto == saldo");

        // 3. rendimentoBruto == bruto - aporteInicial
        afirmarIgual(rendBruto, bruto.subtract(aporte), "Rendimento bruto == Bruto - Aporte Inicial");

        // 4. Saldo líquido deve superar R$ 1 bilhão
        afirmarVerdadeiro(liquido.compareTo(aporte) > 0, "Patrimônio líquido final deve ser superior a 1 bilhão");

        System.out.printf("  -> [OK] 1 Bilhão após 1400 dias: Bruto=%s, Imposto=%s, Líquido=%s%n",
                Moeda.formatar(bruto), Moeda.formatar(imposto), Moeda.formatar(liquido));
        return true;
    }
}

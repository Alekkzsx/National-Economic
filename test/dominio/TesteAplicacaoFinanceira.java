package test.dominio;

import static test.Afirmacao.*;

import dominio.AplicacaoFinanceira;
import dominio.CalculadoraTributaria;
import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;

/**
 * Suíte de testes autônomos para a classe {@link AplicacaoFinanceira} (Item 1, Requisito R1.1).
 *
 * <p>Cobre exaustivamente:
 * <ul>
 *   <li>19 atributos obrigatórios e seus respectivos getters e setters;</li>
 *   <li>Sincronização matemática da taxa diária e mensal para mês comercial de 30 dias;</li>
 *   <li>Regras de tributação de IOF (0=Isento, 1=Carência 30d, 2=Irrestrito);</li>
 *   <li>Regras de tributação de Imposto de Renda Regressivo (0=Isento, 1=Tabela Regressiva 22,5%, 20%, 17,5%, 15%);</li>
 *   <li>Métodos operacionais: setCadastro, atualizarSaldo, incrementarDia, incrementarDiaAplicação;</li>
 *   <li>Herança segura, conformidade contábil e cenários do mundo real.</li>
 * </ul>
 */
public final class TesteAplicacaoFinanceira {

    public static void main(String[] args) {
        System.out.println(">>> Executando TesteAplicacaoFinanceira (Item 1 - 19 Atributos, Tributação e Taxas)...");
        long inicio = System.currentTimeMillis();
        int testesExecutados = 0;

        testarDezenoveAtributosEEncapsulamento(); testesExecutados++;
        testarSincronizacaoTaxasMesComercial30Dias(); testesExecutados++;
        testarSincronizacaoTaxaDizimaPeriodica(); testesExecutados++;
        testarIofTipo0Isento(); testesExecutados++;
        testarIofTipo1Carencia30DiasLimites(); testesExecutados++;
        testarIofTipo2Irrestrito(); testesExecutados++;
        testarIrTipo0Isento(); testesExecutados++;
        testarIrTipo1TabelaRegressivaQuatroFaixas(); testesExecutados++;
        testarIrTransicaoExataLimitesFaixas(); testesExecutados++;
        testarDeducaoIofDaBaseDeCalculoDoIr(); testesExecutados++;
        testarMetodosIncrementoDias(); testesExecutados++;
        testarMetodoSetCadastro(); testesExecutados++;
        testarHerancaSegura(); testesExecutados++;
        testarCenarioMundoRealCicloCurto10Dias(); testesExecutados++;
        testarCenarioMundoRealCicloMedio200Dias(); testesExecutados++;
        testarCenarioMundoRealCicloLongo800Dias(); testesExecutados++;
        testarRejeicoesValoresInvalidos(); testesExecutados++;
        testarAtualizarSaldoProgressaoDiaADia(); testesExecutados++;
        testarDataDaAplicacaoComoDiaZero(); testesExecutados++;
        testarAtualizarSaldoSaltoFimDeSemana(); testesExecutados++;
        testarAtualizarSaldoTrintaDiasEViradaDeMes(); testesExecutados++;
        testarAtualizarSaldoResgateParcialERendimentoSubsequente(); testesExecutados++;
        testarAtualizarSaldoLimitesEVencimento(); testesExecutados++;

        long duracao = System.currentTimeMillis() - inicio;
        System.out.printf(">>> TesteAplicacaoFinanceira CONCLUÍDO com SUCESSO: %d métodos verificados (%d ms)%n%n",
                testesExecutados, duracao);
    }

    /**
     * Tier 1: Validação exaustiva dos 19 atributos obrigatórios com getters e setters nominais em PT-BR.
     */
    public static void testarDezenoveAtributosEEncapsulamento() {
        AplicacaoFinanceira terminal = new AplicacaoFinanceira(
                "CDB Prefixado Master",
                "CDB-2026-001",
                new BigDecimal("10000.00"),
                new BigDecimal("0.015") // 1.5% ao mês
        );

        // 1. Nome da Aplicação
        afirmarIgual("CDB Prefixado Master", terminal.getNomeAplicacao(), "1. nomeAplicacao");
        terminal.setNomeAplicacao("CDB Especial 2026");
        afirmarIgual("CDB Especial 2026", terminal.getNomeAplicacao(), "1. setNomeAplicacao");

        // 2. Número da Aplicação
        afirmarIgual("CDB-2026-001", terminal.getNumeroAplicacao(), "2. numeroAplicacao");
        terminal.setNumeroAplicacao("CDB-2026-999");
        afirmarIgual("CDB-2026-999", terminal.getNumeroAplicacao(), "2. setNumeroAplicacao");

        // 3. Valor Aplicado
        afirmarIgual(new BigDecimal("10000.00"), terminal.getValorAplicado(), "3. valorAplicado");
        terminal.setValorAplicado(new BigDecimal("15000.00"));
        afirmarIgual(new BigDecimal("15000.00"), terminal.getValorAplicado(), "3. setValorAplicado");

        // 4. Rendimento Atual Bruto
        terminal.setRendimentoAtualBruto(new BigDecimal("300.00"));
        afirmarIgual(new BigDecimal("300.00"), terminal.getRendimentoAtualBruto(), "4. rendimentoAtualBruto");

        // 5. Rendimento Atual Líquido
        terminal.setRendimentoAtualLiquido(new BigDecimal("232.50"));
        afirmarIgual(new BigDecimal("232.50"), terminal.getRendimentoAtualLiquido(), "5. rendimentoAtualLiquido");

        // 6. Valor Resgate
        terminal.setValorResgate(new BigDecimal("2000.00"));
        afirmarIgual(new BigDecimal("2000.00"), terminal.getValorResgate(), "6. valorResgate");

        // 7. Valor Atual Bruto
        terminal.setValorAtualBruto(new BigDecimal("13300.00"));
        afirmarIgual(new BigDecimal("13300.00"), terminal.getValorAtualBruto(), "7. valorAtualBruto");

        // 8. Valor Atual Líquido
        terminal.setValorAtualLiquido(new BigDecimal("13232.50"));
        afirmarIgual(new BigDecimal("13232.50"), terminal.getValorAtualLiquido(), "8. valorAtualLiquido");

        // 9. Taxa Mensal (%)
        terminal.setTaxaMensal(new BigDecimal("0.030"));
        afirmarIgual(new BigDecimal("0.030"), terminal.getTaxaMensal(), "9. taxaMensal");

        // 10. Taxa Diária (%) sincronizada para mês comercial de 30 dias (0.030 / 30 = 0.001)
        afirmarIgual(new BigDecimal("0.001"), terminal.getTaxaDiaria(), "10. taxaDiaria sincronizada");

        // 11. Tipo de Rendimento (0 a 5)
        terminal.setTipoRendimento(1); // Mensal
        afirmarIgual(1, terminal.getTipoRendimento(), "11. tipoRendimento");

        // 12. Tempo Final de Vencimento (dias)
        terminal.setTempoFinalVencimento(360);
        afirmarIgual(360, terminal.getTempoFinalVencimento(), "12. tempoFinalVencimento");

        // 13. Tempo Mínimo de Permanência (dias)
        terminal.setTempoMinimoPermanencia(30);
        afirmarIgual(30, terminal.getTempoMinimoPermanencia(), "13. tempoMinimoPermanencia");

        // 14. Tipo de Resgate (0 a 4)
        terminal.setTipoResgate(1); // Mensal
        afirmarIgual(1, terminal.getTipoResgate(), "14. tipoResgate");

        // 15. IOF (0, 1 ou 2)
        terminal.setIof(1);
        afirmarIgual(1, terminal.getIof(), "15. iof");

        // 16. Tipo de Imposto de Renda (0 ou 1)
        terminal.setTipoImpostoRenda(1);
        afirmarIgual(1, terminal.getTipoImpostoRenda(), "16. tipoImpostoRenda");

        // 17. Dias Corridos
        terminal.setDiasCorridos(45);
        afirmarIgual(45, terminal.getDiasCorridos(), "17. diasCorridos");

        // 18. Dias Efetivos para Cálculo
        terminal.setDiasEfetivosCalculo(32);
        afirmarIgual(32, terminal.getDiasEfetivosCalculo(), "18. diasEfetivosCalculo");

        // 19. Imposto Atual Devido
        terminal.setImpostoAtualDevido(new BigDecimal("67.50"));
        afirmarIgual(new BigDecimal("67.50"), terminal.getImpostoAtualDevido(), "19. impostoAtualDevido");
    }

    /**
     * Tier 1 & Tier 3: Sincronização linear de taxas para mês comercial de 30 dias.
     * Fórmula: TaxaDiaria = TaxaMensal / 30; TaxaMensal = TaxaDiaria * 30.
     */
    public static void testarSincronizacaoTaxasMesComercial30Dias() {
        AplicacaoFinanceira terminal = new AplicacaoFinanceira(
                "LCI DI 30d", "LCI-01", new BigDecimal("5000.00"), new BigDecimal("0.030")
        );

        // 1. Atribuição de Taxa Mensal atualiza Taxa Diária
        terminal.setTaxaMensal(new BigDecimal("0.030")); // 3% ao mês
        afirmarIgual(new BigDecimal("0.001"), terminal.getTaxaDiaria(), "taxa diária deve ser 0.030 / 30 = 0.001");

        terminal.setTaxaMensal(new BigDecimal("0.015")); // 1.5% ao mês
        afirmarIgual(new BigDecimal("0.0005"), terminal.getTaxaDiaria(), "taxa diária deve ser 0.015 / 30 = 0.0005");

        // 2. Atribuição de Taxa Diária atualiza Taxa Mensal
        terminal.setTaxaDiaria(new BigDecimal("0.0008"));
        afirmarIgual(new BigDecimal("0.024"), terminal.getTaxaMensal(), "taxa mensal deve ser 0.0008 * 30 = 0.024");

        terminal.setTaxaDiaria(new BigDecimal("0.002"));
        afirmarIgual(new BigDecimal("0.060"), terminal.getTaxaMensal(), "taxa mensal deve ser 0.002 * 30 = 0.060");
    }

    /**
     * Tier 2 (Boundary): Divisões com dízimas periódicas na sincronização (ex.: 0.01 / 30)
     * não podem disparar ArithmeticException e devem manter precisão matemática.
     */
    public static void testarSincronizacaoTaxaDizimaPeriodica() {
        AplicacaoFinanceira terminal = new AplicacaoFinanceira(
                "CDB Taxa Fracionada", "CDB-02", new BigDecimal("1000.00"), new BigDecimal("0.01")
        );

        // 1% / 30 = 0.0003333333...
        terminal.setTaxaMensal(new BigDecimal("0.01"));
        BigDecimal diaria = terminal.getTaxaDiaria();
        afirmarNaoNulo(diaria, "taxa diária não pode ser nula");
        afirmarVerdadeiro(diaria.compareTo(BigDecimal.ZERO) > 0, "taxa diária deve ser positiva");

        // Multiplicando de volta por 30 com MathContext.DECIMAL128 deve restaurar 0.01
        BigDecimal restaurada = diaria.multiply(BigDecimal.valueOf(30), MathContext.DECIMAL128);
        afirmarIgual(new BigDecimal("0.01"), restaurada.round(new MathContext(2)),
                "reversão linear de taxa deve manter equivalência");
    }

    /**
     * Tier 1: IOF Tipo 0 (Isento) — alíquota 0.00% em qualquer prazo e rendimento.
     */
    public static void testarIofTipo0Isento() {
        AplicacaoFinanceira terminal = new AplicacaoFinanceira(
                "LCI Isenta", "LCI-02", new BigDecimal("20000.00"), new BigDecimal("0.02")
        );
        terminal.setIof(0); // Isento
        terminal.setRendimentoAtualBruto(new BigDecimal("500.00"));

        terminal.setDiasCorridos(5);
        afirmarIgual(BigDecimal.ZERO, terminal.getValorIof(), "IOF tipo 0 deve ser zero no dia 5");

        terminal.setDiasCorridos(29);
        afirmarIgual(BigDecimal.ZERO, terminal.getValorIof(), "IOF tipo 0 deve ser zero no dia 29");

        terminal.setDiasCorridos(30);
        afirmarIgual(BigDecimal.ZERO, terminal.getValorIof(), "IOF tipo 0 deve ser zero no dia 30");

        terminal.setDiasCorridos(100);
        afirmarIgual(BigDecimal.ZERO, terminal.getValorIof(), "IOF tipo 0 deve ser zero no dia 100");
    }

    /**
     * Tier 2 (Boundary): IOF Tipo 1 (3,5% com carência de 30 dias corridos).
     * Regra: diasCorridos < 30 aplica 3.5%; diasCorridos >= 30 é isento.
     */
    public static void testarIofTipo1Carencia30DiasLimites() {
        AplicacaoFinanceira terminal = new AplicacaoFinanceira(
                "CDB Curto Prazo", "CDB-03", new BigDecimal("10000.00"), new BigDecimal("0.02")
        );
        terminal.setIof(1); // 3,5% com carência de 30 dias
        terminal.setRendimentoAtualBruto(new BigDecimal("1000.00"));

        // Dia 0: incide 3.5% = 35.00
        terminal.setDiasCorridos(0);
        afirmarIgual(new BigDecimal("35.00"), terminal.getValorIof(), "IOF tipo 1 no dia 0 deve ser 35.00");

        // Dia 15: incide 3.5% = 35.00
        terminal.setDiasCorridos(15);
        afirmarIgual(new BigDecimal("35.00"), terminal.getValorIof(), "IOF tipo 1 no dia 15 deve ser 35.00");

        // Dia 29 (último dia antes da carência): incide 3.5% = 35.00
        terminal.setDiasCorridos(29);
        afirmarIgual(new BigDecimal("35.00"), terminal.getValorIof(), "IOF tipo 1 no dia 29 (borda) deve ser 35.00");

        // Dia 30 (carência atingida): cai para ZERO
        terminal.setDiasCorridos(30);
        afirmarIgual(BigDecimal.ZERO, terminal.getValorIof(), "IOF tipo 1 no dia 30 (carência cumprida) deve ser ZERO");

        // Dia 31 e Dia 90: permanece ZERO
        terminal.setDiasCorridos(31);
        afirmarIgual(BigDecimal.ZERO, terminal.getValorIof(), "IOF tipo 1 no dia 31 deve ser ZERO");
        terminal.setDiasCorridos(90);
        afirmarIgual(BigDecimal.ZERO, terminal.getValorIof(), "IOF tipo 1 no dia 90 deve ser ZERO");
    }

    /**
     * Tier 1: IOF Tipo 2 (3,5% irrestrito) — alíquota de 3,5% contínua em qualquer prazo.
     */
    public static void testarIofTipo2Irrestrito() {
        AplicacaoFinanceira terminal = new AplicacaoFinanceira(
                "Fundo Especulativo", "FND-01", new BigDecimal("10000.00"), new BigDecimal("0.02")
        );
        terminal.setIof(2); // 3,5% irrestrito
        terminal.setRendimentoAtualBruto(new BigDecimal("1000.00"));

        terminal.setDiasCorridos(10);
        afirmarIgual(new BigDecimal("35.00"), terminal.getValorIof(), "IOF tipo 2 no dia 10 deve ser 35.00");

        terminal.setDiasCorridos(30);
        afirmarIgual(new BigDecimal("35.00"), terminal.getValorIof(), "IOF tipo 2 no dia 30 deve ser 35.00");

        terminal.setDiasCorridos(365);
        afirmarIgual(new BigDecimal("35.00"), terminal.getValorIof(), "IOF tipo 2 no dia 365 deve ser 35.00");
    }

    /**
     * Tier 1: IR Tipo 0 (Isento) — alíquota 0.00% em qualquer prazo.
     */
    public static void testarIrTipo0Isento() {
        AplicacaoFinanceira terminal = new AplicacaoFinanceira(
                "LCI Isenta IR", "LCI-03", new BigDecimal("10000.00"), new BigDecimal("0.02")
        );
        terminal.setIof(0);
        terminal.setTipoImpostoRenda(0); // Isento
        terminal.setRendimentoAtualBruto(new BigDecimal("1000.00"));

        terminal.setDiasCorridos(100);
        afirmarIgual(BigDecimal.ZERO, terminal.getValorIr(), "IR tipo 0 deve ser zero no dia 100");

        terminal.setDiasCorridos(500);
        afirmarIgual(BigDecimal.ZERO, terminal.getValorIr(), "IR tipo 0 deve ser zero no dia 500");
    }

    /**
     * Tier 1: IR Tipo 1 (Tabela Regressiva) — quatro faixas canônicas de alíquota.
     * Faixa 1 (0 a 180 dias): 22.50%
     * Faixa 2 (181 a 360 dias): 20.00%
     * Faixa 3 (361 a 720 dias): 17.50%
     * Faixa 4 (> 720 dias): 15.00%
     */
    public static void testarIrTipo1TabelaRegressivaQuatroFaixas() {
        AplicacaoFinanceira terminal = new AplicacaoFinanceira(
                "CDB Regressivo", "CDB-04", new BigDecimal("10000.00"), new BigDecimal("0.02")
        );
        terminal.setIof(0); // Sem IOF para avaliar diretamente a alíquota de IR
        terminal.setTipoImpostoRenda(1);
        terminal.setRendimentoAtualBruto(new BigDecimal("1000.00"));

        // Faixa 1 (ex.: 90 dias) -> 22.50% de 1000 = 225.00
        terminal.setDiasCorridos(90);
        afirmarIgual(new BigDecimal("225.00"), terminal.getValorIr(), "IR aos 90 dias (faixa 1) deve ser 225.00");

        // Faixa 2 (ex.: 250 dias) -> 20.00% de 1000 = 200.00
        terminal.setDiasCorridos(250);
        afirmarIgual(new BigDecimal("200.00"), terminal.getValorIr(), "IR aos 250 dias (faixa 2) deve ser 200.00");

        // Faixa 3 (ex.: 500 dias) -> 17.50% de 1000 = 175.00
        terminal.setDiasCorridos(500);
        afirmarIgual(new BigDecimal("175.00"), terminal.getValorIr(), "IR aos 500 dias (faixa 3) deve ser 175.00");

        // Faixa 4 (ex.: 800 dias) -> 15.00% de 1000 = 150.00
        terminal.setDiasCorridos(800);
        afirmarIgual(new BigDecimal("150.00"), terminal.getValorIr(), "IR aos 800 dias (faixa 4) deve ser 150.00");
    }

    /**
     * Tier 2 (Boundary): Transição exata nos dias limítrofes da Tabela Regressiva de IR.
     * Dias críticos: 180 vs 181; 360 vs 361; 720 vs 721.
     */
    public static void testarIrTransicaoExataLimitesFaixas() {
        AplicacaoFinanceira terminal = new AplicacaoFinanceira(
                "CDB Limites", "CDB-05", new BigDecimal("10000.00"), new BigDecimal("0.02")
        );
        terminal.setIof(0);
        terminal.setTipoImpostoRenda(1);
        terminal.setRendimentoAtualBruto(new BigDecimal("1000.00"));

        // Transição Faixa 1 -> Faixa 2 (Dia 180 vs 181)
        terminal.setDiasCorridos(180);
        afirmarIgual(new BigDecimal("225.00"), terminal.getValorIr(), "Dia 180 deve ser alíquota 22.50%");
        terminal.setDiasCorridos(181);
        afirmarIgual(new BigDecimal("200.00"), terminal.getValorIr(), "Dia 181 deve cair para 20.00%");

        // Transição Faixa 2 -> Faixa 3 (Dia 360 vs 361)
        terminal.setDiasCorridos(360);
        afirmarIgual(new BigDecimal("200.00"), terminal.getValorIr(), "Dia 360 deve ser alíquota 20.00%");
        terminal.setDiasCorridos(361);
        afirmarIgual(new BigDecimal("175.00"), terminal.getValorIr(), "Dia 361 deve cair para 17.50%");

        // Transição Faixa 3 -> Faixa 4 (Dia 720 vs 721)
        terminal.setDiasCorridos(720);
        afirmarIgual(new BigDecimal("175.00"), terminal.getValorIr(), "Dia 720 deve ser alíquota 17.50%");
        terminal.setDiasCorridos(721);
        afirmarIgual(new BigDecimal("150.00"), terminal.getValorIr(), "Dia 721 deve cair para 15.00%");
    }

    /**
     * Tier 3: Dedução do IOF da base de cálculo do IR.
     * Fórmula: BaseIR = max(0, RendimentoBruto - IOF).
     * IR = BaseIR * AliquotaIR.
     */
    public static void testarDeducaoIofDaBaseDeCalculoDoIr() {
        AplicacaoFinanceira terminal = new AplicacaoFinanceira(
                "CDB Curto com IOF", "CDB-06", new BigDecimal("10000.00"), new BigDecimal("0.02")
        );
        terminal.setIof(1); // 3.5%
        terminal.setTipoImpostoRenda(1); // Regressivo
        terminal.setRendimentoAtualBruto(new BigDecimal("1000.00"));
        terminal.setDiasCorridos(10); // < 30 dias -> IOF = 35.00

        BigDecimal iof = terminal.getValorIof();
        afirmarIgual(new BigDecimal("35.00"), iof, "IOF deve ser 35.00");

        // Base de cálculo do IR: 1000.00 - 35.00 = 965.00
        // Alíquota IR dia 10 (faixa 1): 22.50%
        // IR: 965.00 * 0.225 = 217.125 -> 217.13 (arredondado para centavos)
        BigDecimal ir = terminal.getValorIr();
        afirmarIgual(new BigDecimal("217.13"), ir, "IR deve incidir sobre base líquida de IOF (965 * 0.225 = 217.13)");

        // Imposto Total Devido: 35.00 + 217.13 = 252.13
        BigDecimal totalImposto = CalculadoraTributaria.calcularImpostoTotal(
                new BigDecimal("1000.00"), 10, 1, 1
        );
        afirmarIgual(new BigDecimal("252.13"), totalImposto, "imposto total deve somar IOF + IR");
    }

    /**
     * Tier 1: Métodos incrementarDia() e incrementarDiaAplicação() / incrementarDiaAplicacao().
     */
    public static void testarMetodosIncrementoDias() {
        AplicacaoFinanceira terminal = new AplicacaoFinanceira(
                "CDB Teste Dias", "CDB-07", new BigDecimal("5000.00"), new BigDecimal("0.01")
        );
        afirmarIgual(0, terminal.getDiasCorridos(), "diasCorridos inicia em zero");
        afirmarIgual(0, terminal.getDiasEfetivosCalculo(), "diasEfetivosCalculo inicia em zero");

        terminal.incrementarDia();
        afirmarIgual(1, terminal.getDiasCorridos(), "incrementarDia deve somar 1");
        terminal.incrementarDia();
        afirmarIgual(2, terminal.getDiasCorridos(), "incrementarDia deve somar 2");

        // incrementarDiaAplicação com acento
        terminal.incrementarDiaAplicação();
        afirmarIgual(1, terminal.getDiasEfetivosCalculo(), "incrementarDiaAplicação deve somar 1");

        // incrementarDiaAplicacao sem acento (alias de compatibilidade)
        terminal.incrementarDiaAplicacao();
        afirmarIgual(2, terminal.getDiasEfetivosCalculo(), "incrementarDiaAplicacao deve somar 2");
    }

    /**
     * Tier 1: Método setCadastro configurando os parâmetros operacionais da aplicação.
     */
    public static void testarMetodoSetCadastro() {
        AplicacaoFinanceira terminal = new AplicacaoFinanceira(
                "Original", "ORIG-01", new BigDecimal("1000.00"), new BigDecimal("0.01")
        );

        terminal.setCadastro(
                "Atualizada", "NOVO-99", new BigDecimal("8000.00"), new BigDecimal("0.030"),
                1, 180, 30, 0, 1, 1
        );

        afirmarIgual("Atualizada", terminal.getNomeAplicacao(), "nome deve ser Atualizada");
        afirmarIgual("NOVO-99", terminal.getNumeroAplicacao(), "numero deve ser NOVO-99");
        afirmarIgual(new BigDecimal("8000.00"), terminal.getValorAplicado(), "valorAplicado atualizado");
        afirmarIgual(new BigDecimal("0.030"), terminal.getTaxaMensal(), "taxaMensal atualizada");
        afirmarIgual(new BigDecimal("0.001"), terminal.getTaxaDiaria(), "taxaDiaria sincronizada");
        afirmarIgual(1, terminal.getTipoRendimento(), "tipoRendimento atualizado");
        afirmarIgual(180, terminal.getTempoFinalVencimento(), "vencimento atualizado");
        afirmarIgual(30, terminal.getTempoMinimoPermanencia(), "permanência atualizada");
        afirmarIgual(0, terminal.getTipoResgate(), "tipoResgate atualizado");
        afirmarIgual(1, terminal.getIof(), "iof atualizado");
        afirmarIgual(1, terminal.getTipoImpostoRenda(), "ir atualizado");
    }

    /**
     * Tier 3 & Tier 5: Segurança de herança garantindo que subclasses extensíveis
     * não sofram chamadas para métodos sobrescrevíveis no construtor da superclasse.
     */
    public static void testarHerancaSegura() {
        class AplicacaoSubclasseSegura extends AplicacaoFinanceira {
            AplicacaoSubclasseSegura() {
                super("Subclasse", "SUB-01", new BigDecimal("2000.00"), new BigDecimal("0.01"));
            }

            @Override
            public void setCadastro(String nome, String numero, BigDecimal valor, BigDecimal taxaMensal,
                                    int tipoRendimento, int tempoVencimento, int tempoPermanencia,
                                    int tipoResgate, int iof, int tipoIR) {
                throw new AssertionError("Construtor da superclasse jamais deve despachar para setCadastro virtual!");
            }
        }

        AplicacaoSubclasseSegura sub = new AplicacaoSubclasseSegura();
        afirmarIgual("Subclasse", sub.getNomeAplicacao(), "nome da subclasse");
        afirmarIgual(new BigDecimal("2000.00"), sub.getValorAplicado(), "valor da subclasse");
    }

    /**
     * Tier 4: Cenário do mundo real — Aplicação ultracurta (10 dias) com resgate antes da carência.
     */
    public static void testarCenarioMundoRealCicloCurto10Dias() {
        AplicacaoFinanceira terminal = new AplicacaoFinanceira(
                "CDB Giro 10d", "GIRO-10", new BigDecimal("10000.00"), new BigDecimal("0.030")
        );
        terminal.setIof(1); // 3.5% com carência de 30 dias
        terminal.setTipoImpostoRenda(1); // Regressivo

        // 10 dias transcorridos, rendimento bruto de R$ 100,00
        terminal.setDiasCorridos(10);
        terminal.setRendimentoAtualBruto(new BigDecimal("100.00"));
        terminal.setValorAtualBruto(new BigDecimal("10100.00"));

        // IOF devido: 100 * 0.035 = 3.50
        BigDecimal iof = terminal.getValorIof();
        afirmarIgual(new BigDecimal("3.50"), iof, "IOF de 10 dias deve ser 3.50");

        // Base IR: 100 - 3.50 = 96.50
        // IR 22.50%: 96.50 * 0.225 = 21.7125 -> 21.71
        BigDecimal ir = terminal.getValorIr();
        afirmarIgual(new BigDecimal("21.71"), ir, "IR de 10 dias deve ser 21.71");

        // Imposto Total Devido: 3.50 + 21.71 = 25.21
        BigDecimal impostoTotal = iof.add(ir);
        terminal.setImpostoAtualDevido(impostoTotal);
        afirmarIgual(new BigDecimal("25.21"), terminal.getImpostoAtualDevido(), "imposto total devido 25.21");

        // Rendimento Líquido: 100 - 25.21 = 74.79
        terminal.setRendimentoAtualLiquido(terminal.getRendimentoAtualBruto().subtract(impostoTotal));
        afirmarIgual(new BigDecimal("74.79"), terminal.getRendimentoAtualLiquido(), "rendimento líquido 74.79");

        // Valor Atual Líquido: 10100 - 25.21 = 10074.79
        terminal.setValorAtualLiquido(terminal.getValorAtualBruto().subtract(impostoTotal));
        afirmarIgual(new BigDecimal("10074.79"), terminal.getValorAtualLiquido(), "valor atual líquido 10074.79");
    }

    /**
     * Tier 4: Cenário do mundo real — Médio prazo (200 dias) com carência de IOF superada e IR de 20%.
     */
    public static void testarCenarioMundoRealCicloMedio200Dias() {
        AplicacaoFinanceira terminal = new AplicacaoFinanceira(
                "CDB 200d", "MED-200", new BigDecimal("50000.00"), new BigDecimal("0.020")
        );
        terminal.setIof(1);
        terminal.setTipoImpostoRenda(1);

        terminal.setDiasCorridos(200);
        terminal.setRendimentoAtualBruto(new BigDecimal("2500.00"));
        terminal.setValorAtualBruto(new BigDecimal("52500.00"));

        // Carência de 30 dias superada: IOF = 0.00
        afirmarIgual(BigDecimal.ZERO, terminal.getValorIof(), "IOF deve ser isento aos 200 dias");

        // IR na faixa de 181 a 360 dias: 20.00% sobre 2500.00 = 500.00
        afirmarIgual(new BigDecimal("500.00"), terminal.getValorIr(), "IR deve ser 500.00 (20%)");

        terminal.setImpostoAtualDevido(new BigDecimal("500.00"));
        terminal.setRendimentoAtualLiquido(new BigDecimal("2000.00"));
        terminal.setValorAtualLiquido(new BigDecimal("52000.00"));

        afirmarIgual(new BigDecimal("2000.00"), terminal.getRendimentoAtualLiquido(), "rendimento líquido 2000.00");
        afirmarIgual(new BigDecimal("52000.00"), terminal.getValorAtualLiquido(), "saldo líquido 52000.00");
    }

    /**
     * Tier 4: Cenário do mundo real — Longo prazo (800 dias) com alíquota mínima de 15%.
     */
    public static void testarCenarioMundoRealCicloLongo800Dias() {
        AplicacaoFinanceira terminal = new AplicacaoFinanceira(
                "CDB Longo 800d", "LONG-800", new BigDecimal("100000.00"), new BigDecimal("0.015")
        );
        terminal.setIof(1);
        terminal.setTipoImpostoRenda(1);

        terminal.setDiasCorridos(800);
        terminal.setRendimentoAtualBruto(new BigDecimal("12000.00"));

        // IOF isento
        afirmarIgual(BigDecimal.ZERO, terminal.getValorIof(), "IOF isento aos 800 dias");

        // IR na alíquota mínima (acima de 720 dias): 15.00% sobre 12000.00 = 1800.00
        afirmarIgual(new BigDecimal("1800.00"), terminal.getValorIr(), "IR aos 800 dias deve ser 1800.00 (15%)");
    }

    /**
     * Tier 5 (Adversarial): Validações negativas em parâmetros inconsistentes.
     */
    public static void testarRejeicoesValoresInvalidos() {
        // Nome nulo ou em branco
        afirmarRejeicao(() -> new AplicacaoFinanceira(null, "NUM-01", new BigDecimal("100.00"), new BigDecimal("0.01")),
                IllegalArgumentException.class, "nome nulo deve ser rejeitado");
        afirmarRejeicao(() -> new AplicacaoFinanceira("   ", "NUM-01", new BigDecimal("100.00"), new BigDecimal("0.01")),
                IllegalArgumentException.class, "nome em branco deve ser rejeitado");

        // Número nulo ou em branco
        afirmarRejeicao(() -> new AplicacaoFinanceira("CDB", null, new BigDecimal("100.00"), new BigDecimal("0.01")),
                IllegalArgumentException.class, "número nulo deve ser rejeitado");

        // Aporte inicial zero ou negativo
        afirmarRejeicao(() -> new AplicacaoFinanceira("CDB", "01", BigDecimal.ZERO, new BigDecimal("0.01")),
                IllegalArgumentException.class, "aporte zero deve ser rejeitado");
        afirmarRejeicao(() -> new AplicacaoFinanceira("CDB", "01", new BigDecimal("-50.00"), new BigDecimal("0.01")),
                IllegalArgumentException.class, "aporte negativo deve ser rejeitado");

        // Taxa mensal negativa
        afirmarRejeicao(() -> new AplicacaoFinanceira("CDB", "01", new BigDecimal("100.00"), new BigDecimal("-0.01")),
                IllegalArgumentException.class, "taxa negativa deve ser rejeitada");
    }

    // =========================================================================
    // Tier 4 & 5: Suíte de Testes Exaustivos de atualizarSaldo(LocalDate)
    // =========================================================================

    /**
     * Valida a progressão dia a dia de atualizarSaldo(LocalDate):
     * - Avanço de dias corridos e dias úteis efetivos.
     * - Acúmulo de rendimento nos dias úteis (segunda a sexta).
     * - Idempotência ao chamar atualizarSaldo para a mesma data múltiplas vezes.
     */
    public static void testarAtualizarSaldoProgressaoDiaADia() {
        System.out.println("[TESTE] atualizarSaldo com progressão dia a dia e idempotência...");
        LocalDate segunda = LocalDate.of(2026, 10, 5); // Segunda-feira
        AplicacaoFinanceira terminal = new AplicacaoFinanceira(
                "CDB Diário", "CDB-DIA-01",
                new BigDecimal("10000.00"), new BigDecimal("0.030"),
                0, 0, 0, 0, 0, 0, segunda
        ); // taxaMensal = 3% -> taxaDiaria = 0.001; tipoRendimento = 0 (diário); isento iof/ir

        // Estado inicial
        afirmarIgual(0, terminal.getDiasCorridos(), "diasCorridos inicial deve ser 0");
        afirmarIgual(0, terminal.getDiasEfetivosCalculo(), "diasEfetivosCalculo inicial deve ser 0");
        afirmarIgual(new BigDecimal("10000.00"), terminal.getValorAtualBruto(), "saldo inicial 10000.00");

        // Dia de cadastro: segunda-feira 2026-10-05 permanece como dia zero.
        terminal.atualizarSaldo(segunda);
        afirmarIgual(0, terminal.getDiasCorridos(), "a data de cadastro permanece como dia zero");
        afirmarIgual(0, terminal.getDiasEfetivosCalculo(), "não há dia útil efetivo na data de cadastro");
        afirmarIgual(new BigDecimal("0.00"), terminal.getRendimentoAtualBruto(),
                "não há rendimento na data de cadastro");

        // Dia 1: terça-feira 2026-10-06.
        LocalDate terca = segunda.plusDays(1);
        terminal.atualizarSaldo(terca);
        afirmarIgual(1, terminal.getDiasCorridos(), "dias corridos após o primeiro dia decorrido");
        afirmarIgual(1, terminal.getDiasEfetivosCalculo(), "dias efetivos após a primeira terça-feira");
        afirmarIgual(new BigDecimal("10.00"), terminal.getRendimentoAtualBruto(), "rendimento do primeiro dia útil");
        afirmarIgual(new BigDecimal("10010.00"), terminal.getValorAtualBruto(), "saldo bruto após o primeiro dia útil");

        terminal.atualizarSaldo(terca);
        afirmarIgual(1, terminal.getDiasCorridos(), "idempotência: o dia corrido não é duplicado");
        afirmarIgual(1, terminal.getDiasEfetivosCalculo(), "idempotência: o dia efetivo não é duplicado");
        afirmarIgual(new BigDecimal("10010.00"), terminal.getValorAtualBruto(), "idempotência: saldo inalterado");

        // Dia 2: quarta-feira 2026-10-07.
        LocalDate quarta = terca.plusDays(1);
        terminal.atualizarSaldo(quarta);
        afirmarIgual(2, terminal.getDiasCorridos(), "dias corridos na quarta");
        afirmarIgual(2, terminal.getDiasEfetivosCalculo(), "dias efetivos na quarta");
        afirmarIgual(new BigDecimal("20.01"), terminal.getRendimentoAtualBruto(), "rendimento acumulado na quarta");
        afirmarIgual(new BigDecimal("10020.01"), terminal.getValorAtualBruto(), "saldo bruto na quarta");

        // Dia 4: Quinta-feira 2026-10-08
        LocalDate quinta = quarta.plusDays(1);
        terminal.atualizarSaldo(quinta);
        afirmarIgual(3, terminal.getDiasCorridos(), "dias corridos na quinta");
        afirmarIgual(3, terminal.getDiasEfetivosCalculo(), "dias efetivos na quinta");
        afirmarIgual(new BigDecimal("30.03"), terminal.getRendimentoAtualBruto(), "rendimento quinta");
        afirmarIgual(new BigDecimal("10030.03"), terminal.getValorAtualBruto(), "saldo bruto quinta");

        // Dia 5: Sexta-feira 2026-10-09
        LocalDate sexta = quinta.plusDays(1);
        terminal.atualizarSaldo(sexta);
        afirmarIgual(4, terminal.getDiasCorridos(), "dias corridos na sexta");
        afirmarIgual(4, terminal.getDiasEfetivosCalculo(), "dias efetivos na sexta");
        afirmarIgual(new BigDecimal("40.06"), terminal.getRendimentoAtualBruto(), "rendimento sexta");
        afirmarIgual(new BigDecimal("10040.06"), terminal.getValorAtualBruto(), "saldo bruto sexta");
        System.out.println("  -> [OK] Progressão dia a dia e idempotência verificadas.");
    }

    /**
     * Garante que a data do cadastro é o marco inicial e não um dia decorrido.
     */
    public static void testarDataDaAplicacaoComoDiaZero() {
        LocalDate cadastro = LocalDate.of(2026, 10, 5);
        AplicacaoFinanceira aplicacao = new AplicacaoFinanceira(
                "Aplicação dia zero", "DIA-ZERO",
                new BigDecimal("1000.00"), new BigDecimal("0.03"),
                0, 0, 0, 0, 0, 0, cadastro
        );

        aplicacao.atualizarSaldo(cadastro);
        afirmarIgual(0, aplicacao.getDiasCorridos(), "a data de cadastro permanece como dia zero");
        afirmarIgual(0, aplicacao.getDiasEfetivosCalculo(), "não há cálculo de juros na data de cadastro");
        afirmarIgual(new BigDecimal("0.00"), aplicacao.getRendimentoAtualBruto(),
                "não há rendimento no dia de cadastro");

        aplicacao.atualizarSaldo(cadastro.plusDays(1));
        afirmarIgual(1, aplicacao.getDiasCorridos(), "o dia seguinte ao cadastro é o primeiro dia decorrido");
        afirmarIgual(1, aplicacao.getDiasEfetivosCalculo(), "o primeiro dia útil após cadastro é efetivo");
        afirmarIgual(new BigDecimal("1.00"), aplicacao.getRendimentoAtualBruto(),
                "o rendimento começa no primeiro dia útil após o cadastro");
    }

    /**
     * Valida o comportamento de atualizarSaldo ao atravessar fins de semana:
     * - Sábado e domingo incrementam diasCorridos, mas NÃO incrementam diasEfetivosCalculo.
     * - Nenhum rendimento adicional é auferido durante sábado e domingo.
     * - Chamada direta no sábado/domingo processa corretamente os dias úteis anteriores (Desafio 3).
     */
    public static void testarAtualizarSaldoSaltoFimDeSemana() {
        System.out.println("[TESTE] atualizarSaldo ao avançar em finais de semana...");
        LocalDate segunda = LocalDate.of(2026, 10, 5); // Segunda
        AplicacaoFinanceira terminal = new AplicacaoFinanceira(
                "CDB FimDeSemana", "CDB-FDS-01",
                new BigDecimal("10000.00"), new BigDecimal("0.030"),
                1, 0, 0, 0, 0, 0, segunda
        ); // taxaDiaria = 0.001; tipoRendimento = 1 (mensal); isento

        // 1. Atualiza até sexta-feira (5 dias úteis)
        LocalDate sexta = LocalDate.of(2026, 10, 9);
        terminal.atualizarSaldo(sexta);
        afirmarIgual(4, terminal.getDiasCorridos(), "dias corridos até sexta devem excluir o dia de cadastro");
        afirmarIgual(4, terminal.getDiasEfetivosCalculo(), "dias efetivos até sexta devem excluir o dia de cadastro");
        afirmarIgual(new BigDecimal("40.00"), terminal.getRendimentoAtualBruto(), "rendimento bruto na sexta");
        afirmarIgual(new BigDecimal("10040.00"), terminal.getValorAtualBruto(), "saldo bruto sexta");

        // 2. Avança para sábado: diasCorridos incrementa para 6, mas dias úteis e rendimento permanecem 5 e 50.00
        LocalDate sabado = LocalDate.of(2026, 10, 10);
        terminal.atualizarSaldo(sabado);
        afirmarIgual(5, terminal.getDiasCorridos(), "dias corridos no sábado");
        afirmarIgual(4, terminal.getDiasEfetivosCalculo(), "dias efetivos no sábado");
        afirmarIgual(new BigDecimal("40.00"), terminal.getRendimentoAtualBruto(), "rendimento no sábado não se altera");
        afirmarIgual(new BigDecimal("10040.00"), terminal.getValorAtualBruto(), "bruto no sábado inalterado");

        // 3. Avança para domingo: diasCorridos incrementa para 7, dias úteis e rendimento continuam iguais
        LocalDate domingo = LocalDate.of(2026, 10, 11);
        terminal.atualizarSaldo(domingo);
        afirmarIgual(6, terminal.getDiasCorridos(), "dias corridos no domingo");
        afirmarIgual(4, terminal.getDiasEfetivosCalculo(), "dias efetivos no domingo");
        afirmarIgual(new BigDecimal("40.00"), terminal.getRendimentoAtualBruto(), "rendimento no domingo não se altera");
        afirmarIgual(new BigDecimal("10040.00"), terminal.getValorAtualBruto(), "bruto no domingo inalterado");

        // 4. Avança para a segunda seguinte, computando os dias civis do fim de semana.
        LocalDate segundaSeguinte = LocalDate.of(2026, 10, 12);
        terminal.atualizarSaldo(segundaSeguinte);
        afirmarIgual(7, terminal.getDiasCorridos(), "dias corridos na segunda seguinte");
        afirmarIgual(5, terminal.getDiasEfetivosCalculo(), "dias efetivos na segunda seguinte");
        afirmarIgual(new BigDecimal("50.00"), terminal.getRendimentoAtualBruto(), "rendimento na segunda seguinte");
        afirmarIgual(new BigDecimal("10050.00"), terminal.getValorAtualBruto(), "bruto na segunda seguinte");

        // 5. Teste direto do Desafio 3: nova aplicação criada na segunda e atualizada DIRETAMENTE no sábado
        AplicacaoFinanceira appDiretoFds = new AplicacaoFinanceira(
                "CDB Salto Direto", "CDB-FDS-02",
                new BigDecimal("10000.00"), new BigDecimal("0.030"),
                1, 0, 0, 0, 0, 0, segunda
        );
        appDiretoFds.atualizarSaldo(sabado);
        afirmarIgual(5, appDiretoFds.getDiasCorridos(), "salto direto para sábado exclui o dia de cadastro");
        afirmarIgual(4, appDiretoFds.getDiasEfetivosCalculo(), "salto direto processa os quatro dias úteis decorridos");
        afirmarIgual(new BigDecimal("40.00"), appDiretoFds.getRendimentoAtualBruto(),
                "salto direto para sábado não pode abortar prematuramente e deve acumular rendimentos dos dias úteis");
        System.out.println("  -> [OK] Atualização em fins de semana e saltos verificados com precisão.");
    }

    /**
     * Valida a atualização de saldo através de 30 dias corridos e virada de mês:
     * - Capitalização mensal (tipoRendimento = 1): rendimentos pendentes creditados no 30º dia civil.
     * - Rendimento do 31º dia passa a incidir sobre o saldo composto (juros sobre juros).
     * - Carência do IOF tipo 1 (30 dias) atinge isenção no 30º dia.
     * - Incidência correta de IR regressivo.
     */
    public static void testarAtualizarSaldoTrintaDiasEViradaDeMes() {
        System.out.println("[TESTE] atualizarSaldo em 30 dias corridos, capitalização mensal e virada de mês...");
        LocalDate inicio = LocalDate.of(2026, 10, 1); // Quinta-feira
        AplicacaoFinanceira terminal = new AplicacaoFinanceira(
                "CDB Mensal 30d", "CDB-M30-01",
                new BigDecimal("10000.00"), new BigDecimal("0.030"),
                1, 0, 0, 0, 1, 1, inicio
        ); // taxaDiaria = 0.001; tipoRendimento = 1 (mensal); iof = 1 (carência 30d); ir = 1 (regressivo)

        // Em Outubro/2026:
        // Do dia 2 ao dia 29 decorrem 28 dias corridos e 20 dias úteis; o cadastro é dia zero.
        LocalDate dia29 = inicio.plusDays(28); // 2026-10-29 (29 dias corridos: 1 a 29 inclusive)
        terminal.atualizarSaldo(dia29);
        afirmarIgual(28, terminal.getDiasCorridos(), "dias corridos desde o cadastro no dia 29");
        afirmarIgual(20, terminal.getDiasEfetivosCalculo(), "dias úteis desde o cadastro até dia 29");
        afirmarIgual(new BigDecimal("200.00"), terminal.getRendimentoAtualBruto(), "rendimento bruto dia 29");
        afirmarIgual(new BigDecimal("7.00"), terminal.getValorIof(), "IOF deve incidir antes de 30 dias");

        // No dia 30 (2026-10-30, sexta-feira): 29 dias corridos e 21 dias úteis
        LocalDate dia30 = inicio.plusDays(29); // 2026-10-30
        terminal.atualizarSaldo(dia30);
        afirmarIgual(29, terminal.getDiasCorridos(), "dias corridos no dia 30");
        afirmarIgual(21, terminal.getDiasEfetivosCalculo(), "dias úteis até dia 30");
        afirmarIgual(new BigDecimal("210.00"), terminal.getRendimentoAtualBruto(), "rendimento bruto no dia 30");
        afirmarIgual(new BigDecimal("7.35"), terminal.getValorIof(), "IOF ainda incide antes de completar 30 dias");

        // No primeiro dia útil após 30 dias decorridos, o ciclo mensal é creditado.
        LocalDate dia33 = LocalDate.of(2026, 11, 2);
        terminal.atualizarSaldo(dia33);
        afirmarIgual(32, terminal.getDiasCorridos(), "dias corridos desde o cadastro em 02/11");
        afirmarIgual(22, terminal.getDiasEfetivosCalculo(), "dias úteis desde o cadastro em 02/11");
        afirmarIgual(new BigDecimal("220.00"), terminal.getRendimentoAtualBruto(),
                "o rendimento é calculado sobre o saldo anterior antes do crédito mensal");
        afirmarIgual(BigDecimal.ZERO, terminal.getValorIof(), "IOF deixa de incidir após 30 dias");
        afirmarIgual(new BigDecimal("10220.00"), terminal.getValorAtualBruto(),
                "rendimento pendente é creditado no primeiro dia útil após completar o ciclo");
        System.out.println("  -> [OK] Capitalização mensal, carência de IOF e virada de mês validadas.");
    }

    /**
     * Valida o resgate parcial e subsequente atualização de saldo (Desafio 2):
     * - Resgate parcial de valor superior ao aporte inicial mas inferior ao líquido total.
     * - Garante que saldo base não fica negativo e que o rendimento do dia seguinte não decresce o patrimônio.
     */
    public static void testarAtualizarSaldoResgateParcialERendimentoSubsequente() {
        System.out.println("[TESTE] atualizarSaldo com resgate parcial e rendimento subsequente...");
        LocalDate inicio = LocalDate.of(2026, 1, 1); // Quinta-feira
        AplicacaoFinanceira terminal = new AplicacaoFinanceira(
                "CDB Resgate", "CDB-RES-01",
                new BigDecimal("1000.00"), new BigDecimal("0.030"),
                1, 0, 0, 0, 0, 0, inicio
        ); // taxaDiaria = 0.001; tipoRendimento = 1 (mensal); isento

        // Atualiza até quarta-feira 21 de janeiro (14 dias úteis após o cadastro)
        LocalDate dia21 = LocalDate.of(2026, 1, 21);
        terminal.atualizarSaldo(dia21);

        afirmarIgual(14, terminal.getDiasEfetivosCalculo(), "14 dias úteis decorridos até 21/01");
        afirmarIgual(new BigDecimal("14.00"), terminal.getRendimentoAtualBruto(), "rendimento bruto até 21/01");
        afirmarIgual(new BigDecimal("1014.00"), terminal.getValorAtualBruto(), "valor bruto antes do resgate");
        afirmarIgual(new BigDecimal("1014.00"), terminal.getValorAtualLiquido(), "valor líquido antes do resgate");

        // Resgate parcial de R$ 1.010,00 (maior que o aporte inicial, menor que o saldo líquido)
        terminal.resgatar(new BigDecimal("1010.00"));
        afirmarIgual(new BigDecimal("1010.00"), terminal.getValorResgate(), "valor resgate deve ser 1010.00");
        afirmarIgual(new BigDecimal("4.00"), terminal.getValorAtualBruto(), "valor bruto após resgate deve ser 4.00");
        afirmarIgual(new BigDecimal("4.00"), terminal.getValorAtualLiquido(), "valor líquido após resgate deve ser 4.00");

        // Garantia crítica do Desafio 2: saldo base NÃO PODE ser negativo
        afirmarVerdadeiro(terminal.getSaldo().signum() >= 0, "saldo base pós-resgate não pode ser negativo (obtido: " + terminal.getSaldo() + ")");

        // Avança para o dia útil seguinte: quinta-feira 22 de janeiro
        LocalDate dia22 = LocalDate.of(2026, 1, 22);
        terminal.atualizarSaldo(dia22);

        // O patrimônio não pode diminuir! Saldo bruto deve permanecer >= 4.00.
        afirmarVerdadeiro(terminal.getValorAtualBruto().compareTo(new BigDecimal("4.00")) >= 0,
                "patrimônio após próximo dia útil deve ser >= 4.00 (obtido: " + terminal.getValorAtualBruto() + ")");
        System.out.println("  -> [OK] Resgate parcial e rendimento subsequente sem degeneração confirmados.");
    }

    /**
     * Valida limites e contratos do método atualizarSaldo:
     * - Rejeição de data nula (NullPointerException).
     * - Rejeição de data anterior à data da aplicação (IllegalArgumentException).
     * - Interrupção de acréscimo de rendimento após vencimento (tempoFinalVencimento).
     * - Funcionamento do método de conveniência sem argumentos atualizarSaldo().
     */
    public static void testarAtualizarSaldoLimitesEVencimento() {
        System.out.println("[TESTE] atualizarSaldo limites, contratos e cessação no vencimento...");
        LocalDate inicio = LocalDate.of(2026, 6, 1); // Segunda-feira
        AplicacaoFinanceira terminal = new AplicacaoFinanceira(
                "CDB Prazo Determinado", "CDB-PRZ-01",
                new BigDecimal("5000.00"), new BigDecimal("0.030"),
                0, 5, 0, 0, 0, 0, inicio
        ); // Vencimento em 5 dias corridos; tipoRendimento = 0; isento

        // 1. Data nula deve ser rejeitada
        afirmarRejeicao(() -> terminal.atualizarSaldo(null), NullPointerException.class, "data nula deve lançar NPE");

        // 2. Data anterior à data de início deve ser rejeitada
        afirmarRejeicao(() -> terminal.atualizarSaldo(inicio.minusDays(1)), IllegalArgumentException.class,
                "data anterior deve lançar IllegalArgumentException");

        // 3. Atualiza até o vencimento exato: o cadastro é dia zero, então são 5 dias após 01/06.
        LocalDate diaVencimento = inicio.plusDays(5); // 06/06
        terminal.atualizarSaldo(diaVencimento);
        afirmarIgual(5, terminal.getDiasCorridos(), "dias corridos no vencimento deve ser 5");
        BigDecimal rendimentoNoVencimento = terminal.getRendimentoAtualBruto();
        afirmarVerdadeiro(rendimentoNoVencimento.signum() > 0, "rendimento deve ser positivo até o vencimento");

        // 4. Atualiza para além do vencimento (ex.: dia 10 contado após o cadastro).
        LocalDate alemVencimento = inicio.plusDays(10); // 11/06
        terminal.atualizarSaldo(alemVencimento);
        afirmarIgual(10, terminal.getDiasCorridos(), "dias corridos continuam avançando");
        afirmarIgual(rendimentoNoVencimento, terminal.getRendimentoAtualBruto(),
                "rendimento cessa após atingir o prazo final de vencimento");

        // 5. Método sem argumentos atualizarSaldo()
        AplicacaoFinanceira appHoje = new AplicacaoFinanceira(
                "CDB Hoje", "CDB-HJE-01",
                new BigDecimal("2000.00"), new BigDecimal("0.01")
        );
        appHoje.atualizarSaldo(); // Não deve lançar exceção
        afirmarVerdadeiro(appHoje.getDiasCorridos() >= 0, "atualizarSaldo() sem argumentos executa com sucesso");
        System.out.println("  -> [OK] Limites, contratos e vencimento verificados com sucesso.");
    }
}

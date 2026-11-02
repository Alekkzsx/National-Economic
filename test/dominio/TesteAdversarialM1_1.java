package test.dominio;

import static test.Afirmacao.*;

import dominio.AplicacaoFinanceira;
import dominio.CalculadoraTributaria;
import dominio.Moeda;
import dominio.OpcaoIOF;
import dominio.OpcaoIR;
import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;

/**
 * Bateria de testes adversariais empíricos executada pelo Challenger M1-1
 * para estressar AplicacaoFinanceira, CalculadoraTributaria e classes de apoio do Milestone 1.
 */
public final class TesteAdversarialM1_1 {

    public static void main(String[] args) {
        System.out.println("================================================================================");
        System.out.println(">>> INICIANDO SUÍTE ADVERSARIAL EMPÍRICA (CHALLENGER M1-1)");
        System.out.println("================================================================================");

        int total = 0;
        int falhasDetectadas = 0;

        // Vetor 1: Taxas lineares e extremos
        total++; testarTaxasExtremasEConversoes();
        total++; testarRejeicaoTaxasNegativasENulas();

        // Vetor 2: Fronteiras de IOF
        total++; testarFronteirasIofTipo1Dias29_30_31();
        total++; testarIofTipos0e2Extremos();
        total++; testarIofEntradasDegeneradas();

        // Vetor 3: Fronteiras de IR Regressivo e Base de Cálculo
        total++; testarFronteirasIrRegressivoDiasCriticos();
        total++; testarBaseDeCalculoIrLiquidaDeIof();
        total++; testarIrEntradasDegeneradas();

        // Vetor 4: Herança segura e escape de métodos virtuais em construtor
        total++; testarHerancaSobrescritaSetCadastro();
        total++; boolean escapeIof = testarEscapeVirtualGetValorIofNoConstrutor();
        if (escapeIof) falhasDetectadas++;
        total++; boolean escapeIr = testarEscapeVirtualGetValorIrNoConstrutor();
        if (escapeIr) falhasDetectadas++;

        // Vetor 5: Falhas funcionais operacionais (resgatar, atualizarSaldo fim de semana, Moeda.de)
        total++; boolean bugResgate = testarBugRendimentoNegativoAposResgateParcial();
        if (bugResgate) falhasDetectadas++;
        total++; boolean bugFimDeSemana = testarBugAtualizarSaldoFimDeSemanaIgnoraDiasUteis();
        if (bugFimDeSemana) falhasDetectadas++;
        total++; boolean bugMoeda = testarBugMoedaDeNotacaoPonto();
        if (bugMoeda) falhasDetectadas++;

        System.out.println("================================================================================");
        System.out.printf(">>> RESUMO ADVERSARIAL: %d testes executados | %d defeitos empíricos confirmados%n",
                total, falhasDetectadas);
        System.out.println("================================================================================");
    }

    // =========================================================================
    // Vetor 1: Taxas Lineares e Extremos
    // =========================================================================

    public static void testarTaxasExtremasEConversoes() {
        System.out.println("[TESTE] Vetor 1: Taxas lineares extremas e dízimas...");
        AplicacaoFinanceira terminal = new AplicacaoFinanceira();

        // 1. Taxa 0%
        terminal.setTaxaMensal(BigDecimal.ZERO);
        afirmarIgual(BigDecimal.ZERO, terminal.getTaxaDiaria(), "Taxa 0% mensal deve resultar em 0 diária");
        terminal.setTaxaDiaria(BigDecimal.ZERO);
        afirmarIgual(BigDecimal.ZERO, terminal.getTaxaMensal(), "Taxa 0% diária deve resultar em 0 mensal");

        // 2. Taxa ínfima (0.0001% = 0.000001)
        BigDecimal taxaInfima = new BigDecimal("0.000001");
        terminal.setTaxaMensal(taxaInfima);
        afirmarVerdadeiro(terminal.getTaxaDiaria().signum() > 0, "Taxa diária ínfima deve ser positiva");
        BigDecimal roundtripInfima = terminal.getTaxaDiaria().multiply(BigDecimal.valueOf(30), MathContext.DECIMAL128);
        afirmarVerdadeiro(roundtripInfima.subtract(taxaInfima).abs().compareTo(new BigDecimal("1E-30")) < 0,
                "Roundtrip de taxa ínfima deve manter precisão DECIMAL128");

        // 3. Taxa extrema alta (1000% = 10.0)
        BigDecimal taxaExtrema = new BigDecimal("10.0");
        terminal.setTaxaMensal(taxaExtrema);
        afirmarVerdadeiro(terminal.getTaxaDiaria().signum() > 0, "Taxa diária extrema deve ser positiva");
        BigDecimal roundtripExtrema = terminal.getTaxaDiaria().multiply(BigDecimal.valueOf(30), MathContext.DECIMAL128);
        afirmarVerdadeiro(roundtripExtrema.subtract(taxaExtrema).abs().compareTo(new BigDecimal("1E-30")) < 0,
                "Roundtrip de taxa extrema 1000% deve manter precisão DECIMAL128");

        // 4. Dízima periódica: 10% ao mês (0.10)
        BigDecimal taxa10 = new BigDecimal("0.10");
        terminal.setTaxaMensal(taxa10);
        BigDecimal diaria10 = terminal.getTaxaDiaria();
        // 0.10 / 30 = 0.003333333333333333333333333333333333...
        afirmarVerdadeiro(diaria10.toString().startsWith("0.0033333"), "Taxa diária de 10% deve ser dízima 0.00333...");
        BigDecimal roundtrip10 = diaria10.multiply(BigDecimal.valueOf(30), MathContext.DECIMAL128);
        afirmarVerdadeiro(roundtrip10.subtract(taxa10).abs().compareTo(new BigDecimal("1E-30")) < 0,
                "Diferença no roundtrip de dízima 10% deve ser estritamente inferior a 1E-30");
        System.out.println("  -> [OK] Taxas extremas e dízimas verificadas com DECIMAL128.");
    }

    public static void testarRejeicaoTaxasNegativasENulas() {
        System.out.println("[TESTE] Rejeição de taxas nulas e negativas...");
        AplicacaoFinanceira terminal = new AplicacaoFinanceira();

        afirmarRejeicao(() -> terminal.setTaxaMensal(null), IllegalArgumentException.class, "Taxa mensal nula deve ser rejeitada");
        afirmarRejeicao(() -> terminal.setTaxaDiaria(null), IllegalArgumentException.class, "Taxa diária nula deve ser rejeitada");
        afirmarRejeicao(() -> terminal.setTaxaMensal(new BigDecimal("-0.01")), IllegalArgumentException.class, "Taxa mensal negativa deve ser rejeitada");
        afirmarRejeicao(() -> terminal.setTaxaDiaria(new BigDecimal("-0.0001")), IllegalArgumentException.class, "Taxa diária negativa deve ser rejeitada");
        System.out.println("  -> [OK] Rejeição de taxas inválidas comprovada.");
    }

    // =========================================================================
    // Vetor 2: Fronteiras de IOF
    // =========================================================================

    public static void testarFronteirasIofTipo1Dias29_30_31() {
        System.out.println("[TESTE] Vetor 2: Fronteira IOF Tipo 1 (dias 29, 30 e 31)...");
        BigDecimal rendimento = new BigDecimal("1000.00");

        // Dia 29: Aplica 3.5% = 35.00
        BigDecimal iof29 = CalculadoraTributaria.calcularIof(rendimento, 29, 1);
        afirmarIgual(new BigDecimal("35.00"), iof29, "IOF tipo 1 no dia 29 deve ser exatamente 35.00 (3.5%)");

        // Dia 30: Carência cumprida -> 0.00
        BigDecimal iof30 = CalculadoraTributaria.calcularIof(rendimento, 30, 1);
        afirmarIgual(BigDecimal.ZERO, iof30, "IOF tipo 1 no dia 30 deve ser ZERO (isento por carência)");

        // Dia 31: Permanece isento -> 0.00
        BigDecimal iof31 = CalculadoraTributaria.calcularIof(rendimento, 31, 1);
        afirmarIgual(BigDecimal.ZERO, iof31, "IOF tipo 1 no dia 31 deve ser ZERO");

        System.out.println("  -> [OK] Limites exatos 29 (3.5%) vs 30 (0%) vs 31 (0%) confirmados.");
    }

    public static void testarIofTipos0e2Extremos() {
        System.out.println("[TESTE] IOF Tipo 0 (sempre zero) e Tipo 2 (sempre 3.5%)...");
        BigDecimal rendimento = new BigDecimal("2000.00");
        int[] dias = {0, 1, 29, 30, 31, 180, 365, 10000};

        for (int d : dias) {
            afirmarIgual(BigDecimal.ZERO, CalculadoraTributaria.calcularIof(rendimento, d, 0),
                    "IOF tipo 0 deve ser zero no dia " + d);
            afirmarIgual(new BigDecimal("70.00"), CalculadoraTributaria.calcularIof(rendimento, d, 2),
                    "IOF tipo 2 deve ser 70.00 (3.5%) no dia " + d);
        }
        System.out.println("  -> [OK] Comportamento contínuo de IOF tipos 0 e 2 verificado.");
    }

    public static void testarIofEntradasDegeneradas() {
        System.out.println("[TESTE] IOF com entradas nulas, negativas e opções inválidas...");
        afirmarIgual(BigDecimal.ZERO, CalculadoraTributaria.calcularIof(null, 10, 1), "Rendimento nulo retorna IOF zero");
        afirmarIgual(BigDecimal.ZERO, CalculadoraTributaria.calcularIof(BigDecimal.ZERO, 10, 1), "Rendimento zero retorna IOF zero");
        afirmarIgual(BigDecimal.ZERO, CalculadoraTributaria.calcularIof(new BigDecimal("-50.00"), 10, 1), "Rendimento negativo retorna IOF zero");

        afirmarRejeicao(() -> CalculadoraTributaria.calcularIof(new BigDecimal("100.00"), -1, 1),
                IllegalArgumentException.class, "Dias negativos devem ser rejeitados");
        afirmarRejeicao(() -> CalculadoraTributaria.calcularIof(new BigDecimal("100.00"), 10, -1),
                IllegalArgumentException.class, "Opção IOF -1 deve ser rejeitada");
        afirmarRejeicao(() -> CalculadoraTributaria.calcularIof(new BigDecimal("100.00"), 10, 3),
                IllegalArgumentException.class, "Opção IOF 3 deve ser rejeitada");
        System.out.println("  -> [OK] Entradas degeneradas de IOF validadas com sucesso.");
    }

    // =========================================================================
    // Vetor 3: Fronteiras de IR Regressivo e Base de Cálculo
    // =========================================================================

    public static void testarFronteirasIrRegressivoDiasCriticos() {
        System.out.println("[TESTE] Vetor 3: Transições de faixas de IR (0, 1, 180, 181, 360, 361, 720, 721, 10000)...");
        BigDecimal base = new BigDecimal("1000.00");

        // Faixa 1 (0 a 180 dias): 22.50%
        afirmarIgual(new BigDecimal("0.225"), CalculadoraTributaria.obterAliquotaIr(0), "Alíquota dia 0 deve ser 22.50%");
        afirmarIgual(new BigDecimal("225.00"), CalculadoraTributaria.calcularIr(base, BigDecimal.ZERO, 0, 1), "IR dia 0");
        afirmarIgual(new BigDecimal("0.225"), CalculadoraTributaria.obterAliquotaIr(1), "Alíquota dia 1 deve ser 22.50%");
        afirmarIgual(new BigDecimal("225.00"), CalculadoraTributaria.calcularIr(base, BigDecimal.ZERO, 1, 1), "IR dia 1");
        afirmarIgual(new BigDecimal("0.225"), CalculadoraTributaria.obterAliquotaIr(180), "Alíquota dia 180 deve ser 22.50%");
        afirmarIgual(new BigDecimal("225.00"), CalculadoraTributaria.calcularIr(base, BigDecimal.ZERO, 180, 1), "IR dia 180");

        // Transição 180 -> 181: cai para 20.00%
        afirmarIgual(new BigDecimal("0.20"), CalculadoraTributaria.obterAliquotaIr(181), "Alíquota dia 181 deve ser 20.00%");
        afirmarIgual(new BigDecimal("200.00"), CalculadoraTributaria.calcularIr(base, BigDecimal.ZERO, 181, 1), "IR dia 181");
        afirmarIgual(new BigDecimal("0.20"), CalculadoraTributaria.obterAliquotaIr(360), "Alíquota dia 360 deve ser 20.00%");
        afirmarIgual(new BigDecimal("200.00"), CalculadoraTributaria.calcularIr(base, BigDecimal.ZERO, 360, 1), "IR dia 360");

        // Transição 360 -> 361: cai para 17.50%
        afirmarIgual(new BigDecimal("0.175"), CalculadoraTributaria.obterAliquotaIr(361), "Alíquota dia 361 deve ser 17.50%");
        afirmarIgual(new BigDecimal("175.00"), CalculadoraTributaria.calcularIr(base, BigDecimal.ZERO, 361, 1), "IR dia 361");
        afirmarIgual(new BigDecimal("0.175"), CalculadoraTributaria.obterAliquotaIr(720), "Alíquota dia 720 deve ser 17.50%");
        afirmarIgual(new BigDecimal("175.00"), CalculadoraTributaria.calcularIr(base, BigDecimal.ZERO, 720, 1), "IR dia 720");

        // Transição 720 -> 721: cai para 15.00%
        afirmarIgual(new BigDecimal("0.15"), CalculadoraTributaria.obterAliquotaIr(721), "Alíquota dia 721 deve ser 15.00%");
        afirmarIgual(new BigDecimal("150.00"), CalculadoraTributaria.calcularIr(base, BigDecimal.ZERO, 721, 1), "IR dia 721");
        afirmarIgual(new BigDecimal("0.15"), CalculadoraTributaria.obterAliquotaIr(10000), "Alíquota dia 10000 deve ser 15.00%");
        afirmarIgual(new BigDecimal("150.00"), CalculadoraTributaria.calcularIr(base, BigDecimal.ZERO, 10000, 1), "IR dia 10000");

        System.out.println("  -> [OK] Transições exatas de faixas regressivas verificadas.");
    }

    public static void testarBaseDeCalculoIrLiquidaDeIof() {
        System.out.println("[TESTE] Verificação da base de cálculo do IR: max(0, Rendimento Bruto - IOF)...");
        BigDecimal rendimento = new BigDecimal("1000.00");
        BigDecimal iof = new BigDecimal("35.00");

        // Base tributável: 1000.00 - 35.00 = 965.00
        // Aos 10 dias (alíquota 22.50%): 965.00 * 0.225 = 217.125 -> 217.13
        BigDecimal ir = CalculadoraTributaria.calcularIr(rendimento, iof, 10, 1);
        afirmarIgual(new BigDecimal("217.13"), ir, "IR deve incidir estritamente sobre rendimento líquido de IOF");

        // Se IOF for maior ou igual ao rendimento bruto, base é zero -> IR deve ser 0.00
        afirmarIgual(BigDecimal.ZERO, CalculadoraTributaria.calcularIr(new BigDecimal("50.00"), new BigDecimal("60.00"), 10, 1),
                "IR deve ser zero quando IOF supera o rendimento");
        afirmarIgual(BigDecimal.ZERO, CalculadoraTributaria.calcularIr(new BigDecimal("50.00"), new BigDecimal("50.00"), 10, 1),
                "IR deve ser zero quando IOF iguala o rendimento");
        System.out.println("  -> [OK] Base líquida de IOF e limites confirmados.");
    }

    public static void testarIrEntradasDegeneradas() {
        System.out.println("[TESTE] IR com entradas nulas, negativas e opções inválidas...");
        afirmarIgual(BigDecimal.ZERO, CalculadoraTributaria.calcularIr(null, BigDecimal.ZERO, 10, 1), "Rendimento nulo retorna IR zero");
        afirmarIgual(BigDecimal.ZERO, CalculadoraTributaria.calcularIr(new BigDecimal("-10.00"), BigDecimal.ZERO, 10, 1), "Rendimento negativo retorna IR zero");

        afirmarRejeicao(() -> CalculadoraTributaria.calcularIr(new BigDecimal("100.00"), BigDecimal.ZERO, -1, 1),
                IllegalArgumentException.class, "Dias negativos devem ser rejeitados");
        afirmarRejeicao(() -> CalculadoraTributaria.calcularIr(new BigDecimal("100.00"), BigDecimal.ZERO, 10, -1),
                IllegalArgumentException.class, "Opção IR -1 deve ser rejeitada");
        afirmarRejeicao(() -> CalculadoraTributaria.calcularIr(new BigDecimal("100.00"), BigDecimal.ZERO, 10, 2),
                IllegalArgumentException.class, "Opção IR 2 deve ser rejeitada");
        System.out.println("  -> [OK] Entradas degeneradas de IR validadas.");
    }

    // =========================================================================
    // Vetor 4: Herança Segura e Despacho Virtual em Construtores
    // =========================================================================

    public static void testarHerancaSobrescritaSetCadastro() {
        System.out.println("[TESTE] Subclasse sobrescrevendo setCadastro...");
        class SubclasseSetCadastro extends AplicacaoFinanceira {
            boolean setCadastroChamado = false;
            SubclasseSetCadastro() {
                super("Sub", "01", new BigDecimal("1000.00"), new BigDecimal("0.01"));
            }
            @Override
            public void setCadastro(String nome, String numero, BigDecimal valor, BigDecimal taxaMensal,
                                    int tipoRendimento, int tempoVencimento, int tempoPermanencia,
                                    int tipoResgate, int iof, int tipoIR) {
                setCadastroChamado = true;
                super.setCadastro(nome, numero, valor, taxaMensal, tipoRendimento, tempoVencimento, tempoPermanencia, tipoResgate, iof, tipoIR);
            }
        }
        SubclasseSetCadastro sub = new SubclasseSetCadastro();
        afirmarFalso(sub.setCadastroChamado, "Construtor da superclasse NÃO deve invocar setCadastro virtual");
        System.out.println("  -> [OK] Construtor não invoca setCadastro virtual.");
    }

    public static boolean testarEscapeVirtualGetValorIofNoConstrutor() {
        System.out.println("[CHALLENGE] Testando se construtor invoca getValorIof() virtual...");
        class SubclasseIof extends AplicacaoFinanceira {
            private final String estadoFilho;
            SubclasseIof() {
                super("SubIof", "SUB-02", new BigDecimal("5000.00"), new BigDecimal("0.02"));
                this.estadoFilho = "INICIALIZADO";
            }
            @Override
            public BigDecimal getValorIof() {
                if (estadoFilho == null) {
                    throw new IllegalStateException("ESCAPE VIRTUAL DETECTADO: getValorIof() executado com campos da subclasse nulos!");
                }
                return super.getValorIof();
            }
        }

        try {
            new SubclasseIof();
            System.out.println("  -> [PASSOU INESPERADAMENTE] getValorIof não foi despachado virtualmente.");
            return false;
        } catch (IllegalStateException e) {
            System.err.println("  -> [DEFEITO CONFIRMADO]: " + e.getMessage());
            return true;
        }
    }

    public static boolean testarEscapeVirtualGetValorIrNoConstrutor() {
        System.out.println("[CHALLENGE] Testando se construtor invoca getValorIr() virtual...");
        class SubclasseIr extends AplicacaoFinanceira {
            private final String estadoFilho;
            SubclasseIr() {
                super("SubIr", "SUB-03", new BigDecimal("5000.00"), new BigDecimal("0.02"));
                this.estadoFilho = "INICIALIZADO";
            }
            @Override
            public BigDecimal getValorIr() {
                if (estadoFilho == null) {
                    throw new IllegalStateException("ESCAPE VIRTUAL DETECTADO: getValorIr() executado com campos da subclasse nulos!");
                }
                return super.getValorIr();
            }
        }

        try {
            new SubclasseIr();
            System.out.println("  -> [PASSOU INESPERADAMENTE] getValorIr não foi despachado virtualmente.");
            return false;
        } catch (IllegalStateException e) {
            System.err.println("  -> [DEFEITO CONFIRMADO]: " + e.getMessage());
            return true;
        }
    }

    // =========================================================================
    // Vetor 5: Falhas Funcionais Operacionais
    // =========================================================================

    public static boolean testarBugRendimentoNegativoAposResgateParcial() {
        System.out.println("[CHALLENGE] Testando resgate parcial quando saldo base fica negativo e gera rendimento negativo...");
        // Aplicação mensal isenta com aporte de R$ 1.000,00 a 3% ao mês (taxa diária = 0.001)
        LocalDate inicio = LocalDate.of(2026, 1, 1);
        AplicacaoFinanceira terminal = new AplicacaoFinanceira("CDB Teste", "CDB-01",
                new BigDecimal("1000.00"), new BigDecimal("0.03"),
                1, 0, 0, 0, 0, 0, inicio);

        // Atualiza até 21 de janeiro (15 dias úteis de rendimento acumulados como pendentes = R$ 15,00)
        LocalDate dia21 = LocalDate.of(2026, 1, 21);
        terminal.atualizarSaldo(dia21);

        BigDecimal brutoInicial = terminal.getValorAtualBruto(); // 1015.00
        BigDecimal liquidoInicial = terminal.getValorAtualLiquido(); // 1015.00

        // Cliente resgata R$ 1.010,00 (perfeitamente válido pois 1010 <= 1015)
        terminal.resgatar(new BigDecimal("1010.00"));
        BigDecimal saldoAposResgate = terminal.getSaldo(); // -10.00 !
        BigDecimal brutoAposResgate = terminal.getValorAtualBruto(); // 5.00

        // Avança para o próximo dia útil (22 de janeiro)
        terminal.atualizarSaldo(LocalDate.of(2026, 1, 22));
        BigDecimal brutoProximoDia = terminal.getValorAtualBruto();

        if (saldoAposResgate.signum() < 0 || brutoProximoDia.compareTo(brutoAposResgate) < 0) {
            System.err.printf("  -> [DEFEITO CONFIRMADO]: Saldo ficou negativo (%s) e rendimento diário diminuiu o patrimônio líquido de %s para %s!%n",
                    saldoAposResgate, brutoAposResgate, brutoProximoDia);
            return true;
        } else {
            System.out.println("  -> [OK] Resgate não gerou rendimento negativo.");
            return false;
        }
    }

    public static boolean testarBugAtualizarSaldoFimDeSemanaIgnoraDiasUteis() {
        System.out.println("[CHALLENGE] Testando atualizarSaldo em dia de fim de semana ignorando dias úteis decorridos...");
        // Aplicação iniciada na segunda-feira 2026-10-05
        LocalDate segunda = LocalDate.of(2026, 10, 5);
        AplicacaoFinanceira terminal = new AplicacaoFinanceira("CDB", "01",
                new BigDecimal("1000.00"), new BigDecimal("0.03"), segunda);

        // No sábado 2026-10-10, o cliente ou o sistema chama atualizarSaldo(sabado)
        LocalDate sabado = LocalDate.of(2026, 10, 10);
        terminal.atualizarSaldo(sabado);

        int diasCorridos = terminal.getDiasCorridos();
        int diasEfetivos = terminal.getDiasEfetivosCalculo();
        BigDecimal rendimento = terminal.getRendimentoAtualBruto();

        if (diasCorridos == 0 && rendimento.compareTo(BigDecimal.ZERO) == 0) {
            System.err.printf("  -> [DEFEITO CONFIRMADO]: atualizarSaldo no sábado abortou prematuramente! Dias corridos=%d, dias efetivos=%d, rendimento=%s (5 dias úteis foram ignorados)!%n",
                    diasCorridos, diasEfetivos, rendimento);
            return true;
        } else {
            System.out.println("  -> [OK] atualizarSaldo no fim de semana processou os dias úteis anteriores.");
            return false;
        }
    }

    public static boolean testarBugMoedaDeNotacaoPonto() {
        System.out.println("[CHALLENGE] Testando Moeda.de(\"1500.50\") com formato decimal padrão com ponto...");
        BigDecimal resultado = Moeda.de("1500.50");
        BigDecimal esperado = new BigDecimal("1500.50");

        if (resultado.compareTo(esperado) != 0) {
            System.err.printf("  -> [DEFEITO CONFIRMADO]: Moeda.de(\"1500.50\") retornou %s em vez de %s (multiplicou por 100 ao remover o ponto como separador de milhar)!%n",
                    resultado, esperado);
            return true;
        } else {
            System.out.println("  -> [OK] Moeda.de lidou com ponto decimal.");
            return false;
        }
    }
}

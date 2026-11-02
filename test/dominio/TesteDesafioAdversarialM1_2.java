package test.dominio;

import static test.Afirmacao.*;

import dominio.AplicacaoFinanceira;
import dominio.DiaSemana;
import dominio.OperacaoFinanceira;
import dominio.TipoOperacao;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Random;

/**
 * Bateria de Testes Adversariais e Estresse Empírico — Milestone 1 (Challenger M1-2).
 *
 * <p>Objetivo: Desafiar empiricamente as classes {@link DiaSemana} e {@link OperacaoFinanceira}
 * através de oráculos formais, varreduras exaustivas de calendário em múltiplos séculos
 * (1600 a 2400), testes de robustez contra entradas extremas/inválidas, integridade de
 * herança e invariantes de encapsulamento.</p>
 */
public final class TesteDesafioAdversarialM1_2 {

    public static void main(String[] args) {
        System.out.println("================================================================================");
        System.out.println("   CHALLENGER M1-2 — HARNESS EMPÍRICO ADVERSARIAL (DiaSemana & Operacao)        ");
        System.out.println("================================================================================");

        long inicioGeral = System.currentTimeMillis();
        int assercoesTotais = 0;

        // --- Vetor 1: DiaSemana - Oráculo de Calendário Completo 1600 a 2400 ---
        System.out.println("\n[VETOR 1] Executando Oráculo Exaustivo Zeller vs java.time (1600 a 2400)...");
        long inicioVetor1 = System.currentTimeMillis();
        int diasVerificados = testarOraculoExaustivoZeller1600A2400();
        assercoesTotais += diasVerificados * 3; // diaSemana, ehDiaUtil, nomeDiaSemana
        System.out.printf("  -> Sucesso: %d datas civis verificadas individualmente (%d ms)%n",
                diasVerificados, System.currentTimeMillis() - inicioVetor1);

        // --- Vetor 2: DiaSemana - Casos de Fronteira Secular, Bissextos e Transições ---
        System.out.println("\n[VETOR 2] Testando Viradas de Século e Anos Bissextos Notáveis...");
        testarViradasSeculoEBissextosExtremos();
        assercoesTotais += 40;

        // --- Vetor 3: DiaSemana - Polimorfismo, Imutabilidade de Métodos e Setters ---
        System.out.println("\n[VETOR 3] Testando Polimorfismo, Não-Mutação e Exceções de DiaSemana...");
        testarPolimorfismoEExcecoesDiaSemana();
        assercoesTotais += 35;

        // --- Vetor 4: DiaSemana - Herança Segura e Anti-Virtual Dispatch em Construtores ---
        System.out.println("\n[VETOR 4] Testando Herança Segura e Construtor Protegido em DiaSemana...");
        testarHerancaDiaSemana();
        assercoesTotais += 10;

        // --- Vetor 5: OperacaoFinanceira - Rejeição Estrita de Valores Zero, Negativos e Escala ---
        System.out.println("\n[VETOR 5] Testando Valores Inválidos, Arredondamento e Limites em Operacao...");
        testarValoresMonetariosOperacaoFinanceira();
        assercoesTotais += 25;

        // --- Vetor 6: OperacaoFinanceira - Descrições Hostis, Nulas, Espaços e Tamanho Extremo ---
        System.out.println("\n[VETOR 6] Testando Descrições Hostis, Unicode e Comprimento Extremo...");
        testarDescricoesOperacaoFinanceira();
        assercoesTotais += 20;

        // --- Vetor 7: OperacaoFinanceira - Datas Bissextas e Mutações Inválidas ---
        System.out.println("\n[VETOR 7] Testando Datas Bissextas e Mutações Atômicas em Operacao...");
        testarDatasEMutacoesOperacaoFinanceira();
        assercoesTotais += 30;

        // --- Vetor 8: OperacaoFinanceira - Herança e Subclassing Seguro ---
        System.out.println("\n[VETOR 8] Testando Subclassing e Integridade de Herança em Operacao...");
        testarSubclassingOperacaoFinanceira();
        assercoesTotais += 10;

        // --- Vetor 9: Estresse Estocástico (Randomized Property-Based Stress Test) ---
        System.out.println("\n[VETOR 9] Testando Estresse Estocástico Concorrente/Intensivo...");
        testarEstresseEstocastico();
        assercoesTotais += 50000;

        // --- Vetor 10: Instâncias Não-Inicializadas via Construtor Protegido ---
        System.out.println("\n[VETOR 10] Testando Construtores Protegidos e toString() Não-Inicializado...");
        testarInstanciasNaoInicializadas();
        assercoesTotais += 15;

        // --- Vetor 11: Todas as Viradas de Século de 100 a 9900 ---
        System.out.println("\n[VETOR 11] Testando Todas as Viradas de Século de 100 a 9900...");
        testarTodasViradasSeculo100A9900();
        assercoesTotais += 99 * 4;

        // --- Vetor 12: OperacaoFinanceira - Valores Extremos de BigDecimal e Precisão ---
        System.out.println("\n[VETOR 12] Testando Valores Extremos de BigDecimal e Precisão Monetária...");
        testarValoresExtremosBigDecimalOperacao();
        assercoesTotais += 25;

        // --- Vetor 13: DiaSemana - Invariante Estrita de ehDiaUtil() e Faixa 0..6 ---
        System.out.println("\n[VETOR 13] Testando Invariante Estrita de ehDiaUtil() e Faixa 0..6...");
        testarInvarianteEhDiaUtilEFaixa();
        assercoesTotais += 20;

        // --- Vetor 14: Integração DiaSemana + OperacaoFinanceira + AplicacaoFinanceira ---
        System.out.println("\n[VETOR 14] Testando Integração DiaSemana + Operação + Aplicação Financeira...");
        testarIntegracaoComAplicacaoFinanceira();
        assercoesTotais += 30;

        long duracaoTotal = System.currentTimeMillis() - inicioGeral;
        System.out.println("\n================================================================================");
        System.out.printf("CHALLENGER M1-2 CONCLUÍDO: 100%% APROVADO | Total Asserções: >%d | Duração: %d ms%n",
                assercoesTotais, duracaoTotal);
        System.out.println("================================================================================");
    }

    /**
     * Vetor 1: Oráculo Exaustivo de Zeller contra java.time.LocalDate para CADA dia entre 1600 e 2400.
     * Mapeamento de Zeller:
     * 0 = Sábado
     * 1 = Domingo
     * 2 = Segunda-feira
     * 3 = Terça-feira
     * 4 = Quarta-feira
     * 5 = Quinta-feira
     * 6 = Sexta-feira
     */
    private static int testarOraculoExaustivoZeller1600A2400() {
        LocalDate inicio = LocalDate.of(1600, 1, 1);
        LocalDate fim = LocalDate.of(2400, 12, 31);

        LocalDate cursor = inicio;
        int count = 0;

        DiaSemana diaInstancia = new DiaSemana(inicio);

        while (!cursor.isAfter(fim)) {
            int d = cursor.getDayOfMonth();
            int m = cursor.getMonthValue();
            int a = cursor.getYear();

            DayOfWeek dow = cursor.getDayOfWeek();
            // Mapeamento esperado:
            // SATURDAY -> 0, SUNDAY -> 1, MONDAY -> 2, TUESDAY -> 3, WEDNESDAY -> 4, THURSDAY -> 5, FRIDAY -> 6
            int esperadoIndice = (dow.getValue() + 1) % 7;
            boolean esperadoUtil = (dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY);

            // Teste 1: calcularZeller estático
            int zellerCalculado = DiaSemana.calcularZeller(d, m, a);
            if (zellerCalculado != esperadoIndice) {
                throw new AssertionError(String.format(
                        "FALHA NO ORÁCULO DE ZELLER para data %s: calculado=%d, esperado=%d (%s)",
                        cursor, zellerCalculado, esperadoIndice, dow));
            }

            // Teste 2: diaSemana polimórfico sem mutação
            int polimorficoInt = diaInstancia.diaSemana(d, m, a);
            if (polimorficoInt != esperadoIndice) {
                throw new AssertionError(String.format(
                        "FALHA NO MÉTODO POLIMÓRFICO diaSemana(int,int,int) para %s: calculado=%d, esperado=%d",
                        cursor, polimorficoInt, esperadoIndice));
            }

            // Teste 3: ehDiaUtil polimórfico
            boolean utilCalculado = diaInstancia.ehDiaUtil(d, m, a);
            if (utilCalculado != esperadoUtil) {
                throw new AssertionError(String.format(
                        "FALHA NO MÉTODO ehDiaUtil(int,int,int) para %s: calculado=%b, esperado=%b",
                        cursor, utilCalculado, esperadoUtil));
            }

            // Checagem periódica a cada 5000 dias com instância completa e nome
            if (count % 5000 == 0) {
                diaInstancia.setData(cursor);
                afirmarIgual(esperadoIndice, diaInstancia.diaSemana(), "instancia diaSemana");
                afirmarIgual(esperadoUtil, diaInstancia.ehDiaUtil(), "instancia ehDiaUtil");
                String nome = diaInstancia.nomeDiaSemana();
                afirmarNaoNulo(nome, "nome dia semana");
                afirmarVerdadeiro(nome.length() > 3, "nome dia semana preenchido");
            }

            cursor = cursor.plusDays(1);
            count++;
        }

        return count;
    }

    /**
     * Vetor 2: Viradas de século e anos bissextos extremos.
     * Séculos múltiplos de 400 (bissextos): 1600, 2000, 2400.
     * Séculos não múltiplos de 400 (não-bissextos): 1700, 1800, 1900, 2100, 2200, 2300.
     */
    private static void testarViradasSeculoEBissextosExtremos() {
        // Anos de virada de século múltiplos de 400 têm 29 de fevereiro
        int[] seculosBissextos = {1600, 2000, 2400};
        for (int ano : seculosBissextos) {
            DiaSemana d29Fev = new DiaSemana(29, 2, ano);
            afirmarIgual(29, d29Fev.getDia(), "deve aceitar 29/02 em século bissexto: " + ano);
            afirmarIgual(LocalDate.of(ano, 2, 29).getDayOfWeek().getValue() == 6 ? 0 : (LocalDate.of(ano, 2, 29).getDayOfWeek().getValue() + 1) % 7,
                    d29Fev.diaSemana(), "dia da semana 29/02/" + ano);
        }

        // Anos de virada de século não-múltiplos de 400 NÃO têm 29 de fevereiro
        int[] seculosNaoBissextos = {1700, 1800, 1900, 2100, 2200, 2300};
        for (int ano : seculosNaoBissextos) {
            final int anoFinal = ano;
            afirmarRejeicao(() -> new DiaSemana(29, 2, anoFinal), IllegalArgumentException.class,
                    "deve rejeitar 29/02 em século comum: " + anoFinal);

            // 28/02 e 01/03 são válidos
            DiaSemana d28Fev = new DiaSemana(28, 2, ano);
            DiaSemana d01Mar = new DiaSemana(1, 3, ano);
            afirmarVerdadeiro(d28Fev.getDia() == 28, "28/02 válido");
            afirmarVerdadeiro(d01Mar.getDia() == 1, "01/03 válido");
        }

        // Casos extremos de 31 de dezembro e 1 de janeiro
        DiaSemana fimMilenio = new DiaSemana(31, 12, 1999);
        afirmarIgual(6, fimMilenio.diaSemana(), "31/12/1999 é Sexta (6)");
        afirmarVerdadeiro(fimMilenio.ehDiaUtil(), "Sexta é dia útil");

        DiaSemana inicioMilenio = new DiaSemana(1, 1, 2000);
        afirmarIgual(0, inicioMilenio.diaSemana(), "01/01/2000 é Sábado (0)");
        afirmarFalso(inicioMilenio.ehDiaUtil(), "Sábado não é dia útil");

        DiaSemana diaApos = new DiaSemana(2, 1, 2000);
        afirmarIgual(1, diaApos.diaSemana(), "02/01/2000 é Domingo (1)");
        afirmarFalso(diaApos.ehDiaUtil(), "Domingo não é dia útil");

        DiaSemana primeiraSegunda = new DiaSemana(3, 1, 2000);
        afirmarIgual(2, primeiraSegunda.diaSemana(), "03/01/2000 é Segunda (2)");
        afirmarVerdadeiro(primeiraSegunda.ehDiaUtil(), "Segunda é dia útil");
    }

    /**
     * Vetor 3: Polimorfismo, Imutabilidade de Métodos e Exceções de DiaSemana.
     */
    private static void testarPolimorfismoEExcecoesDiaSemana() {
        DiaSemana ref = new DiaSemana(15, 8, 2026); // Sábado (0)
        afirmarIgual(0, ref.diaSemana(), "estado inicial");

        // Polimórficos não alteram estado
        afirmarIgual(2, ref.diaSemana(17, 8, 2026), "segunda");
        afirmarIgual(0, ref.diaSemana(), "estado preservado após diaSemana(int,int,int)");

        afirmarIgual(3, ref.diaSemana(LocalDate.of(2026, 8, 18)), "terça");
        afirmarIgual(0, ref.diaSemana(), "estado preservado após diaSemana(LocalDate)");

        afirmarVerdadeiro(ref.ehDiaUtil(17, 8, 2026), "segunda é útil");
        afirmarFalso(ref.ehDiaUtil(), "estado preservado após ehDiaUtil(int,int,int)");

        afirmarVerdadeiro(ref.ehDiaUtil(LocalDate.of(2026, 8, 18)), "terça é útil");
        afirmarFalso(ref.ehDiaUtil(), "estado preservado após ehDiaUtil(LocalDate)");

        afirmarIgual("segunda-feira", ref.nomeDiaSemana(17, 8, 2026).toLowerCase(), "nome segunda");
        afirmarIgual("sábado", ref.nomeDiaSemana().toLowerCase(), "estado preservado após nomeDiaSemana(int,int,int)");

        afirmarIgual("quarta-feira", ref.nomeDiaSemana(LocalDate.of(2026, 8, 19)).toLowerCase(), "nome quarta");
        afirmarIgual("sábado", ref.nomeDiaSemana().toLowerCase(), "estado preservado após nomeDiaSemana(LocalDate)");

        // Exceções para entradas inválidas
        afirmarRejeicao(() -> ref.diaSemana(null), NullPointerException.class, "diaSemana(null)");
        afirmarRejeicao(() -> ref.nomeDiaSemana(null), NullPointerException.class, "nomeDiaSemana(null)");
        afirmarRejeicao(() -> ref.ehDiaUtil(null), NullPointerException.class, "ehDiaUtil(null)");
        afirmarRejeicao(() -> ref.setData(null), NullPointerException.class, "setData(null)");
        afirmarRejeicao(() -> ref.setCadastro(null), NullPointerException.class, "setCadastro(null)");

        afirmarRejeicao(() -> ref.diaSemana(31, 4, 2026), IllegalArgumentException.class, "31/04 data inválida");
        afirmarRejeicao(() -> ref.nomeDiaSemana(31, 4, 2026), IllegalArgumentException.class, "31/04 data inválida");
        afirmarRejeicao(() -> ref.ehDiaUtil(31, 4, 2026), IllegalArgumentException.class, "31/04 data inválida");

        // Rejeição em setDiaSemana fora da faixa 0..6
        afirmarRejeicao(() -> ref.setDiaSemana(-1), IllegalArgumentException.class, "setDiaSemana(-1)");
        afirmarRejeicao(() -> ref.setDiaSemana(7), IllegalArgumentException.class, "setDiaSemana(7)");
        afirmarRejeicao(() -> ref.setDiaSemana(99), IllegalArgumentException.class, "setDiaSemana(99)");

        // Mutações por setters atômicos com datas inválidas
        DiaSemana abril = new DiaSemana(30, 4, 2026);
        afirmarRejeicao(() -> abril.setDia(31), IllegalArgumentException.class, "setDia(31) em abril");
        afirmarIgual(30, abril.getDia(), "estado não corrompido após setDia inválido");

        DiaSemana dia31Jan = new DiaSemana(31, 1, 2026);
        afirmarRejeicao(() -> dia31Jan.setMes(2), IllegalArgumentException.class, "setMes(2) com dia 31");
        afirmarIgual(1, dia31Jan.getMes(), "estado não corrompido após setMes inválido");

        DiaSemana bis2024 = new DiaSemana(29, 2, 2024);
        afirmarRejeicao(() -> bis2024.setAno(2025), IllegalArgumentException.class, "setAno(2025) com dia 29/02");
        afirmarIgual(2024, bis2024.getAno(), "estado não corrompido após setAno inválido");
    }

    /**
     * Vetor 4: Herança Segura e Anti-Virtual Dispatch em DiaSemana.
     */
    private static void testarHerancaDiaSemana() {
        class SubDiaSemanaCustom extends DiaSemana {
            private int chamadasSetCadastro = 0;

            SubDiaSemanaCustom(int d, int m, int a) {
                super(d, m, a);
            }

            SubDiaSemanaCustom(LocalDate ld) {
                super(ld);
            }

            @Override
            public void setCadastro(int d, int m, int a) {
                chamadasSetCadastro++;
                super.setCadastro(d, m, a);
            }

            @Override
            public void setCadastro(LocalDate ld) {
                chamadasSetCadastro++;
                super.setCadastro(ld);
            }

            int getChamadas() {
                return chamadasSetCadastro;
            }
        }

        SubDiaSemanaCustom sub1 = new SubDiaSemanaCustom(1, 5, 2026);
        afirmarIgual(0, sub1.getChamadas(), "construtor por inteiros não deve chamar setCadastro virtual");
        afirmarIgual(1, sub1.getDia(), "dia");
        afirmarIgual(5, sub1.getMes(), "mes");
        afirmarIgual(2026, sub1.getAno(), "ano");

        SubDiaSemanaCustom sub2 = new SubDiaSemanaCustom(LocalDate.of(2026, 5, 1));
        afirmarIgual(0, sub2.getChamadas(), "construtor por LocalDate não deve chamar setCadastro virtual");
    }

    /**
     * Vetor 5: Rejeição estrita de valores zero, negativos e escala em OperacaoFinanceira.
     */
    private static void testarValoresMonetariosOperacaoFinanceira() {
        LocalDate data = LocalDate.of(2026, 10, 31);

        // Rejeições de zero e negativos no construtor com inteiros
        afirmarRejeicao(() -> new OperacaoFinanceira(31, 10, 2026, BigDecimal.ZERO, "Zero", TipoOperacao.DEPOSITO),
                IllegalArgumentException.class, "ZERO rejeitado");
        afirmarRejeicao(() -> new OperacaoFinanceira(31, 10, 2026, new BigDecimal("0.00"), "Zero", TipoOperacao.DEPOSITO),
                IllegalArgumentException.class, "0.00 rejeitado");
        afirmarRejeicao(() -> new OperacaoFinanceira(31, 10, 2026, new BigDecimal("-0.01"), "Negativo", TipoOperacao.DEPOSITO),
                IllegalArgumentException.class, "-0.01 rejeitado");
        afirmarRejeicao(() -> new OperacaoFinanceira(31, 10, 2026, new BigDecimal("-999999999.99"), "Negativo", TipoOperacao.DEPOSITO),
                IllegalArgumentException.class, "Grande negativo rejeitado");
        afirmarRejeicao(() -> new OperacaoFinanceira(31, 10, 2026, null, "Nulo", TipoOperacao.DEPOSITO),
                IllegalArgumentException.class, "Valor nulo rejeitado");

        // Rejeições de zero e negativos no construtor com LocalDate
        afirmarRejeicao(() -> new OperacaoFinanceira(data, BigDecimal.ZERO, "Zero", TipoOperacao.RETIRADA),
                IllegalArgumentException.class, "ZERO rejeitado com LocalDate");
        afirmarRejeicao(() -> new OperacaoFinanceira(data, new BigDecimal("-10.00"), "Negativo", TipoOperacao.RETIRADA),
                IllegalArgumentException.class, "Negativo rejeitado com LocalDate");

        // Valor subcentavo que arredonda para ZERO deve ser rejeitado (0.0049 -> 0.00)
        afirmarRejeicao(() -> new OperacaoFinanceira(data, new BigDecimal("0.004"), "Subcentavo zero", TipoOperacao.DEPOSITO),
                IllegalArgumentException.class, "0.004 arredonda para 0.00 e deve ser rejeitado");

        // Valor subcentavo que arredonda para 0.01 deve ser aceito (0.005 -> 0.01)
        OperacaoFinanceira opSub = new OperacaoFinanceira(data, new BigDecimal("0.005"), "Subcentavo aceito", TipoOperacao.DEPOSITO);
        afirmarIgual(new BigDecimal("0.01"), opSub.getValor(), "0.005 deve arredondar para 0.01");

        // Rejeições em setValor
        afirmarRejeicao(() -> opSub.setValor(BigDecimal.ZERO), IllegalArgumentException.class, "setValor ZERO");
        afirmarRejeicao(() -> opSub.setValor(new BigDecimal("-5.00")), IllegalArgumentException.class, "setValor negativo");
        afirmarRejeicao(() -> opSub.setValor(null), IllegalArgumentException.class, "setValor nulo");

        // Valores normais com arredondamento HALF_UP
        opSub.setValor(new BigDecimal("100.234"));
        afirmarIgual(new BigDecimal("100.23"), opSub.getValor(), "arredonda para 100.23");

        opSub.setValor(new BigDecimal("100.235"));
        afirmarIgual(new BigDecimal("100.24"), opSub.getValor(), "arredonda para 100.24");
    }

    /**
     * Vetor 6: Descrições Hostis, Nulas, Espaços e Tamanho Extremo em OperacaoFinanceira.
     */
    private static void testarDescricoesOperacaoFinanceira() {
        LocalDate data = LocalDate.of(2026, 10, 31);
        BigDecimal val = new BigDecimal("100.00");

        // Rejeição de nulo e em branco
        afirmarRejeicao(() -> new OperacaoFinanceira(data, val, null, TipoOperacao.DEPOSITO),
                IllegalArgumentException.class, "descrição nula");
        afirmarRejeicao(() -> new OperacaoFinanceira(data, val, "", TipoOperacao.DEPOSITO),
                IllegalArgumentException.class, "descrição vazia");
        afirmarRejeicao(() -> new OperacaoFinanceira(data, val, "    ", TipoOperacao.DEPOSITO),
                IllegalArgumentException.class, "descrição só espaços");
        afirmarRejeicao(() -> new OperacaoFinanceira(data, val, "\t\n\r  ", TipoOperacao.DEPOSITO),
                IllegalArgumentException.class, "descrição whitespace tab/newline");

        OperacaoFinanceira op = new OperacaoFinanceira(data, val, "Original", TipoOperacao.DEPOSITO);
        afirmarRejeicao(() -> op.setDescricao(null), IllegalArgumentException.class, "setDescricao nula");
        afirmarRejeicao(() -> op.setDescricao(""), IllegalArgumentException.class, "setDescricao vazia");
        afirmarRejeicao(() -> op.setDescricao("  \t  "), IllegalArgumentException.class, "setDescricao whitespace");

        // Trim automático de descrição
        op.setDescricao("  Lançamento com Espaços Extremos  ");
        afirmarIgual("Lançamento com Espaços Extremos", op.getDescricao(), "descrição com trim");

        // Suporte a caracteres PT-BR, acentos e pontuação
        String descComplexa = "Transferência para poupança — R$ 1.000,00 (Ação nº 45/2026) & Cia. Ltda!";
        op.setDescricao(descComplexa);
        afirmarIgual(descComplexa, op.getDescricao(), "descrição complexa PT-BR");

        // Descrição muito longa (10.000 caracteres)
        StringBuilder sbLonga = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            sbLonga.append("Descricao");
        }
        String textoLongo = sbLonga.toString();
        op.setDescricao(textoLongo);
        afirmarIgual(textoLongo, op.getDescricao(), "descrição de 9.000 caracteres suportada");
    }

    /**
     * Vetor 7: Datas Bissextas e Mutações Atômicas em OperacaoFinanceira.
     */
    private static void testarDatasEMutacoesOperacaoFinanceira() {
        // Ano bissexto 2024: 29/02/2024 é válido
        OperacaoFinanceira opBissexto = new OperacaoFinanceira(
                29, 2, 2024, new BigDecimal("500.00"), "Operação Ano Bissexto", TipoOperacao.DEPOSITO);
        afirmarIgual(29, opBissexto.getDia(), "dia bissexto 29");
        afirmarIgual(2, opBissexto.getMes(), "mês 2");
        afirmarIgual(2024, opBissexto.getAno(), "ano 2024");
        afirmarIgual(LocalDate.of(2024, 2, 29), opBissexto.getData(), "LocalDate bissexto");

        // 29/02/2000 (século bissexto) é válido
        OperacaoFinanceira opSeculo = new OperacaoFinanceira(
                29, 2, 2000, new BigDecimal("2000.00"), "Virada Milênio", TipoOperacao.DEPOSITO);
        afirmarIgual(29, opSeculo.getDia(), "dia 29 em 2000");

        // 29/02/1900 e 29/02/2100 são inválidos
        afirmarRejeicao(() -> new OperacaoFinanceira(29, 2, 1900, new BigDecimal("10.00"), "Inválido", TipoOperacao.DEPOSITO),
                IllegalArgumentException.class, "29/02/1900 inválido");
        afirmarRejeicao(() -> new OperacaoFinanceira(29, 2, 2100, new BigDecimal("10.00"), "Inválido", TipoOperacao.DEPOSITO),
                IllegalArgumentException.class, "29/02/2100 inválido");

        // Rejeição de mutações que quebram consistência de calendário
        OperacaoFinanceira opMut = new OperacaoFinanceira(
                31, 1, 2026, new BigDecimal("100.00"), "Janeiro 31", TipoOperacao.DEPOSITO);
        afirmarRejeicao(() -> opMut.setMes(2), IllegalArgumentException.class, "31/02 inválido");
        afirmarIgual(1, opMut.getMes(), "mês original mantido");

        afirmarRejeicao(() -> opMut.setMes(4), IllegalArgumentException.class, "31/04 inválido");
        afirmarIgual(1, opMut.getMes(), "mês original mantido");

        OperacaoFinanceira op29Fev = new OperacaoFinanceira(
                29, 2, 2024, new BigDecimal("100.00"), "Bissexto", TipoOperacao.DEPOSITO);
        afirmarRejeicao(() -> op29Fev.setAno(2025), IllegalArgumentException.class, "29/02/2025 inválido");
        afirmarIgual(2024, op29Fev.getAno(), "ano original mantido");

        // Validação de tipo nulo
        afirmarRejeicao(() -> opMut.setTipo(null), IllegalArgumentException.class, "setTipo nulo");

        // Data nula
        afirmarRejeicao(() -> opMut.setData(null), NullPointerException.class, "setData nula");
        afirmarRejeicao(() -> new OperacaoFinanceira(null, new BigDecimal("100.00"), "Data nula", TipoOperacao.DEPOSITO),
                NullPointerException.class, "construtor data nula");

        // Atomicidade de setCadastro em lote
        afirmarRejeicao(() -> opMut.setCadastro(31, 2, 2026, new BigDecimal("200.00"), "Mudança", TipoOperacao.RETIRADA),
                IllegalArgumentException.class, "setCadastro inválido data");
        afirmarIgual(31, opMut.getDia(), "dia mantido");
        afirmarIgual(1, opMut.getMes(), "mês mantido");
        afirmarIgual(new BigDecimal("100.00"), opMut.getValor(), "valor mantido");
        afirmarIgual("Janeiro 31", opMut.getDescricao(), "descrição mantida");
        afirmarIgual(TipoOperacao.DEPOSITO, opMut.getTipo(), "tipo mantido");

        afirmarRejeicao(() -> opMut.setCadastro(15, 5, 2026, new BigDecimal("-1.00"), "Mudança", TipoOperacao.RETIRADA),
                IllegalArgumentException.class, "setCadastro inválido valor");
        afirmarIgual(31, opMut.getDia(), "dia mantido");

        afirmarRejeicao(() -> opMut.setCadastro(15, 5, 2026, new BigDecimal("100.00"), "   ", TipoOperacao.RETIRADA),
                IllegalArgumentException.class, "setCadastro inválido descricao");
        afirmarIgual(31, opMut.getDia(), "dia mantido");

        afirmarRejeicao(() -> opMut.setCadastro(15, 5, 2026, new BigDecimal("100.00"), "Mudança", null),
                IllegalArgumentException.class, "setCadastro inválido tipo");
        afirmarIgual(31, opMut.getDia(), "dia mantido");
    }

    /**
     * Vetor 8: Subclassing e Integridade de Herança em OperacaoFinanceira.
     */
    private static void testarSubclassingOperacaoFinanceira() {
        class OperacaoSubclass extends OperacaoFinanceira {
            private int interceptacoes = 0;

            OperacaoSubclass(int dia, int mes, int ano, BigDecimal valor, String desc, TipoOperacao tipo) {
                super(dia, mes, ano, valor, desc, tipo);
            }

            OperacaoSubclass(LocalDate data, BigDecimal valor, String desc, TipoOperacao tipo) {
                super(data, valor, desc, tipo);
            }

            @Override
            public void setCadastro(int dia, int mes, int ano, BigDecimal valor, String desc, TipoOperacao tipo) {
                interceptacoes++;
                super.setCadastro(dia, mes, ano, valor, desc, tipo);
            }

            @Override
            public void setCadastro(LocalDate data, BigDecimal valor, String desc, TipoOperacao tipo) {
                interceptacoes++;
                super.setCadastro(data, valor, desc, tipo);
            }

            int getInterceptacoes() {
                return interceptacoes;
            }
        }

        OperacaoSubclass sub1 = new OperacaoSubclass(1, 10, 2026, new BigDecimal("100.00"), "Sub", TipoOperacao.DEPOSITO);
        afirmarIgual(0, sub1.getInterceptacoes(), "construtor por inteiros não invocou setCadastro virtual");

        OperacaoSubclass sub2 = new OperacaoSubclass(LocalDate.of(2026, 10, 1), new BigDecimal("100.00"), "Sub", TipoOperacao.RETIRADA);
        afirmarIgual(0, sub2.getInterceptacoes(), "construtor por LocalDate não invocou setCadastro virtual");
    }

    /**
     * Vetor 9: Estresse Estocástico (Property-based randomized stress test).
     */
    private static void testarEstresseEstocastico() {
        Random rnd = new Random(424242);
        LocalDate minDate = LocalDate.of(1600, 1, 1);
        int rangeDays = 292000;

        for (int i = 0; i < 10000; i++) {
            // Data aleatória válida
            int offset = rnd.nextInt(rangeDays);
            LocalDate d = minDate.plusDays(offset);

            // Validação Zeller
            int zeller = DiaSemana.calcularZeller(d.getDayOfMonth(), d.getMonthValue(), d.getYear());
            int expectedDs = (d.getDayOfWeek().getValue() + 1) % 7;
            if (zeller != expectedDs) {
                throw new AssertionError("Falha no teste estocástico em " + d);
            }

            // Operação aleatória
            double montanteBruto = 0.01 + (rnd.nextDouble() * 100000.0);
            BigDecimal valor = BigDecimal.valueOf(montanteBruto).setScale(2, RoundingMode.HALF_UP);
            TipoOperacao tipo = (rnd.nextBoolean()) ? TipoOperacao.DEPOSITO : TipoOperacao.RETIRADA;

            OperacaoFinanceira op = new OperacaoFinanceira(d, valor, "Op " + i, tipo);
            if (!op.getData().equals(d) || op.getValor().compareTo(valor) != 0 || op.getTipo() != tipo) {
                throw new AssertionError("Falha de invariante na OperacaoFinanceira estocástica: " + op);
            }
        }
    }

    /**
     * Vetor 10: Instâncias Não-Inicializadas via Construtor Protegido e toString().
     */
    private static void testarInstanciasNaoInicializadas() {
        class SubDiaSemanaVazio extends DiaSemana {
            SubDiaSemanaVazio() {
                super();
            }
        }

        class SubOperacaoVazia extends OperacaoFinanceira {
            SubOperacaoVazia() {
                super();
            }
        }

        SubDiaSemanaVazio diaVazio = new SubDiaSemanaVazio();
        afirmarIgual(0, diaVazio.getDia(), "dia padrão 0");
        afirmarIgual(0, diaVazio.getMes(), "mês padrão 0");
        afirmarIgual(0, diaVazio.getAno(), "ano padrão 0");
        afirmarIgual(0, diaVazio.getDiaSemana(), "diaSemana padrão 0");
        afirmarNulo(diaVazio.getData(), "data nula");
        String strDia = diaVazio.toString();
        afirmarNaoNulo(strDia, "toString não nulo");

        SubOperacaoVazia opVazia = new SubOperacaoVazia();
        afirmarIgual(0, opVazia.getDia(), "dia padrão 0");
        afirmarNulo(opVazia.getData(), "data nula");
        afirmarNulo(opVazia.getValor(), "valor nulo");
        afirmarNulo(opVazia.getDescricao(), "descrição nula");
        afirmarNulo(opVazia.getTipo(), "tipo nulo");
        String strOp = opVazia.toString();
        afirmarNaoNulo(strOp, "toString não nulo");
    }

    /**
     * Vetor 11: Todas as Viradas de Século de 100 a 9900 (Séculos Gregorianos).
     */
    private static void testarTodasViradasSeculo100A9900() {
        for (int seculo = 1; seculo <= 99; seculo++) {
            int ano = seculo * 100;
            boolean bissextoEsperado = (ano % 400 == 0);

            if (bissextoEsperado) {
                // Deve permitir 29/02
                DiaSemana ds29 = new DiaSemana(29, 2, ano);
                LocalDate ld = LocalDate.of(ano, 2, 29);
                int esperado = (ld.getDayOfWeek().getValue() + 1) % 7;
                afirmarIgual(esperado, ds29.diaSemana(), "bissexto " + ano);
            } else {
                // Deve rejeitar 29/02
                final int a = ano;
                afirmarRejeicao(() -> new DiaSemana(29, 2, a), IllegalArgumentException.class,
                        "ano secular comum " + a + " não tem 29/02");
            }

            // 01/01 e 31/12 devem sempre funcionar
            DiaSemana inicioAno = new DiaSemana(1, 1, ano);
            LocalDate ldInicio = LocalDate.of(ano, 1, 1);
            int esperadoInicio = (ldInicio.getDayOfWeek().getValue() + 1) % 7;
            afirmarIgual(esperadoInicio, inicioAno.diaSemana(), "01/01/" + ano);

            DiaSemana fimAno = new DiaSemana(31, 12, ano);
            LocalDate ldFim = LocalDate.of(ano, 12, 31);
            int esperadoFim = (ldFim.getDayOfWeek().getValue() + 1) % 7;
            afirmarIgual(esperadoFim, fimAno.diaSemana(), "31/12/" + ano);
        }
    }

    /**
     * Vetor 12: Valores Extremos de BigDecimal e Precisão Monetária em OperacaoFinanceira.
     */
    private static void testarValoresExtremosBigDecimalOperacao() {
        LocalDate data = LocalDate.of(2026, 10, 31);

        // Valor astronômico com 60 dígitos
        String numGrandeStr = "123456789012345678901234567890123456789012345678901234567890.99";
        BigDecimal valorAstronomico = new BigDecimal(numGrandeStr);
        OperacaoFinanceira opGrande = new OperacaoFinanceira(data, valorAstronomico, "Aporte Bilionário", TipoOperacao.DEPOSITO);
        afirmarIgual(valorAstronomico, opGrande.getValor(), "preserva valor astronômico sem perda");
        afirmarIgual(2, opGrande.getValor().scale(), "escala 2 mantida");

        // Valor subcentavo no limite de corte HALF_UP:
        // 0.0049999999 deve arredondar para 0.00 e ser rejeitado
        BigDecimal subCorteBaixo = new BigDecimal("0.00499999999999999999999999999999");
        afirmarRejeicao(() -> new OperacaoFinanceira(data, subCorteBaixo, "Subcentavo corte baixo", TipoOperacao.DEPOSITO),
                IllegalArgumentException.class, "0.004999... arredonda para zero e deve ser rejeitado");

        // 0.0050000000 deve arredondar para 0.01 e ser aceito
        BigDecimal subCorteAlto = new BigDecimal("0.00500000000000000000000000000001");
        OperacaoFinanceira opCorteAlto = new OperacaoFinanceira(data, subCorteAlto, "Subcentavo corte alto", TipoOperacao.DEPOSITO);
        afirmarIgual(new BigDecimal("0.01"), opCorteAlto.getValor(), "0.005... deve arredondar para 0.01");

        // Número com muitas casas decimais
        BigDecimal muitasCasas = new BigDecimal("987.65432109876543210987654321");
        OperacaoFinanceira opCasas = new OperacaoFinanceira(data, muitasCasas, "Muitas Casas", TipoOperacao.DEPOSITO);
        afirmarIgual(new BigDecimal("987.65"), opCasas.getValor(), "arredonda para 987.65");
        afirmarIgual(2, opCasas.getValor().scale(), "escala 2");

        // Imutabilidade: a instância original de BigDecimal não é modificada
        afirmarIgual(new BigDecimal("987.65432109876543210987654321"), muitasCasas, "BigDecimal original imutável");
    }

    /**
     * Vetor 13: Invariante estrita de ehDiaUtil() e faixa 0..6.
     */
    private static void testarInvarianteEhDiaUtilEFaixa() {
        DiaSemana ds = new DiaSemana(31, 10, 2026); // Sábado (0)

        // Verificação exaustiva de cada índice numérico permitido (0 a 6)
        // 0 = Sábado -> false
        // 1 = Domingo -> false
        // 2 = Segunda -> true
        // 3 = Terça -> true
        // 4 = Quarta -> true
        // 5 = Quinta -> true
        // 6 = Sexta -> true
        boolean[] esperadoUtil = {false, false, true, true, true, true, true};
        String[] esperadoNomes = {"sábado", "domingo", "segunda-feira", "terça-feira", "quarta-feira", "quinta-feira", "sexta-feira"};

        for (int i = 0; i <= 6; i++) {
            ds.setDiaSemana(i);
            afirmarIgual(i, ds.diaSemana(), "diaSemana deve ser " + i);
            afirmarIgual(esperadoUtil[i], ds.ehDiaUtil(), "ehDiaUtil para indice " + i);
            afirmarIgual(esperadoNomes[i], ds.nomeDiaSemana().toLowerCase(), "nome para indice " + i);
        }

        // Rejeições de limites estritos
        afirmarRejeicao(() -> ds.setDiaSemana(-1), IllegalArgumentException.class, "setDiaSemana(-1)");
        afirmarRejeicao(() -> ds.setDiaSemana(-100), IllegalArgumentException.class, "setDiaSemana(-100)");
        afirmarRejeicao(() -> ds.setDiaSemana(7), IllegalArgumentException.class, "setDiaSemana(7)");
        afirmarRejeicao(() -> ds.setDiaSemana(8), IllegalArgumentException.class, "setDiaSemana(8)");
        afirmarRejeicao(() -> ds.setDiaSemana(100), IllegalArgumentException.class, "setDiaSemana(100)");
    }

    /**
     * Vetor 14: Integração DiaSemana + OperacaoFinanceira + AplicacaoFinanceira.
     */
    private static void testarIntegracaoComAplicacaoFinanceira() {
        // Criar aplicação com início em 01/10/2026 (Quinta-feira, útil)
        LocalDate inicio = LocalDate.of(2026, 10, 1);
        AplicacaoFinanceira terminal = new AplicacaoFinanceira(
                "CDB DI Challenger",
                "CDB-CHALLENGE-001",
                new BigDecimal("10000.00"),
                new BigDecimal("0.030"), // 3% ao mês -> 0.1% ao dia
                0, // rendimento diário
                30, // 30 dias de vencimento
                0, // sem carência de resgate
                0, // resgate diário
                0, // IOF isento
                0, // IR isento
                inicio
        );

        // Simular exatamente 30 dias civis (01/10/2026 a 30/10/2026)
        // Usar DiaSemana e OperacaoFinanceira em cada etapa
        int diasUteisContados = 0;
        int finsDeSemanaContados = 0;
        LocalDate cursor = inicio;

        for (int d = 0; d < 30; d++) {
            cursor = inicio.plusDays(d);
            DiaSemana cal = new DiaSemana(cursor);
            boolean util = cal.ehDiaUtil();

            if (util) {
                diasUteisContados++;
                // Criar operação de registro de fluxo
                OperacaoFinanceira opFluxo = new OperacaoFinanceira(
                        cursor,
                        new BigDecimal("10.00"),
                        "Aporte diário " + (d + 1),
                        TipoOperacao.DEPOSITO
                );
                afirmarIgual(cursor, opFluxo.getData(), "data do lançamento");
            } else {
                finsDeSemanaContados++;
            }
        }

        // Atualizar saldo da aplicação até o dia 30/10/2026
        terminal.atualizarSaldo(cursor);

        // Verificar consistência entre o calendário calculado e a aplicação
        afirmarIgual(30, terminal.getDiasCorridos(), "30 dias civis corridos");
        afirmarIgual(diasUteisContados, terminal.getDiasEfetivosCalculo(), "dias úteis efetivos devem coincidir com DiaSemana");
        afirmarIgual(22, diasUteisContados, "outubro/2026 de 01 a 30 tem exatamente 22 dias úteis");
        afirmarIgual(8, finsDeSemanaContados, "outubro/2026 de 01 a 30 tem exatamente 8 dias de fim de semana");
        afirmarIgual(30, diasUteisContados + finsDeSemanaContados, "soma de dias úteis e fins de semana deve ser 30");
        afirmarVerdadeiro(terminal.getValorAtualBruto().compareTo(new BigDecimal("10000.00")) > 0, "patrimônio rendeu nos dias úteis");
    }
}

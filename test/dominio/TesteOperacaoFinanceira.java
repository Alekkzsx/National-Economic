package test.dominio;

import static test.Afirmacao.*;

import dominio.OperacaoFinanceira;
import dominio.TipoOperacao;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Suíte de testes autônomos para a classe {@link OperacaoFinanceira} (Item 2, Requisito R1.2).
 *
 * <p>Valida o lançamento individual de fluxo financeiro, incluindo atributos de data
 * (dia, mês, ano sincronizados com data civil), valor monetário positivo com arredondamento,
 * descrição textual não vazia, tipo de operação (Depósito/Retirada), construtores e
 * métodos polimórficos de cadastro, atomicidade de atualização e segurança de herança.</p>
 */
public final class TesteOperacaoFinanceira {

    public static void main(String[] args) {
        System.out.println(">>> Executando TesteOperacaoFinanceira (Item 2 - Lançamentos de Fluxo)...");
        long inicio = System.currentTimeMillis();
        int testesExecutados = 0;

        testarInstanciacaoEGettersSetters(); testesExecutados++;
        testarConstrutorComLocalDate(); testesExecutados++;
        testarArredondamentoMonetarioCentavos(); testesExecutados++;
        testarNormalizacaoDescricao(); testesExecutados++;
        testarPolimorfismoSetCadastro(); testesExecutados++;
        testarAtomicidadeSetCadastro(); testesExecutados++;
        testarDiferenciacaoTiposOperacao(); testesExecutados++;
        testarRejeicaoValorZeroOuNegativo(); testesExecutados++;
        testarRejeicaoDataInvalida(); testesExecutados++;
        testarRejeicaoDescricaoInvalida(); testesExecutados++;
        testarRejeicaoTipoNulo(); testesExecutados++;
        testarHerancaSegura(); testesExecutados++;
        testarSobrescritaToString(); testesExecutados++;
        testarAliasesLegadosCompatibilidade(); testesExecutados++;
        testarSimulacaoFluxoLancamentos(); testesExecutados++;

        long duracao = System.currentTimeMillis() - inicio;
        System.out.printf(">>> TesteOperacaoFinanceira CONCLUÍDO com SUCESSO: %d métodos verificados (%d ms)%n%n",
                testesExecutados, duracao);
    }

    /**
     * Tier 1: Construtor completo com inteiros, validação de campos e getters/setters em PT-BR.
     */
    public static void testarInstanciacaoEGettersSetters() {
        OperacaoFinanceira op = new OperacaoFinanceira(
                15, 8, 2026,
                new BigDecimal("500.00"),
                "Aporte Mensal Programado",
                TipoOperacao.DEPOSITO
        );

        afirmarIgual(15, op.getDia(), "dia deve ser 15");
        afirmarIgual(8, op.getMes(), "mês deve ser 8");
        afirmarIgual(2026, op.getAno(), "ano deve ser 2026");
        afirmarIgual(LocalDate.of(2026, 8, 15), op.getData(), "data composta deve bater com 15/08/2026");
        afirmarIgual(new BigDecimal("500.00"), op.getValor(), "valor deve ser 500.00");
        afirmarIgual("Aporte Mensal Programado", op.getDescricao(), "descrição deve bater");
        afirmarIgual(TipoOperacao.DEPOSITO, op.getTipo(), "tipo deve ser DEPOSITO");

        // Modificações por setters
        op.setDescricao("Aporte Extraordinário");
        afirmarIgual("Aporte Extraordinário", op.getDescricao(), "descrição atualizada");

        op.setValor(new BigDecimal("750.50"));
        afirmarIgual(new BigDecimal("750.50"), op.getValor(), "valor atualizado");

        op.setTipo(TipoOperacao.RETIRADA);
        afirmarIgual(TipoOperacao.RETIRADA, op.getTipo(), "tipo atualizado para RETIRADA");
    }

    /**
     * Tier 1: Construtor alternativo a partir de LocalDate.
     */
    public static void testarConstrutorComLocalDate() {
        LocalDate data = LocalDate.of(2026, 11, 20);
        OperacaoFinanceira op = new OperacaoFinanceira(
                data,
                new BigDecimal("1200.00"),
                "Resgate Parcial para Reforma",
                TipoOperacao.RETIRADA
        );

        afirmarIgual(20, op.getDia(), "dia deve ser extraído como 20");
        afirmarIgual(11, op.getMes(), "mês deve ser extraído como 11");
        afirmarIgual(2026, op.getAno(), "ano deve ser extraído como 2026");
        afirmarIgual(data, op.getData(), "data LocalDate deve ser preservada");
        afirmarIgual(TipoOperacao.RETIRADA, op.getTipo(), "tipo deve ser RETIRADA");
    }

    /**
     * Tier 2 (Boundary): Arredondamento do valor monetário para 2 casas decimais.
     */
    public static void testarArredondamentoMonetarioCentavos() {
        // Valor com 3 casas decimais (123.456 -> 123.46)
        OperacaoFinanceira op = new OperacaoFinanceira(
                10, 5, 2026,
                new BigDecimal("123.456"),
                "Ajuste de Centavos",
                TipoOperacao.DEPOSITO
        );
        afirmarIgual(new BigDecimal("123.46"), op.getValor(), "deve arredondar 123.456 para 123.46");

        // Valor com centavos mínimos (0.01)
        OperacaoFinanceira opMin = new OperacaoFinanceira(
                10, 5, 2026,
                new BigDecimal("0.01"),
                "Aporte Mínimo",
                TipoOperacao.DEPOSITO
        );
        afirmarIgual(new BigDecimal("0.01"), opMin.getValor(), "deve permitir 0.01");
    }

    /**
     * Tier 2 (Boundary): Normalização de descrição com espaços externos e caracteres PT-BR.
     */
    public static void testarNormalizacaoDescricao() {
        OperacaoFinanceira op = new OperacaoFinanceira(
                1, 2, 2026,
                new BigDecimal("100.00"),
                "   Depósito com Acentuação e Espaços Excedentes   ",
                TipoOperacao.DEPOSITO
        );
        afirmarIgual("Depósito com Acentuação e Espaços Excedentes", op.getDescricao(),
                "descrição deve ter espaços externos removidos via trim()");
    }

    /**
     * Tier 3: Sobrecargas polimórficas de setCadastro.
     */
    public static void testarPolimorfismoSetCadastro() {
        OperacaoFinanceira op = new OperacaoFinanceira(
                1, 1, 2026,
                new BigDecimal("100.00"),
                "Inicial",
                TipoOperacao.DEPOSITO
        );

        // setCadastro com inteiros
        op.setCadastro(10, 3, 2026, new BigDecimal("250.00"), "Cadastro via Inteiros", TipoOperacao.DEPOSITO);
        afirmarIgual(10, op.getDia(), "dia deve ser 10");
        afirmarIgual(3, op.getMes(), "mês deve ser 3");
        afirmarIgual(new BigDecimal("250.00"), op.getValor(), "valor atualizado para 250.00");
        afirmarIgual("Cadastro via Inteiros", op.getDescricao(), "descrição atualizada");

        // setCadastro com LocalDate
        LocalDate novaData = LocalDate.of(2026, 4, 18);
        op.setCadastro(novaData, new BigDecimal("400.00"), "Cadastro via LocalDate", TipoOperacao.RETIRADA);
        afirmarIgual(18, op.getDia(), "dia deve ser 18");
        afirmarIgual(4, op.getMes(), "mês deve ser 4");
        afirmarIgual(new BigDecimal("400.00"), op.getValor(), "valor atualizado para 400.00");
        afirmarIgual(TipoOperacao.RETIRADA, op.getTipo(), "tipo atualizado para RETIRADA");
    }

    /**
     * Tier 5 (Adversarial): Atomicidade de setCadastro — falha em validação não corrompe estado prévio.
     */
    public static void testarAtomicidadeSetCadastro() {
        OperacaoFinanceira op = new OperacaoFinanceira(
                5, 5, 2026,
                new BigDecimal("1000.00"),
                "Estado Original Intacto",
                TipoOperacao.DEPOSITO
        );

        // Tentativa de setCadastro com valor inválido (negativo)
        afirmarRejeicao(() -> op.setCadastro(10, 10, 2026, new BigDecimal("-50.00"), "Inválido", TipoOperacao.DEPOSITO),
                IllegalArgumentException.class, "valor negativo deve falhar");

        // Garante que todos os campos preservam o valor original
        afirmarIgual(5, op.getDia(), "dia deve continuar 5");
        afirmarIgual(5, op.getMes(), "mês deve continuar 5");
        afirmarIgual(new BigDecimal("1000.00"), op.getValor(), "valor deve continuar 1000.00");
        afirmarIgual("Estado Original Intacto", op.getDescricao(), "descrição deve continuar a original");
    }

    /**
     * Tier 1: Diferenciação clara entre Depósito e Retirada.
     */
    public static void testarDiferenciacaoTiposOperacao() {
        OperacaoFinanceira dep = new OperacaoFinanceira(
                1, 6, 2026, new BigDecimal("100.00"), "Depósito", TipoOperacao.DEPOSITO);
        OperacaoFinanceira ret = new OperacaoFinanceira(
                1, 6, 2026, new BigDecimal("100.00"), "Retirada", TipoOperacao.RETIRADA);

        afirmarVerdadeiro(dep.getTipo().ehDeposito(), "DEPOSITO deve responder true a ehDeposito()");
        afirmarFalso(dep.getTipo().ehRetirada(), "DEPOSITO deve responder false a ehRetirada()");
        afirmarVerdadeiro(ret.getTipo().ehRetirada(), "RETIRADA deve responder true a ehRetirada()");
        afirmarFalso(ret.getTipo().ehDeposito(), "RETIRADA deve responder false a ehDeposito()");
    }

    /**
     * Tier 5 (Adversarial): Rejeição de valores monetários zero ou estritamente negativos.
     */
    public static void testarRejeicaoValorZeroOuNegativo() {
        // Zero no construtor
        afirmarRejeicao(() -> new OperacaoFinanceira(1, 1, 2026, BigDecimal.ZERO, "Zero", TipoOperacao.DEPOSITO),
                IllegalArgumentException.class, "valor zero deve ser rejeitado");

        // Negativo no construtor
        afirmarRejeicao(() -> new OperacaoFinanceira(1, 1, 2026, new BigDecimal("-10.00"), "Negativo", TipoOperacao.DEPOSITO),
                IllegalArgumentException.class, "valor negativo deve ser rejeitado");

        // Zero ou negativo via setValor
        OperacaoFinanceira op = new OperacaoFinanceira(1, 1, 2026, new BigDecimal("50.00"), "Válido", TipoOperacao.DEPOSITO);
        afirmarRejeicao(() -> op.setValor(BigDecimal.ZERO), IllegalArgumentException.class,
                "setValor(ZERO) deve ser rejeitado");
        afirmarRejeicao(() -> op.setValor(new BigDecimal("-0.01")), IllegalArgumentException.class,
                "setValor(negativo) deve ser rejeitado");
    }

    /**
     * Tier 5 (Adversarial): Rejeição de datas civis inválidas.
     */
    public static void testarRejeicaoDataInvalida() {
        // 31 de Abril (mês de 30 dias)
        afirmarRejeicao(() -> new OperacaoFinanceira(31, 4, 2026, new BigDecimal("100.00"), "Data", TipoOperacao.DEPOSITO),
                IllegalArgumentException.class, "deve rejeitar 31/04");

        // 30 de Fevereiro
        afirmarRejeicao(() -> new OperacaoFinanceira(30, 2, 2026, new BigDecimal("100.00"), "Data", TipoOperacao.DEPOSITO),
                IllegalArgumentException.class, "deve rejeitar 30/02");
    }

    /**
     * Tier 5 (Adversarial): Rejeição de descrição nula ou em branco.
     */
    public static void testarRejeicaoDescricaoInvalida() {
        afirmarRejeicao(() -> new OperacaoFinanceira(1, 1, 2026, new BigDecimal("100.00"), null, TipoOperacao.DEPOSITO),
                IllegalArgumentException.class, "descrição nula deve ser rejeitada");

        afirmarRejeicao(() -> new OperacaoFinanceira(1, 1, 2026, new BigDecimal("100.00"), "", TipoOperacao.DEPOSITO),
                IllegalArgumentException.class, "descrição vazia deve ser rejeitada");

        afirmarRejeicao(() -> new OperacaoFinanceira(1, 1, 2026, new BigDecimal("100.00"), "    ", TipoOperacao.DEPOSITO),
                IllegalArgumentException.class, "descrição apenas espaços deve ser rejeitada");
    }

    /**
     * Tier 5 (Adversarial): Rejeição de tipo de operação nulo.
     */
    public static void testarRejeicaoTipoNulo() {
        afirmarRejeicao(() -> new OperacaoFinanceira(1, 1, 2026, new BigDecimal("100.00"), "Valido", null),
                RuntimeException.class, "tipo nulo deve lançar exceção");
    }

    /**
     * Tier 3 & Tier 5: Segurança de herança contra despacho de construtor para métodos sobrescrevíveis.
     */
    public static void testarHerancaSegura() {
        class OperacaoSubclasseSegura extends OperacaoFinanceira {
            OperacaoSubclasseSegura() {
                super(LocalDate.of(2026, 1, 1), new BigDecimal("100.00"), "Subclasse Teste", TipoOperacao.DEPOSITO);
            }

            @Override
            public void setCadastro(LocalDate data, BigDecimal valor, String descricao, TipoOperacao tipo) {
                throw new AssertionError("O construtor jamais deve invocar setCadastro virtual/sobrescrevível!");
            }

            @Override
            public void setCadastro(int dia, int mes, int ano, BigDecimal valor, String descricao, TipoOperacao tipo) {
                throw new AssertionError("O construtor jamais deve invocar setCadastro virtual/sobrescrevível!");
            }
        }

        // A instanciação deve suceder normalmente sem disparar a sobrescrita de setCadastro
        OperacaoSubclasseSegura sub = new OperacaoSubclasseSegura();
        afirmarIgual(new BigDecimal("100.00"), sub.getValor(), "valor da subclasse deve ser preservado");
        afirmarIgual("Subclasse Teste", sub.getDescricao(), "descrição da subclasse");
    }

    /**
     * Tier 1: Sobrescrita descritiva de toString().
     */
    public static void testarSobrescritaToString() {
        OperacaoFinanceira op = new OperacaoFinanceira(
                12, 10, 2026, new BigDecimal("850.75"), "Crédito de Dividendos", TipoOperacao.DEPOSITO);
        String s = op.toString();
        afirmarNaoNulo(s, "toString não pode ser nulo");
        afirmarVerdadeiro(s.contains("850.75") || s.contains("850,75"), "toString deve conter o valor");
        afirmarVerdadeiro(s.contains("Dividendos"), "toString deve conter parte da descrição");
    }

    /**
     * Tier 1: Compatibilidade retroativa de métodos auxiliares e aliases legados.
     */
    public static void testarAliasesLegadosCompatibilidade() {
        LocalDate data = LocalDate.of(2026, 3, 14);
        OperacaoFinanceira op = new OperacaoFinanceira(data, new BigDecimal("300.00"), "Alias", TipoOperacao.DEPOSITO);

        afirmarIgual(data, op.date(), "date() deve retornar LocalDate");
        afirmarIgual(new BigDecimal("300.00"), op.amount(), "amount() deve retornar valor");
        afirmarIgual("Alias", op.description(), "description() deve retornar descrição");
        afirmarIgual(TipoOperacao.DEPOSITO, op.type(), "type() deve retornar TipoOperacao");
    }

    /**
     * Tier 4: Simulação de fluxo contábil de depósitos e retiradas.
     */
    public static void testarSimulacaoFluxoLancamentos() {
        BigDecimal saldoAcumulado = BigDecimal.ZERO;

        OperacaoFinanceira[] operacoes = new OperacaoFinanceira[] {
            new OperacaoFinanceira(1, 10, 2026, new BigDecimal("1000.00"), "Aporte Inicial", TipoOperacao.DEPOSITO),
            new OperacaoFinanceira(5, 10, 2026, new BigDecimal("500.00"), "Aporte Suplementar", TipoOperacao.DEPOSITO),
            new OperacaoFinanceira(12, 10, 2026, new BigDecimal("300.00"), "Resgate Conta", TipoOperacao.RETIRADA),
            new OperacaoFinanceira(20, 10, 2026, new BigDecimal("150.00"), "Despesa", TipoOperacao.RETIRADA)
        };

        for (OperacaoFinanceira op : operacoes) {
            if (op.getTipo() == TipoOperacao.DEPOSITO) {
                saldoAcumulado = saldoAcumulado.add(op.getValor());
            } else {
                saldoAcumulado = saldoAcumulado.subtract(op.getValor());
            }
        }

        // Saldo esperado: 1000 + 500 - 300 - 150 = 1050.00
        afirmarIgual(new BigDecimal("1050.00"), saldoAcumulado, "saldo acumulado do fluxo deve ser 1050.00");
    }
}

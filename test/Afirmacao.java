package test;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Utilitário autônomo de asserções para a suíte de testes do SGI.
 *
 * <p>Fornece métodos de asserção estáticos em Português do Brasil que operam
 * exclusivamente com o JDK padrão, sem qualquer dependência de JUnit, AssertJ
 * ou outras bibliotecas externas. Em caso de falha, lança {@link AssertionError}
 * com mensagens descritivas para diagnóstico rápido no terminal.</p>
 */
public final class Afirmacao {

    /** Contador de asserções executadas com sucesso. */
    private static final AtomicLong TOTAL_AFIRMACOES = new AtomicLong(0);

    /** Construtor privado para impedir instanciação de classe utilitária. */
    private Afirmacao() {
        throw new UnsupportedOperationException("Classe utilitária não deve ser instanciada.");
    }

    /**
     * Valida que uma condição booleana é verdadeira.
     *
     * @param condicao a condição a ser validada
     * @param mensagem descrição do propósito da verificação
     * @throws AssertionError se a condição for falsa
     */
    public static void afirmar(boolean condicao, String mensagem) {
        if (!condicao) {
            throw new AssertionError("Falha na asserção: " + mensagem);
        }
        TOTAL_AFIRMACOES.incrementAndGet();
    }

    /**
     * Valida que uma condição booleana é verdadeira, com mensagem padrão.
     *
     * @param condicao a condição a ser validada
     * @throws AssertionError se a condição for falsa
     */
    public static void afirmar(boolean condicao) {
        afirmar(condicao, "condição booleana esperada como verdadeira");
    }

    /**
     * Valida que a condição é explicitamente verdadeira.
     *
     * @param condicao a condição esperada
     * @param mensagem descrição do teste
     */
    public static void afirmarVerdadeiro(boolean condicao, String mensagem) {
        afirmar(condicao, mensagem);
    }

    /**
     * Valida que a condição é explicitamente falsa.
     *
     * @param condicao a condição esperada
     * @param mensagem descrição do teste
     */
    public static void afirmarFalso(boolean condicao, String mensagem) {
        afirmar(!condicao, mensagem);
    }

    /**
     * Valida que dois objetos são equivalentes.
     *
     * <p>Para instâncias de {@link BigDecimal}, a comparação utiliza
     * {@link BigDecimal#compareTo(BigDecimal)} para garantir igualdade numérica
     * independentemente da escala (ex.: {@code 10.0} equivale a {@code 10.00}).
     * Para arrays e outros tipos de objetos, utiliza {@link Objects#deepEquals(Object, Object)}.</p>
     *
     * @param esperado o valor esperado de referência
     * @param obtido o valor efetivamente obtido durante a execução
     * @param mensagem descrição do contexto da asserção
     * @throws AssertionError se os valores divergirem
     */
    public static void afirmarIgual(Object esperado, Object obtido, String mensagem) {
        boolean iguais;
        if (esperado == null && obtido == null) {
            iguais = true;
        } else if (esperado == null || obtido == null) {
            iguais = false;
        } else if (esperado instanceof BigDecimal && obtido instanceof BigDecimal) {
            iguais = ((BigDecimal) esperado).compareTo((BigDecimal) obtido) == 0;
        } else if (esperado.getClass().isArray() && obtido.getClass().isArray()) {
            iguais = Objects.deepEquals(esperado, obtido);
        } else {
            iguais = Objects.equals(esperado, obtido);
        }

        if (!iguais) {
            throw new AssertionError(String.format(
                    "Falha na asserção [%s]: esperado <%s> (tipo: %s) mas obtido <%s> (tipo: %s)",
                    mensagem,
                    esperado,
                    esperado != null ? esperado.getClass().getSimpleName() : "null",
                    obtido,
                    obtido != null ? obtido.getClass().getSimpleName() : "null"
            ));
        }
        TOTAL_AFIRMACOES.incrementAndGet();
    }

    /**
     * Valida que dois objetos são equivalentes, com mensagem padrão.
     *
     * @param esperado o valor esperado
     * @param obtido o valor obtido
     */
    public static void afirmarIgual(Object esperado, Object obtido) {
        afirmarIgual(esperado, obtido, "valores devem ser iguais");
    }

    /**
     * Valida que o objeto informado não é nulo.
     *
     * @param objeto a referência a verificar
     * @param mensagem contexto da validação
     */
    public static void afirmarNaoNulo(Object objeto, String mensagem) {
        afirmar(objeto != null, mensagem + " (esperado não-nulo)");
    }

    /**
     * Valida que o objeto informado é nulo.
     *
     * @param objeto a referência a verificar
     * @param mensagem contexto da validação
     */
    public static void afirmarNulo(Object objeto, String mensagem) {
        afirmar(objeto == null, mensagem + " (esperado nulo)");
    }

    /**
     * Valida que a execução de um bloco lança a exceção especificada.
     *
     * @param acao bloco de código executável via {@link Runnable}
     * @param tipoExcecao classe da exceção esperada
     * @param mensagem descrição do teste de rejeição
     * @throws AssertionError se nenhuma exceção ou uma exceção de tipo incompatível for lançada
     */
    public static void afirmarRejeicao(Runnable acao, Class<? extends Throwable> tipoExcecao, String mensagem) {
        if (acao == null) {
            throw new IllegalArgumentException("A ação de teste não pode ser nula.");
        }
        if (tipoExcecao == null) {
            throw new IllegalArgumentException("O tipo de exceção esperado não pode ser nulo.");
        }

        try {
            acao.run();
        } catch (Throwable t) {
            if (tipoExcecao.isInstance(t)) {
                TOTAL_AFIRMACOES.incrementAndGet();
                return;
            }
            throw new AssertionError(String.format(
                    "Falha na asserção [%s]: esperava exceção <%s> mas foi lançada <%s>: %s",
                    mensagem,
                    tipoExcecao.getName(),
                    t.getClass().getName(),
                    t.getMessage()
            ), t);
        }

        throw new AssertionError(String.format(
                "Falha na asserção [%s]: esperava exceção <%s>, mas nenhuma exceção foi lançada.",
                mensagem,
                tipoExcecao.getName()
        ));
    }

    /**
     * Valida que a execução de um bloco lança a exceção especificada, com mensagem padrão.
     *
     * @param acao o bloco executável
     * @param tipoExcecao a classe esperada
     */
    public static void afirmarRejeicao(Runnable acao, Class<? extends Throwable> tipoExcecao) {
        afirmarRejeicao(acao, tipoExcecao, "operação deve lançar " + tipoExcecao.getSimpleName());
    }

    /**
     * Retorna a quantidade total de asserções executadas com sucesso.
     *
     * @return o total acumulado de verificações assertivas
     */
    public static long getTotalAfirmacoes() {
        return TOTAL_AFIRMACOES.get();
    }

    /**
     * Reinicializa o contador global de asserções para zero.
     */
    public static void zerarContador() {
        TOTAL_AFIRMACOES.set(0);
    }
}

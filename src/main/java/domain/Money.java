package domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Regras comuns para armazenar valores monetários com precisão de centavos.
 * A classe é interna ao domínio para que todas as entidades usem o mesmo
 * arredondamento e as mesmas validações.
 */
final class Money {
    static final int SCALE = 2;
    static final BigDecimal ZERO = BigDecimal.ZERO.setScale(SCALE);

    private Money() {
    }

    /** Arredonda um valor monetário para centavos. */
    static BigDecimal round(BigDecimal value) {
        Objects.requireNonNull(value, "Valor monetário obrigatório.");
        return value.setScale(SCALE, RoundingMode.HALF_UP);
    }

    /** Arredonda e exige que o valor seja estritamente positivo. */
    static BigDecimal positive(BigDecimal value, String field) {
        BigDecimal rounded = round(value);
        if (rounded.signum() <= 0) {
            throw new IllegalArgumentException(field + " deve ser positivo.");
        }
        return rounded;
    }

    /** Arredonda e não permite valores negativos. */
    static BigDecimal nonNegative(BigDecimal value, String field) {
        BigDecimal rounded = round(value);
        if (rounded.signum() < 0) {
            throw new IllegalArgumentException(field + " não pode ser negativo.");
        }
        return rounded;
    }
}

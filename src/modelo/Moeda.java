package modelo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Regras comuns para armazenar valores monetários com precisão de centavos.
 * A classe é interna ao domínio para que todas as entidades usem o mesmo
 * arredondamento e as mesmas validações.
 */
final class Moeda {
    static final int CASAS_DECIMAIS = 2;
    static final BigDecimal ZERO = BigDecimal.ZERO.setScale(CASAS_DECIMAIS);

    private Moeda() {
    }

    /** Arredonda um valor monetário para centavos. */
    static BigDecimal arredondar(BigDecimal valor) {
        Objects.requireNonNull(valor, "Valor monetário obrigatório.");
        return valor.setScale(CASAS_DECIMAIS, RoundingMode.HALF_UP);
    }

    /** Arredonda e exige que o valor seja estritamente positivo. */
    static BigDecimal positivo(BigDecimal valor, String campo) {
        BigDecimal valorArredondado = arredondar(valor);
        if (valorArredondado.signum() <= 0) {
            throw new IllegalArgumentException(campo + " deve ser positivo.");
        }
        return valorArredondado;
    }

    /** Arredonda e não permite valores negativos. */
    static BigDecimal naoNegativo(BigDecimal valor, String campo) {
        BigDecimal valorArredondado = arredondar(valor);
        if (valorArredondado.signum() < 0) {
            throw new IllegalArgumentException(campo + " não pode ser negativo.");
        }
        return valorArredondado;
    }
}

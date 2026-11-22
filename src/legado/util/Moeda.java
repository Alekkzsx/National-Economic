package legado.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Regras comuns para armazenar valores monetários com precisão de centavos.
 * Uso interno da camada {@code legado/}: garante que as entidades legadas usem o mesmo
 * arredondamento e as mesmas validações.
 */
public final class Moeda {
    public static final int CASAS_DECIMAIS = 2;
    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(CASAS_DECIMAIS);

    private Moeda() {
    }

    /** Arredonda um valor monetário para centavos. */
    public static BigDecimal arredondar(BigDecimal valor) {
        Objects.requireNonNull(valor, "Valor monetário obrigatório.");
        return valor.setScale(CASAS_DECIMAIS, RoundingMode.HALF_UP);
    }

    /** Arredonda e exige que o valor seja estritamente positivo. */
    public static BigDecimal positivo(BigDecimal valor, String campo) {
        BigDecimal valorArredondado = arredondar(valor);
        if (valorArredondado.signum() <= 0) {
            throw new IllegalArgumentException(campo + " deve ser positivo.");
        }
        return valorArredondado;
    }

    /** Arredonda e não permite valores negativos. */
    public static BigDecimal naoNegativo(BigDecimal valor, String campo) {
        BigDecimal valorArredondado = arredondar(valor);
        if (valorArredondado.signum() < 0) {
            throw new IllegalArgumentException(campo + " não pode ser negativo.");
        }
        return valorArredondado;
    }
}

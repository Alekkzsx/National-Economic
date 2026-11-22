package dominio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Objects;

/**
 * Utilitário monetário do domínio: arredondamento em centavos (2 casas decimais),
 * parsing flexível (notação brasileira e internacional), validações e formatação pt-BR.
 */
public final class Moeda {

    public static final int CASAS_DECIMAIS = 2;
    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(CASAS_DECIMAIS, RoundingMode.HALF_UP);
    public static final RoundingMode MODO_COMERCIAL = RoundingMode.HALF_UP;
    public static final RoundingMode MODO_BANCARIO = RoundingMode.HALF_EVEN;
    public static final RoundingMode MODO_PADRAO = MODO_COMERCIAL;

    private static final Locale LOCALE_BRASIL = Locale.forLanguageTag("pt-BR");

    private Moeda() {
    }

    /** Arredonda para 2 casas decimais com modo comercial (HALF_UP). */
    public static BigDecimal arredondar(BigDecimal valor) {
        return arredondar(valor, MODO_PADRAO);
    }

    /** Arredonda para 2 casas decimais utilizando o modo informado. */
    public static BigDecimal arredondar(BigDecimal valor, RoundingMode modo) {
        Objects.requireNonNull(valor, "Valor monetário não pode ser nulo.");
        Objects.requireNonNull(modo, "Modo de arredondamento não pode ser nulo.");
        return valor.setScale(CASAS_DECIMAIS, modo);
    }

    /** Arredonda para 2 casas decimais com modo bancário (HALF_EVEN). */
    public static BigDecimal arredondarBancario(BigDecimal valor) {
        return arredondar(valor, MODO_BANCARIO);
    }

    /** Garante valor estritamente positivo (> 0) já arredondado em centavos. */
    public static BigDecimal positivo(BigDecimal valor, String nomeCampo) {
        Objects.requireNonNull(nomeCampo, "Nome do campo não pode ser nulo.");
        BigDecimal arredondado = arredondar(valor);
        if (arredondado.signum() <= 0) {
            throw new IllegalArgumentException(nomeCampo + " deve ser estritamente positivo (maior que zero).");
        }
        return arredondado;
    }

    /** Garante valor não negativo (>= 0) já arredondado em centavos. */
    public static BigDecimal naoNegativo(BigDecimal valor, String nomeCampo) {
        Objects.requireNonNull(nomeCampo, "Nome do campo não pode ser nulo.");
        BigDecimal arredondado = arredondar(valor);
        if (arredondado.signum() < 0) {
            throw new IllegalArgumentException(nomeCampo + " não pode ser negativo.");
        }
        return arredondado;
    }

    /** Converte {@code double} para {@link BigDecimal} com escala 2. */
    public static BigDecimal de(double valor) {
        return BigDecimal.valueOf(valor).setScale(CASAS_DECIMAIS, MODO_PADRAO);
    }

    /** Converte texto monetário (notação BR ou internacional, com ou sem prefixo R$) em BigDecimal. */
    public static BigDecimal de(String valorTexto) {
        Objects.requireNonNull(valorTexto, "Texto monetário não pode ser nulo.");
        String s = valorTexto.trim();
        if (s.isEmpty()) {
            throw new IllegalArgumentException("Texto monetário não pode ser vazio.");
        }

        // Remove prefixos monetários e espaços (incluindo NBSP e NNBSP)
        s = s.replace("R$", "").replace("r$", "").replace("$", "")
                .replace("\u00A0", "").replace("\u202F", "").replace(" ", "");

        boolean negativo = false;
        if (s.startsWith("-")) {
            negativo = true;
            s = s.substring(1).trim();
        } else if (s.startsWith("+")) {
            s = s.substring(1).trim();
        }
        if (s.isEmpty()) {
            throw new IllegalArgumentException("Valor monetário inválido: '" + valorTexto + "'.");
        }

        String numero = normalizarSeparadores(s);
        if (negativo) {
            numero = "-" + numero;
        }
        try {
            return new BigDecimal(numero).setScale(CASAS_DECIMAIS, MODO_PADRAO);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Valor monetário inválido: '" + valorTexto + "'.", e);
        }
    }

    private static String normalizarSeparadores(String s) {
        int pontos = 0;
        int virgulas = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '.') pontos++;
            else if (c == ',') virgulas++;
        }

        if (pontos > 0 && virgulas > 0) {
            // O último separador define o decimal; o outro é milhar
            return s.lastIndexOf(',') > s.lastIndexOf('.')
                    ? s.replace(".", "").replace(',', '.')
                    : s.replace(",", "");
        }
        if (virgulas > 0) {
            return virgulas == 1 ? s.replace(',', '.') : s.replace(",", "");
        }
        if (pontos == 1) {
            int idx = s.indexOf('.');
            String inteiro = s.substring(0, idx);
            String decimal = s.substring(idx + 1);
            boolean milharBrasileiro = decimal.length() == 3
                    && inteiro.length() >= 1 && inteiro.length() <= 3
                    && !inteiro.equals("0");
            return milharBrasileiro ? s.replace(".", "") : s;
        }
        return pontos > 1 ? s.replace(".", "") : s;
    }

    public static boolean ehZero(BigDecimal valor) {
        return valor == null || valor.signum() == 0;
    }

    public static boolean ehPositivo(BigDecimal valor) {
        return valor != null && valor.signum() > 0;
    }

    public static boolean ehNegativo(BigDecimal valor) {
        return valor != null && valor.signum() < 0;
    }

    /** Formata o valor no padrão monetário brasileiro (ex.: "R$ 1.500,50"). */
    public static String formatar(BigDecimal valor) {
        return NumberFormat.getCurrencyInstance(LOCALE_BRASIL).format(valor == null ? ZERO : valor);
    }
}

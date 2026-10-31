package app;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Formatação de valores exibidos pela interface de terminal. */
public final class CliFormat {
    private CliFormat() { }

    /** Oculta todos os dígitos do CPF, mantendo apenas a apresentação mascarada. */
    public static String maskCpf(Object value) {
        String cpf = value == null ? "" : value.toString();
        String digits = cpf.replaceAll("\\D", "");
        if (digits.length() == 11) {
            return "***." + digits.substring(3, 6) + "." + digits.substring(6, 9) + "-**";
        }
        if (cpf.length() <= 4) {
            return "****";
        }
        return "*".repeat(cpf.length() - 4) + cpf.substring(cpf.length() - 4);
    }

    /** Exibe valores monetários em reais, arredondados para duas casas. */
    public static String money(BigDecimal value) {
        return value == null ? "não disponível" : "R$ " + value.setScale(2, RoundingMode.HALF_UP);
    }

    /** Garante uma mensagem útil e remove CPFs eventualmente incluídos no erro. */
    public static String messageOf(Throwable throwable) {
        String message = throwable == null ? null : throwable.getMessage();
        if (message == null || message.isBlank()) {
            return "erro sem detalhes";
        }
        return message.replaceAll("(?<!\\d)(?:\\d{3}\\.?){2}\\d{3}-?\\d{2}(?!\\d)", "***.***.***-**");
    }
}

package terminal.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Utilitário reutilizável de formatação e mascaramento para a interface de terminal. */
public final class FormatadorTerminal {

    private FormatadorTerminal() {
    }

    /** Oculta dígitos do CPF para exibição segura em listagens e consultas. */
    public static String mascararCpf(Object valor) {
        String cpf = valor == null ? "" : valor.toString();
        String digitos = cpf.replaceAll("\\D", "");
        if (digitos.length() == 11) {
            return "***." + digitos.substring(3, 6) + "." + digitos.substring(6, 9) + "-**";
        }
        if (cpf.length() <= 4) {
            return "****";
        }
        return "*".repeat(cpf.length() - 4) + cpf.substring(cpf.length() - 4);
    }

    /** Formata valores monetários em reais (2 casas decimais). */
    public static String formatarMoeda(BigDecimal valor) {
        return valor == null ? "não disponível" : "R$ " + valor.setScale(2, RoundingMode.HALF_UP);
    }

    /** Formata taxas percentuais anuais vindas do servidor (ex.: {@code 10.50% a.a.}). */
    public static String formatarTaxaAnual(BigDecimal taxaAnualPercentual) {
        return taxaAnualPercentual == null
                ? "não disponível"
                : taxaAnualPercentual.setScale(2, RoundingMode.HALF_UP) + "% a.a.";
    }

    /** Formata cotações cambiais com 4 casas decimais (ex.: {@code R$ 5.1842}). */
    public static String formatarCambio(BigDecimal cotacao) {
        return cotacao == null ? "não disponível" : "R$ " + cotacao.setScale(4, RoundingMode.HALF_UP);
    }

    /** Retorna mensagem de erro legível removendo eventuais CPFs expostos no texto. */
    public static String mensagemDe(Throwable throwable) {
        String message = throwable == null ? null : throwable.getMessage();
        if (message == null || message.isBlank()) {
            return "erro sem detalhes";
        }
        return message.replaceAll("(?<!\\d)(?:\\d{3}\\.?){2}\\d{3}-?\\d{2}(?!\\d)", "***.***.***-**");
    }
}

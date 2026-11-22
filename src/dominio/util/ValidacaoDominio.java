package dominio.util;

import java.time.DateTimeException;
import java.time.LocalDate;

/**
 * Validações simples e reutilizáveis compartilhadas pelas entidades do domínio.
 */
public final class ValidacaoDominio {

    private ValidacaoDominio() {
    }

    /** Garante texto obrigatório não nulo e não em branco, devolvendo o texto aparado. */
    public static String textoObrigatorio(String valor, String mensagemErro) {
        if (valor == null || valor.trim().isEmpty()) {
            throw new IllegalArgumentException(mensagemErro);
        }
        return valor.trim();
    }

    /** Constrói e valida uma data civil gregoriana a partir de dia, mês e ano. */
    public static LocalDate validarData(int dia, int mes, int ano) {
        try {
            return LocalDate.of(ano, mes, dia);
        } catch (DateTimeException ex) {
            throw new IllegalArgumentException(
                    "Data civil inválida: " + dia + "/" + mes + "/" + ano + ".", ex);
        }
    }

    /** Remove qualquer caractere não numérico do texto informado. */
    public static String apenasDigitos(String valor) {
        return valor == null ? "" : valor.replaceAll("\\D", "");
    }

    /** Normaliza um CPF exigindo exatamente 11 dígitos numéricos. */
    public static String normalizarCpf(String cpf) {
        if (cpf == null) {
            throw new IllegalArgumentException("CPF não pode ser nulo.");
        }
        String digitos = apenasDigitos(cpf);
        if (digitos.length() != 11) {
            throw new IllegalArgumentException("CPF deve conter exatamente 11 dígitos numéricos: '" + cpf + "'.");
        }
        return digitos;
    }

    /** Calcula um dígito verificador de CPF em módulo 11 (pesoInicial = 10 para 1º DV, 11 para 2º DV). */
    public static int calcularDigitoVerificadorCpf(String digitos, int pesoInicial) {
        int quantidade = pesoInicial - 1;
        int soma = 0;
        for (int i = 0; i < quantidade; i++) {
            soma += (digitos.charAt(i) - '0') * (pesoInicial - i);
        }
        int resto = (soma * 10) % 11;
        return resto == 10 ? 0 : resto;
    }

    /** Verifica se todos os caracteres da string são iguais. */
    public static boolean todosCaracteresIguais(String texto) {
        if (texto == null || texto.isEmpty()) {
            return true;
        }
        char primeiro = texto.charAt(0);
        for (int i = 1; i < texto.length(); i++) {
            if (texto.charAt(i) != primeiro) {
                return false;
            }
        }
        return true;
    }
}

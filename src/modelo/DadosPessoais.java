package modelo;

import java.util.Objects;

/**
 * Dados cadastrais básicos do titular.
 *
 * <p>O CPF é armazenado apenas com dígitos e validado pelos dígitos
 * verificadores. A representação textual mascara o documento para evitar
 * exposição acidental em logs ou listagens.</p>
 */
public class DadosPessoais {
    /** Nome completo do titular. */
    private String name;
    /** CPF normalizado, composto por onze dígitos. */
    private String cpf;
    /** Telefone normalizado, composto apenas por dígitos. */
    private String mobile;

    /** Cria os dados pessoais com nome, CPF e celular. */
    public DadosPessoais(String name, String cpf, String mobile) {
        this.name = validarNome(name);
        this.cpf = validarCpf(cpf);
        this.mobile = validarCelular(mobile);
    }

    /** Cria os dados pessoais sem informar um celular. */
    public DadosPessoais(String name, String cpf) {
        this.name = validarNome(name);
        this.cpf = validarCpf(cpf);
        this.mobile = "";
    }

    /** Accessor conciso para o nome. */
    public String nome() { return name; }
    /** Accessor conciso para o CPF normalizado. */
    public String cpf() { return cpf; }
    /** Accessor conciso para o celular normalizado. */
    public String celular() { return mobile; }
    /** Retorna o nome do titular. */
    public String getNome() { return name; }
    /** Retorna o CPF normalizado; evite exibi-lo sem máscara. */
    public String getCpf() { return cpf; }
    /** Retorna o celular normalizado. */
    public String getCelular() { return mobile; }

    /** Altera o nome depois de validar que não está vazio. */
    public void setNome(String name) {
        this.name = validarNome(name);
    }

    /** Altera o CPF depois de validar formato e dígitos verificadores. */
    public void setCpf(String cpf) {
        this.cpf = validarCpf(cpf);
    }

    /** Calcula e confere os dois dígitos verificadores do CPF brasileiro. */
    private static boolean cpfValido(String cpf) {
        if (cpf.length() != 11 || cpf.chars().distinct().count() == 1) return false;
        int first = 0;
        for (int i = 0; i < 9; i++) first += (cpf.charAt(i) - '0') * (10 - i);
        int digit = (first * 10) % 11;
        if (digit == 10) digit = 0;
        if (digit != cpf.charAt(9) - '0') return false;
        int second = 0;
        for (int i = 0; i < 10; i++) second += (cpf.charAt(i) - '0') * (11 - i);
        digit = (second * 10) % 11;
        if (digit == 10) digit = 0;
        return digit == cpf.charAt(10) - '0';
    }

    /** Altera o celular após normalizar e validar sua quantidade de dígitos. */
    public void setCelular(String mobile) {
        this.mobile = validarCelular(mobile);
    }

    /** Atualiza todos os campos atomicamente: nenhuma alteração ocorre se houver erro. */
    public void setCadastro(String name, String cpf, String mobile) {
        String validName = validarNome(name);
        String validCpf = validarCpf(cpf);
        String validMobile = validarCelular(mobile);
        this.name = validName;
        this.cpf = validCpf;
        this.mobile = validMobile;
    }

    /** Sobrecarga que cadastra nome e CPF, deixando o celular vazio. */
    public void setCadastro(String name, String cpf) {
        String validName = validarNome(name);
        String validCpf = validarCpf(cpf);
        this.name = validName;
        this.cpf = validCpf;
        this.mobile = "";
    }

    /** Altera os três dados usando as mesmas validações do cadastro. */
    public void alterarDados(String name, String cpf, String mobile) {
        setCadastro(name, cpf, mobile);
    }

    /** Altera nome e celular sem modificar o CPF existente. */
    public void alterarDados(String name, String mobile) {
        String validName = validarNome(name);
        String validMobile = validarCelular(mobile);
        this.name = validName;
        this.mobile = validMobile;
    }

    /** Valida o nome e elimina espaços externos. */
    private static String validarNome(String name) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Nome obrigatório.");
        return name.trim();
    }

    /** Normaliza o CPF e confirma seus dígitos verificadores. */
    private static String validarCpf(String cpf) {
        String digits = somenteDigitos(cpf);
        if (!cpfValido(digits)) throw new IllegalArgumentException("CPF deve conter 11 dígitos válidos.");
        return digits;
    }

    /** Normaliza o telefone e exige uma quantidade plausível de dígitos. */
    private static String validarCelular(String mobile) {
        String digits = somenteDigitos(mobile);
        if (digits.length() < 10 || digits.length() > 13) {
            throw new IllegalArgumentException("Celular deve conter de 10 a 13 dígitos.");
        }
        return digits;
    }

    /** Remove formatação não numérica de CPF ou telefone. */
    private static String somenteDigitos(String value) {
        Objects.requireNonNull(value, "Valor obrigatório.");
        String digits = value.replaceAll("\\D", "");
        if (digits.isEmpty()) throw new IllegalArgumentException("Valor inválido.");
        return digits;
    }

    /** Exibe o cadastro com o CPF parcialmente oculto. */
    @Override
    public String toString() {
        return "DadosPessoais{name='" + name + "', cpf='***.***.***-" + cpf.substring(9) +
                "', mobile='" + mobile + "'}";
    }
}

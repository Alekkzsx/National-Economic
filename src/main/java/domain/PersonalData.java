package domain;

import java.util.Objects;

/**
 * Dados cadastrais básicos do titular.
 *
 * <p>O CPF é armazenado apenas com dígitos e validado pelos dígitos
 * verificadores. A representação textual mascara o documento para evitar
 * exposição acidental em logs ou listagens.</p>
 */
public class PersonalData {
    /** Nome completo do titular. */
    private String name;
    /** CPF normalizado, composto por onze dígitos. */
    private String cpf;
    /** Telefone normalizado, composto apenas por dígitos. */
    private String mobile;

    /** Cria os dados pessoais com nome, CPF e celular. */
    public PersonalData(String name, String cpf, String mobile) {
        this.name = validateName(name);
        this.cpf = validateCpf(cpf);
        this.mobile = validateMobile(mobile);
    }

    /** Cria os dados pessoais sem informar um celular. */
    public PersonalData(String name, String cpf) {
        this.name = validateName(name);
        this.cpf = validateCpf(cpf);
        this.mobile = "";
    }

    /** Accessor conciso para o nome. */
    public String name() { return name; }
    /** Accessor conciso para o CPF normalizado. */
    public String cpf() { return cpf; }
    /** Accessor conciso para o celular normalizado. */
    public String mobile() { return mobile; }
    /** Retorna o nome do titular. */
    public String getName() { return name; }
    /** Retorna o CPF normalizado; evite exibi-lo sem máscara. */
    public String getCpf() { return cpf; }
    /** Retorna o celular normalizado. */
    public String getMobile() { return mobile; }

    /** Altera o nome depois de validar que não está vazio. */
    public void setName(String name) {
        this.name = validateName(name);
    }

    /** Altera o CPF depois de validar formato e dígitos verificadores. */
    public void setCpf(String cpf) {
        this.cpf = validateCpf(cpf);
    }

    /** Calcula e confere os dois dígitos verificadores do CPF brasileiro. */
    private static boolean isValidCpf(String cpf) {
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
    public void setMobile(String mobile) {
        this.mobile = validateMobile(mobile);
    }

    /** Atualiza todos os campos atomicamente: nenhuma alteração ocorre se houver erro. */
    public void setCadastro(String name, String cpf, String mobile) {
        String validName = validateName(name);
        String validCpf = validateCpf(cpf);
        String validMobile = validateMobile(mobile);
        this.name = validName;
        this.cpf = validCpf;
        this.mobile = validMobile;
    }

    /** Sobrecarga que cadastra nome e CPF, deixando o celular vazio. */
    public void setCadastro(String name, String cpf) {
        String validName = validateName(name);
        String validCpf = validateCpf(cpf);
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
        String validName = validateName(name);
        String validMobile = validateMobile(mobile);
        this.name = validName;
        this.mobile = validMobile;
    }

    /** Valida o nome e elimina espaços externos. */
    private static String validateName(String name) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Nome obrigatório.");
        return name.trim();
    }

    /** Normaliza o CPF e confirma seus dígitos verificadores. */
    private static String validateCpf(String cpf) {
        String digits = digitsOnly(cpf);
        if (!isValidCpf(digits)) throw new IllegalArgumentException("CPF deve conter 11 dígitos válidos.");
        return digits;
    }

    /** Normaliza o telefone e exige uma quantidade plausível de dígitos. */
    private static String validateMobile(String mobile) {
        String digits = digitsOnly(mobile);
        if (digits.length() < 10 || digits.length() > 13) {
            throw new IllegalArgumentException("Celular deve conter de 10 a 13 dígitos.");
        }
        return digits;
    }

    /** Remove formatação não numérica de CPF ou telefone. */
    private static String digitsOnly(String value) {
        Objects.requireNonNull(value, "Valor obrigatório.");
        String digits = value.replaceAll("\\D", "");
        if (digits.isEmpty()) throw new IllegalArgumentException("Valor inválido.");
        return digits;
    }

    /** Exibe o cadastro com o CPF parcialmente oculto. */
    @Override
    public String toString() {
        return "PersonalData{name='" + name + "', cpf='***.***.***-" + cpf.substring(9) +
                "', mobile='" + mobile + "'}";
    }
}

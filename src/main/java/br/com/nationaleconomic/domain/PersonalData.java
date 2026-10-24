package br.com.nationaleconomic.domain;

import java.util.Objects;

public final class PersonalData {
    private String name;
    private String cpf;
    private String mobile;

    public PersonalData(String name, String cpf, String mobile) {
        setName(name);
        setCpf(cpf);
        setMobile(mobile);
    }

    public String name() { return name; }
    public String cpf() { return cpf; }
    public String mobile() { return mobile; }
    public String getName() { return name; }
    public String getCpf() { return cpf; }
    public String getMobile() { return mobile; }

    public void setName(String name) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Nome obrigatório.");
        this.name = name.trim();
    }

    public void setCpf(String cpf) {
        String digits = digitsOnly(cpf);
        if (!isValidCpf(digits)) {
            throw new IllegalArgumentException("CPF deve conter 11 dígitos válidos.");
        }
        this.cpf = digits;
    }

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

    public void setMobile(String mobile) {
        String digits = digitsOnly(mobile);
        if (digits.length() < 10 || digits.length() > 13) {
            throw new IllegalArgumentException("Celular deve conter de 10 a 13 dígitos.");
        }
        this.mobile = digits;
    }

    private static String digitsOnly(String value) {
        Objects.requireNonNull(value, "Valor obrigatório.");
        String digits = value.replaceAll("\\D", "");
        if (digits.isEmpty()) throw new IllegalArgumentException("Valor inválido.");
        return digits;
    }

    @Override
    public String toString() {
        return "PersonalData{name='" + name + "', cpf='***.***.***-" + cpf.substring(9) +
                "', mobile='" + mobile + "'}";
    }
}

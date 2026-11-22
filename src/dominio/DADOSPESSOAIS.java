package dominio;

import dominio.util.ValidacaoDominio;

import java.util.Objects;

/**
 * Item 5 — Gestão de dados pessoais do cliente (nome, CPF validado pela Receita Federal e celular).
 *
 * <p>Classe passível de herança, sem despacho virtual nos construtores e com mascaramento
 * de CPF no {@link #toString()} para proteção de dados (LGPD).</p>
 */
public class DADOSPESSOAIS {

    private String nome;
    private String cpf;
    private String celular;

    /** Construtor protegido para herança segura. */
    protected DADOSPESSOAIS() {
        this.nome = "";
        this.cpf = "";
        this.celular = "";
    }

    /** Cria os dados pessoais com nome, CPF e celular validados. */
    public DADOSPESSOAIS(String nome, String cpf, String celular) {
        aplicarCampos(validarNome(nome), validarCpfInterno(cpf), validarCelular(celular));
    }

    /** Cria os dados pessoais com nome e CPF, deixando o celular vazio. */
    public DADOSPESSOAIS(String nome, String cpf) {
        aplicarCampos(validarNome(nome), validarCpfInterno(cpf), "");
    }

    // -------------------------------------------------------------------------
    // Métodos polimórficos de cadastro e alteração (Item 5)
    // -------------------------------------------------------------------------

    public void setCadastro(String nome, String cpf, String celular) {
        aplicarCampos(validarNome(nome), validarCpfInterno(cpf), validarCelular(celular));
    }

    public void setCadastro(String nome, String cpf) {
        aplicarCampos(validarNome(nome), validarCpfInterno(cpf), "");
    }

    public void alterarDados(String nome, String cpf, String celular) {
        setCadastro(nome, cpf, celular);
    }

    public void alterarDados(String nome, String celular) {
        aplicarCampos(validarNome(nome), this.cpf, validarCelular(celular));
    }

    // -------------------------------------------------------------------------
    // Getters, Setters e formatação
    // -------------------------------------------------------------------------

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = validarNome(nome); }

    public String getCpf() { return cpf; }
    public void setCpf(String cpf) { this.cpf = validarCpfInterno(cpf); }

    public String getCelular() { return celular; }
    public void setCelular(String celular) { this.celular = validarCelular(celular); }

    public String nome() { return nome; }
    public String cpf() { return cpf; }
    public String celular() { return celular; }

    public String getCpfMascarado() { return mascararCpf(this.cpf); }
    public String getCpfFormatado() { return formatarCpf(this.cpf); }

    public String getCelularFormatado() {
        if (celular == null || celular.isEmpty()) return "";
        if (celular.length() == 11) {
            return String.format("(%s) %s-%s", celular.substring(0, 2), celular.substring(2, 7), celular.substring(7));
        }
        if (celular.length() == 10) {
            return String.format("(%s) %s-%s", celular.substring(0, 2), celular.substring(2, 6), celular.substring(6));
        }
        return celular;
    }

    // Aliases mantidos para compatibilidade de testes
    public String getName() { return getNome(); }
    public void setName(String name) { setNome(name); }
    public String name() { return getNome(); }
    public String getMobile() { return getCelular(); }
    public void setMobile(String mobile) { setCelular(mobile); }
    public String mobile() { return getCelular(); }

    // -------------------------------------------------------------------------
    // Validação algorítmica e formatação estática de CPF
    // -------------------------------------------------------------------------

    /** Valida CPF segundo os dois dígitos verificadores em módulo 11 da Receita Federal. */
    public static boolean validarCpf(String cpf) {
        if (cpf == null) return false;
        String digitos = ValidacaoDominio.apenasDigitos(cpf);
        return digitos.length() == 11 && verificarDigitosCpf(digitos);
    }

    public static boolean ehCpfValido(String cpf) {
        return validarCpf(cpf);
    }

    public static String formatarCpf(String cpf) {
        if (cpf == null) return "";
        String digitos = ValidacaoDominio.apenasDigitos(cpf);
        if (digitos.length() != 11) return cpf;
        return String.format("%s.%s.%s-%s",
                digitos.substring(0, 3), digitos.substring(3, 6),
                digitos.substring(6, 9), digitos.substring(9));
    }

    public static String mascararCpf(String cpf) {
        String digitos = ValidacaoDominio.apenasDigitos(cpf);
        return digitos.length() == 11 ? "***.***.***-" + digitos.substring(9) : "***.***.***-**";
    }

    private static boolean verificarDigitosCpf(String digitos) {
        if (ValidacaoDominio.todosCaracteresIguais(digitos)) {
            return false;
        }
        int d1 = ValidacaoDominio.calcularDigitoVerificadorCpf(digitos, 10);
        int d2 = ValidacaoDominio.calcularDigitoVerificadorCpf(digitos, 11);
        return d1 == (digitos.charAt(9) - '0') && d2 == (digitos.charAt(10) - '0');
    }

    private void aplicarCampos(String novoNome, String novoCpf, String novoCelular) {
        this.nome = novoNome;
        this.cpf = novoCpf;
        this.celular = novoCelular;
    }

    private static String validarNome(String nome) {
        return ValidacaoDominio.textoObrigatorio(nome, "Nome do cliente é obrigatório e não pode ser vazio.");
    }

    private static String validarCpfInterno(String cpf) {
        String digitos = ValidacaoDominio.normalizarCpf(cpf);
        if (!verificarDigitosCpf(digitos)) {
            throw new IllegalArgumentException(
                    "CPF inválido perante os dígitos verificadores da Receita Federal: '" + cpf + "'");
        }
        return digitos;
    }

    private static String validarCelular(String celular) {
        if (celular == null) {
            throw new IllegalArgumentException("Celular não pode ser nulo.");
        }
        String digitos = ValidacaoDominio.apenasDigitos(celular);
        if (digitos.length() < 10 || digitos.length() > 13) {
            throw new IllegalArgumentException(
                    "Celular deve conter de 10 a 13 dígitos numéricos com DDD. Informado: '" + celular + "'");
        }
        return digitos;
    }

    @Override
    public String toString() {
        return "DADOSPESSOAIS{nome='" + nome + "', cpf='" + getCpfMascarado() + "', celular='" + celular + "'}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DADOSPESSOAIS that)) return false;
        return Objects.equals(this.cpf, that.cpf);
    }

    @Override
    public int hashCode() {
        return Objects.hash(cpf);
    }
}

package dominio;

import java.util.Objects;

/**
 * Representa os Dados Pessoais e Cadastrais de um cliente titular,
 * em conformidade com o Item 5 da Atividade Prática Acadêmica (Requisito R2.2).
 *
 * <p>Esta classe é passível de herança (não-final), encapsula o nome completo, o CPF
 * validado algorítmicamente segundo as regras da Receita Federal do Brasil (11 dígitos,
 * pesos 10..2 e 11..2, rejeição de repetidos) e o telefone celular com DDD.</p>
 *
 * <p>Implementa sobrecargas polimórficas obrigatórias para cadastro e alteração de dados,
 * garantia de atomicidade transacional e mascaramento do documento no método {@link #toString()}
 * para conformidade com a privacidade de dados (LGPD).</p>
 *
 * <p>A classe adota práticas defensivas de herança: seus construtores não despacham chamadas
 * a métodos virtuais sobrescrevíveis, prevenindo corrupção de estado em subclasses.</p>
 */
public class DADOSPESSOAIS {

    /** Nome completo do cliente titular. */
    private String nome;

    /** CPF normalizado composto estritamente por 11 dígitos numéricos validados. */
    private String cpf;

    /** Telefone celular com DDD composto apenas por dígitos numéricos, ou vazio. */
    private String celular;

    /**
     * Construtor protegido padrão para permitir herança segura por subclasses e desserialização.
     */
    protected DADOSPESSOAIS() {
        this.nome = "";
        this.cpf = "";
        this.celular = "";
    }

    /**
     * Cria os dados pessoais com nome, CPF e celular.
     *
     * <p>Construtor seguro contra substituição virtual: valida e atribui os campos diretamente
     * sem despachar chamadas a métodos sobrescrevíveis.</p>
     *
     * @param nome o nome completo do titular (obrigatório, não nulo e não em branco)
     * @param cpf o CPF do titular formatado ou em dígitos (deve ser matematicamente válido)
     * @param celular o telefone celular com DDD (10 a 13 dígitos numéricos)
     * @throws IllegalArgumentException se qualquer parâmetro for inválido
     */
    public DADOSPESSOAIS(String nome, String cpf, String celular) {
        String nomeValidado = validarNomeInterno(nome);
        String cpfValidado = validarCpfInterno(cpf);
        String celularValidado = validarCelularInterno(celular);
        aplicarCampos(nomeValidado, cpfValidado, celularValidado);
    }

    /**
     * Cria os dados pessoais com nome e CPF, deixando o telefone celular vazio.
     *
     * @param nome o nome completo do titular (obrigatório, não nulo e não em branco)
     * @param cpf o CPF do titular formatado ou em dígitos (deve ser matematicamente válido)
     * @throws IllegalArgumentException se o nome ou o CPF forem inválidos
     */
    public DADOSPESSOAIS(String nome, String cpf) {
        String nomeValidado = validarNomeInterno(nome);
        String cpfValidado = validarCpfInterno(cpf);
        aplicarCampos(nomeValidado, cpfValidado, "");
    }

    // =========================================================================
    // Métodos Polimórficos de Cadastro (Item 5)
    // =========================================================================

    /**
     * Atualiza atomicamente todos os dados cadastrais (nome, CPF e celular).
     *
     * <p>Se qualquer dado for inválido, uma exceção é lançada e nenhuma alteração ocorre.</p>
     *
     * @param nome o novo nome completo do titular
     * @param cpf o novo CPF do titular
     * @param celular o novo telefone celular do titular
     * @throws IllegalArgumentException se qualquer parâmetro for inválido
     */
    public void setCadastro(String nome, String cpf, String celular) {
        String nomeValidado = validarNomeInterno(nome);
        String cpfValidado = validarCpfInterno(cpf);
        String celularValidado = validarCelularInterno(celular);
        aplicarCampos(nomeValidado, cpfValidado, celularValidado);
    }

    /**
     * Sobrecarga polimórfica que atualiza nome e CPF, redefinindo o celular para vazio.
     *
     * @param nome o novo nome completo do titular
     * @param cpf o novo CPF do titular
     * @throws IllegalArgumentException se o nome ou o CPF forem inválidos
     */
    public void setCadastro(String nome, String cpf) {
        String nomeValidado = validarNomeInterno(nome);
        String cpfValidado = validarCpfInterno(cpf);
        aplicarCampos(nomeValidado, cpfValidado, "");
    }

    // =========================================================================
    // Métodos Polimórficos de Alteração de Dados (Item 5)
    // =========================================================================

    /**
     * Altera todos os dados pessoais do titular.
     *
     * @param nome o novo nome completo
     * @param cpf o novo CPF
     * @param celular o novo celular
     * @throws IllegalArgumentException se qualquer parâmetro for inválido
     */
    public void alterarDados(String nome, String cpf, String celular) {
        setCadastro(nome, cpf, celular);
    }

    /**
     * Sobrecarga polimórfica que altera o nome e o celular preservando o CPF inalterado.
     *
     * @param nome o novo nome completo
     * @param celular o novo telefone celular
     * @throws IllegalArgumentException se o nome ou o celular forem inválidos
     */
    public void alterarDados(String nome, String celular) {
        String nomeValidado = validarNomeInterno(nome);
        String celularValidado = validarCelularInterno(celular);
        aplicarCampos(nomeValidado, this.cpf, celularValidado);
    }

    // =========================================================================
    // Getters e Setters Padronizados em Português do Brasil
    // =========================================================================

    /**
     * Retorna o nome completo do cliente titular.
     *
     * @return o nome do titular
     */
    public String getNome() {
        return nome;
    }

    /**
     * Atualiza o nome do cliente titular após validação.
     *
     * @param nome o novo nome completo
     * @throws IllegalArgumentException se o nome for nulo ou em branco
     */
    public void setNome(String nome) {
        this.nome = validarNomeInterno(nome);
    }

    /**
     * Retorna o CPF normalizado composto estritamente por 11 dígitos numéricos.
     *
     * @return o CPF de 11 dígitos
     */
    public String getCpf() {
        return cpf;
    }

    /**
     * Atualiza o CPF do cliente titular após validação algorítmica.
     *
     * @param cpf o novo CPF (formatado ou não)
     * @throws IllegalArgumentException se o CPF for matematicamente inválido
     */
    public void setCpf(String cpf) {
        this.cpf = validarCpfInterno(cpf);
    }

    /**
     * Retorna o telefone celular normalizado.
     *
     * @return o telefone celular apenas com dígitos, ou string vazia
     */
    public String getCelular() {
        return celular;
    }

    /**
     * Atualiza o telefone celular do cliente titular após validação de dígitos.
     *
     * @param celular o novo celular com DDD
     * @throws IllegalArgumentException se o celular não contiver de 10 a 13 dígitos
     */
    public void setCelular(String celular) {
        this.celular = validarCelularInterno(celular);
    }

    // =========================================================================
    // Accessors Concisos e Métodos Utilitários de Formatação / Máscara
    // =========================================================================

    /**
     * Accessor conciso para o nome.
     *
     * @return o nome do titular
     */
    public String nome() {
        return nome;
    }

    /**
     * Accessor conciso para o CPF.
     *
     * @return o CPF de 11 dígitos
     */
    public String cpf() {
        return cpf;
    }

    /**
     * Accessor conciso para o celular.
     *
     * @return o telefone celular apenas com dígitos, ou vazio
     */
    public String celular() {
        return celular;
    }

    /**
     * Retorna o CPF com os primeiros 9 dígitos ocultos para preservação de privacidade.
     *
     * @return o CPF mascarado no formato "***.***.***-XX"
     */
    public String getCpfMascarado() {
        return mascararCpf(this.cpf);
    }

    /**
     * Retorna o CPF formatado no padrão oficial "XXX.XXX.XXX-XX".
     *
     * @return o CPF formatado com pontuação
     */
    public String getCpfFormatado() {
        return formatarCpf(this.cpf);
    }

    /**
     * Retorna o telefone celular formatado no padrão brasileiro "(XX) XXXXX-XXXX" ou "(XX) XXXX-XXXX".
     *
     * @return o celular formatado, ou string vazia se não informado
     */
    public String getCelularFormatado() {
        if (celular == null || celular.isEmpty()) {
            return "";
        }
        if (celular.length() == 11) {
            return String.format("(%s) %s-%s",
                    celular.substring(0, 2),
                    celular.substring(2, 7),
                    celular.substring(7, 11));
        }
        if (celular.length() == 10) {
            return String.format("(%s) %s-%s",
                    celular.substring(0, 2),
                    celular.substring(2, 6),
                    celular.substring(6, 10));
        }
        return celular;
    }

    // =========================================================================
    // Aliases de Compatibilidade com Código Legado (modelo.DadosPessoais)
    // =========================================================================

    /**
     * Alias de compatibilidade com o código original para o nome.
     *
     * @return o nome do titular
     */
    public String getName() {
        return getNome();
    }

    /**
     * Alias de compatibilidade com o código original para alteração do nome.
     *
     * @param name o novo nome
     */
    public void setName(String name) {
        setNome(name);
    }

    /**
     * Alias de compatibilidade com o código original para o celular.
     *
     * @return o celular normalizado
     */
    public String getMobile() {
        return getCelular();
    }

    /**
     * Alias de compatibilidade com o código original para alteração do celular.
     *
     * @param mobile o novo telefone
     */
    public void setMobile(String mobile) {
        setCelular(mobile);
    }

    /**
     * Alias de compatibilidade com o código original para o celular.
     *
     * @return o celular normalizado
     */
    public String mobile() {
        return getCelular();
    }

    /**
     * Alias de compatibilidade com o código original para o nome.
     *
     * @return o nome do titular
     */
    public String name() {
        return getNome();
    }

    // =========================================================================
    // Validações Algorítmicas Estáticas e Utilitários de CPF
    // =========================================================================

    /**
     * Valida formalmente se uma cadeia de caracteres representa um CPF brasileiro válido.
     *
     * <p>Executa a sanitização de pontuação, verificação de 11 dígitos, rejeição de repetidos
     * ("00000000000" até "99999999999") e a apuração aritmética dos 2 dígitos verificadores
     * em módulo 11 com pesos decrescentes 10..2 e 11..2.</p>
     *
     * @param cpf a string com o CPF formatado ou em dígitos
     * @return {@code true} se o CPF for matematicamente autêntico; {@code false} caso contrário
     */
    public static boolean validarCpf(String cpf) {
        if (cpf == null) {
            return false;
        }
        String digitos = apenasDigitos(cpf);
        if (digitos.length() != 11) {
            return false;
        }

        // Rejeita sequências de dígitos repetidos
        boolean todosIguais = true;
        for (int i = 1; i < 11; i++) {
            if (digitos.charAt(i) != digitos.charAt(0)) {
                todosIguais = false;
                break;
            }
        }
        if (todosIguais) {
            return false;
        }

        // 1º Dígito Verificador (pesos 10 a 2)
        int soma1 = 0;
        for (int i = 0; i < 9; i++) {
            soma1 += (digitos.charAt(i) - '0') * (10 - i);
        }
        int d1 = (soma1 * 10) % 11;
        if (d1 == 10) {
            d1 = 0;
        }
        if (d1 != (digitos.charAt(9) - '0')) {
            return false;
        }

        // 2º Dígito Verificador (pesos 11 a 2)
        int soma2 = 0;
        for (int i = 0; i < 10; i++) {
            soma2 += (digitos.charAt(i) - '0') * (11 - i);
        }
        int d2 = (soma2 * 10) % 11;
        if (d2 == 10) {
            d2 = 0;
        }
        return d2 == (digitos.charAt(10) - '0');
    }

    /**
     * Alias semântico em português para validação booleana de CPF.
     *
     * @param cpf a string com o CPF
     * @return true se o CPF for válido
     */
    public static boolean ehCpfValido(String cpf) {
        return validarCpf(cpf);
    }

    /**
     * Formata um CPF normalizado no padrão visual "XXX.XXX.XXX-XX".
     *
     * @param cpf string de CPF (11 dígitos ou formatada)
     * @return o CPF com a máscara oficial de pontuação
     */
    public static String formatarCpf(String cpf) {
        if (cpf == null) {
            return "";
        }
        String digitos = apenasDigitos(cpf);
        if (digitos.length() != 11) {
            return cpf;
        }
        return String.format("%s.%s.%s-%s",
                digitos.substring(0, 3),
                digitos.substring(3, 6),
                digitos.substring(6, 9),
                digitos.substring(9, 11));
    }

    /**
     * Mascara os 9 primeiros dígitos do CPF para proteção em logs e telas.
     *
     * @param cpf o CPF a ser protegido
     * @return string no padrão "***.***.***-XX"
     */
    public static String mascararCpf(String cpf) {
        if (cpf == null || cpf.isEmpty()) {
            return "***.***.***-**";
        }
        String digitos = apenasDigitos(cpf);
        if (digitos.length() == 11) {
            return "***.***.***-" + digitos.substring(9);
        }
        return "***.***.***-**";
    }

    // =========================================================================
    // Validações Defensivas Internas
    // =========================================================================

    private void aplicarCampos(String novoNome, String novoCpf, String novoCelular) {
        this.nome = novoNome;
        this.cpf = novoCpf;
        this.celular = novoCelular;
    }

    private static String validarNomeInterno(String nome) {
        if (nome == null || nome.trim().isEmpty()) {
            throw new IllegalArgumentException("Nome do cliente é obrigatório e não pode ser vazio.");
        }
        return nome.trim();
    }

    private static String validarCpfInterno(String cpf) {
        if (cpf == null) {
            throw new IllegalArgumentException("CPF é obrigatório e não pode ser nulo.");
        }
        String digitos = apenasDigitos(cpf);
        if (digitos.length() != 11) {
            throw new IllegalArgumentException(
                    "CPF deve conter exatamente 11 dígitos numéricos. Informado: '" + cpf + "'");
        }
        if (!validarCpf(digitos)) {
            throw new IllegalArgumentException(
                    "CPF inválido perante os dígitos verificadores da Receita Federal: '" + cpf + "'");
        }
        return digitos;
    }

    private static String validarCelularInterno(String celular) {
        if (celular == null) {
            throw new IllegalArgumentException("Celular não pode ser nulo.");
        }
        String digitos = apenasDigitos(celular);
        if (digitos.isEmpty()) {
            throw new IllegalArgumentException("Celular deve conter dígitos numéricos válidos.");
        }
        if (digitos.length() < 10 || digitos.length() > 13) {
            throw new IllegalArgumentException(
                    "Celular deve conter de 10 a 13 dígitos numéricos com DDD. Informado: '" + celular + "'");
        }
        return digitos;
    }

    private static String apenasDigitos(String valor) {
        if (valor == null) {
            return "";
        }
        return valor.replaceAll("\\D", "");
    }

    // =========================================================================
    // Métodos Canônicos: toString, equals e hashCode
    // =========================================================================

    /**
     * Representação textual dos dados pessoais com mascaramento rigoroso do CPF.
     *
     * <p>Garante que o CPF não seja exposto integralmente, atendendo ao Item 5 da atividade
     * e aos requisitos de segurança e privacidade.</p>
     *
     * @return texto formatado contendo nome, CPF mascarado e celular
     */
    @Override
    public String toString() {
        return "DADOSPESSOAIS{nome='" + nome + "', cpf='" + getCpfMascarado() +
                "', celular='" + celular + "'}";
    }

    /**
     * Compara a igualdade entre duas instâncias de dados pessoais.
     *
     * <p>Dois titulares com o mesmo CPF normalizado representam o mesmo indivíduo perante o sistema.</p>
     *
     * @param o o objeto a comparar
     * @return true se ambos possuírem o mesmo CPF
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || !(o instanceof DADOSPESSOAIS)) return false;
        DADOSPESSOAIS that = (DADOSPESSOAIS) o;
        return Objects.equals(this.cpf, that.cpf);
    }

    /**
     * Código hash gerado com base no CPF unívoco do titular.
     *
     * @return hash code do CPF
     */
    @Override
    public int hashCode() {
        return Objects.hash(cpf);
    }
}

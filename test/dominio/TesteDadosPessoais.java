package test.dominio;

import static test.Afirmacao.*;

import dominio.DADOSPESSOAIS;

/**
 * Suíte de testes autônomos para a classe {@link DADOSPESSOAIS} (Item 5 da Atividade Prática).
 *
 * <p>Valida de ponta a ponta:
 * <ul>
 *   <li>Construtores completos e sobrecarregados sem celular.</li>
 *   <li>Validação algorítmica rigorosa de CPF (pesos 10..2 e 11..2).</li>
 *   <li>Rejeição estrita de CPFs com todos os dígitos repetidos ("00000000000" a "99999999999").</li>
 *   <li>Sanitização de máscara de CPF e telefone celular.</li>
 *   <li>Sobrecargas polimórficas de cadastro ({@code setCadastro}) e alteração ({@code alterarDados}).</li>
 *   <li>Atomicidade de atualização cadastral sob falhas.</li>
 *   <li>Mascaramento do CPF na sobrescrita de {@code toString()}.</li>
 *   <li>Segurança contra despacho de métodos virtuais em herança.</li>
 *   <li>Igualdade semântica baseada no CPF e interoperabilidade reversa.</li>
 * </ul>
 * </p>
 */
public final class TesteDadosPessoais {

    public static void main(String[] args) {
        System.out.println(">>> Executando TesteDadosPessoais (Item 5 - DADOSPESSOAIS)...");
        long inicio = System.currentTimeMillis();
        int testesExecutados = 0;

        testarInstanciacaoEGettersBasicos(); testesExecutados++;
        testarConstrutorSemCelular(); testesExecutados++;
        testarPolimorfismoSetCadastro(); testesExecutados++;
        testarPolimorfismoAlterarDados(); testesExecutados++;
        testarAtomicidadeFalhaCadastro(); testesExecutados++;
        testarAtomicidadeFalhaAlteracao(); testesExecutados++;
        testarAlgoritmoCpfValidoDiversos(); testesExecutados++;
        testarRejeicaoCpfDigitosRepetidos(); testesExecutados++;
        testarRejeicaoCpfDigitosVerificadoresIncorretos(); testesExecutados++;
        testarRejeicaoCpfTamanhoEFormatoInvalido(); testesExecutados++;
        testarRejeicaoNomeInvalido(); testesExecutados++;
        testarRejeicaoCelularInvalido(); testesExecutados++;
        testarMascaramentoCpfEmToString(); testesExecutados++;
        testarFormatacaoCpfECelular(); testesExecutados++;
        testarHerancaSeguraSemEscapeVirtual(); testesExecutados++;
        testarIgualdadeEHashCode(); testesExecutados++;
        testarAliasesLegadosCompatibilidade(); testesExecutados++;

        long duracao = System.currentTimeMillis() - inicio;
        System.out.printf(">>> TesteDadosPessoais CONCLUÍDO com SUCESSO: %d métodos verificados (%d ms)%n%n",
                testesExecutados, duracao);
    }

    /**
     * Tier 1: Construtor completo com formatação, normalização e getters em PT-BR.
     */
    public static void testarInstanciacaoEGettersBasicos() {
        DADOSPESSOAIS dados = new DADOSPESSOAIS("Carlos Drummond", "529.982.247-25", "(11) 98765-4321");

        afirmarIgual("Carlos Drummond", dados.getNome(), "nome deve ser preservado");
        afirmarIgual("52998224725", dados.getCpf(), "CPF deve ser normalizado em 11 dígitos");
        afirmarIgual("11987654321", dados.getCelular(), "celular deve ser normalizado apenas com dígitos");
        afirmarIgual("Carlos Drummond", dados.nome(), "accessor conciso nome()");
        afirmarIgual("52998224725", dados.cpf(), "accessor conciso cpf()");
        afirmarIgual("11987654321", dados.celular(), "accessor conciso celular()");
    }

    /**
     * Tier 1: Construtor sobrecarregado sem celular (celular inicializado como vazio).
     */
    public static void testarConstrutorSemCelular() {
        DADOSPESSOAIS dados = new DADOSPESSOAIS("Machado de Assis", "52998224725");

        afirmarIgual("Machado de Assis", dados.getNome(), "nome do titular");
        afirmarIgual("52998224725", dados.getCpf(), "CPF do titular");
        afirmarVerdadeiro(dados.getCelular().isEmpty(), "celular deve ser string vazia quando omitido");
        afirmarNaoNulo(dados.getCelular(), "celular nunca pode ser nulo");
    }

    /**
     * Tier 3: Sobrecargas polimórficas de setCadastro (3 argumentos e 2 argumentos).
     */
    public static void testarPolimorfismoSetCadastro() {
        DADOSPESSOAIS dados = new DADOSPESSOAIS("Inicial", "52998224725");

        // Sobrecarga de 3 argumentos
        dados.setCadastro("Clarice Lispector", "00000000191", "(21) 99887-6655");
        afirmarIgual("Clarice Lispector", dados.getNome(), "nome atualizado via 3 args");
        afirmarIgual("00000000191", dados.getCpf(), "CPF atualizado via 3 args");
        afirmarIgual("21998876655", dados.getCelular(), "celular atualizado via 3 args");

        // Sobrecarga de 2 argumentos (reseta celular para vazio)
        dados.setCadastro("Guimarães Rosa", "52998224725");
        afirmarIgual("Guimarães Rosa", dados.getNome(), "nome atualizado via 2 args");
        afirmarIgual("52998224725", dados.getCpf(), "CPF atualizado via 2 args");
        afirmarVerdadeiro(dados.getCelular().isEmpty(), "celular deve ser resetado para vazio no setCadastro(2 args)");
    }

    /**
     * Tier 3: Sobrecargas polimórficas de alterarDados (3 argumentos e 2 argumentos).
     */
    public static void testarPolimorfismoAlterarDados() {
        DADOSPESSOAIS dados = new DADOSPESSOAIS("Cecília Meireles", "52998224725", "11987654321");

        // Sobrecarga de 2 argumentos: altera nome e celular mantendo o CPF original
        dados.alterarDados("Cecília Benevides de Carvalho Meireles", "(11) 91234-5678");
        afirmarIgual("Cecília Benevides de Carvalho Meireles", dados.getNome(), "nome atualizado");
        afirmarIgual("52998224725", dados.getCpf(), "CPF deve permanecer o original");
        afirmarIgual("11912345678", dados.getCelular(), "celular atualizado");

        // Sobrecarga de 3 argumentos: altera os três campos
        dados.alterarDados("Rachel de Queiroz", "00000000191", "85991112233");
        afirmarIgual("Rachel de Queiroz", dados.getNome(), "nome atualizado via 3 args");
        afirmarIgual("00000000191", dados.getCpf(), "CPF atualizado via 3 args");
        afirmarIgual("85991112233", dados.getCelular(), "celular atualizado via 3 args");
    }

    /**
     * Tier 5 (Adversarial): Atomicidade transacional em setCadastro.
     */
    public static void testarAtomicidadeFalhaCadastro() {
        DADOSPESSOAIS dados = new DADOSPESSOAIS("Graciliano Ramos", "52998224725", "11987654321");

        // Tentativa de cadastro com CPF inválido
        afirmarRejeicao(() -> dados.setCadastro("Novo Nome", "11111111111", "11999998888"),
                IllegalArgumentException.class, "CPF repetido deve falhar");

        // Confere que nenhum dado foi corrompido
        afirmarIgual("Graciliano Ramos", dados.getNome(), "nome deve permanecer inalterado");
        afirmarIgual("52998224725", dados.getCpf(), "CPF deve permanecer inalterado");
        afirmarIgual("11987654321", dados.getCelular(), "celular deve permanecer inalterado");

        // Tentativa de cadastro com celular inválido (muito curto)
        afirmarRejeicao(() -> dados.setCadastro("Outro Nome", "00000000191", "12345"),
                IllegalArgumentException.class, "celular curto deve falhar");

        afirmarIgual("Graciliano Ramos", dados.getNome(), "nome intacto após erro de celular");
        afirmarIgual("52998224725", dados.getCpf(), "CPF intacto após erro de celular");
    }

    /**
     * Tier 5 (Adversarial): Atomicidade transacional em alterarDados.
     */
    public static void testarAtomicidadeFalhaAlteracao() {
        DADOSPESSOAIS dados = new DADOSPESSOAIS("Jorge Amado", "52998224725", "71988887777");

        // Tentativa de alterar nome para vazio
        afirmarRejeicao(() -> dados.alterarDados("   ", "71999990000"),
                IllegalArgumentException.class, "nome vazio deve falhar");

        afirmarIgual("Jorge Amado", dados.getNome(), "nome original preservado");
        afirmarIgual("71988887777", dados.getCelular(), "celular original preservado");
    }

    /**
     * Tier 2: Validação algorítmica de CPFs válidos diversos da Receita Federal.
     */
    public static void testarAlgoritmoCpfValidoDiversos() {
        String[] cpfsValidos = {
            "52998224725",
            "529.982.247-25",
            "00000000191",
            "000.000.001-91",
            "11144477735",
            "111.444.777-35"
        };

        for (String cpf : cpfsValidos) {
            afirmarVerdadeiro(DADOSPESSOAIS.validarCpf(cpf), "CPF deveria ser considerado válido: " + cpf);
            afirmarVerdadeiro(DADOSPESSOAIS.ehCpfValido(cpf), "alias ehCpfValido deveria retornar true: " + cpf);
            DADOSPESSOAIS d = new DADOSPESSOAIS("Titular", cpf);
            afirmarNaoNulo(d.getCpf(), "deve instanciar com sucesso para CPF válido");
        }
    }

    /**
     * Tier 5 (Adversarial): Rejeição estrita de CPFs com todos os dígitos repetidos.
     */
    public static void testarRejeicaoCpfDigitosRepetidos() {
        String[] repetidos = {
            "00000000000", "000.000.000-00",
            "11111111111", "111.111.111-11",
            "22222222222", "222.222.222-22",
            "33333333333", "333.333.333-33",
            "44444444444", "444.444.444-44",
            "55555555555", "555.555.555-55",
            "66666666666", "666.666.666-66",
            "77777777777", "777.777.777-77",
            "88888888888", "888.888.888-88",
            "99999999999", "999.999.999-99"
        };

        for (String cpfRepetido : repetidos) {
            afirmarFalso(DADOSPESSOAIS.validarCpf(cpfRepetido),
                    "CPF repetido não pode ser válido perante a Receita Federal: " + cpfRepetido);
            afirmarRejeicao(() -> new DADOSPESSOAIS("Teste", cpfRepetido),
                    IllegalArgumentException.class, "deve rejeitar no construtor: " + cpfRepetido);
        }
    }

    /**
     * Tier 5 (Adversarial): Rejeição de CPFs com dígitos verificadores incorretos.
     */
    public static void testarRejeicaoCpfDigitosVerificadoresIncorretos() {
        // Base válida: 529.982.247-25
        String dv1Errado = "52998224705";
        String dv2Errado = "52998224720";
        String ambosDvsErrados = "52998224700";

        afirmarFalso(DADOSPESSOAIS.validarCpf(dv1Errado), "1º DV incorreto");
        afirmarFalso(DADOSPESSOAIS.validarCpf(dv2Errado), "2º DV incorreto");
        afirmarFalso(DADOSPESSOAIS.validarCpf(ambosDvsErrados), "ambos DVs incorretos");

        afirmarRejeicao(() -> new DADOSPESSOAIS("Teste", dv1Errado),
                IllegalArgumentException.class, "deve rejeitar 1º DV inválido");
        afirmarRejeicao(() -> new DADOSPESSOAIS("Teste", dv2Errado),
                IllegalArgumentException.class, "deve rejeitar 2º DV inválido");
    }

    /**
     * Tier 5 (Adversarial): Rejeição de CPFs com tamanhos ou formatações espúrias.
     */
    public static void testarRejeicaoCpfTamanhoEFormatoInvalido() {
        afirmarFalso(DADOSPESSOAIS.validarCpf(null), "null deve retornar false");
        afirmarFalso(DADOSPESSOAIS.validarCpf(""), "vazio deve retornar false");
        afirmarFalso(DADOSPESSOAIS.validarCpf("123"), "tamanho insuficiente");
        afirmarFalso(DADOSPESSOAIS.validarCpf("529982247250"), "tamanho excessivo (12 dígitos)");
        afirmarFalso(DADOSPESSOAIS.validarCpf("abc.def.ghi-jk"), "letras sem dígitos");

        afirmarRejeicao(() -> new DADOSPESSOAIS("Teste", null),
                IllegalArgumentException.class, "CPF null no construtor");
        afirmarRejeicao(() -> new DADOSPESSOAIS("Teste", ""),
                IllegalArgumentException.class, "CPF vazio no construtor");
        afirmarRejeicao(() -> new DADOSPESSOAIS("Teste", "123.456"),
                IllegalArgumentException.class, "CPF incompleto no construtor");
    }

    /**
     * Tier 5 (Adversarial): Rejeição de nomes nulos ou em branco.
     */
    public static void testarRejeicaoNomeInvalido() {
        afirmarRejeicao(() -> new DADOSPESSOAIS(null, "52998224725"),
                IllegalArgumentException.class, "nome nulo no construtor");
        afirmarRejeicao(() -> new DADOSPESSOAIS("", "52998224725"),
                IllegalArgumentException.class, "nome vazio no construtor");
        afirmarRejeicao(() -> new DADOSPESSOAIS("     ", "52998224725"),
                IllegalArgumentException.class, "nome apenas espaços no construtor");

        DADOSPESSOAIS d = new DADOSPESSOAIS("Válido", "52998224725");
        afirmarRejeicao(() -> d.setNome(null), IllegalArgumentException.class, "setNome(null)");
        afirmarRejeicao(() -> d.setNome("  "), IllegalArgumentException.class, "setNome(espaços)");
    }

    /**
     * Tier 5 (Adversarial): Rejeição de telefones celulares inválidos.
     */
    public static void testarRejeicaoCelularInvalido() {
        // Menos de 10 dígitos (ex.: 9 dígitos)
        afirmarRejeicao(() -> new DADOSPESSOAIS("Teste", "52998224725", "987654321"),
                IllegalArgumentException.class, "celular com 9 dígitos");

        // Mais de 13 dígitos (ex.: 14 dígitos)
        afirmarRejeicao(() -> new DADOSPESSOAIS("Teste", "52998224725", "55119876543210"),
                IllegalArgumentException.class, "celular com 14 dígitos");

        // Nulo ou vazio na sobrecarga de 3 argumentos
        afirmarRejeicao(() -> new DADOSPESSOAIS("Teste", "52998224725", null),
                IllegalArgumentException.class, "celular null na sobrecarga de 3 args");
        afirmarRejeicao(() -> new DADOSPESSOAIS("Teste", "52998224725", ""),
                IllegalArgumentException.class, "celular vazio na sobrecarga de 3 args");

        DADOSPESSOAIS d = new DADOSPESSOAIS("Teste", "52998224725");
        afirmarRejeicao(() -> d.setCelular("123"), IllegalArgumentException.class, "setCelular inválido");
    }

    /**
     * Tier 4 (LGPD & Segurança): Mascaramento estrito do CPF no método toString().
     */
    public static void testarMascaramentoCpfEmToString() {
        DADOSPESSOAIS titular = new DADOSPESSOAIS("Ana Silva", "529.982.247-25", "(11) 98765-4321");
        String representacao = titular.toString();

        afirmarFalso(representacao.contains("52998224725"),
                "O CPF completo NUNCA deve constar no toString()");
        afirmarFalso(representacao.contains("529.982.247-25"),
                "O CPF formatado NUNCA deve constar no toString()");
        afirmarVerdadeiro(representacao.contains("***.***.***-25"),
                "O CPF deve ser exibido estritamente com máscara ocultando os 9 primeiros dígitos");
        afirmarVerdadeiro(representacao.contains("Ana Silva"), "Nome deve constar no toString");
    }

    /**
     * Tier 2: Formatação oficial de CPF e celular.
     */
    public static void testarFormatacaoCpfECelular() {
        DADOSPESSOAIS d = new DADOSPESSOAIS("Mário de Andrade", "52998224725", "11987654321");
        afirmarIgual("529.982.247-25", d.getCpfFormatado(), "formatação oficial com pontos e traço");
        afirmarIgual("(11) 98765-4321", d.getCelularFormatado(), "formatação oficial de celular 11 dígitos");
        afirmarIgual("***.***.***-25", d.getCpfMascarado(), "máscara LGPD");

        // Telefone fixo de 10 dígitos
        d.setCelular("1134567890");
        afirmarIgual("(11) 3456-7890", d.getCelularFormatado(), "formatação de telefone 10 dígitos");
    }

    /**
     * Tier 5 (Herança Segura): Subclasse sobrescrevendo setCadastro não é chamada no construtor.
     */
    public static void testarHerancaSeguraSemEscapeVirtual() {
        class SubclasseDadosPessoais extends DADOSPESSOAIS {
            SubclasseDadosPessoais(String n, String c, String tel) {
                super(n, c, tel);
            }

            @Override
            public void setCadastro(String nome, String cpf, String celular) {
                throw new AssertionError("O construtor da classe base NÃO deve invocar método sobrescrevível!");
            }

            @Override
            public void setNome(String nome) {
                throw new AssertionError("O construtor não deve chamar setNome virtual!");
            }
        }

        SubclasseDadosPessoais sub = new SubclasseDadosPessoais("Monteiro Lobato", "52998224725", "11987654321");
        afirmarIgual("Monteiro Lobato", sub.getNome(), "herança segura inicializou nome perfeitamente");
        afirmarIgual("52998224725", sub.getCpf(), "herança segura inicializou CPF perfeitamente");
    }

    /**
     * Tier 1: Igualdade e HashCode baseados no CPF (chave unívoca do titular).
     */
    public static void testarIgualdadeEHashCode() {
        DADOSPESSOAIS p1 = new DADOSPESSOAIS("Carlos Drummond", "529.982.247-25", "11987654321");
        DADOSPESSOAIS p2 = new DADOSPESSOAIS("Carlos D. de Andrade", "52998224725", "21999998888");
        DADOSPESSOAIS p3 = new DADOSPESSOAIS("Clarice Lispector", "00000000191", "11987654321");

        afirmarVerdadeiro(p1.equals(p2), "p1 e p2 têm mesmo CPF, devem ser iguais");
        afirmarIgual(p1.hashCode(), p2.hashCode(), "hashCodes de mesmo CPF devem coincidir");
        afirmarFalso(p1.equals(p3), "p1 e p3 têm CPFs distintos, devem ser diferentes");
        afirmarFalso(p1.equals(null), "não pode ser igual a null");
        afirmarFalso(p1.equals("String"), "não pode ser igual a outro tipo");
    }

    /**
     * Tier 1: Interoperabilidade reversa com métodos legados em inglês.
     */
    public static void testarAliasesLegadosCompatibilidade() {
        DADOSPESSOAIS d = new DADOSPESSOAIS("Augusto dos Anjos", "52998224725", "83988776655");

        afirmarIgual("Augusto dos Anjos", d.getName(), "getName()");
        afirmarIgual("Augusto dos Anjos", d.name(), "name()");
        afirmarIgual("83988776655", d.getMobile(), "getMobile()");
        afirmarIgual("83988776655", d.mobile(), "mobile()");

        d.setName("Euclides da Cunha");
        afirmarIgual("Euclides da Cunha", d.getNome(), "setName()");

        d.setMobile("11911223344");
        afirmarIgual("11911223344", d.getCelular(), "setMobile()");
    }
}

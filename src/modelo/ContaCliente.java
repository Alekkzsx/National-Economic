package modelo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Reúne os dados bancários, os dados pessoais e as aplicações de um cliente.
 * A conta protege a coleção para impedir cadastros acima do limite ou números
 * de aplicação repetidos dentro do mesmo cliente.
 */
public class ContaCliente {
    /** Limite de aplicações financeiras permitido por cliente. */
    public static final int MAX_INVESTMENTS = 50;
    private final DadosPessoais dadosPessoais;
    private final int numeroAgencia;
    private final long numeroConta;
    private final List<AplicacaoFinanceira> investments = new ArrayList<>();

    /** Cria uma conta com dados obrigatórios e identificadores positivos. */
    public ContaCliente(DadosPessoais dadosPessoais, int numeroAgencia, long numeroConta) {
        this.dadosPessoais = Objects.requireNonNull(dadosPessoais, "Dados pessoais obrigatórios.");
        if (numeroAgencia <= 0 || numeroConta <= 0) throw new IllegalArgumentException("Agência e conta devem ser positivas.");
        this.numeroAgencia = numeroAgencia;
        this.numeroConta = numeroConta;
    }

    /** Retorna os dados pessoais do titular. */
    public DadosPessoais getDadosPessoais() { return dadosPessoais; }
    /** Retorna o número da agência. */
    public int getNumeroAgencia() { return numeroAgencia; }
    /** Retorna o número da conta. */
    public long getNumeroConta() { return numeroConta; }

    /** Retorna uma visão somente para leitura das aplicações cadastradas. */
    public List<AplicacaoFinanceira> listarAplicacoes() {
        return Collections.unmodifiableList(investments);
    }

    /** Adiciona uma aplicação respeitando o limite e a unicidade do identificador local. */
    public void adicionarAplicacao(AplicacaoFinanceira aplicacao) {
        Objects.requireNonNull(aplicacao, "Investimento obrigatório.");
        if (investments.size() >= MAX_INVESTMENTS) throw new IllegalStateException("Limite de 50 investimentos por cliente atingido.");
        if (buscarAplicacao(aplicacao.getNumero()).isPresent()) {
            throw new IllegalArgumentException("Número de investimento duplicado para o cliente.");
        }
        investments.add(aplicacao);
    }

    /**
     * Remove a aplicação identificada pelo número e retorna a instância removida.
     *
     * @param numero identificador da aplicação
     * @return aplicação removida
     * @throws IllegalArgumentException se a aplicação não pertencer ao cliente
     */
    public AplicacaoFinanceira removerAplicacao(String numero) {
        AplicacaoFinanceira aplicacao = exigirAplicacao(numero);
        investments.remove(aplicacao);
        return aplicacao;
    }

    /** Procura a aplicação pelo número e retorna vazio quando não há correspondência. */
    public Optional<AplicacaoFinanceira> buscarAplicacao(String numero) {
        if (numero == null) return Optional.empty();
        return investments.stream().filter(i -> i.getNumero().equals(numero)).findFirst();
    }

    /** Procura a aplicação ou informa explicitamente que ela não existe. */
    public AplicacaoFinanceira exigirAplicacao(String numero) {
        return buscarAplicacao(numero).orElseThrow(() -> new IllegalArgumentException("Investimento não encontrado."));
    }
}

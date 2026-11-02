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
    private final DadosPessoais personalData;
    private final int agencyNumber;
    private final long accountNumber;
    private final List<AplicacaoFinanceira> investments = new ArrayList<>();

    /** Cria uma conta com dados obrigatórios e identificadores positivos. */
    public ContaCliente(DadosPessoais personalData, int agencyNumber, long accountNumber) {
        this.personalData = Objects.requireNonNull(personalData, "Dados pessoais obrigatórios.");
        if (agencyNumber <= 0 || accountNumber <= 0) throw new IllegalArgumentException("Agência e conta devem ser positivas.");
        this.agencyNumber = agencyNumber;
        this.accountNumber = accountNumber;
    }

    /** Retorna os dados pessoais do titular. */
    public DadosPessoais getDadosPessoais() { return personalData; }
    /** Retorna o número da agência. */
    public int getNumeroAgencia() { return agencyNumber; }
    /** Retorna o número da conta. */
    public long getNumeroConta() { return accountNumber; }

    /** Retorna uma visão somente para leitura das aplicações cadastradas. */
    public List<AplicacaoFinanceira> listarAplicacoes() {
        return Collections.unmodifiableList(investments);
    }

    /** Adiciona uma aplicação respeitando o limite e a unicidade do identificador local. */
    public void adicionarAplicacao(AplicacaoFinanceira investment) {
        Objects.requireNonNull(investment, "Investimento obrigatório.");
        if (investments.size() >= MAX_INVESTMENTS) throw new IllegalStateException("Limite de 50 investimentos por cliente atingido.");
        if (buscarAplicacao(investment.getNumero()).isPresent()) {
            throw new IllegalArgumentException("Número de investimento duplicado para o cliente.");
        }
        investments.add(investment);
    }

    /** Procura a aplicação pelo número e retorna vazio quando não há correspondência. */
    public Optional<AplicacaoFinanceira> buscarAplicacao(String number) {
        if (number == null) return Optional.empty();
        return investments.stream().filter(i -> i.getNumero().equals(number)).findFirst();
    }

    /** Procura a aplicação ou informa explicitamente que ela não existe. */
    public AplicacaoFinanceira exigirAplicacao(String number) {
        return buscarAplicacao(number).orElseThrow(() -> new IllegalArgumentException("Investimento não encontrado."));
    }
}

package domain;

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
public class ClientAccount {
    /** Limite de aplicações financeiras permitido por cliente. */
    public static final int MAX_INVESTMENTS = 50;
    private final PersonalData personalData;
    private final int agencyNumber;
    private final long accountNumber;
    private final List<Investment> investments = new ArrayList<>();

    /** Cria uma conta com dados obrigatórios e identificadores positivos. */
    public ClientAccount(PersonalData personalData, int agencyNumber, long accountNumber) {
        this.personalData = Objects.requireNonNull(personalData, "Dados pessoais obrigatórios.");
        if (agencyNumber <= 0 || accountNumber <= 0) throw new IllegalArgumentException("Agência e conta devem ser positivas.");
        this.agencyNumber = agencyNumber;
        this.accountNumber = accountNumber;
    }

    /** Retorna os dados pessoais do titular. */
    public PersonalData getPersonalData() { return personalData; }
    /** Retorna o número da agência. */
    public int getAgencyNumber() { return agencyNumber; }
    /** Retorna o número da conta. */
    public long getAccountNumber() { return accountNumber; }

    /** Retorna uma visão somente para leitura das aplicações cadastradas. */
    public List<Investment> getInvestments() {
        return Collections.unmodifiableList(investments);
    }

    /** Adiciona uma aplicação respeitando o limite e a unicidade do identificador local. */
    public void addInvestment(Investment investment) {
        Objects.requireNonNull(investment, "Investimento obrigatório.");
        if (investments.size() >= MAX_INVESTMENTS) throw new IllegalStateException("Limite de 50 investimentos por cliente atingido.");
        if (findInvestment(investment.getNumber()).isPresent()) {
            throw new IllegalArgumentException("Número de investimento duplicado para o cliente.");
        }
        investments.add(investment);
    }

    /** Procura a aplicação pelo número e retorna vazio quando não há correspondência. */
    public Optional<Investment> findInvestment(String number) {
        if (number == null) return Optional.empty();
        return investments.stream().filter(i -> i.getNumber().equals(number)).findFirst();
    }

    /** Procura a aplicação ou informa explicitamente que ela não existe. */
    public Investment requireInvestment(String number) {
        return findInvestment(number).orElseThrow(() -> new IllegalArgumentException("Investimento não encontrado."));
    }
}

package domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class ClientAccount {
    public static final int MAX_INVESTMENTS = 50;
    private final PersonalData personalData;
    private final int agencyNumber;
    private final long accountNumber;
    private final List<Investment> investments = new ArrayList<>();

    public ClientAccount(PersonalData personalData, int agencyNumber, long accountNumber) {
        this.personalData = Objects.requireNonNull(personalData, "Dados pessoais obrigatórios.");
        if (agencyNumber <= 0 || accountNumber <= 0) throw new IllegalArgumentException("Agência e conta devem ser positivas.");
        this.agencyNumber = agencyNumber;
        this.accountNumber = accountNumber;
    }

    public PersonalData getPersonalData() { return personalData; }
    public int getAgencyNumber() { return agencyNumber; }
    public long getAccountNumber() { return accountNumber; }

    public List<Investment> getInvestments() {
        return Collections.unmodifiableList(investments);
    }

    public void addInvestment(Investment investment) {
        Objects.requireNonNull(investment, "Investimento obrigatório.");
        if (investments.size() >= MAX_INVESTMENTS) throw new IllegalStateException("Limite de 50 investimentos por cliente atingido.");
        if (findInvestment(investment.getNumber()).isPresent()) {
            throw new IllegalArgumentException("Número de investimento duplicado para o cliente.");
        }
        investments.add(investment);
    }

    public Optional<Investment> findInvestment(String number) {
        if (number == null) return Optional.empty();
        return investments.stream().filter(i -> i.getNumber().equals(number)).findFirst();
    }

    public Investment requireInvestment(String number) {
        return findInvestment(number).orElseThrow(() -> new IllegalArgumentException("Investimento não encontrado."));
    }
}

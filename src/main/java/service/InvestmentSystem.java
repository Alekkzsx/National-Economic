package service;

import domain.ClientAccount;
import domain.FinancialOperation;
import domain.Investment;
import domain.OperationType;
import domain.PersonalData;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Fachada dos serviços do SGI: mantém clientes, aplicações, operações,
 * consultas de saldo e persistência em arquivo.
 *
 * <p>Os métodos que leem ou alteram o estado são sincronizados para que uma
 * mesma instância não tenha operações concorrentes inconsistentes. O arquivo
 * TSV identifica cada linha como cliente ({@code C}), aplicação ({@code I})
 * ou operação ({@code O}).</p>
 */
public class InvestmentSystem {
    /** Limite de clientes definido para o sistema. */
    private static final int MAX_CLIENTS = 100;

    /** Clientes indexados pelo CPF normalizado, preservando a ordem de cadastro. */
    private final Map<String, ClientAccount> clients = new LinkedHashMap<>();
    /** Operações indexadas pelo identificador único da aplicação. */
    private final Map<String, List<FinancialOperation>> statements = new LinkedHashMap<>();

    /** Cadastra um cliente validando CPF, duplicidade e limite do sistema. */
    public synchronized void registerClient(PersonalData personalData, int agencyNumber, long accountNumber) {
        Objects.requireNonNull(personalData, "Dados pessoais obrigatórios.");
        String cpf = canonicalCpf(personalData.getCpf());
        if (clients.containsKey(cpf)) throw new IllegalArgumentException("CPF já cadastrado.");
        if (clients.size() >= MAX_CLIENTS) throw new IllegalStateException("Limite de 100 clientes atingido.");
        clients.put(cpf, new ClientAccount(personalData, agencyNumber, accountNumber));
    }

    /** Associa uma aplicação a um cliente e cria seu extrato vazio. */
    public synchronized void registerInvestment(String cpf, Investment investment) {
        Objects.requireNonNull(investment, "Investimento obrigatório.");
        ClientAccount client = requireClient(cpf);
        if (findInvestmentByNumber(investment.getNumber()).isPresent()) {
            throw new IllegalArgumentException("Número de investimento já cadastrado no sistema.");
        }
        client.addInvestment(investment);
        statements.put(investment.getNumber(), new ArrayList<>());
    }

    /** Valida e aplica uma operação à aplicação, registrando-a no extrato. */
    public synchronized void registerOperation(String cpf, String investmentNumber, FinancialOperation operation) {
        Objects.requireNonNull(operation, "Operação obrigatória.");
        Investment investment = requireClient(cpf).requireInvestment(investmentNumber);
        investment.apply(operation);
        statements.computeIfAbsent(investmentNumber, ignored -> new ArrayList<>()).add(operation);
    }

    /** Procura um cliente pelo CPF e retorna vazio se não existir. */
    public synchronized Optional<ClientAccount> findClient(String cpf) {
        return Optional.ofNullable(clients.get(canonicalCpf(cpf)));
    }

    /** Retorna uma cópia não modificável da lista de clientes. */
    public synchronized List<ClientAccount> getClients() {
        return List.copyOf(clients.values());
    }

    /** Retorna as aplicações do cliente; CPF inválido ou inexistente gera erro. */
    public synchronized List<Investment> getInvestments(String cpf) {
        return requireClient(cpf).getInvestments();
    }

    /**
     * Retorna o extrato inclusivo entre as duas datas, em ordem cronológica.
     * O período deve ser válido e a aplicação deve pertencer ao cliente.
     */
    public synchronized List<FinancialOperation> getStatement(String cpf, String investmentNumber,
                                                                 LocalDate start, LocalDate end) {
        Objects.requireNonNull(start, "Data inicial obrigatória.");
        Objects.requireNonNull(end, "Data final obrigatória.");
        if (end.isBefore(start)) throw new IllegalArgumentException("O fim do período antecede o início.");
        Investment investment = requireClient(cpf).requireInvestment(investmentNumber);
        return statements.getOrDefault(investment.getNumber(), List.of()).stream()
                .filter(op -> !op.date().isBefore(start) && !op.date().isAfter(end))
                .sorted(Comparator.comparing(FinancialOperation::date))
                .toList();
    }

    /** Retorna o saldo líquido de uma aplicação específica. */
    public synchronized BigDecimal getBalance(String cpf, String investmentNumber) {
        return requireClient(cpf).requireInvestment(investmentNumber).getNetBalance();
    }

    /** Soma os saldos líquidos das aplicações pertencentes ao cliente. */
    public synchronized BigDecimal getClientBalance(String cpf) {
        return requireClient(cpf).getInvestments().stream()
                .map(Investment::getNetBalance).reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
    }

    /** Soma os saldos líquidos de todas as aplicações cadastradas. */
    public synchronized BigDecimal getSystemBalance() {
        return clients.values().stream().flatMap(client -> client.getInvestments().stream())
                .map(Investment::getNetBalance).reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
    }

    /**
     * Atualiza todas as aplicações em uma data útil. Fins de semana são
     * ignorados, conforme a regra da atividade.
     */
    public synchronized void updateAll(LocalDate date) {
        Objects.requireNonNull(date, "Data obrigatória.");
        if (date.getDayOfWeek().getValue() >= 6) return;
        for (ClientAccount client : clients.values()) {
            for (Investment investment : client.getInvestments()) investment.update(date);
        }
    }

    /** Delega a gravação do estado completo ao componente de persistência TSV. */
    public synchronized void save(Path path) throws IOException {
        Objects.requireNonNull(path, "Caminho obrigatório.");
        InvestmentFileStore.save(path, clients.values(), statements);
    }

    /**
     * Reconstitui o sistema a partir dos formatos v1, v2 ou v3.
     *
     * @throws IOException se o arquivo não existir, estiver inconsistente ou
     *                     usar uma versão desconhecida
     */
    public static InvestmentSystem load(Path path) throws IOException {
        Objects.requireNonNull(path, "Caminho obrigatório.");
        return InvestmentFileStore.load(path);
    }

    /** Anexa operação já persistida sem reaplicar seus efeitos ao saldo restaurado. */
    synchronized void appendRestoredOperation(
            String cpf,
            String investmentNumber,
            FinancialOperation operation
    ) {
        ClientAccount client = requireClient(cpf);
        Investment investment = client.requireInvestment(investmentNumber);
        List<FinancialOperation> operations = statements.get(investment.getNumber());
        if (operations == null) {
            throw new IllegalArgumentException("Extrato da aplicação não foi inicializado.");
        }
        if (operation.date().isBefore(investment.getApplicationDate())) {
            throw new IllegalArgumentException("Operação anterior à aplicação.");
        }
        if (operations.size() >= 100) {
            throw new IllegalArgumentException("Mais de 100 operações persistidas.");
        }
        operations.add(operation);
    }

    /** Normaliza o CPF e exige que o cliente esteja cadastrado. */
    private synchronized ClientAccount requireClient(String cpf) {
        ClientAccount client = clients.get(canonicalCpf(cpf));
        if (client == null) throw new IllegalArgumentException("Cliente não encontrado.");
        return client;
    }

    /** Busca um número de aplicação globalmente para impedir duplicidades. */
    private Optional<Investment> findInvestmentByNumber(String number) {
        return clients.values().stream().flatMap(c -> c.getInvestments().stream())
                .filter(i -> i.getNumber().equals(number)).findFirst();
    }

    /** Remove pontuação do CPF para usar uma chave estável nos mapas internos. */
    private static String canonicalCpf(String cpf) {
        if (cpf == null) throw new IllegalArgumentException("CPF obrigatório.");
        String digits = cpf.replaceAll("\\D", "");
        if (digits.length() != 11) throw new IllegalArgumentException("CPF deve conter 11 dígitos.");
        return digits;
    }

}

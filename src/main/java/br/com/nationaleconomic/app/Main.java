package br.com.nationaleconomic.app;

import br.com.nationaleconomic.domain.ClientAccount;
import br.com.nationaleconomic.domain.FinancialOperation;
import br.com.nationaleconomic.domain.Investment;
import br.com.nationaleconomic.domain.OperationType;
import br.com.nationaleconomic.domain.PersonalData;
import br.com.nationaleconomic.market.QuoteClient;
import br.com.nationaleconomic.service.InvestmentSystem;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Scanner;

/**
 * Interface de terminal do SGI. A camada de apresentação apenas valida entradas
 * e delega as regras de negócio ao InvestmentSystem.
 */
public final class Main {
    private static final Path DEFAULT_FILE = Path.of("data", "sgi.db");

    private final Scanner scanner;
    private InvestmentSystem system;

    private Main(Scanner scanner, InvestmentSystem system) {
        this.scanner = scanner;
        this.system = system;
    }

    public static void main(String[] args) {
        try (Scanner scanner = new Scanner(System.in)) {
            InvestmentSystem investmentSystem = loadDefaultState(new InvestmentSystem());
            new Main(scanner, investmentSystem).run();
        }
    }

    private static InvestmentSystem loadDefaultState(InvestmentSystem fallback) {
        if (!Files.exists(DEFAULT_FILE)) {
            return fallback;
        }
        try {
            System.out.println("Carregando estado de " + DEFAULT_FILE + "...");
            return InvestmentSystem.load(DEFAULT_FILE);
        } catch (IOException | RuntimeException e) {
            System.out.println("Não foi possível carregar " + DEFAULT_FILE + ": " + messageOf(e));
            System.out.println("O sistema será iniciado sem os dados desse arquivo.");
            return fallback;
        }
    }

    private void run() {
        boolean running = true;
        while (running) {
            try {
                printMenu();
                String option = readLine("Escolha uma opção: ");
                switch (option) {
                    case "1" -> registerClient();
                    case "2" -> listClients();
                    case "3" -> consultClient();
                    case "4" -> registerInvestment();
                    case "5" -> listInvestments();
                    case "6" -> launchOperation(OperationType.DEPOSIT);
                    case "7" -> launchOperation(OperationType.WITHDRAWAL);
                    case "8" -> showStatement();
                    case "9" -> showClientBalance();
                    case "10" -> showSystemBalance();
                    case "11" -> updateInvestments();
                    case "12" -> loadState();
                    case "13" -> saveState();
                    case "14" -> showQuotes();
                    case "0" -> running = false;
                    default -> System.out.println("Opção inválida. Informe uma opção do menu.");
                }
            } catch (EndOfInputException e) {
                running = false;
            } catch (RuntimeException e) {
                System.out.println("Não foi possível concluir a operação: " + messageOf(e));
            }
        }

        saveOnExit();
        System.out.println("SGI encerrado.");
    }

    private void printMenu() {
        System.out.println("""

                ===== Sistema de Gestão de Investimentos =====
                1. Cadastrar cliente
                2. Listar clientes
                3. Consultar cliente
                4. Cadastrar aplicação
                5. Listar aplicações de um cliente
                6. Lançar depósito
                7. Lançar retirada
                8. Consultar extrato
                9. Consultar saldo de cliente/aplicação
                10. Consultar saldo total do sistema
                11. Atualizar aplicações
                12. Carregar estado de arquivo
                13. Salvar estado em arquivo
                14. Consultar cotações do servidor
                0. Salvar e sair
                """);
    }

    private void registerClient() {
        String name = readRequired("Nome completo: ");
        String cpf = readRequired("CPF (será ocultado nas listagens): ");
        String mobile = readRequired("Celular (com DDD): ");
        int agency = readInt("Número da agência: ", 1, Integer.MAX_VALUE);
        long account = readLong("Número da conta: ", 1, Long.MAX_VALUE);

        system.registerClient(new PersonalData(name, cpf, mobile), agency, account);
        System.out.println("Cliente cadastrado com sucesso.");
    }

    private void listClients() {
        List<ClientAccount> clients = system.getClients();
        if (clients.isEmpty()) {
            System.out.println("Não há clientes cadastrados.");
            return;
        }
        System.out.println("Clientes cadastrados:");
        for (ClientAccount client : clients) {
            Object personalData = property(client, "getPersonalData", "personalData");
            String name = firstText(property(personalData, "getName", "name"), "Nome não disponível");
            String id = firstText(
                    property(client, "getId", "getCpf", "getClientId", "id", "cpf", "clientId"),
                    property(personalData, "getCpf", "getDocument", "cpf", "document"));
            System.out.printf("- %s | CPF/identificador: %s%n", name, maskCpf(id));
        }
    }

    private void consultClient() {
        String clientId = readRequired("CPF/identificador do cliente: ");
        Optional<ClientAccount> result = system.findClient(clientId);
        if (result.isEmpty()) {
            System.out.println("Cliente não encontrado.");
            return;
        }
        ClientAccount client = result.get();
        Object personalData = property(client, "getPersonalData", "personalData");
        String name = firstText(property(personalData, "getName", "name"), "Não disponível");
        String id = firstText(property(client, "getId", "getCpf", "getClientId", "id", "cpf", "clientId"),
                property(personalData, "getCpf", "getDocument", "cpf", "document"), clientId);
        Object agency = property(client, "getAgencyNumber", "agencyNumber");
        Object account = property(client, "getAccountNumber", "accountNumber");
        Object mobile = property(personalData, "getMobile", "mobile");
        System.out.println("Cliente: " + name);
        System.out.println("CPF/identificador: " + maskCpf(id));
        if (agency != null && account != null) {
            System.out.println("Agência/conta: " + agency + "/" + account);
        }
        if (mobile != null) {
            System.out.println("Celular: " + mobile);
        }
        System.out.println("Saldo total: " + money(system.getClientBalance(clientId)));
    }

    private void registerInvestment() {
        String clientId = readRequired("CPF/identificador do cliente: ");
        String name = readRequired("Nome da aplicação: ");
        String number = readRequired("Número da aplicação: ");
        BigDecimal initialValue = readPositiveAmount("Valor inicial: ");
        BigDecimal rate = readMonthlyRate();
        system.registerInvestment(clientId, new Investment(name, number, initialValue, rate));
        System.out.println("Aplicação cadastrada com sucesso.");
    }

    private void listInvestments() {
        String clientId = readRequired("CPF/identificador do cliente: ");
        List<Investment> investments = system.getInvestments(clientId);
        if (investments.isEmpty()) {
            System.out.println("O cliente não possui aplicações cadastradas.");
            return;
        }
        System.out.println("Aplicações de " + maskCpf(clientId) + ":");
        for (Investment investment : investments) {
            System.out.printf("- %s | número: %s | saldo: %s | aporte inicial: %s | taxa mensal: %s%n",
                    firstText(property(investment, "getName", "name"), "Sem nome"),
                    firstText(property(investment, "getNumber", "number"), "Sem número"),
                    moneyValue(property(investment, "getBalance", "balance")),
                    moneyValue(property(investment, "getInitialAmount", "initialAmount")),
                    displayValue(property(investment, "getMonthlyRate", "monthlyRate")));
        }
    }

    private void launchOperation(OperationType type) {
        String clientId = readRequired("CPF/identificador do cliente: ");
        String investmentId = readRequired("Código da aplicação: ");
        LocalDate date = readDate("Data da operação (AAAA-MM-DD): ");
        BigDecimal amount = readPositiveAmount("Valor: ");
        String description = readRequired("Descrição: ");
        system.registerOperation(clientId, investmentId, new FinancialOperation(date, amount, description, type));
        System.out.println((type == OperationType.DEPOSIT ? "Depósito" : "Retirada") + " lançado com sucesso.");
    }

    private void showStatement() {
        String clientId = readRequired("CPF/identificador do cliente: ");
        String investmentId = readRequired("Código da aplicação: ");
        LocalDate from;
        LocalDate to;
        if (readYesNo("Usar os últimos 30 dias? (s/n): ")) {
            to = LocalDate.now();
            from = to.minusDays(29);
        } else {
            from = readDate("Data inicial (AAAA-MM-DD): ");
            to = readDate("Data final (AAAA-MM-DD): ");
        }
        if (from.isAfter(to)) {
            System.out.println("Período inválido: a data inicial deve ser anterior ou igual à final.");
            return;
        }
        List<FinancialOperation> operations = system.getStatement(clientId, investmentId, from, to);
        if (operations.isEmpty()) {
            System.out.println("Não há operações no período informado.");
            return;
        }
        System.out.printf("Extrato de %s, de %s a %s:%n", maskCpf(clientId), from, to);
        for (FinancialOperation operation : operations) {
            System.out.printf("- %s | %s | %s | %s%n",
                    displayValue(property(operation, "getDate", "getOperationDate", "date", "operationDate")),
                    displayValue(property(operation, "getType", "getOperationType", "type", "operationType")),
                    moneyValue(property(operation, "getAmount", "getValue", "amount", "value")),
                    firstText(property(operation, "getDescription", "getNote", "description", "note"), ""));
        }
    }

    private void showClientBalance() {
        String clientId = readRequired("CPF/identificador do cliente: ");
        String investmentId = readLine("Código da aplicação (vazio para saldo total do cliente): ").trim();
        BigDecimal balance = investmentId.isEmpty()
                ? system.getClientBalance(clientId)
                : system.getBalance(clientId, investmentId);
        System.out.println("Saldo: " + money(balance));
    }

    private void showSystemBalance() {
        System.out.println("Saldo total do sistema: " + money(system.getSystemBalance()));
    }

    private void updateInvestments() {
        LocalDate date = readDate("Data de atualização (AAAA-MM-DD): ");
        system.updateAll(date);
        System.out.println("Aplicações atualizadas com sucesso.");
    }

    private void loadState() {
        Path path = readPath("Caminho do arquivo para carregar: ");
        try {
            InvestmentSystem loaded = InvestmentSystem.load(path);
            system = loaded;
            System.out.println("Estado carregado com sucesso de " + path + ".");
        } catch (IOException e) {
            System.out.println("Falha ao carregar " + path + ": " + messageOf(e));
        }
    }

    private void saveState() {
        Path path = readPath("Caminho do arquivo para salvar: ");
        saveTo(path);
    }

    private void saveOnExit() {
        try {
            saveTo(DEFAULT_FILE);
        } catch (RuntimeException e) {
            System.out.println("Não foi possível salvar o estado ao sair: " + messageOf(e));
        }
    }

    private void saveTo(Path path) {
        try {
            Path parent = path.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            system.save(path);
            System.out.println("Estado salvo com sucesso em " + path + ".");
        } catch (IOException | RuntimeException e) {
            System.out.println("Falha ao salvar " + path + ": " + messageOf(e));
        }
    }

    private void showQuotes() {
        String host = readRequired("Servidor de cotações (host): ");
        int port = readInt("Porta: ", 1, 65535);
        String clientId = readRequired("CPF/identificador do cliente: ");
        try {
            Map<String, BigDecimal> quotes = new QuoteClient(host, port).getQuotes();
            List<Investment> investments = system.getInvestments(clientId);
            if (investments.isEmpty()) {
                System.out.println("O cliente não possui aplicações cadastradas.");
                return;
            }
            System.out.println("Cotações das aplicações de " + maskCpf(clientId) + ":");
            for (Investment investment : investments) {
                String key = firstText(property(investment, "getNumber", "number"), "");
                BigDecimal quote = quotes.get(key);
                System.out.printf("- %s: %s%n", key, quote == null ? "cotação indisponível" : moneyValue(quote));
            }
        } catch (IOException e) {
            System.out.println("Falha ao consultar o servidor de cotações: " + messageOf(e));
        } catch (RuntimeException e) {
            System.out.println("Falha ao consultar cotações: " + messageOf(e));
        }
    }

    private String readRequired(String prompt) {
        while (true) {
            String value = readLine(prompt).trim();
            if (!value.isEmpty()) {
                return value;
            }
            System.out.println("O campo é obrigatório.");
        }
    }

    private int readInt(String prompt, int min, int max) {
        while (true) {
            String input = readLine(prompt).trim();
            try {
                int value = Integer.parseInt(input);
                if (value >= min && value <= max) {
                    return value;
                }
            } catch (NumberFormatException ignored) {
                // A mensagem abaixo é compartilhada por formato e faixa inválidos.
            }
            System.out.printf("Informe um número inteiro entre %d e %d.%n", min, max);
        }
    }

    private long readLong(String prompt, long min, long max) {
        while (true) {
            String input = readLine(prompt).trim();
            try {
                long value = Long.parseLong(input);
                if (value >= min && value <= max) {
                    return value;
                }
            } catch (NumberFormatException ignored) {
                // A mensagem abaixo é compartilhada por formato e faixa inválidos.
            }
            System.out.printf("Informe um número inteiro entre %d e %d.%n", min, max);
        }
    }

    private BigDecimal readPositiveAmount(String prompt) {
        while (true) {
            BigDecimal value = readDecimal(prompt);
            if (value.signum() > 0) {
                return value;
            }
            System.out.println("O valor deve ser maior que zero.");
        }
    }

    private BigDecimal readMonthlyRate() {
        while (true) {
            BigDecimal value = readDecimal("Taxa mensal entre 0 e 1 (ex.: 0,05 para 5%): ");
            if (value.signum() >= 0 && value.compareTo(BigDecimal.ONE) <= 0) {
                return value;
            }
            System.out.println("A taxa mensal deve estar entre 0 e 1.");
        }
    }

    private BigDecimal readDecimal(String prompt) {
        while (true) {
            String input = readLine(prompt).trim().replace(',', '.');
            try {
                return new BigDecimal(input);
            } catch (NumberFormatException e) {
                System.out.println("Valor inválido. Informe um número decimal, por exemplo 1250,50.");
            }
        }
    }

    private LocalDate readDate(String prompt) {
        while (true) {
            String input = readLine(prompt).trim();
            try {
                return LocalDate.parse(input);
            } catch (DateTimeParseException e) {
                System.out.println("Data inválida. Use o formato AAAA-MM-DD.");
            }
        }
    }

    private boolean readYesNo(String prompt) {
        while (true) {
            String answer = readLine(prompt).trim().toLowerCase();
            if (answer.equals("s") || answer.equals("sim")) {
                return true;
            }
            if (answer.equals("n") || answer.equals("não") || answer.equals("nao")) {
                return false;
            }
            System.out.println("Responda s ou n.");
        }
    }

    private Path readPath(String prompt) {
        return Path.of(readRequired(prompt));
    }

    private String readLine(String prompt) {
        System.out.print(prompt);
        if (!scanner.hasNextLine()) {
            throw new EndOfInputException();
        }
        return scanner.nextLine();
    }

    private static String maskCpf(Object value) {
        String cpf = value == null ? "" : value.toString();
        String digits = cpf.replaceAll("\\D", "");
        if (digits.length() == 11) {
            return "***." + digits.substring(3, 6) + "." + digits.substring(6, 9) + "-**";
        }
        if (cpf.length() <= 4) {
            return "****";
        }
        return "*".repeat(cpf.length() - 4) + cpf.substring(cpf.length() - 4);
    }

    private static String firstText(Object... values) {
        for (Object value : values) {
            if (value != null && !value.toString().isBlank()) {
                return value.toString();
            }
        }
        return "";
    }

    private static String money(BigDecimal value) {
        return value == null ? "não disponível" : "R$ " + value.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private static String moneyValue(Object value) {
        if (value instanceof BigDecimal amount) {
            return money(amount);
        }
        return displayValue(value);
    }

    private static String displayValue(Object value) {
        return value == null ? "não disponível" : value.toString();
    }

    private static String messageOf(Throwable throwable) {
        String message = throwable == null ? null : throwable.getMessage();
        return message == null || message.isBlank() ? "erro sem detalhes" : message;
    }

    /**
     * Acessa getters convencionais e accessors de record sem depender de detalhes
     * ainda não definidos nos modelos compartilhados.
     */
    private static Object property(Object target, String... methodNames) {
        if (target == null) {
            return null;
        }
        for (String methodName : methodNames) {
            try {
                Method method = target.getClass().getMethod(methodName);
                return method.invoke(target);
            } catch (NoSuchMethodException ignored) {
                // Tenta o próximo nome convencional.
            } catch (IllegalAccessException | InvocationTargetException | RuntimeException ignored) {
                return null;
            }

            private static final class EndOfInputException extends RuntimeException {
            }
        }
        return null;
    }
}

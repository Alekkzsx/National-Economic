package app;

import domain.ClientAccount;
import domain.FinancialOperation;
import domain.Investment;
import domain.OperationType;
import domain.PersonalData;
import market.QuoteClient;
import service.InvestmentSystem;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Scanner;

/**
 * Ponto de entrada e controlador do terminal do SGI.
 *
 * <p>A classe coordena o menu e encaminha as operações para o serviço. A
 * leitura, a formatação e as opções de configuração ficam em auxiliares para
 * manter a interação separada das regras de negócio.</p>
 */
public final class Main {
    /** Arquivo padrão usado para carregar e salvar o estado do sistema. */
    private static final Path DEFAULT_FILE = Path.of("data", "sgi.db");

    private final ConsoleInput input;
    private InvestmentSystem system;
    private boolean saveDefaultOnExit;

    private Main(ConsoleInput input, InvestmentSystem system, boolean saveDefaultOnExit) {
        this.input = input;
        this.system = system;
        this.saveDefaultOnExit = saveDefaultOnExit;
    }

    /** Carrega o estado padrão e inicia a interface interativa. */
    public static void main(String[] args) {
        try (Scanner scanner = new Scanner(System.in)) {
            InitialState initialState = loadDefaultState();
            new Main(new ConsoleInput(scanner), initialState.system(), initialState.saveDefaultOnExit()).run();
        }
    }

    /** Usa o arquivo padrão quando disponível e protege dados que não puderam ser lidos. */
    private static InitialState loadDefaultState() {
        if (!Files.exists(DEFAULT_FILE)) {
            return new InitialState(new InvestmentSystem(), true);
        }
        try {
            System.out.println("Carregando estado de " + DEFAULT_FILE + "...");
            return new InitialState(InvestmentSystem.load(DEFAULT_FILE), true);
        } catch (IOException | RuntimeException e) {
            System.out.println("Não foi possível carregar " + DEFAULT_FILE + ": " + CliFormat.messageOf(e));
            System.out.println("O sistema será iniciado vazio; o arquivo original será preservado.");
            return new InitialState(new InvestmentSystem(), false);
        }
    }

    /** Mantém o menu ativo até a saída explícita ou o fim da entrada. */
    private void run() {
        boolean running = true;
        while (running) {
            try {
                printMenu();
                String option = input.readLine("Escolha uma opção: ");
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
            } catch (ConsoleInput.EndOfInputException e) {
                running = false;
            } catch (RuntimeException e) {
                System.out.println("Não foi possível concluir a operação: " + CliFormat.messageOf(e));
            }
        }
        if (saveDefaultOnExit) {
            saveTo(DEFAULT_FILE);
        } else {
            System.out.println("Salvamento automático ignorado para preservar o arquivo padrão.");
        }
        System.out.println("SGI encerrado.");
    }

    /** Mostra as operações disponíveis no terminal. */
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

    /** Cadastra os dados pessoais e bancários de um cliente. */
    private void registerClient() {
        String name = input.readRequired("Nome completo: ");
        String cpf = input.readRequired("CPF (será ocultado nas listagens): ");
        String mobile = input.readRequired("Celular (com DDD): ");
        int agency = input.readInt("Número da agência: ", 1, Integer.MAX_VALUE);
        long account = input.readLong("Número da conta: ", 1, Long.MAX_VALUE);

        system.registerClient(new PersonalData(name, cpf, mobile), agency, account);
        System.out.println("Cliente cadastrado com sucesso.");
    }

    /** Lista clientes sem revelar o CPF completo. */
    private void listClients() {
        List<ClientAccount> clients = system.getClients();
        if (clients.isEmpty()) {
            System.out.println("Não há clientes cadastrados.");
            return;
        }
        System.out.println("Clientes cadastrados:");
        for (ClientAccount client : clients) {
            PersonalData personalData = client.getPersonalData();
            System.out.printf("- %s | CPF: %s%n", personalData.getName(),
                    CliFormat.maskCpf(personalData.getCpf()));
        }
    }

    /** Consulta os dados cadastrais e o saldo consolidado de um cliente. */
    private void consultClient() {
        String clientId = input.readRequired("CPF/identificador do cliente: ");
        Optional<ClientAccount> result = system.findClient(clientId);
        if (result.isEmpty()) {
            System.out.println("Cliente não encontrado.");
            return;
        }
        ClientAccount client = result.get();
        PersonalData personalData = client.getPersonalData();
        System.out.println("Cliente: " + personalData.getName());
        System.out.println("CPF/identificador: " + CliFormat.maskCpf(personalData.getCpf()));
        System.out.println("Agência/conta: " + client.getAgencyNumber() + "/" + client.getAccountNumber());
        System.out.println("Celular: " + personalData.getMobile());
        System.out.println("Saldo total: " + CliFormat.money(system.getClientBalance(clientId)));
    }

    /** Cadastra uma aplicação com termos padrão ou personalizados. */
    private void registerInvestment() {
        String clientId = input.readRequired("CPF/identificador do cliente: ");
        String name = input.readRequired("Nome da aplicação: ");
        String number = input.readRequired("Número da aplicação: ");
        BigDecimal initialValue = input.readPositiveAmount("Valor inicial: ");
        BigDecimal rate = input.readMonthlyRate();
        Investment investment = new Investment(name, number, initialValue, rate);

        if (input.readYesNo("Usar configuração padrão da aplicação? (s/n): ")) {
            System.out.println("""
                    Configuração padrão da aplicação:
                    - rendimento diário; sem vencimento e sem permanência mínima;
                    - resgate diário;
                    - IOF de 3,5% nos primeiros 29 dias; IR regressivo.
                    """);
        } else {
            InvestmentTerms.configure(investment, input);
        }
        system.registerInvestment(clientId, investment);
        System.out.println("Aplicação cadastrada com sucesso.");
    }

    /** Lista os dados principais das aplicações de um cliente. */
    private void listInvestments() {
        String clientId = input.readRequired("CPF/identificador do cliente: ");
        List<Investment> investments = system.getInvestments(clientId);
        if (investments.isEmpty()) {
            System.out.println("O cliente não possui aplicações cadastradas.");
            return;
        }
        System.out.println("Aplicações de " + CliFormat.maskCpf(clientId) + ":");
        for (Investment investment : investments) {
            System.out.printf("- %s | número: %s | saldo: %s | aporte inicial: %s | taxa mensal: %s%n",
                    investment.getName(), investment.getNumber(), CliFormat.money(investment.getBalance()),
                    CliFormat.money(investment.getInitialAmount()), investment.getMonthlyRate());
        }
    }

    /** Registra um depósito ou uma retirada na aplicação indicada. */
    private void launchOperation(OperationType type) {
        String clientId = input.readRequired("CPF/identificador do cliente: ");
        String investmentId = input.readRequired("Código da aplicação: ");
        LocalDate date = input.readDate("Data da operação (AAAA-MM-DD): ");
        BigDecimal amount = input.readPositiveAmount("Valor: ");
        String description = input.readRequired("Descrição: ");
        FinancialOperation operation = new FinancialOperation(date, amount, description, type);
        system.registerOperation(clientId, investmentId, operation);
        String label = type == OperationType.DEPOSIT ? "Depósito" : "Retirada";
        System.out.println(label + " lançado com sucesso.");
    }

    /** Exibe o extrato no período escolhido ou nos últimos 30 dias. */
    private void showStatement() {
        String clientId = input.readRequired("CPF/identificador do cliente: ");
        String investmentId = input.readRequired("Código da aplicação: ");
        LocalDate from;
        LocalDate to;
        if (input.readYesNo("Usar os últimos 30 dias? (s/n): ")) {
            to = LocalDate.now();
            from = to.minusDays(29);
        } else {
            from = input.readDate("Data inicial (AAAA-MM-DD): ");
            to = input.readDate("Data final (AAAA-MM-DD): ");
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
        System.out.printf("Extrato de %s, de %s a %s:%n", CliFormat.maskCpf(clientId), from, to);
        for (FinancialOperation operation : operations) {
            System.out.printf("- %s | %s | %s | %s%n", operation.date(), operation.type(),
                    CliFormat.money(operation.amount()), operation.description());
        }
    }

    /** Consulta o saldo da aplicação escolhida ou o total do cliente. */
    private void showClientBalance() {
        String clientId = input.readRequired("CPF/identificador do cliente: ");
        String investmentId = input.readLine("Código da aplicação (vazio para saldo total do cliente): ").trim();
        BigDecimal balance = investmentId.isEmpty()
                ? system.getClientBalance(clientId)
                : system.getBalance(clientId, investmentId);
        System.out.println("Saldo: " + CliFormat.money(balance));
    }

    /** Consulta o saldo líquido total do sistema. */
    private void showSystemBalance() {
        System.out.println("Saldo total do sistema: " + CliFormat.money(system.getSystemBalance()));
    }

    /** Atualiza as aplicações para a data informada. */
    private void updateInvestments() {
        LocalDate date = input.readDate("Data de atualização (AAAA-MM-DD): ");
        system.updateAll(date);
        if (date.getDayOfWeek().getValue() >= 6) {
            System.out.println("A data informada é fim de semana; o serviço não processou a atualização.");
        } else {
            System.out.println("Aplicações atualizadas com sucesso.");
        }
    }

    /** Carrega um estado e só o substitui depois da leitura bem-sucedida. */
    private void loadState() {
        Path path = input.readPath("Caminho do arquivo para carregar: ");
        try {
            system = InvestmentSystem.load(path);
            System.out.println("Estado carregado com sucesso de " + path + ".");
        } catch (IOException e) {
            System.out.println("Falha ao carregar " + path + ": " + CliFormat.messageOf(e));
        }
    }

    /** Salva o estado no caminho solicitado. */
    private void saveState() {
        saveTo(input.readPath("Caminho do arquivo para salvar: "));
    }

    /** Cria a pasta pai necessária e informa qualquer falha de persistência. */
    private boolean saveTo(Path path) {
        try {
            Path parent = path.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            system.save(path);
            if (path.toAbsolutePath().normalize().equals(DEFAULT_FILE.toAbsolutePath().normalize())) {
                saveDefaultOnExit = true;
            }
            System.out.println("Estado salvo com sucesso em " + path + ".");
            return true;
        } catch (IOException | RuntimeException e) {
            System.out.println("Falha ao salvar " + path + ": " + CliFormat.messageOf(e));
            return false;
        }
    }

    /** Consulta e exibe as cotações disponíveis no servidor TCP. */
    private void showQuotes() {
        String host = input.readRequired("Servidor de cotações (host): ");
        int port = input.readInt("Porta: ", 1, 65535);
        try {
            Map<String, BigDecimal> quotes = new QuoteClient(host, port).getQuotes();
            if (quotes.isEmpty()) {
                System.out.println("O servidor não disponibilizou cotações.");
                return;
            }
            System.out.println("Cotações disponíveis no servidor:");
            for (Map.Entry<String, BigDecimal> quote : quotes.entrySet()) {
                System.out.printf("- %s: %s%n", quote.getKey(), CliFormat.money(quote.getValue()));
            }
        } catch (IOException | RuntimeException e) {
            System.out.println("Falha ao consultar o servidor de cotações: " + CliFormat.messageOf(e));
        }
    }

    private record InitialState(InvestmentSystem system, boolean saveDefaultOnExit) { }
}

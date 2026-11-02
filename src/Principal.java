import terminal.FormatadorTerminal;
import terminal.EntradaTerminal;
import terminal.TermosInvestimento;

import modelo.ContaCliente;
import modelo.OperacaoFinanceira;
import modelo.AplicacaoFinanceira;
import modelo.TipoOperacao;
import modelo.DadosPessoais;
import mercado.ClienteCotacoes;
import servicos.SistemaInvestimentos;

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
public final class Principal {
    /** Arquivo padrão usado para carregar e salvar o estado do sistema. */
    private static final Path DEFAULT_FILE = Path.of("data", "sgi.db");

    private final EntradaTerminal input;
    private SistemaInvestimentos system;
    private boolean saveDefaultOnExit;

    private Principal(EntradaTerminal input, SistemaInvestimentos system, boolean saveDefaultOnExit) {
        this.input = input;
        this.system = system;
        this.saveDefaultOnExit = saveDefaultOnExit;
    }

    /** Carrega o estado padrão e inicia a interface interativa. */
    public static void main(String[] args) {
        try (Scanner scanner = new Scanner(System.in)) {
            EstadoInicial initialState = carregarEstadoPadrao();
            new Principal(new EntradaTerminal(scanner), initialState.system(), initialState.saveDefaultOnExit()).executar();
        }
    }

    /** Usa o arquivo padrão quando disponível e protege dados que não puderam ser lidos. */
    private static EstadoInicial carregarEstadoPadrao() {
        if (!Files.exists(DEFAULT_FILE)) {
            return new EstadoInicial(new SistemaInvestimentos(), true);
        }
        try {
            System.out.println("Carregando estado de " + DEFAULT_FILE + "...");
            return new EstadoInicial(SistemaInvestimentos.carregar(DEFAULT_FILE), true);
        } catch (IOException | RuntimeException e) {
            System.out.println("Não foi possível carregar " + DEFAULT_FILE + ": " + FormatadorTerminal.mensagemDe(e));
            System.out.println("O sistema será iniciado vazio; o arquivo original será preservado.");
            return new EstadoInicial(new SistemaInvestimentos(), false);
        }
    }

    /** Mantém o menu ativo até a saída explícita ou o fim da entrada. */
    private void executar() {
        boolean running = true;
        while (running) {
            try {
                exibirMenu();
                String option = input.lerLinha("Escolha uma opção: ");
                switch (option) {
                    case "1" -> cadastrarCliente();
                    case "2" -> listarClientes();
                    case "3" -> consultarCliente();
                    case "4" -> cadastrarAplicacao();
                    case "5" -> listarAplicacoes();
                    case "6" -> lancarOperacao(TipoOperacao.DEPOSIT);
                    case "7" -> lancarOperacao(TipoOperacao.WITHDRAWAL);
                    case "8" -> exibirExtrato();
                    case "9" -> exibirSaldoCliente();
                    case "10" -> exibirSaldoSistema();
                    case "11" -> atualizarAplicacoes();
                    case "12" -> carregarEstado();
                    case "13" -> salvarEstado();
                    case "14" -> exibirCotacoes();
                    case "0" -> running = false;
                    default -> System.out.println("Opção inválida. Informe uma opção do menu.");
                }
            } catch (EntradaTerminal.EndOfInputException e) {
                running = false;
            } catch (RuntimeException e) {
                System.out.println("Não foi possível concluir a operação: " + FormatadorTerminal.mensagemDe(e));
            }
        }
        if (saveDefaultOnExit) {
            salvarEm(DEFAULT_FILE);
        } else {
            System.out.println("Salvamento automático ignorado para preservar o arquivo padrão.");
        }
        System.out.println("SGI encerrado.");
    }

    /** Mostra as operações disponíveis no terminal. */
    private void exibirMenu() {
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
    private void cadastrarCliente() {
        String name = input.lerObrigatorio("Nome completo: ");
        String cpf = input.lerObrigatorio("CPF (será ocultado nas listagens): ");
        String mobile = input.lerObrigatorio("Celular (com DDD): ");
        int agency = input.lerInteiro("Número da agência: ", 1, Integer.MAX_VALUE);
        long account = input.lerInteiroLongo("Número da conta: ", 1, Long.MAX_VALUE);

        system.cadastrarCliente(new DadosPessoais(name, cpf, mobile), agency, account);
        System.out.println("Cliente cadastrado com sucesso.");
    }

    /** Lista clientes sem revelar o CPF completo. */
    private void listarClientes() {
        List<ContaCliente> clients = system.listarClientes();
        if (clients.isEmpty()) {
            System.out.println("Não há clientes cadastrados.");
            return;
        }
        System.out.println("Clientes cadastrados:");
        for (ContaCliente client : clients) {
            DadosPessoais personalData = client.getDadosPessoais();
            System.out.printf("- %s | CPF: %s%n", personalData.getNome(),
                    FormatadorTerminal.mascararCpf(personalData.getCpf()));
        }
    }

    /** Consulta os dados cadastrais e o saldo consolidado de um cliente. */
    private void consultarCliente() {
        String clientId = input.lerObrigatorio("CPF/identificador do cliente: ");
        Optional<ContaCliente> result = system.buscarCliente(clientId);
        if (result.isEmpty()) {
            System.out.println("Cliente não encontrado.");
            return;
        }
        ContaCliente client = result.get();
        DadosPessoais personalData = client.getDadosPessoais();
        System.out.println("Cliente: " + personalData.getNome());
        System.out.println("CPF/identificador: " + FormatadorTerminal.mascararCpf(personalData.getCpf()));
        System.out.println("Agência/conta: " + client.getNumeroAgencia() + "/" + client.getNumeroConta());
        System.out.println("Celular: " + personalData.getCelular());
        System.out.println("Saldo total: " + FormatadorTerminal.formatarMoeda(system.getSaldoCliente(clientId)));
    }

    /** Cadastra uma aplicação com termos padrão ou personalizados. */
    private void cadastrarAplicacao() {
        String clientId = input.lerObrigatorio("CPF/identificador do cliente: ");
        String name = input.lerObrigatorio("Nome da aplicação: ");
        String number = input.lerObrigatorio("Número da aplicação: ");
        BigDecimal initialValue = input.lerValorPositivo("Valor inicial: ");
        BigDecimal rate = input.lerTaxaMensal();
        AplicacaoFinanceira investment = new AplicacaoFinanceira(name, number, initialValue, rate);

        if (input.lerSimNao("Usar configuração padrão da aplicação? (s/n): ")) {
            System.out.println("""
                    Configuração padrão da aplicação:
                    - rendimento diário; sem vencimento e sem permanência mínima;
                    - resgate diário;
                    - IOF de 3,5% nos primeiros 29 dias; IR regressivo.
                    """);
        } else {
            TermosInvestimento.configurar(investment, input);
        }
        system.cadastrarAplicacao(clientId, investment);
        System.out.println("Aplicação cadastrada com sucesso.");
    }

    /** Lista os dados principais das aplicações de um cliente. */
    private void listarAplicacoes() {
        String clientId = input.lerObrigatorio("CPF/identificador do cliente: ");
        List<AplicacaoFinanceira> investments = system.listarAplicacoes(clientId);
        if (investments.isEmpty()) {
            System.out.println("O cliente não possui aplicações cadastradas.");
            return;
        }
        System.out.println("Aplicações de " + FormatadorTerminal.mascararCpf(clientId) + ":");
        for (AplicacaoFinanceira investment : investments) {
            System.out.printf("- %s | número: %s | saldo: %s | aporte inicial: %s | taxa mensal: %s%n",
                    investment.getNome(), investment.getNumero(), FormatadorTerminal.formatarMoeda(investment.getSaldo()),
                    FormatadorTerminal.formatarMoeda(investment.getValorInicial()), investment.getTaxaMensal());
        }
    }

    /** Registra um depósito ou uma retirada na aplicação indicada. */
    private void lancarOperacao(TipoOperacao type) {
        String clientId = input.lerObrigatorio("CPF/identificador do cliente: ");
        String investmentId = input.lerObrigatorio("Código da aplicação: ");
        LocalDate date = input.lerData("Data da operação (AAAA-MM-DD): ");
        BigDecimal amount = input.lerValorPositivo("Valor: ");
        String description = input.lerObrigatorio("Descrição: ");
        OperacaoFinanceira operation = new OperacaoFinanceira(date, amount, description, type);
        system.registrarOperacao(clientId, investmentId, operation);
        String label = type == TipoOperacao.DEPOSIT ? "Depósito" : "Retirada";
        System.out.println(label + " lançado com sucesso.");
    }

    /** Exibe o extrato no período escolhido ou nos últimos 30 dias. */
    private void exibirExtrato() {
        String clientId = input.lerObrigatorio("CPF/identificador do cliente: ");
        String investmentId = input.lerObrigatorio("Código da aplicação: ");
        LocalDate from;
        LocalDate to;
        if (input.lerSimNao("Usar os últimos 30 dias? (s/n): ")) {
            to = LocalDate.now();
            from = to.minusDays(29);
        } else {
            from = input.lerData("Data inicial (AAAA-MM-DD): ");
            to = input.lerData("Data final (AAAA-MM-DD): ");
        }
        if (from.isAfter(to)) {
            System.out.println("Período inválido: a data inicial deve ser anterior ou igual à final.");
            return;
        }
        List<OperacaoFinanceira> operations = system.obterExtrato(clientId, investmentId, from, to);
        if (operations.isEmpty()) {
            System.out.println("Não há operações no período informado.");
            return;
        }
        System.out.printf("Extrato de %s, de %s a %s:%n", FormatadorTerminal.mascararCpf(clientId), from, to);
        for (OperacaoFinanceira operation : operations) {
            System.out.printf("- %s | %s | %s | %s%n", operation.data(), operation.tipo(),
                    FormatadorTerminal.formatarMoeda(operation.valor()), operation.descricao());
        }
    }

    /** Consulta o saldo da aplicação escolhida ou o total do cliente. */
    private void exibirSaldoCliente() {
        String clientId = input.lerObrigatorio("CPF/identificador do cliente: ");
        String investmentId = input.lerLinha("Código da aplicação (vazio para saldo total do cliente): ").trim();
        BigDecimal balance = investmentId.isEmpty()
                ? system.getSaldoCliente(clientId)
                : system.getSaldo(clientId, investmentId);
        System.out.println("Saldo: " + FormatadorTerminal.formatarMoeda(balance));
    }

    /** Consulta o saldo líquido total do sistema. */
    private void exibirSaldoSistema() {
        System.out.println("Saldo total do sistema: " + FormatadorTerminal.formatarMoeda(system.getSaldoSistema()));
    }

    /** Atualiza as aplicações para a data informada. */
    private void atualizarAplicacoes() {
        LocalDate date = input.lerData("Data de atualização (AAAA-MM-DD): ");
        system.atualizarTodos(date);
        if (date.getDayOfWeek().getValue() >= 6) {
            System.out.println("A data informada é fim de semana; o serviço não processou a atualização.");
        } else {
            System.out.println("Aplicações atualizadas com sucesso.");
        }
    }

    /** Carrega um estado e só o substitui depois da leitura bem-sucedida. */
    private void carregarEstado() {
        Path path = input.lerCaminho("Caminho do arquivo para carregar: ");
        try {
            system = SistemaInvestimentos.carregar(path);
            System.out.println("Estado carregado com sucesso de " + path + ".");
        } catch (IOException e) {
            System.out.println("Falha ao carregar " + path + ": " + FormatadorTerminal.mensagemDe(e));
        }
    }

    /** Salva o estado no caminho solicitado. */
    private void salvarEstado() {
        salvarEm(input.lerCaminho("Caminho do arquivo para salvar: "));
    }

    /** Cria a pasta pai necessária e informa qualquer falha de persistência. */
    private boolean salvarEm(Path path) {
        try {
            Path parent = path.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            system.salvar(path);
            if (path.toAbsolutePath().normalize().equals(DEFAULT_FILE.toAbsolutePath().normalize())) {
                saveDefaultOnExit = true;
            }
            System.out.println("Estado salvo com sucesso em " + path + ".");
            return true;
        } catch (IOException | RuntimeException e) {
            System.out.println("Falha ao salvar " + path + ": " + FormatadorTerminal.mensagemDe(e));
            return false;
        }
    }

    /** Consulta e exibe as cotações disponíveis no servidor TCP. */
    private void exibirCotacoes() {
        String host = input.lerObrigatorio("Servidor de cotações (host): ");
        int port = input.lerInteiro("Porta: ", 1, 65535);
        try {
            Map<String, BigDecimal> quotes = new ClienteCotacoes(host, port).obterCotacoes();
            if (quotes.isEmpty()) {
                System.out.println("O servidor não disponibilizou cotações.");
                return;
            }
            System.out.println("Cotações disponíveis no servidor:");
            for (Map.Entry<String, BigDecimal> quote : quotes.entrySet()) {
                System.out.printf("- %s: %s%n", quote.getKey(), FormatadorTerminal.formatarMoeda(quote.getValue()));
            }
        } catch (IOException | RuntimeException e) {
            System.out.println("Falha ao consultar o servidor de cotações: " + FormatadorTerminal.mensagemDe(e));
        }
    }

    private record EstadoInicial(SistemaInvestimentos system, boolean saveDefaultOnExit) { }
}

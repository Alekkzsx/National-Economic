import mercado.ClienteCotacoes;
import modelo.AplicacaoFinanceira;
import modelo.ContaCliente;
import modelo.DadosPessoais;
import modelo.OperacaoFinanceira;
import modelo.TipoOperacao;
import servicos.SistemaInvestimentos;
import terminal.EntradaTerminal;
import terminal.FormatadorTerminal;
import terminal.TermosInvestimento;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
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
    private static final Path ARQUIVO_PADRAO = Path.of("data", "sgi.db");

    /** Quantidade de dias, contando o dia atual, do extrato padrão. */
    private static final int DIAS_EXTRATO_PADRAO = 30;

    /** Maior número de porta TCP válido. */
    private static final int PORTA_MAXIMA = 65_535;

    private static final String PROMPT_CLIENTE = "CPF/identificador do cliente: ";
    private static final String PROMPT_APLICACAO = "Código da aplicação: ";
    private static final String FORMATO_DATA = "(AAAA-MM-DD): ";

    private final EntradaTerminal entrada;

    /** Não é final porque a opção "Carregar estado" substitui a instância inteira. */
    private SistemaInvestimentos sistema;

    /**
     * Indica se o estado deve ser gravado em {@link #ARQUIVO_PADRAO} ao sair.
     *
     * <p>Fica desligado quando o arquivo padrão existe mas não pôde ser lido,
     * para não sobrescrevê-lo com um sistema vazio. Volta a ser ligado assim
     * que o usuário salva explicitamente no arquivo padrão.</p>
     */
    private boolean salvarAutomaticamenteAoSair;

    private Principal(EntradaTerminal entrada, EstadoInicial estadoInicial) {
        this.entrada = entrada;
        this.sistema = estadoInicial.sistema();
        this.salvarAutomaticamenteAoSair = estadoInicial.salvarAutomaticamenteAoSair();
    }

    /** Carrega o estado padrão e inicia a interface interativa. */
    public static void main(String[] args) {
        try (Scanner leitor = new Scanner(System.in)) {
            new Principal(new EntradaTerminal(leitor), carregarEstadoPadrao()).executar();
        }
    }

    // ---------------------------------------------------------------------
    // Ciclo de vida
    // ---------------------------------------------------------------------

    /** Usa o arquivo padrão quando disponível e protege dados que não puderam ser lidos. */
    private static EstadoInicial carregarEstadoPadrao() {
        if (!Files.exists(ARQUIVO_PADRAO)) {
            return new EstadoInicial(new SistemaInvestimentos(), true);
        }
        try {
            System.out.println("Carregando estado de " + ARQUIVO_PADRAO + "...");
            return new EstadoInicial(SistemaInvestimentos.carregar(ARQUIVO_PADRAO), true);
        } catch (IOException | RuntimeException e) {
            System.out.println("Não foi possível carregar " + ARQUIVO_PADRAO + ": "
                    + FormatadorTerminal.mensagemDe(e));
            System.out.println("O sistema será iniciado vazio; o arquivo original será preservado.");
            return new EstadoInicial(new SistemaInvestimentos(), false);
        }
    }

    /** Mantém o menu ativo até a saída explícita ou o fim da entrada. */
    private void executar() {
        boolean emExecucao = true;
        while (emExecucao) {
            try {
                exibirMenu();
                emExecucao = processarOpcao(entrada.lerLinha("Escolha uma opção: "));
            } catch (EntradaTerminal.EndOfInputException e) {
                emExecucao = false;
            } catch (RuntimeException e) {
                System.out.println("Não foi possível concluir a operação: " + FormatadorTerminal.mensagemDe(e));
            }
        }
        encerrar();
    }

    /**
     * Executa a ação associada à opção do menu.
     *
     * @return {@code false} quando o usuário pede para sair; {@code true} caso contrário
     */
    private boolean processarOpcao(String opcao) {
        switch (opcao) {
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
            case "15" -> listarClientesEAplicacoes();
            case "16" -> alterarCliente();
            case "17" -> excluirCliente();
            case "18" -> alterarAplicacao();
            case "19" -> excluirAplicacao();
            case "20" -> alterarOperacao();
            case "21" -> excluirOperacao();
            case "0" -> {
                return false;
            }
            default -> System.out.println("Opção inválida. Informe uma opção do menu.");
        }
        return true;
    }

    /** Salva no arquivo padrão, quando permitido, e finaliza a sessão. */
    private void encerrar() {
        if (salvarAutomaticamenteAoSair) {
            salvarEm(ARQUIVO_PADRAO);
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
                15. Listar clientes e aplicações
                16. Alterar dados de cliente
                17. Excluir cliente e dados vinculados
                18. Alterar nome ou taxa de aplicação
                19. Excluir aplicação e operações vinculadas
                20. Alterar operação financeira
                21. Excluir operação financeira
                0. Salvar e sair
                """);
    }

    // ---------------------------------------------------------------------
    // Clientes
    // ---------------------------------------------------------------------

    /** Cadastra os dados pessoais e bancários de um cliente. */
    private void cadastrarCliente() {
        String nome = entrada.lerObrigatorio("Nome completo: ");
        String cpf = entrada.lerObrigatorio("CPF (será ocultado nas listagens): ");
        String celular = entrada.lerObrigatorio("Celular (com DDD): ");
        int agencia = entrada.lerInteiro("Número da agência: ", 1, Integer.MAX_VALUE);
        long conta = entrada.lerInteiroLongo("Número da conta: ", 1, Long.MAX_VALUE);

        sistema.cadastrarCliente(new DadosPessoais(nome, cpf, celular), agencia, conta);
        System.out.println("Cliente cadastrado com sucesso.");
    }

    /** Lista clientes sem revelar o CPF completo. */
    private void listarClientes() {
        List<ContaCliente> clientes = sistema.listarClientes();
        if (clientes.isEmpty()) {
            System.out.println("Não há clientes cadastrados.");
            return;
        }
        System.out.println("Clientes cadastrados:");
        for (ContaCliente cliente : clientes) {
            DadosPessoais dados = cliente.getDadosPessoais();
            System.out.printf("- %s | CPF: %s%n", dados.getNome(), FormatadorTerminal.mascararCpf(dados.getCpf()));
        }
    }

    /** Lista cada cliente junto das aplicações que compõem sua carteira. */
    private void listarClientesEAplicacoes() {
        List<ContaCliente> clientes = sistema.listarClientes();
        if (clientes.isEmpty()) {
            System.out.println("Não há clientes cadastrados.");
            return;
        }
        for (ContaCliente cliente : clientes) {
            DadosPessoais dados = cliente.getDadosPessoais();
            System.out.printf("%s | CPF: %s | Agência/conta: %d/%d%n",
                    dados.getNome(),
                    FormatadorTerminal.mascararCpf(dados.getCpf()),
                    cliente.getNumeroAgencia(),
                    cliente.getNumeroConta());
            List<AplicacaoFinanceira> aplicacoes = cliente.listarAplicacoes();
            if (aplicacoes.isEmpty()) {
                System.out.println("  (sem aplicações cadastradas)");
                continue;
            }
            for (AplicacaoFinanceira aplicacao : aplicacoes) {
                System.out.printf("  - %s | número: %s | saldo: %s%n",
                        aplicacao.getNome(),
                        aplicacao.getNumero(),
                        FormatadorTerminal.formatarMoeda(aplicacao.getSaldoLiquido()));
            }
        }
    }

    /** Altera o nome e o celular do cliente, preservando o CPF como identificador. */
    private void alterarCliente() {
        String cpf = lerIdentificadorCliente();
        ContaCliente cliente = sistema.buscarCliente(cpf)
                .orElseThrow(() -> new IllegalArgumentException("Cliente não encontrado."));
        String nome = entrada.lerObrigatorio("Novo nome completo: ");
        String celular = entrada.lerObrigatorio("Novo celular (com DDD): ");
        sistema.alterarDadosCliente(cpf, nome, celular);
        System.out.println("Dados do cliente " + FormatadorTerminal.mascararCpf(cliente.getDadosPessoais().getCpf())
                + " atualizados com sucesso.");
    }

    /** Exclui o cliente e a carteira inteira após confirmação explícita. */
    private void excluirCliente() {
        String cpf = lerIdentificadorCliente();
        ContaCliente cliente = sistema.buscarCliente(cpf)
                .orElseThrow(() -> new IllegalArgumentException("Cliente não encontrado."));
        if (!entrada.lerSimNao("Excluir cliente e todas as aplicações vinculadas? (s/n): ")) {
            System.out.println("Exclusão cancelada.");
            return;
        }
        sistema.excluirCliente(cpf);
        System.out.println("Cliente " + FormatadorTerminal.mascararCpf(cliente.getDadosPessoais().getCpf())
                + " e seus dados vinculados foram excluídos.");
    }

    /** Consulta os dados cadastrais e o saldo consolidado de um cliente. */
    private void consultarCliente() {
        String identificadorCliente = lerIdentificadorCliente();
        Optional<ContaCliente> resultado = sistema.buscarCliente(identificadorCliente);
        if (resultado.isEmpty()) {
            System.out.println("Cliente não encontrado.");
            return;
        }
        ContaCliente cliente = resultado.get();
        DadosPessoais dados = cliente.getDadosPessoais();
        BigDecimal saldoTotal = sistema.getSaldoCliente(identificadorCliente);

        System.out.println("Cliente: " + dados.getNome());
        System.out.println("CPF/identificador: " + FormatadorTerminal.mascararCpf(dados.getCpf()));
        System.out.println("Agência/conta: " + cliente.getNumeroAgencia() + "/" + cliente.getNumeroConta());
        System.out.println("Celular: " + dados.getCelular());
        System.out.println("Saldo total: " + FormatadorTerminal.formatarMoeda(saldoTotal));
    }

    // ---------------------------------------------------------------------
    // Aplicações e operações
    // ---------------------------------------------------------------------

    /** Cadastra uma aplicação com termos padrão ou personalizados. */
    private void cadastrarAplicacao() {
        String identificadorCliente = lerIdentificadorCliente();
        String nome = entrada.lerObrigatorio("Nome da aplicação: ");
        String numero = entrada.lerObrigatorio("Número da aplicação: ");
        BigDecimal valorInicial = entrada.lerValorPositivo("Valor inicial: ");
        BigDecimal taxaMensal = entrada.lerTaxaMensal();
        AplicacaoFinanceira aplicacao = new AplicacaoFinanceira(nome, numero, valorInicial, taxaMensal);

        if (entrada.lerSimNao("Usar configuração padrão da aplicação? (s/n): ")) {
            System.out.println("""
                    Configuração padrão da aplicação:
                    - rendimento diário; sem vencimento e sem permanência mínima;
                    - resgate diário;
                    - IOF de 3,5% nos primeiros 29 dias; IR regressivo.
                    """);
        } else {
            TermosInvestimento.configurar(aplicacao, entrada);
        }
        sistema.cadastrarAplicacao(identificadorCliente, aplicacao);
        System.out.println("Aplicação cadastrada com sucesso.");
    }

    /** Lista os dados principais das aplicações de um cliente. */
    private void listarAplicacoes() {
        String identificadorCliente = lerIdentificadorCliente();
        List<AplicacaoFinanceira> aplicacoes = sistema.listarAplicacoes(identificadorCliente);
        if (aplicacoes.isEmpty()) {
            System.out.println("O cliente não possui aplicações cadastradas.");
            return;
        }
        System.out.println("Aplicações de " + FormatadorTerminal.mascararCpf(identificadorCliente) + ":");
        for (AplicacaoFinanceira aplicacao : aplicacoes) {
            System.out.printf("- %s | número: %s | saldo: %s | aporte inicial: %s | taxa mensal: %s%n",
                    aplicacao.getNome(),
                    aplicacao.getNumero(),
                    FormatadorTerminal.formatarMoeda(aplicacao.getSaldo()),
                    FormatadorTerminal.formatarMoeda(aplicacao.getValorInicial()),
                    aplicacao.getTaxaMensal());
        }
    }

    /** Altera os campos descritivos de uma aplicação sem apagar seu histórico. */
    private void alterarAplicacao() {
        String cpf = lerIdentificadorCliente();
        String numero = lerIdentificadorAplicacao();
        String nome = entrada.lerObrigatorio("Novo nome da aplicação: ");
        BigDecimal taxaMensal = entrada.lerTaxaMensal();
        sistema.alterarAplicacao(cpf, numero, nome, taxaMensal);
        System.out.println("Aplicação atualizada com sucesso.");
    }

    /** Exclui uma aplicação e todos os lançamentos associados após confirmação. */
    private void excluirAplicacao() {
        String cpf = lerIdentificadorCliente();
        String numero = lerIdentificadorAplicacao();
        if (!entrada.lerSimNao("Excluir a aplicação e todas as operações vinculadas? (s/n): ")) {
            System.out.println("Exclusão cancelada.");
            return;
        }
        sistema.excluirAplicacao(cpf, numero);
        System.out.println("Aplicação e operações vinculadas excluídas.");
    }

    /** Exibe os lançamentos disponíveis e retorna o índice escolhido pelo usuário. */
    private int selecionarOperacao(String cpf, String numeroAplicacao) {
        List<OperacaoFinanceira> operacoes = sistema.listarOperacoes(cpf, numeroAplicacao);
        if (operacoes.isEmpty()) {
            System.out.println("A aplicação não possui operações cadastradas.");
            return -1;
        }
        for (int indice = 0; indice < operacoes.size(); indice++) {
            OperacaoFinanceira operacao = operacoes.get(indice);
            System.out.printf("%d. %s | %s | %s | %s%n",
                    indice + 1,
                    operacao.data(),
                    operacao.tipo(),
                    FormatadorTerminal.formatarMoeda(operacao.valor()),
                    operacao.descricao());
        }
        return entrada.lerInteiro("Número da operação: ", 1, operacoes.size()) - 1;
    }

    /** Substitui um lançamento e atualiza o saldo de forma transacional. */
    private void alterarOperacao() {
        String cpf = lerIdentificadorCliente();
        String numero = lerIdentificadorAplicacao();
        int indice = selecionarOperacao(cpf, numero);
        if (indice < 0) {
            return;
        }
        OperacaoFinanceira novaOperacao = lerOperacao();
        sistema.alterarOperacao(cpf, numero, indice, novaOperacao);
        System.out.println("Operação atualizada com sucesso.");
    }

    /** Remove um lançamento e reverte seu efeito financeiro após confirmação. */
    private void excluirOperacao() {
        String cpf = lerIdentificadorCliente();
        String numero = lerIdentificadorAplicacao();
        int indice = selecionarOperacao(cpf, numero);
        if (indice < 0) {
            return;
        }
        if (!entrada.lerSimNao("Excluir a operação selecionada e reverter seu efeito no saldo? (s/n): ")) {
            System.out.println("Exclusão cancelada.");
            return;
        }
        sistema.excluirOperacao(cpf, numero, indice);
        System.out.println("Operação excluída e saldo ajustado com sucesso.");
    }

    /** Lê os dados validados de um depósito ou retirada. */
    private OperacaoFinanceira lerOperacao() {
        LocalDate data = entrada.lerData("Data da operação " + FORMATO_DATA);
        BigDecimal valor = entrada.lerValorPositivo("Valor: ");
        String descricao = entrada.lerObrigatorio("Descrição: ");
        int opcaoTipo = entrada.lerInteiro("Tipo (1 = depósito, 2 = retirada): ", 1, 2);
        TipoOperacao tipo = opcaoTipo == 1 ? TipoOperacao.DEPOSIT : TipoOperacao.WITHDRAWAL;
        return new OperacaoFinanceira(data, valor, descricao, tipo);
    }

    /** Registra um depósito ou uma retirada na aplicação indicada. */
    private void lancarOperacao(TipoOperacao tipo) {
        String identificadorCliente = lerIdentificadorCliente();
        String identificadorAplicacao = lerIdentificadorAplicacao();
        LocalDate data = entrada.lerData("Data da operação " + FORMATO_DATA);
        BigDecimal valor = entrada.lerValorPositivo("Valor: ");
        String descricao = entrada.lerObrigatorio("Descrição: ");

        OperacaoFinanceira operacao = new OperacaoFinanceira(data, valor, descricao, tipo);
        sistema.registrarOperacao(identificadorCliente, identificadorAplicacao, operacao);

        String mensagemSucesso = switch (tipo) {
            case DEPOSIT -> "Depósito lançado com sucesso.";
            case WITHDRAWAL -> "Retirada lançada com sucesso.";
        };
        System.out.println(mensagemSucesso);
    }

    /** Exibe o extrato no período escolhido ou nos últimos {@value #DIAS_EXTRATO_PADRAO} dias. */
    private void exibirExtrato() {
        String identificadorCliente = lerIdentificadorCliente();
        String identificadorAplicacao = lerIdentificadorAplicacao();

        LocalDate dataInicial;
        LocalDate dataFinal;
        if (entrada.lerSimNao("Usar os últimos " + DIAS_EXTRATO_PADRAO + " dias? (s/n): ")) {
            dataFinal = LocalDate.now();
            // O dia atual já conta como um dos dias do período.
            dataInicial = dataFinal.minusDays(DIAS_EXTRATO_PADRAO - 1L);
        } else {
            dataInicial = entrada.lerData("Data inicial " + FORMATO_DATA);
            dataFinal = entrada.lerData("Data final " + FORMATO_DATA);
        }
        if (dataInicial.isAfter(dataFinal)) {
            System.out.println("Período inválido: a data inicial deve ser anterior ou igual à final.");
            return;
        }

        List<OperacaoFinanceira> operacoes =
                sistema.obterExtrato(identificadorCliente, identificadorAplicacao, dataInicial, dataFinal);
        if (operacoes.isEmpty()) {
            System.out.println("Não há operações no período informado.");
            return;
        }
        System.out.printf("Extrato de %s, de %s a %s:%n",
                FormatadorTerminal.mascararCpf(identificadorCliente), dataInicial, dataFinal);
        for (OperacaoFinanceira operacao : operacoes) {
            System.out.printf("- %s | %s | %s | %s%n",
                    operacao.data(),
                    operacao.tipo(),
                    FormatadorTerminal.formatarMoeda(operacao.valor()),
                    operacao.descricao());
        }
    }

    // ---------------------------------------------------------------------
    // Saldos e atualização
    // ---------------------------------------------------------------------

    /** Consulta o saldo da aplicação escolhida ou, se nenhuma for informada, o total do cliente. */
    private void exibirSaldoCliente() {
        String identificadorCliente = lerIdentificadorCliente();
        String identificadorAplicacao =
                entrada.lerLinha("Código da aplicação (vazio para saldo total do cliente): ").trim();

        BigDecimal saldo = identificadorAplicacao.isEmpty()
                ? sistema.getSaldoCliente(identificadorCliente)
                : sistema.getSaldo(identificadorCliente, identificadorAplicacao);
        System.out.println("Saldo: " + FormatadorTerminal.formatarMoeda(saldo));
    }

    /** Consulta o saldo líquido total do sistema. */
    private void exibirSaldoSistema() {
        System.out.println("Saldo total do sistema: " + FormatadorTerminal.formatarMoeda(sistema.getSaldoSistema()));
    }

    /**
     * Atualiza as aplicações para a data informada.
     *
     * <p>O serviço ignora silenciosamente datas de fim de semana; a mensagem
     * exibida aqui apenas reflete essa regra para o usuário.</p>
     */
    private void atualizarAplicacoes() {
        LocalDate data = entrada.lerData("Data de atualização " + FORMATO_DATA);
        sistema.atualizarTodos(data);
        if (ehFimDeSemana(data)) {
            System.out.println("A data informada é fim de semana; o serviço não processou a atualização.");
        } else {
            System.out.println("Aplicações atualizadas com sucesso.");
        }
    }

    // ---------------------------------------------------------------------
    // Persistência
    // ---------------------------------------------------------------------

    /** Carrega um estado e só substitui o atual depois de uma leitura bem-sucedida. */
    private void carregarEstado() {
        Path caminho = entrada.lerCaminho("Caminho do arquivo para carregar: ");
        try {
            sistema = SistemaInvestimentos.carregar(caminho);
            System.out.println("Estado carregado com sucesso de " + caminho + ".");
        } catch (IOException | RuntimeException e) {
            System.out.println("Falha ao carregar " + caminho + ": " + FormatadorTerminal.mensagemDe(e));
        }
    }

    /** Salva o estado no caminho solicitado. */
    private void salvarEstado() {
        salvarEm(entrada.lerCaminho("Caminho do arquivo para salvar: "));
    }

    /**
     * Grava o estado em {@code caminho}, criando a pasta pai se necessário.
     *
     * <p>Um salvamento bem-sucedido no arquivo padrão reativa o salvamento
     * automático, pois o conteúdo dele passa a ser o estado atual.</p>
     */
    private void salvarEm(Path caminho) {
        try {
            Path pastaPai = caminho.toAbsolutePath().getParent();
            if (pastaPai != null) {
                Files.createDirectories(pastaPai);
            }
            sistema.salvar(caminho);
            if (ehArquivoPadrao(caminho)) {
                salvarAutomaticamenteAoSair = true;
            }
            System.out.println("Estado salvo com sucesso em " + caminho + ".");
        } catch (IOException | RuntimeException e) {
            System.out.println("Falha ao salvar " + caminho + ": " + FormatadorTerminal.mensagemDe(e));
        }
    }

    // ---------------------------------------------------------------------
    // Cotações
    // ---------------------------------------------------------------------

    /** Consulta e exibe as cotações disponíveis no servidor TCP. */
    private void exibirCotacoes() {
        String host = entrada.lerObrigatorio("Servidor de cotações (host): ");
        int porta = entrada.lerInteiro("Porta: ", 1, PORTA_MAXIMA);
        try {
            Map<String, BigDecimal> cotacoes = new ClienteCotacoes(host, porta).obterCotacoes();
            if (cotacoes.isEmpty()) {
                System.out.println("O servidor não disponibilizou cotações.");
                return;
            }
            System.out.println("Cotações disponíveis no servidor:");
            for (Map.Entry<String, BigDecimal> cotacao : cotacoes.entrySet()) {
                System.out.printf("- %s: %s%n", cotacao.getKey(), FormatadorTerminal.formatarMoeda(cotacao.getValue()));
            }
        } catch (IOException | RuntimeException e) {
            System.out.println("Falha ao consultar o servidor de cotações: " + FormatadorTerminal.mensagemDe(e));
        }
    }

    // ---------------------------------------------------------------------
    // Auxiliares
    // ---------------------------------------------------------------------

    private String lerIdentificadorCliente() {
        return entrada.lerObrigatorio(PROMPT_CLIENTE);
    }

    private String lerIdentificadorAplicacao() {
        return entrada.lerObrigatorio(PROMPT_APLICACAO);
    }

    private static boolean ehFimDeSemana(LocalDate data) {
        DayOfWeek dia = data.getDayOfWeek();
        return dia == DayOfWeek.SATURDAY || dia == DayOfWeek.SUNDAY;
    }

    /** Compara caminhos já normalizados para reconhecer variações como {@code ./data/sgi.db}. */
    private static boolean ehArquivoPadrao(Path caminho) {
        return caminho.toAbsolutePath().normalize().equals(ARQUIVO_PADRAO.toAbsolutePath().normalize());
    }

    /**
     * Estado com que a aplicação é iniciada.
     *
     * @param sistema                     sistema carregado do disco ou recém-criado
     * @param salvarAutomaticamenteAoSair se é seguro sobrescrever o arquivo padrão ao sair
     */
    private record EstadoInicial(SistemaInvestimentos sistema, boolean salvarAutomaticamenteAoSair) { }
}

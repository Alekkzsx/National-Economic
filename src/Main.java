import dominio.AplicacaoFinanceira;
import dominio.Cliente;
import dominio.DADOSPESSOAIS;
import dominio.DiaSemana;
import dominio.MultiplasOperacoesFinanceiras;
import dominio.OperacaoFinanceira;
import dominio.TipoOperacao;
import legado.servicos.MigradorSistemaInvestimentos;
import mercado.ClienteCotacoes;
import servico.SGI;
import terminal.EntradaTerminal;
import terminal.TermosInvestimento;
import terminal.util.AuxiliarCotacoes;
import terminal.util.FormatadorTerminal;

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
 * Itens 8 e 9 — Ponto de entrada e menu CRUD interativo do SGI no terminal,
 * integrado ao servidor de cotações (SELICRA, CDIR, USD e EUR).
 */
public final class Main {

    private static final Path ARQUIVO_PADRAO = Path.of("data", "sgi.db");
    private static final int DIAS_EXTRATO_PADRAO = 30;
    private static final int PORTA_PADRAO_COTACOES = 5050;
    private static final int PORTA_MAXIMA = 65_535;
    private static final String FORMATO_DATA = "(AAAA-MM-DD): ";

    private final EntradaTerminal entrada;
    private SGI sistema;
    private boolean salvarAutomaticamenteAoSair;

    private Main(EntradaTerminal entrada, EstadoInicial estadoInicial) {
        this.entrada = entrada;
        this.sistema = estadoInicial.sistema();
        this.salvarAutomaticamenteAoSair = estadoInicial.salvarAutomaticamenteAoSair();
    }

    public static void main(String[] args) {
        try (Scanner leitor = new Scanner(System.in)) {
            new Main(new EntradaTerminal(leitor), carregarEstadoPadrao()).executar();
        }
    }

    // -------------------------------------------------------------------------
    // Ciclo de vida e menu
    // -------------------------------------------------------------------------

    private static EstadoInicial carregarEstadoPadrao() {
        if (!Files.exists(ARQUIVO_PADRAO)) {
            return new EstadoInicial(new SGI(), true);
        }
        try {
            System.out.println("Carregando estado de " + ARQUIVO_PADRAO + "...");
            return new EstadoInicial(MigradorSistemaInvestimentos.carregarComMigracao(ARQUIVO_PADRAO), true);
        } catch (IOException | RuntimeException e) {
            System.out.println("Não foi possível carregar " + ARQUIVO_PADRAO + ": "
                    + FormatadorTerminal.mensagemDe(e));
            System.out.println("O sistema será iniciado vazio; o arquivo original será preservado.");
            return new EstadoInicial(new SGI(), false);
        }
    }

    private void executar() {
        boolean emExecucao = true;
        while (emExecucao) {
            try {
                exibirMenu();
                emExecucao = processarOpcao(entrada.lerLinha("Escolha uma opção: ").trim());
            } catch (EntradaTerminal.EndOfInputException e) {
                emExecucao = false;
            } catch (RuntimeException e) {
                System.out.println("Não foi possível concluir a operação: " + FormatadorTerminal.mensagemDe(e));
            }
        }
        encerrar();
    }

    private boolean processarOpcao(String opcao) {
        switch (opcao) {
            case "1" -> cadastrarCliente();
            case "2" -> listarClientes();
            case "3" -> consultarCliente();
            case "4" -> alterarCliente();
            case "5" -> excluirCliente();
            case "6" -> listarClientesEAplicacoes();
            case "7" -> cadastrarAplicacao();
            case "8" -> listarAplicacoes();
            case "9" -> alterarAplicacao();
            case "10" -> excluirAplicacao();
            case "11" -> lancarOperacao(TipoOperacao.DEPOSITO);
            case "12" -> lancarOperacao(TipoOperacao.RETIRADA);
            case "13" -> alterarOperacao();
            case "14" -> excluirOperacao();
            case "15" -> exibirExtrato();
            case "16" -> exibirSaldoCliente();
            case "17" -> exibirSaldoSistema();
            case "18" -> atualizarAplicacoes();
            case "19" -> consultarCotacoes();
            case "20" -> carregarEstado();
            case "21" -> salvarEstado();
            case "0" -> {
                return false;
            }
            default -> System.out.println("Opção inválida. Informe uma opção do menu.");
        }
        return true;
    }

    private void encerrar() {
        if (salvarAutomaticamenteAoSair) {
            salvarEm(ARQUIVO_PADRAO);
        } else {
            System.out.println("Salvamento automático ignorado para preservar o arquivo padrão.");
        }
        System.out.println("SGI encerrado.");
    }

    private void exibirMenu() {
        String statusCotacoes = AuxiliarCotacoes.resumoStatus(sistema);
        System.out.println();
        System.out.println("===== Sistema de Gestão de Investimentos (SGI) =====");
        if (!statusCotacoes.isEmpty()) {
            System.out.println(statusCotacoes);
        }
        System.out.println("""
                [Clientes]
                 1. Cadastrar cliente
                 2. Listar clientes
                 3. Consultar cliente
                 4. Alterar dados de cliente
                 5. Excluir cliente e dados vinculados
                 6. Listar clientes e aplicações
                [Aplicações]
                 7. Cadastrar aplicação
                 8. Listar aplicações de um cliente
                 9. Alterar nome ou taxa de aplicação
                10. Excluir aplicação e operações vinculadas
                [Operações e Extrato]
                11. Lançar depósito
                12. Lançar retirada
                13. Alterar operação financeira
                14. Excluir operação financeira
                15. Consultar extrato
                [Saldos e Atualização]
                16. Consultar saldo de cliente/aplicação
                17. Consultar saldo total do sistema
                18. Atualizar aplicações (dias úteis)
                [Cotações e Arquivos]
                19. Consultar e carregar cotações do servidor
                20. Carregar estado de arquivo
                21. Salvar estado em arquivo
                 0. Salvar e sair
                """);
    }

    // -------------------------------------------------------------------------
    // Clientes
    // -------------------------------------------------------------------------

    private void cadastrarCliente() {
        String nome = entrada.lerObrigatorio("Nome completo: ");
        DADOSPESSOAIS dadosPessoais;
        while (true) {
            String cpf = entrada.lerObrigatorio("CPF (será ocultado nas listagens): ");
            try {
                dadosPessoais = new DADOSPESSOAIS(nome, cpf);
            } catch (IllegalArgumentException e) {
                System.out.println("CPF inválido. Informe um CPF válido com 11 dígitos.");
                continue;
            }
            if (sistema.buscarCliente(dadosPessoais.getCpf()).isPresent()) {
                System.out.println("Já existe um cliente cadastrado com esse CPF.");
                continue;
            }
            break;
        }

        while (true) {
            String celular = entrada.lerObrigatorio("Celular (com DDD): ");
            try {
                dadosPessoais.setCelular(celular);
                break;
            } catch (IllegalArgumentException e) {
                System.out.println("Celular inválido. Informe de 10 a 13 dígitos, incluindo o DDD.");
            }
        }
        int agencia = entrada.lerInteiro("Número da agência: ", 1, Integer.MAX_VALUE);
        long conta = entrada.lerInteiroLongo("Número da conta: ", 1, Long.MAX_VALUE);

        sistema.cadastrarCliente(dadosPessoais, agencia, conta);
        System.out.println("Cliente cadastrado com sucesso.");
    }

    private void listarClientes() {
        List<Cliente> clientes = sistema.getClientes();
        if (clientes.isEmpty()) {
            System.out.println("Não há clientes cadastrados.");
            return;
        }
        System.out.println("Clientes cadastrados:");
        for (Cliente cliente : clientes) {
            DADOSPESSOAIS dados = cliente.getDadosPessoais();
            System.out.printf("- %s | CPF: %s%n", dados.getNome(), FormatadorTerminal.mascararCpf(dados.getCpf()));
        }
    }

    private void listarClientesEAplicacoes() {
        List<Cliente> clientes = sistema.getClientes();
        if (clientes.isEmpty()) {
            System.out.println("Não há clientes cadastrados.");
            return;
        }
        for (Cliente cliente : clientes) {
            DADOSPESSOAIS dados = cliente.getDadosPessoais();
            System.out.printf("%s | CPF: %s | Agência/conta: %d/%d%n",
                    dados.getNome(), FormatadorTerminal.mascararCpf(dados.getCpf()),
                    cliente.getNumeroAgencia(), cliente.getNumeroConta());
            List<MultiplasOperacoesFinanceiras> aplicacoes = cliente.getAplicacoes();
            if (aplicacoes.isEmpty()) {
                System.out.println("  (sem aplicações cadastradas)");
                continue;
            }
            for (MultiplasOperacoesFinanceiras carteira : aplicacoes) {
                AplicacaoFinanceira app = carteira.getAplicacao();
                System.out.printf("  - %s | número: %s | saldo: %s%n",
                        app.getNomeAplicacao(), app.getNumeroAplicacao(),
                        FormatadorTerminal.formatarMoeda(carteira.getSaldo()));
            }
        }
    }

    private void consultarCliente() {
        String cpf = lerCpfCliente();
        Optional<Cliente> resultado = sistema.buscarCliente(cpf);
        if (resultado.isEmpty()) {
            System.out.println("Cliente não encontrado.");
            return;
        }
        Cliente cliente = resultado.get();
        DADOSPESSOAIS dados = cliente.getDadosPessoais();
        BigDecimal saldoTotal = sistema.getSaldo(cpf);

        System.out.println("Cliente: " + dados.getNome());
        System.out.println("CPF/identificador: " + FormatadorTerminal.mascararCpf(dados.getCpf()));
        System.out.println("Agência/conta: " + cliente.getNumeroAgencia() + "/" + cliente.getNumeroConta());
        System.out.println("Celular: " + dados.getCelular());
        System.out.println("Saldo total: " + FormatadorTerminal.formatarMoeda(saldoTotal));
        AuxiliarCotacoes.exibirEquivalenteCambial(saldoTotal, sistema);
    }

    private void alterarCliente() {
        String cpf = lerCpfCliente();
        Cliente cliente = sistema.requererCliente(cpf);
        String nome = entrada.lerObrigatorio("Novo nome completo: ");
        String celular = entrada.lerObrigatorio("Novo celular (com DDD): ");
        sistema.alterarCliente(cpf, nome, celular);
        System.out.println("Dados do cliente "
                + FormatadorTerminal.mascararCpf(cliente.getDadosPessoais().getCpf())
                + " atualizados com sucesso.");
    }

    private void excluirCliente() {
        String cpf = lerCpfCliente();
        Cliente cliente = sistema.requererCliente(cpf);
        if (!entrada.lerSimNao("Excluir cliente e todas as aplicações vinculadas? (s/n): ")) {
            System.out.println("Exclusão cancelada.");
            return;
        }
        sistema.excluirCliente(cpf);
        System.out.println("Cliente "
                + FormatadorTerminal.mascararCpf(cliente.getDadosPessoais().getCpf())
                + " e seus dados vinculados foram excluídos.");
    }

    // -------------------------------------------------------------------------
    // Aplicações
    // -------------------------------------------------------------------------

    private void cadastrarAplicacao() {
        String cpf = lerCpfCliente();
        String nome = entrada.lerObrigatorio("Nome da aplicação: ");
        String numero = entrada.lerObrigatorio("Número da aplicação: ");
        BigDecimal valorInicial = AuxiliarCotacoes.lerValorEmReal(entrada, sistema, "Valor inicial: ");
        BigDecimal taxaMensal = AuxiliarCotacoes.lerTaxaMensal(entrada, sistema);
        AplicacaoFinanceira aplicacao = new AplicacaoFinanceira(nome, numero, valorInicial, taxaMensal);

        if (entrada.lerSimNao("Usar configuração padrão da aplicação? (s/n): ")) {
            System.out.println("""
                    Configuração padrão:
                    - rendimento diário; sem vencimento e sem permanência mínima;
                    - resgate diário; IOF de 3,5% nos primeiros 29 dias; IR regressivo.
                    """);
        } else {
            TermosInvestimento.configurar(aplicacao, entrada);
        }
        sistema.cadastrarAplicacao(cpf, aplicacao);
        System.out.println("Aplicação cadastrada com sucesso.");
    }

    private void listarAplicacoes() {
        String cpf = lerCpfCliente();
        List<MultiplasOperacoesFinanceiras> aplicacoes = sistema.getAplicacoes(cpf);
        if (aplicacoes.isEmpty()) {
            System.out.println("O cliente não possui aplicações cadastradas.");
            return;
        }
        System.out.println("Aplicações de " + FormatadorTerminal.mascararCpf(cpf) + ":");
        for (MultiplasOperacoesFinanceiras carteira : aplicacoes) {
            AplicacaoFinanceira app = carteira.getAplicacao();
            System.out.printf("- %s | número: %s | saldo: %s | aporte inicial: %s | taxa mensal: %s%n",
                    app.getNomeAplicacao(), app.getNumeroAplicacao(),
                    FormatadorTerminal.formatarMoeda(carteira.getSaldo()),
                    FormatadorTerminal.formatarMoeda(app.getValorAplicado()),
                    app.getTaxaMensal());
        }
    }

    private void alterarAplicacao() {
        String cpf = lerCpfCliente();
        String numero = lerCodigoAplicacao();
        String nome = entrada.lerObrigatorio("Novo nome da aplicação: ");
        BigDecimal taxaMensal = AuxiliarCotacoes.lerTaxaMensal(entrada, sistema);
        sistema.alterarAplicacao(cpf, numero, nome, taxaMensal);
        System.out.println("Aplicação atualizada com sucesso.");
    }

    private void excluirAplicacao() {
        String cpf = lerCpfCliente();
        String numero = lerCodigoAplicacao();
        if (!entrada.lerSimNao("Excluir a aplicação e todas as operações vinculadas? (s/n): ")) {
            System.out.println("Exclusão cancelada.");
            return;
        }
        sistema.excluirAplicacao(cpf, numero);
        System.out.println("Aplicação e operações vinculadas excluídas.");
    }

    // -------------------------------------------------------------------------
    // Operações e Extrato
    // -------------------------------------------------------------------------

    private void lancarOperacao(TipoOperacao tipo) {
        String cpf = lerCpfCliente();
        String numero = lerCodigoAplicacao();
        LocalDate data = entrada.lerData("Data da operação " + FORMATO_DATA);
        BigDecimal valor = AuxiliarCotacoes.lerValorEmReal(entrada, sistema, "Valor: ");
        String descricao = entrada.lerObrigatorio("Descrição: ");

        sistema.cadastrarOperacao(cpf, numero, new OperacaoFinanceira(data, valor, descricao, tipo));
        System.out.println(tipo == TipoOperacao.DEPOSITO
                ? "Depósito lançado com sucesso."
                : "Retirada lançada com sucesso.");
    }

    private void alterarOperacao() {
        String cpf = lerCpfCliente();
        String numero = lerCodigoAplicacao();
        int indice = selecionarOperacao(cpf, numero);
        if (indice < 0) return;

        LocalDate data = entrada.lerData("Data da operação " + FORMATO_DATA);
        BigDecimal valor = AuxiliarCotacoes.lerValorEmReal(entrada, sistema, "Valor: ");
        String descricao = entrada.lerObrigatorio("Descrição: ");
        int tipoOp = entrada.lerInteiro("Tipo (1 = depósito, 2 = retirada): ", 1, 2);
        TipoOperacao tipo = tipoOp == 1 ? TipoOperacao.DEPOSITO : TipoOperacao.RETIRADA;

        sistema.alterarOperacao(cpf, numero, indice, new OperacaoFinanceira(data, valor, descricao, tipo));
        System.out.println("Operação atualizada com sucesso.");
    }

    private void excluirOperacao() {
        String cpf = lerCpfCliente();
        String numero = lerCodigoAplicacao();
        int indice = selecionarOperacao(cpf, numero);
        if (indice < 0) return;

        if (!entrada.lerSimNao("Excluir a operação selecionada e reverter seu efeito no saldo? (s/n): ")) {
            System.out.println("Exclusão cancelada.");
            return;
        }
        sistema.excluirOperacao(cpf, numero, indice);
        System.out.println("Operação excluída e saldo ajustado com sucesso.");
    }

    private int selecionarOperacao(String cpf, String numeroAplicacao) {
        List<OperacaoFinanceira> operacoes =
                sistema.requererCliente(cpf).requererAplicacao(numeroAplicacao).getOperacoes();
        if (operacoes.isEmpty()) {
            System.out.println("A aplicação não possui operações cadastradas.");
            return -1;
        }
        for (int i = 0; i < operacoes.size(); i++) {
            OperacaoFinanceira op = operacoes.get(i);
            System.out.printf("%d. %s | %s | %s | %s%n",
                    i + 1, op.getData(), op.getTipo().getDescricao(),
                    FormatadorTerminal.formatarMoeda(op.getValor()), op.getDescricao());
        }
        return entrada.lerInteiro("Número da operação: ", 1, operacoes.size()) - 1;
    }

    private void exibirExtrato() {
        String cpf = lerCpfCliente();
        String numero = entrada.lerLinha("Código da aplicação (vazio para extrato consolidado do cliente): ").trim();

        LocalDate dataInicial;
        LocalDate dataFinal;
        if (entrada.lerSimNao("Usar os últimos " + DIAS_EXTRATO_PADRAO + " dias? (s/n): ")) {
            dataFinal = LocalDate.now();
            dataInicial = dataFinal.minusDays(DIAS_EXTRATO_PADRAO - 1L);
        } else {
            dataInicial = entrada.lerData("Data inicial " + FORMATO_DATA);
            dataFinal = entrada.lerData("Data final " + FORMATO_DATA);
        }
        if (dataInicial.isAfter(dataFinal)) {
            System.out.println("Período inválido: a data inicial deve ser anterior ou igual à final.");
            return;
        }

        List<OperacaoFinanceira> operacoes = numero.isEmpty()
                ? sistema.getExtrato(cpf, dataInicial, dataFinal)
                : sistema.getExtrato(cpf, numero, dataInicial, dataFinal);
        if (operacoes.isEmpty()) {
            System.out.println("Não há operações no período informado.");
            return;
        }
        System.out.printf("Extrato de %s (%s), de %s a %s:%n",
                FormatadorTerminal.mascararCpf(cpf),
                numero.isEmpty() ? "consolidado" : "aplicação " + numero,
                dataInicial, dataFinal);
        for (OperacaoFinanceira op : operacoes) {
            System.out.printf("- %s | %s | %s | %s%n",
                    op.getData(), op.getTipo().getDescricao(),
                    FormatadorTerminal.formatarMoeda(op.getValor()), op.getDescricao());
        }
    }

    // -------------------------------------------------------------------------
    // Saldos e Atualização
    // -------------------------------------------------------------------------

    private void exibirSaldoCliente() {
        String cpf = lerCpfCliente();
        String numero = entrada.lerLinha("Código da aplicação (vazio para saldo total do cliente): ").trim();
        BigDecimal saldo = numero.isEmpty() ? sistema.getSaldo(cpf) : sistema.getSaldo(cpf, numero);
        System.out.println("Saldo: " + FormatadorTerminal.formatarMoeda(saldo));
        AuxiliarCotacoes.exibirEquivalenteCambial(saldo, sistema);
    }

    private void exibirSaldoSistema() {
        BigDecimal saldo = sistema.getSaldo();
        System.out.println("Saldo total do sistema: " + FormatadorTerminal.formatarMoeda(saldo));
        AuxiliarCotacoes.exibirEquivalenteCambial(saldo, sistema);
    }

    private void atualizarAplicacoes() {
        LocalDate data = entrada.lerData("Data de atualização " + FORMATO_DATA);
        sistema.atualizarAplicacao(data);
        if (!new DiaSemana(data).ehDiaUtil()) {
            System.out.println("A data informada é fim de semana; o serviço não processou a atualização.");
        } else {
            System.out.println("Aplicações atualizadas com sucesso.");
        }
    }

    // -------------------------------------------------------------------------
    // Cotações e Persistência
    // -------------------------------------------------------------------------

    private void consultarCotacoes() {
        String host = entrada.lerLinhaComPadrao("Servidor de cotações [localhost]: ", "localhost");
        int porta = entrada.lerInteiroComPadrao(
                "Porta [" + PORTA_PADRAO_COTACOES + "]: ", 1, PORTA_MAXIMA, PORTA_PADRAO_COTACOES);
        try {
            Map<String, BigDecimal> cotacoes = new ClienteCotacoes(host, porta).obterCotacoes();
            if (cotacoes.isEmpty()) {
                System.out.println("O servidor não disponibilizou cotações.");
                return;
            }
            sistema.atualizarCotacoes(cotacoes);
            AuxiliarCotacoes.exibirCotacoes(cotacoes);
            System.out.println("As taxas SELICRA/CDIR e o câmbio USD/EUR já podem ser usados nas operações.");
        } catch (IOException | RuntimeException e) {
            System.out.println("Falha ao consultar o servidor de cotações: " + FormatadorTerminal.mensagemDe(e));
        }
    }

    private void carregarEstado() {
        Path caminho = entrada.lerCaminho("Caminho do arquivo para carregar: ");
        try {
            Map<String, BigDecimal> cotacoesSessao = sistema.getCotacoes();
            SGI carregado = MigradorSistemaInvestimentos.carregarComMigracao(caminho);
            if (!cotacoesSessao.isEmpty()) {
                carregado.atualizarCotacoes(cotacoesSessao);
            }
            sistema = carregado;
            System.out.println("Estado carregado com sucesso de " + caminho + ".");
        } catch (IOException | RuntimeException e) {
            System.out.println("Falha ao carregar " + caminho + ": " + FormatadorTerminal.mensagemDe(e));
        }
    }

    private void salvarEstado() {
        salvarEm(entrada.lerCaminho("Caminho do arquivo para salvar: "));
    }

    private void salvarEm(Path caminho) {
        try {
            Path pastaPai = caminho.toAbsolutePath().getParent();
            if (pastaPai != null) {
                Files.createDirectories(pastaPai);
            }
            sistema.arquivoSalvarDados(caminho);
            if (ehArquivoPadrao(caminho)) {
                salvarAutomaticamenteAoSair = true;
            }
            System.out.println("Estado salvo com sucesso em " + caminho + ".");
        } catch (IOException | RuntimeException e) {
            System.out.println("Falha ao salvar " + caminho + ": " + FormatadorTerminal.mensagemDe(e));
        }
    }

    private String lerCpfCliente() {
        return entrada.lerObrigatorio("CPF/identificador do cliente: ");
    }

    private String lerCodigoAplicacao() {
        return entrada.lerObrigatorio("Código da aplicação: ");
    }

    private static boolean ehArquivoPadrao(Path caminho) {
        return caminho.toAbsolutePath().normalize().equals(ARQUIVO_PADRAO.toAbsolutePath().normalize());
    }

    private record EstadoInicial(SGI sistema, boolean salvarAutomaticamenteAoSair) { }
}

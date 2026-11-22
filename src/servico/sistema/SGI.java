package servico.sistema;

import dominio.aplicacao.AplicacaoFinanceira;
import dominio.cliente.Cliente;
import dominio.cliente.DADOSPESSOAIS;
import dominio.util.Moeda;
import dominio.operacao.MultiplasOperacoesFinanceiras;
import dominio.operacao.OperacaoFinanceira;
import dominio.abstracoes.CarteiraFinanceira;
import dominio.util.ValidacaoDominio;
import servico.util.CotacoesUtil;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Item 7 — Fachada principal do Sistema de Gestão de Investimentos (SGI).
 *
 * <p>Gerencia até 100 clientes ({@link Cliente}), coordena atualizações de rendimento
 * restritas a dias úteis (via {@link dominio.calendario.DiaSemana}), oferece saldos e extratos em 3 níveis
 * (aplicação, cliente e sistema), mantém cotações de mercado e persiste o estado via
 * {@link ArmazenadorArquivo}. Todos os métodos de instância são sincronizados pelo monitor do objeto
 * ({@code synchronized}) para proteger {@code clientes} e {@code ultimasCotacoes} contra acesso concorrente.
 * Passível de herança.</p>
 */
public class SGI extends CarteiraFinanceira {

    public static final int LIMITE_CLIENTES = 100;
    public static final Path CAMINHO_PADRAO_DADOS = Path.of("data", "sgi.db");

    private final Map<String, Cliente> clientes = new LinkedHashMap<>();
    private final Map<String, BigDecimal> ultimasCotacoes = new LinkedHashMap<>();

    /** Construtor seguro sem despacho de métodos virtuais. */
    public SGI() {
    }

    public static SGI criarNovo() {
        return new SGI();
    }

    // -------------------------------------------------------------------------
    // Cadastro e gestão de clientes (máx. 100)
    // -------------------------------------------------------------------------

    /** Alias de cadastro conforme especificação do Item 7. */
    public synchronized void setCadastro(Cliente cliente) {
        cadastrarCliente(cliente);
    }

    public synchronized void setCadastro(DADOSPESSOAIS dados, int agencia, long conta) {
        cadastrarCliente(dados, agencia, conta);
    }

    public synchronized void setCadastro(String nome, String cpf, String celular, int agencia, long conta) {
        cadastrarCliente(nome, cpf, celular, agencia, conta);
    }

    /** Cadastra um cliente validando CPF, limite global (100) e unicidade dos números de aplicação. */
    public synchronized void cadastrarCliente(Cliente cliente) {
        String cpf = extrairCpfValidado(cliente);
        if (clientes.containsKey(cpf)) {
            throw new IllegalArgumentException("Cliente já cadastrado para o CPF informado: " + cpf);
        }
        if (clientes.size() >= LIMITE_CLIENTES) {
            throw new IllegalStateException("Limite de 100 clientes atingido.");
        }
        for (MultiplasOperacoesFinanceiras item : cliente.getAplicacoes()) {
            String num = item.getNumeroAplicacao();
            if (num != null && buscarAplicacao(num).isPresent()) {
                throw new IllegalArgumentException("Número de aplicação já cadastrado no sistema: " + num);
            }
        }
        clientes.put(cpf, cliente);
    }

    public synchronized void cadastrarCliente(DADOSPESSOAIS dados, int agencia, long conta) {
        Objects.requireNonNull(dados, "Dados pessoais são obrigatórios.");
        cadastrarCliente(new Cliente(dados, agencia, conta));
    }

    public synchronized void cadastrarCliente(String nome, String cpf, String celular, int agencia, long conta) {
        Objects.requireNonNull(nome, "Nome do cliente é obrigatório.");
        Objects.requireNonNull(cpf, "CPF do cliente é obrigatório.");
        Objects.requireNonNull(celular, "Celular do cliente é obrigatório.");
        cadastrarCliente(new Cliente(nome, cpf, celular, agencia, conta));
    }

    public synchronized Optional<Cliente> buscarCliente(String cpf) {
        if (cpf == null || cpf.isBlank()) {
            return Optional.empty();
        }
        String digitos = ValidacaoDominio.apenasDigitos(cpf);
        return digitos.length() == 11 ? Optional.ofNullable(clientes.get(digitos)) : Optional.empty();
    }

    public synchronized Cliente requererCliente(String cpf) {
        Objects.requireNonNull(cpf, "CPF do cliente é obrigatório.");
        return buscarCliente(cpf).orElseThrow(() ->
                new IllegalArgumentException("Cliente não encontrado para o CPF informado: " + cpf));
    }

    public synchronized int getQuantidadeClientes() {
        return clientes.size();
    }

    public synchronized List<Cliente> getClientes() {
        return Collections.unmodifiableList(new ArrayList<>(clientes.values()));
    }

    /** Substitui atomicamente todos os clientes cadastrados validando limites e unicidade global. */
    public synchronized void setClientes(List<Cliente> novosClientes) {
        Objects.requireNonNull(novosClientes, "Lista de clientes é obrigatória.");
        if (novosClientes.size() > LIMITE_CLIENTES) {
            throw new IllegalStateException("Limite de 100 clientes atingido.");
        }
        Map<String, Cliente> validados = new LinkedHashMap<>();
        Set<String> numerosAplicacaoVistos = new HashSet<>();

        for (Cliente cliente : novosClientes) {
            String cpf = extrairCpfValidado(cliente);
            if (validados.containsKey(cpf)) {
                throw new IllegalArgumentException("Cliente duplicado para o CPF informado: " + cpf);
            }
            for (MultiplasOperacoesFinanceiras item : cliente.getAplicacoes()) {
                String num = item.getNumeroAplicacao();
                if (num != null && !numerosAplicacaoVistos.add(num)) {
                    throw new IllegalArgumentException("Número de aplicação duplicado no sistema: " + num);
                }
            }
            validados.put(cpf, cliente);
        }

        clientes.clear();
        clientes.putAll(validados);
    }

    private static String extrairCpfValidado(Cliente cliente) {
        Objects.requireNonNull(cliente, "Cliente não pode ser nulo.");
        Objects.requireNonNull(cliente.getDadosPessoais(), "Dados pessoais do cliente são obrigatórios.");
        return ValidacaoDominio.normalizarCpf(cliente.getDadosPessoais().getCpf());
    }

    public synchronized void alterarCliente(String cpf, String nome, String celular) {
        requererCliente(cpf).getDadosPessoais().alterarDados(nome, celular);
    }

    public synchronized void excluirCliente(String cpf) {
        String chave = ValidacaoDominio.normalizarCpf(cpf);
        if (clientes.remove(chave) == null) {
            throw new IllegalArgumentException("Cliente não encontrado para o CPF informado.");
        }
    }

    // -------------------------------------------------------------------------
    // Gestão de aplicações e operações financeiras
    // -------------------------------------------------------------------------

    public synchronized void cadastrarAplicacao(String cpf, MultiplasOperacoesFinanceiras aplicacao) {
        Objects.requireNonNull(cpf, "CPF do titular é obrigatório.");
        Objects.requireNonNull(aplicacao, "Aplicação financeira é obrigatória.");
        Cliente titular = requererCliente(cpf);

        String numero = aplicacao.getNumeroAplicacao();
        if (numero != null && buscarAplicacao(numero).isPresent()) {
            throw new IllegalArgumentException("Número de aplicação já cadastrado no sistema: " + numero);
        }
        titular.adicionarAplicacao(aplicacao);
    }

    public synchronized void cadastrarAplicacao(String cpf, AplicacaoFinanceira aplicacaoBase) {
        Objects.requireNonNull(cpf, "CPF do titular é obrigatório.");
        Objects.requireNonNull(aplicacaoBase, "Aplicação financeira é obrigatória.");
        cadastrarAplicacao(cpf, new MultiplasOperacoesFinanceiras(aplicacaoBase));
    }

    public synchronized Optional<MultiplasOperacoesFinanceiras> buscarAplicacao(String numeroAplicacao) {
        if (numeroAplicacao == null || numeroAplicacao.isBlank()) {
            return Optional.empty();
        }
        for (Cliente cliente : clientes.values()) {
            Optional<MultiplasOperacoesFinanceiras> encontrada = cliente.buscarAplicacao(numeroAplicacao);
            if (encontrada.isPresent()) {
                return encontrada;
            }
        }
        return Optional.empty();
    }

    public synchronized List<MultiplasOperacoesFinanceiras> getAplicacoes(String cpf) {
        return requererCliente(cpf).getAplicacoes();
    }

    public synchronized void alterarAplicacao(String cpf, String numeroAplicacao,
                                              String nome, BigDecimal taxaMensal) {
        if (nome == null || nome.isBlank()) {
            throw new IllegalArgumentException("Nome da aplicação é obrigatório.");
        }
        Objects.requireNonNull(taxaMensal, "Taxa mensal é obrigatória.");
        if (taxaMensal.signum() < 0 || taxaMensal.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("A taxa mensal deve estar entre zero e um.");
        }
        MultiplasOperacoesFinanceiras carteira = requererCliente(cpf).requererAplicacao(numeroAplicacao);
        carteira.getAplicacao().setNomeAplicacao(nome);
        carteira.getAplicacao().setTaxaMensal(taxaMensal);
    }

    public synchronized void excluirAplicacao(String cpf, String numeroAplicacao) {
        requererCliente(cpf).removerAplicacao(numeroAplicacao);
    }

    public synchronized void cadastrarOperacao(String cpf, String numeroAplicacao, OperacaoFinanceira operacao) {
        Objects.requireNonNull(cpf, "CPF do cliente é obrigatório.");
        Objects.requireNonNull(numeroAplicacao, "Número da aplicação é obrigatório.");
        Objects.requireNonNull(operacao, "Operação financeira é obrigatória.");
        requererCliente(cpf).requererAplicacao(numeroAplicacao).adicionarOperacao(operacao);
    }

    public synchronized void alterarOperacao(String cpf, String numeroAplicacao,
                                             int indice, OperacaoFinanceira novaOperacao) {
        Objects.requireNonNull(novaOperacao, "Nova operação é obrigatória.");
        requererCliente(cpf).requererAplicacao(numeroAplicacao).alterarOperacao(indice, novaOperacao);
    }

    public synchronized void excluirOperacao(String cpf, String numeroAplicacao, int indice) {
        requererCliente(cpf).requererAplicacao(numeroAplicacao).alterarOperacao(indice, null);
    }

    // -------------------------------------------------------------------------
    // Atualização em lote restrita a dias úteis (segunda a sexta via DiaSemana)
    // -------------------------------------------------------------------------

    /** Atualiza todas as aplicações cadastradas na data atual (nome do enunciado). */
    @Override
    public synchronized void atualizarAplicacao() {
        atualizarAplicacao(LocalDate.now());
    }

    /** Atualiza todas as aplicações na data informada; fins de semana são ignorados. */
    @Override
    public synchronized void atualizarAplicacao(LocalDate data) {
        Objects.requireNonNull(data, "Data de atualização é obrigatória.");
        if (!ehDiaUtil(data)) {
            return;
        }
        for (Cliente cliente : clientes.values()) {
            cliente.atualizarAplicacao(data);
        }
    }

    public synchronized void atualizarAplicacoes() {
        atualizarAplicacao();
    }

    public synchronized void atualizarAplicacoes(LocalDate data) {
        atualizarAplicacao(data);
    }

    // -------------------------------------------------------------------------
    // Saldos (individual, por cliente e global do sistema)
    // -------------------------------------------------------------------------

    public synchronized BigDecimal getSaldo(String cpf, String numeroAplicacao) {
        Objects.requireNonNull(cpf, "CPF do cliente é obrigatório.");
        Objects.requireNonNull(numeroAplicacao, "Número da aplicação é obrigatório.");
        return requererCliente(cpf).getSaldo(numeroAplicacao);
    }

    public synchronized BigDecimal getSaldo(String cpf) {
        Objects.requireNonNull(cpf, "CPF do cliente é obrigatório.");
        return requererCliente(cpf).getSaldo();
    }

    @Override
    public synchronized BigDecimal getSaldo() {
        BigDecimal total = BigDecimal.ZERO;
        for (Cliente cliente : clientes.values()) {
            BigDecimal saldoCliente = cliente.getSaldo();
            if (saldoCliente != null) {
                total = total.add(saldoCliente);
            }
        }
        return Moeda.arredondar(total);
    }

    public synchronized BigDecimal getSaldoBruto(String cpf, String numeroAplicacao) {
        Objects.requireNonNull(cpf, "CPF do cliente é obrigatório.");
        Objects.requireNonNull(numeroAplicacao, "Número da aplicação é obrigatório.");
        return requererCliente(cpf).requererAplicacao(numeroAplicacao).getSaldoBruto();
    }

    public synchronized BigDecimal getSaldoBruto(String cpf) {
        Objects.requireNonNull(cpf, "CPF do cliente é obrigatório.");
        return somarSaldosBrutos(requererCliente(cpf).getAplicacoes());
    }

    public synchronized BigDecimal getSaldoBruto() {
        BigDecimal total = BigDecimal.ZERO;
        for (Cliente cliente : clientes.values()) {
            total = total.add(somarSaldosBrutos(cliente.getAplicacoes()));
        }
        return Moeda.arredondar(total);
    }

    private static BigDecimal somarSaldosBrutos(List<MultiplasOperacoesFinanceiras> aplicacoes) {
        BigDecimal total = BigDecimal.ZERO;
        for (MultiplasOperacoesFinanceiras item : aplicacoes) {
            if (item.getSaldoBruto() != null) {
                total = total.add(item.getSaldoBruto());
            }
        }
        return Moeda.arredondar(total);
    }

    // -------------------------------------------------------------------------
    // Extratos polimórficos (aplicação, cliente e global — 30 dias ou intervalo)
    // -------------------------------------------------------------------------

    public synchronized List<OperacaoFinanceira> getExtrato(String cpf, String numeroAplicacao) {
        Objects.requireNonNull(cpf, "CPF do cliente é obrigatório.");
        Objects.requireNonNull(numeroAplicacao, "Número da aplicação é obrigatório.");
        return requererCliente(cpf).getExtrato(numeroAplicacao);
    }

    public synchronized List<OperacaoFinanceira> getExtrato(String cpf, String numeroAplicacao,
                                                            LocalDate inicio, LocalDate fim) {
        Objects.requireNonNull(cpf, "CPF do cliente é obrigatório.");
        Objects.requireNonNull(numeroAplicacao, "Número da aplicação é obrigatório.");
        Objects.requireNonNull(inicio, "Data inicial é obrigatória.");
        Objects.requireNonNull(fim, "Data final é obrigatória.");
        return requererCliente(cpf).getExtrato(numeroAplicacao, inicio, fim);
    }

    public synchronized List<OperacaoFinanceira> getExtrato(String cpf, String numeroAplicacao,
                                                            int diaIni, int mesIni, int anoIni,
                                                            int diaFim, int mesFim, int anoFim) {
        return getExtrato(cpf, numeroAplicacao,
                LocalDate.of(anoIni, mesIni, diaIni), LocalDate.of(anoFim, mesFim, diaFim));
    }

    public synchronized List<OperacaoFinanceira> getExtrato(String cpf) {
        Objects.requireNonNull(cpf, "CPF do cliente é obrigatório.");
        return requererCliente(cpf).getExtrato();
    }

    public synchronized List<OperacaoFinanceira> getExtrato(String cpf, LocalDate inicio, LocalDate fim) {
        Objects.requireNonNull(cpf, "CPF do cliente é obrigatório.");
        Objects.requireNonNull(inicio, "Data inicial é obrigatória.");
        Objects.requireNonNull(fim, "Data final é obrigatória.");
        return requererCliente(cpf).getExtrato(inicio, fim);
    }

    public synchronized List<OperacaoFinanceira> getExtrato(String cpf,
                                                            int diaIni, int mesIni, int anoIni,
                                                            int diaFim, int mesFim, int anoFim) {
        return getExtrato(cpf, LocalDate.of(anoIni, mesIni, diaIni), LocalDate.of(anoFim, mesFim, diaFim));
    }

    @Override
    public synchronized List<OperacaoFinanceira> getExtrato() {
        List<OperacaoFinanceira> unificado = new ArrayList<>();
        for (Cliente cliente : clientes.values()) {
            unificado.addAll(cliente.getExtrato());
        }
        return ordenarExtrato(unificado);
    }

    @Override
    public synchronized List<OperacaoFinanceira> getExtrato(LocalDate inicio, LocalDate fim) {
        Objects.requireNonNull(inicio, "Data inicial é obrigatória.");
        Objects.requireNonNull(fim, "Data final é obrigatória.");
        if (fim.isBefore(inicio)) {
            throw new IllegalArgumentException("A data final do período não pode anteceder a data inicial.");
        }
        List<OperacaoFinanceira> unificado = new ArrayList<>();
        for (Cliente cliente : clientes.values()) {
            unificado.addAll(cliente.getExtrato(inicio, fim));
        }
        return ordenarExtrato(unificado);
    }

    public synchronized List<OperacaoFinanceira> getExtrato(int diaIni, int mesIni, int anoIni,
                                                            int diaFim, int mesFim, int anoFim) {
        return getExtrato(LocalDate.of(anoIni, mesIni, diaIni), LocalDate.of(anoFim, mesFim, diaFim));
    }

    // -------------------------------------------------------------------------
    // Persistência em arquivo (Item 7)
    // -------------------------------------------------------------------------

    @Override
    public synchronized void arquivoSalvarDados(Path caminho) throws IOException {
        Objects.requireNonNull(caminho, "Caminho do arquivo é obrigatório.");
        ArmazenadorArquivo.salvarDados(caminho, this.clientes.values());
    }

    @Override
    public synchronized void arquivoSalvarDados(String caminho) throws IOException {
        Objects.requireNonNull(caminho, "Caminho do arquivo é obrigatório.");
        arquivoSalvarDados(Path.of(caminho));
    }

    public synchronized void arquivoSalvarDados() throws IOException {
        arquivoSalvarDados(CAMINHO_PADRAO_DADOS);
    }

    public static SGI arquivoLerDados(Path caminho) throws IOException {
        Objects.requireNonNull(caminho, "Caminho do arquivo é obrigatório.");
        return ArmazenadorArquivo.carregarDados(caminho);
    }

    public static SGI arquivoLerDados(String caminho) throws IOException {
        Objects.requireNonNull(caminho, "Caminho do arquivo é obrigatório.");
        return arquivoLerDados(Path.of(caminho));
    }

    public static SGI arquivoLerDados() throws IOException {
        return arquivoLerDados(CAMINHO_PADRAO_DADOS);
    }

    public synchronized void carregarDados(Path caminho) throws IOException {
        Objects.requireNonNull(caminho, "Caminho do arquivo é obrigatório.");
        SGI restaurado = arquivoLerDados(caminho);
        this.clientes.clear();
        for (Cliente cliente : restaurado.getClientes()) {
            this.clientes.put(ValidacaoDominio.normalizarCpf(cliente.getDadosPessoais().getCpf()), cliente);
        }
        this.ultimasCotacoes.clear();
        this.ultimasCotacoes.putAll(restaurado.getCotacoes());
    }

    public synchronized void carregarDados(String caminho) throws IOException {
        Objects.requireNonNull(caminho, "Caminho do arquivo é obrigatório.");
        carregarDados(Path.of(caminho));
    }

    public synchronized void carregarDados() throws IOException {
        carregarDados(CAMINHO_PADRAO_DADOS);
    }

    // -------------------------------------------------------------------------
    // Cotações de mercado (Item 9 — via servico.util.CotacoesUtil)
    // -------------------------------------------------------------------------

    public synchronized void atualizarCotacoes(Map<String, BigDecimal> cotacoes) {
        CotacoesUtil.registrar(this.ultimasCotacoes, cotacoes);
    }

    /** Substitui atomicamente o conjunto de cotações ativas do sistema. */
    public synchronized void setCotacoes(Map<String, BigDecimal> cotacoes) {
        Objects.requireNonNull(cotacoes, "Mapa de cotações é obrigatório.");
        Map<String, BigDecimal> novas = new LinkedHashMap<>();
        CotacoesUtil.registrar(novas, cotacoes);
        this.ultimasCotacoes.clear();
        this.ultimasCotacoes.putAll(novas);
    }

    public synchronized Map<String, BigDecimal> getCotacoes() {
        return CotacoesUtil.copiaImutavel(this.ultimasCotacoes);
    }

    public synchronized Optional<BigDecimal> getCotacao(String codigoOuNome) {
        return CotacoesUtil.consultar(this.ultimasCotacoes, codigoOuNome);
    }
}

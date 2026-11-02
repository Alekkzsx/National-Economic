package servicos;

import modelo.ContaCliente;
import modelo.OperacaoFinanceira;
import modelo.AplicacaoFinanceira;
import modelo.TipoOperacao;
import modelo.DadosPessoais;

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
public class SistemaInvestimentos {
    /** Limite de clientes definido para o sistema. */
    private static final int LIMITE_CLIENTES = 100;

    /** Clientes indexados pelo CPF normalizado, preservando a ordem de cadastro. */
    private final Map<String, ContaCliente> clientes = new LinkedHashMap<>();
    /** Operações indexadas pelo identificador único da aplicação. */
    private final Map<String, List<OperacaoFinanceira>> extratos = new LinkedHashMap<>();

    /** Cadastra um cliente validando CPF, duplicidade e limite do sistema. */
    public synchronized void cadastrarCliente(DadosPessoais dadosPessoais, int numeroAgencia, long numeroConta) {
        Objects.requireNonNull(dadosPessoais, "Dados pessoais obrigatórios.");
        String cpf = cpfCanonico(dadosPessoais.getCpf());
        if (clientes.containsKey(cpf)) throw new IllegalArgumentException("CPF já cadastrado.");
        if (clientes.size() >= LIMITE_CLIENTES) throw new IllegalStateException("Limite de 100 clientes atingido.");
        clientes.put(cpf, new ContaCliente(dadosPessoais, numeroAgencia, numeroConta));
    }

    /**
     * Atualiza nome e celular sem alterar o CPF que identifica a conta.
     *
     * @param cpf CPF do cliente
     * @param nome novo nome completo
     * @param celular novo celular com DDD
     */
    public synchronized void alterarDadosCliente(String cpf, String nome, String celular) {
        exigirCliente(cpf).getDadosPessoais().alterarDados(nome, celular);
    }

    /**
     * Exclui um cliente e todas as aplicações e operações vinculadas.
     *
     * @param cpf CPF do cliente a excluir
     */
    public synchronized void excluirCliente(String cpf) {
        ContaCliente cliente = exigirCliente(cpf);
        for (AplicacaoFinanceira aplicacao : cliente.listarAplicacoes()) {
            extratos.remove(aplicacao.getNumero());
        }
        clientes.remove(cpfCanonico(cpf));
    }

    /** Associa uma aplicação a um cliente e cria seu extrato vazio. */
    public synchronized void cadastrarAplicacao(String cpf, AplicacaoFinanceira aplicacao) {
        Objects.requireNonNull(aplicacao, "Investimento obrigatório.");
        ContaCliente cliente = exigirCliente(cpf);
        if (buscarAplicacaoPorNumero(aplicacao.getNumero()).isPresent()) {
            throw new IllegalArgumentException("Número de investimento já cadastrado no sistema.");
        }
        cliente.adicionarAplicacao(aplicacao);
        extratos.put(aplicacao.getNumero(), new ArrayList<>());
    }

    /**
     * Atualiza os campos descritivos de uma aplicação sem alterar seu histórico financeiro.
     *
     * @param cpf CPF do titular
     * @param numeroAplicacao identificador imutável da aplicação
     * @param nome novo nome legível
     * @param taxaMensal nova taxa mensal fracionária
     */
    public synchronized void alterarAplicacao(
            String cpf,
            String numeroAplicacao,
            String nome,
            BigDecimal taxaMensal
    ) {
        AplicacaoFinanceira aplicacao = exigirCliente(cpf).exigirAplicacao(numeroAplicacao);
        if (nome == null || nome.isBlank()) {
            throw new IllegalArgumentException("Nome da aplicação obrigatório.");
        }
        if (taxaMensal == null || taxaMensal.signum() < 0 || taxaMensal.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("A taxa mensal deve estar entre zero e um.");
        }
        aplicacao.setNome(nome);
        aplicacao.setTaxaMensal(taxaMensal);
    }

    /**
     * Exclui uma aplicação e seus lançamentos associados.
     *
     * @param cpf CPF do titular
     * @param numeroAplicacao identificador da aplicação
     */
    public synchronized void excluirAplicacao(String cpf, String numeroAplicacao) {
        ContaCliente cliente = exigirCliente(cpf);
        AplicacaoFinanceira aplicacao = cliente.removerAplicacao(numeroAplicacao);
        extratos.remove(aplicacao.getNumero());
    }

    /** Valida e aplica uma operação à aplicação, registrando-a no extrato. */
    public synchronized void registrarOperacao(String cpf, String numeroAplicacao, OperacaoFinanceira operacao) {
        Objects.requireNonNull(operacao, "Operação obrigatória.");
        AplicacaoFinanceira aplicacao = exigirCliente(cpf).exigirAplicacao(numeroAplicacao);
        aplicacao.aplicar(operacao);
        extratos.computeIfAbsent(numeroAplicacao, ignorado -> new ArrayList<>()).add(operacao);
    }

    /**
     * Retorna uma cópia da sequência de operações de uma aplicação.
     *
     * @param cpf CPF do titular
     * @param numeroAplicacao identificador da aplicação
     * @return lançamentos na ordem em que foram registrados
     */
    public synchronized List<OperacaoFinanceira> listarOperacoes(String cpf, String numeroAplicacao) {
        AplicacaoFinanceira aplicacao = exigirCliente(cpf).exigirAplicacao(numeroAplicacao);
        return List.copyOf(extratos.getOrDefault(aplicacao.getNumero(), List.of()));
    }

    /**
     * Substitui uma operação e ajusta o saldo de forma atômica.
     *
     * @param cpf CPF do titular
     * @param numeroAplicacao identificador da aplicação
     * @param indice índice baseado em zero na ordem de cadastro
     * @param novaOperacao novo lançamento validado
     */
    public synchronized void alterarOperacao(
            String cpf,
            String numeroAplicacao,
            int indice,
            OperacaoFinanceira novaOperacao
    ) {
        Objects.requireNonNull(novaOperacao, "Nova operação obrigatória.");
        AplicacaoFinanceira aplicacao = exigirCliente(cpf).exigirAplicacao(numeroAplicacao);
        List<OperacaoFinanceira> operacoes = extratos.get(aplicacao.getNumero());
        exigirIndiceOperacao(operacoes, indice);
        exigirOperacaoSemRendimentosAcumulados(aplicacao);

        OperacaoFinanceira anterior = operacoes.get(indice);
        aplicacao.reverter(anterior);
        try {
            aplicacao.aplicar(novaOperacao);
        } catch (RuntimeException excecao) {
            aplicacao.aplicar(anterior);
            throw excecao;
        }
        operacoes.set(indice, novaOperacao);
    }

    /**
     * Exclui uma operação e reverte seu efeito no saldo.
     *
     * @param cpf CPF do titular
     * @param numeroAplicacao identificador da aplicação
     * @param indice índice baseado em zero na ordem de cadastro
     */
    public synchronized void excluirOperacao(String cpf, String numeroAplicacao, int indice) {
        AplicacaoFinanceira aplicacao = exigirCliente(cpf).exigirAplicacao(numeroAplicacao);
        List<OperacaoFinanceira> operacoes = extratos.get(aplicacao.getNumero());
        exigirIndiceOperacao(operacoes, indice);
        exigirOperacaoSemRendimentosAcumulados(aplicacao);
        aplicacao.reverter(operacoes.get(indice));
        operacoes.remove(indice);
    }

    private static void exigirOperacaoSemRendimentosAcumulados(AplicacaoFinanceira aplicacao) {
        if (aplicacao.getDiasEfetivos() > 0
                || aplicacao.getRendimentoCreditado().signum() > 0
                || aplicacao.getRendimentosPendentes().signum() > 0) {
            throw new IllegalStateException(
                    "Não é possível alterar operações após a apuração de rendimentos; isso preserva a integridade do saldo.");
        }
    }

    private static void exigirIndiceOperacao(List<OperacaoFinanceira> operacoes, int indice) {
        if (operacoes == null || indice < 0 || indice >= operacoes.size()) {
            throw new IllegalArgumentException("Número de operação inexistente.");
        }
    }

    /** Procura um cliente pelo CPF e retorna vazio se não existir. */
    public synchronized Optional<ContaCliente> buscarCliente(String cpf) {
        return Optional.ofNullable(clientes.get(cpfCanonico(cpf)));
    }

    /** Retorna uma cópia não modificável da lista de clientes. */
    public synchronized List<ContaCliente> listarClientes() {
        return List.copyOf(clientes.values());
    }

    /** Retorna as aplicações do cliente; CPF inválido ou inexistente gera erro. */
    public synchronized List<AplicacaoFinanceira> listarAplicacoes(String cpf) {
        return exigirCliente(cpf).listarAplicacoes();
    }

    /**
     * Retorna o extrato inclusivo entre as duas datas, em ordem cronológica.
     * O período deve ser válido e a aplicação deve pertencer ao cliente.
     */
    public synchronized List<OperacaoFinanceira> obterExtrato(String cpf, String numeroAplicacao,
                                                                 LocalDate dataInicio, LocalDate dataFim) {
        Objects.requireNonNull(dataInicio, "Data inicial obrigatória.");
        Objects.requireNonNull(dataFim, "Data final obrigatória.");
        if (dataFim.isBefore(dataInicio)) throw new IllegalArgumentException("O fim do período antecede o início.");
        AplicacaoFinanceira aplicacao = exigirCliente(cpf).exigirAplicacao(numeroAplicacao);
        return extratos.getOrDefault(aplicacao.getNumero(), List.of()).stream()
                .filter(op -> !op.data().isBefore(dataInicio) && !op.data().isAfter(dataFim))
                .sorted(Comparator.comparing(OperacaoFinanceira::data))
                .toList();
    }

    /** Retorna o saldo líquido de uma aplicação específica. */
    public synchronized BigDecimal getSaldo(String cpf, String numeroAplicacao) {
        return exigirCliente(cpf).exigirAplicacao(numeroAplicacao).getSaldoLiquido();
    }

    /** Soma os saldos líquidos das aplicações pertencentes ao cliente. */
    public synchronized BigDecimal getSaldoCliente(String cpf) {
        return exigirCliente(cpf).listarAplicacoes().stream()
                .map(AplicacaoFinanceira::getSaldoLiquido).reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
    }

    /** Soma os saldos líquidos de todas as aplicações cadastradas. */
    public synchronized BigDecimal getSaldoSistema() {
        return clientes.values().stream().flatMap(cliente -> cliente.listarAplicacoes().stream())
                .map(AplicacaoFinanceira::getSaldoLiquido).reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
    }

    /**
     * Atualiza todas as aplicações em uma data útil. Fins de semana são
     * ignorados, conforme a regra da atividade.
     */
    public synchronized void atualizarTodos(LocalDate data) {
        Objects.requireNonNull(data, "Data obrigatória.");
        if (data.getDayOfWeek().getValue() >= 6) return;
        for (ContaCliente cliente : clientes.values()) {
            for (AplicacaoFinanceira aplicacao : cliente.listarAplicacoes()) aplicacao.atualizar(data);
        }
    }

    /** Delega a gravação do estado completo ao componente de persistência TSV. */
    public synchronized void salvar(Path caminho) throws IOException {
        Objects.requireNonNull(caminho, "Caminho obrigatório.");
        ArmazenadorInvestimentos.salvar(caminho, clientes.values(), extratos);
    }

    /**
     * Reconstitui o sistema a partir dos formatos v1, v2, v3 ou v4.
     *
     * @throws IOException se o arquivo não existir, estiver inconsistente ou
     *                     usar uma versão desconhecida
     */
    public static SistemaInvestimentos carregar(Path caminho) throws IOException {
        Objects.requireNonNull(caminho, "Caminho obrigatório.");
        return ArmazenadorInvestimentos.carregar(caminho);
    }

    /** Anexa operação já persistida sem reaplicar seus efeitos ao saldo restaurado. */
    synchronized void anexarOperacaoRestaurada(
            String cpf,
            String numeroAplicacao,
            OperacaoFinanceira operacao
    ) {
        ContaCliente cliente = exigirCliente(cpf);
        AplicacaoFinanceira aplicacao = cliente.exigirAplicacao(numeroAplicacao);
        List<OperacaoFinanceira> operacoes = extratos.get(aplicacao.getNumero());
        if (operacoes == null) {
            throw new IllegalArgumentException("Extrato da aplicação não foi inicializado.");
        }
        if (operacao.data().isBefore(aplicacao.getDataAplicacao())) {
            throw new IllegalArgumentException("Operação anterior à aplicação.");
        }
        if (operacoes.size() >= 100) {
            throw new IllegalArgumentException("Mais de 100 operações persistidas.");
        }
        operacoes.add(operacao);
    }

    /** Normaliza o CPF e exige que o cliente esteja cadastrado. */
    private synchronized ContaCliente exigirCliente(String cpf) {
        ContaCliente cliente = clientes.get(cpfCanonico(cpf));
        if (cliente == null) throw new IllegalArgumentException("Cliente não encontrado.");
        return cliente;
    }

    /** Busca um número de aplicação globalmente para impedir duplicidades. */
    private Optional<AplicacaoFinanceira> buscarAplicacaoPorNumero(String numero) {
        return clientes.values().stream().flatMap(c -> c.listarAplicacoes().stream())
                .filter(i -> i.getNumero().equals(numero)).findFirst();
    }

    /** Remove pontuação do CPF para usar uma chave estável nos mapas internos. */
    private static String cpfCanonico(String cpf) {
        if (cpf == null) throw new IllegalArgumentException("CPF obrigatório.");
        String digits = cpf.replaceAll("\\D", "");
        if (digits.length() != 11) throw new IllegalArgumentException("CPF deve conter 11 dígitos.");
        return digits;
    }

}

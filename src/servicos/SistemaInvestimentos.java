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
    private static final int MAX_CLIENTS = 100;

    /** Clientes indexados pelo CPF normalizado, preservando a ordem de cadastro. */
    private final Map<String, ContaCliente> clients = new LinkedHashMap<>();
    /** Operações indexadas pelo identificador único da aplicação. */
    private final Map<String, List<OperacaoFinanceira>> statements = new LinkedHashMap<>();

    /** Cadastra um cliente validando CPF, duplicidade e limite do sistema. */
    public synchronized void cadastrarCliente(DadosPessoais personalData, int agencyNumber, long accountNumber) {
        Objects.requireNonNull(personalData, "Dados pessoais obrigatórios.");
        String cpf = cpfCanonico(personalData.getCpf());
        if (clients.containsKey(cpf)) throw new IllegalArgumentException("CPF já cadastrado.");
        if (clients.size() >= MAX_CLIENTS) throw new IllegalStateException("Limite de 100 clientes atingido.");
        clients.put(cpf, new ContaCliente(personalData, agencyNumber, accountNumber));
    }

    /** Associa uma aplicação a um cliente e cria seu extrato vazio. */
    public synchronized void cadastrarAplicacao(String cpf, AplicacaoFinanceira investment) {
        Objects.requireNonNull(investment, "Investimento obrigatório.");
        ContaCliente client = exigirCliente(cpf);
        if (buscarAplicacaoPorNumero(investment.getNumero()).isPresent()) {
            throw new IllegalArgumentException("Número de investimento já cadastrado no sistema.");
        }
        client.adicionarAplicacao(investment);
        statements.put(investment.getNumero(), new ArrayList<>());
    }

    /** Valida e aplica uma operação à aplicação, registrando-a no extrato. */
    public synchronized void registrarOperacao(String cpf, String investmentNumber, OperacaoFinanceira operation) {
        Objects.requireNonNull(operation, "Operação obrigatória.");
        AplicacaoFinanceira investment = exigirCliente(cpf).exigirAplicacao(investmentNumber);
        investment.aplicar(operation);
        statements.computeIfAbsent(investmentNumber, ignored -> new ArrayList<>()).add(operation);
    }

    /** Procura um cliente pelo CPF e retorna vazio se não existir. */
    public synchronized Optional<ContaCliente> buscarCliente(String cpf) {
        return Optional.ofNullable(clients.get(cpfCanonico(cpf)));
    }

    /** Retorna uma cópia não modificável da lista de clientes. */
    public synchronized List<ContaCliente> listarClientes() {
        return List.copyOf(clients.values());
    }

    /** Retorna as aplicações do cliente; CPF inválido ou inexistente gera erro. */
    public synchronized List<AplicacaoFinanceira> listarAplicacoes(String cpf) {
        return exigirCliente(cpf).listarAplicacoes();
    }

    /**
     * Retorna o extrato inclusivo entre as duas datas, em ordem cronológica.
     * O período deve ser válido e a aplicação deve pertencer ao cliente.
     */
    public synchronized List<OperacaoFinanceira> obterExtrato(String cpf, String investmentNumber,
                                                                 LocalDate start, LocalDate end) {
        Objects.requireNonNull(start, "Data inicial obrigatória.");
        Objects.requireNonNull(end, "Data final obrigatória.");
        if (end.isBefore(start)) throw new IllegalArgumentException("O fim do período antecede o início.");
        AplicacaoFinanceira investment = exigirCliente(cpf).exigirAplicacao(investmentNumber);
        return statements.getOrDefault(investment.getNumero(), List.of()).stream()
                .filter(op -> !op.data().isBefore(start) && !op.data().isAfter(end))
                .sorted(Comparator.comparing(OperacaoFinanceira::data))
                .toList();
    }

    /** Retorna o saldo líquido de uma aplicação específica. */
    public synchronized BigDecimal getSaldo(String cpf, String investmentNumber) {
        return exigirCliente(cpf).exigirAplicacao(investmentNumber).getSaldoLiquido();
    }

    /** Soma os saldos líquidos das aplicações pertencentes ao cliente. */
    public synchronized BigDecimal getSaldoCliente(String cpf) {
        return exigirCliente(cpf).listarAplicacoes().stream()
                .map(AplicacaoFinanceira::getSaldoLiquido).reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
    }

    /** Soma os saldos líquidos de todas as aplicações cadastradas. */
    public synchronized BigDecimal getSaldoSistema() {
        return clients.values().stream().flatMap(client -> client.listarAplicacoes().stream())
                .map(AplicacaoFinanceira::getSaldoLiquido).reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
    }

    /**
     * Atualiza todas as aplicações em uma data útil. Fins de semana são
     * ignorados, conforme a regra da atividade.
     */
    public synchronized void atualizarTodos(LocalDate date) {
        Objects.requireNonNull(date, "Data obrigatória.");
        if (date.getDayOfWeek().getValue() >= 6) return;
        for (ContaCliente client : clients.values()) {
            for (AplicacaoFinanceira investment : client.listarAplicacoes()) investment.atualizar(date);
        }
    }

    /** Delega a gravação do estado completo ao componente de persistência TSV. */
    public synchronized void salvar(Path path) throws IOException {
        Objects.requireNonNull(path, "Caminho obrigatório.");
        ArmazenadorInvestimentos.salvar(path, clients.values(), statements);
    }

    /**
     * Reconstitui o sistema a partir dos formatos v1, v2 ou v3.
     *
     * @throws IOException se o arquivo não existir, estiver inconsistente ou
     *                     usar uma versão desconhecida
     */
    public static SistemaInvestimentos carregar(Path path) throws IOException {
        Objects.requireNonNull(path, "Caminho obrigatório.");
        return ArmazenadorInvestimentos.carregar(path);
    }

    /** Anexa operação já persistida sem reaplicar seus efeitos ao saldo restaurado. */
    synchronized void appendRestoredOperation(
            String cpf,
            String investmentNumber,
            OperacaoFinanceira operation
    ) {
        ContaCliente client = exigirCliente(cpf);
        AplicacaoFinanceira investment = client.exigirAplicacao(investmentNumber);
        List<OperacaoFinanceira> operations = statements.get(investment.getNumero());
        if (operations == null) {
            throw new IllegalArgumentException("Extrato da aplicação não foi inicializado.");
        }
        if (operation.data().isBefore(investment.getDataAplicacao())) {
            throw new IllegalArgumentException("Operação anterior à aplicação.");
        }
        if (operations.size() >= 100) {
            throw new IllegalArgumentException("Mais de 100 operações persistidas.");
        }
        operations.add(operation);
    }

    /** Normaliza o CPF e exige que o cliente esteja cadastrado. */
    private synchronized ContaCliente exigirCliente(String cpf) {
        ContaCliente client = clients.get(cpfCanonico(cpf));
        if (client == null) throw new IllegalArgumentException("Cliente não encontrado.");
        return client;
    }

    /** Busca um número de aplicação globalmente para impedir duplicidades. */
    private Optional<AplicacaoFinanceira> buscarAplicacaoPorNumero(String number) {
        return clients.values().stream().flatMap(c -> c.listarAplicacoes().stream())
                .filter(i -> i.getNumero().equals(number)).findFirst();
    }

    /** Remove pontuação do CPF para usar uma chave estável nos mapas internos. */
    private static String cpfCanonico(String cpf) {
        if (cpf == null) throw new IllegalArgumentException("CPF obrigatório.");
        String digits = cpf.replaceAll("\\D", "");
        if (digits.length() != 11) throw new IllegalArgumentException("CPF deve conter 11 dígitos.");
        return digits;
    }

}

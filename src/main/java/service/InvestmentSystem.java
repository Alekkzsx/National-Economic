package service;

import domain.ClientAccount;
import domain.DayCountConvention;
import domain.FinancialOperation;
import domain.Investment;
import domain.IofOption;
import domain.IrOption;
import domain.OperationType;
import domain.PersonalData;
import domain.RedemptionFrequency;
import domain.TaxRegime;
import domain.YieldPeriod;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

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
    /** Cabeçalho do formato atual de persistência, versão 3. */
    private static final String FORMAT_HEADER = "NATIONAL-ECONOMIC\t3";
    /** Cabeçalho do formato anterior, que não guardava rendimentos pendentes. */
    private static final String VERSION_2_HEADER = "NATIONAL-ECONOMIC\t2";
    /** Cabeçalho legado, anterior à configuração de prazos e impostos. */
    private static final String LEGACY_FORMAT_HEADER = "NATIONAL-ECONOMIC\t1";

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

    /**
     * Grava clientes, aplicações e operações em TSV versionado, sem serialização
     * Java. O arquivo temporário e a troca atômica reduzem o risco de deixar
     * dados incompletos se a gravação for interrompida.
     */
    public synchronized void save(Path path) throws IOException {
        Objects.requireNonNull(path, "Caminho obrigatório.");
        Path absolute = path.toAbsolutePath();
        Path parent = absolute.getParent();
        if (parent != null) Files.createDirectories(parent);
        Path temp = absolute.resolveSibling(absolute.getFileName() + ".tmp");
        try {
            try (BufferedWriter writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                writer.write(FORMAT_HEADER);
                writer.newLine();
                for (ClientAccount client : clients.values()) {
                    PersonalData data = client.getPersonalData();
                    writeRecord(writer, "C", data.getCpf(), data.getName(), data.getMobile(),
                            Integer.toString(client.getAgencyNumber()), Long.toString(client.getAccountNumber()));
                    for (Investment investment : client.getInvestments()) {
                        writeRecord(writer, "I", data.getCpf(), investment.getNumber(), investment.getName(),
                                investment.getInitialAmount().toPlainString(), investment.getMonthlyRate().toPlainString(),
                                investment.getApplicationDate().toString(), investment.getCreditedBalance().toPlainString(),
                                investment.getCreditedEarnings().toPlainString(), investment.getLastUpdateDate().toString(),
                                Integer.toString(investment.getElapsedDays()), Integer.toString(investment.getApplicationDays()),
                                Integer.toString(investment.getOperationCount()), investment.getDayCountConvention().name(),
                                investment.getTaxRegime().name(), investment.getYieldPeriod().name(),
                                Integer.toString(investment.getMaturityDays()),
                                Integer.toString(investment.getMinimumStayDays()),
                                investment.getRedemptionFrequency().name(), investment.getIofOption().name(),
                                investment.getIrOption().name(), investment.getPendingEarnings().toPlainString(),
                                Integer.toString(investment.getDaysSinceYield()));
                        for (FinancialOperation operation : statements.getOrDefault(investment.getNumber(), List.of())) {
                            writeRecord(writer, "O", data.getCpf(), investment.getNumber(), operation.date().toString(),
                                    operation.amount().toPlainString(), operation.description(), operation.type().name());
                        }
                    }
                }
            }
            setPrivatePermissions(temp);
            try {
                Files.move(temp, absolute, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temp, absolute, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    /**
     * Reconstitui o sistema a partir dos formatos v1, v2 ou v3.
     *
     * @throws IOException se o arquivo não existir, estiver inconsistente ou
     *                     usar uma versão desconhecida
     */
    public static InvestmentSystem load(Path path) throws IOException {
        Objects.requireNonNull(path, "Caminho obrigatório.");
        InvestmentSystem system = new InvestmentSystem();
        Map<String, Investment> investments = new LinkedHashMap<>();
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String header = reader.readLine();
            int formatVersion = LEGACY_FORMAT_HEADER.equals(header) ? 1
                    : VERSION_2_HEADER.equals(header) ? 2
                    : FORMAT_HEADER.equals(header) ? 3 : 0;
            if (formatVersion == 0) {
                throw new IOException("Formato de arquivo desconhecido ou versão incompatível.");
            }
            String line;
            int lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isEmpty()) continue;
                try {
                    List<String> fields = parseRecord(line);
                    switch (fields.get(0)) {
                        case "C" -> system.readClient(fields);
                        case "I" -> {
                            Investment investment = system.readInvestment(fields, formatVersion);
                            if (investments.putIfAbsent(investment.getNumber(), investment) != null) {
                                throw new IllegalArgumentException("Número de investimento duplicado.");
                            }
                        }
                        case "O" -> system.readOperation(fields, investments);
                        default -> throw new IllegalArgumentException("Tipo de registro desconhecido.");
                    }
                } catch (RuntimeException ex) {
                    throw new IOException("Registro inválido na linha " + lineNumber + ": " + ex.getMessage(), ex);
                }
            }
        }
        for (ClientAccount client : system.clients.values()) {
            for (Investment investment : client.getInvestments()) {
                if (!investments.containsKey(investment.getNumber())) throw new IOException("Investimento sem estado persistido.");
                if (system.statements.getOrDefault(investment.getNumber(), List.of()).size()
                        != investment.getOperationCount()) {
                    throw new IOException("Quantidade de operações inconsistente no investimento " + investment.getNumber() + ".");
                }
            }
        }
        return system;
    }

    /** Interpreta um registro de cliente do arquivo e delega as regras ao cadastro. */
    private void readClient(List<String> f) {
        requireFieldCount(f, 6);
        PersonalData data = new PersonalData(f.get(2), f.get(1), f.get(3));
        registerClient(data, Integer.parseInt(f.get(4)), Long.parseLong(f.get(5)));
    }

    /**
     * Interpreta um registro de aplicação e escolhe os campos conforme a versão
     * do arquivo, preenchendo defaults apenas para os formatos antigos.
     */
    private Investment readInvestment(List<String> f, int formatVersion) {
        requireFieldCount(f, switch (formatVersion) {
            case 1 -> 15;
            case 2 -> 21;
            case 3 -> 23;
            default -> throw new IllegalArgumentException("Versão inválida.");
        });
        ClientAccount client = requireClient(f.get(1));
        Investment investment = new Investment(f.get(3), f.get(2), new BigDecimal(f.get(4)),
                new BigDecimal(f.get(5)), LocalDate.parse(f.get(6)));
        investment.setDayCountConvention(DayCountConvention.valueOf(f.get(13)));
        investment.setTaxRegime(TaxRegime.valueOf(f.get(14)));
        if (formatVersion == 1) {
            investment.restoreState(new BigDecimal(f.get(7)), new BigDecimal(f.get(8)), LocalDate.parse(f.get(9)),
                    Integer.parseInt(f.get(10)), Integer.parseInt(f.get(11)), Integer.parseInt(f.get(12)));
        } else {
            Object[] terms = {
                    YieldPeriod.valueOf(f.get(15)), Integer.parseInt(f.get(16)), Integer.parseInt(f.get(17)),
                    RedemptionFrequency.valueOf(f.get(18)), IofOption.valueOf(f.get(19)), IrOption.valueOf(f.get(20))
            };
            if (formatVersion == 2) {
                investment.restoreState(new BigDecimal(f.get(7)), new BigDecimal(f.get(8)), LocalDate.parse(f.get(9)),
                        Integer.parseInt(f.get(10)), Integer.parseInt(f.get(11)), Integer.parseInt(f.get(12)),
                        (YieldPeriod) terms[0], (int) terms[1], (int) terms[2], (RedemptionFrequency) terms[3],
                        (IofOption) terms[4], (IrOption) terms[5]);
            } else {
                investment.restoreState(new BigDecimal(f.get(7)), new BigDecimal(f.get(8)), LocalDate.parse(f.get(9)),
                        Integer.parseInt(f.get(10)), Integer.parseInt(f.get(11)), Integer.parseInt(f.get(12)),
                        (YieldPeriod) terms[0], (int) terms[1], (int) terms[2], (RedemptionFrequency) terms[3],
                        (IofOption) terms[4], (IrOption) terms[5], new BigDecimal(f.get(21)),
                        Integer.parseInt(f.get(22)));
            }
        }
        client.addInvestment(investment);
        return investment;
    }

    /** Interpreta uma operação e verifica que cliente e aplicação correspondem. */
    private void readOperation(List<String> f, Map<String, Investment> investments) {
        requireFieldCount(f, 7);
        Investment investment = investments.get(f.get(2));
        if (investment == null || !investment.getNumber().equals(f.get(2))) {
            throw new IllegalArgumentException("Operação associada a investimento inexistente ou ainda não declarado.");
        }
        ClientAccount client = requireClient(f.get(1));
        if (client.findInvestment(f.get(2)).isEmpty()) throw new IllegalArgumentException("CPF não corresponde ao investimento.");
        FinancialOperation operation = new FinancialOperation(LocalDate.parse(f.get(3)),
                new BigDecimal(f.get(4)), f.get(5), OperationType.valueOf(f.get(6)));
        List<FinancialOperation> operations = statements.computeIfAbsent(f.get(2), ignored -> new ArrayList<>());
        if (operations.size() >= 100) throw new IllegalArgumentException("Mais de 100 operações persistidas.");
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

    /** Escreve uma linha TSV escapando cada campo antes da separação por tabulação. */
    private static void writeRecord(BufferedWriter writer, String... fields) throws IOException {
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) writer.write('\t');
            writer.write(escape(fields[i]));
        }
        writer.newLine();
    }

    /** Escapa caracteres de controle para manter cada registro em uma linha. */
    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n").replace("\r", "\\r");
    }

    /**
     * Decodifica um registro TSV respeitando os escapes usados por {@link #escape(String)}.
     * Rejeita sequências desconhecidas e escapes truncados para detectar corrupção.
     */
    private static List<String> parseRecord(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean escaped = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (escaped) {
                switch (c) {
                    case 't' -> field.append('\t');
                    case 'n' -> field.append('\n');
                    case 'r' -> field.append('\r');
                    case '\\' -> field.append('\\');
                    default -> throw new IllegalArgumentException("Sequência de escape inválida.");
                }
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '\t') {
                fields.add(field.toString());
                field.setLength(0);
            } else {
                field.append(c);
            }
        }
        if (escaped) throw new IllegalArgumentException("Escape incompleto.");
        fields.add(field.toString());
        return fields;
    }

    /** Confere a forma do registro antes que os campos sejam interpretados. */
    private static void requireFieldCount(List<String> fields, int expected) {
        if (fields.size() != expected) throw new IllegalArgumentException("Quantidade de campos inválida.");
    }

    /** Restringe leitura e escrita ao usuário atual em sistemas POSIX. */
    private static void setPrivatePermissions(Path path) {
        try {
            Set<PosixFilePermission> permissions = EnumSet.of(PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE);
            Files.setPosixFilePermissions(path, permissions);
        } catch (UnsupportedOperationException | IOException ignored) {
            // Em sistemas sem permissões POSIX, mantém-se a proteção padrão do sistema de arquivos.
        }
    }
}

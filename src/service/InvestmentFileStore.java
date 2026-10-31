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
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Responsável por converter o estado do sistema para o formato TSV versionado
 * e reconstruí-lo a partir de arquivos existentes.
 *
 * <p>Registros de cliente, investimento e operação usam os marcadores {@code C},
 * {@code I} e {@code O}. Campos com tabulação, quebra de linha ou barra são
 * escapados para manter cada registro em uma única linha.</p>
 */
final class InvestmentFileStore {
    private static final String CURRENT_HEADER = "NATIONAL-ECONOMIC\t3";
    private static final String VERSION_2_HEADER = "NATIONAL-ECONOMIC\t2";
    private static final String VERSION_1_HEADER = "NATIONAL-ECONOMIC\t1";

    private InvestmentFileStore() {
    }

    /** Salva todos os dados em arquivo temporário e depois o substitui com segurança. */
    static void save(
            Path path,
            Collection<ClientAccount> clients,
            Map<String, List<FinancialOperation>> statements
    ) throws IOException {
        Path absolutePath = path.toAbsolutePath();
        Path parent = absolutePath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        Path temporaryPath = absolutePath.resolveSibling(absolutePath.getFileName() + ".tmp");
        try {
            writeFile(temporaryPath, clients, statements);
            setPrivatePermissions(temporaryPath);
            replaceFile(temporaryPath, absolutePath);
        } finally {
            Files.deleteIfExists(temporaryPath);
        }
    }

    /** Carrega as versões suportadas e informa a linha caso encontre dados inválidos. */
    static InvestmentSystem load(Path path) throws IOException {
        InvestmentSystem system = new InvestmentSystem();
        Map<String, Investment> investmentsByNumber = new LinkedHashMap<>();
        Map<String, Integer> operationCounts = new LinkedHashMap<>();

        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            int version = readVersion(reader.readLine());
            String line;
            int lineNumber = 1;

            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isEmpty()) {
                    continue;
                }

                try {
                    readRecord(line, version, system, investmentsByNumber, operationCounts);
                } catch (RuntimeException exception) {
                    throw new IOException(
                            "Registro inválido na linha " + lineNumber + ": " + exception.getMessage(),
                            exception
                    );
                }
            }
        }

        validateOperationCounts(system, operationCounts);
        return system;
    }

    private static void writeFile(
            Path path,
            Collection<ClientAccount> clients,
            Map<String, List<FinancialOperation>> statements
    ) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            writer.write(CURRENT_HEADER);
            writer.newLine();

            for (ClientAccount client : clients) {
                writeClient(writer, client);
                for (Investment investment : client.getInvestments()) {
                    writeInvestment(writer, client, investment);
                    writeOperations(writer, client, investment, statements);
                }
            }
        }
    }

    private static void writeClient(BufferedWriter writer, ClientAccount client) throws IOException {
        PersonalData personalData = client.getPersonalData();
        writeRecord(
                writer,
                "C",
                personalData.getCpf(),
                personalData.getName(),
                personalData.getMobile(),
                Integer.toString(client.getAgencyNumber()),
                Long.toString(client.getAccountNumber())
        );
    }

    private static void writeInvestment(
            BufferedWriter writer,
            ClientAccount client,
            Investment investment
    ) throws IOException {
        writeRecord(
                writer,
                "I",
                client.getPersonalData().getCpf(),
                investment.getNumber(),
                investment.getName(),
                investment.getInitialAmount().toPlainString(),
                investment.getMonthlyRate().toPlainString(),
                investment.getApplicationDate().toString(),
                investment.getCreditedBalance().toPlainString(),
                investment.getCreditedEarnings().toPlainString(),
                investment.getLastUpdateDate().toString(),
                Integer.toString(investment.getElapsedDays()),
                Integer.toString(investment.getApplicationDays()),
                Integer.toString(investment.getOperationCount()),
                investment.getDayCountConvention().name(),
                investment.getTaxRegime().name(),
                investment.getYieldPeriod().name(),
                Integer.toString(investment.getMaturityDays()),
                Integer.toString(investment.getMinimumStayDays()),
                investment.getRedemptionFrequency().name(),
                investment.getIofOption().name(),
                investment.getIrOption().name(),
                investment.getPendingEarnings().toPlainString(),
                Integer.toString(investment.getDaysSinceYield())
        );
    }

    private static void writeOperations(
            BufferedWriter writer,
            ClientAccount client,
            Investment investment,
            Map<String, List<FinancialOperation>> statements
    ) throws IOException {
        for (FinancialOperation operation : statements.getOrDefault(investment.getNumber(), List.of())) {
            writeRecord(
                    writer,
                    "O",
                    client.getPersonalData().getCpf(),
                    investment.getNumber(),
                    operation.date().toString(),
                    operation.amount().toPlainString(),
                    operation.description(),
                    operation.type().name()
            );
        }
    }

    private static void readRecord(
            String line,
            int version,
            InvestmentSystem system,
            Map<String, Investment> investmentsByNumber,
            Map<String, Integer> operationCounts
    ) throws IOException {
        List<String> fields = parseRecord(line);
        switch (fields.getFirst()) {
            case "C" -> readClient(fields, system);
            case "I" -> readInvestment(fields, version, system, investmentsByNumber);
            case "O" -> readOperation(fields, system, investmentsByNumber, operationCounts);
            default -> throw new IllegalArgumentException("Tipo de registro desconhecido.");
        }
    }

    private static void readClient(List<String> fields, InvestmentSystem system) {
        requireFieldCount(fields, 6);
        PersonalData personalData = new PersonalData(fields.get(2), fields.get(1), fields.get(3));
        system.registerClient(personalData, Integer.parseInt(fields.get(4)), Long.parseLong(fields.get(5)));
    }

    private static void readInvestment(
            List<String> fields,
            int version,
            InvestmentSystem system,
            Map<String, Investment> investmentsByNumber
    ) {
        requireFieldCount(fields, fieldCountFor(version));
        String clientCpf = fields.get(1);
        Investment investment = new Investment(
                fields.get(3),
                fields.get(2),
                new BigDecimal(fields.get(4)),
                new BigDecimal(fields.get(5)),
                LocalDate.parse(fields.get(6))
        );

        investment.setDayCountConvention(DayCountConvention.valueOf(fields.get(13)));
        investment.setTaxRegime(TaxRegime.valueOf(fields.get(14)));
        restoreInvestmentState(investment, fields, version);
        system.registerInvestment(clientCpf, investment);

        if (investmentsByNumber.putIfAbsent(investment.getNumber(), investment) != null) {
            throw new IllegalArgumentException("Número de investimento duplicado.");
        }
    }

    private static void restoreInvestmentState(Investment investment, List<String> fields, int version) {
        BigDecimal balance = new BigDecimal(fields.get(7));
        BigDecimal accruedEarnings = new BigDecimal(fields.get(8));
        LocalDate lastUpdateDate = LocalDate.parse(fields.get(9));
        int elapsedDays = Integer.parseInt(fields.get(10));
        int applicationDays = Integer.parseInt(fields.get(11));
        int operationCount = Integer.parseInt(fields.get(12));

        if (version == 1) {
            investment.restoreState(
                    balance, accruedEarnings, lastUpdateDate, elapsedDays, applicationDays, operationCount
            );
            return;
        }

        YieldPeriod yieldPeriod = YieldPeriod.valueOf(fields.get(15));
        int maturityDays = Integer.parseInt(fields.get(16));
        int minimumStayDays = Integer.parseInt(fields.get(17));
        RedemptionFrequency redemptionFrequency = RedemptionFrequency.valueOf(fields.get(18));
        IofOption iofOption = IofOption.valueOf(fields.get(19));
        IrOption irOption = IrOption.valueOf(fields.get(20));

        if (version == 2) {
            investment.restoreState(
                    balance,
                    accruedEarnings,
                    lastUpdateDate,
                    elapsedDays,
                    applicationDays,
                    operationCount,
                    yieldPeriod,
                    maturityDays,
                    minimumStayDays,
                    redemptionFrequency,
                    iofOption,
                    irOption
            );
            return;
        }

        investment.restoreState(
                balance,
                accruedEarnings,
                lastUpdateDate,
                elapsedDays,
                applicationDays,
                operationCount,
                yieldPeriod,
                maturityDays,
                minimumStayDays,
                redemptionFrequency,
                iofOption,
                irOption,
                new BigDecimal(fields.get(21)),
                Integer.parseInt(fields.get(22))
        );
    }

    private static void readOperation(
            List<String> fields,
            InvestmentSystem system,
            Map<String, Investment> investmentsByNumber,
            Map<String, Integer> operationCounts
    ) {
        requireFieldCount(fields, 7);
        String clientCpf = fields.get(1);
        String investmentNumber = fields.get(2);
        Investment investment = investmentsByNumber.get(investmentNumber);
        if (investment == null) {
            throw new IllegalArgumentException("Operação associada a investimento inexistente ou ainda não declarado.");
        }

        FinancialOperation operation = new FinancialOperation(
                LocalDate.parse(fields.get(3)),
                new BigDecimal(fields.get(4)),
                fields.get(5),
                OperationType.valueOf(fields.get(6))
        );
        system.appendRestoredOperation(clientCpf, investmentNumber, operation);
        operationCounts.merge(investmentNumber, 1, Integer::sum);
    }

    private static void validateOperationCounts(
            InvestmentSystem system,
            Map<String, Integer> operationCounts
    ) throws IOException {
        for (ClientAccount client : system.getClients()) {
            for (Investment investment : client.getInvestments()) {
                int persistedCount = operationCounts.getOrDefault(investment.getNumber(), 0);
                if (persistedCount != investment.getOperationCount()) {
                    throw new IOException(
                            "Quantidade de operações inconsistente no investimento "
                                    + investment.getNumber() + "."
                    );
                }
            }
        }
    }

    private static int readVersion(String header) throws IOException {
        if (VERSION_1_HEADER.equals(header)) {
            return 1;
        }
        if (VERSION_2_HEADER.equals(header)) {
            return 2;
        }
        if (CURRENT_HEADER.equals(header)) {
            return 3;
        }
        throw new IOException("Formato de arquivo desconhecido ou versão incompatível.");
    }

    private static int fieldCountFor(int version) {
        return switch (version) {
            case 1 -> 15;
            case 2 -> 21;
            case 3 -> 23;
            default -> throw new IllegalArgumentException("Versão inválida.");
        };
    }

    private static void writeRecord(BufferedWriter writer, String... fields) throws IOException {
        for (int index = 0; index < fields.length; index++) {
            if (index > 0) {
                writer.write('\t');
            }
            writer.write(escape(fields[index]));
        }
        writer.newLine();
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\")
                .replace("\t", "\\t")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    private static List<String> parseRecord(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean escaped = false;

        for (int index = 0; index < line.length(); index++) {
            char character = line.charAt(index);
            if (escaped) {
                appendEscapedCharacter(field, character);
                escaped = false;
            } else if (character == '\\') {
                escaped = true;
            } else if (character == '\t') {
                fields.add(field.toString());
                field.setLength(0);
            } else {
                field.append(character);
            }
        }

        if (escaped) {
            throw new IllegalArgumentException("Escape incompleto.");
        }
        fields.add(field.toString());
        return fields;
    }

    private static void appendEscapedCharacter(StringBuilder field, char character) {
        switch (character) {
            case 't' -> field.append('\t');
            case 'n' -> field.append('\n');
            case 'r' -> field.append('\r');
            case '\\' -> field.append('\\');
            default -> throw new IllegalArgumentException("Sequência de escape inválida.");
        }
    }

    private static void requireFieldCount(List<String> fields, int expected) {
        if (fields.size() != expected) {
            throw new IllegalArgumentException("Quantidade de campos inválida.");
        }
    }

    private static void replaceFile(Path temporaryPath, Path destinationPath) throws IOException {
        try {
            Files.move(
                    temporaryPath,
                    destinationPath,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
            );
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporaryPath, destinationPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void setPrivatePermissions(Path path) throws IOException {
        try {
            Set<PosixFilePermission> permissions = EnumSet.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE
            );
            Files.setPosixFilePermissions(path, permissions);
        } catch (UnsupportedOperationException ignored) {
            // Sistemas sem permissões POSIX usam as proteções padrão do sistema de arquivos.
        }
    }
}

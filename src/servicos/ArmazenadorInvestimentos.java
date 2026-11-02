package servicos;

import modelo.ContaCliente;
import modelo.ConvencaoContagemDias;
import modelo.OperacaoFinanceira;
import modelo.AplicacaoFinanceira;
import modelo.OpcaoIOF;
import modelo.OpcaoIR;
import modelo.TipoOperacao;
import modelo.DadosPessoais;
import modelo.FrequenciaResgate;
import modelo.RegimeTributario;
import modelo.PeriodicidadeRendimento;

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
final class ArmazenadorInvestimentos {
    private static final String CURRENT_HEADER = "NATIONAL-ECONOMIC\t3";
    private static final String VERSION_2_HEADER = "NATIONAL-ECONOMIC\t2";
    private static final String VERSION_1_HEADER = "NATIONAL-ECONOMIC\t1";

    private ArmazenadorInvestimentos() {
    }

    /** Salva todos os dados em arquivo temporário e depois o substitui com segurança. */
    static void salvar(
            Path path,
            Collection<ContaCliente> clients,
            Map<String, List<OperacaoFinanceira>> statements
    ) throws IOException {
        Path absolutePath = path.toAbsolutePath();
        Path parent = absolutePath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        Path temporaryPath = absolutePath.resolveSibling(absolutePath.getFileName() + ".tmp");
        try {
            gravarArquivo(temporaryPath, clients, statements);
            definirPermissoesPrivadas(temporaryPath);
            substituirArquivo(temporaryPath, absolutePath);
        } finally {
            Files.deleteIfExists(temporaryPath);
        }
    }

    /** Carrega as versões suportadas e informa a linha caso encontre dados inválidos. */
    static SistemaInvestimentos carregar(Path path) throws IOException {
        SistemaInvestimentos system = new SistemaInvestimentos();
        Map<String, AplicacaoFinanceira> investmentsByNumber = new LinkedHashMap<>();
        Map<String, Integer> operationCounts = new LinkedHashMap<>();

        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            int version = lerVersao(reader.readLine());
            String line;
            int lineNumber = 1;

            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isEmpty()) {
                    continue;
                }

                try {
                    lerRegistro(line, version, system, investmentsByNumber, operationCounts);
                } catch (RuntimeException exception) {
                    throw new IOException(
                            "Registro inválido na linha " + lineNumber + ": " + exception.getMessage(),
                            exception
                    );
                }
            }
        }

        validarContagemOperacoes(system, operationCounts);
        return system;
    }

    private static void gravarArquivo(
            Path path,
            Collection<ContaCliente> clients,
            Map<String, List<OperacaoFinanceira>> statements
    ) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            writer.write(CURRENT_HEADER);
            writer.newLine();

            for (ContaCliente client : clients) {
                gravarCliente(writer, client);
                for (AplicacaoFinanceira investment : client.listarAplicacoes()) {
                    gravarAplicacao(writer, client, investment);
                    gravarOperacoes(writer, client, investment, statements);
                }
            }
        }
    }

    private static void gravarCliente(BufferedWriter writer, ContaCliente client) throws IOException {
        DadosPessoais personalData = client.getDadosPessoais();
        gravarRegistro(
                writer,
                "C",
                personalData.getCpf(),
                personalData.getNome(),
                personalData.getCelular(),
                Integer.toString(client.getNumeroAgencia()),
                Long.toString(client.getNumeroConta())
        );
    }

    private static void gravarAplicacao(
            BufferedWriter writer,
            ContaCliente client,
            AplicacaoFinanceira investment
    ) throws IOException {
        gravarRegistro(
                writer,
                "I",
                client.getDadosPessoais().getCpf(),
                investment.getNumero(),
                investment.getNome(),
                investment.getValorInicial().toPlainString(),
                investment.getTaxaMensal().toPlainString(),
                investment.getDataAplicacao().toString(),
                investment.getSaldoCreditado().toPlainString(),
                investment.getRendimentoCreditado().toPlainString(),
                investment.getDataUltimaAtualizacao().toString(),
                Integer.toString(investment.getDiasCorridos()),
                Integer.toString(investment.getDiasEfetivos()),
                Integer.toString(investment.getQuantidadeOperacoes()),
                investment.getConvencaoContagemDias().nome(),
                investment.getRegimeTributario().nome(),
                investment.getPeriodicidadeRendimento().nome(),
                Integer.toString(investment.getDiasVencimento()),
                Integer.toString(investment.getDiasPermanenciaMinima()),
                investment.getFrequenciaResgate().nome(),
                investment.getOpcaoIOF().nome(),
                investment.getOpcaoIR().nome(),
                investment.getRendimentosPendentes().toPlainString(),
                Integer.toString(investment.getDiasDesdeRendimento())
        );
    }

    private static void gravarOperacoes(
            BufferedWriter writer,
            ContaCliente client,
            AplicacaoFinanceira investment,
            Map<String, List<OperacaoFinanceira>> statements
    ) throws IOException {
        for (OperacaoFinanceira operation : statements.getOrDefault(investment.getNumero(), List.of())) {
            gravarRegistro(
                    writer,
                    "O",
                    client.getDadosPessoais().getCpf(),
                    investment.getNumero(),
                    operation.data().toString(),
                    operation.valor().toPlainString(),
                    operation.descricao(),
                    operation.tipo().nome()
            );
        }
    }

    private static void lerRegistro(
            String line,
            int version,
            SistemaInvestimentos system,
            Map<String, AplicacaoFinanceira> investmentsByNumber,
            Map<String, Integer> operationCounts
    ) throws IOException {
        List<String> fields = interpretarRegistro(line);
        switch (fields.getFirst()) {
            case "C" -> lerCliente(fields, system);
            case "I" -> lerAplicacao(fields, version, system, investmentsByNumber);
            case "O" -> lerOperacao(fields, system, investmentsByNumber, operationCounts);
            default -> throw new IllegalArgumentException("Tipo de registro desconhecido.");
        }
    }

    private static void lerCliente(List<String> fields, SistemaInvestimentos system) {
        exigirQuantidadeCampos(fields, 6);
        DadosPessoais personalData = new DadosPessoais(fields.get(2), fields.get(1), fields.get(3));
        system.cadastrarCliente(personalData, Integer.parseInt(fields.get(4)), Long.parseLong(fields.get(5)));
    }

    private static void lerAplicacao(
            List<String> fields,
            int version,
            SistemaInvestimentos system,
            Map<String, AplicacaoFinanceira> investmentsByNumber
    ) {
        exigirQuantidadeCampos(fields, quantidadeCamposPara(version));
        String clientCpf = fields.get(1);
        AplicacaoFinanceira investment = new AplicacaoFinanceira(
                fields.get(3),
                fields.get(2),
                new BigDecimal(fields.get(4)),
                new BigDecimal(fields.get(5)),
                LocalDate.parse(fields.get(6))
        );

        investment.setConvencaoContagemDias(ConvencaoContagemDias.valueOf(fields.get(13)));
        investment.setRegimeTributario(RegimeTributario.valueOf(fields.get(14)));
        restaurarEstadoAplicacao(investment, fields, version);
        system.cadastrarAplicacao(clientCpf, investment);

        if (investmentsByNumber.putIfAbsent(investment.getNumero(), investment) != null) {
            throw new IllegalArgumentException("Número de investimento duplicado.");
        }
    }

    private static void restaurarEstadoAplicacao(AplicacaoFinanceira investment, List<String> fields, int version) {
        BigDecimal balance = new BigDecimal(fields.get(7));
        BigDecimal accruedEarnings = new BigDecimal(fields.get(8));
        LocalDate lastUpdateDate = LocalDate.parse(fields.get(9));
        int elapsedDays = Integer.parseInt(fields.get(10));
        int applicationDays = Integer.parseInt(fields.get(11));
        int operationCount = Integer.parseInt(fields.get(12));

        if (version == 1) {
            investment.restaurarEstado(
                    balance, accruedEarnings, lastUpdateDate, elapsedDays, applicationDays, operationCount
            );
            return;
        }

        PeriodicidadeRendimento yieldPeriod = PeriodicidadeRendimento.valueOf(fields.get(15));
        int maturityDays = Integer.parseInt(fields.get(16));
        int minimumStayDays = Integer.parseInt(fields.get(17));
        FrequenciaResgate redemptionFrequency = FrequenciaResgate.valueOf(fields.get(18));
        OpcaoIOF iofOption = OpcaoIOF.valueOf(fields.get(19));
        OpcaoIR irOption = OpcaoIR.valueOf(fields.get(20));

        if (version == 2) {
            investment.restaurarEstado(
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

        investment.restaurarEstado(
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

    private static void lerOperacao(
            List<String> fields,
            SistemaInvestimentos system,
            Map<String, AplicacaoFinanceira> investmentsByNumber,
            Map<String, Integer> operationCounts
    ) {
        exigirQuantidadeCampos(fields, 7);
        String clientCpf = fields.get(1);
        String investmentNumber = fields.get(2);
        AplicacaoFinanceira investment = investmentsByNumber.get(investmentNumber);
        if (investment == null) {
            throw new IllegalArgumentException("Operação associada a investimento inexistente ou ainda não declarado.");
        }

        OperacaoFinanceira operation = new OperacaoFinanceira(
                LocalDate.parse(fields.get(3)),
                new BigDecimal(fields.get(4)),
                fields.get(5),
                TipoOperacao.valueOf(fields.get(6))
        );
        system.appendRestoredOperation(clientCpf, investmentNumber, operation);
        operationCounts.merge(investmentNumber, 1, Integer::sum);
    }

    private static void validarContagemOperacoes(
            SistemaInvestimentos system,
            Map<String, Integer> operationCounts
    ) throws IOException {
        for (ContaCliente client : system.listarClientes()) {
            for (AplicacaoFinanceira investment : client.listarAplicacoes()) {
                int persistedCount = operationCounts.getOrDefault(investment.getNumero(), 0);
                if (persistedCount != investment.getQuantidadeOperacoes()) {
                    throw new IOException(
                            "Quantidade de operações inconsistente no investimento "
                                    + investment.getNumero() + "."
                    );
                }
            }
        }
    }

    private static int lerVersao(String header) throws IOException {
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

    private static int quantidadeCamposPara(int version) {
        return switch (version) {
            case 1 -> 15;
            case 2 -> 21;
            case 3 -> 23;
            default -> throw new IllegalArgumentException("Versão inválida.");
        };
    }

    private static void gravarRegistro(BufferedWriter writer, String... fields) throws IOException {
        for (int index = 0; index < fields.length; index++) {
            if (index > 0) {
                writer.write('\t');
            }
            writer.write(escapar(fields[index]));
        }
        writer.newLine();
    }

    private static String escapar(String value) {
        return value.replace("\\", "\\\\")
                .replace("\t", "\\t")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    private static List<String> interpretarRegistro(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean escaped = false;

        for (int index = 0; index < line.length(); index++) {
            char character = line.charAt(index);
            if (escaped) {
                acrescentarCaractereEscapado(field, character);
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

    private static void acrescentarCaractereEscapado(StringBuilder field, char character) {
        switch (character) {
            case 't' -> field.append('\t');
            case 'n' -> field.append('\n');
            case 'r' -> field.append('\r');
            case '\\' -> field.append('\\');
            default -> throw new IllegalArgumentException("Sequência de escape inválida.");
        }
    }

    private static void exigirQuantidadeCampos(List<String> fields, int expected) {
        if (fields.size() != expected) {
            throw new IllegalArgumentException("Quantidade de campos inválida.");
        }
    }

    private static void substituirArquivo(Path temporaryPath, Path destinationPath) throws IOException {
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

    private static void definirPermissoesPrivadas(Path path) throws IOException {
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

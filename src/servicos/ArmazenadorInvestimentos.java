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
    private static final String CABECALHO_ATUAL = "NATIONAL-ECONOMIC\t4";
    private static final String CABECALHO_VERSAO_3 = "NATIONAL-ECONOMIC\t3";
    private static final String CABECALHO_VERSAO_2 = "NATIONAL-ECONOMIC\t2";
    private static final String CABECALHO_VERSAO_1 = "NATIONAL-ECONOMIC\t1";

    private ArmazenadorInvestimentos() {
    }

    /** Salva todos os dados em arquivo temporário e depois o substitui com segurança. */
    static void salvar(
            Path caminho,
            Collection<ContaCliente> clientes,
            Map<String, List<OperacaoFinanceira>> extratos
    ) throws IOException {
        Path caminhoAbsoluto = caminho.toAbsolutePath();
        Path diretorioPai = caminhoAbsoluto.getParent();
        if (diretorioPai != null) {
            Files.createDirectories(diretorioPai);
        }

        Path caminhoTemporario = caminhoAbsoluto.resolveSibling(caminhoAbsoluto.getFileName() + ".tmp");
        try {
            gravarArquivo(caminhoTemporario, clientes, extratos);
            definirPermissoesPrivadas(caminhoTemporario);
            substituirArquivo(caminhoTemporario, caminhoAbsoluto);
        } finally {
            Files.deleteIfExists(caminhoTemporario);
        }
    }

    /** Carrega as versões suportadas e informa a linha caso encontre dados inválidos. */
    static SistemaInvestimentos carregar(Path caminho) throws IOException {
        SistemaInvestimentos sistema = new SistemaInvestimentos();
        Map<String, AplicacaoFinanceira> aplicacoesPorNumero = new LinkedHashMap<>();
        Map<String, Integer> contagemOperacoes = new LinkedHashMap<>();
        int versao;

        try (BufferedReader leitor = Files.newBufferedReader(caminho, StandardCharsets.UTF_8)) {
            versao = lerVersao(leitor.readLine());
            String linha;
            int numeroLinha = 1;

            while ((linha = leitor.readLine()) != null) {
                numeroLinha++;
                if (linha.isEmpty()) {
                    continue;
                }

                try {
                    lerRegistro(linha, versao, sistema, aplicacoesPorNumero, contagemOperacoes);
                } catch (RuntimeException excecao) {
                    throw new IOException(
                            "Registro inválido na linha " + numeroLinha + ": " + excecao.getMessage(),
                            excecao
                    );
                }
            }
        }

        if (versao < 4) {
            restaurarTotalResgatadoLegado(sistema);
        }
        validarContagemOperacoes(sistema, contagemOperacoes);
        return sistema;
    }

    /**
     * Reconstrói o total de retiradas para arquivos antigos que não o armazenavam.
     */
    private static void restaurarTotalResgatadoLegado(SistemaInvestimentos sistema) {
        for (ContaCliente cliente : sistema.listarClientes()) {
            String cpf = cliente.getDadosPessoais().getCpf();
            for (AplicacaoFinanceira aplicacao : cliente.listarAplicacoes()) {
                BigDecimal totalRetirado = sistema.listarOperacoes(cpf, aplicacao.getNumero()).stream()
                        .filter(operacao -> operacao.tipo() == TipoOperacao.WITHDRAWAL)
                        .map(OperacaoFinanceira::valor)
                        .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
                aplicacao.setValorResgatado(totalRetirado);
            }
        }
    }

    private static void gravarArquivo(
            Path caminho,
            Collection<ContaCliente> clientes,
            Map<String, List<OperacaoFinanceira>> extratos
    ) throws IOException {
        try (BufferedWriter escritor = Files.newBufferedWriter(caminho, StandardCharsets.UTF_8)) {
            escritor.write(CABECALHO_ATUAL);
            escritor.newLine();

            for (ContaCliente cliente : clientes) {
                gravarCliente(escritor, cliente);
                for (AplicacaoFinanceira aplicacao : cliente.listarAplicacoes()) {
                    gravarAplicacao(escritor, cliente, aplicacao);
                    gravarOperacoes(escritor, cliente, aplicacao, extratos);
                }
            }
        }
    }

    private static void gravarCliente(BufferedWriter escritor, ContaCliente cliente) throws IOException {
        DadosPessoais dadosPessoais = cliente.getDadosPessoais();
        gravarRegistro(
                escritor,
                "C",
                dadosPessoais.getCpf(),
                dadosPessoais.getNome(),
                dadosPessoais.getCelular(),
                Integer.toString(cliente.getNumeroAgencia()),
                Long.toString(cliente.getNumeroConta())
        );
    }

    private static void gravarAplicacao(
            BufferedWriter escritor,
            ContaCliente cliente,
            AplicacaoFinanceira aplicacao
    ) throws IOException {
        gravarRegistro(
                escritor,
                "I",
                cliente.getDadosPessoais().getCpf(),
                aplicacao.getNumero(),
                aplicacao.getNome(),
                aplicacao.getValorInicial().toPlainString(),
                aplicacao.getTaxaMensal().toPlainString(),
                aplicacao.getDataAplicacao().toString(),
                aplicacao.getSaldoCreditado().toPlainString(),
                aplicacao.getRendimentoCreditado().toPlainString(),
                aplicacao.getDataUltimaAtualizacao().toString(),
                Integer.toString(aplicacao.getDiasCorridos()),
                Integer.toString(aplicacao.getDiasEfetivos()),
                Integer.toString(aplicacao.getQuantidadeOperacoes()),
                aplicacao.getConvencaoContagemDias().name(),
                aplicacao.getRegimeTributario().name(),
                aplicacao.getPeriodicidadeRendimento().name(),
                Integer.toString(aplicacao.getDiasVencimento()),
                Integer.toString(aplicacao.getDiasPermanenciaMinima()),
                aplicacao.getFrequenciaResgate().name(),
                aplicacao.getOpcaoIOF().name(),
                aplicacao.getOpcaoIR().name(),
                aplicacao.getRendimentosPendentes().toPlainString(),
                Integer.toString(aplicacao.getDiasDesdeRendimento()),
                aplicacao.getValorResgatado().toPlainString()
        );
    }

    private static void gravarOperacoes(
            BufferedWriter escritor,
            ContaCliente cliente,
            AplicacaoFinanceira aplicacao,
            Map<String, List<OperacaoFinanceira>> extratos
    ) throws IOException {
        for (OperacaoFinanceira operacao : extratos.getOrDefault(aplicacao.getNumero(), List.of())) {
            gravarRegistro(
                    escritor,
                    "O",
                    cliente.getDadosPessoais().getCpf(),
                    aplicacao.getNumero(),
                    operacao.data().toString(),
                    operacao.valor().toPlainString(),
                    operacao.descricao(),
                    operacao.tipo().name()
            );
        }
    }

    private static void lerRegistro(
            String linha,
            int versao,
            SistemaInvestimentos sistema,
            Map<String, AplicacaoFinanceira> aplicacoesPorNumero,
            Map<String, Integer> contagemOperacoes
    ) throws IOException {
        List<String> campos = interpretarRegistro(linha);
        switch (campos.getFirst()) {
            case "C" -> lerCliente(campos, sistema);
            case "I" -> lerAplicacao(campos, versao, sistema, aplicacoesPorNumero);
            case "O" -> lerOperacao(campos, sistema, aplicacoesPorNumero, contagemOperacoes);
            default -> throw new IllegalArgumentException("Tipo de registro desconhecido.");
        }
    }

    private static void lerCliente(List<String> campos, SistemaInvestimentos sistema) {
        exigirQuantidadeCampos(campos, 6);
        DadosPessoais dadosPessoais = new DadosPessoais(campos.get(2), campos.get(1), campos.get(3));
        sistema.cadastrarCliente(dadosPessoais, Integer.parseInt(campos.get(4)), Long.parseLong(campos.get(5)));
    }

    private static void lerAplicacao(
            List<String> campos,
            int versao,
            SistemaInvestimentos sistema,
            Map<String, AplicacaoFinanceira> aplicacoesPorNumero
    ) {
        exigirQuantidadeCampos(campos, quantidadeCamposPara(versao));
        String cpfCliente = campos.get(1);
        AplicacaoFinanceira aplicacao = new AplicacaoFinanceira(
                campos.get(3),
                campos.get(2),
                new BigDecimal(campos.get(4)),
                new BigDecimal(campos.get(5)),
                LocalDate.parse(campos.get(6))
        );

        aplicacao.setConvencaoContagemDias(ConvencaoContagemDias.valueOf(campos.get(13)));
        aplicacao.setRegimeTributario(RegimeTributario.valueOf(campos.get(14)));
        restaurarEstadoAplicacao(aplicacao, campos, versao);
        sistema.cadastrarAplicacao(cpfCliente, aplicacao);

        if (aplicacoesPorNumero.putIfAbsent(aplicacao.getNumero(), aplicacao) != null) {
            throw new IllegalArgumentException("Número de investimento duplicado.");
        }
    }

    private static void restaurarEstadoAplicacao(AplicacaoFinanceira aplicacao, List<String> campos, int versao) {
        BigDecimal saldo = new BigDecimal(campos.get(7));
        BigDecimal rendimentosAcumulados = new BigDecimal(campos.get(8));
        LocalDate dataUltimaAtualizacao = LocalDate.parse(campos.get(9));
        int diasCorridos = Integer.parseInt(campos.get(10));
        int diasEfetivos = Integer.parseInt(campos.get(11));
        int quantidadeOperacoes = Integer.parseInt(campos.get(12));

        if (versao == 1) {
            aplicacao.restaurarEstado(
                    saldo, rendimentosAcumulados, dataUltimaAtualizacao, diasCorridos, diasEfetivos, quantidadeOperacoes
            );
            return;
        }

        PeriodicidadeRendimento periodicidadeRendimento = PeriodicidadeRendimento.valueOf(campos.get(15));
        int diasVencimento = Integer.parseInt(campos.get(16));
        int diasPermanenciaMinima = Integer.parseInt(campos.get(17));
        FrequenciaResgate frequenciaResgate = FrequenciaResgate.valueOf(campos.get(18));
        OpcaoIOF opcaoIOF = OpcaoIOF.valueOf(campos.get(19));
        OpcaoIR opcaoIR = OpcaoIR.valueOf(campos.get(20));

        if (versao == 2) {
            aplicacao.restaurarEstado(
                    saldo,
                    rendimentosAcumulados,
                    dataUltimaAtualizacao,
                    diasCorridos,
                    diasEfetivos,
                    quantidadeOperacoes,
                    periodicidadeRendimento,
                    diasVencimento,
                    diasPermanenciaMinima,
                    frequenciaResgate,
                    opcaoIOF,
                    opcaoIR
            );
            return;
        }

        aplicacao.restaurarEstado(
                saldo,
                rendimentosAcumulados,
                dataUltimaAtualizacao,
                diasCorridos,
                diasEfetivos,
                quantidadeOperacoes,
                periodicidadeRendimento,
                diasVencimento,
                diasPermanenciaMinima,
                frequenciaResgate,
                opcaoIOF,
                opcaoIR,
                new BigDecimal(campos.get(21)),
                Integer.parseInt(campos.get(22))
        );
        if (versao >= 4) {
            aplicacao.setValorResgatado(new BigDecimal(campos.get(23)));
        }
    }

    private static void lerOperacao(
            List<String> campos,
            SistemaInvestimentos sistema,
            Map<String, AplicacaoFinanceira> aplicacoesPorNumero,
            Map<String, Integer> contagemOperacoes
    ) {
        exigirQuantidadeCampos(campos, 7);
        String cpfCliente = campos.get(1);
        String numeroAplicacao = campos.get(2);
        AplicacaoFinanceira aplicacao = aplicacoesPorNumero.get(numeroAplicacao);
        if (aplicacao == null) {
            throw new IllegalArgumentException("Operação associada a investimento inexistente ou ainda não declarado.");
        }

        OperacaoFinanceira operacao = new OperacaoFinanceira(
                LocalDate.parse(campos.get(3)),
                new BigDecimal(campos.get(4)),
                campos.get(5),
                TipoOperacao.valueOf(campos.get(6))
        );
        sistema.anexarOperacaoRestaurada(cpfCliente, numeroAplicacao, operacao);
        contagemOperacoes.merge(numeroAplicacao, 1, Integer::sum);
    }

    private static void validarContagemOperacoes(
            SistemaInvestimentos sistema,
            Map<String, Integer> contagemOperacoes
    ) throws IOException {
        for (ContaCliente cliente : sistema.listarClientes()) {
            for (AplicacaoFinanceira aplicacao : cliente.listarAplicacoes()) {
                int quantidadePersistida = contagemOperacoes.getOrDefault(aplicacao.getNumero(), 0);
                if (quantidadePersistida != aplicacao.getQuantidadeOperacoes()) {
                    throw new IOException(
                            "Quantidade de operações inconsistente no investimento "
                                    + aplicacao.getNumero() + "."
                    );
                }
            }
        }
    }

    private static int lerVersao(String cabecalho) throws IOException {
        if (CABECALHO_VERSAO_1.equals(cabecalho)) {
            return 1;
        }
        if (CABECALHO_VERSAO_2.equals(cabecalho)) {
            return 2;
        }
        if (CABECALHO_VERSAO_3.equals(cabecalho)) {
            return 3;
        }
        if (CABECALHO_ATUAL.equals(cabecalho)) {
            return 4;
        }
        throw new IOException("Formato de arquivo desconhecido ou versão incompatível.");
    }

    private static int quantidadeCamposPara(int versao) {
        return switch (versao) {
            case 1 -> 15;
            case 2 -> 21;
            case 3 -> 23;
            case 4 -> 24;
            default -> throw new IllegalArgumentException("Versão inválida.");
        };
    }

    private static void gravarRegistro(BufferedWriter escritor, String... campos) throws IOException {
        for (int indice = 0; indice < campos.length; indice++) {
            if (indice > 0) {
                escritor.write('\t');
            }
            escritor.write(escapar(campos[indice]));
        }
        escritor.newLine();
    }

    private static String escapar(String valor) {
        return valor.replace("\\", "\\\\")
                .replace("\t", "\\t")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    private static List<String> interpretarRegistro(String linha) {
        List<String> campos = new ArrayList<>();
        StringBuilder campo = new StringBuilder();
        boolean escaped = false;

        for (int indice = 0; indice < linha.length(); indice++) {
            char character = linha.charAt(indice);
            if (escaped) {
                acrescentarCaractereEscapado(campo, character);
                escaped = false;
            } else if (character == '\\') {
                escaped = true;
            } else if (character == '\t') {
                campos.add(campo.toString());
                campo.setLength(0);
            } else {
                campo.append(character);
            }
        }

        if (escaped) {
            throw new IllegalArgumentException("Escape incompleto.");
        }
        campos.add(campo.toString());
        return campos;
    }

    private static void acrescentarCaractereEscapado(StringBuilder campo, char character) {
        switch (character) {
            case 't' -> campo.append('\t');
            case 'n' -> campo.append('\n');
            case 'r' -> campo.append('\r');
            case '\\' -> campo.append('\\');
            default -> throw new IllegalArgumentException("Sequência de escape inválida.");
        }
    }

    private static void exigirQuantidadeCampos(List<String> campos, int expected) {
        if (campos.size() != expected) {
            throw new IllegalArgumentException("Quantidade de campos inválida.");
        }
    }

    private static void substituirArquivo(Path caminhoTemporario, Path destinationPath) throws IOException {
        try {
            Files.move(
                    caminhoTemporario,
                    destinationPath,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
            );
        } catch (AtomicMoveNotSupportedException excecao) {
            Files.move(caminhoTemporario, destinationPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void definirPermissoesPrivadas(Path caminho) throws IOException {
        try {
            Set<PosixFilePermission> permissions = EnumSet.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE
            );
            Files.setPosixFilePermissions(caminho, permissions);
        } catch (UnsupportedOperationException ignored) {
            // Sistemas sem permissões POSIX usam as proteções padrão do sistema de arquivos.
        }
    }
}

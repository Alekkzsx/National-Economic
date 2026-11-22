package dominio.util;

import dominio.aplicacao.AplicacaoFinanceira;
import dominio.cliente.Cliente;
import dominio.aplicacao.ConvencaoContagemDias;
import dominio.cliente.DADOSPESSOAIS;
import dominio.operacao.MultiplasOperacoesFinanceiras;
import dominio.operacao.OperacaoFinanceira;
import dominio.tributacao.RegimeTributario;
import dominio.operacao.TipoOperacao;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Utilitário único de persistência em formato tabular (TSV): escape seguro, escrita
 * atômica com {@code fsync} + permissões POSIX 0600 e codecs de registros {@code C/I/O}.
 */
public final class ArquivoTSV {

    /** Cabeçalho canônico oficial da versão 3. */
    public static final String CABECALHO_V3 = "NATIONAL-ECONOMIC\t3";

    private static final Set<PosixFilePermission> PERMISSOES_0600 =
            PosixFilePermissions.fromString("rw-------");

    /** Callback funcional para gravar o conteúdo do arquivo antes da troca atômica. */
    @FunctionalInterface
    public interface EscritorConteudo {
        void escrever(BufferedWriter writer) throws IOException;
    }

    private ArquivoTSV() {
    }

    // -------------------------------------------------------------------------
    // Escrita atômica e permissões seguras
    // -------------------------------------------------------------------------

    /**
     * Grava atomicamente via arquivo temporário na mesma pasta, {@code fsync}, troca atômica
     * ({@code ATOMIC_MOVE}) e permissão POSIX {@code 0600} para proteger dados cadastrais (LGPD).
     *
     * @param caminho arquivo de destino
     * @param escritor callback que escreve todas as linhas no {@link BufferedWriter}
     * @throws IOException em caso de falha de escrita ou movimentação
     */
    public static void escreverAtomico(Path caminho, EscritorConteudo escritor) throws IOException {
        Objects.requireNonNull(caminho, "Caminho do arquivo é obrigatório.");
        Objects.requireNonNull(escritor, "Escritor de conteúdo é obrigatório.");

        Path absoluto = caminho.toAbsolutePath();
        Path diretorioPai = absoluto.getParent();
        if (diretorioPai != null) {
            Files.createDirectories(diretorioPai);
        }

        // Mantém o temporário no mesmo diretório/filesystem para garantir ATOMIC_MOVE no mesmo inode
        Path pastaTmp = diretorioPai != null ? diretorioPai : Path.of(".");
        Path arquivoTmp = Files.createTempFile(pastaTmp, "." + absoluto.getFileName() + ".", ".tmp");
        try {
            aplicarPermissoes0600(arquivoTmp);
            try (FileOutputStream fos = new FileOutputStream(arquivoTmp.toFile());
                 OutputStreamWriter osw = new OutputStreamWriter(fos, StandardCharsets.UTF_8);
                 BufferedWriter writer = new BufferedWriter(osw)) {
                escritor.escrever(writer);
                writer.flush();
                // Força descarga dos buffers do sistema operacional antes da troca atômica
                fos.getFD().sync();
            }
            aplicarPermissoes0600(arquivoTmp);
            substituirArquivoAtomico(arquivoTmp, absoluto);
            aplicarPermissoes0600(absoluto);
        } finally {
            Files.deleteIfExists(arquivoTmp);
        }
    }

    /** Aplica permissão POSIX 0600 (rw-------) com fallback seguro para sistemas não-POSIX. */
    public static void aplicarPermissoes0600(Path caminho) {
        try {
            Files.setPosixFilePermissions(caminho, PERMISSOES_0600);
        } catch (UnsupportedOperationException | IOException e) {
            try {
                File arquivo = caminho.toFile();
                arquivo.setReadable(false, false);
                arquivo.setWritable(false, false);
                arquivo.setExecutable(false, false);
                arquivo.setReadable(true, true);
                arquivo.setWritable(true, true);
            } catch (SecurityException ignored) {
                // Ambientes restritivos sem alteração de permissões
            }
        }
    }

    /** Move o arquivo temporário para o destino de forma atômica, com fallback seguro. */
    public static void substituirArquivoAtomico(Path origem, Path destino) throws IOException {
        try {
            Files.move(origem, destino, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(origem, destino, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    // -------------------------------------------------------------------------
    // Escape e parsing de linha TSV
    // -------------------------------------------------------------------------

    /** Escapa barra invertida, tabulação e quebras de linha para manter cada registro em 1 linha. */
    public static String escapar(String valor) {
        if (valor == null) {
            return "";
        }
        return valor.replace("\\", "\\\\")
                .replace("\t", "\\t")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }

    /** Converte um campo escapado de volta para o texto original. */
    public static String desescapar(String valor) {
        if (valor == null || valor.isEmpty()) {
            return "";
        }
        return parseRegistro(valor, 0).get(0);
    }

    /** Separa uma linha TSV em campos desescapados, rejeitando escapes inválidos ou truncados. */
    public static List<String> parseRegistro(String linha, int numeroLinha) {
        List<String> campos = new ArrayList<>();
        StringBuilder atual = new StringBuilder();
        boolean emEscape = false;

        for (int i = 0; i < linha.length(); i++) {
            char c = linha.charAt(i);
            if (emEscape) {
                switch (c) {
                    case 't' -> atual.append('\t');
                    case 'n' -> atual.append('\n');
                    case 'r' -> atual.append('\r');
                    case '\\' -> atual.append('\\');
                    default -> throw new IllegalArgumentException(
                            "Sequência de escape inválida '\\" + c + "' na linha " + numeroLinha + ".");
                }
                emEscape = false;
            } else if (c == '\\') {
                emEscape = true;
            } else if (c == '\t') {
                campos.add(atual.toString());
                atual.setLength(0);
            } else {
                atual.append(c);
            }
        }

        if (emEscape) {
            throw new IllegalArgumentException(
                    "Sequência de escape incompleta no final da linha " + numeroLinha + ".");
        }
        campos.add(atual.toString());
        return campos;
    }

    // -------------------------------------------------------------------------
    // Codecs canônicos do formato TSV v3 (C=6 cols, I=26 cols, O=7 cols):
    //   C: tipo \t cpf \t nome \t celular \t agencia \t conta
    //   I: tipo \t cpf \t numero \t nome \t valorAplicado \t taxaMensal \t dataApp \t
    //      saldo \t valorAtualBruto \t dataUltimaAtualizacao \t diasCorridos \t
    //      diasEfetivos \t qtdOps \t convencao \t regime \t tipoRend \t vencimento \t
    //      permanencia \t tipoResgate \t iof \t tipoIR \t valorResgate \t
    //      valorAtualLiquido \t rendAtualBruto \t rendAtualLiquido \t impostoDevido
    //   O: tipo \t cpf \t numeroApp \t data \t valor \t descricao \t tipoOperacao
    // -------------------------------------------------------------------------

    /** Serializa um registro de cliente {@code C} (6 colunas). */
    public static String formatarLinhaCliente(Cliente cliente) {
        DADOSPESSOAIS dados = cliente.getDadosPessoais();
        return String.join("\t",
                "C",
                escapar(dados.getCpf()),
                escapar(dados.getNome()),
                escapar(dados.getCelular()),
                Integer.toString(cliente.getNumeroAgencia()),
                Long.toString(cliente.getNumeroConta())
        );
    }

    /** Reconstrói um {@link Cliente} a partir de um registro {@code C} (6 colunas). */
    public static Cliente desserializarCliente(List<String> campos, int numeroLinha) {
        if (campos == null || campos.size() < 6) {
            throw new IllegalArgumentException(
                    "Registro de cliente corrompido na linha " + numeroLinha
                            + " (esperado 6 campos, obtido " + (campos == null ? 0 : campos.size()) + ").");
        }
        String cpf = ValidacaoDominio.normalizarCpf(campos.get(1));
        String nome = campos.get(2);
        String celular = campos.get(3);
        int agencia = Integer.parseInt(campos.get(4));
        long conta = Long.parseLong(campos.get(5));
        return new Cliente(new DADOSPESSOAIS(nome, cpf, celular), agencia, conta);
    }

    /** Serializa um registro de aplicação financeira {@code I} (26 colunas). */
    public static String formatarLinhaAplicacao(String cpf, AplicacaoFinanceira af) {
        return String.join("\t",
                "I",
                escapar(cpf),
                escapar(af.getNumeroAplicacao()),
                escapar(af.getNomeAplicacao()),
                textoDecimal(af.getValorAplicado()),
                textoDecimal(af.getTaxaMensal()),
                af.getDataAplicacao() != null ? af.getDataAplicacao().toString() : LocalDate.now().toString(),
                textoDecimal(af.getSaldo()),
                textoDecimal(af.getValorAtualBruto()),
                af.getDataUltimaAtualizacao() != null ? af.getDataUltimaAtualizacao().toString() : "",
                Integer.toString(af.getDiasCorridos()),
                Integer.toString(af.getDiasEfetivosCalculo()),
                Integer.toString(af.getQuantidadeOperacoes()),
                af.getConvencaoContagemDias() != null ? af.getConvencaoContagemDias().name() : "COMERCIAL_30_360",
                af.getRegimeTributario() != null ? af.getRegimeTributario().name() : "PADRAO",
                Integer.toString(af.getTipoRendimento()),
                Integer.toString(af.getTempoFinalVencimento()),
                Integer.toString(af.getTempoMinimoPermanencia()),
                Integer.toString(af.getTipoResgate()),
                Integer.toString(af.getIof()),
                Integer.toString(af.getTipoImpostoRenda()),
                textoDecimal(af.getValorResgate()),
                textoDecimal(af.getValorAtualLiquido()),
                textoDecimal(af.getRendimentoAtualBruto()),
                textoDecimal(af.getRendimentoAtualLiquido()),
                textoDecimal(af.getImpostoAtualDevido())
        );
    }

    /**
     * Reconstrói uma {@link MultiplasOperacoesFinanceiras} a partir de um registro {@code I},
     * respeitando a ordem Fase 1 (estrutura/parâmetros) → Fase 2 (grandezas consolidadas).
     */
    public static MultiplasOperacoesFinanceiras desserializarAplicacao(
            List<String> campos, int minCampos, int numeroLinha) {
        if (campos == null || campos.size() < minCampos) {
            throw new IllegalArgumentException(
                    "Registro de aplicação corrompido na linha " + numeroLinha
                            + " (campos insuficientes: " + (campos == null ? 0 : campos.size())
                            + ", mínimo esperado: " + minCampos + ").");
        }

        String numero = campos.get(2);
        String nome = campos.get(3);
        BigDecimal valorAplicado = new BigDecimal(campos.get(4));
        BigDecimal taxaMensal = new BigDecimal(campos.get(5));
        LocalDate dataAplicacao = LocalDate.parse(campos.get(6));

        int tipoRendimento = lerIntOpcional(campos, 15, 0);
        int tempoVencimento = lerIntOpcional(campos, 16, 0);
        int tempoPermanencia = lerIntOpcional(campos, 17, 0);
        int tipoResgate = lerIntOpcional(campos, 18, 0);
        int iof = lerIntOpcional(campos, 19, 0);
        int tipoIR = lerIntOpcional(campos, 20, 0);

        AplicacaoFinanceira af = new AplicacaoFinanceira(
                nome, numero, valorAplicado, taxaMensal,
                tipoRendimento, tempoVencimento, tempoPermanencia,
                tipoResgate, iof, tipoIR, dataAplicacao
        );

        // Fase 1: parâmetros estruturais e contadores (que podem disparar recálculo interno)
        if (presente(campos, 13)) af.setConvencaoContagemDias(ConvencaoContagemDias.valueOf(campos.get(13)));
        if (presente(campos, 14)) af.setRegimeTributario(RegimeTributario.valueOf(campos.get(14)));
        if (presente(campos, 10)) af.setDiasCorridos(Integer.parseInt(campos.get(10)));
        if (presente(campos, 11)) af.setDiasEfetivosCalculo(Integer.parseInt(campos.get(11)));
        if (presente(campos, 9))  af.setDataUltimaAtualizacao(LocalDate.parse(campos.get(9)));
        if (presente(campos, 12)) af.setQuantidadeOperacoes(Integer.parseInt(campos.get(12)));
        if (presente(campos, 21)) af.setValorResgate(new BigDecimal(campos.get(21)));
        if (presente(campos, 7))  af.setSaldo(new BigDecimal(campos.get(7)));

        // Fase 2: grandezas patrimoniais e fiscais consolidadas preservadas exatamente como salvas
        if (presente(campos, 23)) af.setRendimentoAtualBruto(new BigDecimal(campos.get(23)));
        if (presente(campos, 24)) af.setRendimentoAtualLiquido(new BigDecimal(campos.get(24)));
        if (presente(campos, 25)) af.setImpostoAtualDevido(new BigDecimal(campos.get(25)));
        if (presente(campos, 8))  af.setValorAtualBruto(new BigDecimal(campos.get(8)));
        if (presente(campos, 22)) af.setValorAtualLiquido(new BigDecimal(campos.get(22)));

        return new MultiplasOperacoesFinanceiras(af);
    }

    /** Serializa um registro de operação financeira {@code O} (7 colunas). */
    public static String formatarLinhaOperacao(String cpf, String numeroAplicacao, OperacaoFinanceira op) {
        return String.join("\t",
                "O",
                escapar(cpf),
                escapar(numeroAplicacao),
                op.getData() != null ? op.getData().toString() : LocalDate.now().toString(),
                textoDecimal(op.getValor()),
                escapar(op.getDescricao()),
                op.getTipo() != null ? op.getTipo().name() : TipoOperacao.DEPOSITO.name()
        );
    }

    /** Reconstrói uma {@link OperacaoFinanceira} a partir de um registro {@code O} (7 colunas). */
    public static OperacaoFinanceira desserializarOperacao(List<String> campos, int numeroLinha) {
        if (campos == null || campos.size() < 7) {
            throw new IllegalArgumentException(
                    "Registro de operação corrompido na linha " + numeroLinha
                            + " (campos insuficientes: " + (campos == null ? 0 : campos.size()) + ").");
        }
        LocalDate data = LocalDate.parse(campos.get(3));
        BigDecimal valor = new BigDecimal(campos.get(4));
        String descricao = campos.get(5);
        TipoOperacao tipo = TipoOperacao.deTexto(campos.get(6));
        return new OperacaoFinanceira(data, valor, descricao, tipo);
    }

    private static String textoDecimal(BigDecimal valor) {
        return valor != null ? valor.toPlainString() : "0.00";
    }

    private static boolean presente(List<String> campos, int indice) {
        return campos.size() > indice && !campos.get(indice).isBlank();
    }

    private static int lerIntOpcional(List<String> campos, int indice, int padrao) {
        return presente(campos, indice) ? Integer.parseInt(campos.get(indice)) : padrao;
    }
}

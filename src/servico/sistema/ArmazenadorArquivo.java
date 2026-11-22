package servico.sistema;

import dominio.cliente.Cliente;
import dominio.operacao.MultiplasOperacoesFinanceiras;
import dominio.operacao.OperacaoFinanceira;
import dominio.util.ArquivoTSV;
import dominio.util.ValidacaoDominio;
import servico.abstracoes.ArmazenadorBase;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Persistência global do {@link SGI} em formato TSV versionado ({@code NATIONAL-ECONOMIC\t<1..3>}).
 *
 * <p>Layout oficial v3 composto por registros {@code C} (cliente), {@code I} (aplicação,
 * 26 colunas) e {@code O} (operação), com verificação estrita de integridade comparando a
 * quantidade de operações declarada em {@code I} com os registros {@code O} restaurados.
 * Estende {@link ArmazenadorBase} e delega escape, {@code fsync}, troca atômica e permissões
 * POSIX 0600 para {@link ArquivoTSV}.</p>
 */
public class ArmazenadorArquivo extends ArmazenadorBase<SGI> {

    public static final String CABECALHO_V3 = ArquivoTSV.CABECALHO_V3;
    public static final String CABECALHO_V2 = "NATIONAL-ECONOMIC\t2";
    public static final String CABECALHO_V1 = "NATIONAL-ECONOMIC\t1";
    public static final Path CAMINHO_PADRAO = Path.of("data/sgi.db");

    private static final String PREFIXO_CABECALHO = "NATIONAL-ECONOMIC\t";
    private static final int VERSAO_MINIMA = 1;
    private static final int VERSAO_MAXIMA = 3;

    /** Cria armazenador apontando para {@link #CAMINHO_PADRAO}. */
    public ArmazenadorArquivo() {
        super(CAMINHO_PADRAO);
    }

    /** Cria armazenador apontando para o caminho {@link Path} informado. */
    public ArmazenadorArquivo(Path caminhoPadrao) {
        super(caminhoPadrao);
    }

    /** Cria armazenador apontando para o caminho em texto informado. */
    public ArmazenadorArquivo(String caminhoPadrao) {
        super(caminhoPadrao);
    }

    // -------------------------------------------------------------------------
    // Gravação atômica
    // -------------------------------------------------------------------------

    @Override
    public void salvar(Path caminho, SGI sgi) throws IOException {
        Objects.requireNonNull(sgi, "Instância de SGI não pode ser nula.");
        salvar(caminho, sgi.getClientes());
    }

    public void salvar(Path caminho, Collection<Cliente> clientes) throws IOException {
        Objects.requireNonNull(caminho, "Caminho não pode ser nulo.");
        Objects.requireNonNull(clientes, "Coleção de clientes não pode ser nula.");

        ArquivoTSV.escreverAtomico(caminho, writer -> {
            writer.write(CABECALHO_V3);
            writer.newLine();
            for (Cliente cliente : clientes) {
                gravarRegistroCliente(writer, cliente);
                for (MultiplasOperacoesFinanceiras multi : cliente.getAplicacoes()) {
                    gravarRegistroAplicacao(writer, cliente, multi);
                    gravarRegistrosOperacoes(writer, cliente, multi);
                }
            }
        });
    }

    public static void salvarDados(Path caminho, SGI sgi) throws IOException {
        new ArmazenadorArquivo().salvar(caminho, sgi);
    }

    public static void salvarDados(String caminho, SGI sgi) throws IOException {
        new ArmazenadorArquivo().salvar(caminho, sgi);
    }

    public static void salvarDados(Path caminho, Collection<Cliente> clientes) throws IOException {
        new ArmazenadorArquivo().salvar(caminho, clientes);
    }

    // -------------------------------------------------------------------------
    // Leitura e reconstrução com validação rigorosa (versões 1 a 3)
    // -------------------------------------------------------------------------

    @Override
    public SGI carregar(Path caminho) throws IOException {
        exigirArquivoExistente(caminho);
        SGI sgi = new SGI();
        for (Cliente cliente : carregarListaClientes(caminho)) {
            sgi.cadastrarCliente(cliente);
        }
        return sgi;
    }

    public List<Cliente> carregarClientes(Path caminho) throws IOException {
        return carregarListaClientes(caminho);
    }

    public static SGI carregarDados(Path caminho) throws IOException {
        return new ArmazenadorArquivo().carregar(caminho);
    }

    public static SGI carregarDados(String caminho) throws IOException {
        return new ArmazenadorArquivo().carregar(caminho);
    }

    /** Lê todos os clientes, aplicações e operações de um arquivo TSV validando integridade referencial. */
    public static List<Cliente> carregarListaClientes(Path caminho) throws IOException {
        exigirArquivoExistente(caminho);

        try (BufferedReader reader = Files.newBufferedReader(caminho, StandardCharsets.UTF_8)) {
            int versao = validarCabecalho(reader.readLine());
            int minCamposAplicacao = switch (versao) {
                case 1 -> 15;
                case 2 -> 21;
                default -> 23;
            };
            ContextoLeitura contexto = new ContextoLeitura(minCamposAplicacao);

            String linha;
            int numeroLinha = 1;
            while ((linha = reader.readLine()) != null) {
                numeroLinha++;
                linha = linha.trim();
                if (linha.isEmpty()) continue;

                List<String> campos = ArquivoTSV.parseRegistro(linha, numeroLinha);
                switch (campos.get(0)) {
                    case "C" -> lerRegistroCliente(campos, numeroLinha, contexto);
                    case "I" -> lerRegistroAplicacao(campos, numeroLinha, contexto);
                    case "O" -> lerRegistroOperacao(campos, numeroLinha, contexto);
                    default -> throw new IllegalArgumentException(
                            "Tipo de registro desconhecido na linha " + numeroLinha + ": '" + campos.get(0) + "'.");
                }
            }

            validarContagemOperacoes(contexto.operacoesDeclaradas, contexto.operacoesRestauradas);
            return new ArrayList<>(contexto.clientesPorCpf.values());
        }
    }

    private static final class ContextoLeitura {
        final int minCamposAplicacao;
        final Map<String, Cliente> clientesPorCpf = new LinkedHashMap<>();
        final Map<String, MultiplasOperacoesFinanceiras> aplicacoesPorNumero = new LinkedHashMap<>();
        final Map<String, Integer> operacoesDeclaradas = new LinkedHashMap<>();
        final Map<String, Integer> operacoesRestauradas = new LinkedHashMap<>();
        Cliente clienteAtual;
        MultiplasOperacoesFinanceiras aplicacaoAtual;

        ContextoLeitura(int minCamposAplicacao) {
            this.minCamposAplicacao = minCamposAplicacao;
        }
    }

    private static int validarCabecalho(String cabecalho) {
        if (cabecalho == null || cabecalho.trim().isEmpty()) {
            throw new IllegalArgumentException("Cabeçalho de arquivo ausente ou arquivo vazio.");
        }
        String limpo = cabecalho.trim();
        if (!limpo.startsWith(PREFIXO_CABECALHO)) {
            throw new IllegalArgumentException("Cabeçalho de arquivo inválido ou formato incompatível: " + cabecalho);
        }
        String[] partes = limpo.split("\t", -1);
        if (partes.length < 2) {
            throw new IllegalArgumentException("Versão ausente no cabeçalho do arquivo.");
        }
        try {
            int versao = Integer.parseInt(partes[1].trim());
            if (versao < VERSAO_MINIMA || versao > VERSAO_MAXIMA) {
                throw new IllegalArgumentException("Versão de formato incompatível ou desconhecida: " + versao);
            }
            return versao;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Número de versão inválido no cabeçalho: " + partes[1], e);
        }
    }

    private static void lerRegistroCliente(List<String> campos, int numeroLinha, ContextoLeitura ctx) {
        Cliente cliente = ArquivoTSV.desserializarCliente(campos, numeroLinha);
        String cpf = cliente.getDadosPessoais().getCpf();
        if (ctx.clientesPorCpf.containsKey(cpf)) {
            throw new IllegalArgumentException("CPF duplicado na linha " + numeroLinha + ": " + cpf);
        }
        if (ctx.clientesPorCpf.size() >= SGI.LIMITE_CLIENTES) {
            throw new IllegalStateException("Limite regulamentar de 100 clientes atingido.");
        }
        ctx.clientesPorCpf.put(cpf, cliente);
        ctx.clienteAtual = cliente;
        ctx.aplicacaoAtual = null;
    }

    private static void lerRegistroAplicacao(List<String> campos, int numeroLinha, ContextoLeitura ctx) {
        if (ctx.clienteAtual == null) {
            throw new IllegalArgumentException(
                    "Registro de aplicação antes do registro de cliente na linha " + numeroLinha + ".");
        }
        if (campos.size() < ctx.minCamposAplicacao) {
            throw new IllegalArgumentException(
                    "Registro de aplicação corrompido na linha " + numeroLinha
                            + " (campos insuficientes: " + campos.size()
                            + ", mínimo " + ctx.minCamposAplicacao + ").");
        }
        String cpfNaLinha = ValidacaoDominio.normalizarCpf(campos.get(1));
        if (!ctx.clienteAtual.getDadosPessoais().getCpf().equals(cpfNaLinha)) {
            throw new IllegalArgumentException("Aplicação na linha " + numeroLinha + " possui CPF divergente.");
        }
        String numeroAplicacao = campos.get(2);
        if (ctx.aplicacoesPorNumero.containsKey(numeroAplicacao)) {
            throw new IllegalArgumentException(
                    "Número de aplicação duplicado no sistema na linha " + numeroLinha + ": " + numeroAplicacao);
        }

        MultiplasOperacoesFinanceiras multi = ArquivoTSV.desserializarAplicacao(
                campos, ctx.minCamposAplicacao, numeroLinha);
        int declaradas = multi.getAplicacao().getQuantidadeOperacoes();
        ctx.clienteAtual.adicionarAplicacao(multi);

        ctx.aplicacoesPorNumero.put(numeroAplicacao, multi);
        ctx.operacoesDeclaradas.put(numeroAplicacao, declaradas);
        ctx.operacoesRestauradas.put(numeroAplicacao, 0);
        ctx.aplicacaoAtual = multi;
    }

    private static void lerRegistroOperacao(List<String> campos, int numeroLinha, ContextoLeitura ctx) {
        if (ctx.clienteAtual == null) {
            throw new IllegalArgumentException("Registro de operação sem cliente associado na linha " + numeroLinha + ".");
        }
        if (ctx.aplicacaoAtual == null) {
            throw new IllegalArgumentException("Registro de operação sem aplicação associada na linha " + numeroLinha + ".");
        }
        if (campos.size() < 7) {
            throw new IllegalArgumentException("Registro de operação corrompido na linha " + numeroLinha + ".");
        }
        String cpfNaLinha = ValidacaoDominio.normalizarCpf(campos.get(1));
        if (!ctx.clienteAtual.getDadosPessoais().getCpf().equals(cpfNaLinha)) {
            throw new IllegalArgumentException("Operação na linha " + numeroLinha + " possui CPF diferente do cliente ativo.");
        }
        String numAppNaLinha = campos.get(2);
        if (!ctx.aplicacaoAtual.getAplicacao().getNumeroAplicacao().equals(numAppNaLinha)) {
            throw new IllegalArgumentException("Operação na linha " + numeroLinha + " diverge da aplicação ativa.");
        }

        OperacaoFinanceira op = ArquivoTSV.desserializarOperacao(campos, numeroLinha);
        if (op.getData().isBefore(ctx.aplicacaoAtual.getAplicacao().getDataAplicacao())) {
            throw new IllegalArgumentException(
                    "Data da operação na linha " + numeroLinha + " é anterior à data de início da aplicação.");
        }
        ctx.aplicacaoAtual.vincularOperacaoRestaurada(op);
        ctx.operacoesRestauradas.merge(numAppNaLinha, 1, Integer::sum);
    }

    private static void validarContagemOperacoes(Map<String, Integer> declaradas, Map<String, Integer> restauradas) {
        for (Map.Entry<String, Integer> entry : declaradas.entrySet()) {
            int esperadas = entry.getValue();
            int obtidas = restauradas.getOrDefault(entry.getKey(), 0);
            if (esperadas > 0 && esperadas != obtidas) {
                throw new IllegalArgumentException(
                        "Quantidade de operações inconsistente na aplicação " + entry.getKey()
                                + ": declarado=" + esperadas + ", encontrado=" + obtidas + ".");
            }
        }
    }

    // -------------------------------------------------------------------------
    // Delegações públicas compatíveis com a API utilitária
    // -------------------------------------------------------------------------

    /** Delega a tokenização TSV com tratamento de escapes para {@link ArquivoTSV}. */
    public static List<String> parseRegistro(String linha, int numeroLinha) {
        return ArquivoTSV.parseRegistro(linha, numeroLinha);
    }

    /** Delega o escape de caracteres de controle TSV para {@link ArquivoTSV}. */
    public static String escapar(String valor) {
        return ArquivoTSV.escapar(valor);
    }

    /** Delega o desescape de um campo TSV para {@link ArquivoTSV}. */
    public static String desescapar(String valor) {
        return ArquivoTSV.desescapar(valor);
    }

    /** Delega a restrição de permissões POSIX 0600 para {@link ArquivoTSV}. */
    public static void aplicarPermissoes0600(Path caminho) {
        ArquivoTSV.aplicarPermissoes0600(caminho);
    }

    /** Delega a substituição atômica de arquivo para {@link ArquivoTSV}. */
    public static void substituirArquivoAtomico(Path origem, Path destino) throws IOException {
        ArquivoTSV.substituirArquivoAtomico(origem, destino);
    }

    /** Delega a normalização de CPF (11 dígitos) para {@link ValidacaoDominio}. */
    public static String normalizarCpf(String cpf) {
        return ValidacaoDominio.normalizarCpf(cpf);
    }

    // -------------------------------------------------------------------------
    // Ganchos protegidos para especialização por subclasses
    // -------------------------------------------------------------------------

    protected void gravarRegistroCliente(BufferedWriter writer, Cliente cliente) throws IOException {
        writer.write(ArquivoTSV.formatarLinhaCliente(cliente));
        writer.newLine();
    }

    protected void gravarRegistroAplicacao(BufferedWriter writer, Cliente cliente,
                                           MultiplasOperacoesFinanceiras multi) throws IOException {
        if (multi.getAplicacao() == null) return;
        writer.write(ArquivoTSV.formatarLinhaAplicacao(
                cliente.getDadosPessoais().getCpf(), multi.getAplicacao()));
        writer.newLine();
    }

    protected void gravarRegistrosOperacoes(BufferedWriter writer, Cliente cliente,
                                            MultiplasOperacoesFinanceiras multi) throws IOException {
        String cpf = cliente.getDadosPessoais().getCpf();
        String numApp = multi.getAplicacao() != null ? multi.getAplicacao().getNumeroAplicacao() : "";
        for (OperacaoFinanceira op : multi.getOperacoes()) {
            writer.write(ArquivoTSV.formatarLinhaOperacao(cpf, numApp, op));
            writer.newLine();
        }
    }
}

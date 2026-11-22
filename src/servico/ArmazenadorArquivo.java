package servico;

import dominio.Cliente;
import dominio.MultiplasOperacoesFinanceiras;
import dominio.OperacaoFinanceira;
import dominio.util.ArquivoTSV;
import dominio.util.ValidacaoDominio;

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
 * Persistência global do {@link SGI} em formato TSV versionado (v3, leitura retrocompatível v1–v3).
 *
 * <p>Delega escape, codecs de linha {@code C/I/O}, {@code fsync}, troca atômica e permissões
 * POSIX 0600 para {@link ArquivoTSV}, mantendo ganchos {@code protected} para herança.</p>
 */
public class ArmazenadorArquivo {

    public static final String CABECALHO_V3 = ArquivoTSV.CABECALHO_V3;
    public static final String CABECALHO_V2 = "NATIONAL-ECONOMIC\t2";
    public static final String CABECALHO_V1 = "NATIONAL-ECONOMIC\t1";
    public static final Path CAMINHO_PADRAO = Path.of("data/sgi.db");

    private final Path caminhoPadrao;

    public ArmazenadorArquivo() {
        this(CAMINHO_PADRAO);
    }

    public ArmazenadorArquivo(Path caminhoPadrao) {
        this.caminhoPadrao = Objects.requireNonNull(caminhoPadrao, "Caminho padrão não pode ser nulo.");
    }

    public ArmazenadorArquivo(String caminhoPadrao) {
        this(Path.of(Objects.requireNonNull(caminhoPadrao, "Caminho padrão não pode ser nulo.")));
    }

    public Path getCaminhoPadrao() {
        return caminhoPadrao;
    }

    // -------------------------------------------------------------------------
    // Gravação atômica
    // -------------------------------------------------------------------------

    public void salvar(SGI sgi) throws IOException {
        Objects.requireNonNull(sgi, "Instância de SGI não pode ser nula.");
        salvar(this.caminhoPadrao, sgi.getClientes());
    }

    public void salvar(Path caminho, SGI sgi) throws IOException {
        Objects.requireNonNull(sgi, "Instância de SGI não pode ser nula.");
        salvar(caminho, sgi.getClientes());
    }

    public void salvar(String caminho, SGI sgi) throws IOException {
        Objects.requireNonNull(caminho, "Caminho não pode ser nulo.");
        salvar(Path.of(caminho), sgi);
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

    public SGI carregar() throws IOException {
        return carregar(this.caminhoPadrao);
    }

    public SGI carregar(Path caminho) throws IOException {
        Objects.requireNonNull(caminho, "Caminho do arquivo não pode ser nulo.");
        if (!Files.exists(caminho)) {
            throw new IllegalArgumentException("Arquivo de dados não encontrado: " + caminho);
        }
        SGI sgi = new SGI();
        for (Cliente cliente : carregarListaClientes(caminho)) {
            sgi.cadastrarCliente(cliente);
        }
        return sgi;
    }

    public SGI carregar(String caminho) throws IOException {
        Objects.requireNonNull(caminho, "Caminho do arquivo não pode ser nulo.");
        return carregar(Path.of(caminho));
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

    public static List<Cliente> carregarListaClientes(Path caminho) throws IOException {
        Objects.requireNonNull(caminho, "Caminho do arquivo não pode ser nulo.");
        if (!Files.exists(caminho)) {
            throw new IllegalArgumentException("Arquivo de dados não encontrado: " + caminho);
        }

        Map<String, Cliente> clientesPorCpf = new LinkedHashMap<>();
        Map<String, MultiplasOperacoesFinanceiras> aplicacoesPorNumero = new LinkedHashMap<>();
        Map<String, Integer> operacoesDeclaradas = new LinkedHashMap<>();
        Map<String, Integer> operacoesRestauradas = new LinkedHashMap<>();

        try (BufferedReader reader = Files.newBufferedReader(caminho, StandardCharsets.UTF_8)) {
            int versao = validarCabecalho(reader.readLine());
            int minCamposAplicacao = switch (versao) {
                case 1 -> 15;
                case 2 -> 21;
                default -> 23;
            };

            Cliente clienteAtual = null;
            MultiplasOperacoesFinanceiras aplicacaoAtual = null;
            String linha;
            int numeroLinha = 1;

            while ((linha = reader.readLine()) != null) {
                numeroLinha++;
                linha = linha.trim();
                if (linha.isEmpty()) continue;

                List<String> campos = ArquivoTSV.parseRegistro(linha, numeroLinha);
                switch (campos.get(0)) {
                    case "C" -> {
                        clienteAtual = lerRegistroCliente(campos, numeroLinha, clientesPorCpf);
                        aplicacaoAtual = null;
                    }
                    case "I" -> aplicacaoAtual = lerRegistroAplicacao(
                            campos, numeroLinha, minCamposAplicacao, clienteAtual,
                            aplicacoesPorNumero, operacoesDeclaradas, operacoesRestauradas);
                    case "O" -> lerRegistroOperacao(
                            campos, numeroLinha, clienteAtual, aplicacaoAtual, operacoesRestauradas);
                    default -> throw new IllegalArgumentException(
                            "Tipo de registro desconhecido na linha " + numeroLinha + ": '" + campos.get(0) + "'.");
                }
            }
        }

        validarContagemOperacoes(operacoesDeclaradas, operacoesRestauradas);
        return new ArrayList<>(clientesPorCpf.values());
    }

    private static int validarCabecalho(String cabecalho) {
        if (cabecalho == null || cabecalho.trim().isEmpty()) {
            throw new IllegalArgumentException("Cabeçalho de arquivo ausente ou arquivo vazio.");
        }
        String limpo = cabecalho.trim();
        if (!limpo.startsWith("NATIONAL-ECONOMIC\t")) {
            throw new IllegalArgumentException("Cabeçalho de arquivo inválido ou formato incompatível: " + cabecalho);
        }
        String[] partes = limpo.split("\t", -1);
        if (partes.length < 2) {
            throw new IllegalArgumentException("Versão ausente no cabeçalho do arquivo.");
        }
        try {
            int versao = Integer.parseInt(partes[1].trim());
            if (versao < 1 || versao > 3) {
                throw new IllegalArgumentException("Versão de formato incompatível ou desconhecida: " + versao);
            }
            return versao;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Número de versão inválido no cabeçalho: " + partes[1], e);
        }
    }

    private static Cliente lerRegistroCliente(List<String> campos, int numeroLinha,
                                              Map<String, Cliente> clientesPorCpf) {
        Cliente cliente = ArquivoTSV.desserializarCliente(campos, numeroLinha);
        String cpf = cliente.getDadosPessoais().getCpf();
        if (clientesPorCpf.containsKey(cpf)) {
            throw new IllegalArgumentException("CPF duplicado na linha " + numeroLinha + ": " + cpf);
        }
        if (clientesPorCpf.size() >= SGI.LIMITE_CLIENTES) {
            throw new IllegalStateException("Limite regulamentar de 100 clientes atingido.");
        }
        clientesPorCpf.put(cpf, cliente);
        return cliente;
    }

    private static MultiplasOperacoesFinanceiras lerRegistroAplicacao(
            List<String> campos, int numeroLinha, int minCampos, Cliente clienteAtual,
            Map<String, MultiplasOperacoesFinanceiras> aplicacoesPorNumero,
            Map<String, Integer> operacoesDeclaradas, Map<String, Integer> operacoesRestauradas) {
        if (clienteAtual == null) {
            throw new IllegalArgumentException(
                    "Registro de aplicação antes do registro de cliente na linha " + numeroLinha + ".");
        }
        if (campos.size() < minCampos) {
            throw new IllegalArgumentException(
                    "Registro de aplicação corrompido na linha " + numeroLinha
                            + " (campos insuficientes: " + campos.size() + ", mínimo " + minCampos + ").");
        }
        String cpfNaLinha = ValidacaoDominio.normalizarCpf(campos.get(1));
        if (!clienteAtual.getDadosPessoais().getCpf().equals(cpfNaLinha)) {
            throw new IllegalArgumentException("Aplicação na linha " + numeroLinha + " possui CPF divergente.");
        }
        String numeroAplicacao = campos.get(2);
        if (aplicacoesPorNumero.containsKey(numeroAplicacao)) {
            throw new IllegalArgumentException(
                    "Número de aplicação duplicado no sistema na linha " + numeroLinha + ": " + numeroAplicacao);
        }

        MultiplasOperacoesFinanceiras multi = ArquivoTSV.desserializarAplicacao(campos, minCampos, numeroLinha);
        int declaradas = multi.getAplicacao().getQuantidadeOperacoes();
        clienteAtual.adicionarAplicacao(multi);

        aplicacoesPorNumero.put(numeroAplicacao, multi);
        operacoesDeclaradas.put(numeroAplicacao, declaradas);
        operacoesRestauradas.put(numeroAplicacao, 0);
        return multi;
    }

    private static void lerRegistroOperacao(
            List<String> campos, int numeroLinha, Cliente clienteAtual,
            MultiplasOperacoesFinanceiras aplicacaoAtual, Map<String, Integer> operacoesRestauradas) {
        if (clienteAtual == null) {
            throw new IllegalArgumentException("Registro de operação sem cliente associado na linha " + numeroLinha + ".");
        }
        if (aplicacaoAtual == null) {
            throw new IllegalArgumentException("Registro de operação sem aplicação associada na linha " + numeroLinha + ".");
        }
        if (campos.size() < 7) {
            throw new IllegalArgumentException("Registro de operação corrompido na linha " + numeroLinha + ".");
        }
        String cpfNaLinha = ValidacaoDominio.normalizarCpf(campos.get(1));
        if (!clienteAtual.getDadosPessoais().getCpf().equals(cpfNaLinha)) {
            throw new IllegalArgumentException("Operação na linha " + numeroLinha + " possui CPF diferente do cliente ativo.");
        }
        String numAppNaLinha = campos.get(2);
        if (!aplicacaoAtual.getAplicacao().getNumeroAplicacao().equals(numAppNaLinha)) {
            throw new IllegalArgumentException("Operação na linha " + numeroLinha + " diverge da aplicação ativa.");
        }

        OperacaoFinanceira op = ArquivoTSV.desserializarOperacao(campos, numeroLinha);
        if (op.getData().isBefore(aplicacaoAtual.getAplicacao().getDataAplicacao())) {
            throw new IllegalArgumentException(
                    "Data da operação na linha " + numeroLinha + " é anterior à data de início da aplicação.");
        }
        aplicacaoAtual.vincularOperacaoRestaurada(op);
        operacoesRestauradas.merge(numAppNaLinha, 1, Integer::sum);
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

    public static List<String> parseRegistro(String linha, int numeroLinha) {
        return ArquivoTSV.parseRegistro(linha, numeroLinha);
    }

    public static String escapar(String valor) {
        return ArquivoTSV.escapar(valor);
    }

    public static String desescapar(String valor) {
        return ArquivoTSV.desescapar(valor);
    }

    public static void aplicarPermissoes0600(Path caminho) {
        ArquivoTSV.aplicarPermissoes0600(caminho);
    }

    public static void substituirArquivoAtomico(Path origem, Path destino) throws IOException {
        ArquivoTSV.substituirArquivoAtomico(origem, destino);
    }

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

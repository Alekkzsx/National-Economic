package servico;

import dominio.AplicacaoFinanceira;
import dominio.Cliente;
import dominio.ConvencaoContagemDias;
import dominio.DADOSPESSOAIS;
import dominio.MultiplasOperacoesFinanceiras;
import dominio.OperacaoFinanceira;
import dominio.RegimeTributario;
import dominio.TipoOperacao;

import java.io.BufferedReader;
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
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Componente de persistência e armazenamento atômico do Sistema de Gestão de Investimentos (SGI).
 *
 * <p>Esta classe coordena a gravação atômica segura e a restauração consistente do estado
 * contábil do sistema a partir de arquivos no formato canônico tabular versionado (TSV v3).</p>
 *
 * <p>Principais garantias operacionais e de segurança:</p>
 * <ul>
 *   <li><b>Atomicidade (ACID)</b>: Gravação em arquivo temporário {@code .tmp} no mesmo diretório,
 *       {@code flush()}, sincronização física em disco com {@code fsync} e renomeação atômica via
 *       {@link StandardCopyOption#ATOMIC_MOVE};</li>
 *   <li><b>Segurança POSIX 0600</b>: Permissões estritas de leitura e escrita exclusivas do
 *       proprietário ({@code rw-------}) com fallback seguro em sistemas não-POSIX;</li>
 *   <li><b>Hierarquia Canônica</b>: Cabeçalho canônico {@code "NATIONAL-ECONOMIC\t3\n"},
 *       registros de cliente ({@code C}, 6 colunas), aplicações ({@code I}, 26 colunas estendidas)
 *       e operações ({@code O}, 7 colunas);</li>
 *   <li><b>Validação Defensiva</b>: Rejeição imediata de arquivos corrompidos, cabeçalhos inválidos,
 *       linhas fora de ordem ou registros truncados através de {@link IllegalArgumentException};</li>
 *   <li><b>Escapamento Bidirecional</b>: Tratamento imune a injeções em caracteres especiais
 *       ({@code \t}, {@code \n}, {@code \r}, {@code \\}).</li>
 * </ul>
 *
 * <p>A classe é pública e passível de herança (não-final), provendo ganchos protegidos para
 * especialização por subclasses.</p>
 */
public class ArmazenadorArquivo {

    /** Cabeçalho canônico oficial para gravação na versão 3. */
    public static final String CABECALHO_V3 = "NATIONAL-ECONOMIC\t3";

    /** Cabeçalho legado de versão 2. */
    public static final String CABECALHO_V2 = "NATIONAL-ECONOMIC\t2";

    /** Cabeçalho legado de versão 1. */
    public static final String CABECALHO_V1 = "NATIONAL-ECONOMIC\t1";

    /** Caminho padrão de persistência da base de dados do SGI. */
    public static final Path CAMINHO_PADRAO = Path.of("data/sgi.db");

    /** Permissões estritas POSIX 0600 (rw-------): somente leitura e escrita pelo proprietário. */
    private static final Set<PosixFilePermission> PERMISSOES_0600 =
            PosixFilePermissions.fromString("rw-------");

    /** Caminho de persistência associado a esta instância do armazenador. */
    private final Path caminhoPadrao;

    // =========================================================================
    // Construtores
    // =========================================================================

    /**
     * Construtor padrão que vincula o armazenador ao caminho padrão {@code data/sgi.db}.
     */
    public ArmazenadorArquivo() {
        this(CAMINHO_PADRAO);
    }

    /**
     * Construtor parametrizado com caminho customizado do arquivo de banco de dados.
     *
     * @param caminhoPadrao rota do arquivo de persistência (não nulo)
     * @throws NullPointerException se o caminho for nulo
     */
    public ArmazenadorArquivo(Path caminhoPadrao) {
        this.caminhoPadrao = Objects.requireNonNull(caminhoPadrao, "Caminho padrão não pode ser nulo.");
    }

    /**
     * Sobrecarga de construtor aceitando caminho em representação {@link String}.
     *
     * @param caminhoPadrao texto do caminho do arquivo (não nulo)
     * @throws NullPointerException se a string for nula
     */
    public ArmazenadorArquivo(String caminhoPadrao) {
        this(Path.of(Objects.requireNonNull(caminhoPadrao, "Caminho padrão não pode ser nulo.")));
    }

    /**
     * Retorna o caminho de persistência configurado para esta instância.
     *
     * @return o {@link Path} do arquivo de banco de dados
     */
    public Path getCaminhoPadrao() {
        return caminhoPadrao;
    }

    // =========================================================================
    // Métodos de Gravação (Salvar / Escrita Atômica)
    // =========================================================================

    /**
     * Grava o estado contábil completo do SGI no caminho configurado nesta instância.
     *
     * @param sgi instância do SGI contendo os clientes e aplicações
     * @throws IOException se houver erro de entrada/saída durante a gravação
     * @throws NullPointerException se o SGI for nulo
     */
    public void salvar(SGI sgi) throws IOException {
        Objects.requireNonNull(sgi, "Instância de SGI não pode ser nula.");
        salvar(this.caminhoPadrao, sgi.getClientes());
    }

    /**
     * Grava o estado contábil do SGI no caminho especificado em {@link Path}.
     *
     * @param caminho rota do arquivo de destino
     * @param sgi instância do SGI contendo os dados
     * @throws IOException se houver erro de gravação
     * @throws NullPointerException se qualquer argumento for nulo
     */
    public void salvar(Path caminho, SGI sgi) throws IOException {
        Objects.requireNonNull(sgi, "Instância de SGI não pode ser nula.");
        salvar(caminho, sgi.getClientes());
    }

    /**
     * Sobrecarga de gravação do SGI aceitando representação textual da rota.
     *
     * @param caminho caminho do arquivo em texto
     * @param sgi instância do SGI
     * @throws IOException se ocorrer falha na gravação
     * @throws NullPointerException se algum argumento for nulo
     */
    public void salvar(String caminho, SGI sgi) throws IOException {
        Objects.requireNonNull(caminho, "Caminho não pode ser nulo.");
        salvar(Path.of(caminho), sgi);
    }

    /**
     * Ponto central de gravação atômica a partir de uma coleção de clientes.
     *
     * <p>Aplica o padrão rigoroso de escrita em arquivo temporário na mesma partição,
     * aplicação de permissões POSIX 0600, sincronização física via {@code fsync} e
     * renomeação atômica.</p>
     *
     * @param caminho rota do arquivo de destino
     * @param clientes coleção de clientes a serializar
     * @throws IOException em caso de falha de E/S
     * @throws NullPointerException se qualquer parâmetro for nulo
     */
    public void salvar(Path caminho, Collection<Cliente> clientes) throws IOException {
        Objects.requireNonNull(caminho, "Caminho não pode ser nulo.");
        Objects.requireNonNull(clientes, "Coleção de clientes não pode ser nula.");

        Path absoluto = caminho.toAbsolutePath();
        Path diretorioPai = absoluto.getParent();
        if (diretorioPai != null) {
            Files.createDirectories(diretorioPai);
        }

        Path arquivoTemporario = absoluto.resolveSibling(absoluto.getFileName() + ".tmp");

        try {
            aplicarPermissoes0600(arquivoTemporario);

            try (FileOutputStream fos = new FileOutputStream(arquivoTemporario.toFile());
                 OutputStreamWriter osw = new OutputStreamWriter(fos, StandardCharsets.UTF_8);
                 BufferedWriter writer = new BufferedWriter(osw)) {

                writer.write(CABECALHO_V3);
                writer.newLine();

                for (Cliente cliente : clientes) {
                    gravarRegistroCliente(writer, cliente);
                    for (MultiplasOperacoesFinanceiras multi : cliente.getAplicacoes()) {
                        gravarRegistroAplicacao(writer, cliente, multi);
                        gravarRegistrosOperacoes(writer, cliente, multi);
                    }
                }

                writer.flush();
                fos.getFD().sync();
            }

            aplicarPermissoes0600(arquivoTemporario);
            substituirArquivoAtomico(arquivoTemporario, absoluto);
            aplicarPermissoes0600(absoluto);
        } finally {
            Files.deleteIfExists(arquivoTemporario);
        }
    }

    /**
     * Método estático de conveniência para salvar dados do SGI em rota especificada.
     *
     * @param caminho rota do arquivo
     * @param sgi instância contendo clientes
     * @throws IOException se falhar a gravação
     */
    public static void salvarDados(Path caminho, SGI sgi) throws IOException {
        new ArmazenadorArquivo().salvar(caminho, sgi);
    }

    /**
     * Sobrecarga estática aceitando caminho em texto.
     *
     * @param caminho texto da rota
     * @param sgi instância do SGI
     * @throws IOException se falhar a gravação
     */
    public static void salvarDados(String caminho, SGI sgi) throws IOException {
        new ArmazenadorArquivo().salvar(caminho, sgi);
    }

    /**
     * Sobrecarga estática para salvar diretamente uma coleção de clientes.
     *
     * @param caminho rota do arquivo
     * @param clientes lista de clientes
     * @throws IOException se houver erro de E/S
     */
    public static void salvarDados(Path caminho, Collection<Cliente> clientes) throws IOException {
        new ArmazenadorArquivo().salvar(caminho, clientes);
    }

    // =========================================================================
    // Métodos de Carregamento (Carregar / Leitura e Restauração)
    // =========================================================================

    /**
     * Carrega e reconstitui o sistema {@link SGI} a partir do caminho padrão desta instância.
     *
     * @return instância de {@link SGI} inteiramente reconstruída
     * @throws IOException em caso de erro de leitura de disco
     * @throws IllegalArgumentException se o arquivo for inválido, corrompido ou inconsistente
     */
    public SGI carregar() throws IOException {
        return carregar(this.caminhoPadrao);
    }

    /**
     * Carrega e reconstitui o sistema {@link SGI} a partir de um {@link Path} informado.
     *
     * @param caminho rota do arquivo
     * @return instância de {@link SGI} restaurada
     * @throws IOException se ocorrer falha na leitura
     * @throws IllegalArgumentException se o arquivo for inválido ou corrompido
     */
    public SGI carregar(Path caminho) throws IOException {
        Objects.requireNonNull(caminho, "Caminho do arquivo não pode ser nulo.");
        if (!Files.exists(caminho)) {
            throw new IllegalArgumentException("Arquivo de dados não encontrado: " + caminho);
        }

        SGI sgi = new SGI();
        List<Cliente> clientes = carregarListaClientes(caminho);
        for (Cliente cliente : clientes) {
            sgi.cadastrarCliente(cliente);
        }
        return sgi;
    }

    /**
     * Sobrecarga de carregamento aceitando rota em texto.
     *
     * @param caminho texto com a rota do arquivo
     * @return instância de {@link SGI}
     * @throws IOException se houver erro de leitura
     */
    public SGI carregar(String caminho) throws IOException {
        Objects.requireNonNull(caminho, "Caminho do arquivo não pode ser nulo.");
        return carregar(Path.of(caminho));
    }

    /**
     * Carrega os clientes diretamente como lista de objetos {@link Cliente}.
     *
     * @param caminho rota do arquivo
     * @return lista de clientes hidratados
     * @throws IOException se houver falha de E/S
     */
    public List<Cliente> carregarClientes(Path caminho) throws IOException {
        return carregarListaClientes(caminho);
    }

    /**
     * Método estático de conveniência para carregar o SGI a partir de um {@link Path}.
     *
     * @param caminho caminho do arquivo de banco de dados
     * @return o {@link SGI} carregado
     * @throws IOException se falhar a leitura
     */
    public static SGI carregarDados(Path caminho) throws IOException {
        return new ArmazenadorArquivo().carregar(caminho);
    }

    /**
     * Sobrecarga estática de carregamento por texto de caminho.
     *
     * @param caminho caminho em String
     * @return o {@link SGI} restaurado
     * @throws IOException se falhar a leitura
     */
    public static SGI carregarDados(String caminho) throws IOException {
        return new ArmazenadorArquivo().carregar(caminho);
    }

    // =========================================================================
    // Parser e Mecanismo Central de Desserialização
    // =========================================================================

    /**
     * Lê e desserializa o arquivo TSV retornando a lista de clientes com suas aplicações e operações.
     *
     * @param caminho caminho do arquivo
     * @return lista de clientes
     * @throws IOException se falhar na leitura
     * @throws IllegalArgumentException se o arquivo for inválido ou corrompido
     */
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
            String cabecalho = reader.readLine();
            int versao = validarCabecalho(cabecalho);

            Cliente clienteAtual = null;
            MultiplasOperacoesFinanceiras aplicacaoAtual = null;
            String linha;
            int numeroLinha = 1;

            while ((linha = reader.readLine()) != null) {
                numeroLinha++;
                linha = linha.trim();
                if (linha.isEmpty()) {
                    continue;
                }

                List<String> campos = parseRegistro(linha, numeroLinha);
                String tipoRegistro = campos.get(0);

                switch (tipoRegistro) {
                    case "C" -> {
                        clienteAtual = lerRegistroCliente(campos, numeroLinha, clientesPorCpf);
                        aplicacaoAtual = null;
                    }
                    case "I" -> {
                        aplicacaoAtual = lerRegistroAplicacao(
                                campos, numeroLinha, versao, clienteAtual,
                                aplicacoesPorNumero, operacoesDeclaradas, operacoesRestauradas
                        );
                    }
                    case "O" -> {
                        lerRegistroOperacao(
                                campos, numeroLinha, clienteAtual,
                                aplicacaoAtual, operacoesRestauradas
                        );
                    }
                    default -> throw new IllegalArgumentException(
                            "Tipo de registro desconhecido na linha " + numeroLinha + ": '" + tipoRegistro + "'."
                    );
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

    private static Cliente lerRegistroCliente(
            List<String> campos,
            int numeroLinha,
            Map<String, Cliente> clientesPorCpf
    ) {
        if (campos.size() < 6) {
            throw new IllegalArgumentException(
                    "Registro de cliente corrompido na linha " + numeroLinha +
                    " (esperado 6 campos, obtido " + campos.size() + ")."
            );
        }
        String cpf = normalizarCpf(campos.get(1));
        String nome = campos.get(2);
        String celular = campos.get(3);
        int agencia;
        long conta;
        try {
            agencia = Integer.parseInt(campos.get(4));
            conta = Long.parseLong(campos.get(5));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Valores numéricos de agência ou conta corrompidos na linha " + numeroLinha + ".", e
            );
        }

        if (clientesPorCpf.containsKey(cpf)) {
            throw new IllegalArgumentException("CPF duplicado na linha " + numeroLinha + ": " + cpf);
        }
        if (clientesPorCpf.size() >= 100) {
            throw new IllegalStateException("Limite regulamentar de 100 clientes atingido.");
        }

        DADOSPESSOAIS dados = new DADOSPESSOAIS(nome, cpf, celular);
        Cliente cliente = new Cliente(dados, agencia, conta);
        clientesPorCpf.put(cpf, cliente);
        return cliente;
    }

    private static MultiplasOperacoesFinanceiras lerRegistroAplicacao(
            List<String> campos,
            int numeroLinha,
            int versao,
            Cliente clienteAtual,
            Map<String, MultiplasOperacoesFinanceiras> aplicacoesPorNumero,
            Map<String, Integer> operacoesDeclaradas,
            Map<String, Integer> operacoesRestauradas
    ) {
        if (clienteAtual == null) {
            throw new IllegalArgumentException(
                    "Registro de aplicação antes do registro de cliente na linha " + numeroLinha + "."
            );
        }

        int camposEsperadosMinimos = switch (versao) {
            case 1 -> 15;
            case 2 -> 21;
            case 3 -> 23;
            default -> 23;
        };

        if (campos.size() < camposEsperadosMinimos) {
            throw new IllegalArgumentException(
                    "Registro de aplicação corrompido na linha " + numeroLinha +
                    " (campos insuficientes: " + campos.size() + ", esperado no mínimo " + camposEsperadosMinimos + ")."
            );
        }

        String cpfNaLinha = normalizarCpf(campos.get(1));
        if (!clienteAtual.getDadosPessoais().getCpf().equals(cpfNaLinha)) {
            throw new IllegalArgumentException(
                    "Aplicação na linha " + numeroLinha + " possui CPF (" + cpfNaLinha +
                    ") divergente do cliente ativo (" + clienteAtual.getDadosPessoais().getCpf() + ")."
            );
        }

        String numeroAplicacao = campos.get(2);
        if (aplicacoesPorNumero.containsKey(numeroAplicacao)) {
            throw new IllegalArgumentException(
                    "Número de aplicação duplicado no sistema na linha " + numeroLinha + ": " + numeroAplicacao
            );
        }

        String nomeAplicacao = campos.get(3);
        BigDecimal valorAplicado = new BigDecimal(campos.get(4));
        BigDecimal taxaMensal = new BigDecimal(campos.get(5));
        LocalDate dataAplicacao = LocalDate.parse(campos.get(6));

        int tipoRendimento = (campos.size() > 15 && !campos.get(15).isBlank()) ? Integer.parseInt(campos.get(15)) : 0;
        int tempoVencimento = (campos.size() > 16 && !campos.get(16).isBlank()) ? Integer.parseInt(campos.get(16)) : 0;
        int tempoPermanencia = (campos.size() > 17 && !campos.get(17).isBlank()) ? Integer.parseInt(campos.get(17)) : 0;
        int tipoResgate = (campos.size() > 18 && !campos.get(18).isBlank()) ? Integer.parseInt(campos.get(18)) : 0;
        int iof = (campos.size() > 19 && !campos.get(19).isBlank()) ? Integer.parseInt(campos.get(19)) : 0;
        int tipoIR = (campos.size() > 20 && !campos.get(20).isBlank()) ? Integer.parseInt(campos.get(20)) : 0;

        AplicacaoFinanceira af = new AplicacaoFinanceira(
                nomeAplicacao, numeroAplicacao, valorAplicado, taxaMensal,
                tipoRendimento, tempoVencimento, tempoPermanencia,
                tipoResgate, iof, tipoIR, dataAplicacao
        );

        // FASE 1: Configurações estruturais e parâmetros de base
        if (campos.size() > 13 && !campos.get(13).isBlank()) {
            af.setConvencaoContagemDias(ConvencaoContagemDias.valueOf(campos.get(13)));
        }
        if (campos.size() > 14 && !campos.get(14).isBlank()) {
            af.setRegimeTributario(RegimeTributario.valueOf(campos.get(14)));
        }
        if (campos.size() > 10 && !campos.get(10).isBlank()) {
            af.setDiasCorridos(Integer.parseInt(campos.get(10)));
        }
        if (campos.size() > 11 && !campos.get(11).isBlank()) {
            af.setDiasEfetivosCalculo(Integer.parseInt(campos.get(11)));
        }
        if (campos.size() > 9 && !campos.get(9).isBlank()) {
            af.setDataUltimaAtualizacao(LocalDate.parse(campos.get(9)));
        }
        int qtdOpsDeclarada = 0;
        if (campos.size() > 12 && !campos.get(12).isBlank()) {
            qtdOpsDeclarada = Integer.parseInt(campos.get(12));
            af.setQuantidadeOperacoes(qtdOpsDeclarada);
        }
        if (campos.size() > 21 && !campos.get(21).isBlank()) {
            af.setValorResgate(new BigDecimal(campos.get(21)));
        }
        if (campos.size() > 7 && !campos.get(7).isBlank()) {
            af.setSaldo(new BigDecimal(campos.get(7)));
        }

        // FASE 2: Grandezas patrimoniais e fiscais consolidadas
        if (campos.size() > 23 && !campos.get(23).isBlank()) {
            af.setRendimentoAtualBruto(new BigDecimal(campos.get(23)));
        }
        if (campos.size() > 24 && !campos.get(24).isBlank()) {
            af.setRendimentoAtualLiquido(new BigDecimal(campos.get(24)));
        }
        if (campos.size() > 25 && !campos.get(25).isBlank()) {
            af.setImpostoAtualDevido(new BigDecimal(campos.get(25)));
        }
        if (campos.size() > 8 && !campos.get(8).isBlank()) {
            af.setValorAtualBruto(new BigDecimal(campos.get(8)));
        }
        if (campos.size() > 22 && !campos.get(22).isBlank()) {
            af.setValorAtualLiquido(new BigDecimal(campos.get(22)));
        }

        MultiplasOperacoesFinanceiras multi = new MultiplasOperacoesFinanceiras(af);
        clienteAtual.adicionarAplicacao(multi);

        aplicacoesPorNumero.put(numeroAplicacao, multi);
        operacoesDeclaradas.put(numeroAplicacao, qtdOpsDeclarada);
        operacoesRestauradas.put(numeroAplicacao, 0);

        return multi;
    }

    private static void lerRegistroOperacao(
            List<String> campos,
            int numeroLinha,
            Cliente clienteAtual,
            MultiplasOperacoesFinanceiras aplicacaoAtual,
            Map<String, Integer> operacoesRestauradas
    ) {
        if (clienteAtual == null) {
            throw new IllegalArgumentException(
                    "Registro de operação sem cliente associado na linha " + numeroLinha + "."
            );
        }
        if (aplicacaoAtual == null) {
            throw new IllegalArgumentException(
                    "Registro de operação sem aplicação associada na linha " + numeroLinha + "."
            );
        }
        if (campos.size() < 7) {
            throw new IllegalArgumentException(
                    "Registro de operação corrompido na linha " + numeroLinha +
                    " (campos insuficientes: " + campos.size() + ", esperado 7)."
            );
        }

        String cpfNaLinha = normalizarCpf(campos.get(1));
        if (!clienteAtual.getDadosPessoais().getCpf().equals(cpfNaLinha)) {
            throw new IllegalArgumentException(
                    "Operação na linha " + numeroLinha + " possui CPF (" + cpfNaLinha +
                    ") diferente do cliente ativo (" + clienteAtual.getDadosPessoais().getCpf() + ")."
            );
        }

        String numAppNaLinha = campos.get(2);
        if (!aplicacaoAtual.getAplicacao().getNumeroAplicacao().equals(numAppNaLinha)) {
            throw new IllegalArgumentException(
                    "Operação na linha " + numeroLinha + " associada à aplicação " + numAppNaLinha +
                    ", divergente da aplicação ativa (" + aplicacaoAtual.getAplicacao().getNumeroAplicacao() + ")."
            );
        }

        LocalDate dataOperacao = LocalDate.parse(campos.get(3));
        BigDecimal valor = new BigDecimal(campos.get(4));
        String descricao = campos.get(5);
        TipoOperacao tipo = TipoOperacao.deTexto(campos.get(6));

        if (dataOperacao.isBefore(aplicacaoAtual.getAplicacao().getDataAplicacao())) {
            throw new IllegalArgumentException(
                    "Data da operação na linha " + numeroLinha + " (" + dataOperacao +
                    ") é anterior à data de início da aplicação (" + aplicacaoAtual.getAplicacao().getDataAplicacao() + ")."
            );
        }

        OperacaoFinanceira op = new OperacaoFinanceira(dataOperacao, valor, descricao, tipo);
        aplicacaoAtual.vincularOperacaoRestaurada(op);
        operacoesRestauradas.merge(numAppNaLinha, 1, Integer::sum);
    }

    private static void validarContagemOperacoes(
            Map<String, Integer> operacoesDeclaradas,
            Map<String, Integer> operacoesRestauradas
    ) {
        for (Map.Entry<String, Integer> entry : operacoesDeclaradas.entrySet()) {
            String numApp = entry.getKey();
            int declaradas = entry.getValue();
            int restauradas = operacoesRestauradas.getOrDefault(numApp, 0);
            if (declaradas > 0 && declaradas != restauradas) {
                throw new IllegalArgumentException(
                        "Quantidade de operações inconsistente na aplicação " + numApp +
                        ": declarado no registro I=" + declaradas + ", registros O encontrados=" + restauradas + "."
                );
            }
        }
    }

    // =========================================================================
    // Utilitários de Parsing, Escapamento e Segurança de Arquivo
    // =========================================================================

    /**
     * Decompõe uma linha do arquivo TSV em campos individuais respeitando sequências de escape.
     *
     * @param linha linha do arquivo
     * @param numeroLinha número identificador da linha para mensagens de erro
     * @return lista de strings contendo os campos desescapados
     * @throws IllegalArgumentException se houver escape incompleto ou caractere inválido
     */
    public static List<String> parseRegistro(String linha, int numeroLinha) {
        List<String> campos = new ArrayList<>();
        StringBuilder campoAtual = new StringBuilder();
        boolean emEscape = false;

        for (int i = 0; i < linha.length(); i++) {
            char c = linha.charAt(i);
            if (emEscape) {
                switch (c) {
                    case 't' -> campoAtual.append('\t');
                    case 'n' -> campoAtual.append('\n');
                    case 'r' -> campoAtual.append('\r');
                    case '\\' -> campoAtual.append('\\');
                    default -> throw new IllegalArgumentException(
                            "Sequência de escape inválida '\\" + c + "' na linha " + numeroLinha + "."
                    );
                }
                emEscape = false;
            } else if (c == '\\') {
                emEscape = true;
            } else if (c == '\t') {
                campos.add(campoAtual.toString());
                campoAtual.setLength(0);
            } else {
                campoAtual.append(c);
            }
        }

        if (emEscape) {
            throw new IllegalArgumentException(
                    "Sequência de escape incompleta no final da linha " + numeroLinha + "."
            );
        }

        campos.add(campoAtual.toString());
        return campos;
    }

    /**
     * Aplica o escape de caracteres especiais para preservar o formato TSV em linha única.
     *
     * @param valor texto original
     * @return texto com barras invertidas, tabs e quebras de linha escapados
     */
    public static String escapar(String valor) {
        if (valor == null) {
            return "";
        }
        return valor.replace("\\", "\\\\")
                    .replace("\t", "\\t")
                    .replace("\r", "\\r")
                    .replace("\n", "\\n");
    }

    /**
     * Converte texto escapado de volta à representação pura em memória.
     *
     * @param valor texto com sequências de escape
     * @return texto puro original
     */
    public static String desescapar(String valor) {
        if (valor == null || valor.isEmpty()) {
            return "";
        }
        return parseRegistro(valor, 0).get(0);
    }

    /**
     * Aplica restrição de permissão POSIX 0600 (rw-------) com fallback não-POSIX seguro.
     *
     * @param caminho arquivo a ser protegido
     */
    public static void aplicarPermissoes0600(Path caminho) {
        try {
            Files.setPosixFilePermissions(caminho, PERMISSOES_0600);
        } catch (UnsupportedOperationException | IOException e) {
            try {
                File file = caminho.toFile();
                file.setReadable(false, false);
                file.setWritable(false, false);
                file.setExecutable(false, false);
                file.setReadable(true, true);
                file.setWritable(true, true);
            } catch (SecurityException ignored) {
                // Ambientes restritivos
            }
        }
    }

    /**
     * Realiza a substituição atômica de arquivo via renomeação no sistema de arquivos.
     *
     * @param origem arquivo temporário
     * @param destino arquivo definitivo
     * @throws IOException se falhar a renomeação
     */
    public static void substituirArquivoAtomico(Path origem, Path destino) throws IOException {
        try {
            Files.move(origem, destino, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(origem, destino, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Normaliza CPF removendo pontuação e validando 11 dígitos numéricos.
     *
     * @param cpf string de CPF
     * @return 11 dígitos numéricos limpos
     * @throws IllegalArgumentException se o CPF for nulo ou não possuir 11 dígitos
     */
    public static String normalizarCpf(String cpf) {
        if (cpf == null) {
            throw new IllegalArgumentException("CPF não pode ser nulo.");
        }
        String digitos = cpf.replaceAll("\\D", "");
        if (digitos.length() != 11) {
            throw new IllegalArgumentException("CPF deve conter exatamente 11 dígitos numéricos: '" + cpf + "'.");
        }
        return digitos;
    }

    // =========================================================================
    // Ganchos Protegidos para Especialização por Subclasses (Herança)
    // =========================================================================

    /**
     * Serializa o registro de cliente {@code C}. Pode ser sobrescrito por subclasses.
     *
     * @param writer escritor bufferizado
     * @param cliente instância de cliente
     * @throws IOException se houver falha de escrita
     */
    protected void gravarRegistroCliente(BufferedWriter writer, Cliente cliente) throws IOException {
        String linha = String.join("\t",
                "C",
                escapar(cliente.getDadosPessoais().getCpf()),
                escapar(cliente.getDadosPessoais().getNome()),
                escapar(cliente.getDadosPessoais().getCelular()),
                Integer.toString(cliente.getNumeroAgencia()),
                Long.toString(cliente.getNumeroConta())
        );
        writer.write(linha);
        writer.newLine();
    }

    /**
     * Serializa o registro de aplicação financeira {@code I}. Pode ser sobrescrito por subclasses.
     *
     * @param writer escritor bufferizado
     * @param cliente titular da conta
     * @param multi agregação de aplicação e operações
     * @throws IOException se houver falha de escrita
     */
    protected void gravarRegistroAplicacao(BufferedWriter writer, Cliente cliente, MultiplasOperacoesFinanceiras multi) throws IOException {
        AplicacaoFinanceira af = multi.getAplicacao();
        if (af == null) {
            return;
        }
        String linha = String.join("\t",
                "I",
                escapar(cliente.getDadosPessoais().getCpf()),
                escapar(af.getNumeroAplicacao()),
                escapar(af.getNomeAplicacao()),
                af.getValorAplicado() != null ? af.getValorAplicado().toPlainString() : "0.00",
                af.getTaxaMensal() != null ? af.getTaxaMensal().toPlainString() : "0.00",
                af.getDataAplicacao() != null ? af.getDataAplicacao().toString() : LocalDate.now().toString(),
                af.getSaldo() != null ? af.getSaldo().toPlainString() : "0.00",
                af.getValorAtualBruto() != null ? af.getValorAtualBruto().toPlainString() : "0.00",
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
                af.getValorResgate() != null ? af.getValorResgate().toPlainString() : "0.00",
                af.getValorAtualLiquido() != null ? af.getValorAtualLiquido().toPlainString() : "0.00",
                af.getRendimentoAtualBruto() != null ? af.getRendimentoAtualBruto().toPlainString() : "0.00",
                af.getRendimentoAtualLiquido() != null ? af.getRendimentoAtualLiquido().toPlainString() : "0.00",
                af.getImpostoAtualDevido() != null ? af.getImpostoAtualDevido().toPlainString() : "0.00"
        );
        writer.write(linha);
        writer.newLine();
    }

    /**
     * Serializa todos os registros de operações {@code O} de uma aplicação.
     *
     * @param writer escritor bufferizado
     * @param cliente titular da conta
     * @param multi agregação de operações
     * @throws IOException se houver erro de escrita
     */
    protected void gravarRegistrosOperacoes(BufferedWriter writer, Cliente cliente, MultiplasOperacoesFinanceiras multi) throws IOException {
        String numApp = (multi.getAplicacao() != null) ? multi.getAplicacao().getNumeroAplicacao() : "";
        for (OperacaoFinanceira op : multi.getOperacoes()) {
            String linha = String.join("\t",
                    "O",
                    escapar(cliente.getDadosPessoais().getCpf()),
                    escapar(numApp),
                    op.getData() != null ? op.getData().toString() : LocalDate.now().toString(),
                    op.getValor() != null ? op.getValor().toPlainString() : "0.00",
                    escapar(op.getDescricao()),
                    op.getTipo() != null ? op.getTipo().name() : TipoOperacao.DEPOSITO.name()
            );
            writer.write(linha);
            writer.newLine();
        }
    }
}

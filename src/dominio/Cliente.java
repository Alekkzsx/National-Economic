package dominio;

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
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Representa a gestão bancária e contábil de um Cliente do SGI (Item 6, Requisito R2.3).
 *
 * <p>Esta classe reúne os {@link DADOSPESSOAIS} do titular, as coordenadas bancárias
 * (número da agência e da conta) e uma carteira agregada de até 50 aplicações financeiras
 * ({@link MultiplasOperacoesFinanceiras}).</p>
 *
 * <p>Principais responsabilidades e contratos operacionais:</p>
 * <ul>
 *   <li>Gestão de até 50 aplicações financeiras com garantia de unicidade de identificador;</li>
 *   <li>Processamento de atualização financeira em lote restrito a dias úteis;</li>
 *   <li>Apuração de saldos líquidos individuais por aplicação e consolidado de toda a conta;</li>
 *   <li>Geração polimórfica de extratos de fluxo de caixa individuais e consolidados
 *       (janela de 30 dias e intervalos arbitrários entre datas), com ordenação cronológica estável;</li>
 *   <li>Persistência autônoma em disco através dos métodos {@link #arquivoSalvarDados} e
 *       {@link #arquivoLerDados}, adotando gravação atômica em formato TSV versionado (v3) e
 *       permissões de arquivo POSIX 0600.</li>
 * </ul>
 *
 * <p>A classe é passível de herança (não-final) e adota práticas defensivas contra despacho
 * virtual durante a inicialização em construtores.</p>
 */
public class Cliente {

    /** Limite regulamentar máximo de aplicações financeiras permitido por conta de cliente. */
    public static final int LIMITE_APLICACOES = 50;

    /** Alias didático de compatibilidade para o limite máximo de aplicações. */
    public static final int MAX_APLICACOES = LIMITE_APLICACOES;

    /** Cabeçalho padrão do arquivo de persistência em versão 3. */
    private static final String CABECALHO_V3 = "NATIONAL-ECONOMIC\t3";

    /** Permissões estritas de leitura/escrita exclusivas do proprietário (0600). */
    private static final Set<PosixFilePermission> PERMISSOES_POSIX_0600 = EnumSet.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE
    );

    /** Dados cadastrais pessoais e fiscais do titular da conta. */
    private DADOSPESSOAIS dadosPessoais;

    /** Número identificador da agência bancária de vinculação (&gt; 0). */
    private int numeroAgencia;

    /** Número identificador da conta corrente/investimento (&gt; 0). */
    private long numeroConta;

    /** Carteira agregada de aplicações financeiras do cliente (máx. 50). */
    private final List<MultiplasOperacoesFinanceiras> aplicacoes = new ArrayList<>();

    // =========================================================================
    // Construtores
    // =========================================================================

    /**
     * Construtor protegido sem argumentos para viabilizar herança segura por especializações.
     */
    protected Cliente() {
    }

    /**
     * Construtor principal para inicialização de cliente com validação estrita.
     *
     * <p>Não despacha chamadas a métodos virtuais sobrecarregáveis, assegurando integridade
     * do estado na extensão por subclasses.</p>
     *
     * @param dadosPessoais instância contendo nome, CPF e celular do titular (não nulo)
     * @param numeroAgencia número da agência bancária (estritamente positivo &gt; 0)
     * @param numeroConta número da conta do cliente (estritamente positivo &gt; 0)
     * @throws NullPointerException se dadosPessoais for nulo
     * @throws IllegalArgumentException se agência ou conta forem menores ou iguais a zero
     */
    public Cliente(DADOSPESSOAIS dadosPessoais, int numeroAgencia, long numeroConta) {
        this.dadosPessoais = Objects.requireNonNull(dadosPessoais, "Dados pessoais são obrigatórios.");
        if (numeroAgencia <= 0) {
            throw new IllegalArgumentException("Número da agência deve ser positivo (> 0).");
        }
        if (numeroConta <= 0) {
            throw new IllegalArgumentException("Número da conta deve ser positivo (> 0).");
        }
        this.numeroAgencia = numeroAgencia;
        this.numeroConta = numeroConta;
    }

    /**
     * Construtor de conveniência que instancia automaticamente os dados pessoais do titular.
     *
     * @param nome nome completo do cliente
     * @param cpf cadastro de pessoa física com 11 dígitos válidos
     * @param celular telefone de contato do titular
     * @param numeroAgencia número da agência bancária (&gt; 0)
     * @param numeroConta número da conta do cliente (&gt; 0)
     * @throws NullPointerException se dados pessoais forem nulos
     * @throws IllegalArgumentException se parâmetros forem inválidos
     */
    public Cliente(String nome, String cpf, String celular, int numeroAgencia, long numeroConta) {
        this(new DADOSPESSOAIS(nome, cpf, celular), numeroAgencia, numeroConta);
    }

    // =========================================================================
    // Getters e Setters de Atributos Básicos
    // =========================================================================

    /**
     * Retorna os dados pessoais do titular.
     *
     * @return dados pessoais cadastrados
     */
    public DADOSPESSOAIS getDadosPessoais() {
        return dadosPessoais;
    }

    /**
     * Atualiza os dados pessoais do titular.
     *
     * @param dadosPessoais novos dados cadastrais (não nulo)
     * @throws NullPointerException se dadosPessoais for nulo
     */
    public void setDadosPessoais(DADOSPESSOAIS dadosPessoais) {
        this.dadosPessoais = Objects.requireNonNull(dadosPessoais, "Dados pessoais são obrigatórios.");
    }

    /**
     * Retorna o número da agência bancária.
     *
     * @return número da agência
     */
    public int getNumeroAgencia() {
        return numeroAgencia;
    }

    /**
     * Define o número da agência bancária.
     *
     * @param numeroAgencia identificador positivo da agência
     * @throws IllegalArgumentException se menor ou igual a zero
     */
    public void setNumeroAgencia(int numeroAgencia) {
        if (numeroAgencia <= 0) {
            throw new IllegalArgumentException("Número da agência deve ser positivo (> 0).");
        }
        this.numeroAgencia = numeroAgencia;
    }

    /**
     * Retorna o número da conta bancária.
     *
     * @return número da conta
     */
    public long getNumeroConta() {
        return numeroConta;
    }

    /**
     * Define o número da conta bancária.
     *
     * @param numeroConta identificador positivo da conta
     * @throws IllegalArgumentException se menor ou igual a zero
     */
    public void setNumeroConta(long numeroConta) {
        if (numeroConta <= 0) {
            throw new IllegalArgumentException("Número da conta deve ser positivo (> 0).");
        }
        this.numeroConta = numeroConta;
    }

    /**
     * Retorna a quantidade de aplicações ativas cadastradas para o cliente.
     *
     * @return total de aplicações financeiras
     */
    public int getQuantidadeAplicacoes() {
        return aplicacoes.size();
    }

    /**
     * Retorna uma visão somente para leitura da lista de aplicações cadastradas.
     *
     * @return lista imutável de aplicações do cliente
     */
    public List<MultiplasOperacoesFinanceiras> getAplicacoes() {
        return Collections.unmodifiableList(aplicacoes);
    }

    // =========================================================================
    // Gestão de Aplicações Financeiras (Item 6)
    // =========================================================================

    /**
     * Adiciona uma aplicação financeira à carteira do cliente respeitando a cota máxima de 50
     * e a unicidade de número de aplicação.
     *
     * @param aplicacao instância contendo aplicação, histórico de operações e calendário
     * @throws NullPointerException se aplicacao for nula
     * @throws IllegalStateException se o limite de 50 aplicações já tiver sido atingido
     * @throws IllegalArgumentException se o número da aplicação já existir na conta do cliente
     */
    public void adicionarAplicacao(MultiplasOperacoesFinanceiras aplicacao) {
        Objects.requireNonNull(aplicacao, "Aplicação financeira é obrigatória.");
        if (aplicacoes.size() >= LIMITE_APLICACOES) {
            throw new IllegalStateException("Limite de 50 aplicações por cliente atingido.");
        }

        String numero = extrairNumeroAplicacao(aplicacao);
        if (numero != null && buscarAplicacao(numero).isPresent()) {
            throw new IllegalArgumentException("Número de aplicação duplicado para este cliente: " + numero);
        }

        aplicacoes.add(aplicacao);
    }

    /**
     * Sobrecarga de conveniência que encapsula uma {@link AplicacaoFinanceira} direta
     * em uma estrutura de {@link MultiplasOperacoesFinanceiras}.
     *
     * @param aplicacaoBase aplicação financeira pura a ser integrada
     * @throws NullPointerException se aplicacaoBase for nula
     */
    public void adicionarAplicacao(AplicacaoFinanceira aplicacaoBase) {
        Objects.requireNonNull(aplicacaoBase, "Aplicação financeira é obrigatória.");
        adicionarAplicacao(new MultiplasOperacoesFinanceiras(aplicacaoBase));
    }

    /**
     * Remove uma aplicação da carteira pelo identificador único.
     *
     * @param numeroAplicacao identificador da aplicação a remover
     * @return carteira removida
     */
    public MultiplasOperacoesFinanceiras removerAplicacao(String numeroAplicacao) {
        MultiplasOperacoesFinanceiras aplicacao = requererAplicacao(numeroAplicacao);
        aplicacoes.remove(aplicacao);
        return aplicacao;
    }

    /**
     * Localiza uma aplicação pelo seu número identificador no âmbito do cliente.
     *
     * @param numeroAplicacao identificador único da aplicação buscada
     * @return {@link Optional} contendo a aplicação se encontrada, ou vazio caso contrário
     */
    public Optional<MultiplasOperacoesFinanceiras> buscarAplicacao(String numeroAplicacao) {
        if (numeroAplicacao == null || numeroAplicacao.isBlank()) {
            return Optional.empty();
        }
        for (MultiplasOperacoesFinanceiras terminal : aplicacoes) {
            if (Objects.equals(extrairNumeroAplicacao(terminal), numeroAplicacao.trim())) {
                return Optional.of(terminal);
            }
        }
        return Optional.empty();
    }

    /**
     * Requer a existência de uma aplicação financeira cadastrada sob o número informado.
     *
     * @param numeroAplicacao identificador único da aplicação
     * @return a aplicação financeira correspondente
     * @throws IllegalArgumentException se nenhuma aplicação com o número fornecido for localizada
     */
    public MultiplasOperacoesFinanceiras requererAplicacao(String numeroAplicacao) {
        return buscarAplicacao(numeroAplicacao).orElseThrow(() ->
                new IllegalArgumentException("Aplicação não encontrada para o número: " + numeroAplicacao));
    }

    // =========================================================================
    // Atualização Financeira em Dias Úteis (Item 6, R2.3)
    // =========================================================================

    /**
     * Atualiza os saldos e rendimentos de todas as aplicações da carteira do cliente na data civil corrente.
     */
    public void atualizarAplicacoes() {
        atualizarAplicacoes(LocalDate.now());
    }

    /**
     * Atualiza os saldos e rendimentos de todas as aplicações da carteira do cliente na data civil informada.
     *
     * <p>Conforme especificação formal, a atualização financeira opera estritamente em dias úteis
     * (segunda a sexta-feira). Em sábados e domingos, os rendimentos diários não são computados.</p>
     *
     * @param data data civil de referência da atualização financeira
     * @throws NullPointerException se a data for nula
     */
    public void atualizarAplicacoes(LocalDate data) {
        Objects.requireNonNull(data, "Data de atualização é obrigatória.");
        for (MultiplasOperacoesFinanceiras terminal : aplicacoes) {
            terminal.atualizarAplicacao(data);
        }
    }

    // =========================================================================
    // Consulta de Saldos (Item 6, R2.3)
    // =========================================================================

    /**
     * Retorna o saldo patrimonial líquido de uma aplicação financeira específica do cliente.
     *
     * @param numeroAplicacao identificador unívoco da aplicação desejada
     * @return montante monetário líquido atual da aplicação
     * @throws IllegalArgumentException se a aplicação não for encontrada
     */
    public BigDecimal getSaldo(String numeroAplicacao) {
        return requererAplicacao(numeroAplicacao).getSaldo();
    }

    /**
     * Retorna o saldo patrimonial líquido consolidado de todas as aplicações do cliente.
     *
     * @return somatório dos saldos líquidos de todas as aplicações (retorna ZERO se não houver aplicações)
     */
    public BigDecimal getSaldoConsolidado() {
        BigDecimal consolidado = BigDecimal.ZERO;
        for (MultiplasOperacoesFinanceiras terminal : aplicacoes) {
            BigDecimal saldoApp = terminal.getSaldo();
            if (saldoApp != null) {
                consolidado = consolidado.add(saldoApp);
            }
        }
        return Moeda.arredondar(consolidado);
    }

    // =========================================================================
    // Consulta de Extratos Individuais e Consolidados (Item 6, R2.3)
    // =========================================================================

    /**
     * Retorna o extrato de lançamentos de uma aplicação individual relativo aos últimos 30 dias.
     *
     * @param numeroAplicacao identificador da aplicação
     * @return lista cronológica de operações financeiras dos últimos 30 dias
     */
    public List<OperacaoFinanceira> getExtrato(String numeroAplicacao) {
        return requererAplicacao(numeroAplicacao).getExtrato();
    }

    /**
     * Retorna o extrato polimórfico de uma aplicação individual compreendido entre duas datas civis.
     *
     * @param numeroAplicacao identificador da aplicação
     * @param inicio data inicial inclusiva do intervalo
     * @param fim data final inclusiva do intervalo
     * @return lista cronológica de operações do período informado
     * @throws IllegalArgumentException se a data final anteceder a data inicial
     */
    public List<OperacaoFinanceira> getExtrato(String numeroAplicacao, LocalDate inicio, LocalDate fim) {
        return requererAplicacao(numeroAplicacao).getExtrato(inicio, fim);
    }

    /**
     * Retorna o extrato consolidado de todas as aplicações do cliente nos últimos 30 dias.
     *
     * <p>Reúne os lançamentos de fluxo de caixa de toda a carteira e os apresenta em ordem
     * cronológica estrita por data civil da operação.</p>
     *
     * @return lista imutável e ordenada dos lançamentos dos últimos 30 dias de todas as aplicações
     */
    public List<OperacaoFinanceira> getExtratoConsolidado() {
        List<OperacaoFinanceira> consolidado = new ArrayList<>();
        for (MultiplasOperacoesFinanceiras terminal : aplicacoes) {
            consolidado.addAll(terminal.getExtrato());
        }
        consolidado.sort(Comparator.comparing(OperacaoFinanceira::getData));
        return Collections.unmodifiableList(consolidado);
    }

    /**
     * Retorna o extrato consolidado polimórfico de todas as aplicações do cliente entre duas datas civis.
     *
     * @param inicio data civil inicial inclusiva
     * @param fim data civil final inclusiva
     * @return lista imutável e ordenada dos lançamentos do período informado de todas as aplicações
     * @throws NullPointerException se inicio ou fim forem nulos
     * @throws IllegalArgumentException se a data final for anterior à data inicial
     */
    public List<OperacaoFinanceira> getExtratoConsolidado(LocalDate inicio, LocalDate fim) {
        Objects.requireNonNull(inicio, "Data inicial é obrigatória.");
        Objects.requireNonNull(fim, "Data final é obrigatória.");
        if (fim.isBefore(inicio)) {
            throw new IllegalArgumentException("A data final do período não pode anteceder a data inicial.");
        }

        List<OperacaoFinanceira> consolidado = new ArrayList<>();
        for (MultiplasOperacoesFinanceiras terminal : aplicacoes) {
            consolidado.addAll(terminal.getExtrato(inicio, fim));
        }
        consolidado.sort(Comparator.comparing(OperacaoFinanceira::getData));
        return Collections.unmodifiableList(consolidado);
    }

    // =========================================================================
    // Persistência em Arquivo (Item 6, R2.3)
    // =========================================================================

    /**
     * Persiste integralmente os dados do cliente, suas aplicações e histórico de operações em disco.
     *
     * <p>Adota escrita atômica via arquivo temporário na mesma partição e restrição de permissão
     * POSIX 0600 em conformidade com as diretrizes de segurança contábil do sistema.</p>
     *
     * @param caminho caminho do arquivo de destino
     * @throws IOException se ocorrer falha de E/S na gravação
     * @throws NullPointerException se o caminho for nulo
     */
    public void arquivoSalvarDados(Path caminho) throws IOException {
        Objects.requireNonNull(caminho, "Caminho do arquivo é obrigatório.");
        Path absoluto = caminho.toAbsolutePath();
        Path diretorioPai = absoluto.getParent();
        if (diretorioPai != null) {
            Files.createDirectories(diretorioPai);
        }

        Path arquivoTemporario = Files.createTempFile(
                diretorioPai != null ? diretorioPai : Path.of("."),
                "cliente_",
                ".tmp"
        );

        try {
            aplicarPermissoesSeguras(arquivoTemporario);
            try (BufferedWriter writer = Files.newBufferedWriter(arquivoTemporario, StandardCharsets.UTF_8)) {
                writer.write(CABECALHO_V3);
                writer.newLine();

                // Registro do Cliente (C)
                gravarRegistroCliente(writer);

                // Registros de Aplicações (I) e Operações (O)
                for (MultiplasOperacoesFinanceiras terminal : aplicacoes) {
                    gravarRegistroAplicacao(writer, terminal);
                    gravarRegistrosOperacoes(writer, terminal);
                }
            }

            try {
                Files.move(arquivoTemporario, absoluto, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(arquivoTemporario, absoluto, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(arquivoTemporario);
        }
    }

    /**
     * Sobrecarga de persistência em arquivo a partir de representação em {@link String}.
     *
     * @param caminho rota do arquivo no sistema de arquivos
     * @throws IOException se houver erro de gravação
     * @throws NullPointerException se o caminho for nulo
     */
    public void arquivoSalvarDados(String caminho) throws IOException {
        Objects.requireNonNull(caminho, "Caminho do arquivo é obrigatório.");
        arquivoSalvarDados(Path.of(caminho));
    }

    /**
     * Restaura o estado completo de um cliente a partir de um arquivo em formato TSV versionado.
     *
     * @param caminho caminho do arquivo de origem
     * @return instância de {@link Cliente} inteiramente reconstruída com suas aplicações e operações
     * @throws IOException se ocorrer falha na leitura do arquivo
     * @throws IllegalArgumentException se o arquivo for inválido, corrompido ou não contiver cliente
     * @throws NullPointerException se o caminho for nulo
     */
    public static Cliente arquivoLerDados(Path caminho) throws IOException {
        Objects.requireNonNull(caminho, "Caminho do arquivo é obrigatório.");
        if (!Files.exists(caminho)) {
            throw new IllegalArgumentException("Arquivo não encontrado: " + caminho);
        }

        try (BufferedReader reader = Files.newBufferedReader(caminho, StandardCharsets.UTF_8)) {
            String cabecalho = reader.readLine();
            if (cabecalho == null || !cabecalho.startsWith("NATIONAL-ECONOMIC")) {
                throw new IllegalArgumentException("Cabeçalho de arquivo inválido ou formato incompatível.");
            }

            Cliente cliente = null;
            MultiplasOperacoesFinanceiras aplicacaoAtual = null;

            String linha;
            while ((linha = reader.readLine()) != null) {
                linha = linha.trim();
                if (linha.isEmpty()) {
                    continue;
                }

                String[] campos = linha.split("\t", -1);
                String tipoRegistro = campos[0];

                switch (tipoRegistro) {
                    case "C" -> {
                        if (campos.length < 6) {
                            throw new IllegalArgumentException("Registro de cliente corrompido (campos insuficientes).");
                        }
                        String cpf = desescapar(campos[1]);
                        String nome = desescapar(campos[2]);
                        String celular = desescapar(campos[3]);
                        int agencia = Integer.parseInt(campos[4]);
                        long conta = Long.parseLong(campos[5]);

                        DADOSPESSOAIS dados = new DADOSPESSOAIS(nome, cpf, celular);
                        cliente = new Cliente(dados, agencia, conta);
                    }
                    case "I" -> {
                        if (cliente == null) {
                            throw new IllegalArgumentException("Registro de aplicação antes do registro de cliente.");
                        }
                        if (campos.length < 23) {
                            throw new IllegalArgumentException("Registro de aplicação corrompido (campos insuficientes: " + campos.length + ").");
                        }
                        aplicacaoAtual = desserializarAplicacao(campos);
                        cliente.adicionarAplicacao(aplicacaoAtual);
                    }
                    case "O" -> {
                        if (aplicacaoAtual == null) {
                            throw new IllegalArgumentException("Registro de operação sem aplicação associada.");
                        }
                        if (campos.length < 7) {
                            throw new IllegalArgumentException("Registro de operação corrompido (campos insuficientes: " + campos.length + ").");
                        }
                        OperacaoFinanceira op = desserializarOperacao(campos);
                        aplicacaoAtual.vincularOperacaoRestaurada(op);
                    }
                    default -> {
                        // Linha desconhecida ou comentário ignorado
                    }
                }
            }

            if (cliente == null) {
                throw new IllegalArgumentException("Nenhum registro de cliente foi localizado no arquivo.");
            }

            return cliente;
        }
    }

    /**
     * Sobrecarga de leitura de dados a partir de rota em {@link String}.
     *
     * @param caminho texto com o caminho do arquivo
     * @return cliente recuperado
     * @throws IOException se falhar na leitura
     * @throws NullPointerException se o caminho for nulo
     */
    public static Cliente arquivoLerDados(String caminho) throws IOException {
        Objects.requireNonNull(caminho, "Caminho do arquivo é obrigatório.");
        return arquivoLerDados(Path.of(caminho));
    }

    // =========================================================================
    // Métodos Auxiliares Privados de Persistência e Formatação
    // =========================================================================

    private void gravarRegistroCliente(BufferedWriter writer) throws IOException {
        String linha = String.join("\t",
                "C",
                escapar(dadosPessoais.getCpf()),
                escapar(dadosPessoais.getNome()),
                escapar(dadosPessoais.getCelular()),
                Integer.toString(numeroAgencia),
                Long.toString(numeroConta)
        );
        writer.write(linha);
        writer.newLine();
    }

    private void gravarRegistroAplicacao(BufferedWriter writer, MultiplasOperacoesFinanceiras multi) throws IOException {
        AplicacaoFinanceira af = multi.getAplicacao();
        if (af == null) {
            return;
        }

        String linha = String.join("\t",
                "I",
                escapar(dadosPessoais.getCpf()),
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

    private void gravarRegistrosOperacoes(BufferedWriter writer, MultiplasOperacoesFinanceiras multi) throws IOException {
        String numApp = extrairNumeroAplicacao(multi);
        for (OperacaoFinanceira op : multi.getOperacoes()) {
            String linha = String.join("\t",
                    "O",
                    escapar(dadosPessoais.getCpf()),
                    escapar(numApp),
                    op.getData().toString(),
                    op.getValor().toPlainString(),
                    escapar(op.getDescricao()),
                    op.getTipo().name()
            );
            writer.write(linha);
            writer.newLine();
        }
    }

    private static MultiplasOperacoesFinanceiras desserializarAplicacao(String[] campos) {
        if (campos == null || campos.length < 23) {
            throw new IllegalArgumentException("Registro de aplicação corrompido (campos insuficientes: "
                    + (campos == null ? 0 : campos.length) + ").");
        }

        String numero = desescapar(campos[2]);
        String nome = desescapar(campos[3]);
        BigDecimal valorAplicado = new BigDecimal(campos[4]);
        BigDecimal taxaMensal = new BigDecimal(campos[5]);
        LocalDate dataAplicacao = LocalDate.parse(campos[6]);

        int tipoRendimento = (campos.length > 15 && !campos[15].isBlank()) ? Integer.parseInt(campos[15]) : 0;
        int tempoVencimento = (campos.length > 16 && !campos[16].isBlank()) ? Integer.parseInt(campos[16]) : 0;
        int tempoPermanencia = (campos.length > 17 && !campos[17].isBlank()) ? Integer.parseInt(campos[17]) : 0;
        int tipoResgate = (campos.length > 18 && !campos[18].isBlank()) ? Integer.parseInt(campos[18]) : 0;
        int iof = (campos.length > 19 && !campos[19].isBlank()) ? Integer.parseInt(campos[19]) : 0;
        int tipoIR = (campos.length > 20 && !campos[20].isBlank()) ? Integer.parseInt(campos[20]) : 0;

        AplicacaoFinanceira af = new AplicacaoFinanceira(
                nome, numero, valorAplicado, taxaMensal,
                tipoRendimento, tempoVencimento, tempoPermanencia,
                tipoResgate, iof, tipoIR, dataAplicacao
        );

        // FASE 1: Configurações estruturais e parâmetros base que disparam recalcularPosicaoInterno
        if (campos.length > 13 && !campos[13].isBlank()) {
            af.setConvencaoContagemDias(ConvencaoContagemDias.valueOf(campos[13]));
        }
        if (campos.length > 14 && !campos[14].isBlank()) {
            af.setRegimeTributario(RegimeTributario.valueOf(campos[14]));
        }
        if (campos.length > 10 && !campos[10].isBlank()) {
            af.setDiasCorridos(Integer.parseInt(campos[10]));
        }
        if (campos.length > 11 && !campos[11].isBlank()) {
            af.setDiasEfetivosCalculo(Integer.parseInt(campos[11]));
        }
        if (campos.length > 9 && !campos[9].isBlank()) {
            af.setDataUltimaAtualizacao(LocalDate.parse(campos[9]));
        }
        if (campos.length > 12 && !campos[12].isBlank()) {
            af.setQuantidadeOperacoes(Integer.parseInt(campos[12]));
        }
        if (campos.length > 21 && !campos[21].isBlank()) {
            af.setValorResgate(new BigDecimal(campos[21]));
        }
        if (campos.length > 7 && !campos[7].isBlank()) {
            af.setSaldo(new BigDecimal(campos[7]));
        }

        // FASE 2: Grandezas patrimoniais e fiscais consolidadas intocáveis (após Fase 1)
        if (campos.length > 23 && !campos[23].isBlank()) {
            af.setRendimentoAtualBruto(new BigDecimal(campos[23]));
        }
        if (campos.length > 24 && !campos[24].isBlank()) {
            af.setRendimentoAtualLiquido(new BigDecimal(campos[24]));
        }
        if (campos.length > 25 && !campos[25].isBlank()) {
            af.setImpostoAtualDevido(new BigDecimal(campos[25]));
        }
        if (campos.length > 8 && !campos[8].isBlank()) {
            af.setValorAtualBruto(new BigDecimal(campos[8]));
        }
        if (campos.length > 22 && !campos[22].isBlank()) {
            af.setValorAtualLiquido(new BigDecimal(campos[22]));
        }

        return new MultiplasOperacoesFinanceiras(af);
    }

    private static OperacaoFinanceira desserializarOperacao(String[] campos) {
        if (campos == null || campos.length < 7) {
            throw new IllegalArgumentException("Registro de operação corrompido (campos insuficientes: "
                    + (campos == null ? 0 : campos.length) + ").");
        }
        LocalDate data = LocalDate.parse(campos[3]);
        BigDecimal valor = new BigDecimal(campos[4]);
        String descricao = desescapar(campos[5]);
        TipoOperacao tipo = TipoOperacao.valueOf(campos[6]);
        return new OperacaoFinanceira(data, valor, descricao, tipo);
    }

    private static String extrairNumeroAplicacao(MultiplasOperacoesFinanceiras multi) {
        if (multi == null || multi.getAplicacao() == null) {
            return null;
        }
        return multi.getAplicacao().getNumeroAplicacao();
    }

    private static void aplicarPermissoesSeguras(Path arquivo) {
        try {
            Files.setPosixFilePermissions(arquivo, PERMISSOES_POSIX_0600);
        } catch (UnsupportedOperationException | IOException ignored) {
            // Sistemas de arquivos sem suporte a atributos POSIX (ex.: FAT/NTFS)
        }
    }

    private static String escapar(String valor) {
        if (valor == null) return "";
        return valor.replace("\\", "\\\\")
                    .replace("\t", "\\t")
                    .replace("\r", "\\r")
                    .replace("\n", "\\n");
    }

    private static String desescapar(String valor) {
        if (valor == null || valor.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        boolean barra = false;
        for (int i = 0; i < valor.length(); i++) {
            char c = valor.charAt(i);
            if (barra) {
                switch (c) {
                    case '\\' -> sb.append('\\');
                    case 't' -> sb.append('\t');
                    case 'r' -> sb.append('\r');
                    case 'n' -> sb.append('\n');
                    default -> { sb.append('\\'); sb.append(c); }
                }
                barra = false;
            } else if (c == '\\') {
                barra = true;
            } else {
                sb.append(c);
            }
        }
        if (barra) sb.append('\\');
        return sb.toString();
    }

    // =========================================================================
    // Sobrescritas de Object e Métodos de Compatibilidade Legada
    // =========================================================================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Cliente cliente)) return false;
        return numeroAgencia == cliente.numeroAgencia &&
                numeroConta == cliente.numeroConta &&
                Objects.equals(dadosPessoais, cliente.dadosPessoais);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dadosPessoais, numeroAgencia, numeroConta);
    }

    @Override
    public String toString() {
        String titularFormatado = dadosPessoais != null ? dadosPessoais.toString() : "null";
        return String.format("Cliente{agencia=%d, conta=%d, titular=%s, aplicacoes=%d}",
                numeroAgencia, numeroConta, titularFormatado, aplicacoes.size());
    }

    // Aliases legados para preservação de compatibilidade transitória

    /**
     * Alias de compatibilidade com o legado para o número da agência.
     *
     * @return o número da agência bancária
     */
    public int getAgencyNumber() {
        return getNumeroAgencia();
    }

    /**
     * Alias de compatibilidade com o legado para o número da conta.
     *
     * @return o número da conta
     */
    public long getAccountNumber() {
        return getNumeroConta();
    }

    /**
     * Alias de compatibilidade com o legado para os dados pessoais.
     *
     * @return os dados cadastrais
     */
    public DADOSPESSOAIS getPersonalData() {
        return getDadosPessoais();
    }

    /**
     * Alias de compatibilidade com o legado para a lista de aplicações.
     *
     * @return lista imutável de aplicações
     */
    public List<MultiplasOperacoesFinanceiras> getInvestments() {
        return getAplicacoes();
    }

    /**
     * Alias de compatibilidade com o legado para adição de aplicação.
     *
     * @param terminal a aplicação a ser adicionada
     */
    public void addInvestment(MultiplasOperacoesFinanceiras terminal) {
        adicionarAplicacao(terminal);
    }

    /**
     * Alias de compatibilidade com o legado para busca de aplicação.
     *
     * @param numero o número da aplicação
     * @return optional com a aplicação se encontrada
     */
    public Optional<MultiplasOperacoesFinanceiras> findInvestment(String numero) {
        return buscarAplicacao(numero);
    }

    /**
     * Alias de compatibilidade com o legado para requisição obrigatória de aplicação.
     *
     * @param numero o número da aplicação
     * @return a aplicação financeira
     */
    public MultiplasOperacoesFinanceiras requireInvestment(String numero) {
        return requererAplicacao(numero);
    }
}

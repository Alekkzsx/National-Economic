package dominio;

import dominio.util.ArquivoTSV;

import java.io.BufferedReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Item 6 — Gestão bancária de um cliente: {@link DADOSPESSOAIS} (Item 5), agência, conta
 * e carteira de até 50 aplicações financeiras ({@link MultiplasOperacoesFinanceiras}, Item 4).
 *
 * <p>Classe passível de herança, com construtores seguros sem despacho virtual.</p>
 */
public class Cliente {

    public static final int LIMITE_APLICACOES = 50;

    private DADOSPESSOAIS dadosPessoais;
    private int numeroAgencia;
    private long numeroConta;
    private final List<MultiplasOperacoesFinanceiras> aplicacoes = new ArrayList<>();

    /** Construtor protegido para herança segura. */
    protected Cliente() {
    }

    /** Cria o cliente validando dados pessoais, agência (> 0) e conta (> 0). */
    public Cliente(DADOSPESSOAIS dadosPessoais, int numeroAgencia, long numeroConta) {
        aplicarDadosBancarios(dadosPessoais, numeroAgencia, numeroConta);
    }

    /** Construtor de conveniência a partir dos dados literais do titular. */
    public Cliente(String nome, String cpf, String celular, int numeroAgencia, long numeroConta) {
        this(new DADOSPESSOAIS(nome, cpf, celular), numeroAgencia, numeroConta);
    }

    private void aplicarDadosBancarios(DADOSPESSOAIS dados, int agencia, long conta) {
        Objects.requireNonNull(dados, "Dados pessoais são obrigatórios.");
        if (agencia <= 0) {
            throw new IllegalArgumentException("Número da agência deve ser positivo (> 0).");
        }
        if (conta <= 0) {
            throw new IllegalArgumentException("Número da conta deve ser positivo (> 0).");
        }
        this.dadosPessoais = dados;
        this.numeroAgencia = agencia;
        this.numeroConta = conta;
    }

    // -------------------------------------------------------------------------
    // Cadastro (Item 6)
    // -------------------------------------------------------------------------

    /** Atualiza atomicamente os dados cadastrais e bancários do cliente. */
    public void setCadastro(DADOSPESSOAIS dadosPessoais, int numeroAgencia, long numeroConta) {
        aplicarDadosBancarios(dadosPessoais, numeroAgencia, numeroConta);
    }

    /** Sobrecarga polimórfica que cria os dados pessoais e atualiza agência e conta atomicamente. */
    public void setCadastro(String nome, String cpf, String celular, int numeroAgencia, long numeroConta) {
        aplicarDadosBancarios(new DADOSPESSOAIS(nome, cpf, celular), numeroAgencia, numeroConta);
    }

    // -------------------------------------------------------------------------
    // Getters e Setters básicos
    // -------------------------------------------------------------------------

    public DADOSPESSOAIS getDadosPessoais() { return dadosPessoais; }

    public void setDadosPessoais(DADOSPESSOAIS dadosPessoais) {
        this.dadosPessoais = Objects.requireNonNull(dadosPessoais, "Dados pessoais são obrigatórios.");
    }

    public int getNumeroAgencia() { return numeroAgencia; }

    public void setNumeroAgencia(int numeroAgencia) {
        if (numeroAgencia <= 0) {
            throw new IllegalArgumentException("Número da agência deve ser positivo (> 0).");
        }
        this.numeroAgencia = numeroAgencia;
    }

    public long getNumeroConta() { return numeroConta; }

    public void setNumeroConta(long numeroConta) {
        if (numeroConta <= 0) {
            throw new IllegalArgumentException("Número da conta deve ser positivo (> 0).");
        }
        this.numeroConta = numeroConta;
    }

    public int getQuantidadeAplicacoes() { return aplicacoes.size(); }

    public List<MultiplasOperacoesFinanceiras> getAplicacoes() {
        return Collections.unmodifiableList(aplicacoes);
    }

    /** Substitui atomicamente a carteira do cliente respeitando limite de 50 e unicidade. */
    public void setAplicacoes(List<MultiplasOperacoesFinanceiras> novasAplicacoes) {
        Objects.requireNonNull(novasAplicacoes, "Lista de aplicações é obrigatória.");
        if (novasAplicacoes.size() > LIMITE_APLICACOES) {
            throw new IllegalStateException("Limite de 50 aplicações por cliente atingido.");
        }
        List<MultiplasOperacoesFinanceiras> validadas = new ArrayList<>(novasAplicacoes.size());
        Set<String> numerosVistos = new HashSet<>();
        for (MultiplasOperacoesFinanceiras item : novasAplicacoes) {
            Objects.requireNonNull(item, "Aplicação financeira é obrigatória.");
            String numero = extrairNumeroAplicacao(item);
            if (numero != null && !numerosVistos.add(numero)) {
                throw new IllegalArgumentException("Número de aplicação duplicado para este cliente: " + numero);
            }
            validadas.add(item);
        }
        aplicacoes.clear();
        aplicacoes.addAll(validadas);
    }

    // -------------------------------------------------------------------------
    // Gestão da carteira de aplicações (máx. 50)
    // -------------------------------------------------------------------------

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

    public void adicionarAplicacao(AplicacaoFinanceira aplicacaoBase) {
        Objects.requireNonNull(aplicacaoBase, "Aplicação financeira é obrigatória.");
        adicionarAplicacao(new MultiplasOperacoesFinanceiras(aplicacaoBase));
    }

    public MultiplasOperacoesFinanceiras removerAplicacao(String numeroAplicacao) {
        MultiplasOperacoesFinanceiras aplicacao = requererAplicacao(numeroAplicacao);
        aplicacoes.remove(aplicacao);
        return aplicacao;
    }

    public Optional<MultiplasOperacoesFinanceiras> buscarAplicacao(String numeroAplicacao) {
        if (numeroAplicacao == null || numeroAplicacao.isBlank()) {
            return Optional.empty();
        }
        String procurado = numeroAplicacao.trim();
        for (MultiplasOperacoesFinanceiras item : aplicacoes) {
            if (Objects.equals(extrairNumeroAplicacao(item), procurado)) {
                return Optional.of(item);
            }
        }
        return Optional.empty();
    }

    public MultiplasOperacoesFinanceiras requererAplicacao(String numeroAplicacao) {
        return buscarAplicacao(numeroAplicacao).orElseThrow(() ->
                new IllegalArgumentException("Aplicação não encontrada para o número: " + numeroAplicacao));
    }

    // -------------------------------------------------------------------------
    // Atualização em dias úteis (segunda a sexta via Item 4)
    // -------------------------------------------------------------------------

    /** Atualiza todas as aplicações do cliente na data atual (nome do enunciado). */
    public void atualizarAplicacao() {
        atualizarAplicacao(LocalDate.now());
    }

    /** Atualiza todas as aplicações do cliente na data informada (somente dias úteis). */
    public void atualizarAplicacao(LocalDate data) {
        Objects.requireNonNull(data, "Data de atualização é obrigatória.");
        for (MultiplasOperacoesFinanceiras item : aplicacoes) {
            item.atualizarAplicacao(data);
        }
    }

    public void atualizarAplicacoes() {
        atualizarAplicacao();
    }

    public void atualizarAplicacoes(LocalDate data) {
        atualizarAplicacao(data);
    }

    // -------------------------------------------------------------------------
    // Saldos e Extratos (individual e consolidado total)
    // -------------------------------------------------------------------------

    /** Retorna o saldo líquido consolidado de todas as aplicações do cliente. */
    public BigDecimal getSaldo() {
        return getSaldoConsolidado();
    }

    /** Retorna o saldo líquido de uma aplicação específica do cliente. */
    public BigDecimal getSaldo(String numeroAplicacao) {
        return requererAplicacao(numeroAplicacao).getSaldo();
    }

    public BigDecimal getSaldoConsolidado() {
        BigDecimal total = BigDecimal.ZERO;
        for (MultiplasOperacoesFinanceiras item : aplicacoes) {
            if (item.getSaldo() != null) {
                total = total.add(item.getSaldo());
            }
        }
        return Moeda.arredondar(total);
    }

    /** Retorna o extrato consolidado de todas as aplicações nos últimos 30 dias. */
    public List<OperacaoFinanceira> getExtrato() {
        return getExtratoConsolidado();
    }

    /** Retorna o extrato consolidado de todas as aplicações entre duas datas (polimórfico). */
    public List<OperacaoFinanceira> getExtrato(LocalDate inicio, LocalDate fim) {
        return getExtratoConsolidado(inicio, fim);
    }

    public List<OperacaoFinanceira> getExtrato(String numeroAplicacao) {
        return requererAplicacao(numeroAplicacao).getExtrato();
    }

    public List<OperacaoFinanceira> getExtrato(String numeroAplicacao, LocalDate inicio, LocalDate fim) {
        return requererAplicacao(numeroAplicacao).getExtrato(inicio, fim);
    }

    public List<OperacaoFinanceira> getExtratoConsolidado() {
        List<OperacaoFinanceira> consolidado = new ArrayList<>();
        for (MultiplasOperacoesFinanceiras item : aplicacoes) {
            consolidado.addAll(item.getExtrato());
        }
        consolidado.sort(Comparator.comparing(OperacaoFinanceira::getData));
        return Collections.unmodifiableList(consolidado);
    }

    public List<OperacaoFinanceira> getExtratoConsolidado(LocalDate inicio, LocalDate fim) {
        Objects.requireNonNull(inicio, "Data inicial é obrigatória.");
        Objects.requireNonNull(fim, "Data final é obrigatória.");
        if (fim.isBefore(inicio)) {
            throw new IllegalArgumentException("A data final do período não pode anteceder a data inicial.");
        }
        List<OperacaoFinanceira> consolidado = new ArrayList<>();
        for (MultiplasOperacoesFinanceiras item : aplicacoes) {
            consolidado.addAll(item.getExtrato(inicio, fim));
        }
        consolidado.sort(Comparator.comparing(OperacaoFinanceira::getData));
        return Collections.unmodifiableList(consolidado);
    }

    // -------------------------------------------------------------------------
    // Persistência individual em arquivo TSV v3 (via dominio.util.ArquivoTSV)
    // -------------------------------------------------------------------------

    public void arquivoSalvarDados(String caminho) throws IOException {
        Objects.requireNonNull(caminho, "Caminho do arquivo é obrigatório.");
        arquivoSalvarDados(Path.of(caminho));
    }

    public void arquivoSalvarDados(Path caminho) throws IOException {
        Objects.requireNonNull(caminho, "Caminho do arquivo é obrigatório.");
        String cpf = dadosPessoais.getCpf();
        ArquivoTSV.escreverAtomico(caminho, writer -> {
            writer.write(ArquivoTSV.CABECALHO_V3);
            writer.newLine();
            writer.write(ArquivoTSV.formatarLinhaCliente(this));
            writer.newLine();

            for (MultiplasOperacoesFinanceiras item : aplicacoes) {
                if (item.getAplicacao() == null) continue;
                writer.write(ArquivoTSV.formatarLinhaAplicacao(cpf, item.getAplicacao()));
                writer.newLine();
                String numApp = item.getAplicacao().getNumeroAplicacao();
                for (OperacaoFinanceira op : item.getOperacoes()) {
                    writer.write(ArquivoTSV.formatarLinhaOperacao(cpf, numApp, op));
                    writer.newLine();
                }
            }
        });
    }

    public static Cliente arquivoLerDados(String caminho) throws IOException {
        Objects.requireNonNull(caminho, "Caminho do arquivo é obrigatório.");
        return arquivoLerDados(Path.of(caminho));
    }

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
            int numeroLinha = 1;

            while ((linha = reader.readLine()) != null) {
                numeroLinha++;
                linha = linha.trim();
                if (linha.isEmpty()) continue;

                List<String> campos = ArquivoTSV.parseRegistro(linha, numeroLinha);
                switch (campos.get(0)) {
                    case "C" -> {
                        cliente = ArquivoTSV.desserializarCliente(campos, numeroLinha);
                        aplicacaoAtual = null;
                    }
                    case "I" -> {
                        if (cliente == null) {
                            throw new IllegalArgumentException("Registro de aplicação antes do registro de cliente.");
                        }
                        aplicacaoAtual = ArquivoTSV.desserializarAplicacao(campos, 23, numeroLinha);
                        cliente.adicionarAplicacao(aplicacaoAtual);
                    }
                    case "O" -> {
                        if (aplicacaoAtual == null) {
                            throw new IllegalArgumentException("Registro de operação sem aplicação associada.");
                        }
                        aplicacaoAtual.vincularOperacaoRestaurada(
                                ArquivoTSV.desserializarOperacao(campos, numeroLinha));
                    }
                    default -> {
                        // Registros desconhecidos são ignorados na leitura individual
                    }
                }
            }

            if (cliente == null) {
                throw new IllegalArgumentException("Nenhum registro de cliente foi localizado no arquivo.");
            }
            return cliente;
        }
    }

    private static String extrairNumeroAplicacao(MultiplasOperacoesFinanceiras item) {
        return (item == null || item.getAplicacao() == null) ? null : item.getAplicacao().getNumeroAplicacao();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Cliente cliente)) return false;
        return numeroAgencia == cliente.numeroAgencia
                && numeroConta == cliente.numeroConta
                && Objects.equals(dadosPessoais, cliente.dadosPessoais);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dadosPessoais, numeroAgencia, numeroConta);
    }

    @Override
    public String toString() {
        String titular = dadosPessoais != null ? dadosPessoais.toString() : "null";
        return String.format("Cliente{agencia=%d, conta=%d, titular=%s, aplicacoes=%d}",
                numeroAgencia, numeroConta, titular, aplicacoes.size());
    }
}

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
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Agrega uma Aplicação Financeira, até 100 lançamentos de Operação Financeira
 * e o Calendário de dias úteis, em conformidade com o Item 4 da Atividade Prática Acadêmica.
 *
 * <p>Esta classe coordena a restrição operacional de atualização financeira exclusivamente
 * aos dias úteis (segunda a sexta-feira), gera extratos de movimentações (polimórficos de 30
 * dias e entre datas arbitrárias), disponibiliza consultas de saldo patrimonial líquido e bruto,
 * e implementa persistência autônoma em disco via {@link #arquivoSalvarDados} e {@link #arquivoLerDados}.</p>
 *
 * <p>A classe é passível de herança (não-final) e adota construtores seguros que não disparam
 * chamadas a métodos sobrescrevíveis, prevenindo corrupção de estado em subclasses especializadas.</p>
 */
public class MultiplasOperacoesFinanceiras {

    /** Limite estrito de operações de fluxo por aplicação financeira exigido na especificação acadêmica. */
    public static final int LIMITE_OPERACOES = 100;

    /** Cabeçalho do arquivo TSV de persistência de múltiplas operações versão 1. */
    private static final String CABECALHO_ARQUIVO_V1 = "SGI-MOF\t1";
    private static final String CABECALHO_ARQUIVO = "SGI-MOF\t2";

    /** Instância da aplicação financeira vinculada (Item 1). */
    private AplicacaoFinanceira aplicacao;

    /** Coleção sequencial de até 100 operações financeiras vinculadas (Item 2). */
    private final List<OperacaoFinanceira> operacoes = new ArrayList<>();

    /** Instância do calendário para cálculo de dias da semana e dias úteis (Item 3). */
    private DiaSemana calendario;

    /**
     * Construtor protegido padrão para viabilizar herança segura por subclasses.
     */
    protected MultiplasOperacoesFinanceiras() {
    }

    /**
     * Construtor principal que vincula uma aplicação financeira e cria o calendário na data de início.
     *
     * @param aplicacao a aplicação financeira a ser agregada (não nula)
     * @throws IllegalArgumentException se a aplicação for nula
     */
    public MultiplasOperacoesFinanceiras(AplicacaoFinanceira aplicacao) {
        if (aplicacao == null) {
            throw new IllegalArgumentException("Aplicação financeira é obrigatória.");
        }
        LocalDate dataBase = (aplicacao.getDataAplicacao() != null)
                ? aplicacao.getDataAplicacao()
                : LocalDate.now();
        inicializarInterno(aplicacao, new DiaSemana(dataBase));
    }

    /**
     * Construtor completo que associa explicitamente a aplicação e o calendário.
     *
     * @param aplicacao a aplicação financeira a ser agregada (não nula)
     * @param calendario o calendário de cálculo de dias úteis (não nulo)
     * @throws IllegalArgumentException se a aplicação ou o calendário forem nulos
     */
    public MultiplasOperacoesFinanceiras(AplicacaoFinanceira aplicacao, DiaSemana calendario) {
        if (aplicacao == null) {
            throw new IllegalArgumentException("Aplicação financeira é obrigatória.");
        }
        if (calendario == null) {
            throw new IllegalArgumentException("Calendário de dias da semana é obrigatório.");
        }
        inicializarInterno(aplicacao, calendario);
    }

    /**
     * Atribuição privada segura de campos internos para impedir escape virtual em construtores.
     */
    private void inicializarInterno(AplicacaoFinanceira aplicacao, DiaSemana calendario) {
        this.aplicacao = aplicacao;
        this.calendario = calendario;
    }

    // =========================================================================
    // Gestão de Operações Financeiras (Item 4, Requisito R2.1)
    // =========================================================================

    /**
     * Registra e aplica um lançamento individual de operação financeira (depósito ou retirada).
     *
     * <p>Valida o limite de até 100 operações, a conformidade da data da operação em relação à data
     * de aplicação, e reflete atomicamente o impacto financeiro no saldo da aplicação agregada.</p>
     *
     * @param operacao a operação financeira a ser cadastrada
     * @throws IllegalArgumentException se a operação for nula, com data anterior à aplicação,
     *                                  fora da carência/frequência de resgate ou com saldo insuficiente
     * @throws IllegalStateException se o limite de 100 operações já tiver sido alcançado
     */
    public void adicionarOperacao(OperacaoFinanceira operacao) {
        if (operacao == null) {
            throw new IllegalArgumentException("Operação financeira não pode ser nula.");
        }
        if (this.operacoes.size() >= LIMITE_OPERACOES) {
            throw new IllegalStateException("Limite de 100 operações por aplicação atingido.");
        }
        if (operacao.getData().isBefore(this.aplicacao.getDataAplicacao())) {
            throw new IllegalArgumentException("Operação não pode ter data anterior à data de início da aplicação.");
        }

        if (operacao.ehDeposito()) {
            // Depósito: incrementa o saldo base creditado da aplicação
            BigDecimal novoSaldo = this.aplicacao.getSaldo().add(operacao.getValor());
            this.aplicacao.setSaldo(novoSaldo);
        } else {
            // Retirada: valida regras de carência e frequência de resgate antes da dedução
            long diasAteOperacao = ChronoUnit.DAYS.between(this.aplicacao.getDataAplicacao(), operacao.getData());
            if (diasAteOperacao < this.aplicacao.getTempoMinimoPermanencia()) {
                throw new IllegalArgumentException("Resgate fora da carência mínima de permanência.");
            }

            int intervalo = switch (this.aplicacao.getTipoResgate()) {
                case 0 -> 1;   // Diário
                case 1 -> 30;  // Mensal
                case 2 -> 90;  // Trimestral
                case 3 -> 180; // Semestral
                case 4 -> 365; // Anual
                default -> 1;
            };

            if (intervalo > 1 && (diasAteOperacao == 0 || diasAteOperacao % intervalo != 0)) {
                throw new IllegalArgumentException("Resgate fora da frequência permitida.");
            }

            // Efetua o resgate patrimonial (lança IllegalArgumentException se saldo líquido insuficiente)
            this.aplicacao.resgatar(operacao.getValor());
        }

        this.operacoes.add(operacao);
        this.aplicacao.setQuantidadeOperacoes(this.operacoes.size());
    }

    /**
     * Vincula uma operação financeira restaurada do arquivo em disco sem reprocessar saldo financeiro.
     *
     * @param operacao operação restaurada
     * @throws IllegalArgumentException se a operação for nula
     * @throws IllegalStateException se o limite de operações já tiver sido atingido
     */
    public void vincularOperacaoRestaurada(OperacaoFinanceira operacao) {
        if (operacao == null) {
            throw new IllegalArgumentException("Operação restaurada não pode ser nula.");
        }
        if (this.operacoes.size() >= LIMITE_OPERACOES) {
            throw new IllegalStateException("Limite de 100 operações por aplicação atingido.");
        }
        this.operacoes.add(operacao);
        this.aplicacao.setQuantidadeOperacoes(this.operacoes.size());
    }

    /**
     * Substitui ou remove um lançamento reconstruindo o saldo sem juros acumulados.
     *
     * <p>A operação é recusada depois de qualquer apuração de rendimento, pois
     * nesse caso o histórico financeiro precisaria ser recalculado desde o início.</p>
     *
     * @param indice índice baseado em zero do lançamento a substituir ou remover
     * @param novaOperacao novo lançamento; nulo remove o lançamento existente
     * @throws IllegalStateException se já houver rendimento apurado
     */
    public void alterarOperacao(int indice, OperacaoFinanceira novaOperacao) {
        if (indice < 0 || indice >= operacoes.size()) {
            throw new IndexOutOfBoundsException("Índice de operação fora do intervalo.");
        }
        if (aplicacao.getDiasEfetivosCalculo() > 0
                || aplicacao.getRendimentoAtualBruto().signum() > 0) {
            throw new IllegalStateException(
                    "Não é possível alterar operações após a apuração de rendimentos.");
        }

        List<OperacaoFinanceira> atualizadas = new ArrayList<>(operacoes);
        if (novaOperacao == null) {
            atualizadas.remove(indice);
        } else {
            atualizadas.set(indice, novaOperacao);
        }

        AplicacaoFinanceira reconstruida = reconstruirAplicacao();
        MultiplasOperacoesFinanceiras validacao = new MultiplasOperacoesFinanceiras(
                reconstruida, new DiaSemana(reconstruida.getDataAplicacao()));
        for (OperacaoFinanceira operacao : atualizadas) {
            validacao.adicionarOperacao(operacao);
        }
        this.aplicacao = validacao.getAplicacao();
        this.operacoes.clear();
        this.operacoes.addAll(atualizadas);
        this.calendario = validacao.getCalendario();
        this.aplicacao.setQuantidadeOperacoes(this.operacoes.size());
    }

    private AplicacaoFinanceira reconstruirAplicacao() {
        AplicacaoFinanceira original = this.aplicacao;
        AplicacaoFinanceira reconstruida = new AplicacaoFinanceira(
                original.getNomeAplicacao(),
                original.getNumeroAplicacao(),
                original.getValorAplicado(),
                original.getTaxaMensal(),
                original.getTipoRendimento(),
                original.getTempoFinalVencimento(),
                original.getTempoMinimoPermanencia(),
                original.getTipoResgate(),
                original.getIof(),
                original.getTipoImpostoRenda(),
                original.getDataAplicacao()
        );
        reconstruida.setConvencaoContagemDias(original.getConvencaoContagemDias());
        reconstruida.setRegimeTributario(original.getRegimeTributario());
        return reconstruida;
    }

    // =========================================================================
    // Atualização Patrimonial em Dias Úteis (Item 4, Requisito R2.1)
    // =========================================================================

    /**
     * Atualiza a aplicação financeira na data corrente, restrita exclusivamente aos dias de semana.
     */
    public void atualizarAplicacao() {
        atualizarAplicacao(LocalDate.now());
    }

    /**
     * Atualiza o saldo e rentabilidade da aplicação até a data informada, restrita exclusivamente
     * aos dias úteis (segunda a sexta-feira).
     *
     * <p>Caso a data coincida com sábado ou domingo, nenhum rendimento diário é processado e
     * o estado operacional permanece inalterado.</p>
     *
     * @param data data de referência para a atualização
     * @throws IllegalArgumentException se a data for nula ou anterior à data de início da aplicação
     */
    public void atualizarAplicacao(LocalDate data) {
        if (data == null) {
            throw new IllegalArgumentException("Data de referência para atualização não pode ser nula.");
        }
        if (data.isBefore(this.aplicacao.getDataAplicacao())) {
            throw new IllegalArgumentException("Data de referência não pode ser anterior à data da aplicação.");
        }

        if (this.calendario == null) {
            this.calendario = new DiaSemana(data);
        } else {
            this.calendario.setCadastro(data);
        }

        // Restrição exclusiva aos dias de semana: sábado (0) e domingo (1) são ignorados
        if (!this.calendario.ehDiaUtil()) {
            return;
        }

        this.aplicacao.atualizarSaldo(data);
    }

    // =========================================================================
    // Extratos Polimórficos (Item 4, Requisito R2.1)
    // =========================================================================

    /**
     * Retorna o extrato das operações realizadas nos últimos 30 dias corridos em relação ao calendário atual.
     *
     * @return lista de operações financeiras no período de 30 dias
     */
    public List<OperacaoFinanceira> getExtrato() {
        LocalDate fim = (this.calendario != null && this.calendario.getData() != null)
                ? this.calendario.getData()
                : LocalDate.now();
        LocalDate inicio = fim.minusDays(29);
        return getExtrato(inicio, fim);
    }

    /**
     * Sobrecarga polimórfica que retorna o extrato dos últimos 30 dias corridos retroativos a uma data de referência.
     *
     * @param dataReferencia data limite final do período de 30 dias
     * @return lista de operações financeiras no período
     * @throws IllegalArgumentException se a dataReferencia for nula
     */
    public List<OperacaoFinanceira> getExtrato(LocalDate dataReferencia) {
        if (dataReferencia == null) {
            throw new IllegalArgumentException("Data de referência não pode ser nula.");
        }
        return getExtrato(dataReferencia.minusDays(29), dataReferencia);
    }

    /**
     * Sobrecarga polimórfica que retorna as operações realizadas no período inclusivo entre duas datas arbitrárias.
     *
     * @param inicio data inicial do extrato (inclusiva)
     * @param fim data final do extrato (inclusiva)
     * @return lista ordenada cronologicamente com as operações pertencentes ao intervalo
     * @throws IllegalArgumentException se as datas forem nulas ou se fim for anterior a inicio
     */
    public List<OperacaoFinanceira> getExtrato(LocalDate inicio, LocalDate fim) {
        if (inicio == null) {
            throw new IllegalArgumentException("Data inicial obrigatória.");
        }
        if (fim == null) {
            throw new IllegalArgumentException("Data final obrigatória.");
        }
        if (fim.isBefore(inicio)) {
            throw new IllegalArgumentException("A data final não pode ser anterior à data inicial.");
        }

        return this.operacoes.stream()
                .filter(op -> !op.getData().isBefore(inicio) && !op.getData().isAfter(fim))
                .sorted(Comparator.comparing(OperacaoFinanceira::getData))
                .toList();
    }

    /**
     * Sobrecarga polimórfica de extrato a partir dos inteiros de dia, mês e ano de início e fim.
     *
     * @param diaIni dia inicial
     * @param mesIni mês inicial
     * @param anoIni ano inicial
     * @param diaFim dia final
     * @param mesFim mês final
     * @param anoFim ano final
     * @return lista de operações no intervalo
     */
    public List<OperacaoFinanceira> getExtrato(int diaIni, int mesIni, int anoIni, int diaFim, int mesFim, int anoFim) {
        return getExtrato(LocalDate.of(anoIni, mesIni, diaIni), LocalDate.of(anoFim, mesFim, diaFim));
    }

    /**
     * Retorna uma visão somente leitura de todas as operações financeiras registradas.
     *
     * @return lista não modificável de operações
     */
    public List<OperacaoFinanceira> getOperacoes() {
        return Collections.unmodifiableList(this.operacoes);
    }

    /**
     * Retorna a quantidade atual de operações registradas.
     *
     * @return número de lançamentos (0 a 100)
     */
    public int getQuantidadeOperacoes() {
        return this.operacoes.size();
    }

    // =========================================================================
    // Consultas de Saldo e Rentabilidade (Item 4)
    // =========================================================================

    /**
     * Retorna o saldo patrimonial líquido atual da aplicação financeira (descontados tributos devidos).
     *
     * @return saldo líquido em reais com 2 casas decimais
     */
    public BigDecimal getSaldo() {
        return this.aplicacao.getValorAtualLiquido();
    }

    /**
     * Alias explícito para saldo patrimonial líquido.
     *
     * @return saldo líquido
     */
    public BigDecimal getSaldoLiquido() {
        return getSaldo();
    }

    /**
     * Retorna o saldo patrimonial bruto atual (saldo base + rendimentos antes do imposto).
     *
     * @return saldo bruto
     */
    public BigDecimal getSaldoBruto() {
        return this.aplicacao.getValorAtualBruto();
    }

    /**
     * Retorna o rendimento bruto acumulado.
     *
     * @return rendimento bruto
     */
    public BigDecimal getRendimentoBruto() {
        return this.aplicacao.getRendimentoAtualBruto();
    }

    /**
     * Retorna o rendimento líquido acumulado após deduções de IOF e IR.
     *
     * @return rendimento líquido
     */
    public BigDecimal getRendimentoLiquido() {
        return this.aplicacao.getRendimentoAtualLiquido();
    }

    /**
     * Retorna o total de tributos devidos apurados (IOF + IR).
     *
     * @return total de impostos devidos
     */
    public BigDecimal getImpostoDevido() {
        return this.aplicacao.getImpostoAtualDevido();
    }

    /**
     * Retorna o valor inicialmente aplicado no investimento.
     *
     * @return valor aplicado
     */
    public BigDecimal getValorAplicado() {
        return this.aplicacao.getValorAplicado();
    }

    /**
     * Retorna o montante total de resgates já efetuados na aplicação.
     *
     * @return valor total resgatado
     */
    public BigDecimal getValorResgate() {
        return this.aplicacao.getValorResgate();
    }

    /**
     * Alias de compatibilidade com o legado para saldo líquido.
     *
     * @return saldo patrimonial líquido
     */
    public BigDecimal getNetBalance() {
        return getSaldo();
    }

    /**
     * Alias de compatibilidade com o legado para saldo bruto.
     *
     * @return saldo patrimonial bruto
     */
    public BigDecimal getBalance() {
        return getSaldoBruto();
    }

    // =========================================================================
    // Getters e Setters
    // =========================================================================

    /**
     * Retorna a aplicação financeira vinculada.
     *
     * @return a aplicação financeira agregada
     */
    public AplicacaoFinanceira getAplicacao() {
        return aplicacao;
    }

    /**
     * Define a aplicação financeira vinculada.
     *
     * @param aplicacao nova aplicação financeira (não nula)
     * @throws IllegalArgumentException se a aplicação for nula
     */
    public void setAplicacao(AplicacaoFinanceira aplicacao) {
        if (aplicacao == null) {
            throw new IllegalArgumentException("Aplicação financeira não pode ser nula.");
        }
        this.aplicacao = aplicacao;
    }

    /**
     * Retorna o calendário vinculado.
     *
     * @return o calendário de dias úteis
     */
    public DiaSemana getCalendario() {
        return calendario;
    }

    /**
     * Define o calendário vinculado.
     *
     * @param calendario novo calendário de dias úteis (não nulo)
     * @throws IllegalArgumentException se o calendário for nulo
     */
    public void setCalendario(DiaSemana calendario) {
        if (calendario == null) {
            throw new IllegalArgumentException("Calendário não pode ser nulo.");
        }
        this.calendario = calendario;
    }

    /**
     * Retorna o número identificador único da aplicação agregada.
     *
     * @return número da aplicação
     */
    public String getNumeroAplicacao() {
        return this.aplicacao.getNumeroAplicacao();
    }

    /**
     * Retorna o nome da aplicação agregada.
     *
     * @return nome descritivo da aplicação
     */
    public String getNomeAplicacao() {
        return this.aplicacao.getNomeAplicacao();
    }

    // =========================================================================
    // Persistência em Disco: arquivoSalvarDados e arquivoLerDados (Item 4)
    // =========================================================================

    /**
     * Salva os dados completos desta agregação em arquivo no caminho padrão data/aplicacao_&lt;numero&gt;.db.
     *
     * @throws IOException se ocorrer falha na gravação do arquivo
     */
    public void arquivoSalvarDados() throws IOException {
        String nome = "data/aplicacao_" + this.aplicacao.getNumeroAplicacao() + ".db";
        arquivoSalvarDados(Path.of(nome));
    }

    /**
     * Salva os dados completos desta agregação no arquivo indicado.
     *
     * @param caminho caminho de destino em texto
     * @throws IOException se ocorrer erro na gravação
     * @throws IllegalArgumentException se o caminho for nulo ou vazio
     */
    public void arquivoSalvarDados(String caminho) throws IOException {
        if (caminho == null || caminho.isBlank()) {
            throw new IllegalArgumentException("Caminho do arquivo não pode ser nulo ou vazio.");
        }
        arquivoSalvarDados(Path.of(caminho));
    }

    /**
     * Salva os dados de forma atômica no arquivo especificado via {@link Path}.
     *
     * @param caminho caminho do arquivo
     * @throws IOException se ocorrer erro de E/S
     * @throws NullPointerException se caminho for nulo
     */
    public void arquivoSalvarDados(Path caminho) throws IOException {
        Objects.requireNonNull(caminho, "Caminho do arquivo é obrigatório.");
        Path absoluto = caminho.toAbsolutePath();
        Path diretorioPai = absoluto.getParent();
        if (diretorioPai != null) {
            Files.createDirectories(diretorioPai);
        }

        Path arquivoTemporario = absoluto.resolveSibling(absoluto.getFileName() + ".tmp");
        try {
            try (BufferedWriter writer = Files.newBufferedWriter(arquivoTemporario, StandardCharsets.UTF_8)) {
                writer.write(CABECALHO_ARQUIVO);
                writer.newLine();

                // Linha I: Dados completos da AplicacaoFinanceira
                writer.write(String.join("\t",
                        "I",
                        escaparTexto(this.aplicacao.getNumeroAplicacao()),
                        escaparTexto(this.aplicacao.getNomeAplicacao()),
                        this.aplicacao.getValorAplicado().toPlainString(),
                        this.aplicacao.getTaxaMensal().toPlainString(),
                        this.aplicacao.getTaxaDiaria().toPlainString(),
                        Integer.toString(this.aplicacao.getTipoRendimento()),
                        Integer.toString(this.aplicacao.getTempoFinalVencimento()),
                        Integer.toString(this.aplicacao.getTempoMinimoPermanencia()),
                        Integer.toString(this.aplicacao.getTipoResgate()),
                        Integer.toString(this.aplicacao.getIof()),
                        Integer.toString(this.aplicacao.getTipoImpostoRenda()),
                        this.aplicacao.getDataAplicacao().toString(),
                        this.aplicacao.getSaldo().toPlainString(),
                        this.aplicacao.getRendimentoAtualBruto().toPlainString(),
                        this.aplicacao.getRendimentoAtualLiquido().toPlainString(),
                        (this.aplicacao.getDataUltimaAtualizacao() != null
                                ? this.aplicacao.getDataUltimaAtualizacao().toString()
                                : this.aplicacao.getDataAplicacao().toString()),
                        Integer.toString(this.aplicacao.getDiasCorridos()),
                        Integer.toString(this.aplicacao.getDiasEfetivosCalculo()),
                        this.aplicacao.getValorResgate().toPlainString(),
                        this.aplicacao.getConvencaoContagemDias().name(),
                        this.aplicacao.getRegimeTributario().name(),
                        this.aplicacao.getRendimentosCreditados().toPlainString(),
                        this.aplicacao.getRendimentosPendentes().toPlainString(),
                        Integer.toString(this.aplicacao.getDiasDesdeUltimoRendimento())
                ));
                writer.newLine();

                // Linha K: Dados do Calendário DiaSemana
                if (this.calendario != null) {
                    writer.write(String.join("\t",
                            "K",
                            Integer.toString(this.calendario.getDia()),
                            Integer.toString(this.calendario.getMes()),
                            Integer.toString(this.calendario.getAno()),
                            Integer.toString(this.calendario.getDiaSemana())
                    ));
                    writer.newLine();
                }

                // Linhas O: Operações Financeiras cadastradas
                for (OperacaoFinanceira op : this.operacoes) {
                    writer.write(String.join("\t",
                            "O",
                            Integer.toString(op.getDia()),
                            Integer.toString(op.getMes()),
                            Integer.toString(op.getAno()),
                            op.getData().toString(),
                            op.getValor().toPlainString(),
                            op.getTipo().name(),
                            escaparTexto(op.getDescricao())
                    ));
                    writer.newLine();
                }
            }

            definirPermissoesPrivadas(arquivoTemporario);
            substituirArquivoAtomicamente(arquivoTemporario, absoluto);
        } finally {
            Files.deleteIfExists(arquivoTemporario);
        }
    }

    /**
     * Restaura os dados de uma instância a partir de caminho de texto.
     *
     * @param caminho caminho do arquivo
     * @return nova instância reconstituída
     * @throws IOException se o arquivo não existir ou for inválido
     * @throws IllegalArgumentException se o caminho for nulo ou vazio
     */
    public static MultiplasOperacoesFinanceiras arquivoLerDados(String caminho) throws IOException {
        if (caminho == null || caminho.isBlank()) {
            throw new IllegalArgumentException("Caminho do arquivo não pode ser nulo ou vazio.");
        }
        return arquivoLerDados(Path.of(caminho));
    }

    /**
     * Restaura os dados de uma instância a partir de {@link Path}.
     *
     * @param caminho caminho do arquivo
     * @return nova instância reconstituída
     * @throws IOException se o arquivo não existir ou for inválido
     * @throws NullPointerException se o caminho for nulo
     */
    public static MultiplasOperacoesFinanceiras arquivoLerDados(Path caminho) throws IOException {
        Objects.requireNonNull(caminho, "Caminho do arquivo é obrigatório.");
        if (!Files.exists(caminho)) {
            throw new IOException("Arquivo não encontrado: " + caminho);
        }

        AplicacaoFinanceira appRestaurada = null;
        DiaSemana calRestaurado = null;
        List<OperacaoFinanceira> opsRestauradas = new ArrayList<>();

        try (BufferedReader reader = Files.newBufferedReader(caminho, StandardCharsets.UTF_8)) {
            String cabecalho = reader.readLine();
            boolean formatoAtual = CABECALHO_ARQUIVO.equals(cabecalho);
            if (!formatoAtual && !CABECALHO_ARQUIVO_V1.equals(cabecalho)) {
                throw new IOException("Formato de arquivo incompatível ou versão não suportada: " + cabecalho);
            }

            String linha;
            int numLinha = 1;
            while ((linha = reader.readLine()) != null) {
                numLinha++;
                if (linha.isBlank()) continue;

                String[] campos = linha.split("\t", -1);
                String tipoRegistro = campos[0];

                switch (tipoRegistro) {
                    case "I" -> {
                        if (campos.length < (formatoAtual ? 25 : 20)) {
                            throw new IOException("Registro de aplicação incompleto na linha " + numLinha);
                        }
                        String numero = desescaparTexto(campos[1]);
                        String nome = desescaparTexto(campos[2]);
                        BigDecimal valorAplicado = new BigDecimal(campos[3]);
                        BigDecimal taxaMensal = new BigDecimal(campos[4]);
                        int tipoRendimento = Integer.parseInt(campos[6]);
                        int tempoVencimento = Integer.parseInt(campos[7]);
                        int tempoPermanencia = Integer.parseInt(campos[8]);
                        int tipoResgate = Integer.parseInt(campos[9]);
                        int iof = Integer.parseInt(campos[10]);
                        int tipoIR = Integer.parseInt(campos[11]);
                        LocalDate dataAplicacao = LocalDate.parse(campos[12]);
                        BigDecimal saldo = new BigDecimal(campos[13]);
                        LocalDate dataUltima = LocalDate.parse(campos[16]);
                        int diasCorridos = Integer.parseInt(campos[17]);
                        int diasEfetivos = Integer.parseInt(campos[18]);
                        BigDecimal valorResgate = new BigDecimal(campos[19]);

                        appRestaurada = new AplicacaoFinanceira(
                                nome, numero, valorAplicado, taxaMensal,
                                tipoRendimento, tempoVencimento, tempoPermanencia,
                                tipoResgate, iof, tipoIR, dataAplicacao
                        );
                        appRestaurada.setSaldo(saldo);
                        appRestaurada.setDataUltimaAtualizacao(dataUltima);
                        appRestaurada.setDiasCorridos(diasCorridos);
                        appRestaurada.setDiasEfetivosCalculo(diasEfetivos);
                        appRestaurada.setValorResgate(valorResgate);
                        if (formatoAtual) {
                            appRestaurada.setConvencaoContagemDias(
                                    ConvencaoContagemDias.valueOf(campos[20]));
                            appRestaurada.setRegimeTributario(RegimeTributario.valueOf(campos[21]));
                            appRestaurada.restaurarAcumuladores(
                                    saldo,
                                    new BigDecimal(campos[22]),
                                    new BigDecimal(campos[23]),
                                    Integer.parseInt(campos[24])
                            );
                        } else {
                            BigDecimal rendimentoBruto = new BigDecimal(campos[14]);
                            BigDecimal rendimentoLiquido = new BigDecimal(campos[15]);
                            appRestaurada.restaurarAcumuladores(
                                    saldo,
                                    rendimentoBruto,
                                    BigDecimal.ZERO,
                                    diasCorridos
                            );
                            appRestaurada.setRendimentoAtualLiquido(rendimentoLiquido);
                        }
                    }
                    case "K" -> {
                        if (campos.length >= 4) {
                            int dia = Integer.parseInt(campos[1]);
                            int mes = Integer.parseInt(campos[2]);
                            int ano = Integer.parseInt(campos[3]);
                            calRestaurado = new DiaSemana(dia, mes, ano);
                        }
                    }
                    case "O" -> {
                        if (campos.length < 8) {
                            throw new IOException("Registro de operação incompleto na linha " + numLinha);
                        }
                        LocalDate dataOp = LocalDate.parse(campos[4]);
                        BigDecimal valorOp = new BigDecimal(campos[5]);
                        TipoOperacao tipoOp = TipoOperacao.valueOf(campos[6]);
                        String descOp = desescaparTexto(campos[7]);

                        OperacaoFinanceira op = new OperacaoFinanceira(dataOp, valorOp, descOp, tipoOp);
                        opsRestauradas.add(op);
                    }
                    default -> throw new IOException("Registro desconhecido na linha " + numLinha + ": " + tipoRegistro);
                }
            }
        }

        if (appRestaurada == null) {
            throw new IOException("Arquivo não contém registro de aplicação financeira.");
        }
        if (calRestaurado == null) {
            calRestaurado = new DiaSemana(appRestaurada.getDataAplicacao());
        }

        MultiplasOperacoesFinanceiras instancia = new MultiplasOperacoesFinanceiras(appRestaurada, calRestaurado);
        for (OperacaoFinanceira op : opsRestauradas) {
            instancia.vincularOperacaoRestaurada(op);
        }
        return instancia;
    }

    private static String escaparTexto(String texto) {
        if (texto == null) return "";
        return texto.replace("\\", "\\\\")
                    .replace("\t", "\\t")
                    .replace("\r", "\\r")
                    .replace("\n", "\\n");
    }

    private static String desescaparTexto(String texto) {
        if (texto == null) return "";
        StringBuilder sb = new StringBuilder(texto.length());
        boolean barra = false;
        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            if (barra) {
                switch (c) {
                    case '\\' -> sb.append('\\');
                    case 't' -> sb.append('\t');
                    case 'r' -> sb.append('\r');
                    case 'n' -> sb.append('\n');
                    default -> sb.append('\\').append(c);
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

    private static void definirPermissoesPrivadas(Path arquivo) {
        try {
            Set<PosixFilePermission> permissoes = EnumSet.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE
            );
            Files.setPosixFilePermissions(arquivo, permissoes);
        } catch (UnsupportedOperationException | IOException ignored) {
            // Em sistemas não POSIX (ex.: Windows FAT/NTFS sem POSIX), ignora silenciosamente
        }
    }

    private static void substituirArquivoAtomicamente(Path origem, Path destino) throws IOException {
        try {
            Files.move(origem, destino, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(origem, destino, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @Override
    public String toString() {
        return "MultiplasOperacoesFinanceiras{"
                + "aplicacao=" + aplicacao.getNumeroAplicacao() + " - " + aplicacao.getNomeAplicacao()
                + ", saldoLiquido=" + getSaldo()
                + ", operacoes=" + operacoes.size()
                + ", calendario=" + (calendario != null ? calendario.getData() : "nulo")
                + '}';
    }
}

package dominio.operacao;

import dominio.abstracoes.CarteiraFinanceira;
import dominio.util.ArquivoTSV;
import dominio.util.ValidacaoDominio;

import java.io.BufferedReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import dominio.aplicacao.AplicacaoFinanceira;
import dominio.aplicacao.ConvencaoContagemDias;
import dominio.aplicacao.TipoResgate;
import dominio.calendario.DiaSemana;
import dominio.tributacao.RegimeTributario;

/**
 * Item 4 — Agrega uma {@link AplicacaoFinanceira} (Item 1), até 100 lançamentos de
 * {@link OperacaoFinanceira} (Item 2) e o calendário {@link DiaSemana} (Item 3).
 *
 * <p>Restringe atualizações de rendimento a dias úteis (segunda a sexta), oferece extratos
 * polimórficos e persistência individual em disco.</p>
 */
public class MultiplasOperacoesFinanceiras extends CarteiraFinanceira {

    public static final int LIMITE_OPERACOES = 100;

    private static final String CABECALHO_V1 = "SGI-MOF\t1";
    private static final String CABECALHO_V2 = "SGI-MOF\t2";

    private AplicacaoFinanceira aplicacao;
    private final List<OperacaoFinanceira> operacoes = new ArrayList<>();
    private DiaSemana calendario;

    /** Construtor protegido para herança segura. */
    protected MultiplasOperacoesFinanceiras() {
    }

    /** Vincula uma aplicação e inicializa o calendário na data de início dela. */
    public MultiplasOperacoesFinanceiras(AplicacaoFinanceira aplicacao) {
        if (aplicacao == null) {
            throw new IllegalArgumentException("Aplicação financeira é obrigatória.");
        }
        LocalDate dataBase = aplicacao.getDataAplicacao() != null ? aplicacao.getDataAplicacao() : LocalDate.now();
        inicializarInterno(aplicacao, new DiaSemana(dataBase));
    }

    /** Vincula explicitamente a aplicação financeira e o calendário de dias úteis. */
    public MultiplasOperacoesFinanceiras(AplicacaoFinanceira aplicacao, DiaSemana calendario) {
        if (aplicacao == null) {
            throw new IllegalArgumentException("Aplicação financeira é obrigatória.");
        }
        if (calendario == null) {
            throw new IllegalArgumentException("Calendário de dias da semana é obrigatório.");
        }
        inicializarInterno(aplicacao, calendario);
    }

    private void inicializarInterno(AplicacaoFinanceira aplicacao, DiaSemana calendario) {
        this.aplicacao = aplicacao;
        this.calendario = calendario;
    }

    // -------------------------------------------------------------------------
    // Cadastro (Item 4)
    // -------------------------------------------------------------------------

    /** Reconfigura atomicamente a aplicação agregada, reiniciando o histórico de operações. */
    public void setCadastro(AplicacaoFinanceira aplicacao) {
        if (aplicacao == null) {
            throw new IllegalArgumentException("Aplicação financeira é obrigatória.");
        }
        LocalDate dataBase = aplicacao.getDataAplicacao() != null ? aplicacao.getDataAplicacao() : LocalDate.now();
        setCadastro(aplicacao, new DiaSemana(dataBase));
    }

    /** Reconfigura atomicamente a aplicação e o calendário, reiniciando o histórico de operações. */
    public void setCadastro(AplicacaoFinanceira aplicacao, DiaSemana calendario) {
        if (aplicacao == null) {
            throw new IllegalArgumentException("Aplicação financeira é obrigatória.");
        }
        if (calendario == null) {
            throw new IllegalArgumentException("Calendário de dias da semana é obrigatório.");
        }
        this.aplicacao = aplicacao;
        this.calendario = calendario;
        this.operacoes.clear();
        this.aplicacao.setQuantidadeOperacoes(0);
    }

    // -------------------------------------------------------------------------
    // Gestão de operações financeiras (máx. 100)
    // -------------------------------------------------------------------------

    /** Registra depósito ou retirada validando limite, data, carência, frequência e saldo. */
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
            this.aplicacao.setSaldo(this.aplicacao.getSaldo().add(operacao.getValor()));
        } else {
            validarCondicoesResgate(operacao.getData());
            this.aplicacao.resgatar(operacao.getValor());
        }

        this.operacoes.add(operacao);
        this.aplicacao.setQuantidadeOperacoes(this.operacoes.size());
    }

    private void validarCondicoesResgate(LocalDate dataOperacao) {
        long dias = ChronoUnit.DAYS.between(this.aplicacao.getDataAplicacao(), dataOperacao);
        // Carência mínima bloqueia retiradas antes de completar N dias desde a contratação
        if (dias < this.aplicacao.getTempoMinimoPermanencia()) {
            throw new IllegalArgumentException("Resgate fora da carência mínima de permanência.");
        }
        // Frequências periódicas (intervalo > 1) só liberam resgate em múltiplos exatos do período
        int intervalo = TipoResgate.doCodigo(this.aplicacao.getTipoResgate()).getIntervaloDias();
        if (intervalo > 1 && (dias == 0 || dias % intervalo != 0)) {
            throw new IllegalArgumentException("Resgate fora da frequência permitida.");
        }
    }

    /** Vincula uma operação já restaurada do disco sem reaplicar o impacto no saldo. */
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
     * Substitui ou remove (se {@code novaOperacao == null}) uma operação antes da apuração de juros.
     *
     * @param indice índice da operação na lista
     * @param novaOperacao nova operação substituta ou {@code null} para exclusão
     * @throws IndexOutOfBoundsException se o índice for inválido
     * @throws IllegalStateException se já houve apuração de rendimentos
     */
    public void alterarOperacao(int indice, OperacaoFinanceira novaOperacao) {
        if (indice < 0 || indice >= operacoes.size()) {
            throw new IndexOutOfBoundsException("Índice de operação fora do intervalo.");
        }
        if (aplicacao.getDiasEfetivosCalculo() > 0 || aplicacao.getRendimentoAtualBruto().signum() > 0) {
            throw new IllegalStateException("Não é possível alterar operações após a apuração de rendimentos.");
        }

        List<OperacaoFinanceira> atualizadas = new ArrayList<>(operacoes);
        if (novaOperacao == null) {
            atualizadas.remove(indice);
        } else {
            atualizadas.set(indice, novaOperacao);
        }

        // Simula o histórico completo em uma cópia isolada: só comita se todas as regras aprovarem (rollback natural)
        AplicacaoFinanceira reconstruida = reconstruirAplicacao();
        MultiplasOperacoesFinanceiras validacao = new MultiplasOperacoesFinanceiras(
                reconstruida, new DiaSemana(reconstruida.getDataAplicacao()));
        for (OperacaoFinanceira op : atualizadas) {
            validacao.adicionarOperacao(op);
        }

        this.aplicacao = validacao.getAplicacao();
        this.operacoes.clear();
        this.operacoes.addAll(atualizadas);
        this.calendario = validacao.getCalendario();
        this.aplicacao.setQuantidadeOperacoes(this.operacoes.size());
    }

    private AplicacaoFinanceira reconstruirAplicacao() {
        AplicacaoFinanceira orig = this.aplicacao;
        AplicacaoFinanceira copia = new AplicacaoFinanceira(
                orig.getNomeAplicacao(), orig.getNumeroAplicacao(), orig.getValorAplicado(),
                orig.getTaxaMensal(), orig.getTipoRendimento(), orig.getTempoFinalVencimento(),
                orig.getTempoMinimoPermanencia(), orig.getTipoResgate(), orig.getIof(),
                orig.getTipoImpostoRenda(), orig.getDataAplicacao()
        );
        copia.setConvencaoContagemDias(orig.getConvencaoContagemDias());
        copia.setRegimeTributario(orig.getRegimeTributario());
        return copia;
    }

    // -------------------------------------------------------------------------
    // Atualização em dias úteis (segunda a sexta-feira via DiaSemana)
    // -------------------------------------------------------------------------

    public void atualizarAplicacao(int dia, int mes, int ano) {
        atualizarAplicacao(ValidacaoDominio.validarData(dia, mes, ano));
    }

    /** Atualiza a aplicação somente se {@code data} for dia útil (segunda a sexta). */
    @Override
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
        if (!this.calendario.ehDiaUtil()) {
            return;
        }
        this.aplicacao.atualizarSaldo(data);
    }

    // -------------------------------------------------------------------------
    // Extratos polimórficos (últimos 30 dias inclusivos ou entre datas)
    // -------------------------------------------------------------------------

    /** Retorna o extrato dos últimos 30 dias corridos até a data do calendário (janela inclusiva). */
    @Override
    public List<OperacaoFinanceira> getExtrato() {
        LocalDate fim = (this.calendario != null && this.calendario.getData() != null)
                ? this.calendario.getData()
                : LocalDate.now();
        // minusDays(29)..fim perfaz exatamente 30 dias civis inclusivos
        return getExtrato(fim.minusDays(29), fim);
    }

    /** Retorna o extrato dos últimos 30 dias corridos terminados em {@code dataReferencia}. */
    public List<OperacaoFinanceira> getExtrato(LocalDate dataReferencia) {
        if (dataReferencia == null) {
            throw new IllegalArgumentException("Data de referência não pode ser nula.");
        }
        return getExtrato(dataReferencia.minusDays(29), dataReferencia);
    }

    /** Retorna o extrato ordenado entre {@code inicio} e {@code fim} (ambos inclusivos). */
    @Override
    public List<OperacaoFinanceira> getExtrato(LocalDate inicio, LocalDate fim) {
        if (inicio == null) throw new IllegalArgumentException("Data inicial obrigatória.");
        if (fim == null) throw new IllegalArgumentException("Data final obrigatória.");
        if (fim.isBefore(inicio)) {
            throw new IllegalArgumentException("A data final não pode ser anterior à data inicial.");
        }
        return this.operacoes.stream()
                .filter(op -> !op.getData().isBefore(inicio) && !op.getData().isAfter(fim))
                .sorted(Comparator.comparing(OperacaoFinanceira::getData))
                .toList();
    }

    public List<OperacaoFinanceira> getExtrato(int diaIni, int mesIni, int anoIni,
                                               int diaFim, int mesFim, int anoFim) {
        return getExtrato(ValidacaoDominio.validarData(diaIni, mesIni, anoIni),
                ValidacaoDominio.validarData(diaFim, mesFim, anoFim));
    }

    // -------------------------------------------------------------------------
    // Saldos, Getters e Setters
    // -------------------------------------------------------------------------

    @Override
    public BigDecimal getSaldo() { return this.aplicacao.getValorAtualLiquido(); }
    public BigDecimal getSaldoLiquido() { return getSaldo(); }
    public BigDecimal getSaldoBruto() { return this.aplicacao.getValorAtualBruto(); }
    public BigDecimal getRendimentoBruto() { return this.aplicacao.getRendimentoAtualBruto(); }
    public BigDecimal getRendimentoLiquido() { return this.aplicacao.getRendimentoAtualLiquido(); }
    public BigDecimal getImpostoDevido() { return this.aplicacao.getImpostoAtualDevido(); }
    public BigDecimal getValorAplicado() { return this.aplicacao.getValorAplicado(); }
    public BigDecimal getValorResgate() { return this.aplicacao.getValorResgate(); }

    public AplicacaoFinanceira getAplicacao() { return aplicacao; }

    public void setAplicacao(AplicacaoFinanceira aplicacao) {
        if (aplicacao == null) {
            throw new IllegalArgumentException("Aplicação financeira não pode ser nula.");
        }
        this.aplicacao = aplicacao;
    }

    public DiaSemana getCalendario() { return calendario; }

    public void setCalendario(DiaSemana calendario) {
        if (calendario == null) {
            throw new IllegalArgumentException("Calendário não pode ser nulo.");
        }
        this.calendario = calendario;
    }

    public List<OperacaoFinanceira> getOperacoes() {
        return Collections.unmodifiableList(this.operacoes);
    }

    /** Substitui atomicamente a lista de operações (setter estrutural, sem reaplicar saldo). */
    public void setOperacoes(List<OperacaoFinanceira> novasOperacoes) {
        if (novasOperacoes == null) {
            throw new IllegalArgumentException("Lista de operações não pode ser nula.");
        }
        if (novasOperacoes.size() > LIMITE_OPERACOES) {
            throw new IllegalStateException("Limite de 100 operações por aplicação atingido.");
        }
        List<OperacaoFinanceira> validadas = new ArrayList<>(novasOperacoes.size());
        for (OperacaoFinanceira op : novasOperacoes) {
            if (op == null) {
                throw new IllegalArgumentException("Operação financeira não pode ser nula.");
            }
            if (this.aplicacao != null && this.aplicacao.getDataAplicacao() != null
                    && op.getData().isBefore(this.aplicacao.getDataAplicacao())) {
                throw new IllegalArgumentException("Operação não pode ter data anterior à data de início da aplicação.");
            }
            validadas.add(op);
        }
        this.operacoes.clear();
        this.operacoes.addAll(validadas);
        if (this.aplicacao != null) {
            this.aplicacao.setQuantidadeOperacoes(this.operacoes.size());
        }
    }

    public int getQuantidadeOperacoes() { return this.operacoes.size(); }
    public String getNumeroAplicacao() { return this.aplicacao.getNumeroAplicacao(); }
    public String getNomeAplicacao() { return this.aplicacao.getNomeAplicacao(); }

    // -------------------------------------------------------------------------
    // Persistência individual em disco (formato SGI-MOF v2 / leitura v1 e v2)
    //   v1: I com 20 colunas (saldo e rendimentos básicos)
    //   v2: I com 25 colunas (inclui convenção, regime e acumuladores do ciclo)
    // -------------------------------------------------------------------------

    /** Salva esta carteira no caminho padrão {@code data/aplicacao_<numero>.db}. */
    public void arquivoSalvarDados() throws IOException {
        arquivoSalvarDados(Path.of("data/aplicacao_" + this.aplicacao.getNumeroAplicacao() + ".db"));
    }

    /** Salva esta carteira em formato {@code SGI-MOF\t2} no caminho informado. */
    @Override
    public void arquivoSalvarDados(String caminho) throws IOException {
        if (caminho == null || caminho.isBlank()) {
            throw new IllegalArgumentException("Caminho do arquivo não pode ser nulo ou vazio.");
        }
        arquivoSalvarDados(Path.of(caminho));
    }

    /** Salva esta carteira atomicamente em formato {@code SGI-MOF\t2}. */
    @Override
    public void arquivoSalvarDados(Path caminho) throws IOException {
        Objects.requireNonNull(caminho, "Caminho do arquivo é obrigatório.");
        ArquivoTSV.escreverAtomico(caminho, writer -> {
            writer.write(CABECALHO_V2);
            writer.newLine();
            writer.write(formatarLinhaAplicacaoMof(this.aplicacao));
            writer.newLine();

            if (this.calendario != null) {
                writer.write(formatarLinhaCalendarioMof(this.calendario));
                writer.newLine();
            }

            for (OperacaoFinanceira op : this.operacoes) {
                writer.write(formatarLinhaOperacaoMof(op));
                writer.newLine();
            }
        });
    }

    private static String formatarLinhaAplicacaoMof(AplicacaoFinanceira af) {
        LocalDate ultimaAtualizacao = af.getDataUltimaAtualizacao() != null
                ? af.getDataUltimaAtualizacao() : af.getDataAplicacao();
        return String.join("\t",
                "I",
                ArquivoTSV.escapar(af.getNumeroAplicacao()),
                ArquivoTSV.escapar(af.getNomeAplicacao()),
                af.getValorAplicado().toPlainString(),
                af.getTaxaMensal().toPlainString(),
                af.getTaxaDiaria().toPlainString(),
                Integer.toString(af.getTipoRendimento()),
                Integer.toString(af.getTempoFinalVencimento()),
                Integer.toString(af.getTempoMinimoPermanencia()),
                Integer.toString(af.getTipoResgate()),
                Integer.toString(af.getIof()),
                Integer.toString(af.getTipoImpostoRenda()),
                af.getDataAplicacao().toString(),
                af.getSaldo().toPlainString(),
                af.getRendimentoAtualBruto().toPlainString(),
                af.getRendimentoAtualLiquido().toPlainString(),
                ultimaAtualizacao.toString(),
                Integer.toString(af.getDiasCorridos()),
                Integer.toString(af.getDiasEfetivosCalculo()),
                af.getValorResgate().toPlainString(),
                af.getConvencaoContagemDias().name(),
                af.getRegimeTributario().name(),
                af.getRendimentosCreditados().toPlainString(),
                af.getRendimentosPendentes().toPlainString(),
                Integer.toString(af.getDiasDesdeUltimoRendimento())
        );
    }

    private static String formatarLinhaCalendarioMof(DiaSemana cal) {
        return String.join("\t",
                "K",
                Integer.toString(cal.getDia()),
                Integer.toString(cal.getMes()),
                Integer.toString(cal.getAno()),
                Integer.toString(cal.getDiaSemana())
        );
    }

    private static String formatarLinhaOperacaoMof(OperacaoFinanceira op) {
        return String.join("\t",
                "O",
                Integer.toString(op.getDia()),
                Integer.toString(op.getMes()),
                Integer.toString(op.getAno()),
                op.getData().toString(),
                op.getValor().toPlainString(),
                op.getTipo().name(),
                ArquivoTSV.escapar(op.getDescricao())
        );
    }

    /** Lê uma carteira {@code SGI-MOF} (v1 ou v2) a partir de caminho em texto. */
    public static MultiplasOperacoesFinanceiras arquivoLerDados(String caminho) throws IOException {
        if (caminho == null || caminho.isBlank()) {
            throw new IllegalArgumentException("Caminho do arquivo não pode ser nulo ou vazio.");
        }
        return arquivoLerDados(Path.of(caminho));
    }

    /** Lê e restaura uma carteira {@code SGI-MOF} (v1 ou v2) do disco. */
    public static MultiplasOperacoesFinanceiras arquivoLerDados(Path caminho) throws IOException {
        Objects.requireNonNull(caminho, "Caminho do arquivo é obrigatório.");
        if (!Files.exists(caminho)) {
            throw new IOException("Arquivo não encontrado: " + caminho);
        }

        AplicacaoFinanceira app = null;
        DiaSemana cal = null;
        List<OperacaoFinanceira> ops = new ArrayList<>();

        try (BufferedReader reader = Files.newBufferedReader(caminho, StandardCharsets.UTF_8)) {
            String cabecalho = reader.readLine();
            boolean v2 = CABECALHO_V2.equals(cabecalho);
            if (!v2 && !CABECALHO_V1.equals(cabecalho)) {
                throw new IOException("Formato de arquivo incompatível ou versão não suportada: " + cabecalho);
            }

            String linha;
            int numLinha = 1;
            while ((linha = reader.readLine()) != null) {
                numLinha++;
                if (linha.isBlank()) continue;

                List<String> campos;
                try {
                    campos = ArquivoTSV.parseRegistro(linha, numLinha);
                } catch (IllegalArgumentException e) {
                    throw new IOException("Linha malformada no arquivo (" + numLinha + "): " + e.getMessage(), e);
                }

                switch (campos.get(0)) {
                    case "I" -> app = lerAplicacaoMof(campos, v2, numLinha);
                    case "K" -> cal = lerCalendarioMof(campos, cal);
                    case "O" -> ops.add(lerOperacaoMof(campos, numLinha));
                    default -> throw new IOException("Registro desconhecido na linha " + numLinha + ": " + campos.get(0));
                }
            }
        } catch (RuntimeException e) {
            throw new IOException("Conteúdo inválido no arquivo: " + e.getMessage(), e);
        }

        if (app == null) {
            throw new IOException("Arquivo não contém registro de aplicação financeira.");
        }
        if (cal == null) {
            cal = new DiaSemana(app.getDataAplicacao());
        }

        MultiplasOperacoesFinanceiras instancia = new MultiplasOperacoesFinanceiras(app, cal);
        for (OperacaoFinanceira op : ops) {
            instancia.vincularOperacaoRestaurada(op);
        }
        return instancia;
    }

    private static DiaSemana lerCalendarioMof(List<String> campos, DiaSemana atual) {
        if (campos.size() < 4) {
            return atual;
        }
        return new DiaSemana(
                Integer.parseInt(campos.get(1)),
                Integer.parseInt(campos.get(2)),
                Integer.parseInt(campos.get(3)));
    }

    private static OperacaoFinanceira lerOperacaoMof(List<String> campos, int numLinha) throws IOException {
        if (campos.size() < 8) {
            throw new IOException("Registro de operação incompleto na linha " + numLinha);
        }
        return new OperacaoFinanceira(
                LocalDate.parse(campos.get(4)),
                new BigDecimal(campos.get(5)),
                campos.get(7),
                TipoOperacao.valueOf(campos.get(6)));
    }

    private static AplicacaoFinanceira lerAplicacaoMof(List<String> campos, boolean v2, int numLinha)
            throws IOException {
        if (campos.size() < (v2 ? 25 : 20)) {
            throw new IOException("Registro de aplicação incompleto na linha " + numLinha);
        }
        AplicacaoFinanceira af = new AplicacaoFinanceira(
                campos.get(2),
                campos.get(1),
                new BigDecimal(campos.get(3)),
                new BigDecimal(campos.get(4)),
                Integer.parseInt(campos.get(6)),
                Integer.parseInt(campos.get(7)),
                Integer.parseInt(campos.get(8)),
                Integer.parseInt(campos.get(9)),
                Integer.parseInt(campos.get(10)),
                Integer.parseInt(campos.get(11)),
                LocalDate.parse(campos.get(12))
        );
        BigDecimal saldo = new BigDecimal(campos.get(13));
        int diasCorridos = Integer.parseInt(campos.get(17));
        af.setSaldo(saldo);
        af.setDataUltimaAtualizacao(LocalDate.parse(campos.get(16)));
        af.setDiasCorridos(diasCorridos);
        af.setDiasEfetivosCalculo(Integer.parseInt(campos.get(18)));
        af.setValorResgate(new BigDecimal(campos.get(19)));

        if (v2) {
            af.setConvencaoContagemDias(ConvencaoContagemDias.valueOf(campos.get(20)));
            af.setRegimeTributario(RegimeTributario.valueOf(campos.get(21)));
            af.restaurarAcumuladores(saldo, new BigDecimal(campos.get(22)),
                    new BigDecimal(campos.get(23)), Integer.parseInt(campos.get(24)));
        } else {
            af.restaurarAcumuladores(saldo, new BigDecimal(campos.get(14)), BigDecimal.ZERO, diasCorridos);
            af.setRendimentoAtualLiquido(new BigDecimal(campos.get(15)));
        }
        return af;
    }

    @Override
    public String toString() {
        return "MultiplasOperacoesFinanceiras{aplicacao="
                + aplicacao.getNumeroAplicacao() + " - " + aplicacao.getNomeAplicacao()
                + ", saldoLiquido=" + getSaldo()
                + ", operacoes=" + operacoes.size()
                + ", calendario=" + (calendario != null ? calendario.getData() : "nulo") + '}';
    }
}

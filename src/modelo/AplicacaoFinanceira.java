package modelo;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * Reúne os dados e as regras de cálculo de uma aplicação financeira.
 *
 * <p>Os valores monetários usam {@link BigDecimal}. Dias corridos controlam
 * carência, vencimento, periodicidade e tributação; a apuração de juros ocorre
 * em atualizações de dias úteis.</p>
 */
public class AplicacaoFinanceira {
    /** Precisão intermediária para os cálculos de taxa e juros. */
    private static final MathContext CONTEXTO_TAXA = MathContext.DECIMAL128;
    /** Nome legível da aplicação. */
    private String nome;
    /** Identificador único da aplicação no sistema. */
    private String numero;
    /** Valor originalmente aplicado. */
    private BigDecimal valorInicial;
    /** Saldo que já recebeu os rendimentos periódicos. */
    private BigDecimal saldo;
    /** Taxa mensal representada como fração decimal. */
    private BigDecimal taxaMensal;
    /** Rendimentos já incorporados ao saldo. */
    private BigDecimal rendimentosAcumulados = Moeda.ZERO;
    /** Rendimentos apurados que aguardam a data de crédito configurada. */
    private BigDecimal rendimentosPendentes = Moeda.ZERO;
    /** Convenção usada para converter a taxa mensal em taxa diária. */
    private ConvencaoContagemDias convencaoContagemDias = ConvencaoContagemDias.THIRTY_360;
    /** Regime agregado, mantido em conjunto com as opções específicas de imposto. */
    private RegimeTributario regimeTributario = RegimeTributario.STANDARD;
    /** Periodicidade de crédito dos rendimentos pendentes. */
    private PeriodicidadeRendimento periodicidadeRendimento = PeriodicidadeRendimento.DAILY;
    /** Prazo até o vencimento em dias corridos; zero significa sem vencimento. */
    private int diasVencimento;
    /** Período mínimo em dias corridos antes de permitir resgate. */
    private int diasPermanenciaMinima;
    /** Frequência mínima entre datas em que o resgate é permitido. */
    private FrequenciaResgate frequenciaResgate = FrequenciaResgate.DAILY;
    /** Política de cálculo do IOF. */
    private OpcaoIOF opcaoIOF = OpcaoIOF.THREE_POINT_FIVE_PERCENT_BEFORE_30_DAYS;
    /** Política de cálculo do Imposto de Renda. */
    private OpcaoIR opcaoIR = OpcaoIR.REGRESSIVE;
    /** Data em que a aplicação foi iniciada. */
    private LocalDate dataAplicacao;
    /** Última data processada, para não atualizar o mesmo intervalo novamente. */
    private LocalDate dataUltimaAtualizacao;
    /** Total de dias corridos desde a aplicação. */
    private int diasCorridos;
    /** Total de dias úteis considerados para correção financeira. */
    private int diasEfetivos;
    /** Dias corridos desde o último crédito de rendimento. */
    private int diasDesdeRendimento;
    /** Quantidade de operações já registradas na aplicação. */
    private int quantidadeOperacoes;

    /** Cria uma aplicação com início na data corrente. */
    public AplicacaoFinanceira(String nome, String numero, BigDecimal valorInicial, BigDecimal taxaMensal) {
        this(nome, numero, valorInicial, taxaMensal, LocalDate.now());
    }

    /** Cria uma aplicação com a data inicial informada pelo chamador. */
    public AplicacaoFinanceira(String nome, String numero, BigDecimal valorInicial, BigDecimal taxaMensal,
                      LocalDate dataAplicacao) {
        this.nome = textoObrigatorio(nome, "Nome do investimento obrigatório.");
        this.numero = textoObrigatorio(numero, "Número do investimento obrigatório.");
        this.valorInicial = Moeda.positivo(valorInicial, "Valor inicial");
        this.taxaMensal = validarTaxaMensal(taxaMensal);
        this.dataAplicacao = Objects.requireNonNull(dataAplicacao, "Data de aplicação obrigatória.");
        this.dataUltimaAtualizacao = dataAplicacao;
        this.saldo = this.valorInicial;
    }

    /** Retorna o nome cadastrado da aplicação. */
    public String getNome() { return nome; }
    /** Retorna o identificador da aplicação. */
    public String getNumero() { return numero; }
    /** Retorna o aporte inicial. */
    public BigDecimal getValorInicial() { return valorInicial; }
    /** Retorna a taxa mensal fracionária. */
    public BigDecimal getTaxaMensal() { return taxaMensal; }
    /** Retorna o saldo bruto, incluindo rendimentos pendentes. */
    public BigDecimal getSaldo() { return getSaldoBruto(); }
    /** Retorna o saldo que já recebeu rendimentos creditados. */
    public BigDecimal getSaldoCreditado() { return saldo; }
    /** Soma o saldo creditado aos rendimentos que aguardam crédito. */
    public BigDecimal getSaldoBruto() { return Moeda.arredondar(saldo.add(rendimentosPendentes)); }
    /** Retorna o saldo bruto descontados os impostos devidos. */
    public BigDecimal getSaldoLiquido() { return Moeda.arredondar(getSaldoBruto().subtract(getTributos()).max(BigDecimal.ZERO)); }
    /** Soma os rendimentos creditados aos rendimentos pendentes. */
    public BigDecimal getRendimentoBruto() { return Moeda.arredondar(rendimentosAcumulados.add(rendimentosPendentes)); }
    /** Retorna somente os rendimentos já incorporados ao saldo. */
    public BigDecimal getRendimentoCreditado() { return rendimentosAcumulados; }
    /** Retorna os rendimentos após deduzir os tributos atuais. */
    public BigDecimal getRendimentoLiquido() { return Moeda.arredondar(getRendimentoBruto().subtract(getTributos()).max(BigDecimal.ZERO)); }
    /** Calcula o IOF sobre os rendimentos conforme a opção configurada. */
    public BigDecimal getIOF() {
        return CalculadoraTributaria.calcularIOF(getRendimentoBruto(), diasCorridos, opcaoIOF, regimeTributario);
    }
    /** Calcula o IR sobre os rendimentos restantes depois do IOF. */
    public BigDecimal getIR() {
        return CalculadoraTributaria.calcularIR(getRendimentoBruto(), getIOF(), diasCorridos, opcaoIR, regimeTributario);
    }
    /** Soma os impostos calculados para a posição atual. */
    public BigDecimal getTributos() { return Moeda.arredondar(getIOF().add(getIR())); }
    /** Retorna a contagem de dias corridos desde o início. */
    public int getDiasCorridos() { return diasCorridos; }
    /** Retorna a contagem de dias úteis considerados na correção. */
    public int getDiasEfetivos() { return diasEfetivos; }
    /** Retorna a quantidade de operações registradas. */
    public int getQuantidadeOperacoes() { return quantidadeOperacoes; }
    /** Retorna os rendimentos que ainda aguardam crédito. */
    public BigDecimal getRendimentosPendentes() { return rendimentosPendentes; }
    /** Retorna os dias acumulados desde o último crédito de rendimento. */
    public int getDiasDesdeRendimento() { return diasDesdeRendimento; }
    /** Retorna a data de início da aplicação. */
    public LocalDate getDataAplicacao() { return dataAplicacao; }
    /** Retorna a data da última atualização processada. */
    public LocalDate getDataUltimaAtualizacao() { return dataUltimaAtualizacao; }
    /** Retorna a convenção de dias usada na taxa diária. */
    public ConvencaoContagemDias getConvencaoContagemDias() { return convencaoContagemDias; }
    /** Retorna o regime tributário geral compatível. */
    public RegimeTributario getRegimeTributario() { return regimeTributario; }
    /** Retorna a periodicidade de crédito dos rendimentos. */
    public PeriodicidadeRendimento getPeriodicidadeRendimento() { return periodicidadeRendimento; }
    /** Retorna o vencimento em dias corridos, ou zero quando não configurado. */
    public int getDiasVencimento() { return diasVencimento; }
    /** Retorna a carência em dias corridos. */
    public int getDiasPermanenciaMinima() { return diasPermanenciaMinima; }
    /** Retorna a frequência permitida para resgate. */
    public FrequenciaResgate getFrequenciaResgate() { return frequenciaResgate; }
    /** Retorna a política de IOF configurada. */
    public OpcaoIOF getOpcaoIOF() { return opcaoIOF; }
    /** Retorna a política de IR configurada. */
    public OpcaoIR getOpcaoIR() { return opcaoIR; }

    /** Define um nome obrigatório, removendo espaços externos. */
    public void setNome(String nome) {
        this.nome = textoObrigatorio(nome, "Nome do investimento obrigatório.");
    }

    /** Define um identificador obrigatório, removendo espaços externos. */
    public void setNumero(String numero) {
        this.numero = textoObrigatorio(numero, "Número do investimento obrigatório.");
    }

    /** Permite alterar o aporte somente antes de operações ou rendimentos. */
    public void setValorInicial(BigDecimal valor) {
        BigDecimal valorValidado = Moeda.positivo(valor, "Valor inicial");
        if (saldo != null) {
            if (quantidadeOperacoes > 0 || rendimentosAcumulados.signum() > 0 || rendimentosPendentes.signum() > 0) {
                throw new IllegalStateException("Não é possível alterar o aporte inicial após movimentação ou rendimento.");
            }
            saldo = saldo.add(valorValidado.subtract(valorInicial));
        } else {
            saldo = valorValidado;
        }
        this.valorInicial = valorValidado;
    }

    /** Define a taxa mensal como fração entre zero e um, inclusive. */
    public void setTaxaMensal(BigDecimal taxaMensal) {
        this.taxaMensal = validarTaxaMensal(taxaMensal);
    }

    /** Define diretamente um saldo não negativo. */
    public void setSaldo(BigDecimal saldo) {
        this.saldo = Moeda.naoNegativo(saldo, "Saldo");
    }

    /** Define a base anual usada para converter taxa mensal em diária. */
    public void setConvencaoContagemDias(ConvencaoContagemDias convention) {
        this.convencaoContagemDias = Objects.requireNonNull(convention, "Convenção de dias obrigatória.");
    }

    /** Define o regime tributário geral e sincroniza as opções de IOF e IR. */
    public void setRegimeTributario(RegimeTributario regime) {
        this.regimeTributario = Objects.requireNonNull(regime, "Regime tributário obrigatório.");
        if (regime == RegimeTributario.TAX_FREE) {
            opcaoIOF = OpcaoIOF.EXEMPT;
            opcaoIR = OpcaoIR.EXEMPT;
        } else {
            opcaoIOF = OpcaoIOF.THREE_POINT_FIVE_PERCENT_BEFORE_30_DAYS;
            opcaoIR = OpcaoIR.REGRESSIVE;
        }
    }

    /** Define a periodicidade; rendimento no vencimento exige vencimento positivo. */
    public void setPeriodicidadeRendimento(PeriodicidadeRendimento period) {
        Objects.requireNonNull(period, "Periodicidade de rendimento obrigatória.");
        if (period == PeriodicidadeRendimento.AT_MATURITY && diasVencimento <= 0) {
            throw new IllegalArgumentException("Configure os dias até o vencimento antes de usar rendimento no vencimento.");
        }
        this.periodicidadeRendimento = period;
    }

    /** Define o vencimento e verifica sua coerência com carência e rendimento. */
    public void setDiasVencimento(int days) {
        if (days < 0) throw new IllegalArgumentException("Dias até o vencimento não podem ser negativos.");
        if (diasPermanenciaMinima > days && days > 0) {
            throw new IllegalArgumentException("Vencimento não pode ser anterior à permanência mínima.");
        }
        if (periodicidadeRendimento == PeriodicidadeRendimento.AT_MATURITY && days == 0) {
            throw new IllegalArgumentException("Dias até o vencimento devem ser positivos para rendimento no vencimento.");
        }
        diasVencimento = days;
    }

    /** Define o prazo mínimo de permanência, sem ultrapassar o vencimento. */
    public void setDiasPermanenciaMinima(int days) {
        if (days < 0) throw new IllegalArgumentException("Permanência mínima não pode ser negativa.");
        if (diasVencimento > 0 && days > diasVencimento) {
            throw new IllegalArgumentException("Permanência mínima não pode exceder o vencimento.");
        }
        diasPermanenciaMinima = days;
    }

    /** Define a frequência mínima permitida entre retiradas. */
    public void setFrequenciaResgate(FrequenciaResgate frequency) {
        this.frequenciaResgate = Objects.requireNonNull(frequency, "Frequência de resgate obrigatória.");
    }

    /** Define a opção de IOF e atualiza o regime geral correspondente. */
    public void setOpcaoIOF(OpcaoIOF opcaoMenu) {
        this.opcaoIOF = Objects.requireNonNull(opcaoMenu, "Opção de IOF obrigatória.");
        sincronizarRegimeTributarioLegado();
    }

    /** Define a opção de IR e atualiza o regime geral correspondente. */
    public void setOpcaoIR(OpcaoIR opcaoMenu) {
        this.opcaoIR = Objects.requireNonNull(opcaoMenu, "Opção de IR obrigatória.");
        sincronizarRegimeTributarioLegado();
    }

    /** Define a data inicial e a usa como referência da primeira atualização. */
    public void setDataAplicacao(LocalDate data) {
        this.dataAplicacao = Objects.requireNonNull(data, "Data de aplicação obrigatória.");
        if (dataUltimaAtualizacao == null) dataUltimaAtualizacao = data;
    }

    /** Acrescenta um dia corrido ao contador do investimento. */
    public void incrementarDia() {
        diasCorridos++;
        diasDesdeRendimento++;
    }

    /** Acrescenta um dia de aplicação; usado apenas para dias úteis. */
    public void incrementarDiaAplicacao() { diasEfetivos++; }

    /** Alias em inglês para incrementar o contador de dias corridos. */
    public void incrementDay() { incrementarDia(); }
    /** Alias em inglês para incrementar o contador de dias de aplicação. */
    public void incrementApplicationDay() { incrementarDiaAplicacao(); }

    /** Informa se o prazo configurado de vencimento já foi alcançado. */
    public boolean venceu() {
        return diasVencimento > 0 && diasCorridos >= diasVencimento;
    }

    /**
     * Informa se, na posição atual do contador, carência e frequência permitem
     * resgate. Operações datadas validam a data do lançamento separadamente.
     */
    public boolean podeResgatar() {
        return diasCorridos >= diasPermanenciaMinima && ehDataDeResgate();
    }

    /** Indica quando o rendimento acumulado é creditado e passa a compor o saldo. */
    public boolean ehDataDeRendimento() {
        return switch (periodicidadeRendimento) {
            case DAILY -> diasDesdeRendimento >= 1;
            case MONTHLY_30_DAYS, QUARTERLY_90_DAYS, HALF_YEARLY_180_DAYS, YEARLY_365_DAYS ->
                    diasDesdeRendimento >= periodicidadeRendimento.dias();
            case AT_MATURITY -> venceu();
        };
    }

    private boolean rendimentoVenceu() {
        // Crédito em data de vencimento só é avaliado contra a maturidade configurada.
        return periodicidadeRendimento == PeriodicidadeRendimento.AT_MATURITY ? venceu() : ehDataDeRendimento();
    }

    private void creditarRendimentosPendentes() {
        if (rendimentosPendentes.signum() > 0) {
            saldo = Moeda.arredondar(saldo.add(rendimentosPendentes));
            rendimentosAcumulados = Moeda.arredondar(rendimentosAcumulados.add(rendimentosPendentes));
            rendimentosPendentes = Moeda.ZERO;
        }
        // Mantém apenas dias excedentes ao período para o próximo ciclo de crédito.
        if (periodicidadeRendimento != PeriodicidadeRendimento.AT_MATURITY) {
            diasDesdeRendimento %= periodicidadeRendimento.dias();
        }
    }

    private boolean ehDataDeResgate() {
        int intervaloDias = frequenciaResgate.dias();
        return intervaloDias == 1 || (diasCorridos > 0 && diasCorridos % intervaloDias == 0);
    }

    private boolean podeResgatarEm(LocalDate data) {
        long diasAteResgate = ChronoUnit.DAYS.between(dataAplicacao, data);
        int intervaloDias = frequenciaResgate.dias();
        return diasAteResgate >= diasPermanenciaMinima
                && (intervaloDias == 1 || (diasAteResgate > 0 && diasAteResgate % intervaloDias == 0));
    }

    /**
     * Processa dias corridos e acumula juros em dias úteis. O rendimento pendente
     * só é incorporado ao saldo conforme a periodicidade de crédito configurada.
     */
    public void atualizar(LocalDate data) {
        Objects.requireNonNull(data, "Data obrigatória.");
        if (data.isBefore(dataAplicacao)) throw new IllegalArgumentException("Data anterior à aplicação.");
        if (!ehDiaUtil(data)) return;
        if (dataUltimaAtualizacao != null && !data.isAfter(dataUltimaAtualizacao)) return;

        // Percorre todos os dias não processados para manter os contadores corretos,
        // ainda que a aplicação seja atualizada depois de vários dias sem uso.
        LocalDate dataPercorrida = dataUltimaAtualizacao == null ? data : dataUltimaAtualizacao.plusDays(1);
        while (!dataPercorrida.isAfter(data)) {
            incrementarDia();
            if (ehDiaUtil(dataPercorrida)) {
                incrementarDiaAplicacao();
                if (!dataPercorrida.isBefore(dataAplicacao)) {
                    // Após o vencimento, não se acumulam novos rendimentos.
                    if (diasVencimento == 0 || diasCorridos <= diasVencimento) acumularDiaUtil();
                    if (rendimentoVenceu()) creditarRendimentosPendentes();
                }
            }
            dataPercorrida = dataPercorrida.plusDays(1);
        }
        dataUltimaAtualizacao = data;
    }

    /** Aplica o lançamento ao saldo, validando limite, data e regra de resgate. */
    public void aplicar(OperacaoFinanceira operacao) {
        Objects.requireNonNull(operacao, "Operação obrigatória.");
        if (quantidadeOperacoes >= 100) throw new IllegalStateException("Limite de 100 operações por aplicação atingido.");
        if (operacao.data().isBefore(dataAplicacao)) throw new IllegalArgumentException("Operação anterior à aplicação.");
        BigDecimal valor = Moeda.arredondar(operacao.valor());
        if (operacao.tipo() == TipoOperacao.DEPOSIT) {
            saldo = Moeda.arredondar(saldo.add(valor));
        } else {
            if (!podeResgatarEm(operacao.data())) {
                throw new IllegalArgumentException("Resgate fora da carência ou da frequência permitida.");
            }
            if (valor.compareTo(saldo) > 0) throw new IllegalArgumentException("Saldo insuficiente.");
            saldo = Moeda.arredondar(saldo.subtract(valor));
        }
        quantidadeOperacoes++;
    }

    /**
     * Restaura os dados do formato mais antigo, que não persistia termos
     * avançados nem rendimentos pendentes.
     */
    public void restaurarEstado(BigDecimal saldo, BigDecimal rendimentosAcumulados, LocalDate dataUltimaAtualizacao,
                             int diasCorridos, int diasEfetivos, int quantidadeOperacoes) {
        restaurarEstado(saldo, rendimentosAcumulados, dataUltimaAtualizacao, diasCorridos, diasEfetivos, quantidadeOperacoes,
                PeriodicidadeRendimento.DAILY, 0, 0, FrequenciaResgate.DAILY, opcaoIOF, opcaoIR);
    }

    /**
     * Restaura os termos avançados de uma versão que ainda não salvava o
     * rendimento pendente nem o contador do ciclo atual.
     */
    public void restaurarEstado(BigDecimal saldo, BigDecimal rendimentosAcumulados, LocalDate dataUltimaAtualizacao,
                             int diasCorridos, int diasEfetivos, int quantidadeOperacoes, PeriodicidadeRendimento periodicidadeRendimento,
                             int diasVencimento, int diasPermanenciaMinima, FrequenciaResgate frequenciaResgate,
                             OpcaoIOF opcaoIOF, OpcaoIR opcaoIR) {
        restaurarEstado(saldo, rendimentosAcumulados, dataUltimaAtualizacao, diasCorridos, diasEfetivos, quantidadeOperacoes,
                periodicidadeRendimento, diasVencimento, diasPermanenciaMinima, frequenciaResgate, opcaoIOF, opcaoIR,
                BigDecimal.ZERO, diasCorridos % (periodicidadeRendimento == PeriodicidadeRendimento.AT_MATURITY
                        ? Math.max(1, diasCorridos + 1) : Math.max(1, periodicidadeRendimento.dias())));
    }

    /**
     * Restaura o estado completo da aplicação e valida os dados recuperados
     * antes de permitir novas operações.
     */
    public void restaurarEstado(BigDecimal saldo, BigDecimal rendimentosAcumulados, LocalDate dataUltimaAtualizacao,
                             int diasCorridos, int diasEfetivos, int quantidadeOperacoes, PeriodicidadeRendimento periodicidadeRendimento,
                             int diasVencimento, int diasPermanenciaMinima, FrequenciaResgate frequenciaResgate,
                             OpcaoIOF opcaoIOF, OpcaoIR opcaoIR, BigDecimal rendimentosPendentes,
                             int diasDesdeRendimento) {
        this.saldo = Moeda.naoNegativo(saldo, "Saldo");
        this.rendimentosAcumulados = Moeda.naoNegativo(rendimentosAcumulados, "Rendimento");
        this.rendimentosPendentes = Moeda.naoNegativo(rendimentosPendentes, "Rendimento pendente");
        this.dataUltimaAtualizacao = Objects.requireNonNull(dataUltimaAtualizacao, "Data de atualização obrigatória.");
        if (dataUltimaAtualizacao.isBefore(dataAplicacao)) throw new IllegalArgumentException("Atualização anterior à aplicação.");
        if (diasCorridos < 0 || diasEfetivos < 0 || quantidadeOperacoes < 0 || quantidadeOperacoes > 100
                || diasDesdeRendimento < 0) {
            throw new IllegalArgumentException("Contadores inválidos.");
        }
        this.diasCorridos = diasCorridos;
        this.diasEfetivos = diasEfetivos;
        this.quantidadeOperacoes = quantidadeOperacoes;
        this.diasDesdeRendimento = diasDesdeRendimento;
        this.diasVencimento = 0;
        this.diasPermanenciaMinima = 0;
        this.periodicidadeRendimento = PeriodicidadeRendimento.DAILY;
        this.frequenciaResgate = FrequenciaResgate.DAILY;
        this.opcaoIOF = OpcaoIOF.THREE_POINT_FIVE_PERCENT_BEFORE_30_DAYS;
        this.opcaoIR = OpcaoIR.REGRESSIVE;
        setDiasVencimento(diasVencimento);
        setDiasPermanenciaMinima(diasPermanenciaMinima);
        setPeriodicidadeRendimento(periodicidadeRendimento);
        setFrequenciaResgate(frequenciaResgate);
        setOpcaoIOF(opcaoIOF);
        setOpcaoIR(opcaoIR);
        if (this.periodicidadeRendimento != PeriodicidadeRendimento.AT_MATURITY && this.diasDesdeRendimento >= this.periodicidadeRendimento.dias()) {
            throw new IllegalArgumentException("Contador de periodicidade inválido.");
        }
    }

    /** Informa se a data não cai em sábado ou domingo. */
    public boolean ehDiaUtil(LocalDate data) {
        return data.getDayOfWeek() != DayOfWeek.SATURDAY && data.getDayOfWeek() != DayOfWeek.SUNDAY;
    }

    /** Mapeia o dia conforme o enunciado: sábado=0, domingo=1, segunda=2... */
    public static int indiceDiaSemana(LocalDate data) {
        return switch (Objects.requireNonNull(data).getDayOfWeek()) {
            case SATURDAY -> 0;
            case SUNDAY -> 1;
            case MONDAY -> 2;
            case TUESDAY -> 3;
            case WEDNESDAY -> 4;
            case THURSDAY -> 5;
            case FRIDAY -> 6;
        };
    }

    private void acumularDiaUtil() {
        if (saldo.signum() == 0) return;
        // Converte a taxa mensal em taxa diária pela convenção selecionada.
        BigDecimal taxaAnualizada = taxaMensal.multiply(BigDecimal.valueOf(12), CONTEXTO_TAXA);
        BigDecimal taxaDiaria = taxaAnualizada.divide(
                BigDecimal.valueOf(convencaoContagemDias.diasPorAno()), CONTEXTO_TAXA);
        BigDecimal rendimentoDoDia = Moeda.arredondar(saldo.multiply(taxaDiaria, CONTEXTO_TAXA));
        if (rendimentoDoDia.signum() > 0) {
            rendimentosPendentes = Moeda.arredondar(rendimentosPendentes.add(rendimentoDoDia));
        }
    }

    private void sincronizarRegimeTributarioLegado() {
        // Mantém o enum legado em sincronia com as duas políticas independentes.
        regimeTributario = opcaoIOF == OpcaoIOF.EXEMPT && opcaoIR == OpcaoIR.EXEMPT
                ? RegimeTributario.TAX_FREE : RegimeTributario.STANDARD;
    }

    private static String textoObrigatorio(String valor, String message) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return valor.trim();
    }

    private static BigDecimal validarTaxaMensal(BigDecimal taxaMensal) {
        Objects.requireNonNull(taxaMensal, "Taxa mensal obrigatória.");
        if (taxaMensal.signum() < 0 || taxaMensal.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("A taxa mensal deve estar entre 0 e 1 (inclusive).");
        }
        return taxaMensal;
    }
}

package dominio;

import dominio.util.ValidacaoDominio;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Item 1 — Aplicação Financeira com os 19 atributos obrigatórios, sincronização linear
 * entre taxa mensal e taxa diária (mês comercial de 30 dias), apuração de IOF e IR
 * regressivo e evolução de rendimentos em dias úteis.
 *
 * <p>Classe passível de herança, sem despacho de métodos virtuais nos construtores.</p>
 */
public class AplicacaoFinanceira {

    private static final MathContext CONTEXTO_TAXA = MathContext.DECIMAL128;
    private static final BigDecimal DIAS_MES_COMERCIAL = BigDecimal.valueOf(30);

    // 19 atributos obrigatórios (Item 1)
    private String nomeAplicacao = "Aplicação Padrão";
    private String numeroAplicacao = "0";
    private BigDecimal valorAplicado = BigDecimal.ZERO;
    private BigDecimal rendimentoAtualBruto = BigDecimal.ZERO;
    private BigDecimal rendimentoAtualLiquido = BigDecimal.ZERO;
    private BigDecimal valorResgate = BigDecimal.ZERO;
    private BigDecimal valorAtualBruto = BigDecimal.ZERO;
    private BigDecimal valorAtualLiquido = BigDecimal.ZERO;
    private BigDecimal taxaMensal = BigDecimal.ZERO;
    private BigDecimal taxaDiaria = BigDecimal.ZERO;
    private int tipoRendimento = 0;
    private int tempoFinalVencimento = 0;
    private int tempoMinimoPermanencia = 0;
    private int tipoResgate = 0;
    private int iof = 1;
    private int tipoImpostoRenda = 1;
    private int diasCorridos = 0;
    private int diasEfetivosCalculo = 0;
    private BigDecimal impostoAtualDevido = BigDecimal.ZERO;

    // Controle operacional interno
    private LocalDate dataAplicacao;
    private LocalDate dataUltimaAtualizacao;
    private BigDecimal saldo = BigDecimal.ZERO;
    private BigDecimal rendimentosCreditados = BigDecimal.ZERO;
    private BigDecimal rendimentosPendentes = BigDecimal.ZERO;
    private int diasDesdeUltimoRendimento = 0;
    private int quantidadeOperacoes = 0;
    private ConvencaoContagemDias convencaoContagemDias = ConvencaoContagemDias.COMERCIAL_30_360;
    private RegimeTributario regimeTributario = RegimeTributario.PADRAO;

    // -------------------------------------------------------------------------
    // Construtores (seguros contra despacho virtual)
    // -------------------------------------------------------------------------

    public AplicacaoFinanceira() {
        this.dataAplicacao = LocalDate.now();
        recalcularPosicaoInterno();
    }

    public AplicacaoFinanceira(String nomeAplicacao, String numeroAplicacao,
                               BigDecimal valorAplicado, BigDecimal taxaMensal) {
        this(nomeAplicacao, numeroAplicacao, valorAplicado, taxaMensal, LocalDate.now());
    }

    public AplicacaoFinanceira(String nomeAplicacao, String numeroAplicacao,
                               BigDecimal valorAplicado, BigDecimal taxaMensal, LocalDate dataAplicacao) {
        inicializarSeguro(nomeAplicacao, numeroAplicacao, valorAplicado, taxaMensal, dataAplicacao);
    }

    public AplicacaoFinanceira(String nome, String numero, BigDecimal valor, BigDecimal taxaMensal,
                               int tipoRendimento, int tempoVencimento, int tempoPermanencia,
                               int tipoResgate, int iof, int tipoIR) {
        this(nome, numero, valor, taxaMensal, tipoRendimento, tempoVencimento,
                tempoPermanencia, tipoResgate, iof, tipoIR, LocalDate.now());
    }

    public AplicacaoFinanceira(String nome, String numero, BigDecimal valor, BigDecimal taxaMensal,
                               int tipoRendimento, int tempoVencimento, int tempoPermanencia,
                               int tipoResgate, int iof, int tipoIR, LocalDate dataAplicacao) {
        inicializarSeguro(nome, numero, valor, taxaMensal, dataAplicacao);
        validarParametrosCadastrais(tipoRendimento, tempoVencimento, tempoPermanencia, tipoResgate, iof, tipoIR);
        this.tipoRendimento = tipoRendimento;
        this.tempoFinalVencimento = tempoVencimento;
        this.tempoMinimoPermanencia = tempoPermanencia;
        this.tipoResgate = tipoResgate;
        this.iof = iof;
        this.tipoImpostoRenda = tipoIR;
        recalcularPosicaoInterno();
    }

    private void inicializarSeguro(String nome, String numero, BigDecimal valor, BigDecimal taxa, LocalDate data) {
        String nomeValidado = validarObrigatorios(nome, numero, valor, taxa);
        LocalDate dataValidada = Objects.requireNonNull(data, "Data de aplicação não pode ser nula.");

        this.nomeAplicacao = nomeValidado;
        this.numeroAplicacao = numero.trim();
        this.valorAplicado = Moeda.arredondar(valor);
        this.saldo = this.valorAplicado;
        this.taxaMensal = taxa;
        this.taxaDiaria = calcularTaxaDiaria(taxa);
        this.dataAplicacao = dataValidada;
        this.dataUltimaAtualizacao = null;
        recalcularPosicaoInterno();
    }

    private static String validarObrigatorios(String nome, String numero, BigDecimal valor, BigDecimal taxa) {
        String nomeLimpo = ValidacaoDominio.textoObrigatorio(nome, "Nome da aplicação não pode ser nulo ou vazio.");
        ValidacaoDominio.textoObrigatorio(numero, "Número da aplicação não pode ser nulo ou vazio.");
        if (valor == null || valor.signum() <= 0) {
            throw new IllegalArgumentException("Valor aplicado inicial deve ser estritamente positivo.");
        }
        if (taxa == null || taxa.signum() < 0) {
            throw new IllegalArgumentException("Taxa mensal não pode ser negativa.");
        }
        return nomeLimpo;
    }

    private static BigDecimal calcularTaxaDiaria(BigDecimal taxaMensal) {
        return taxaMensal.signum() == 0
                ? BigDecimal.ZERO
                : taxaMensal.divide(DIAS_MES_COMERCIAL, CONTEXTO_TAXA).stripTrailingZeros();
    }

    private static void validarParametrosCadastrais(int tipoRendimento, int tempoVencimento, int tempoPermanencia,
                                                    int tipoResgate, int iof, int tipoIR) {
        TipoRendimento.doCodigo(tipoRendimento);
        if (tempoVencimento < 0) {
            throw new IllegalArgumentException("Tempo final de vencimento não pode ser negativo.");
        }
        if (tempoPermanencia < 0) {
            throw new IllegalArgumentException("Tempo mínimo de permanência não pode ser negativo.");
        }
        if (tempoVencimento > 0 && tempoPermanencia > tempoVencimento) {
            throw new IllegalArgumentException("Tempo de permanência (" + tempoPermanencia
                    + ") não pode ser superior ao vencimento (" + tempoVencimento + ").");
        }
        TipoResgate.doCodigo(tipoResgate);
        OpcaoIOF.doCodigo(iof);
        OpcaoIR.doCodigo(tipoIR);
    }

    // -------------------------------------------------------------------------
    // Métodos operacionais obrigatórios (Item 1)
    // -------------------------------------------------------------------------

    /** Configura atomicamente todos os termos cadastrais e tributários da aplicação. */
    public void setCadastro(String nome, String numero, BigDecimal valor, BigDecimal taxaMensal,
                            int tipoRendimento, int tempoVencimento, int tempoPermanencia,
                            int tipoResgate, int iof, int tipoIR) {
        String nomeValidado = validarObrigatorios(nome, numero, valor, taxaMensal);
        validarParametrosCadastrais(tipoRendimento, tempoVencimento, tempoPermanencia, tipoResgate, iof, tipoIR);

        this.nomeAplicacao = nomeValidado;
        this.numeroAplicacao = numero.trim();
        this.valorAplicado = Moeda.arredondar(valor);
        if (this.saldo == null || this.saldo.signum() == 0) {
            this.saldo = this.valorAplicado;
        }
        this.taxaMensal = taxaMensal;
        this.taxaDiaria = calcularTaxaDiaria(taxaMensal);
        this.tipoRendimento = tipoRendimento;
        this.tempoFinalVencimento = tempoVencimento;
        this.tempoMinimoPermanencia = tempoPermanencia;
        this.tipoResgate = tipoResgate;
        this.iof = iof;
        this.tipoImpostoRenda = tipoIR;
        recalcularPosicaoInterno();
    }

    /** Sobrecarga simplificada de cadastro com opções padrão (diário, IOF 30d, IR regressivo). */
    public void setCadastro(String nome, String numero, BigDecimal valor, BigDecimal taxaMensal) {
        setCadastro(nome, numero, valor, taxaMensal, 0, 0, 0, 0, 1, 1);
    }

    /** Incrementa o contador de dias corridos desde o cadastro (dia zero). */
    public void incrementarDia() {
        this.diasCorridos++;
        this.diasDesdeUltimoRendimento++;
    }

    /** Incrementa o contador de dias efetivos para cálculo da aplicação (nome do enunciado). */
    public void incrementarDiaAplicação() {
        incrementarDiaAplicacao();
    }

    /** Alias sem acento de {@link #incrementarDiaAplicação()}. */
    public void incrementarDiaAplicacao() {
        this.diasEfetivosCalculo++;
    }

    /** Atualiza saldo e rendimentos até a data atual do sistema. */
    public void atualizarSaldo() {
        atualizarSaldo(LocalDate.now());
    }

    /** Atualiza saldo e rendimentos dia a dia até {@code dataReferencia}, rendendo apenas em dias úteis. */
    public void atualizarSaldo(LocalDate dataReferencia) {
        Objects.requireNonNull(dataReferencia, "Data de referência não pode ser nula.");
        if (dataReferencia.isBefore(this.dataAplicacao)) {
            throw new IllegalArgumentException("Data de referência não pode ser anterior à data da aplicação.");
        }
        if (this.dataUltimaAtualizacao != null && !dataReferencia.isAfter(this.dataUltimaAtualizacao)) {
            return;
        }

        LocalDate cursor = (this.dataUltimaAtualizacao == null)
                ? this.dataAplicacao.plusDays(1)
                : this.dataUltimaAtualizacao.plusDays(1);

        while (!cursor.isAfter(dataReferencia)) {
            incrementarDia();
            DayOfWeek diaDaSemana = cursor.getDayOfWeek();
            boolean ehDiaUtil = diaDaSemana != DayOfWeek.SATURDAY && diaDaSemana != DayOfWeek.SUNDAY;

            if (ehDiaUtil) {
                incrementarDiaAplicacao();
                boolean dentroDoPrazo = this.tempoFinalVencimento == 0 || this.diasCorridos <= this.tempoFinalVencimento;
                if (dentroDoPrazo) {
                    BigDecimal rendimentoDia = Moeda.arredondar(this.saldo.multiply(this.taxaDiaria));
                    this.rendimentosPendentes = this.rendimentosPendentes.add(rendimentoDia);
                }
                if (atingiuDataRendimento()) {
                    this.saldo = this.saldo.add(this.rendimentosPendentes);
                    this.rendimentosCreditados = this.rendimentosCreditados.add(this.rendimentosPendentes);
                    this.rendimentosPendentes = BigDecimal.ZERO;
                    this.diasDesdeUltimoRendimento = 0;
                }
            }
            cursor = cursor.plusDays(1);
        }

        this.dataUltimaAtualizacao = dataReferencia;
        recalcularPosicaoInterno();
    }

    private boolean atingiuDataRendimento() {
        TipoRendimento tipo = TipoRendimento.doCodigo(this.tipoRendimento);
        if (tipo == TipoRendimento.NO_PRAZO) {
            return this.tempoFinalVencimento > 0 && this.diasCorridos >= this.tempoFinalVencimento;
        }
        return this.diasDesdeUltimoRendimento >= tipo.getIntervaloDias();
    }

    private BigDecimal calcularIofInterno() {
        return CalculadoraTributaria.calcularIof(this.rendimentoAtualBruto, this.diasCorridos, this.iof);
    }

    private BigDecimal calcularIrInterno(BigDecimal iofDevido) {
        return CalculadoraTributaria.calcularIr(
                this.rendimentoAtualBruto, iofDevido, this.diasCorridos, this.tipoImpostoRenda);
    }

    private void recalcularPosicaoInterno() {
        this.rendimentoAtualBruto = Moeda.arredondar(this.rendimentosCreditados.add(this.rendimentosPendentes));
        BigDecimal iofDevido = calcularIofInterno();
        BigDecimal irDevido = calcularIrInterno(iofDevido);
        this.impostoAtualDevido = Moeda.arredondar(iofDevido.add(irDevido));
        this.rendimentoAtualLiquido = this.rendimentoAtualBruto.subtract(this.impostoAtualDevido).max(Moeda.ZERO);
        this.valorAtualBruto = Moeda.arredondar(this.saldo.add(this.rendimentosPendentes));
        this.valorAtualLiquido = this.valorAtualBruto.subtract(this.impostoAtualDevido).max(Moeda.ZERO);
    }

    // -------------------------------------------------------------------------
    // Sincronização linear de taxas (mês comercial de 30 dias)
    // -------------------------------------------------------------------------

    public BigDecimal getTaxaMensal() { return taxaMensal; }

    /** Atualiza a taxa mensal e recalcula a taxa diária proporcional para 30 dias. */
    public void setTaxaMensal(BigDecimal taxaMensal) {
        if (taxaMensal == null || taxaMensal.signum() < 0) {
            throw new IllegalArgumentException("Taxa mensal não pode ser nula ou negativa.");
        }
        this.taxaMensal = taxaMensal;
        this.taxaDiaria = calcularTaxaDiaria(taxaMensal);
    }

    public BigDecimal getTaxaDiaria() { return taxaDiaria; }

    /** Atualiza a taxa diária e recalcula a taxa mensal proporcional para 30 dias. */
    public void setTaxaDiaria(BigDecimal taxaDiaria) {
        if (taxaDiaria == null || taxaDiaria.signum() < 0) {
            throw new IllegalArgumentException("Taxa diária não pode ser nula ou negativa.");
        }
        this.taxaDiaria = taxaDiaria;
        this.taxaMensal = taxaDiaria.signum() == 0
                ? BigDecimal.ZERO
                : taxaDiaria.multiply(DIAS_MES_COMERCIAL, CONTEXTO_TAXA).stripTrailingZeros();
    }

    // -------------------------------------------------------------------------
    // Getters e Setters dos 19 atributos obrigatórios
    // -------------------------------------------------------------------------

    public String getNomeAplicacao() { return nomeAplicacao; }

    public void setNomeAplicacao(String nomeAplicacao) {
        this.nomeAplicacao = ValidacaoDominio.textoObrigatorio(
                nomeAplicacao, "Nome da aplicação não pode ser nulo ou vazio.");
    }

    public String getNumeroAplicacao() { return numeroAplicacao; }

    public void setNumeroAplicacao(String numeroAplicacao) {
        this.numeroAplicacao = ValidacaoDominio.textoObrigatorio(
                numeroAplicacao, "Número da aplicação não pode ser nulo ou vazio.");
    }

    public BigDecimal getValorAplicado() { return valorAplicado; }

    public void setValorAplicado(BigDecimal valorAplicado) {
        if (valorAplicado == null || valorAplicado.signum() <= 0) {
            throw new IllegalArgumentException("Valor aplicado deve ser estritamente positivo.");
        }
        this.valorAplicado = Moeda.arredondar(valorAplicado);
    }

    public BigDecimal getRendimentoAtualBruto() { return rendimentoAtualBruto; }

    public void setRendimentoAtualBruto(BigDecimal rendimentoAtualBruto) {
        this.rendimentoAtualBruto = rendimentoAtualBruto == null ? BigDecimal.ZERO : Moeda.arredondar(rendimentoAtualBruto);
    }

    public BigDecimal getRendimentoAtualLiquido() { return rendimentoAtualLiquido; }

    public void setRendimentoAtualLiquido(BigDecimal rendimentoAtualLiquido) {
        this.rendimentoAtualLiquido = rendimentoAtualLiquido == null ? BigDecimal.ZERO : Moeda.arredondar(rendimentoAtualLiquido);
    }

    public BigDecimal getValorResgate() { return valorResgate; }

    public void setValorResgate(BigDecimal valorResgate) {
        this.valorResgate = valorResgate == null ? BigDecimal.ZERO : Moeda.arredondar(valorResgate);
    }

    public BigDecimal getValorAtualBruto() { return valorAtualBruto; }

    public void setValorAtualBruto(BigDecimal valorAtualBruto) {
        this.valorAtualBruto = valorAtualBruto == null ? BigDecimal.ZERO : Moeda.arredondar(valorAtualBruto);
    }

    public BigDecimal getValorAtualLiquido() { return valorAtualLiquido; }

    public void setValorAtualLiquido(BigDecimal valorAtualLiquido) {
        this.valorAtualLiquido = valorAtualLiquido == null ? BigDecimal.ZERO : Moeda.arredondar(valorAtualLiquido);
    }

    public int getTipoRendimento() { return tipoRendimento; }

    public void setTipoRendimento(int tipoRendimento) {
        TipoRendimento.doCodigo(tipoRendimento);
        this.tipoRendimento = tipoRendimento;
    }

    public int getTempoFinalVencimento() { return tempoFinalVencimento; }

    public void setTempoFinalVencimento(int tempoFinalVencimento) {
        if (tempoFinalVencimento < 0) {
            throw new IllegalArgumentException("Tempo final de vencimento não pode ser negativo.");
        }
        this.tempoFinalVencimento = tempoFinalVencimento;
    }

    public int getTempoMinimoPermanencia() { return tempoMinimoPermanencia; }

    public void setTempoMinimoPermanencia(int tempoMinimoPermanencia) {
        if (tempoMinimoPermanencia < 0) {
            throw new IllegalArgumentException("Tempo mínimo de permanência não pode ser negativo.");
        }
        this.tempoMinimoPermanencia = tempoMinimoPermanencia;
    }

    public int getTipoResgate() { return tipoResgate; }

    public void setTipoResgate(int tipoResgate) {
        TipoResgate.doCodigo(tipoResgate);
        this.tipoResgate = tipoResgate;
    }

    public int getIof() { return iof; }

    public void setIof(int iof) {
        OpcaoIOF.doCodigo(iof);
        this.iof = iof;
    }

    public int getTipoImpostoRenda() { return tipoImpostoRenda; }

    public void setTipoImpostoRenda(int tipoImpostoRenda) {
        OpcaoIR.doCodigo(tipoImpostoRenda);
        this.tipoImpostoRenda = tipoImpostoRenda;
    }

    public int getDiasCorridos() { return diasCorridos; }
    public void setDiasCorridos(int diasCorridos) { this.diasCorridos = Math.max(0, diasCorridos); }

    public int getDiasEfetivosCalculo() { return diasEfetivosCalculo; }
    public void setDiasEfetivosCalculo(int diasEfetivosCalculo) { this.diasEfetivosCalculo = Math.max(0, diasEfetivosCalculo); }

    public BigDecimal getImpostoAtualDevido() { return impostoAtualDevido; }

    public void setImpostoAtualDevido(BigDecimal impostoAtualDevido) {
        this.impostoAtualDevido = impostoAtualDevido == null ? BigDecimal.ZERO : Moeda.arredondar(impostoAtualDevido);
    }

    // -------------------------------------------------------------------------
    // Consultas fiscais, acumuladores internos e resgate
    // -------------------------------------------------------------------------

    public BigDecimal getValorIof() { return calcularIofInterno(); }

    public BigDecimal getValorIr() { return calcularIrInterno(getValorIof()); }

    public BigDecimal getSaldo() { return saldo; }

    public void setSaldo(BigDecimal saldo) {
        this.saldo = saldo == null ? BigDecimal.ZERO : Moeda.arredondar(saldo);
        recalcularPosicaoInterno();
    }

    public BigDecimal getRendimentosCreditados() { return rendimentosCreditados; }
    public BigDecimal getRendimentosPendentes() { return rendimentosPendentes; }
    public int getDiasDesdeUltimoRendimento() { return diasDesdeUltimoRendimento; }

    /** Restaura acumuladores internos salvos em disco para retomar o ciclo de juros sem desvio. */
    public void restaurarAcumuladores(BigDecimal saldo, BigDecimal rendimentosCreditados,
                                      BigDecimal rendimentosPendentes, int diasDesdeUltimoRendimento) {
        if (diasDesdeUltimoRendimento < 0) {
            throw new IllegalArgumentException("Dias desde o último rendimento não podem ser negativos.");
        }
        this.saldo = Moeda.naoNegativo(saldo, "Saldo");
        this.rendimentosCreditados = Moeda.naoNegativo(rendimentosCreditados, "Rendimentos creditados");
        this.rendimentosPendentes = Moeda.naoNegativo(rendimentosPendentes, "Rendimentos pendentes");
        this.diasDesdeUltimoRendimento = diasDesdeUltimoRendimento;
        recalcularPosicaoInterno();
    }

    public LocalDate getDataAplicacao() { return dataAplicacao; }

    public void setDataAplicacao(LocalDate dataAplicacao) {
        this.dataAplicacao = Objects.requireNonNull(dataAplicacao, "Data da aplicação é obrigatória.");
    }

    public LocalDate getDataUltimaAtualizacao() { return dataUltimaAtualizacao; }
    public void setDataUltimaAtualizacao(LocalDate dataUltimaAtualizacao) { this.dataUltimaAtualizacao = dataUltimaAtualizacao; }

    public int getQuantidadeOperacoes() { return quantidadeOperacoes; }
    public void setQuantidadeOperacoes(int quantidadeOperacoes) { this.quantidadeOperacoes = Math.max(0, quantidadeOperacoes); }

    public ConvencaoContagemDias getConvencaoContagemDias() { return convencaoContagemDias; }

    public void setConvencaoContagemDias(ConvencaoContagemDias convencaoContagemDias) {
        this.convencaoContagemDias = convencaoContagemDias == null
                ? ConvencaoContagemDias.COMERCIAL_30_360
                : convencaoContagemDias;
    }

    public RegimeTributario getRegimeTributario() { return regimeTributario; }

    public void setRegimeTributario(RegimeTributario regimeTributario) {
        this.regimeTributario = regimeTributario == null ? RegimeTributario.PADRAO : regimeTributario;
        if (this.regimeTributario.ehIsento()) {
            this.iof = 0;
            this.tipoImpostoRenda = 0;
        }
        recalcularPosicaoInterno();
    }

    public boolean podeResgatar() {
        return podeResgatar(LocalDate.now());
    }

    public boolean podeResgatar(LocalDate data) {
        if (this.diasCorridos < this.tempoMinimoPermanencia) {
            return false;
        }
        int intervalo = TipoResgate.doCodigo(this.tipoResgate).getIntervaloDias();
        return intervalo == 1 || this.diasCorridos % intervalo == 0;
    }

    /** Efetua resgate parcial ou total, consolidando rendimentos pendentes antes da dedução. */
    public void resgatar(BigDecimal valor) {
        if (valor == null || valor.signum() <= 0) {
            throw new IllegalArgumentException("Valor de resgate deve ser estritamente positivo.");
        }
        BigDecimal resgateArredondado = Moeda.arredondar(valor);
        if (resgateArredondado.compareTo(this.valorAtualLiquido) > 0) {
            throw new IllegalArgumentException("Saldo líquido insuficiente para o resgate: "
                    + resgateArredondado + " > " + this.valorAtualLiquido);
        }

        if (this.rendimentosPendentes.signum() > 0) {
            this.saldo = this.saldo.add(this.rendimentosPendentes);
            this.rendimentosCreditados = this.rendimentosCreditados.add(this.rendimentosPendentes);
            this.rendimentosPendentes = BigDecimal.ZERO;
        }

        this.saldo = resgateArredondado.compareTo(this.valorAtualLiquido) == 0
                ? BigDecimal.ZERO
                : this.saldo.subtract(resgateArredondado);
        this.valorResgate = this.valorResgate.add(resgateArredondado);
        recalcularPosicaoInterno();
    }

    @Override
    public String toString() {
        return "AplicacaoFinanceira{numero='" + numeroAplicacao + '\''
                + ", nome='" + nomeAplicacao + '\''
                + ", valorAplicado=" + valorAplicado
                + ", valorAtualBruto=" + valorAtualBruto
                + ", valorAtualLiquido=" + valorAtualLiquido
                + ", taxaMensal=" + taxaMensal
                + ", taxaDiaria=" + taxaDiaria
                + ", diasCorridos=" + diasCorridos
                + ", impostoDevido=" + impostoAtualDevido + '}';
    }
}

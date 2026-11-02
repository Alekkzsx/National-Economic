package dominio;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Representa uma Aplicação Financeira em conformidade com o Item 1 da Atividade Prática Acadêmica.
 *
 * <p>Esta classe é passível de herança (não-final), encapsula os 19 atributos obrigatórios do sistema
 * financeiro didático, mantém equivalência linear matemática bidirecional entre as taxas mensal e diária
 * para mês comercial de 30 dias com precisão de {@link MathContext#DECIMAL128}, apura impostos
 * incidentes (IOF e IR Regressivo) e gerencia o ciclo temporal de rendimentos em dias úteis.</p>
 *
 * <p>A classe adota práticas defensivas de herança: seus construtores não despacham chamadas a métodos
 * virtuais sobrescrevíveis, prevenindo corrupção de estado em especializações por subclasses.</p>
 */
public class AplicacaoFinanceira {

    private static final MathContext CONTEXTO_TAXA = MathContext.DECIMAL128;

    // =========================================================================
    // 19 Atributos Obrigatórios da Atividade Acadêmica (Item 1, R1.1)
    // =========================================================================

    /** 1. Nome legível descritivo da aplicação financeira. */
    private String nomeAplicacao = "Aplicação Padrão";

    /** 2. Número da aplicação: rótulo/identificador único no sistema bancário. */
    private String numeroAplicacao = "0";

    /** 3. Valor monetário inicialmente aplicado (aporte inicial). */
    private BigDecimal valorAplicado = BigDecimal.ZERO;

    /** 4. Rendimento atual acumulado antes da retenção tributária. */
    private BigDecimal rendimentoAtualBruto = BigDecimal.ZERO;

    /** 5. Rendimento líquido acumulado após dedução de todos os impostos devidos. */
    private BigDecimal rendimentoAtualLiquido = BigDecimal.ZERO;

    /** 6. Montante total acumulado de resgates já efetuados. */
    private BigDecimal valorResgate = BigDecimal.ZERO;

    /** 7. Saldo patrimonial bruto atual (saldo base + rendimentos pendentes). */
    private BigDecimal valorAtualBruto = BigDecimal.ZERO;

    /** 8. Saldo patrimonial líquido atual deduzido de tributos devidos. */
    private BigDecimal valorAtualLiquido = BigDecimal.ZERO;

    /** 9. Taxa de juros mensal como fração decimal (ex.: 0.03 para 3%). */
    private BigDecimal taxaMensal = BigDecimal.ZERO;

    /** 10. Taxa de juros diária proporcional para mês comercial de 30 dias. */
    private BigDecimal taxaDiaria = BigDecimal.ZERO;

    /** 11. Periodicidade de rendimento (0=Diário..5=No Prazo). */
    private int tipoRendimento = 0;

    /** 12. Prazo até o vencimento da aplicação em dias corridos (0 indica prazo indeterminado). */
    private int tempoFinalVencimento = 0;

    /** 13. Prazo de carência mínima em dias corridos antes de permitir resgates. */
    private int tempoMinimoPermanencia = 0;

    /** 14. Periodicidade mínima entre resgates permitidos (0=Diário..4=Anual). */
    private int tipoResgate = 0;

    /** 15. Regime de IOF (0=Isento, 1=3,5% com carência de 30 dias, 2=3,5% irrestrito). */
    private int iof = 1;

    /** 16. Regime de Imposto de Renda (0=Isento, 1=Tabela Regressiva). */
    private int tipoImpostoRenda = 1;

    /** 17. Total de dias civis corridos desde a data de aplicação. */
    private int diasCorridos = 0;

    /** 18. Total de dias úteis efetivos com cálculo de rendimento. */
    private int diasEfetivosCalculo = 0;

    /** 19. Montante monetário total de tributos devidos (IOF + IR). */
    private BigDecimal impostoAtualDevido = BigDecimal.ZERO;

    // =========================================================================
    // Atributos de Controle Operacional Interno
    // =========================================================================

    /** Data de início da aplicação. */
    private LocalDate dataAplicacao;

    /** Última data civil em que ocorreu atualização de saldo. */
    private LocalDate dataUltimaAtualizacao;

    /** Saldo base já creditado que serve de base para cálculo dos juros do próximo dia útil. */
    private BigDecimal saldo = BigDecimal.ZERO;

    /** Rendimentos incorporados definitivamente ao saldo. */
    private BigDecimal rendimentosCreditados = BigDecimal.ZERO;

    /** Rendimentos auferidos diariamente que aguardam a data de corte/aniversário da periodicidade. */
    private BigDecimal rendimentosPendentes = BigDecimal.ZERO;

    /** Contador de dias civis transcorridos desde o último crédito de rendimento. */
    private int diasDesdeUltimoRendimento = 0;

    /** Contador de operações de fluxo vinculadas. */
    private int quantidadeOperacoes = 0;

    /** Convenção de contagem de dias adotada na aplicação. */
    private ConvencaoContagemDias convencaoContagemDias = ConvencaoContagemDias.COMERCIAL_30_360;

    /** Regime tributário geral da aplicação. */
    private RegimeTributario regimeTributario = RegimeTributario.PADRAO;

    // =========================================================================
    // Construtores Seguros contra Despacho Sobrescrevível
    // =========================================================================

    /**
     * Construtor padrão que inicializa a aplicação com parâmetros seguros na data corrente.
     */
    public AplicacaoFinanceira() {
        this.nomeAplicacao = "Aplicação Padrão";
        this.numeroAplicacao = "0";
        this.dataAplicacao = LocalDate.now();
        this.dataUltimaAtualizacao = null;
        this.saldo = BigDecimal.ZERO;
        this.valorAplicado = BigDecimal.ZERO;
        recalcularPosicaoInterno();
    }

    /**
     * Construtor essencial com início na data corrente do sistema.
     *
     * @param nomeAplicacao nome legível da aplicação
     * @param numeroAplicacao identificador único da aplicação
     * @param valorAplicado aporte financeiro inicial (> 0)
     * @param taxaMensal taxa mensal fracionária (>= 0)
     * @throws IllegalArgumentException se algum dado for inválido
     */
    public AplicacaoFinanceira(String nomeAplicacao, String numeroAplicacao, BigDecimal valorAplicado, BigDecimal taxaMensal) {
        this(nomeAplicacao, numeroAplicacao, valorAplicado, taxaMensal, LocalDate.now());
    }

    /**
     * Construtor essencial com especificação explícita da data inicial de aplicação.
     *
     * @param nomeAplicacao nome legível da aplicação
     * @param numeroAplicacao identificador único da aplicação
     * @param valorAplicado aporte financeiro inicial (> 0)
     * @param taxaMensal taxa mensal fracionária (>= 0)
     * @param dataAplicacao data civil inicial
     * @throws IllegalArgumentException se algum dado for inválido
     * @throws NullPointerException se dataAplicacao for nula
     */
    public AplicacaoFinanceira(String nomeAplicacao, String numeroAplicacao, BigDecimal valorAplicado,
                               BigDecimal taxaMensal, LocalDate dataAplicacao) {
        inicializarSeguro(nomeAplicacao, numeroAplicacao, valorAplicado, taxaMensal, dataAplicacao);
    }

    /**
     * Construtor completo configurando todos os termos didáticos da aplicação na data corrente.
     *
     * @param nome nome descritivo da aplicação
     * @param numero identificador único da aplicação
     * @param valor aporte inicial (> 0)
     * @param taxaMensal taxa de juros mensal
     * @param tipoRendimento código da periodicidade de rendimento (0 a 5)
     * @param tempoVencimento prazo até o vencimento em dias corridos (>= 0)
     * @param tempoPermanencia prazo de carência em dias corridos (>= 0)
     * @param tipoResgate código da periodicidade de resgate (0 a 4)
     * @param iof política de IOF (0, 1 ou 2)
     * @param tipoIR política de IR (0 ou 1)
     */
    public AplicacaoFinanceira(String nome, String numero, BigDecimal valor, BigDecimal taxaMensal,
                               int tipoRendimento, int tempoVencimento, int tempoPermanencia,
                               int tipoResgate, int iof, int tipoIR) {
        this(nome, numero, valor, taxaMensal, tipoRendimento, tempoVencimento, tempoPermanencia, tipoResgate, iof, tipoIR, LocalDate.now());
    }

    /**
     * Construtor completo com todos os parâmetros didáticos e data civil explícita.
     *
     * @param nome nome descritivo da aplicação
     * @param numero identificador único da aplicação
     * @param valor aporte inicial (> 0)
     * @param taxaMensal taxa de juros mensal
     * @param tipoRendimento código da periodicidade de rendimento (0 a 5)
     * @param tempoVencimento prazo até o vencimento em dias corridos (>= 0)
     * @param tempoPermanencia prazo de carência em dias corridos (>= 0)
     * @param tipoResgate código da periodicidade de resgate (0 a 4)
     * @param iof política de IOF (0, 1 ou 2)
     * @param tipoIR política de IR (0 ou 1)
     * @param dataAplicacao data civil de início da aplicação
     */
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

    /**
     * Inicializador privado invocado pelos construtores sem despachar chamadas virtuais a subclasses.
     */
    private void inicializarSeguro(String nome, String numero, BigDecimal valor, BigDecimal taxa, LocalDate data) {
        if (nome == null || nome.trim().isEmpty()) {
            throw new IllegalArgumentException("Nome da aplicação não pode ser nulo ou vazio.");
        }
        if (numero == null || numero.trim().isEmpty()) {
            throw new IllegalArgumentException("Número da aplicação não pode ser nulo ou vazio.");
        }
        if (valor == null || valor.signum() <= 0) {
            throw new IllegalArgumentException("Valor aplicado inicial deve ser estritamente positivo.");
        }
        if (taxa == null || taxa.signum() < 0) {
            throw new IllegalArgumentException("Taxa mensal não pode ser negativa.");
        }
        this.nomeAplicacao = nome.trim();
        this.numeroAplicacao = numero.trim();
        this.valorAplicado = Moeda.arredondar(valor);
        this.saldo = this.valorAplicado;
        this.taxaMensal = taxa;
        this.taxaDiaria = (taxa.signum() == 0)
                ? BigDecimal.ZERO
                : taxa.divide(BigDecimal.valueOf(30), CONTEXTO_TAXA).stripTrailingZeros();
        this.dataAplicacao = Objects.requireNonNull(data, "Data de aplicação não pode ser nula.");
        this.dataUltimaAtualizacao = null;
        recalcularPosicaoInterno();
    }

    private static void validarParametrosCadastrais(int tipoRendimento, int tempoVencimento, int tempoPermanencia,
                                                   int tipoResgate, int iof, int tipoIR) {
        if (tipoRendimento < 0 || tipoRendimento > 5) {
            throw new IllegalArgumentException("Tipo de rendimento inválido: " + tipoRendimento + ". Deve ser de 0 a 5.");
        }
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
        if (tipoResgate < 0 || tipoResgate > 4) {
            throw new IllegalArgumentException("Tipo de resgate inválido: " + tipoResgate + ". Deve ser de 0 a 4.");
        }
        if (iof < 0 || iof > 2) {
            throw new IllegalArgumentException("Opção de IOF inválida: " + iof + ". Deve ser 0, 1 ou 2.");
        }
        if (tipoIR < 0 || tipoIR > 1) {
            throw new IllegalArgumentException("Opção de IR inválida: " + tipoIR + ". Deve ser 0 ou 1.");
        }
    }

    // =========================================================================
    // Métodos Operacionais Obrigatórios
    // =========================================================================

    /**
     * Inicializa ou reconfigura os parâmetros cadastrais e tributários da aplicação financeira.
     *
     * @param nome nome descritivo da aplicação
     * @param numero identificador único da aplicação
     * @param valor valor monetário do aporte inicial (> 0)
     * @param taxaMensal taxa de juros mensal como fração decimal
     * @param tipoRendimento periodicidade de crédito dos rendimentos (0 a 5)
     * @param tempoVencimento prazo até o vencimento em dias corridos (>= 0)
     * @param tempoPermanencia prazo de carência em dias corridos (&gt;= 0 e &lt;= vencimento)
     * @param tipoResgate intervalo mínimo entre resgates autorizados (0 a 4)
     * @param iof política de IOF (0, 1 ou 2)
     * @param tipoIR política de IR (0 ou 1)
     * @throws IllegalArgumentException se algum parâmetro for inválido ou inconsistente
     */
    public void setCadastro(
            String nome,
            String numero,
            BigDecimal valor,
            BigDecimal taxaMensal,
            int tipoRendimento,
            int tempoVencimento,
            int tempoPermanencia,
            int tipoResgate,
            int iof,
            int tipoIR
    ) {
        if (nome == null || nome.trim().isEmpty()) {
            throw new IllegalArgumentException("Nome da aplicação não pode ser nulo ou vazio.");
        }
        if (numero == null || numero.trim().isEmpty()) {
            throw new IllegalArgumentException("Número da aplicação não pode ser nulo ou vazio.");
        }
        if (valor == null || valor.signum() <= 0) {
            throw new IllegalArgumentException("Valor aplicado inicial deve ser estritamente positivo.");
        }
        if (taxaMensal == null || taxaMensal.signum() < 0) {
            throw new IllegalArgumentException("Taxa mensal não pode ser negativa.");
        }
        validarParametrosCadastrais(tipoRendimento, tempoVencimento, tempoPermanencia, tipoResgate, iof, tipoIR);

        this.nomeAplicacao = nome.trim();
        this.numeroAplicacao = numero.trim();
        this.valorAplicado = Moeda.arredondar(valor);
        if (this.saldo == null || this.saldo.signum() == 0) {
            this.saldo = this.valorAplicado;
        }
        this.taxaMensal = taxaMensal;
        this.taxaDiaria = (taxaMensal.signum() == 0)
                ? BigDecimal.ZERO
                : taxaMensal.divide(BigDecimal.valueOf(30), CONTEXTO_TAXA).stripTrailingZeros();
        this.tipoRendimento = tipoRendimento;
        this.tempoFinalVencimento = tempoVencimento;
        this.tempoMinimoPermanencia = tempoPermanencia;
        this.tipoResgate = tipoResgate;
        this.iof = iof;
        this.tipoImpostoRenda = tipoIR;
        recalcularPosicaoInterno();
    }

    /**
     * Sobrecarga polimórfica simplificada de cadastro com opções operacionais padrão.
     *
     * @param nome nome descritivo da aplicação
     * @param numero identificador único da aplicação
     * @param valor valor monetário do aporte inicial (> 0)
     * @param taxaMensal taxa mensal fracionária
     */
    public void setCadastro(String nome, String numero, BigDecimal valor, BigDecimal taxaMensal) {
        setCadastro(nome, numero, valor, taxaMensal, 0, 0, 0, 0, 1, 1);
    }

    /**
     * Incrementa o contador de dias civis corridos e o contador do ciclo de periodicidade.
     */
    public void incrementarDia() {
        this.diasCorridos++;
        this.diasDesdeUltimoRendimento++;
    }

    /**
     * Incrementa o contador de dias efetivos de aplicação (dias úteis com correção).
     *
     * <p>Atende ao nome especificado no Item 1 da atividade acadêmica.</p>
     */
    public void incrementarDiaAplicação() {
        incrementarDiaAplicacao();
    }

    /**
     * Alias sem acento de {@link #incrementarDiaAplicação()} para garantia de portabilidade em teclados e charsets.
     */
    public void incrementarDiaAplicacao() {
        this.diasEfetivosCalculo++;
    }

    /**
     * Atualiza o saldo e a rentabilidade considerando a data civil corrente do sistema.
     */
    public void atualizarSaldo() {
        atualizarSaldo(LocalDate.now());
    }

    /**
     * Atualiza o saldo e a rentabilidade acumulada até a data civil de referência informada.
     *
     * <p>A evolução do rendimento diário é restrita aos dias úteis (segunda a sexta-feira).
     * Se a data de referência coincidir com sábado ou domingo, os dias úteis anteriores decorridos
     * são integralmente computados, sem incidência de novos rendimentos nos dias de fim de semana.</p>
     *
     * @param dataReferencia data civil até a qual o processamento deve ocorrer
     * @throws NullPointerException se dataReferencia for nula
     * @throws IllegalArgumentException se dataReferencia for anterior à data de início da aplicação
     */
    public void atualizarSaldo(LocalDate dataReferencia) {
        Objects.requireNonNull(dataReferencia, "Data de referência não pode ser nula.");
        if (dataReferencia.isBefore(this.dataAplicacao)) {
            throw new IllegalArgumentException("Data de referência não pode ser anterior à data da aplicação.");
        }

        if (this.dataUltimaAtualizacao != null && !dataReferencia.isAfter(this.dataUltimaAtualizacao)) {
            return;
        }

        LocalDate cursor = (this.dataUltimaAtualizacao == null)
                ? this.dataAplicacao
                : this.dataUltimaAtualizacao.plusDays(1);

        while (!cursor.isAfter(dataReferencia)) {
            incrementarDia();
            DayOfWeek diaDaSemana = cursor.getDayOfWeek();
            boolean ehDiaUtil = (diaDaSemana != DayOfWeek.SATURDAY && diaDaSemana != DayOfWeek.SUNDAY);

            if (ehDiaUtil) {
                incrementarDiaAplicacao();
                boolean dentroDoPrazo = (this.tempoFinalVencimento == 0 || this.diasCorridos <= this.tempoFinalVencimento);
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
        return switch (this.tipoRendimento) {
            case 0 -> this.diasDesdeUltimoRendimento >= 1; // Diário
            case 1 -> this.diasDesdeUltimoRendimento >= 30; // Mensal
            case 2 -> this.diasDesdeUltimoRendimento >= 90; // Trimestral
            case 3 -> this.diasDesdeUltimoRendimento >= 180; // Semestral
            case 4 -> this.diasDesdeUltimoRendimento >= 365; // Anual
            case 5 -> (this.tempoFinalVencimento > 0 && this.diasCorridos >= this.tempoFinalVencimento); // No prazo
            default -> true;
        };
    }

    /**
     * Calcula internamente o IOF sem despacho virtual polimórfico,
     * prevenindo escape de métodos virtuais durante a construção da classe.
     *
     * @return montante de IOF devido em centavos
     */
    private BigDecimal calcularIofInterno() {
        return CalculadoraTributaria.calcularIof(this.rendimentoAtualBruto, this.diasCorridos, this.iof);
    }

    /**
     * Calcula internamente o IR sobre a base líquida de IOF sem despacho virtual polimórfico,
     * prevenindo escape de métodos virtuais durante a construção da classe.
     *
     * @param iofDevido valor de IOF previamente apurado
     * @return montante de IR devido em centavos
     */
    private BigDecimal calcularIrInterno(BigDecimal iofDevido) {
        return CalculadoraTributaria.calcularIr(this.rendimentoAtualBruto, iofDevido, this.diasCorridos, this.tipoImpostoRenda);
    }

    /**
     * Recalcula internamente a posição financeira e os tributos incidentes.
     */
    private void recalcularPosicaoInterno() {
        this.rendimentoAtualBruto = Moeda.arredondar(this.rendimentosCreditados.add(this.rendimentosPendentes));
        BigDecimal iofDevido = calcularIofInterno();
        BigDecimal irDevido = calcularIrInterno(iofDevido);
        this.impostoAtualDevido = Moeda.arredondar(iofDevido.add(irDevido));
        this.rendimentoAtualLiquido = this.rendimentoAtualBruto.subtract(this.impostoAtualDevido).max(Moeda.ZERO);
        this.valorAtualBruto = Moeda.arredondar(this.saldo.add(this.rendimentosPendentes));
        this.valorAtualLiquido = this.valorAtualBruto.subtract(this.impostoAtualDevido).max(Moeda.ZERO);
    }

    // =========================================================================
    // Sincronização Matemática Linear de Taxas (Mês Comercial de 30 Dias)
    // =========================================================================

    /**
     * Retorna a taxa mensal fracionária.
     *
     * @return taxa mensal (ex.: 0.03 para 3%)
     */
    public BigDecimal getTaxaMensal() {
        return taxaMensal;
    }

    /**
     * Define a taxa de juros mensal e sincroniza linearmente a taxa diária para mês comercial de 30 dias.
     *
     * @param taxaMensal taxa mensal fracionária (>= 0)
     * @throws IllegalArgumentException se taxaMensal for nula ou negativa
     */
    public void setTaxaMensal(BigDecimal taxaMensal) {
        if (taxaMensal == null) {
            throw new IllegalArgumentException("Taxa mensal não pode ser nula.");
        }
        if (taxaMensal.signum() < 0) {
            throw new IllegalArgumentException("Taxa mensal não pode ser negativa.");
        }
        this.taxaMensal = taxaMensal;
        if (taxaMensal.signum() == 0) {
            this.taxaDiaria = BigDecimal.ZERO;
        } else {
            this.taxaDiaria = taxaMensal.divide(BigDecimal.valueOf(30), CONTEXTO_TAXA).stripTrailingZeros();
        }
    }

    /**
     * Retorna a taxa diária fracionária.
     *
     * @return taxa diária linear
     */
    public BigDecimal getTaxaDiaria() {
        return taxaDiaria;
    }

    /**
     * Define a taxa de juros diária e sincroniza linearmente a taxa mensal para mês comercial de 30 dias.
     *
     * @param taxaDiaria taxa diária fracionária (>= 0)
     * @throws IllegalArgumentException se taxaDiaria for nula ou negativa
     */
    public void setTaxaDiaria(BigDecimal taxaDiaria) {
        if (taxaDiaria == null) {
            throw new IllegalArgumentException("Taxa diária não pode ser nula.");
        }
        if (taxaDiaria.signum() < 0) {
            throw new IllegalArgumentException("Taxa diária não pode ser negativa.");
        }
        this.taxaDiaria = taxaDiaria;
        if (taxaDiaria.signum() == 0) {
            this.taxaMensal = BigDecimal.ZERO;
        } else {
            this.taxaMensal = taxaDiaria.multiply(BigDecimal.valueOf(30), CONTEXTO_TAXA).stripTrailingZeros();
        }
    }

    // =========================================================================
    // Getters e Setters dos 19 Atributos Obrigatórios
    // =========================================================================

    /**
     * Retorna o nome descritivo da aplicação financeira.
     *
     * @return nome da aplicação
     */
    public String getNomeAplicacao() {
        return nomeAplicacao;
    }

    /**
     * Atualiza o nome descritivo da aplicação financeira.
     *
     * @param nomeAplicacao novo nome não nulo e não em branco
     * @throws IllegalArgumentException se o nome for nulo ou vazio
     */
    public void setNomeAplicacao(String nomeAplicacao) {
        if (nomeAplicacao == null || nomeAplicacao.trim().isEmpty()) {
            throw new IllegalArgumentException("Nome da aplicação não pode ser nulo ou vazio.");
        }
        this.nomeAplicacao = nomeAplicacao.trim();
    }

    /**
     * Retorna o identificador/número único da aplicação financeira.
     *
     * @return identificador único
     */
    public String getNumeroAplicacao() {
        return numeroAplicacao;
    }

    /**
     * Atualiza o identificador/número único da aplicação financeira.
     *
     * @param numeroAplicacao novo identificador não vazio
     * @throws IllegalArgumentException se o identificador for nulo ou vazio
     */
    public void setNumeroAplicacao(String numeroAplicacao) {
        if (numeroAplicacao == null || numeroAplicacao.trim().isEmpty()) {
            throw new IllegalArgumentException("Número da aplicação não pode ser nulo ou vazio.");
        }
        this.numeroAplicacao = numeroAplicacao.trim();
    }

    /**
     * Retorna o montante originalmente aplicado.
     *
     * @return valor aplicado inicial
     */
    public BigDecimal getValorAplicado() {
        return valorAplicado;
    }

    /**
     * Atualiza o montante aplicado inicial.
     *
     * @param valorAplicado novo valor aplicado (> 0)
     * @throws IllegalArgumentException se o valor for nulo ou menor ou igual a zero
     */
    public void setValorAplicado(BigDecimal valorAplicado) {
        if (valorAplicado == null || valorAplicado.signum() <= 0) {
            throw new IllegalArgumentException("Valor aplicado deve ser estritamente positivo.");
        }
        this.valorAplicado = Moeda.arredondar(valorAplicado);
    }

    /**
     * Retorna o rendimento atual bruto apurado.
     *
     * @return rendimento bruto em centavos
     */
    public BigDecimal getRendimentoAtualBruto() {
        return rendimentoAtualBruto;
    }

    /**
     * Define diretamente o rendimento atual bruto.
     *
     * @param rendimentoAtualBruto novo rendimento bruto
     */
    public void setRendimentoAtualBruto(BigDecimal rendimentoAtualBruto) {
        this.rendimentoAtualBruto = (rendimentoAtualBruto == null) ? BigDecimal.ZERO : Moeda.arredondar(rendimentoAtualBruto);
    }

    /**
     * Retorna o rendimento atual líquido deduzido de impostos.
     *
     * @return rendimento líquido em centavos
     */
    public BigDecimal getRendimentoAtualLiquido() {
        return rendimentoAtualLiquido;
    }

    /**
     * Define diretamente o rendimento atual líquido.
     *
     * @param rendimentoAtualLiquido novo rendimento líquido
     */
    public void setRendimentoAtualLiquido(BigDecimal rendimentoAtualLiquido) {
        this.rendimentoAtualLiquido = (rendimentoAtualLiquido == null) ? BigDecimal.ZERO : Moeda.arredondar(rendimentoAtualLiquido);
    }

    /**
     * Retorna o montante financeiro total de resgates efetuados.
     *
     * @return total resgatado em centavos
     */
    public BigDecimal getValorResgate() {
        return valorResgate;
    }

    /**
     * Define diretamente o montante acumulado de resgates.
     *
     * @param valorResgate novo total de resgates
     */
    public void setValorResgate(BigDecimal valorResgate) {
        this.valorResgate = (valorResgate == null) ? BigDecimal.ZERO : Moeda.arredondar(valorResgate);
    }

    /**
     * Retorna o saldo bruto total da aplicação (saldo + rendimentos pendentes).
     *
     * @return valor atual bruto em centavos
     */
    public BigDecimal getValorAtualBruto() {
        return valorAtualBruto;
    }

    /**
     * Define diretamente o saldo patrimonial bruto atual.
     *
     * @param valorAtualBruto novo valor atual bruto
     */
    public void setValorAtualBruto(BigDecimal valorAtualBruto) {
        this.valorAtualBruto = (valorAtualBruto == null) ? BigDecimal.ZERO : Moeda.arredondar(valorAtualBruto);
    }

    /**
     * Retorna o saldo líquido total da aplicação disponível para o cliente.
     *
     * @return valor atual líquido em centavos
     */
    public BigDecimal getValorAtualLiquido() {
        return valorAtualLiquido;
    }

    /**
     * Define diretamente o saldo patrimonial líquido atual.
     *
     * @param valorAtualLiquido novo valor atual líquido
     */
    public void setValorAtualLiquido(BigDecimal valorAtualLiquido) {
        this.valorAtualLiquido = (valorAtualLiquido == null) ? BigDecimal.ZERO : Moeda.arredondar(valorAtualLiquido);
    }

    /**
     * Retorna o código da periodicidade de crédito dos rendimentos (0 a 5).
     *
     * @return código numérico
     */
    public int getTipoRendimento() {
        return tipoRendimento;
    }

    /**
     * Define o código da periodicidade de rendimento.
     *
     * @param tipoRendimento código de 0 a 5
     * @throws IllegalArgumentException se o código for inválido
     */
    public void setTipoRendimento(int tipoRendimento) {
        if (tipoRendimento < 0 || tipoRendimento > 5) {
            throw new IllegalArgumentException("Tipo de rendimento inválido: " + tipoRendimento);
        }
        this.tipoRendimento = tipoRendimento;
    }

    /**
     * Retorna o prazo final de vencimento em dias corridos.
     *
     * @return prazo em dias corridos (0 indica sem vencimento)
     */
    public int getTempoFinalVencimento() {
        return tempoFinalVencimento;
    }

    /**
     * Define o prazo final de vencimento em dias corridos.
     *
     * @param tempoFinalVencimento prazo em dias (>= 0)
     * @throws IllegalArgumentException se for negativo
     */
    public void setTempoFinalVencimento(int tempoFinalVencimento) {
        if (tempoFinalVencimento < 0) {
            throw new IllegalArgumentException("Tempo final de vencimento não pode ser negativo.");
        }
        this.tempoFinalVencimento = tempoFinalVencimento;
    }

    /**
     * Retorna o prazo mínimo de carência antes do primeiro resgate em dias corridos.
     *
     * @return carência em dias
     */
    public int getTempoMinimoPermanencia() {
        return tempoMinimoPermanencia;
    }

    /**
     * Define o prazo mínimo de permanência em dias corridos.
     *
     * @param tempoMinimoPermanencia prazo em dias (>= 0)
     * @throws IllegalArgumentException se for negativo
     */
    public void setTempoMinimoPermanencia(int tempoMinimoPermanencia) {
        if (tempoMinimoPermanencia < 0) {
            throw new IllegalArgumentException("Tempo mínimo de permanência não pode ser negativo.");
        }
        this.tempoMinimoPermanencia = tempoMinimoPermanencia;
    }

    /**
     * Retorna o código do intervalo entre resgates autorizados (0 a 4).
     *
     * @return código do tipo de resgate
     */
    public int getTipoResgate() {
        return tipoResgate;
    }

    /**
     * Define o código do intervalo entre resgates.
     *
     * @param tipoResgate código de 0 a 4
     * @throws IllegalArgumentException se o código for inválido
     */
    public void setTipoResgate(int tipoResgate) {
        if (tipoResgate < 0 || tipoResgate > 4) {
            throw new IllegalArgumentException("Tipo de resgate inválido: " + tipoResgate);
        }
        this.tipoResgate = tipoResgate;
    }

    /**
     * Retorna o código do regime de IOF (0=Isento, 1=Carência 30d, 2=Irrestrito).
     *
     * @return código de IOF (0, 1 ou 2)
     */
    public int getIof() {
        return iof;
    }

    /**
     * Define o código do regime de IOF.
     *
     * @param iof código de 0 a 2
     * @throws IllegalArgumentException se o código for inválido
     */
    public void setIof(int iof) {
        if (iof < 0 || iof > 2) {
            throw new IllegalArgumentException("Opção de IOF inválida: " + iof);
        }
        this.iof = iof;
    }

    /**
     * Retorna o código da política de Imposto de Renda (0=Isento, 1=Tabela Regressiva).
     *
     * @return código de IR (0 ou 1)
     */
    public int getTipoImpostoRenda() {
        return tipoImpostoRenda;
    }

    /**
     * Define o código da política de Imposto de Renda.
     *
     * @param tipoImpostoRenda código 0 ou 1
     * @throws IllegalArgumentException se o código for inválido
     */
    public void setTipoImpostoRenda(int tipoImpostoRenda) {
        if (tipoImpostoRenda < 0 || tipoImpostoRenda > 1) {
            throw new IllegalArgumentException("Opção de IR inválida: " + tipoImpostoRenda);
        }
        this.tipoImpostoRenda = tipoImpostoRenda;
    }

    /**
     * Retorna a quantidade de dias corridos decorridos desde o aporte inicial.
     *
     * @return dias corridos
     */
    public int getDiasCorridos() {
        return diasCorridos;
    }

    /**
     * Define diretamente a quantidade de dias corridos transcorridos.
     *
     * @param diasCorridos dias corridos (>= 0)
     */
    public void setDiasCorridos(int diasCorridos) {
        this.diasCorridos = Math.max(0, diasCorridos);
    }

    /**
     * Retorna a quantidade de dias úteis com efetivo cálculo de rendimento.
     *
     * @return dias úteis com correção
     */
    public int getDiasEfetivosCalculo() {
        return diasEfetivosCalculo;
    }

    /**
     * Define diretamente a quantidade de dias efetivos de cálculo.
     *
     * @param diasEfetivosCalculo dias úteis com rendimento
     */
    public void setDiasEfetivosCalculo(int diasEfetivosCalculo) {
        this.diasEfetivosCalculo = Math.max(0, diasEfetivosCalculo);
    }

    /**
     * Retorna o montante total apurado de tributos devidos (IOF + IR).
     *
     * @return total de tributos devidos
     */
    public BigDecimal getImpostoAtualDevido() {
        return impostoAtualDevido;
    }

    /**
     * Define diretamente o total de impostos devidos.
     *
     * @param impostoAtualDevido novo montante de tributos
     */
    public void setImpostoAtualDevido(BigDecimal impostoAtualDevido) {
        this.impostoAtualDevido = (impostoAtualDevido == null) ? BigDecimal.ZERO : Moeda.arredondar(impostoAtualDevido);
    }

    // =========================================================================
    // Métodos Auxiliares de Consulta Tributária e Patrimonial
    // =========================================================================

    /**
     * Retorna o valor monetário apurado de IOF em reais com base no rendimento bruto e dias corridos.
     *
     * @return montante de IOF devido em centavos
     */
    public BigDecimal getValorIof() {
        return calcularIofInterno();
    }

    /**
     * Retorna o valor monetário apurado de Imposto de Renda em reais sobre a base líquida de IOF.
     *
     * @return montante de IR devido em centavos
     */
    public BigDecimal getValorIr() {
        BigDecimal iofDevido = getValorIof();
        return calcularIrInterno(iofDevido);
    }

    /**
     * Retorna o saldo patrimonial base já incorporado.
     *
     * @return saldo creditado
     */
    public BigDecimal getSaldo() {
        return saldo;
    }

    /**
     * Define o saldo base creditado e recalcula a posição patrimonial.
     *
     * @param saldo novo saldo base
     */
    public void setSaldo(BigDecimal saldo) {
        this.saldo = (saldo == null) ? BigDecimal.ZERO : Moeda.arredondar(saldo);
        recalcularPosicaoInterno();
    }

    /**
     * Retorna a data civil de início da aplicação.
     *
     * @return data de aplicação
     */
    public LocalDate getDataAplicacao() {
        return dataAplicacao;
    }

    /**
     * Atualiza a data de início da aplicação.
     *
     * @param dataAplicacao nova data de início
     */
    public void setDataAplicacao(LocalDate dataAplicacao) {
        this.dataAplicacao = Objects.requireNonNull(dataAplicacao, "Data da aplicação é obrigatória.");
    }

    /**
     * Retorna a última data em que ocorreu cálculo de juros.
     *
     * @return última data civil processada
     */
    public LocalDate getDataUltimaAtualizacao() {
        return dataUltimaAtualizacao;
    }

    /**
     * Define a última data em que ocorreu cálculo de juros.
     *
     * @param dataUltimaAtualizacao data processada
     */
    public void setDataUltimaAtualizacao(LocalDate dataUltimaAtualizacao) {
        this.dataUltimaAtualizacao = dataUltimaAtualizacao;
    }

    /**
     * Retorna a quantidade de lançamentos de operações financeiras vinculadas.
     *
     * @return contagem de lançamentos
     */
    public int getQuantidadeOperacoes() {
        return quantidadeOperacoes;
    }

    /**
     * Atualiza a quantidade de lançamentos vinculados.
     *
     * @param quantidadeOperacoes total de lançamentos
     */
    public void setQuantidadeOperacoes(int quantidadeOperacoes) {
        this.quantidadeOperacoes = Math.max(0, quantidadeOperacoes);
    }

    /**
     * Retorna a convenção de contagem de dias da aplicação.
     *
     * @return convenção de contagem
     */
    public ConvencaoContagemDias getConvencaoContagemDias() {
        return convencaoContagemDias;
    }

    /**
     * Define a convenção de contagem de dias da aplicação.
     *
     * @param convencaoContagemDias convenção desejada
     */
    public void setConvencaoContagemDias(ConvencaoContagemDias convencaoContagemDias) {
        this.convencaoContagemDias = (convencaoContagemDias == null)
                ? ConvencaoContagemDias.COMERCIAL_30_360
                : convencaoContagemDias;
    }

    /**
     * Retorna o regime tributário geral da aplicação.
     *
     * @return constante {@link RegimeTributario}
     */
    public RegimeTributario getRegimeTributario() {
        return regimeTributario;
    }

    /**
     * Define o regime tributário geral da aplicação e sincroniza as opções fiscais.
     *
     * @param regimeTributario novo regime
     */
    public void setRegimeTributario(RegimeTributario regimeTributario) {
        this.regimeTributario = (regimeTributario == null) ? RegimeTributario.PADRAO : regimeTributario;
        if (this.regimeTributario.ehIsento()) {
            this.iof = 0;
            this.tipoImpostoRenda = 0;
        }
        recalcularPosicaoInterno();
    }

    /**
     * Verifica se as condições de carência e periodicidade de resgate estão satisfeitas na data corrente.
     *
     * @return {@code true} se o resgate for permitido; {@code false} caso contrário
     */
    public boolean podeResgatar() {
        return podeResgatar(LocalDate.now());
    }

    /**
     * Verifica se as condições de carência e periodicidade de resgate estão satisfeitas na data informada.
     *
     * @param data data de simulação do resgate
     * @return {@code true} se permitido; {@code false} se impedido por carência ou intervalo
     */
    public boolean podeResgatar(LocalDate data) {
        if (this.diasCorridos < this.tempoMinimoPermanencia) {
            return false;
        }
        return switch (this.tipoResgate) {
            case 0 -> true; // Diário
            case 1 -> (this.diasCorridos % 30 == 0); // Mensal
            case 2 -> (this.diasCorridos % 90 == 0); // Trimestral
            case 3 -> (this.diasCorridos % 180 == 0); // Semestral
            case 4 -> (this.diasCorridos % 365 == 0); // Anual
            default -> true;
        };
    }

    /**
     * Efetua um resgate financeiro parcial ou total da aplicação, deduzindo do saldo base e acumulando no valor de resgate.
     *
     * <p>Consolida preventivamente rendimentos pendentes no saldo base antes da dedução, garantindo
     * que o saldo base nunca assuma valor negativo enquanto o patrimônio for positivo.</p>
     *
     * @param valor montante a ser resgatado (> 0)
     * @throws IllegalArgumentException se valor for nulo, menor ou igual a zero, ou superior ao saldo disponível
     */
    public void resgatar(BigDecimal valor) {
        if (valor == null || valor.signum() <= 0) {
            throw new IllegalArgumentException("Valor de resgate deve ser estritamente positivo.");
        }
        BigDecimal resgateArredondado = Moeda.arredondar(valor);
        if (resgateArredondado.compareTo(this.valorAtualLiquido) > 0) {
            throw new IllegalArgumentException("Saldo líquido insuficiente para o resgate: "
                    + resgateArredondado + " > " + this.valorAtualLiquido);
        }

        // Consolida rendimentos pendentes no saldo base antes da dedução para que
        // this.saldo reflita todo o patrimônio acumulado e nunca fique negativo.
        if (this.rendimentosPendentes.signum() > 0) {
            this.saldo = this.saldo.add(this.rendimentosPendentes);
            this.rendimentosCreditados = this.rendimentosCreditados.add(this.rendimentosPendentes);
            this.rendimentosPendentes = BigDecimal.ZERO;
        }

        // Se o resgate for total (resgateArredondado igual ao saldo líquido), zera o saldo base
        // para evitar que eventual resíduo de retenção tributária continue rendendo juros.
        if (resgateArredondado.compareTo(this.valorAtualLiquido) == 0) {
            this.saldo = BigDecimal.ZERO;
        } else {
            this.saldo = this.saldo.subtract(resgateArredondado);
        }

        this.valorResgate = this.valorResgate.add(resgateArredondado);
        recalcularPosicaoInterno();
    }

    // =========================================================================
    // Métodos de Compatibilidade com Código Legado e Testes de Regressão
    // =========================================================================

    /**
     * Alias de compatibilidade com o legado para saldo bruto.
     *
     * @return valor atual bruto
     */
    public BigDecimal getBalance() {
        return getValorAtualBruto();
    }

    /**
     * Alias de compatibilidade com o legado para saldo líquido.
     *
     * @return valor atual líquido
     */
    public BigDecimal getNetBalance() {
        return getValorAtualLiquido();
    }

    /**
     * Alias de compatibilidade com o legado para rendimento bruto.
     *
     * @return rendimento atual bruto
     */
    public BigDecimal getGrossEarnings() {
        return getRendimentoAtualBruto();
    }

    /**
     * Alias de compatibilidade com o legado para rendimento líquido.
     *
     * @return rendimento atual líquido
     */
    public BigDecimal getNetEarnings() {
        return getRendimentoAtualLiquido();
    }

    /**
     * Alias de compatibilidade com o legado para dias corridos.
     *
     * @return dias corridos
     */
    public int getElapsedDays() {
        return getDiasCorridos();
    }

    /**
     * Alias de compatibilidade com o legado para dias efetivos de aplicação.
     *
     * @return dias efetivos de cálculo
     */
    public int getApplicationDays() {
        return getDiasEfetivosCalculo();
    }

    /**
     * Alias de compatibilidade com o legado para impostos devidos.
     *
     * @return total de impostos devidos
     */
    public BigDecimal getTaxes() {
        return getImpostoAtualDevido();
    }

    /**
     * Alias de compatibilidade com o legado para atualização patrimonial por data.
     *
     * @param data data de referência
     */
    public void update(LocalDate data) {
        atualizarSaldo(data);
    }

    @Override
    public String toString() {
        return "AplicacaoFinanceira{"
                + "numero='" + numeroAplicacao + '\''
                + ", nome='" + nomeAplicacao + '\''
                + ", valorAplicado=" + valorAplicado
                + ", valorAtualBruto=" + valorAtualBruto
                + ", valorAtualLiquido=" + valorAtualLiquido
                + ", taxaMensal=" + taxaMensal
                + ", taxaDiaria=" + taxaDiaria
                + ", diasCorridos=" + diasCorridos
                + ", impostoDevido=" + impostoAtualDevido
                + '}';
    }
}

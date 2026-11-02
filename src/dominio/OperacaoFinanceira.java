package dominio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Representa um lançamento individual de fluxo financeiro (depósito ou retirada).
 *
 * <p>Esta classe gerencia a data da transação (dia, mês, ano e {@link LocalDate}),
 * o montante monetário estritamente positivo arredondado em centavos (2 casas decimais),
 * a descrição textual da operação e a sua natureza ({@link TipoOperacao}).</p>
 *
 * <p>A classe é passível de herança (não-final), adota validação atômica em alterações
 * de cadastro e construtores seguros que não disparam chamadas a métodos sobrescrevíveis.</p>
 */
public class OperacaoFinanceira {

    private int dia;
    private int mes;
    private int ano;
    private LocalDate data;
    private BigDecimal valor;
    private String descricao;
    private TipoOperacao tipo;

    /**
     * Construtor protegido padrão para permitir herança segura por subclasses.
     */
    protected OperacaoFinanceira() {
    }

    /**
     * Construtor principal a partir dos componentes da data civil.
     *
     * @param dia o dia do mês (1 a 31)
     * @param mes o mês do ano (1 a 12)
     * @param ano o ano civil gregoriano (ex.: 2026)
     * @param valor o montante financeiro da operação, estritamente positivo (> 0)
     * @param descricao texto explicativo não nulo e não em branco
     * @param tipo o tipo de movimentação ({@link TipoOperacao#DEPOSITO} ou {@link TipoOperacao#RETIRADA})
     * @throws IllegalArgumentException se a data for inválida, valor menor ou igual a zero,
     *                                  ou descrição for nula ou vazia
     */
    public OperacaoFinanceira(int dia, int mes, int ano, BigDecimal valor,
                              String descricao, TipoOperacao tipo) {
        LocalDate dataValidada = validarData(dia, mes, ano);
        BigDecimal valorValidado = validarValor(valor);
        String descricaoValidada = validarDescricao(descricao);
        TipoOperacao tipoValidado = validarTipo(tipo);

        aplicarCampos(dataValidada, valorValidado, descricaoValidada, tipoValidado);
    }

    /**
     * Construtor sobrecarregado a partir de uma data civil pronta.
     *
     * @param data a data civil do lançamento financeiro (não nula)
     * @param valor o montante financeiro da operação, estritamente positivo (> 0)
     * @param descricao texto explicativo não nulo e não em branco
     * @param tipo o tipo de movimentação (depósito ou retirada)
     * @throws IllegalArgumentException se valor menor ou igual a zero ou descrição nula/vazia
     * @throws NullPointerException se data for nula
     */
    public OperacaoFinanceira(LocalDate data, BigDecimal valor,
                              String descricao, TipoOperacao tipo) {
        LocalDate dataValidada = Objects.requireNonNull(data, "Data da operação é obrigatória.");
        BigDecimal valorValidado = validarValor(valor);
        String descricaoValidada = validarDescricao(descricao);
        TipoOperacao tipoValidado = validarTipo(tipo);

        aplicarCampos(dataValidada, valorValidado, descricaoValidada, tipoValidado);
    }

    /**
     * Atualiza atomicamente os dados do lançamento a partir dos inteiros de data.
     *
     * @param dia o dia do mês (1 a 31)
     * @param mes o mês do ano (1 a 12)
     * @param ano o ano civil gregoriano (ex.: 2026)
     * @param valor o novo valor monetário (> 0)
     * @param descricao a nova descrição textual
     * @param tipo o novo tipo de operação
     * @throws IllegalArgumentException se qualquer argumento for inválido
     */
    public void setCadastro(int dia, int mes, int ano, BigDecimal valor,
                            String descricao, TipoOperacao tipo) {
        LocalDate novaData = validarData(dia, mes, ano);
        BigDecimal novoValor = validarValor(valor);
        String novaDescricao = validarDescricao(descricao);
        TipoOperacao novoTipo = validarTipo(tipo);

        aplicarCampos(novaData, novoValor, novaDescricao, novoTipo);
    }

    /**
     * Atualiza atomicamente os dados do lançamento a partir de um {@link LocalDate}.
     *
     * @param data a nova data civil
     * @param valor o novo valor monetário (> 0)
     * @param descricao a nova descrição textual
     * @param tipo o novo tipo de operação
     * @throws IllegalArgumentException se qualquer argumento for inválido
     * @throws NullPointerException se data for nula
     */
    public void setCadastro(LocalDate data, BigDecimal valor,
                            String descricao, TipoOperacao tipo) {
        LocalDate novaData = Objects.requireNonNull(data, "Data da operação é obrigatória.");
        BigDecimal novoValor = validarValor(valor);
        String novaDescricao = validarDescricao(descricao);
        TipoOperacao novoTipo = validarTipo(tipo);

        aplicarCampos(novaData, novoValor, novaDescricao, novoTipo);
    }

    /* =========================================================================
     * Validações Defensivas e Atribuição Segura
     * ========================================================================= */

    private void aplicarCampos(LocalDate novaData, BigDecimal novoValor,
                               String novaDescricao, TipoOperacao novoTipo) {
        this.data = novaData;
        this.dia = novaData.getDayOfMonth();
        this.mes = novaData.getMonthValue();
        this.ano = novaData.getYear();
        this.valor = novoValor;
        this.descricao = novaDescricao;
        this.tipo = novoTipo;
    }

    private static LocalDate validarData(int dia, int mes, int ano) {
        try {
            return LocalDate.of(ano, mes, dia);
        } catch (DateTimeException ex) {
            throw new IllegalArgumentException(
                    "Data civil inválida: " + dia + "/" + mes + "/" + ano + ".", ex);
        }
    }

    private static BigDecimal validarValor(BigDecimal valor) {
        if (valor == null) {
            throw new IllegalArgumentException("Valor monetário é obrigatório.");
        }
        BigDecimal arredondado = valor.setScale(2, RoundingMode.HALF_UP);
        if (arredondado.signum() <= 0) {
            throw new IllegalArgumentException("O valor da operação deve ser positivo (maior que zero).");
        }
        return arredondado;
    }

    private static String validarDescricao(String descricao) {
        if (descricao == null) {
            throw new IllegalArgumentException("Descrição da operação não pode ser nula.");
        }
        String limpa = descricao.trim();
        if (limpa.isEmpty()) {
            throw new IllegalArgumentException("Descrição da operação não pode ser vazia.");
        }
        return limpa;
    }

    private static TipoOperacao validarTipo(TipoOperacao tipo) {
        if (tipo == null) {
            throw new IllegalArgumentException("Tipo da operação é obrigatório.");
        }
        return tipo;
    }

    /* =========================================================================
     * Getters e Setters em Português do Brasil
     * ========================================================================= */

    /**
     * Retorna o dia do mês da transação.
     *
     * @return dia civil (1 a 31)
     */
    public int getDia() {
        return dia;
    }

    /**
     * Atualiza o dia da transação, recalculando a data associada.
     *
     * @param dia novo dia do mês
     * @throws IllegalArgumentException se a combinação resultar em data civil inválida
     */
    public void setDia(int dia) {
        LocalDate novaData = validarData(dia, this.mes, this.ano);
        this.dia = dia;
        this.data = novaData;
    }

    /**
     * Retorna o mês da transação.
     *
     * @return mês civil (1 a 12)
     */
    public int getMes() {
        return mes;
    }

    /**
     * Atualiza o mês da transação, recalculando a data associada.
     *
     * @param mes novo mês civil
     * @throws IllegalArgumentException se a combinação resultar em data civil inválida
     */
    public void setMes(int mes) {
        LocalDate novaData = validarData(this.dia, mes, this.ano);
        this.mes = mes;
        this.data = novaData;
    }

    /**
     * Retorna o ano civil da transação.
     *
     * @return ano (ex.: 2026)
     */
    public int getAno() {
        return ano;
    }

    /**
     * Atualiza o ano civil da transação, recalculando a data associada.
     *
     * @param ano novo ano civil
     * @throws IllegalArgumentException se a combinação resultar em data civil inválida
     */
    public void setAno(int ano) {
        LocalDate novaData = validarData(this.dia, this.mes, ano);
        this.ano = ano;
        this.data = novaData;
    }

    /**
     * Retorna a data civil da operação financeira.
     *
     * @return instância de {@link LocalDate}
     */
    public LocalDate getData() {
        return data;
    }

    /**
     * Atualiza a data civil da operação e seus componentes (dia, mês e ano).
     *
     * @param data nova data civil
     * @throws NullPointerException se data for nula
     */
    public void setData(LocalDate data) {
        LocalDate novaData = Objects.requireNonNull(data, "Data da operação é obrigatória.");
        this.data = novaData;
        this.dia = novaData.getDayOfMonth();
        this.mes = novaData.getMonthValue();
        this.ano = novaData.getYear();
    }

    /**
     * Retorna o montante financeiro da operação.
     *
     * @return valor com escala de 2 casas decimais
     */
    public BigDecimal getValor() {
        return valor;
    }

    /**
     * Atualiza o montante financeiro da operação, validando positividade e escala.
     *
     * @param valor novo valor positivo
     * @throws IllegalArgumentException se valor for nulo ou menor ou igual a zero
     */
    public void setValor(BigDecimal valor) {
        this.valor = validarValor(valor);
    }

    /**
     * Retorna o texto descritivo da operação.
     *
     * @return descrição textual
     */
    public String getDescricao() {
        return descricao;
    }

    /**
     * Atualiza a descrição textual da operação.
     *
     * @param descricao nova descrição não vazia
     * @throws IllegalArgumentException se descrição for nula ou em branco
     */
    public void setDescricao(String descricao) {
        this.descricao = validarDescricao(descricao);
    }

    /**
     * Retorna o tipo da operação financeira.
     *
     * @return constante {@link TipoOperacao}
     */
    public TipoOperacao getTipo() {
        return tipo;
    }

    /**
     * Atualiza o tipo da operação financeira.
     *
     * @param tipo novo tipo de operação
     * @throws IllegalArgumentException se tipo for nulo
     */
    public void setTipo(TipoOperacao tipo) {
        this.tipo = validarTipo(tipo);
    }

    /* =========================================================================
     * Métodos Convenientes e de Compatibilidade
     * ========================================================================= */

    /**
     * Acesso rápido à data da operação.
     *
     * @return a data civil
     */
    public LocalDate data() {
        return data;
    }

    /**
     * Acesso rápido ao valor monetário da operação.
     *
     * @return o valor monetário
     */
    public BigDecimal valor() {
        return valor;
    }

    /**
     * Acesso rápido à descrição da operação.
     *
     * @return a descrição
     */
    public String descricao() {
        return descricao;
    }

    /**
     * Acesso rápido ao tipo da operação.
     *
     * @return o tipo de operação
     */
    public TipoOperacao tipo() {
        return tipo;
    }

    /**
     * Informa se este lançamento é um depósito.
     *
     * @return {@code true} se for depósito; {@code false} se for retirada
     */
    public boolean ehDeposito() {
        return tipo != null && tipo.ehDeposito();
    }

    /**
     * Informa se este lançamento é uma retirada.
     *
     * @return {@code true} se for retirada; {@code false} se for depósito
     */
    public boolean ehRetirada() {
        return tipo != null && tipo.ehRetirada();
    }

    // Aliases legados para compatibilidade com suítes de teste de regressão
    /**
     * Alias legado para data.
     *
     * @return data civil
     */
    public LocalDate date() {
        return data;
    }

    /**
     * Alias legado para valor.
     *
     * @return valor monetário
     */
    public BigDecimal amount() {
        return valor;
    }

    /**
     * Alias legado para descrição.
     *
     * @return descrição
     */
    public String description() {
        return descricao;
    }

    /**
     * Alias legado para tipo.
     *
     * @return tipo de operação
     */
    public TipoOperacao type() {
        return tipo;
    }

    /**
     * Alias JavaBean legado para valor.
     *
     * @return valor monetário
     */
    public BigDecimal getAmount() {
        return valor;
    }

    /**
     * Alias JavaBean legado para data.
     *
     * @return data civil
     */
    public LocalDate getDate() {
        return data;
    }

    /**
     * Alias JavaBean legado para descrição.
     *
     * @return descrição
     */
    public String getDescription() {
        return descricao;
    }

    /**
     * Alias JavaBean legado para tipo.
     *
     * @return tipo de operação
     */
    public TipoOperacao getType() {
        return tipo;
    }

    @Override
    public String toString() {
        return "OperacaoFinanceira{"
                + "data=" + data
                + ", valor=" + valor
                + ", descricao='" + descricao + '\''
                + ", tipo=" + tipo
                + '}';
    }
}

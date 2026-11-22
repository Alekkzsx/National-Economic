package dominio;

import dominio.util.ValidacaoDominio;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Item 2 — Representa um lançamento individual de fluxo financeiro (depósito ou retirada).
 *
 * <p>Classe passível de herança, com validação atômica e construtores sem despacho virtual.</p>
 */
public class OperacaoFinanceira {

    private int dia;
    private int mes;
    private int ano;
    private LocalDate data;
    private BigDecimal valor;
    private String descricao;
    private TipoOperacao tipo;

    /** Construtor protegido para herança segura. */
    protected OperacaoFinanceira() {
    }

    /** Cria um lançamento validado a partir de dia, mês e ano. */
    public OperacaoFinanceira(int dia, int mes, int ano, BigDecimal valor,
                              String descricao, TipoOperacao tipo) {
        LocalDate dataValidada = ValidacaoDominio.validarData(dia, mes, ano);
        aplicarCampos(dataValidada, validarValor(valor), validarDescricao(descricao), validarTipo(tipo));
    }

    /** Cria um lançamento validado a partir de um {@link LocalDate}. */
    public OperacaoFinanceira(LocalDate data, BigDecimal valor,
                              String descricao, TipoOperacao tipo) {
        LocalDate dataValidada = Objects.requireNonNull(data, "Data da operação é obrigatória.");
        aplicarCampos(dataValidada, validarValor(valor), validarDescricao(descricao), validarTipo(tipo));
    }

    /** Atualiza atomicamente todos os campos a partir de dia, mês e ano. */
    public void setCadastro(int dia, int mes, int ano, BigDecimal valor,
                            String descricao, TipoOperacao tipo) {
        LocalDate novaData = ValidacaoDominio.validarData(dia, mes, ano);
        aplicarCampos(novaData, validarValor(valor), validarDescricao(descricao), validarTipo(tipo));
    }

    /** Atualiza atomicamente todos os campos a partir de um {@link LocalDate}. */
    public void setCadastro(LocalDate data, BigDecimal valor,
                            String descricao, TipoOperacao tipo) {
        LocalDate novaData = Objects.requireNonNull(data, "Data da operação é obrigatória.");
        aplicarCampos(novaData, validarValor(valor), validarDescricao(descricao), validarTipo(tipo));
    }

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

    private static BigDecimal validarValor(BigDecimal valor) {
        if (valor == null) {
            throw new IllegalArgumentException("Valor monetário é obrigatório.");
        }
        return Moeda.positivo(valor, "O valor da operação");
    }

    private static String validarDescricao(String descricao) {
        return ValidacaoDominio.textoObrigatorio(descricao, "Descrição da operação não pode ser nula ou vazia.");
    }

    private static TipoOperacao validarTipo(TipoOperacao tipo) {
        if (tipo == null) {
            throw new IllegalArgumentException("Tipo da operação é obrigatório.");
        }
        return tipo;
    }

    // -------------------------------------------------------------------------
    // Getters e Setters
    // -------------------------------------------------------------------------

    public int getDia() { return dia; }

    public void setDia(int dia) {
        this.data = ValidacaoDominio.validarData(dia, this.mes, this.ano);
        this.dia = dia;
    }

    public int getMes() { return mes; }

    public void setMes(int mes) {
        this.data = ValidacaoDominio.validarData(this.dia, mes, this.ano);
        this.mes = mes;
    }

    public int getAno() { return ano; }

    public void setAno(int ano) {
        this.data = ValidacaoDominio.validarData(this.dia, this.mes, ano);
        this.ano = ano;
    }

    public LocalDate getData() { return data; }

    public void setData(LocalDate data) {
        LocalDate novaData = Objects.requireNonNull(data, "Data da operação é obrigatória.");
        this.data = novaData;
        this.dia = novaData.getDayOfMonth();
        this.mes = novaData.getMonthValue();
        this.ano = novaData.getYear();
    }

    public BigDecimal getValor() { return valor; }
    public void setValor(BigDecimal valor) { this.valor = validarValor(valor); }

    public String getDescricao() { return descricao; }
    public void setDescricao(String descricao) { this.descricao = validarDescricao(descricao); }

    public TipoOperacao getTipo() { return tipo; }
    public void setTipo(TipoOperacao tipo) { this.tipo = validarTipo(tipo); }

    public boolean ehDeposito() { return tipo != null && tipo.ehDeposito(); }
    public boolean ehRetirada() { return tipo != null && tipo.ehRetirada(); }

    // -------------------------------------------------------------------------
    // Aliases mantidos para compatibilidade de testes
    // -------------------------------------------------------------------------

    public LocalDate date() { return data; }
    public BigDecimal amount() { return valor; }
    public String description() { return descricao; }
    public TipoOperacao type() { return tipo; }

    @Override
    public String toString() {
        return "OperacaoFinanceira{data=" + data
                + ", valor=" + valor
                + ", descricao='" + descricao + '\''
                + ", tipo=" + tipo + '}';
    }
}

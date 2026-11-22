package dominio;

import dominio.abstracoes.RegistroDatado;
import dominio.util.ValidacaoDominio;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Item 2 — Representa um lançamento individual de fluxo financeiro (depósito ou retirada).
 *
 * <p>Herda a gestão sincronizada de data civil de {@link RegistroDatado}, mantendo
 * validação atômica e construtores seguros sem despacho virtual.</p>
 */
public class OperacaoFinanceira extends RegistroDatado {

    private BigDecimal valor;
    private String descricao;
    private TipoOperacao tipo;

    /** Construtor protegido para herança segura. */
    protected OperacaoFinanceira() {
        super();
    }

    /** Cria um lançamento validado a partir de dia, mês e ano. */
    public OperacaoFinanceira(int dia, int mes, int ano, BigDecimal valor,
                              String descricao, TipoOperacao tipo) {
        super(dia, mes, ano);
        this.valor = validarValor(valor);
        this.descricao = validarDescricao(descricao);
        this.tipo = validarTipo(tipo);
    }

    /** Cria um lançamento validado a partir de um {@link LocalDate}. */
    public OperacaoFinanceira(LocalDate data, BigDecimal valor,
                              String descricao, TipoOperacao tipo) {
        super(data, "Data da operação é obrigatória.");
        this.valor = validarValor(valor);
        this.descricao = validarDescricao(descricao);
        this.tipo = validarTipo(tipo);
    }

    /** Atualiza atomicamente todos os campos a partir de dia, mês e ano. */
    public void setCadastro(int dia, int mes, int ano, BigDecimal valor,
                            String descricao, TipoOperacao tipo) {
        LocalDate novaData = ValidacaoDominio.validarData(dia, mes, ano);
        aplicarCamposAtomico(novaData, validarValor(valor), validarDescricao(descricao), validarTipo(tipo));
    }

    /** Atualiza atomicamente todos os campos a partir de um {@link LocalDate}. */
    public void setCadastro(LocalDate data, BigDecimal valor,
                            String descricao, TipoOperacao tipo) {
        LocalDate novaData = Objects.requireNonNull(data, "Data da operação é obrigatória.");
        aplicarCamposAtomico(novaData, validarValor(valor), validarDescricao(descricao), validarTipo(tipo));
    }

    private void aplicarCamposAtomico(LocalDate novaData, BigDecimal novoValor,
                                      String novaDescricao, TipoOperacao novoTipo) {
        aplicarDataInterno(novaData);
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

    public BigDecimal getValor() { return valor; }
    public void setValor(BigDecimal valor) { this.valor = validarValor(valor); }

    public String getDescricao() { return descricao; }
    public void setDescricao(String descricao) { this.descricao = validarDescricao(descricao); }

    public TipoOperacao getTipo() { return tipo; }
    public void setTipo(TipoOperacao tipo) { this.tipo = validarTipo(tipo); }

    public boolean ehDeposito() { return tipo != null && tipo.ehDeposito(); }
    public boolean ehRetirada() { return tipo != null && tipo.ehRetirada(); }

    // Aliases mantidos para compatibilidade de testes
    public LocalDate date() { return getData(); }
    public BigDecimal amount() { return valor; }
    public String description() { return descricao; }
    public TipoOperacao type() { return tipo; }

    @Override
    public String toString() {
        return "OperacaoFinanceira{data=" + getData()
                + ", valor=" + valor
                + ", descricao='" + descricao + '\''
                + ", tipo=" + tipo + '}';
    }
}

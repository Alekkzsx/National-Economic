package modelo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Representa um lançamento de depósito ou retirada.
 *
 * <p>A classe é mutável para permitir os métodos de cadastro e alteração do
 * exercício. Cada valor é validado antes de ser armazenado; quantias são
 * arredondadas para duas casas decimais.</p>
 */
public class OperacaoFinanceira {
    /** Data em que o lançamento financeiro foi realizado. */
    private LocalDate data;
    /** Valor positivo do lançamento, sempre mantido com escala de centavos. */
    private BigDecimal valor;
    /** Texto que explica a origem ou finalidade do lançamento. */
    private String descricao;
    /** Indica se o valor entra ou sai do saldo. */
    private TipoOperacao tipo;

    /** Cria a operação usando as mesmas validações do cadastro. */
    public OperacaoFinanceira(LocalDate data, BigDecimal valor, String descricao, TipoOperacao tipo) {
        this.data = Objects.requireNonNull(data, "Data obrigatória.");
        this.tipo = Objects.requireNonNull(tipo, "Tipo obrigatório.");
        this.valor = validarValor(valor);
        this.descricao = validarDescricao(descricao);
    }

    // Os accessors sem prefixo preservam o formato de acesso usado pelo extrato.
    /** Retorna a data do lançamento. */
    public LocalDate data() { return data; }
    /** Retorna o valor em centavos. */
    public BigDecimal valor() { return valor; }
    /** Retorna a descrição registrada. */
    public String descricao() { return descricao; }
    /** Retorna o tipo de operação. */
    public TipoOperacao tipo() { return tipo; }
    /** Getter JavaBeans para a data do lançamento. */
    public LocalDate getData() { return data; }
    /** Getter JavaBeans para o valor da operação. */
    public BigDecimal getValor() { return valor; }
    /** Getter JavaBeans para a descrição. */
    public String getDescricao() { return descricao; }
    /** Getter JavaBeans para o tipo da operação. */
    public TipoOperacao getTipo() { return tipo; }

    /** Substitui a data do lançamento, que não pode ser nula. */
    public void setData(LocalDate data) {
        this.data = Objects.requireNonNull(data, "Data obrigatória.");
    }

    /** Define um valor positivo e arredonda para centavos. */
    public void setValor(BigDecimal valor) {
        this.valor = Moeda.positivo(valor, "O valor da operação");
    }

    /** Define uma descrição não vazia e remove espaços externos. */
    public void setDescricao(String descricao) {
        if (descricao == null || descricao.isBlank()) {
            throw new IllegalArgumentException("Descrição obrigatória.");
        }
        this.descricao = descricao.trim();
    }

    /** Define se o lançamento será depósito ou retirada. */
    public void setTipo(TipoOperacao tipo) {
        this.tipo = Objects.requireNonNull(tipo, "Tipo obrigatório.");
    }

    /**
     * Atualiza todos os dados como uma única operação: só altera o objeto
     * depois que todos os campos passaram pela validação.
     */
    public void setCadastro(LocalDate data, BigDecimal valor, String descricao, TipoOperacao tipo) {
        Objects.requireNonNull(data, "Data obrigatória.");
        Objects.requireNonNull(tipo, "Tipo obrigatório.");
        BigDecimal rounded = validarValor(valor);
        String validDescription = validarDescricao(descricao);
        this.data = data;
        this.valor = rounded;
        this.descricao = validDescription;
        this.tipo = tipo;
    }

    /** Sobrecarga de cadastro para datas informadas como dia, mês e ano. */
    public void setCadastro(int day, int month, int year, BigDecimal valor,
                            String descricao, TipoOperacao tipo) {
        setCadastro(LocalDate.of(year, month, day), valor, descricao, tipo);
    }

    /** Centraliza a validação monetária usada pelo cadastro completo. */
    private static BigDecimal validarValor(BigDecimal valor) {
        return Moeda.positivo(valor, "O valor da operação");
    }

    /** Centraliza a validação e normalização da descrição. */
    private static String validarDescricao(String descricao) {
        if (descricao == null || descricao.isBlank()) {
            throw new IllegalArgumentException("Descrição obrigatória.");
        }
        return descricao.trim();
    }

    /** Retorna uma representação legível dos dados do lançamento. */
    @Override
    public String toString() {
        return "OperacaoFinanceira{data=" + data + ", valor=" + valor + ", descricao='"
                + descricao + "', tipo=" + tipo + "}";
    }
}

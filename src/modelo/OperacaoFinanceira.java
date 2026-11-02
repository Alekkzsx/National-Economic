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
    private LocalDate date;
    /** Valor positivo do lançamento, sempre mantido com escala de centavos. */
    private BigDecimal amount;
    /** Texto que explica a origem ou finalidade do lançamento. */
    private String description;
    /** Indica se o valor entra ou sai do saldo. */
    private TipoOperacao type;

    /** Cria a operação usando as mesmas validações do cadastro. */
    public OperacaoFinanceira(LocalDate date, BigDecimal amount, String description, TipoOperacao type) {
        this.date = Objects.requireNonNull(date, "Data obrigatória.");
        this.type = Objects.requireNonNull(type, "Tipo obrigatório.");
        this.amount = validarValor(amount);
        this.description = validarDescricao(description);
    }

    // Os accessors sem prefixo preservam o formato de acesso usado pelo extrato.
    /** Retorna a data do lançamento. */
    public LocalDate data() { return date; }
    /** Retorna o valor em centavos. */
    public BigDecimal valor() { return amount; }
    /** Retorna a descrição registrada. */
    public String descricao() { return description; }
    /** Retorna o tipo de operação. */
    public TipoOperacao tipo() { return type; }
    /** Getter JavaBeans para a data do lançamento. */
    public LocalDate getData() { return date; }
    /** Getter JavaBeans para o valor da operação. */
    public BigDecimal getValor() { return amount; }
    /** Getter JavaBeans para a descrição. */
    public String getDescricao() { return description; }
    /** Getter JavaBeans para o tipo da operação. */
    public TipoOperacao getTipo() { return type; }

    /** Substitui a data do lançamento, que não pode ser nula. */
    public void setData(LocalDate date) {
        this.date = Objects.requireNonNull(date, "Data obrigatória.");
    }

    /** Define um valor positivo e arredonda para centavos. */
    public void setValor(BigDecimal amount) {
        this.amount = Moeda.positive(amount, "O valor da operação");
    }

    /** Define uma descrição não vazia e remove espaços externos. */
    public void setDescricao(String description) {
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("Descrição obrigatória.");
        }
        this.description = description.trim();
    }

    /** Define se o lançamento será depósito ou retirada. */
    public void setTipo(TipoOperacao type) {
        this.type = Objects.requireNonNull(type, "Tipo obrigatório.");
    }

    /**
     * Atualiza todos os dados como uma única operação: só altera o objeto
     * depois que todos os campos passaram pela validação.
     */
    public void setCadastro(LocalDate date, BigDecimal amount, String description, TipoOperacao type) {
        Objects.requireNonNull(date, "Data obrigatória.");
        Objects.requireNonNull(type, "Tipo obrigatório.");
        BigDecimal rounded = validarValor(amount);
        String validDescription = validarDescricao(description);
        this.date = date;
        this.amount = rounded;
        this.description = validDescription;
        this.type = type;
    }

    /** Sobrecarga de cadastro para datas informadas como dia, mês e ano. */
    public void setCadastro(int day, int month, int year, BigDecimal amount,
                            String description, TipoOperacao type) {
        setCadastro(LocalDate.of(year, month, day), amount, description, type);
    }

    /** Centraliza a validação monetária usada pelo cadastro completo. */
    private static BigDecimal validarValor(BigDecimal amount) {
        return Moeda.positive(amount, "O valor da operação");
    }

    /** Centraliza a validação e normalização da descrição. */
    private static String validarDescricao(String description) {
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("Descrição obrigatória.");
        }
        return description.trim();
    }

    /** Retorna uma representação legível dos dados do lançamento. */
    @Override
    public String toString() {
        return "OperacaoFinanceira{date=" + date + ", amount=" + amount + ", description='"
                + description + "', type=" + type + "}";
    }
}

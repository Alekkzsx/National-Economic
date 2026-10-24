package domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Representa um lançamento de depósito ou retirada.
 *
 * <p>A classe é mutável para permitir os métodos de cadastro e alteração do
 * exercício. Cada valor é validado antes de ser armazenado; quantias são
 * arredondadas para duas casas decimais.</p>
 */
public class FinancialOperation {
    /** Data em que o lançamento financeiro foi realizado. */
    private LocalDate date;
    /** Valor positivo do lançamento, sempre mantido com escala de centavos. */
    private BigDecimal amount;
    /** Texto que explica a origem ou finalidade do lançamento. */
    private String description;
    /** Indica se o valor entra ou sai do saldo. */
    private OperationType type;

    /** Cria a operação usando as mesmas validações do cadastro. */
    public FinancialOperation(LocalDate date, BigDecimal amount, String description, OperationType type) {
        setCadastro(date, amount, description, type);
    }

    // Os accessors sem prefixo preservam o formato de acesso usado pelo extrato.
    /** Retorna a data do lançamento. */
    public LocalDate date() { return date; }
    /** Retorna o valor em centavos. */
    public BigDecimal amount() { return amount; }
    /** Retorna a descrição registrada. */
    public String description() { return description; }
    /** Retorna o tipo de operação. */
    public OperationType type() { return type; }
    /** Getter JavaBeans para a data do lançamento. */
    public LocalDate getDate() { return date; }
    /** Getter JavaBeans para o valor da operação. */
    public BigDecimal getAmount() { return amount; }
    /** Getter JavaBeans para a descrição. */
    public String getDescription() { return description; }
    /** Getter JavaBeans para o tipo da operação. */
    public OperationType getType() { return type; }

    /** Substitui a data do lançamento, que não pode ser nula. */
    public void setDate(LocalDate date) {
        this.date = Objects.requireNonNull(date, "Data obrigatória.");
    }

    /** Define um valor positivo e arredonda para centavos. */
    public void setAmount(BigDecimal amount) {
        Objects.requireNonNull(amount, "Valor obrigatório.");
        BigDecimal rounded = amount.setScale(2, RoundingMode.HALF_UP);
        if (amount.signum() <= 0 || rounded.signum() <= 0) {
            throw new IllegalArgumentException("O valor da operação deve ser positivo.");
        }
        this.amount = rounded;
    }

    /** Define uma descrição não vazia e remove espaços externos. */
    public void setDescription(String description) {
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("Descrição obrigatória.");
        }
        this.description = description.trim();
    }

    /** Define se o lançamento será depósito ou retirada. */
    public void setType(OperationType type) {
        this.type = Objects.requireNonNull(type, "Tipo obrigatório.");
    }

    /**
     * Atualiza todos os dados como uma única operação: só altera o objeto
     * depois que todos os campos passaram pela validação.
     */
    public void setCadastro(LocalDate date, BigDecimal amount, String description, OperationType type) {
        Objects.requireNonNull(date, "Data obrigatória.");
        Objects.requireNonNull(type, "Tipo obrigatório.");
        BigDecimal rounded = validateAmount(amount);
        String validDescription = validateDescription(description);
        this.date = date;
        this.amount = rounded;
        this.description = validDescription;
        this.type = type;
    }

    /** Sobrecarga de cadastro para datas informadas como dia, mês e ano. */
    public void setCadastro(int day, int month, int year, BigDecimal amount,
                            String description, OperationType type) {
        setCadastro(LocalDate.of(year, month, day), amount, description, type);
    }

    /** Centraliza a validação monetária usada pelo cadastro completo. */
    private static BigDecimal validateAmount(BigDecimal amount) {
        Objects.requireNonNull(amount, "Valor obrigatório.");
        BigDecimal rounded = amount.setScale(2, RoundingMode.HALF_UP);
        if (amount.signum() <= 0 || rounded.signum() <= 0) {
            throw new IllegalArgumentException("O valor da operação deve ser positivo.");
        }
        return rounded;
    }

    /** Centraliza a validação e normalização da descrição. */
    private static String validateDescription(String description) {
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("Descrição obrigatória.");
        }
        return description.trim();
    }

    /** Retorna uma representação legível dos dados do lançamento. */
    @Override
    public String toString() {
        return "FinancialOperation{date=" + date + ", amount=" + amount + ", description='"
                + description + "', type=" + type + "}";
    }
}

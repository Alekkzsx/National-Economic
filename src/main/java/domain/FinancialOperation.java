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
    private LocalDate date;
    private BigDecimal amount;
    private String description;
    private OperationType type;

    /** Cria a operação usando as mesmas validações do cadastro. */
    public FinancialOperation(LocalDate date, BigDecimal amount, String description, OperationType type) {
        setCadastro(date, amount, description, type);
    }

    // Accessors sem prefixo preservam a API usada pelos registros de extrato.
    public LocalDate date() { return date; }
    public BigDecimal amount() { return amount; }
    public String description() { return description; }
    public OperationType type() { return type; }
    public LocalDate getDate() { return date; }
    public BigDecimal getAmount() { return amount; }
    public String getDescription() { return description; }
    public OperationType getType() { return type; }

    public void setDate(LocalDate date) {
        this.date = Objects.requireNonNull(date, "Data obrigatória.");
    }

    public void setAmount(BigDecimal amount) {
        Objects.requireNonNull(amount, "Valor obrigatório.");
        BigDecimal rounded = amount.setScale(2, RoundingMode.HALF_UP);
        if (amount.signum() <= 0 || rounded.signum() <= 0) {
            throw new IllegalArgumentException("O valor da operação deve ser positivo.");
        }
        this.amount = rounded;
    }

    public void setDescription(String description) {
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("Descrição obrigatória.");
        }
        this.description = description.trim();
    }

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

    private static BigDecimal validateAmount(BigDecimal amount) {
        Objects.requireNonNull(amount, "Valor obrigatório.");
        BigDecimal rounded = amount.setScale(2, RoundingMode.HALF_UP);
        if (amount.signum() <= 0 || rounded.signum() <= 0) {
            throw new IllegalArgumentException("O valor da operação deve ser positivo.");
        }
        return rounded;
    }

    private static String validateDescription(String description) {
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("Descrição obrigatória.");
        }
        return description.trim();
    }

    @Override
    public String toString() {
        return "FinancialOperation{date=" + date + ", amount=" + amount + ", description='"
                + description + "', type=" + type + "}";
    }
}

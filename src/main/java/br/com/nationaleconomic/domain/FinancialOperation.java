package br.com.nationaleconomic.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

public record FinancialOperation(LocalDate date, BigDecimal amount, String description, OperationType type) {
    public FinancialOperation {
        Objects.requireNonNull(date, "Data obrigatória.");
        Objects.requireNonNull(amount, "Valor obrigatório.");
        Objects.requireNonNull(type, "Tipo obrigatório.");
        if (amount.signum() <= 0) throw new IllegalArgumentException("O valor da operação deve ser positivo.");
        amount = amount.setScale(2, java.math.RoundingMode.HALF_UP);
        if (amount.signum() <= 0) throw new IllegalArgumentException("O valor deve ser maior que zero após arredondamento.");
        if (description == null || description.isBlank()) throw new IllegalArgumentException("Descrição obrigatória.");
        description = description.trim();
    }
}

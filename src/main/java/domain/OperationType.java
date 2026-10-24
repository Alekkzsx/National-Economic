package domain;

/** Direção de uma movimentação registrada no extrato da aplicação. */
public enum OperationType {
    /** Entrada de recursos, que aumenta o saldo. */
    DEPOSIT,
    /** Saída de recursos, sujeita a saldo e condições de resgate. */
    WITHDRAWAL
}

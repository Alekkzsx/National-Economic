package modelo;

/** Direção de uma movimentação registrada no extrato da aplicação. */
public enum TipoOperacao {
    /** Entrada de recursos, que aumenta o saldo. */
    DEPOSIT,
    /** Saída de recursos, sujeita a saldo e condições de resgate. */
    WITHDRAWAL
}

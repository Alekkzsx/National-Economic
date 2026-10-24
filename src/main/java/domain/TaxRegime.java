package domain;

/** Compatibilidade com os regimes tributários gerais usados na persistência. */
public enum TaxRegime {
    /** Aplica as opções específicas de IOF e IR configuradas na aplicação. */
    STANDARD,
    /** Isenta simultaneamente a aplicação de IOF e IR. */
    TAX_FREE
}

package br.com.nationaleconomic.domain;

public enum IofOption {
    EXEMPT,
    /** Alíquota fixa de 3,5% sobre rendimentos somente nos primeiros 29 dias corridos. */
    THREE_POINT_FIVE_PERCENT_BEFORE_30_DAYS,
    /** Alíquota fixa de 3,5% sobre rendimentos durante todo o prazo. */
    THREE_POINT_FIVE_PERCENT_ALWAYS
}

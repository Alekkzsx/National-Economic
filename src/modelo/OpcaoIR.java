package modelo;

/** Políticas disponíveis para o cálculo simplificado de Imposto de Renda. */
public enum OpcaoIR {
    /** Não desconta Imposto de Renda. */
    EXEMPT,
    /** 22,5% até 180 dias; 20% até 360; 17,5% até 720; e 15% após 720 dias. */
    REGRESSIVE
}

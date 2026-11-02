package dominio;

import java.util.Objects;

/**
 * Define o regime tributário geral da aplicação financeira.
 *
 * <p>Regimes suportados:</p>
 * <ul>
 *   <li>{@link #PADRAO}: Aplica as regras normais de IOF e Imposto de Renda configuradas na aplicação.</li>
 *   <li>{@link #ISENTO}: Isenção total e simultânea de retenção de IOF e Imposto de Renda.</li>
 * </ul>
 */
public enum RegimeTributario {

    /** Regime padrão de mercado, com apuração de IOF e IR conforme prazos e regras definidas. */
    PADRAO("Padrão", "Aplica as opções tributárias de IOF e IR configuradas na aplicação financeira."),

    /** Regime isento de tributação para aplicações financeiras incentivadas. */
    ISENTO("Isento", "Isenção total e simultânea de IOF e Imposto de Renda.");

    private final String descricao;
    private final String detalhe;

    /**
     * Construtor do regime tributário.
     *
     * @param descricao nome amigável em português
     * @param detalhe regra de negócio tributária
     */
    RegimeTributario(String descricao, String detalhe) {
        this.descricao = descricao;
        this.detalhe = detalhe;
    }

    /**
     * Retorna a descrição legível do regime.
     *
     * @return nome do regime
     */
    public String getDescricao() {
        return descricao;
    }

    /**
     * Retorna o detalhamento operacional do regime.
     *
     * @return texto descritivo
     */
    public String getDetalhe() {
        return detalhe;
    }

    /**
     * Verifica se este regime confere isenção fiscal integral.
     *
     * @return {@code true} se for isento; {@code false} se for padrão
     */
    public boolean ehIsento() {
        return this == ISENTO;
    }

    /**
     * Converte uma representação textual no respectivo {@link RegimeTributario}.
     *
     * @param texto o identificador textual a ser interpretado
     * @return a constante enumerada correspondente
     * @throws NullPointerException se o texto for nulo
     * @throws IllegalArgumentException se o texto não corresponder a um regime reconhecido
     */
    public static RegimeTributario deTexto(String texto) {
        Objects.requireNonNull(texto, "Texto do regime tributário não pode ser nulo.");
        String normalizado = texto.trim().toUpperCase();
        return switch (normalizado) {
            case "PADRAO", "PADRÃO", "STANDARD" -> PADRAO;
            case "ISENTO", "TAX_FREE", "EXEMPT" -> ISENTO;
            default -> throw new IllegalArgumentException(
                    "Regime tributário não reconhecido para o texto: '" + texto + "'.");
        };
    }

    @Override
    public String toString() {
        return descricao;
    }
}

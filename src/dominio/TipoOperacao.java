package dominio;

import java.util.Objects;

/**
 * Define o tipo de movimentação em um lançamento financeiro.
 *
 * <p>Representa operações fundamentais de fluxo de caixa:</p>
 * <ul>
 *   <li>{@link #DEPOSITO}: Aporte ou entrada de recursos que incrementa o saldo da aplicação.</li>
 *   <li>{@link #RETIRADA}: Resgate ou saída de recursos da aplicação financeira.</li>
 * </ul>
 */
public enum TipoOperacao {

    /**
     * Entrada de recursos financeiros na aplicação.
     */
    DEPOSITO(1, "Depósito", "Entrada de recursos que aumenta o saldo da aplicação."),

    /**
     * Saída ou resgate de recursos financeiros da aplicação.
     */
    RETIRADA(2, "Retirada", "Saída de recursos sujeita a saldo disponível e condições de resgate.");

    private final int codigo;
    private final String descricao;
    private final String detalhe;

    /**
     * Construtor do tipo enumerado.
     *
     * @param codigo o identificador numérico da operação (1 para Depósito, 2 para Retirada)
     * @param descricao nome legível em português do Brasil
     * @param detalhe descrição detalhada da regra da operação
     */
    TipoOperacao(int codigo, String descricao, String detalhe) {
        this.codigo = codigo;
        this.descricao = descricao;
        this.detalhe = detalhe;
    }

    /**
     * Retorna o código numérico associado à operação.
     *
     * @return 1 para Depósito ou 2 para Retirada
     */
    public int getCodigo() {
        return codigo;
    }

    /**
     * Retorna a descrição amigável da operação em português.
     *
     * @return texto descritivo ("Depósito" ou "Retirada")
     */
    public String getDescricao() {
        return descricao;
    }

    /**
     * Retorna o detalhamento operacional da movimentação.
     *
     * @return texto explicativo da regra de negócio
     */
    public String getDetalhe() {
        return detalhe;
    }

    /**
     * Verifica se esta operação é um depósito financeiro.
     *
     * @return {@code true} se for depósito; {@code false} se for retirada
     */
    public boolean ehDeposito() {
        return this == DEPOSITO;
    }

    /**
     * Verifica se esta operação é uma retirada ou resgate financeiro.
     *
     * @return {@code true} se for retirada; {@code false} se for depósito
     */
    public boolean ehRetirada() {
        return this == RETIRADA;
    }

    /**
     * Obtém a instância de {@link TipoOperacao} correspondente ao código numérico informado.
     *
     * @param codigo o código numérico da operação (1 ou 2)
     * @return a constante enumerada correspondente
     * @throws IllegalArgumentException se o código não for 1 nem 2
     */
    public static TipoOperacao doCodigo(int codigo) {
        for (TipoOperacao tipo : values()) {
            if (tipo.codigo == codigo) {
                return tipo;
            }
        }
        throw new IllegalArgumentException(
                "Código de operação inválido: " + codigo + ". Use 1 para Depósito ou 2 para Retirada.");
    }

    /**
     * Converte uma representação textual no respectivo {@link TipoOperacao}, suportando termos em português e inglês.
     *
     * @param texto o identificador textual a ser convertido
     * @return a constante enumerada correspondente
     * @throws NullPointerException se o texto for nulo
     * @throws IllegalArgumentException se o texto não corresponder a uma operação reconhecida
     */
    public static TipoOperacao deTexto(String texto) {
        Objects.requireNonNull(texto, "Texto da operação não pode ser nulo.");
        String normalizado = texto.trim().toUpperCase();
        return switch (normalizado) {
            case "DEPOSITO", "DEPÓSITO", "DEPOSIT", "1" -> DEPOSITO;
            case "RETIRADA", "RESGATE", "WITHDRAWAL", "2" -> RETIRADA;
            default -> throw new IllegalArgumentException(
                    "Tipo de operação não reconhecido para o texto: '" + texto + "'.");
        };
    }

    @Override
    public String toString() {
        return descricao;
    }
}

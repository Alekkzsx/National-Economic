package dominio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Objects;

/**
 * Utilitário responsável pela padronização, arredondamento e validação de grandezas monetárias.
 *
 * <p>Centraliza a escala de centavos (2 casas decimais), suporta arredondamento comercial
 * ({@link RoundingMode#HALF_UP}) e bancário ({@link RoundingMode#HALF_EVEN}), e provê validações
 * estritas de positividade e não-negatividade para operações financeiras.</p>
 */
public final class Moeda {

    /** Escala padrão de centavos do Real (2 casas decimais). */
    public static final int CASAS_DECIMAIS = 2;

    /** Constante representativa de zero reais com a escala monetária padronizada (0.00). */
    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(CASAS_DECIMAIS, RoundingMode.HALF_UP);

    /** Modo de arredondamento comercial padrão (HALF_UP). */
    public static final RoundingMode MODO_COMERCIAL = RoundingMode.HALF_UP;

    /** Modo de arredondamento bancário (HALF_EVEN / Banker's Rounding). */
    public static final RoundingMode MODO_BANCARIO = RoundingMode.HALF_EVEN;

    /** Modo de arredondamento adotado por padrão no sistema. */
    public static final RoundingMode MODO_PADRAO = MODO_COMERCIAL;

    private static final Locale LOCALE_BRASIL = Locale.forLanguageTag("pt-BR");

    private Moeda() {
        // Construtor privado para classe utilitária
    }

    /**
     * Arredonda o valor monetário fornecido para 2 casas decimais utilizando o modo comercial padrão (HALF_UP).
     *
     * @param valor o montante monetário a arredondar
     * @return o {@link BigDecimal} com escala 2 arredondado
     * @throws NullPointerException se o valor for nulo
     */
    public static BigDecimal arredondar(BigDecimal valor) {
        return arredondar(valor, MODO_PADRAO);
    }

    /**
     * Arredonda o valor monetário utilizando expressamente o modo de arredondamento informado.
     *
     * @param valor o montante a ser arredondado
     * @param modo o modo de arredondamento {@link RoundingMode}
     * @return o montante ajustado para a escala de 2 casas decimais
     * @throws NullPointerException se valor ou modo forem nulos
     */
    public static BigDecimal arredondar(BigDecimal valor, RoundingMode modo) {
        Objects.requireNonNull(valor, "Valor monetário não pode ser nulo.");
        Objects.requireNonNull(modo, "Modo de arredondamento não pode ser nulo.");
        return valor.setScale(CASAS_DECIMAIS, modo);
    }

    /**
     * Arredonda o valor para 2 casas decimais utilizando o modo bancário (HALF_EVEN).
     *
     * @param valor o montante monetário a arredondar
     * @return o valor ajustado com escala 2 via HALF_EVEN
     * @throws NullPointerException se o valor for nulo
     */
    public static BigDecimal arredondarBancario(BigDecimal valor) {
        return arredondar(valor, MODO_BANCARIO);
    }

    /**
     * Valida e garante que o montante monetário seja estritamente positivo (maior que zero).
     *
     * @param valor o montante a verificar
     * @param nomeCampo nome descritivo do campo para mensagens de erro
     * @return o valor arredondado para 2 casas decimais
     * @throws NullPointerException se valor ou nomeCampo forem nulos
     * @throws IllegalArgumentException se o valor arredondado for menor ou igual a zero
     */
    public static BigDecimal positivo(BigDecimal valor, String nomeCampo) {
        Objects.requireNonNull(nomeCampo, "Nome do campo não pode ser nulo.");
        BigDecimal arredondado = arredondar(valor);
        if (arredondado.signum() <= 0) {
            throw new IllegalArgumentException(nomeCampo + " deve ser estritamente positivo (maior que zero).");
        }
        return arredondado;
    }

    /**
     * Valida e garante que o montante monetário seja não-negativo (maior ou igual a zero).
     *
     * @param valor o montante a verificar
     * @param nomeCampo nome descritivo do campo para mensagens de erro
     * @return o valor arredondado para 2 casas decimais
     * @throws NullPointerException se valor ou nomeCampo forem nulos
     * @throws IllegalArgumentException se o valor arredondado for estritamente menor que zero
     */
    public static BigDecimal naoNegativo(BigDecimal valor, String nomeCampo) {
        Objects.requireNonNull(nomeCampo, "Nome do campo não pode ser nulo.");
        BigDecimal arredondado = arredondar(valor);
        if (arredondado.signum() < 0) {
            throw new IllegalArgumentException(nomeCampo + " não pode ser negativo.");
        }
        return arredondado;
    }

    /**
     * Converte um valor primitivo {@code double} para {@link BigDecimal} com escala 2 e arredondamento padrão.
     *
     * @param valor montante em ponto flutuante
     * @return o {@link BigDecimal} correspondente
     */
    public static BigDecimal de(double valor) {
        return BigDecimal.valueOf(valor).setScale(CASAS_DECIMAIS, MODO_PADRAO);
    }

    /**
     * Converte e valida uma representação textual de valor em {@link BigDecimal} com escala monetária de 2 casas.
     *
     * <p>Suporta indistintamente a notação brasileira (ex.: {@code "1.500,50"} ou {@code "1500,50"})
     * e a notação internacional (ex.: {@code "1,500.50"} ou {@code "1500.50"}), além de entradas
     * com prefixos monetários ({@code "R$ 1.500,50"}), decimais truncados ({@code ".50"}) e inteiros.</p>
     *
     * @param valorTexto string contendo o número monetário
     * @return o {@link BigDecimal} correspondente ajustado para 2 casas decimais
     * @throws NullPointerException se valorTexto for nulo
     * @throws IllegalArgumentException se a string for vazia ou não puder ser convertida em decimal válido
     */
    public static BigDecimal de(String valorTexto) {
        Objects.requireNonNull(valorTexto, "Texto monetário não pode ser nulo.");
        String s = valorTexto.trim();
        if (s.isEmpty()) {
            throw new IllegalArgumentException("Texto monetário não pode ser vazio.");
        }

        // 1. Sanitização de prefixos monetários e caracteres de espaçamento (incluindo NBSP)
        s = s.replace("R$", "").replace("r$", "").replace("$", "");
        s = s.replace("\u00A0", "").replace("\u202F", "").replace(" ", "");

        // 2. Extração do sinal algébrico (+ / -)
        boolean negativo = false;
        if (s.startsWith("-")) {
            negativo = true;
            s = s.substring(1).trim();
        } else if (s.startsWith("+")) {
            s = s.substring(1).trim();
        }

        if (s.isEmpty()) {
            throw new IllegalArgumentException("Valor monetário inválido: '" + valorTexto + "'.");
        }

        // 3. Contagem de separadores
        int contagemPontos = 0;
        int contagemVirgulas = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '.') contagemPontos++;
            else if (c == ',') contagemVirgulas++;
        }

        String parseavel;
        if (contagemPontos > 0 && contagemVirgulas > 0) {
            // Caso A: Ambos separadores presentes
            int ultimoPonto = s.lastIndexOf('.');
            int ultimaVirgula = s.lastIndexOf(',');
            if (ultimaVirgula > ultimoPonto) {
                // Notação brasileira: ponto é milhar, vírgula é decimal (ex.: "1.500,50")
                parseavel = s.replace(".", "").replace(',', '.');
            } else {
                // Notação internacional: vírgula é milhar, ponto é decimal (ex.: "1,500.50")
                parseavel = s.replace(",", "");
            }
        } else if (contagemVirgulas > 0) {
            // Caso B: Apenas vírgula(s)
            if (contagemVirgulas == 1) {
                // Vírgula única: sempre decimal (ex.: "1500,50", "0,5", ",50")
                parseavel = s.replace(',', '.');
            } else {
                // Múltiplas vírgulas: milhar no padrão internacional sem decimal (ex.: "1,000,000")
                parseavel = s.replace(",", "");
            }
        } else if (contagemPontos > 0) {
            // Caso C: Apenas ponto(s)
            if (contagemPontos == 1) {
                int idx = s.indexOf('.');
                String parteInteira = s.substring(0, idx);
                String parteDecimal = s.substring(idx + 1);

                // Se sufixo tiver 1 ou 2 dígitos, é decimal (ex.: "1500.50", "1500.5", "0.5")
                // Se prefixo for vazio ou "0", também é decimal (ex.: ".50", "0.500")
                // Se sufixo tiver 3 dígitos e prefixo tiver 1..3 dígitos não-zero (ex.: "1.000"), é milhar brasileiro
                if (parteDecimal.length() == 3 && parteInteira.length() >= 1 && parteInteira.length() <= 3 && !parteInteira.equals("0")) {
                    parseavel = s.replace(".", "");
                } else {
                    parseavel = s;
                }
            } else {
                // Múltiplos pontos: milhar no padrão brasileiro (ex.: "1.000.000")
                parseavel = s.replace(".", "");
            }
        } else {
            // Caso D: Apenas dígitos inteiros (ex.: "1500")
            parseavel = s;
        }

        if (negativo) {
            parseavel = "-" + parseavel;
        }

        try {
            return new BigDecimal(parseavel).setScale(CASAS_DECIMAIS, MODO_PADRAO);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Valor monetário inválido: '" + valorTexto + "'.", e);
        }
    }

    /**
     * Verifica se o valor monetário equivale a zero.
     *
     * @param valor o montante a avaliar
     * @return {@code true} se for nulo ou igual a zero; {@code false} caso contrário
     */
    public static boolean ehZero(BigDecimal valor) {
        return valor == null || valor.signum() == 0;
    }

    /**
     * Verifica se o valor monetário é estritamente positivo (maior que zero).
     *
     * @param valor o montante a avaliar
     * @return {@code true} se não for nulo e seu sinal for positivo; {@code false} caso contrário
     */
    public static boolean ehPositivo(BigDecimal valor) {
        return valor != null && valor.signum() > 0;
    }

    /**
     * Verifica se o valor monetário é negativo (menor que zero).
     *
     * @param valor o montante a avaliar
     * @return {@code true} se não for nulo e seu sinal for negativo; {@code false} caso contrário
     */
    public static boolean ehNegativo(BigDecimal valor) {
        return valor != null && valor.signum() < 0;
    }

    /**
     * Formata o valor monetário no padrão oficial de moeda brasileira (ex: "R$ 1.500,50").
     *
     * @param valor o montante a formatar
     * @return representação monetária formatada em português do Brasil
     */
    public static String formatar(BigDecimal valor) {
        if (valor == null) {
            valor = ZERO;
        }
        NumberFormat formato = NumberFormat.getCurrencyInstance(LOCALE_BRASIL);
        return formato.format(valor);
    }
}

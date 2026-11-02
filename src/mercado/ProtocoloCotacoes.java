package mercado;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Regras compartilhadas de leitura, validação e formatação do protocolo TCP. */
final class ProtocoloCotacoes {
    static final String REQUEST = "GET_QUOTES";
    static final String INVALID_REQUEST = "ERR|INVALID_REQUEST";
    static final int MAX_LINE_BYTES = 512;

    private static final String[] CURRENCIES = {"SELICRA", "CDIR", "USD", "EUR"};
    private static final Set<String> CURRENCY_SET = Set.of(CURRENCIES);
    private static final Pattern DECIMAL = Pattern.compile("[+-]?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?");

    private ProtocoloCotacoes() {}

    /**
     * Lê uma linha ASCII sem alocação ilimitada. A quebra de linha não faz parte do limite.
     */
    static String lerLinha(InputStream entrada) throws IOException {
        byte[] linha = new byte[MAX_LINE_BYTES];
        int length = 0;
        while (true) {
            int next = entrada.read();
            if (next == -1) {
                if (length == 0) {
                    throw new IOException("Conexão encerrada antes de receber uma linha.");
                }
                break;
            }
            if (next == '\n') {
                break;
            }
            if (next == '\r' || next > 0x7f) {
                throw new IOException("A linha contém um caractere não permitido.");
            }
            if (length == linha.length) {
                throw new IOException("A linha excede o limite do protocolo.");
            }
            linha[length++] = (byte) next;
        }
        return new String(linha, 0, length, StandardCharsets.US_ASCII);
    }

    /** Valida uma resposta completa e devolve as quatro taxas na ordem do protocolo. */
    static Map<String, BigDecimal> interpretarCotacoes(String resposta) throws IOException {
        if (resposta.startsWith("ERR|")) {
            throw new IOException("O servidor recusou a solicitação: " + resposta.substring(4));
        }

        String[] fields = resposta.split("\\|", -1);
        if (fields.length != CURRENCIES.length + 1 || !"OK".equals(fields[0])) {
            throw new IOException("Resposta de protocolo inválida ou incompleta.");
        }

        Map<String, BigDecimal> cotacoes = new LinkedHashMap<>();
        for (int i = 1; i < fields.length; i++) {
            int separator = fields[i].indexOf('=');
            if (separator <= 0 || separator != fields[i].lastIndexOf('=')) {
                throw new IOException("Campo inválido na resposta do servidor.");
            }
            String nome = fields[i].substring(0, separator);
            String valor = fields[i].substring(separator + 1);
            if (!CURRENCY_SET.contains(nome) || cotacoes.containsKey(nome)
                    || !DECIMAL.matcher(valor).matches()) {
                throw new IOException("Cotação inválida na resposta do servidor.");
            }
            cotacoes.put(nome, new BigDecimal(valor));
        }
        if (cotacoes.size() != CURRENCIES.length) {
            throw new IOException("Resposta incompleta do servidor.");
        }
        return Collections.unmodifiableMap(cotacoes);
    }

    /** Confere as taxas antes de guardá-las no servidor. */
    static Map<String, BigDecimal> validateQuotes(Map<String, BigDecimal> values) {
        if (values == null || values.size() != CURRENCIES.length
                || !values.keySet().equals(CURRENCY_SET)) {
            throw new IllegalArgumentException("Informe exatamente SELICRA, CDIR, USD e EUR.");
        }

        Map<String, BigDecimal> cotacoes = new LinkedHashMap<>();
        for (String currency : CURRENCIES) {
            BigDecimal valor = values.get(currency);
            if (valor == null || comprimentoDecimalSimples(valor) > MAX_LINE_BYTES) {
                throw new IllegalArgumentException("Valor decimal inválido para " + currency + ".");
            }
            cotacoes.put(currency, valor);
        }
        if (formatQuotes(cotacoes).length() > MAX_LINE_BYTES) {
            throw new IllegalArgumentException("As cotações excedem o limite do protocolo.");
        }
        return Collections.unmodifiableMap(cotacoes);
    }

    /** Calcula o tamanho serializado antes de criar uma string potencialmente enorme. */
    private static long comprimentoDecimalSimples(BigDecimal valor) {
        long precision = valor.precision();
        long scale = valor.scale();
        long digits = scale <= 0
                ? precision - scale
                : Math.max(precision, scale) + (precision > scale ? 1 : 2);
        return digits + (valor.signum() < 0 ? 1 : 0);
    }

    /** Produz uma resposta com nomes e ordem canônicos, sem formatação localizada. */
    static String formatQuotes(Map<String, BigDecimal> cotacoes) {
        StringBuilder resposta = new StringBuilder("OK");
        for (String currency : CURRENCIES) {
            resposta.append('|').append(currency).append('=')
                    .append(cotacoes.get(currency).toPlainString());
        }
        return resposta.toString();
    }
}

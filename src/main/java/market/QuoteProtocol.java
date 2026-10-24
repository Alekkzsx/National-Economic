package market;

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
final class QuoteProtocol {
    static final String REQUEST = "GET_QUOTES";
    static final String INVALID_REQUEST = "ERR|INVALID_REQUEST";
    static final int MAX_LINE_BYTES = 512;

    private static final String[] CURRENCIES = {"SELICRA", "CDIR", "USD", "EUR"};
    private static final Set<String> CURRENCY_SET = Set.of(CURRENCIES);
    private static final Pattern DECIMAL = Pattern.compile("[+-]?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?");

    private QuoteProtocol() {}

    /**
     * Lê uma linha ASCII sem alocação ilimitada. A quebra de linha não faz parte do limite.
     */
    static String readLine(InputStream input) throws IOException {
        byte[] line = new byte[MAX_LINE_BYTES];
        int length = 0;
        while (true) {
            int next = input.read();
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
            if (length == line.length) {
                throw new IOException("A linha excede o limite do protocolo.");
            }
            line[length++] = (byte) next;
        }
        return new String(line, 0, length, StandardCharsets.US_ASCII);
    }

    /** Valida uma resposta completa e devolve as quatro taxas na ordem do protocolo. */
    static Map<String, BigDecimal> parseQuotes(String response) throws IOException {
        if (response.startsWith("ERR|")) {
            throw new IOException("O servidor recusou a solicitação: " + response.substring(4));
        }

        String[] fields = response.split("\\|", -1);
        if (fields.length != CURRENCIES.length + 1 || !"OK".equals(fields[0])) {
            throw new IOException("Resposta de protocolo inválida ou incompleta.");
        }

        Map<String, BigDecimal> quotes = new LinkedHashMap<>();
        for (int i = 1; i < fields.length; i++) {
            int separator = fields[i].indexOf('=');
            if (separator <= 0 || separator != fields[i].lastIndexOf('=')) {
                throw new IOException("Campo inválido na resposta do servidor.");
            }
            String name = fields[i].substring(0, separator);
            String value = fields[i].substring(separator + 1);
            if (!CURRENCY_SET.contains(name) || quotes.containsKey(name)
                    || !DECIMAL.matcher(value).matches()) {
                throw new IOException("Cotação inválida na resposta do servidor.");
            }
            quotes.put(name, new BigDecimal(value));
        }
        if (quotes.size() != CURRENCIES.length) {
            throw new IOException("Resposta incompleta do servidor.");
        }
        return Collections.unmodifiableMap(quotes);
    }

    /** Confere as taxas antes de guardá-las no servidor. */
    static Map<String, BigDecimal> validateQuotes(Map<String, BigDecimal> values) {
        if (values == null || values.size() != CURRENCIES.length
                || !values.keySet().equals(CURRENCY_SET)) {
            throw new IllegalArgumentException("Informe exatamente SELICRA, CDIR, USD e EUR.");
        }

        Map<String, BigDecimal> quotes = new LinkedHashMap<>();
        for (String currency : CURRENCIES) {
            BigDecimal value = values.get(currency);
            if (value == null || plainDecimalLength(value) > MAX_LINE_BYTES) {
                throw new IllegalArgumentException("Valor decimal inválido para " + currency + ".");
            }
            quotes.put(currency, value);
        }
        if (formatQuotes(quotes).length() > MAX_LINE_BYTES) {
            throw new IllegalArgumentException("As cotações excedem o limite do protocolo.");
        }
        return Collections.unmodifiableMap(quotes);
    }

    /** Calcula o tamanho serializado antes de criar uma string potencialmente enorme. */
    private static long plainDecimalLength(BigDecimal value) {
        long precision = value.precision();
        long scale = value.scale();
        long digits = scale <= 0
                ? precision - scale
                : Math.max(precision, scale) + (precision > scale ? 1 : 2);
        return digits + (value.signum() < 0 ? 1 : 0);
    }

    /** Produz uma resposta com nomes e ordem canônicos, sem formatação localizada. */
    static String formatQuotes(Map<String, BigDecimal> quotes) {
        StringBuilder response = new StringBuilder("OK");
        for (String currency : CURRENCIES) {
            response.append('|').append(currency).append('=')
                    .append(quotes.get(currency).toPlainString());
        }
        return response.toString();
    }
}

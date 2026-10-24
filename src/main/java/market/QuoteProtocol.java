package market;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Protocolo textual: o cliente envia {@code GET_QUOTES\n} e recebe
 * {@code OK|SELICRA=...|CDIR=...|USD=...|EUR=...} ou {@code ERR|...},
 * sempre em uma única linha. Valores usam decimal simples, sem expoente.
 */
final class QuoteProtocol {
    static final int MAX_LINE_BYTES = 512;
    private static final String[] CURRENCIES = {"SELICRA", "CDIR", "USD", "EUR"};
    private static final Set<String> CURRENCY_SET = Set.of(CURRENCIES);
    private static final Pattern DECIMAL = Pattern.compile("[+-]?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?");

    private QuoteProtocol() {}

    static String readLine(InputStream input) throws IOException {
        byte[] bytes = new byte[MAX_LINE_BYTES];
        int length = 0;
        boolean sawByte = false;
        while (true) {
            int value = input.read();
            if (value == -1) {
                if (!sawByte) {
                    throw new IOException("Conexão encerrada antes da resposta.");
                }
                break;
            }
            sawByte = true;
            if (value == '\n') {
                break;
            }
            if (value == '\r' || value > 0x7f) {
                throw new IOException("Linha contém caractere inválido.");
            }
            if (length == bytes.length) {
                throw new IOException("Linha excede o limite do protocolo.");
            }
            bytes[length++] = (byte) value;
        }
        return new String(bytes, 0, length, java.nio.charset.StandardCharsets.US_ASCII);
    }

    static Map<String, BigDecimal> parseQuotes(String response) throws IOException {
        if (response.startsWith("ERR|")) {
            throw new IOException("Servidor recusou a solicitação: " + response.substring(4));
        }
        String[] fields = response.split("\\|", -1);
        if (fields.length != CURRENCIES.length + 1 || !"OK".equals(fields[0])) {
            throw new IOException("Resposta de protocolo inválida.");
        }

        Map<String, BigDecimal> quotes = new LinkedHashMap<>();
        for (int i = 1; i < fields.length; i++) {
            int separator = fields[i].indexOf('=');
            if (separator <= 0 || separator != fields[i].lastIndexOf('=')) {
                throw new IOException("Campo inválido na resposta do servidor.");
            }
            String name = fields[i].substring(0, separator);
            String value = fields[i].substring(separator + 1);
            if (!CURRENCY_SET.contains(name) || !DECIMAL.matcher(value).matches()
                    || quotes.containsKey(name)) {
                throw new IOException("Cotação inválida na resposta do servidor.");
            }
            try {
                quotes.put(name, new BigDecimal(value));
            } catch (NumberFormatException exception) {
                throw new IOException("Cotação inválida na resposta do servidor.", exception);
            }
        }
        if (quotes.size() != CURRENCIES.length) {
            throw new IOException("Resposta incompleta do servidor.");
        }
        return Map.copyOf(quotes);
    }

    static Map<String, BigDecimal> validateQuotes(Map<String, BigDecimal> values) {
        if (values == null || values.size() != CURRENCIES.length
                || !values.keySet().equals(CURRENCY_SET)) {
            throw new IllegalArgumentException("Informe exatamente SELICRA, CDIR, USD e EUR.");
        }
        Map<String, BigDecimal> quotes = new LinkedHashMap<>();
        for (String currency : CURRENCIES) {
            BigDecimal value = values.get(currency);
            if (value == null || !DECIMAL.matcher(value.toPlainString()).matches()) {
                throw new IllegalArgumentException("Valor decimal inválido para " + currency + ".");
            }
            quotes.put(currency, value);
        }
        if (formatQuotes(quotes).length() > MAX_LINE_BYTES) {
            throw new IllegalArgumentException("As cotações excedem o limite de tamanho do protocolo.");
        }
        return Map.copyOf(quotes);
    }

    static String formatQuotes(Map<String, BigDecimal> quotes) {
        StringBuilder response = new StringBuilder("OK");
        for (String currency : CURRENCIES) {
            response.append('|').append(currency).append('=').append(quotes.get(currency).toPlainString());
        }
        return response.toString();
    }
}

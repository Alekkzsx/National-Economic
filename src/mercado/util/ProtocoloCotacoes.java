package mercado.util;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Utilitário reutilizável do protocolo TCP ASCII de cotações:
 * <ul>
 *   <li>Requisição do cliente: {@code GET_QUOTES\n} (até {@value #LIMITE_BYTES_LINHA} bytes, ASCII puro, sem {@code \r}).</li>
 *   <li>Resposta de sucesso: {@code OK|SELICRA=...|CDIR=...|USD=...|EUR=...\n}.</li>
 *   <li>Resposta de erro: {@code ERR|INVALID_REQUEST\n}.</li>
 *   <li>Validação simétrica em servidor e cliente: taxas {@code >= 0}, câmbio {@code > 0}.</li>
 * </ul>
 */
public final class ProtocoloCotacoes {

    public static final String REQUISICAO = "GET_QUOTES";
    public static final String REQUISICAO_INVALIDA = "ERR|INVALID_REQUEST";
    public static final int LIMITE_BYTES_LINHA = 512;

    private static final String[] CHAVES = {"SELICRA", "CDIR", "USD", "EUR"};
    private static final Set<String> CONJUNTO_CHAVES = Set.of(CHAVES);
    private static final Pattern DECIMAL = Pattern.compile("[+-]?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?");

    private ProtocoloCotacoes() {
    }

    /** Lê uma linha ASCII limitada a {@value #LIMITE_BYTES_LINHA} bytes, rejeitando CR e não-ASCII. */
    public static String lerLinha(InputStream entrada) throws IOException {
        byte[] linha = new byte[LIMITE_BYTES_LINHA];
        int tamanho = 0;
        while (true) {
            int proximo = entrada.read();
            if (proximo == -1) {
                if (tamanho == 0) {
                    throw new IOException("Conexão encerrada antes de receber uma linha.");
                }
                break;
            }
            if (proximo == '\n') {
                break;
            }
            if (proximo == '\r' || proximo > 0x7f) {
                throw new IOException("A linha contém um caractere não permitido.");
            }
            if (tamanho == linha.length) {
                throw new IOException("A linha excede o limite do protocolo.");
            }
            linha[tamanho++] = (byte) proximo;
        }
        return new String(linha, 0, tamanho, StandardCharsets.US_ASCII);
    }

    /** Valida e interpreta uma resposta do servidor, devolvendo as 4 cotações na ordem canônica. */
    public static Map<String, BigDecimal> interpretarCotacoes(String resposta) throws IOException {
        if (resposta.startsWith("ERR|")) {
            throw new IOException("O servidor recusou a solicitação: " + resposta.substring(4));
        }
        String[] campos = resposta.split("\\|", -1);
        if (campos.length != CHAVES.length + 1 || !"OK".equals(campos[0])) {
            throw new IOException("Resposta de protocolo inválida ou incompleta.");
        }

        Map<String, BigDecimal> cotacoes = new LinkedHashMap<>();
        for (int i = 1; i < campos.length; i++) {
            int separador = campos[i].indexOf('=');
            if (separador <= 0 || separador != campos[i].lastIndexOf('=')) {
                throw new IOException("Campo inválido na resposta do servidor.");
            }
            String nome = campos[i].substring(0, separador);
            String textoValor = campos[i].substring(separador + 1);
            if (!CONJUNTO_CHAVES.contains(nome) || cotacoes.containsKey(nome)
                    || !DECIMAL.matcher(textoValor).matches()) {
                throw new IOException("Cotação inválida na resposta do servidor.");
            }
            BigDecimal valor = new BigDecimal(textoValor);
            if (!ehValorValido(nome, valor)) {
                throw new IOException("Valor fora do intervalo permitido para " + nome + ".");
            }
            cotacoes.put(nome, valor);
        }
        if (cotacoes.size() != CHAVES.length) {
            throw new IOException("Resposta incompleta do servidor.");
        }
        return Collections.unmodifiableMap(cotacoes);
    }

    /** Valida o mapa de cotações antes de armazená-lo no servidor TCP. */
    public static Map<String, BigDecimal> validarCotacoes(Map<String, BigDecimal> valores) {
        if (valores == null || valores.size() != CHAVES.length || !valores.keySet().equals(CONJUNTO_CHAVES)) {
            throw new IllegalArgumentException("Informe exatamente SELICRA, CDIR, USD e EUR.");
        }

        Map<String, BigDecimal> cotacoes = new LinkedHashMap<>();
        for (String chave : CHAVES) {
            BigDecimal valor = valores.get(chave);
            if (!ehValorValido(chave, valor) || comprimentoDecimalSimples(valor) > LIMITE_BYTES_LINHA) {
                throw new IllegalArgumentException("Valor decimal inválido para " + chave + ".");
            }
            cotacoes.put(chave, valor);
        }
        if (formatarCotacoes(cotacoes).length() > LIMITE_BYTES_LINHA) {
            throw new IllegalArgumentException("As cotações excedem o limite do protocolo.");
        }
        return Collections.unmodifiableMap(cotacoes);
    }

    /** Produz a linha de resposta canônica: {@code OK|SELICRA=...|CDIR=...|USD=...|EUR=...}. */
    public static String formatarCotacoes(Map<String, BigDecimal> cotacoes) {
        StringBuilder resposta = new StringBuilder("OK");
        for (String chave : CHAVES) {
            resposta.append('|').append(chave).append('=')
                    .append(cotacoes.get(chave).toPlainString());
        }
        return resposta.toString();
    }

    /** Fecha um recurso de rede ignorando falhas já esperadas durante o encerramento. */
    public static void encerrarSilenciosamente(Closeable recurso) {
        if (recurso == null) return;
        try {
            recurso.close();
        } catch (IOException ignored) {
            // Recurso já fechado pela outra ponta ou durante shutdown
        }
    }

    /** Taxas (SELICRA/CDIR) devem ser >= 0; moedas (USD/EUR) devem ser estritamente > 0. */
    private static boolean ehValorValido(String chave, BigDecimal valor) {
        if (valor == null) {
            return false;
        }
        return ("USD".equals(chave) || "EUR".equals(chave))
                ? valor.signum() > 0
                : valor.signum() >= 0;
    }

    private static long comprimentoDecimalSimples(BigDecimal valor) {
        long precision = valor.precision();
        long scale = valor.scale();
        long digitos = scale <= 0
                ? precision - scale
                : Math.max(precision, scale) + (precision > scale ? 1 : 2);
        return digitos + (valor.signum() < 0 ? 1 : 0);
    }
}

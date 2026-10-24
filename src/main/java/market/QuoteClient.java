package market;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Cliente síncrono para o serviço TCP de cotações.
 *
 * <p>Cada consulta abre uma conexão curta, envia o comando permitido e valida
 * a resposta completa antes de devolvê-la ao chamador.</p>
 */
public final class QuoteClient {
    /** Limite para estabelecimento e leitura da conexão. */
    private static final int TIMEOUT_MILLIS = 5_000;
    /** Nome de host ou endereço do servidor de cotações. */
    private final String host;
    /** Porta TCP do servidor. */
    private final int port;

    /** Cria um cliente após validar host e porta. */
    public QuoteClient(String host, int port) {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("O host não pode ser vazio.");
        }
        if (port < 1 || port > 65_535) {
            throw new IllegalArgumentException("A porta deve estar entre 1 e 65535.");
        }
        this.host = host;
        this.port = port;
    }

    /**
     * Consulta as quatro taxas configuradas no servidor.
     *
     * @throws IOException em falha de rede, resposta incompleta ou protocolo inválido
     */
    public Map<String, BigDecimal> getQuotes() throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), TIMEOUT_MILLIS);
            socket.setSoTimeout(TIMEOUT_MILLIS);
            OutputStream output = socket.getOutputStream();
            // O protocolo é uma única linha ASCII, seguida por quebra de linha.
            output.write("GET_QUOTES\n".getBytes(StandardCharsets.US_ASCII));
            output.flush();
            return QuoteProtocol.parseQuotes(QuoteProtocol.readLine(socket.getInputStream()));
        }
    }
}

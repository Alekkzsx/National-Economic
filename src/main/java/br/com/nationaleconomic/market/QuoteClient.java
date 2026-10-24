package br.com.nationaleconomic.market;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Cliente síncrono para o serviço TCP de cotações. */
public final class QuoteClient {
    private static final int TIMEOUT_MILLIS = 5_000;
    private final String host;
    private final int port;

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

    public Map<String, BigDecimal> getQuotes() throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), TIMEOUT_MILLIS);
            socket.setSoTimeout(TIMEOUT_MILLIS);
            OutputStream output = socket.getOutputStream();
            output.write("GET_QUOTES\n".getBytes(StandardCharsets.US_ASCII));
            output.flush();
            return QuoteProtocol.parseQuotes(QuoteProtocol.readLine(socket.getInputStream()));
        }
    }
}

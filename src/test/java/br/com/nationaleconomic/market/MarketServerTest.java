package br.com.nationaleconomic.market;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MarketServerTest {
    private static final BigDecimal SELICRA = new BigDecimal("10.50");
    private static final BigDecimal CDIR = new BigDecimal("1.25");
    private static final BigDecimal USD = new BigDecimal("5.10");
    private static final BigDecimal EUR = new BigDecimal("5.55");

    @Test
    void servesConfiguredQuotesToClient() throws Exception {
        try (MarketServer server = createServer()) {
            server.start();

            Map<String, BigDecimal> quotes = new QuoteClient("127.0.0.1", server.getPort()).getQuotes();

            assertEquals(Map.of(
                    "SELICRA", SELICRA,
                    "CDIR", CDIR,
                    "USD", USD,
                    "EUR", EUR), quotes);
        }
    }

    @Test
    void rejectsCommandsOutsideTheProtocol() throws Exception {
        try (MarketServer server = createServer()) {
            server.start();
            try (Socket socket = new Socket("127.0.0.1", server.getPort())) {
                socket.getOutputStream().write("RUN_SOMETHING\n".getBytes(StandardCharsets.US_ASCII));
                socket.getOutputStream().flush();

                assertEquals("ERR|INVALID_REQUEST", QuoteProtocol.readLine(socket.getInputStream()));
            }
        }
    }

    @Test
    void rejectsMissingAndInvalidConfiguredQuotes() {
        assertThrows(IllegalArgumentException.class,
                () -> new MarketServer(0, Map.of("SELICRA", SELICRA)));
        assertThrows(IllegalArgumentException.class,
                () -> new MarketServer(0, SELICRA, CDIR, null, EUR));
    }

    @Test
    void clientReportsMalformedServerResponses() throws Exception {
        try (ServerSocket listener = new ServerSocket(0)) {
            Thread peer = new Thread(() -> {
                try (Socket socket = listener.accept()) {
                    QuoteProtocol.readLine(socket.getInputStream());
                    socket.getOutputStream().write("OK|USD=5.10\n".getBytes(StandardCharsets.US_ASCII));
                    socket.getOutputStream().flush();
                } catch (IOException exception) {
                    throw new AssertionError(exception);
                }
            });
            peer.start();

            IOException exception = assertThrows(IOException.class,
                    () -> new QuoteClient("127.0.0.1", listener.getLocalPort()).getQuotes());

            assertTrue(exception.getMessage().contains("Resposta"));
            peer.join();
        }
    }

    private static MarketServer createServer() throws IOException {
        return new MarketServer(0, SELICRA, CDIR, USD, EUR);
    }
}

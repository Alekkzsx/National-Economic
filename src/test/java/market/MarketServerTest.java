package market;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** Testes locais de integração para o ciclo de vida e o protocolo TCP. */
class MarketServerTest {
    private static final BigDecimal SELICRA = new BigDecimal("10.50");
    private static final BigDecimal CDIR = new BigDecimal("1.25");
    private static final BigDecimal USD = new BigDecimal("5.10");
    private static final BigDecimal EUR = new BigDecimal("5.55");

    @Test
    void clientReceivesConfiguredQuotes() throws Exception {
        try (MarketServer server = createServer()) {
            server.start();

            Map<String, BigDecimal> quotes =
                    new QuoteClient("127.0.0.1", server.getPort()).getQuotes();

            assertEquals(Map.of(
                    "SELICRA", SELICRA,
                    "CDIR", CDIR,
                    "USD", USD,
                    "EUR", EUR), quotes);
        }
    }

    @Test
    void serverUsesTheCanonicalWireFormat() throws Exception {
        try (MarketServer server = createServer()) {
            server.start();
            try (Socket socket = connectAndSend(server, "GET_QUOTES\n")) {
                assertEquals("OK|SELICRA=10.50|CDIR=1.25|USD=5.10|EUR=5.55",
                        QuoteProtocol.readLine(socket.getInputStream()));
            }
        }
    }

    @Test
    void rejectsCommandsOutsideTheProtocol() throws Exception {
        try (MarketServer server = createServer()) {
            server.start();
            try (Socket socket = connectAndSend(server, "RUN_SOMETHING\n")) {
                assertEquals(QuoteProtocol.INVALID_REQUEST,
                        QuoteProtocol.readLine(socket.getInputStream()));
            }
        }
    }

    @Test
    void rejectsOversizedRequestsWithAnExplicitError() throws Exception {
        try (MarketServer server = createServer()) {
            server.start();
            try (Socket socket = new Socket("127.0.0.1", server.getPort())) {
                byte[] request = ("X".repeat(QuoteProtocol.MAX_LINE_BYTES + 1) + "\n")
                        .getBytes(StandardCharsets.US_ASCII);
                socket.getOutputStream().write(request);
                socket.getOutputStream().flush();

                assertEquals(QuoteProtocol.INVALID_REQUEST,
                        QuoteProtocol.readLine(socket.getInputStream()));
            }
        }
    }

    @Test
    void rejectsIncompleteOrNullConfiguredQuotes() {
        assertThrows(IllegalArgumentException.class,
                () -> new MarketServer(0, Map.of("SELICRA", SELICRA)));
        assertThrows(IllegalArgumentException.class,
                () -> new MarketServer(0, SELICRA, CDIR, null, EUR));
    }

    @Test
    void rejectsAConfiguredValueWhoseSerializedFormIsTooLarge() {
        BigDecimal oversized = BigDecimal.ONE.scaleByPowerOfTen(QuoteProtocol.MAX_LINE_BYTES);
        assertThrows(IllegalArgumentException.class,
                () -> new MarketServer(0, oversized, CDIR, USD, EUR));
    }

    @Test
    void clientReportsMalformedServerResponses() throws Exception {
        try (ServerSocket listener = new ServerSocket(0)) {
            AtomicReference<Throwable> peerFailure = new AtomicReference<>();
            Thread peer = new Thread(() -> {
                try (Socket socket = listener.accept()) {
                    QuoteProtocol.readLine(socket.getInputStream());
                    socket.getOutputStream().write("OK|USD=5.10\n".getBytes(StandardCharsets.US_ASCII));
                    socket.getOutputStream().flush();
                } catch (Throwable failure) {
                    peerFailure.set(failure);
                }
            });
            peer.start();

            IOException exception = assertThrows(IOException.class,
                    () -> new QuoteClient("127.0.0.1", listener.getLocalPort()).getQuotes());

            peer.join();
            assertTrue(exception.getMessage().contains("Resposta"));
            assertNull(peerFailure.get());
        }
    }

    @Test
    void rejectsProtocolLinesThatExceedTheLimit() {
        byte[] line = ("A".repeat(QuoteProtocol.MAX_LINE_BYTES + 1) + "\n")
                .getBytes(StandardCharsets.US_ASCII);
        assertThrows(IOException.class,
                () -> QuoteProtocol.readLine(new ByteArrayInputStream(line)));
    }

    @Test
    void cannotRestartAClosedServer() throws Exception {
        MarketServer server = createServer();
        server.close();
        assertThrows(IllegalStateException.class, server::start);
    }

    private static Socket connectAndSend(MarketServer server, String request) throws IOException {
        Socket socket = new Socket("127.0.0.1", server.getPort());
        socket.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII));
        socket.getOutputStream().flush();
        return socket;
    }

    private static MarketServer createServer() throws IOException {
        return new MarketServer(0, SELICRA, CDIR, USD, EUR);
    }
}

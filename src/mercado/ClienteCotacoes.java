package mercado;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Cliente síncrono: abre uma conexão por consulta e valida a resposta recebida. */
public final class ClienteCotacoes {
    private static final int TIMEOUT_MILLIS = 5_000;

    private final String host;
    private final int porta;

    /** Cria o cliente após validar o endereço do servidor. */
    public ClienteCotacoes(String host, int porta) {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("O host não pode ser vazio.");
        }
        if (porta < 1 || porta > 65_535) {
            throw new IllegalArgumentException("A porta deve estar entre 1 e 65535.");
        }
        this.host = host;
        this.porta = porta;
    }

    /**
     * Envia o único comando suportado e retorna as quatro cotações.
     *
     * @throws IOException em falha de rede, timeout ou resposta inválida
     */
    public Map<String, BigDecimal> obterCotacoes() throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, porta), TIMEOUT_MILLIS);
            socket.setSoTimeout(TIMEOUT_MILLIS);
            socket.getOutputStream().write(
                    (ProtocoloCotacoes.REQUEST + "\n").getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().flush();
            return ProtocoloCotacoes.interpretarCotacoes(ProtocoloCotacoes.lerLinha(socket.getInputStream()));
        }
    }
}

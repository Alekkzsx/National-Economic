package mercado;

import static test.Afirmacao.*;

import mercado.util.ProtocoloCotacoes;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Testes autônomos do servidor TCP multithread e do protocolo de cotações (Item 9). */
public final class TesteServidorCotacoes {

    private TesteServidorCotacoes() {
    }

    public static void main(String[] args) throws Exception {
        testarFormatoWireEComandoInvalido();
        testarLimitesECaracteresProibidosNoProtocolo();
        testarValidacoesSimetricasDeValores();
        testarCicloDeVidaEFechamentoIdempotente();
        testarConsultasConcorrentesMultithread();
    }

    private static void testarFormatoWireEComandoInvalido() throws Exception {
        try (ServidorCotacoes servidor = new ServidorCotacoes(
                0, new BigDecimal("10.50"), new BigDecimal("10.40"),
                new BigDecimal("5.1825"), new BigDecimal("6.1410"))) {
            servidor.start();

            // Consulta direta via socket cru conferindo formato canônico
            try (Socket s = new Socket("127.0.0.1", servidor.getPorta())) {
                s.getOutputStream().write("GET_QUOTES\n".getBytes(StandardCharsets.US_ASCII));
                s.getOutputStream().flush();
                String linha = ProtocoloCotacoes.lerLinha(s.getInputStream());
                afirmarIgual("OK|SELICRA=10.50|CDIR=10.40|USD=5.1825|EUR=6.1410", linha,
                        "formato wire canônico");
            }

            // Comando fora do protocolo devolve ERR|INVALID_REQUEST
            try (Socket s = new Socket("127.0.0.1", servidor.getPorta())) {
                s.getOutputStream().write("COMANDO_INVALIDO\n".getBytes(StandardCharsets.US_ASCII));
                s.getOutputStream().flush();
                String linha = ProtocoloCotacoes.lerLinha(s.getInputStream());
                afirmarIgual("ERR|INVALID_REQUEST", linha, "comando desconhecido devolve erro");
            }

            // Linha acima do limite devolve ERR|INVALID_REQUEST
            try (Socket s = new Socket("127.0.0.1", servidor.getPorta())) {
                s.getOutputStream().write(("A".repeat(600) + "\n").getBytes(StandardCharsets.US_ASCII));
                s.getOutputStream().flush();
                String linha = ProtocoloCotacoes.lerLinha(s.getInputStream());
                afirmarIgual("ERR|INVALID_REQUEST", linha, "linha acima do limite devolve erro");
            }
        }
    }

    private static void testarLimitesECaracteresProibidosNoProtocolo() {
        afirmarRejeicao(() -> lerLinhaDe("GET_QUOTES\r\n"), RuntimeException.class, "rejeita CR");
        afirmarRejeicao(() -> lerLinhaDe("COTAÇÃO\n"), RuntimeException.class, "rejeita não-ASCII");
        afirmarRejeicao(() -> lerLinhaDe("X".repeat(ProtocoloCotacoes.LIMITE_BYTES_LINHA + 1) + "\n"),
                RuntimeException.class, "rejeita linha > MAX_LINE_BYTES");
    }

    private static void testarValidacoesSimetricasDeValores() throws Exception {
        // Servidor rejeita valores negativos/zero e mapa incompleto
        afirmarRejeicao(() -> {
            try {
                new ServidorCotacoes(0, new BigDecimal("-1"), BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }, IllegalArgumentException.class, "SELICRA negativa rejeitada no servidor");

        afirmarRejeicao(() -> {
            try {
                new ServidorCotacoes(0, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ONE);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }, IllegalArgumentException.class, "USD zero rejeitado no servidor");

        afirmarRejeicao(() -> {
            try {
                new ServidorCotacoes(0, Map.of("SELICRA", BigDecimal.ONE));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }, IllegalArgumentException.class, "mapa incompleto rejeitado");

        // Cliente rejeita resposta malformada ou com câmbio <= 0
        afirmarRejeicao(() -> interpretar("OK|SELICRA=10.50|CDIR=10.40|USD=0|EUR=6.14"),
                RuntimeException.class, "cliente rejeita USD=0");
        afirmarRejeicao(() -> interpretar("OK|SELICRA=-2.00|CDIR=10.40|USD=5.18|EUR=6.14"),
                RuntimeException.class, "cliente rejeita SELICRA negativa");
        afirmarRejeicao(() -> interpretar("ERR|INVALID_REQUEST"),
                RuntimeException.class, "cliente rejeita resposta ERR");

        // Servidor simulado devolvendo resposta inválida
        try (ServerSocket fake = new ServerSocket(0)) {
            Thread responder = new Thread(() -> {
                try (Socket c = fake.accept()) {
                    c.getOutputStream().write("RESPOSTA_QUEBRADA\n".getBytes(StandardCharsets.US_ASCII));
                    c.getOutputStream().flush();
                } catch (IOException ignored) { }
            });
            responder.start();
            afirmarRejeicao(() -> {
                try {
                    new ClienteCotacoes("127.0.0.1", fake.getLocalPort()).obterCotacoes();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }, RuntimeException.class, "ClienteCotacoes lança IOException em resposta malformada");
            responder.join(2_000);
        }
    }

    private static void testarCicloDeVidaEFechamentoIdempotente() throws Exception {
        ServidorCotacoes servidor = new ServidorCotacoes(
                0, BigDecimal.TEN, BigDecimal.ONE, new BigDecimal("5.00"), new BigDecimal("6.00"));
        servidor.start();
        afirmarRejeicao(servidor::start, IllegalStateException.class, "não permite iniciar duas vezes");
        servidor.close();
        servidor.close(); // close repetido é seguro
        afirmarRejeicao(servidor::start, IllegalStateException.class, "não permite reiniciar após fechado");
    }

    private static void testarConsultasConcorrentesMultithread() throws Exception {
        Map<String, BigDecimal> esperadas = new LinkedHashMap<>();
        esperadas.put("SELICRA", new BigDecimal("11.25"));
        esperadas.put("CDIR", new BigDecimal("11.15"));
        esperadas.put("USD", new BigDecimal("5.20"));
        esperadas.put("EUR", new BigDecimal("5.90"));

        try (ServidorCotacoes servidor = new ServidorCotacoes(0, esperadas)) {
            servidor.start();
            ExecutorService pool = Executors.newFixedThreadPool(8);
            try {
                List<Callable<Map<String, BigDecimal>>> tarefas = new ArrayList<>();
                for (int i = 0; i < 8; i++) {
                    tarefas.add(() -> new ClienteCotacoes("127.0.0.1", servidor.getPorta()).obterCotacoes());
                }
                List<Future<Map<String, BigDecimal>>> resultados = pool.invokeAll(tarefas);
                for (Future<Map<String, BigDecimal>> futuro : resultados) {
                    afirmarIgual(esperadas, futuro.get(), "todas as 8 threads recebem as 4 cotações íntegras");
                }
            } finally {
                pool.shutdownNow();
            }
        }
    }

    private static void lerLinhaDe(String texto) {
        try {
            ProtocoloCotacoes.lerLinha(new ByteArrayInputStream(texto.getBytes(StandardCharsets.UTF_8)));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void interpretar(String linha) {
        try {
            ProtocoloCotacoes.interpretarCotacoes(linha);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}

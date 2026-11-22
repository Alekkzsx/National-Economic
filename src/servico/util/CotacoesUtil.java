package servico.util;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Utilitário reutilizável para normalização e consulta de cotações de mercado
 * com espelhamento bidirecional {@code USD <-> DOLAR} e {@code EUR <-> EURO}.
 */
public final class CotacoesUtil {

    private CotacoesUtil() {
    }

    /** Insere todas as entradas válidas de {@code origem} em {@code destino}, normalizando chaves. */
    public static void registrar(Map<String, BigDecimal> destino, Map<String, BigDecimal> origem) {
        Objects.requireNonNull(destino, "Mapa de destino é obrigatório.");
        Objects.requireNonNull(origem, "Mapa de cotações é obrigatório.");
        for (Map.Entry<String, BigDecimal> entry : origem.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            String codigo = entry.getKey().trim().toUpperCase();
            BigDecimal valor = entry.getValue();
            destino.put(codigo, valor);

            switch (codigo) {
                case "USD" -> destino.put("DOLAR", valor);
                case "DOLAR" -> destino.put("USD", valor);
                case "EUR" -> destino.put("EURO", valor);
                case "EURO" -> destino.put("EUR", valor);
                default -> { }
            }
        }
    }

    /** Consulta uma cotação ignorando maiúsculas/minúsculas e espaços. */
    public static Optional<BigDecimal> consultar(Map<String, BigDecimal> mapa, String codigoOuNome) {
        if (mapa == null || codigoOuNome == null || codigoOuNome.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(mapa.get(codigoOuNome.trim().toUpperCase()));
    }

    /** Retorna uma cópia defensiva imutável preservando a ordem das chaves. */
    public static Map<String, BigDecimal> copiaImutavel(Map<String, BigDecimal> mapa) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(mapa));
    }
}

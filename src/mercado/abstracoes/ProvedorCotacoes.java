package mercado.abstracoes;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * Base abstrata para provedores de cotações de mercado (servidor TCP local ou API PTAX do BCB).
 */
public abstract class ProvedorCotacoes {

    private final String nomeOrigem;
    private final int timeoutMs;

    protected ProvedorCotacoes(String nomeOrigem, int timeoutMs) {
        if (nomeOrigem == null || nomeOrigem.isBlank()) {
            throw new IllegalArgumentException("Nome da origem das cotações é obrigatório.");
        }
        if (timeoutMs <= 0) {
            throw new IllegalArgumentException("Timeout deve ser positivo.");
        }
        this.nomeOrigem = nomeOrigem.trim();
        this.timeoutMs = timeoutMs;
    }

    public String getNomeOrigem() {
        return nomeOrigem;
    }

    public int getTimeoutMs() {
        return timeoutMs;
    }

    protected Duration getTimeout() {
        return Duration.ofMillis(timeoutMs);
    }

    /** Consulta todas as cotações disponibilizadas por esta origem. */
    public abstract Map<String, BigDecimal> obterCotacoes() throws IOException, InterruptedException;

    /** Consulta polimorficamente uma cotação específica (ex.: SELICRA, CDIR, USD, EUR). */
    public Optional<BigDecimal> consultarCotacao(String codigo) throws IOException, InterruptedException {
        if (codigo == null || codigo.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(obterCotacoes().get(codigo.trim().toUpperCase()));
    }
}

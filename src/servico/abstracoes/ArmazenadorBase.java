package servico.abstracoes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Base abstrata genérica para armazenadores em arquivo local (Template Method).
 *
 * @param <T> tipo do agregado persistido e restaurado
 */
public abstract class ArmazenadorBase<T> {

    protected final Path caminhoPadrao;

    protected ArmazenadorBase(Path caminhoPadrao) {
        this.caminhoPadrao = Objects.requireNonNull(caminhoPadrao, "Caminho padrão não pode ser nulo.");
    }

    protected ArmazenadorBase(String caminhoPadrao) {
        this(Path.of(Objects.requireNonNull(caminhoPadrao, "Caminho padrão não pode ser nulo.")));
    }

    public Path getCaminhoPadrao() {
        return caminhoPadrao;
    }

    public void salvar(T dados) throws IOException {
        Objects.requireNonNull(dados, "Dados para salvamento não podem ser nulos.");
        salvar(this.caminhoPadrao, dados);
    }

    public void salvar(String caminho, T dados) throws IOException {
        Objects.requireNonNull(caminho, "Caminho não pode ser nulo.");
        salvar(Path.of(caminho), dados);
    }

    public T carregar() throws IOException {
        return carregar(this.caminhoPadrao);
    }

    public T carregar(String caminho) throws IOException {
        Objects.requireNonNull(caminho, "Caminho do arquivo não pode ser nulo.");
        return carregar(Path.of(caminho));
    }

    public abstract void salvar(Path caminho, T dados) throws IOException;
    public abstract T carregar(Path caminho) throws IOException;

    protected static void exigirArquivoExistente(Path caminho) {
        Objects.requireNonNull(caminho, "Caminho do arquivo não pode ser nulo.");
        if (!Files.exists(caminho)) {
            throw new IllegalArgumentException("Arquivo de dados não encontrado: " + caminho);
        }
    }
}

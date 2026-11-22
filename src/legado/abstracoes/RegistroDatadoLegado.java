package legado.abstracoes;

import java.time.LocalDate;
import java.util.Objects;

/** Base abstrata simples para entidades legadas que armazenam uma data civil obrigatória. */
public abstract class RegistroDatadoLegado {

    private LocalDate data;

    protected RegistroDatadoLegado(LocalDate data) {
        this.data = Objects.requireNonNull(data, "Data obrigatória.");
    }

    /** Atualiza internamente a data validada (final: seguro para construtores e setCadastro). */
    protected final void definirDataInterna(LocalDate novaData) {
        this.data = Objects.requireNonNull(novaData, "Data obrigatória.");
    }

    public LocalDate getData() {
        return data;
    }

    public void setData(LocalDate data) {
        definirDataInterna(data);
    }
}

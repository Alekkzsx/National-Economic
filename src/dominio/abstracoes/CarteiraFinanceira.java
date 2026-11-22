package dominio.abstracoes;

import dominio.DiaSemana;
import dominio.OperacaoFinanceira;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Base abstrata para estruturas de gestão financeira com atualização em dias úteis,
 * apuração de saldo, extrato polimórfico e persistência em arquivo (Itens 4, 6 e 7).
 */
public abstract class CarteiraFinanceira {

    protected CarteiraFinanceira() {
    }

    /** Atualiza os rendimentos considerando a data atual do sistema (somente dias úteis). */
    public void atualizarAplicacao() {
        atualizarAplicacao(LocalDate.now());
    }

    public abstract void atualizarAplicacao(LocalDate data);
    public abstract BigDecimal getSaldo();
    public abstract List<OperacaoFinanceira> getExtrato();
    public abstract List<OperacaoFinanceira> getExtrato(LocalDate inicio, LocalDate fim);
    public abstract void arquivoSalvarDados(Path caminho) throws IOException;

    /** Sobrecarga conveniente para salvar a partir de um caminho em texto. */
    public void arquivoSalvarDados(String caminho) throws IOException {
        Objects.requireNonNull(caminho, "Caminho do arquivo é obrigatório.");
        arquivoSalvarDados(Path.of(caminho));
    }

    /** Verifica se a data informada é dia útil (segunda a sexta-feira) usando o Item 3. */
    protected static boolean ehDiaUtil(LocalDate data) {
        return new DiaSemana(data).ehDiaUtil();
    }

    /** Ordena cronologicamente as operações e devolve uma visão imutável. */
    protected static List<OperacaoFinanceira> ordenarExtrato(List<OperacaoFinanceira> operacoes) {
        operacoes.sort(Comparator.comparing(OperacaoFinanceira::getData));
        return Collections.unmodifiableList(operacoes);
    }
}

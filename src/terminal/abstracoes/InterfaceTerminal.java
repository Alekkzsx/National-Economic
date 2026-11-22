package terminal.abstracoes;

import terminal.interacao.EntradaTerminal;
import terminal.util.FormatadorTerminal;

import java.util.Objects;

/**
 * Base abstrata para interfaces interativas de console (Template Method).
 *
 * <p>Gerencia o laço principal do menu, o encerramento em fim de entrada (EOF) e o
 * tratamento seguro de exceções, delegando às subclasses apenas exibição e ações.</p>
 */
public abstract class InterfaceTerminal {

    protected final EntradaTerminal entrada;

    protected InterfaceTerminal(EntradaTerminal entrada) {
        this.entrada = Objects.requireNonNull(entrada, "Entrada do terminal é obrigatória.");
    }

    /** Executa o laço interativo até a saída explícita ou encerramento da entrada padrão. */
    public final void executar() {
        boolean emExecucao = true;
        while (emExecucao) {
            try {
                exibirMenu();
                emExecucao = processarOpcao(entrada.lerLinha("Escolha uma opção: ").trim());
            } catch (EntradaTerminal.EndOfInputException e) {
                emExecucao = false;
            } catch (RuntimeException e) {
                tratarErro(e);
            }
        }
        encerrar();
    }

    /** Exibe o menu de opções disponíveis. */
    protected abstract void exibirMenu();

    /** Processa a opção informada; retorna {@code false} para encerrar a sessão. */
    protected abstract boolean processarOpcao(String opcao);

    /** Exibe falhas de execução mascarando eventuais CPFs na mensagem. */
    protected void tratarErro(RuntimeException erro) {
        System.out.println("Não foi possível concluir a operação: " + FormatadorTerminal.mensagemDe(erro));
    }

    /** Gancho executado ao finalizar o laço principal. */
    protected void encerrar() {
    }
}

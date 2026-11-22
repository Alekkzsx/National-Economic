package terminal.interacao;

import dominio.aplicacao.AplicacaoFinanceira;
import dominio.tributacao.OpcaoIOF;
import dominio.tributacao.OpcaoIR;
import dominio.aplicacao.TipoRendimento;
import dominio.aplicacao.TipoResgate;

import java.util.Arrays;

/** Configura os termos de rendimento, resgate e tributação usando os enums de {@code dominio}. */
public final class TermosInvestimento {

    private TermosInvestimento() {
    }

    /** Pergunta ao usuário e aplica os termos personalizados na aplicação financeira. */
    public static void configurar(AplicacaoFinanceira aplicacao, EntradaTerminal entrada) {
        int diasVencimento = entrada.lerInteiro("Dias até o vencimento (0 = sem vencimento): ",
                0, Integer.MAX_VALUE);
        aplicacao.setTempoFinalVencimento(diasVencimento);

        int permanenciaMaxima = diasVencimento == 0 ? Integer.MAX_VALUE : diasVencimento;
        aplicacao.setTempoMinimoPermanencia(
                entrada.lerInteiro("Permanência mínima em dias: ", 0, permanenciaMaxima));

        TipoRendimento[] periodicidades = diasVencimento == 0
                ? Arrays.stream(TipoRendimento.values())
                        .filter(tipo -> tipo != TipoRendimento.NO_PRAZO)
                        .toArray(TipoRendimento[]::new)
                : TipoRendimento.values();

        TipoRendimento rendimento = entrada.lerOpcaoEnum(
                "Periodicidade de rendimento", periodicidades, TipoRendimento::getDescricao);
        aplicacao.setTipoRendimento(rendimento.getCodigo());

        TipoResgate resgate = entrada.lerOpcaoEnum(
                "Frequência de resgate", TipoResgate.values(), TipoResgate::getDescricao);
        aplicacao.setTipoResgate(resgate.getCodigo());

        OpcaoIOF iof = entrada.lerOpcaoEnum(
                "Opção de IOF", OpcaoIOF.values(), OpcaoIOF::getDescricao);
        aplicacao.setIof(iof.getCodigo());

        OpcaoIR ir = entrada.lerOpcaoEnum(
                "Opção de IR", OpcaoIR.values(), OpcaoIR::getDescricao);
        aplicacao.setTipoImpostoRenda(ir.getCodigo());
    }
}

package legado.util;

import dominio.AplicacaoFinanceira;
import dominio.ConvencaoContagemDias;
import dominio.OperacaoFinanceira;
import dominio.RegimeTributario;
import dominio.TipoOperacao;
import legado.modelo.FrequenciaResgate;
import legado.modelo.PeriodicidadeRendimento;

/**
 * Utilitário reutilizável de conversão entre as entidades legadas ({@code legado.modelo})
 * e o modelo acadêmico atual ({@code dominio}).
 */
public final class ConversorLegado {

    private ConversorLegado() {
    }

    /** Converte uma aplicação financeira legada preservando termos, acumuladores e contadores. */
    public static AplicacaoFinanceira converterAplicacao(legado.modelo.AplicacaoFinanceira origem) {
        AplicacaoFinanceira destino = new AplicacaoFinanceira(
                origem.getNome(),
                origem.getNumero(),
                origem.getValorInicial(),
                origem.getTaxaMensal(),
                codigoTipoRendimento(origem.getPeriodicidadeRendimento()),
                origem.getDiasVencimento(),
                origem.getDiasPermanenciaMinima(),
                codigoTipoResgate(origem.getFrequenciaResgate()),
                origem.getOpcaoIOF().ordinal(),
                origem.getOpcaoIR().ordinal(),
                origem.getDataAplicacao()
        );
        destino.setConvencaoContagemDias(converterConvencao(origem.getConvencaoContagemDias()));
        destino.setRegimeTributario(
                origem.getRegimeTributario() == legado.modelo.RegimeTributario.TAX_FREE
                        ? RegimeTributario.ISENTO
                        : RegimeTributario.PADRAO
        );
        destino.setIof(origem.getOpcaoIOF().ordinal());
        destino.setTipoImpostoRenda(origem.getOpcaoIR().ordinal());
        destino.restaurarAcumuladores(
                origem.getSaldoCreditado(),
                origem.getRendimentoCreditado(),
                origem.getRendimentosPendentes(),
                origem.getDiasDesdeRendimento()
        );
        destino.setDataUltimaAtualizacao(origem.getDataUltimaAtualizacao());
        destino.setDiasCorridos(origem.getDiasCorridos());
        destino.setDiasEfetivosCalculo(origem.getDiasEfetivos());
        destino.setValorResgate(origem.getValorResgatado());
        return destino;
    }

    /** Converte um lançamento legado de operação financeira para o domínio atual. */
    public static OperacaoFinanceira converterOperacao(legado.modelo.OperacaoFinanceira origem) {
        TipoOperacao tipo = origem.tipo() == legado.modelo.TipoOperacao.DEPOSIT
                ? TipoOperacao.DEPOSITO
                : TipoOperacao.RETIRADA;
        return new OperacaoFinanceira(origem.data(), origem.valor(), origem.descricao(), tipo);
    }

    public static ConvencaoContagemDias converterConvencao(legado.modelo.ConvencaoContagemDias origem) {
        return switch (origem) {
            case THIRTY_360 -> ConvencaoContagemDias.COMERCIAL_30_360;
            case ACTUAL_365 -> ConvencaoContagemDias.CIVIL_365;
            case ACTUAL_360 -> ConvencaoContagemDias.EFETIVO_360;
        };
    }

    public static int codigoTipoRendimento(PeriodicidadeRendimento periodicidade) {
        return switch (periodicidade) {
            case DAILY -> 0;
            case MONTHLY_30_DAYS -> 1;
            case QUARTERLY_90_DAYS -> 2;
            case HALF_YEARLY_180_DAYS -> 3;
            case YEARLY_365_DAYS -> 4;
            case AT_MATURITY -> 5;
        };
    }

    public static int codigoTipoResgate(FrequenciaResgate frequencia) {
        return switch (frequencia) {
            case DAILY -> 0;
            case MONTHLY -> 1;
            case QUARTERLY -> 2;
            case HALF_YEARLY -> 3;
            case YEARLY -> 4;
        };
    }
}

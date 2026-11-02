package servicos;

import dominio.Cliente;
import dominio.DADOSPESSOAIS;
import dominio.DiaSemana;
import dominio.MultiplasOperacoesFinanceiras;
import servico.SGI;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

/**
 * Converte os dados do formato anterior do terminal para o modelo acadêmico SGI.
 *
 * <p>A conversão mantém os identificadores, os termos financeiros, os acumuladores
 * e o histórico de operações. O estado restaurado é associado às carteiras sem
 * reaplicar lançamentos já contabilizados.</p>
 */
public final class MigradorSistemaInvestimentos {
    private MigradorSistemaInvestimentos() {
    }

    /**
     * Carrega um arquivo SGI ou migra automaticamente um arquivo do serviço anterior.
     *
     * <p>Antes de substituir o formato antigo, preserva uma cópia byte a byte
     * com o sufixo {@code .antes-da-migracao.bak}.</p>
     *
     * @param caminho arquivo a carregar
     * @return sistema carregado ou migrado
     * @throws IOException se nenhum formato suportado puder ser carregado
     */
    public static SGI carregarComMigracao(java.nio.file.Path caminho) throws IOException {
        Objects.requireNonNull(caminho, "Caminho do arquivo é obrigatório.");
        try {
            return SGI.arquivoLerDados(caminho);
        } catch (IOException | RuntimeException falhaLeituraSgi) {
            SistemaInvestimentos legado = SistemaInvestimentos.carregar(caminho);
            SGI migrado = converter(legado);
            java.nio.file.Path backup = caminho.resolveSibling(
                    caminho.getFileName() + ".antes-da-migracao.bak"
            );
            if (!Files.exists(backup)) {
                Files.copy(caminho, backup, StandardCopyOption.COPY_ATTRIBUTES);
            }
            migrado.arquivoSalvarDados(caminho);
            return migrado;
        }
    }

    /**
     * Converte uma instância carregada pelo serviço legado em uma nova instância de SGI.
     *
     * @param legado estado carregado no modelo anterior
     * @return estado equivalente no modelo acadêmico
     */
    public static SGI converter(SistemaInvestimentos legado) {
        Objects.requireNonNull(legado, "Sistema legado é obrigatório.");
        SGI migrado = new SGI();

        for (modelo.ContaCliente clienteLegado : legado.listarClientes()) {
            modelo.DadosPessoais dadosLegados = clienteLegado.getDadosPessoais();
            Cliente cliente = new Cliente(
                    new DADOSPESSOAIS(
                            dadosLegados.getNome(),
                            dadosLegados.getCpf(),
                            dadosLegados.getCelular()
                    ),
                    clienteLegado.getNumeroAgencia(),
                    clienteLegado.getNumeroConta()
            );
            migrado.cadastrarCliente(cliente);

            for (modelo.AplicacaoFinanceira aplicacaoLegada : clienteLegado.listarAplicacoes()) {
                dominio.AplicacaoFinanceira aplicacao = converterAplicacao(aplicacaoLegada);
                MultiplasOperacoesFinanceiras carteira = new MultiplasOperacoesFinanceiras(
                        aplicacao, new DiaSemana(aplicacao.getDataAplicacao())
                );
                for (modelo.OperacaoFinanceira operacaoLegada :
                        legado.listarOperacoes(dadosLegados.getCpf(), aplicacaoLegada.getNumero())) {
                    carteira.vincularOperacaoRestaurada(new dominio.OperacaoFinanceira(
                            operacaoLegada.data(),
                            operacaoLegada.valor(),
                            operacaoLegada.descricao(),
                            operacaoLegada.tipo() == modelo.TipoOperacao.DEPOSIT
                                    ? dominio.TipoOperacao.DEPOSITO
                                    : dominio.TipoOperacao.RETIRADA
                    ));
                }
                migrado.cadastrarAplicacao(dadosLegados.getCpf(), carteira);
            }
        }
        return migrado;
    }

    private static dominio.AplicacaoFinanceira converterAplicacao(modelo.AplicacaoFinanceira origem) {
        dominio.AplicacaoFinanceira destino = new dominio.AplicacaoFinanceira(
                origem.getNome(),
                origem.getNumero(),
                origem.getValorInicial(),
                origem.getTaxaMensal(),
                tipoRendimento(origem.getPeriodicidadeRendimento()),
                origem.getDiasVencimento(),
                origem.getDiasPermanenciaMinima(),
                tipoResgate(origem.getFrequenciaResgate()),
                origem.getOpcaoIOF().ordinal(),
                origem.getOpcaoIR().ordinal(),
                origem.getDataAplicacao()
        );
        destino.setConvencaoContagemDias(switch (origem.getConvencaoContagemDias()) {
            case THIRTY_360 -> dominio.ConvencaoContagemDias.COMERCIAL_30_360;
            case ACTUAL_365 -> dominio.ConvencaoContagemDias.CIVIL_365;
            case ACTUAL_360 -> dominio.ConvencaoContagemDias.EFETIVO_360;
        });
        destino.setRegimeTributario(
                origem.getRegimeTributario() == modelo.RegimeTributario.TAX_FREE
                        ? dominio.RegimeTributario.ISENTO
                        : dominio.RegimeTributario.PADRAO
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

    private static int tipoRendimento(modelo.PeriodicidadeRendimento periodicidade) {
        return switch (periodicidade) {
            case DAILY -> 0;
            case MONTHLY_30_DAYS -> 1;
            case QUARTERLY_90_DAYS -> 2;
            case HALF_YEARLY_180_DAYS -> 3;
            case YEARLY_365_DAYS -> 4;
            case AT_MATURITY -> 5;
        };
    }

    private static int tipoResgate(modelo.FrequenciaResgate frequencia) {
        return switch (frequencia) {
            case DAILY -> 0;
            case MONTHLY -> 1;
            case QUARTERLY -> 2;
            case HALF_YEARLY -> 3;
            case YEARLY -> 4;
        };
    }
}

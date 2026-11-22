package legado.servicos;

import dominio.AplicacaoFinanceira;
import dominio.Cliente;
import dominio.DADOSPESSOAIS;
import dominio.DiaSemana;
import dominio.MultiplasOperacoesFinanceiras;
import legado.modelo.ContaCliente;
import legado.modelo.DadosPessoais;
import legado.util.ConversorLegado;
import servico.SGI;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

/**
 * Converte arquivos salvos no formato anterior do terminal para o modelo acadêmico {@link SGI},
 * preservando um backup byte a byte antes da substituição.
 */
public final class MigradorSistemaInvestimentos {

    private MigradorSistemaInvestimentos() {
    }

    /** Carrega o arquivo no formato SGI atual ou migra automaticamente um arquivo legado. */
    public static SGI carregarComMigracao(Path caminho) throws IOException {
        Objects.requireNonNull(caminho, "Caminho do arquivo é obrigatório.");
        try {
            return SGI.arquivoLerDados(caminho);
        } catch (IOException | RuntimeException falhaLeituraSgi) {
            SistemaInvestimentos legado = SistemaInvestimentos.carregar(caminho);
            SGI migrado = converter(legado);
            Path backup = caminho.resolveSibling(caminho.getFileName() + ".antes-da-migracao.bak");
            if (!Files.exists(backup)) {
                Files.copy(caminho, backup, StandardCopyOption.COPY_ATTRIBUTES);
            }
            migrado.arquivoSalvarDados(caminho);
            return migrado;
        }
    }

    /** Converte uma instância carregada pelo serviço legado em uma nova instância de {@link SGI}. */
    public static SGI converter(SistemaInvestimentos legado) {
        Objects.requireNonNull(legado, "Sistema legado é obrigatório.");
        SGI migrado = new SGI();

        for (ContaCliente clienteLegado : legado.listarClientes()) {
            DadosPessoais dadosLegados = clienteLegado.getDadosPessoais();
            Cliente cliente = new Cliente(
                    new DADOSPESSOAIS(dadosLegados.getNome(), dadosLegados.getCpf(), dadosLegados.getCelular()),
                    clienteLegado.getNumeroAgencia(),
                    clienteLegado.getNumeroConta()
            );
            migrado.cadastrarCliente(cliente);

            for (legado.modelo.AplicacaoFinanceira appLegada : clienteLegado.listarAplicacoes()) {
                AplicacaoFinanceira aplicacao = ConversorLegado.converterAplicacao(appLegada);
                MultiplasOperacoesFinanceiras carteira = new MultiplasOperacoesFinanceiras(
                        aplicacao, new DiaSemana(aplicacao.getDataAplicacao()));

                for (legado.modelo.OperacaoFinanceira opLegada :
                        legado.listarOperacoes(dadosLegados.getCpf(), appLegada.getNumero())) {
                    carteira.vincularOperacaoRestaurada(ConversorLegado.converterOperacao(opLegada));
                }
                migrado.cadastrarAplicacao(dadosLegados.getCpf(), carteira);
            }
        }
        return migrado;
    }
}

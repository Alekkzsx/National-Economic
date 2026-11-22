package mercado.provedores;

import mercado.abstracoes.ProvedorCotacoes;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Consulta no serviço oficial PTAX do Banco Central do Brasil as cotações de
 * venda mais recentes disponíveis para dólar e euro.
 *
 * <p>Como o Banco Central não publica cotações em fins de semana e feriados,
 * cada consulta cobre os dez dias civis anteriores e seleciona a observação
 * mais recente retornada. Para o euro, somente boletins de fechamento são
 * considerados.</p>
 */
public final class ProvedorCotacoesBancoCentral extends ProvedorCotacoes {
    private static final String ENDERECO_BASE =
            "https://olinda.bcb.gov.br/olinda/servico/PTAX/versao/v1/odata/";
    private static final DateTimeFormatter DATA_SERVICO = DateTimeFormatter.ofPattern("MM-dd-yyyy");
    private static final int TIMEOUT_MS = 8_000;
    private static final Pattern OBJETO_JSON = Pattern.compile("\\{([^{}]*)}");
    private static final Pattern COTACAO_VENDA =
            Pattern.compile("\"cotacaoVenda\"\\s*:\\s*([0-9]+(?:\\.[0-9]+)?)");
    private static final Pattern DATA_HORA =
            Pattern.compile("\"dataHoraCotacao\"\\s*:\\s*\"([0-9]{4}-[0-9]{2}-[0-9]{2} [0-9:.]+)\"");
    private static final Pattern TIPO_BOLETIM =
            Pattern.compile("\"tipoBoletim\"\\s*:\\s*\"([^\"]+)\"");

    private final HttpClient clienteHttp;

    /** Cria um cliente HTTP com limite de conexão e redirecionamentos seguros. */
    public ProvedorCotacoesBancoCentral() {
        super("Banco Central do Brasil (PTAX)", TIMEOUT_MS);
        this.clienteHttp = HttpClient.newBuilder()
                .connectTimeout(getTimeout())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    private record Observacao(String dataHora, BigDecimal valor) {
    }

    /**
     * Consulta as cotações PTAX de venda mais recentes para dólar e euro.
     *
     * @return mapa imutável com as chaves {@code USD} e {@code EUR}, em reais
     * @throws IOException se o Banco Central estiver indisponível, não responder
     *                     com sucesso ou não houver cotações recentes
     * @throws InterruptedException se a consulta for interrompida
     */
    @Override
    public Map<String, BigDecimal> obterCotacoes() throws IOException, InterruptedException {
        LocalDate hoje = LocalDate.now();
        // Janela de 10 dias garante cobertura sobre fins de semana e feriados prolongados
        LocalDate inicio = hoje.minusDays(10);
        String dataInicial = DATA_SERVICO.format(inicio);
        String dataFinal = DATA_SERVICO.format(hoje);

        Map<String, BigDecimal> cotacoes = new LinkedHashMap<>();
        cotacoes.put("USD", consultarCotacao(montarConsultaDolar(dataInicial, dataFinal), false));
        // Para o euro, o boletim intermediário pode divergir do fechamento oficial PTAX
        cotacoes.put("EUR", consultarCotacao(montarConsultaEuro(dataInicial, dataFinal), true));
        return Map.copyOf(cotacoes);
    }

    private static String montarConsultaDolar(String dataInicial, String dataFinal) {
        return "CotacaoDolarPeriodo(dataInicial=@dataInicial,dataFinalCotacao=@dataFinalCotacao)"
                + "?@dataInicial=%27" + dataInicial + "%27"
                + "&@dataFinalCotacao=%27" + dataFinal + "%27"
                + "&$top=1000&$orderby=dataHoraCotacao%20desc&$format=json";
    }

    private static String montarConsultaEuro(String dataInicial, String dataFinal) {
        return "CotacaoMoedaPeriodo(moeda=@moeda,dataInicial=@dataInicial,dataFinalCotacao=@dataFinalCotacao)"
                + "?@moeda=%27EUR%27"
                + "&@dataInicial=%27" + dataInicial + "%27"
                + "&@dataFinalCotacao=%27" + dataFinal + "%27"
                + "&$top=1000&$orderby=dataHoraCotacao%20desc&$format=json";
    }

    private BigDecimal consultarCotacao(String caminho, boolean somenteFechamento)
            throws IOException, InterruptedException {
        HttpRequest requisicao = HttpRequest.newBuilder(URI.create(ENDERECO_BASE + caminho))
                .timeout(getTimeout())
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<String> resposta = clienteHttp.send(
                requisicao,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );
        if (resposta.statusCode() != 200) {
            throw new IOException("O Banco Central respondeu com o status HTTP " + resposta.statusCode() + ".");
        }
        return interpretarCotacaoMaisRecente(resposta.body(), somenteFechamento);
    }

    /**
     * Extrai a cotação de venda mais recente de uma resposta PTAX.
     *
     * <p>Usa regex diretamente sobre o JSON OData para manter zero dependências externas
     * e impõe teto de 1 MB na resposta para evitar abuso de memória.</p>
     *
     * @param json resposta JSON do Banco Central
     * @param somenteFechamento quando verdadeiro, ignora boletins intermediários
     * @return cotação de venda positiva mais recente
     * @throws IOException se não houver uma observação de cotação válida
     */
    public static BigDecimal interpretarCotacaoMaisRecente(String json, boolean somenteFechamento) throws IOException {
        if (json == null || json.isBlank() || json.length() > 1_000_000) {
            throw new IOException("Resposta do Banco Central vazia ou acima do limite permitido.");
        }

        Observacao maisRecente = null;
        Matcher objetos = OBJETO_JSON.matcher(json);
        while (objetos.find()) {
            Observacao atual = extrairObservacao(objetos.group(1), somenteFechamento);
            if (atual != null && atual.valor().signum() > 0
                    && (maisRecente == null || atual.dataHora().compareTo(maisRecente.dataHora()) > 0)) {
                maisRecente = atual;
            }
        }

        if (maisRecente == null) {
            throw new IOException("O Banco Central não retornou uma cotação de venda recente válida.");
        }
        return maisRecente.valor();
    }

    private static Observacao extrairObservacao(String objeto, boolean somenteFechamento) throws IOException {
        if (somenteFechamento) {
            Matcher tipo = TIPO_BOLETIM.matcher(objeto);
            if (!tipo.find() || !"Fechamento".equals(tipo.group(1))) {
                return null;
            }
        }

        Matcher cotacao = COTACAO_VENDA.matcher(objeto);
        Matcher dataHora = DATA_HORA.matcher(objeto);
        if (!cotacao.find() || !dataHora.find()) {
            return null;
        }

        try {
            BigDecimal valor = new BigDecimal(cotacao.group(1));
            LocalDate.parse(dataHora.group(1).substring(0, 10));
            return new Observacao(dataHora.group(1), valor);
        } catch (RuntimeException excecao) {
            throw new IOException("O Banco Central retornou uma cotação em formato inválido.", excecao);
        }
    }
}

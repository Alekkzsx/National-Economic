package mercado;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
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
public final class ProvedorCotacoesBancoCentral {
    private static final String ENDERECO_BASE =
            "https://olinda.bcb.gov.br/olinda/servico/PTAX/versao/v1/odata/";
    private static final DateTimeFormatter DATA_SERVICO = DateTimeFormatter.ofPattern("MM-dd-yyyy");
    private static final Duration TEMPO_LIMITE = Duration.ofSeconds(8);
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
        this.clienteHttp = HttpClient.newBuilder()
                .connectTimeout(TEMPO_LIMITE)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /**
     * Consulta as cotações PTAX de venda mais recentes para dólar e euro.
     *
     * @return mapa imutável com as chaves {@code USD} e {@code EUR}, em reais
     * @throws IOException se o Banco Central estiver indisponível, não responder
     *                     com sucesso ou não houver cotações recentes
     * @throws InterruptedException se a consulta for interrompida
     */
    public Map<String, BigDecimal> obterCotacoes() throws IOException, InterruptedException {
        LocalDate hoje = LocalDate.now();
        LocalDate inicio = hoje.minusDays(10);
        String dataInicial = DATA_SERVICO.format(inicio);
        String dataFinal = DATA_SERVICO.format(hoje);

        String consultaDolar = "CotacaoDolarPeriodo(dataInicial=@dataInicial,dataFinalCotacao=@dataFinalCotacao)"
                + "?@dataInicial=%27" + dataInicial + "%27"
                + "&@dataFinalCotacao=%27" + dataFinal + "%27"
                + "&$top=1000&$orderby=dataHoraCotacao%20desc&$format=json";
        String consultaEuro = "CotacaoMoedaPeriodo(moeda=@moeda,dataInicial=@dataInicial,dataFinalCotacao=@dataFinalCotacao)"
                + "?@moeda=%27EUR%27"
                + "&@dataInicial=%27" + dataInicial + "%27"
                + "&@dataFinalCotacao=%27" + dataFinal + "%27"
                + "&$top=1000&$orderby=dataHoraCotacao%20desc&$format=json";

        Map<String, BigDecimal> cotacoes = new LinkedHashMap<>();
        cotacoes.put("USD", consultarCotacao(consultaDolar, false));
        cotacoes.put("EUR", consultarCotacao(consultaEuro, true));
        return Map.copyOf(cotacoes);
    }

    private BigDecimal consultarCotacao(String caminho, boolean somenteFechamento)
            throws IOException, InterruptedException {
        HttpRequest requisicao = HttpRequest.newBuilder(URI.create(ENDERECO_BASE + caminho))
                .timeout(TEMPO_LIMITE)
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
     * @param json resposta JSON do Banco Central
     * @param somenteFechamento quando verdadeiro, ignora boletins intermediários
     * @return cotação de venda positiva mais recente
     * @throws IOException se não houver uma observação de cotação válida
     */
    static BigDecimal interpretarCotacaoMaisRecente(String json, boolean somenteFechamento) throws IOException {
        if (json == null || json.isBlank() || json.length() > 1_000_000) {
            throw new IOException("Resposta do Banco Central vazia ou acima do limite permitido.");
        }

        String dataHoraMaisRecente = null;
        BigDecimal cotacaoMaisRecente = null;
        Matcher objetos = OBJETO_JSON.matcher(json);
        while (objetos.find()) {
            String objeto = objetos.group(1);
            Matcher tipo = TIPO_BOLETIM.matcher(objeto);
            if (somenteFechamento && (!tipo.find() || !"Fechamento".equals(tipo.group(1)))) {
                continue;
            }

            Matcher cotacao = COTACAO_VENDA.matcher(objeto);
            Matcher dataHora = DATA_HORA.matcher(objeto);
            if (!cotacao.find() || !dataHora.find()) {
                continue;
            }

            BigDecimal valor;
            try {
                valor = new BigDecimal(cotacao.group(1));
                LocalDate.parse(dataHora.group(1).substring(0, 10));
            } catch (RuntimeException excecao) {
                throw new IOException("O Banco Central retornou uma cotação em formato inválido.", excecao);
            }
            String dataHoraAtual = dataHora.group(1);
            if (valor.signum() > 0
                    && (dataHoraMaisRecente == null || dataHoraAtual.compareTo(dataHoraMaisRecente) > 0)) {
                dataHoraMaisRecente = dataHoraAtual;
                cotacaoMaisRecente = valor;
            }
        }

        if (cotacaoMaisRecente == null) {
            throw new IOException("O Banco Central não retornou uma cotação de venda recente válida.");
        }
        return cotacaoMaisRecente;
    }
}

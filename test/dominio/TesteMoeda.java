package test.dominio;

import static test.Afirmacao.*;

import dominio.Moeda;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Suíte de testes autônomos para o utilitário {@link Moeda}.
 *
 * <p>Cobre exaustivamente:
 * <ul>
 *   <li>Parsing de representações textuais em notação brasileira e internacional (Desafio 4);</li>
 *   <li>Desambiguação de pontos e vírgulas para milhar e centavos;</li>
 *   <li>Sanitização de prefixos "R$", "$" e espaços normais ou não-quebráveis;</li>
 *   <li>Arredondamento comercial (HALF_UP) e bancário (HALF_EVEN);</li>
 *   <li>Validações estritas de positividade e não-negatividade;</li>
 *   <li>Rejeições de strings nulas, vazias e mal formatadas.</li>
 * </ul>
 */
public final class TesteMoeda {

    public static void main(String[] args) {
        System.out.println(">>> Executando TesteMoeda (Parsing Monetário, Arredondamento e Validações)...");
        long inicio = System.currentTimeMillis();
        int testes = 0;

        testarParsingNotacaoPontoEDecimal(); testes++;
        testarParsingNotacaoBrasileiraComVirgula(); testes++;
        testarParsingComPrefixosESimbolos(); testes++;
        testarParsingSeparadoresMilhar(); testes++;
        testarParsingNumerosNegativos(); testes++;
        testarRejeicoesParsingInvalido(); testes++;
        testarMetodosArredondamento(); testes++;
        testarValidacoesPositivoENaoNegativo(); testes++;
        testarFormatacaoMoedaBrasileira(); testes++;

        long duracao = System.currentTimeMillis() - inicio;
        System.out.printf(">>> TesteMoeda CONCLUÍDO com SUCESSO: %d métodos verificados (%d ms)%n%n", testes, duracao);
    }

    public static void testarParsingNotacaoPontoEDecimal() {
        System.out.println("[TESTE] Parsing com ponto decimal (Desafio 4)...");
        // Notação internacional com 2 casas: "1500.50" -> 1500.50 (NÃO 150050.00!)
        afirmarIgual(new BigDecimal("1500.50"), Moeda.de("1500.50"), "1500.50 deve resultar em 1500.50");
        afirmarIgual(new BigDecimal("1500.50"), Moeda.de("1500.5"), "1500.5 deve expandir para 1500.50");
        afirmarIgual(new BigDecimal("0.75"), Moeda.de("0.75"), "0.75 deve resultar em 0.75");
        afirmarIgual(new BigDecimal("0.50"), Moeda.de(".50"), ".50 deve resultar em 0.50");
        afirmarIgual(new BigDecimal("0.50"), Moeda.de(".5"), ".5 deve resultar em 0.50");
        afirmarIgual(new BigDecimal("100.00"), Moeda.de("100."), "100. com ponto final deve ser 100.00");
        afirmarIgual(new BigDecimal("0.50"), Moeda.de("0.500"), "0.500 deve ser 0.50");
    }

    public static void testarParsingNotacaoBrasileiraComVirgula() {
        System.out.println("[TESTE] Parsing com vírgula decimal brasileira...");
        afirmarIgual(new BigDecimal("1500.50"), Moeda.de("1500,50"), "1500,50 deve ser 1500.50");
        afirmarIgual(new BigDecimal("1500.50"), Moeda.de("1500,5"), "1500,5 deve ser 1500.50");
        afirmarIgual(new BigDecimal("0.75"), Moeda.de("0,75"), "0,75 deve ser 0.75");
        afirmarIgual(new BigDecimal("0.50"), Moeda.de(",50"), ",50 deve ser 0.50");
        afirmarIgual(new BigDecimal("100.00"), Moeda.de("100,"), "100, deve ser 100.00");
    }

    public static void testarParsingComPrefixosESimbolos() {
        System.out.println("[TESTE] Parsing com prefixos de moeda e espaços...");
        afirmarIgual(new BigDecimal("1500.50"), Moeda.de("R$ 1.500,50"), "R$ 1.500,50");
        afirmarIgual(new BigDecimal("1500.50"), Moeda.de("R$1500.50"), "R$1500.50");
        afirmarIgual(new BigDecimal("250.00"), Moeda.de("$ 250.00"), "$ 250.00");
        afirmarIgual(new BigDecimal("1500.50"), Moeda.de("  R$\u00A01500,50  "), "R$ com NBSP");
    }

    public static void testarParsingSeparadoresMilhar() {
        System.out.println("[TESTE] Parsing com separadores de milhar...");
        // Notação brasileira: ponto é milhar, vírgula é decimal
        afirmarIgual(new BigDecimal("1500.50"), Moeda.de("1.500,50"), "1.500,50");
        afirmarIgual(new BigDecimal("1000000.00"), Moeda.de("1.000.000,00"), "1.000.000,00");
        afirmarIgual(new BigDecimal("1000.00"), Moeda.de("1.000"), "1.000 sem decimal é milhar");
        afirmarIgual(new BigDecimal("25000.00"), Moeda.de("25.000"), "25.000 é milhar");

        // Notação internacional: vírgula é milhar, ponto é decimal
        afirmarIgual(new BigDecimal("1500.50"), Moeda.de("1,500.50"), "1,500.50");
        afirmarIgual(new BigDecimal("1000000.00"), Moeda.de("1,000,000.00"), "1,000,000.00");
        afirmarIgual(new BigDecimal("1000000.00"), Moeda.de("1,000,000"), "1,000,000");

        // Apenas inteiro
        afirmarIgual(new BigDecimal("1500.00"), Moeda.de("1500"), "1500 inteiro");
    }

    public static void testarParsingNumerosNegativos() {
        System.out.println("[TESTE] Parsing de valores negativos...");
        afirmarIgual(new BigDecimal("-1500.50"), Moeda.de("-1500.50"), "-1500.50");
        afirmarIgual(new BigDecimal("-1500.50"), Moeda.de("-1.500,50"), "-1.500,50");
        afirmarIgual(new BigDecimal("-50.00"), Moeda.de("-R$ 50,00"), "-R$ 50,00");
    }

    public static void testarRejeicoesParsingInvalido() {
        System.out.println("[TESTE] Rejeição de strings inválidas...");
        afirmarRejeicao(() -> Moeda.de(null), NullPointerException.class, "nulo");
        afirmarRejeicao(() -> Moeda.de(""), IllegalArgumentException.class, "vazio");
        afirmarRejeicao(() -> Moeda.de("   "), IllegalArgumentException.class, "espaços");
        afirmarRejeicao(() -> Moeda.de("abc"), IllegalArgumentException.class, "letras");
        afirmarRejeicao(() -> Moeda.de("R$"), IllegalArgumentException.class, "apenas cifrão");
    }

    public static void testarMetodosArredondamento() {
        System.out.println("[TESTE] Arredondamento comercial e bancário...");
        BigDecimal valor1 = new BigDecimal("10.555");
        afirmarIgual(new BigDecimal("10.56"), Moeda.arredondar(valor1), "HALF_UP padrão");
        afirmarIgual(new BigDecimal("10.56"), Moeda.arredondar(valor1, RoundingMode.HALF_UP), "HALF_UP explícito");

        // Banker's Rounding: 2.525 -> 2.52 (arredonda para o par mais próximo)
        BigDecimal valor2 = new BigDecimal("2.525");
        afirmarIgual(new BigDecimal("2.52"), Moeda.arredondarBancario(valor2), "HALF_EVEN para par");

        // de(double)
        afirmarIgual(new BigDecimal("123.45"), Moeda.de(123.45), "de(double)");
    }

    public static void testarValidacoesPositivoENaoNegativo() {
        System.out.println("[TESTE] Validações de positividade e não-negatividade...");
        afirmarIgual(new BigDecimal("10.00"), Moeda.positivo(new BigDecimal("10.00"), "aporte"), "positivo");
        afirmarRejeicao(() -> Moeda.positivo(BigDecimal.ZERO, "aporte"), IllegalArgumentException.class, "zero não é positivo");
        afirmarRejeicao(() -> Moeda.positivo(new BigDecimal("-1.00"), "aporte"), IllegalArgumentException.class, "negativo não é positivo");

        afirmarIgual(new BigDecimal("0.00"), Moeda.naoNegativo(BigDecimal.ZERO, "saldo"), "zero é não-negativo");
        afirmarIgual(new BigDecimal("15.00"), Moeda.naoNegativo(new BigDecimal("15.00"), "saldo"), "positivo é não-negativo");
        afirmarRejeicao(() -> Moeda.naoNegativo(new BigDecimal("-0.01"), "saldo"), IllegalArgumentException.class, "negativo rejeitado");
    }

    public static void testarFormatacaoMoedaBrasileira() {
        System.out.println("[TESTE] Formatação monetária oficial pt-BR...");
        String formatado = Moeda.formatar(new BigDecimal("1500.50"));
        afirmarVerdadeiro(formatado.contains("1.500,50"), "deve conter separadores pt-BR '1.500,50'");
        afirmarVerdadeiro(formatado.contains("R$"), "deve conter prefixo R$");
    }
}

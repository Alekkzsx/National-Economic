package terminal.util;

import dominio.util.Moeda;
import servico.sistema.SGI;
import terminal.interacao.EntradaTerminal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Utilitário reutilizável do terminal para integrar cotações ({@code SELICRA}, {@code CDIR},
 * {@code USD}, {@code EUR}) às operações financeiras e consultas do CRUD (Item 9).
 */
public final class AuxiliarCotacoes {

    private static final BigDecimal DIVISOR_ANUAL_PARA_MENSAL = BigDecimal.valueOf(1200);

    private AuxiliarCotacoes() {
    }

    /**
     * Converte taxa anual em % (ex.: {@code 10.50} para 10,50% a.a.) em taxa mensal fracionária
     * linear ({@code taxaAnual / 1200}), coerente com o mês comercial de 30 dias do domínio.
     */
    public static BigDecimal taxaMensalDeAnualPercentual(BigDecimal taxaAnualPercentual) {
        if (taxaAnualPercentual == null || taxaAnualPercentual.signum() < 0) {
            throw new IllegalArgumentException("Taxa anual deve ser não negativa.");
        }
        if (taxaAnualPercentual.signum() == 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal mensal = taxaAnualPercentual
                .divide(DIVISOR_ANUAL_PARA_MENSAL, 6, RoundingMode.HALF_UP)
                .stripTrailingZeros();
        if (mensal.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("A taxa mensal resultante não pode ultrapassar 1 (100% a.m.).");
        }
        return mensal;
    }

    /** Converte um valor em moeda estrangeira (USD/EUR) para reais (BRL) arredondado em centavos. */
    public static BigDecimal converterParaReal(BigDecimal valorEstrangeiro, BigDecimal cotacao) {
        if (valorEstrangeiro == null || valorEstrangeiro.signum() <= 0) {
            throw new IllegalArgumentException("O valor em moeda estrangeira deve ser positivo.");
        }
        if (cotacao == null || cotacao.signum() <= 0) {
            throw new IllegalArgumentException("A cotação da moeda deve ser positiva.");
        }
        return Moeda.arredondar(valorEstrangeiro.multiply(cotacao));
    }

    /** Converte um valor em reais (BRL) para moeda estrangeira usando a cotação informada. */
    public static BigDecimal converterDeReal(BigDecimal valorEmReal, BigDecimal cotacao) {
        if (valorEmReal == null) {
            throw new IllegalArgumentException("O valor em reais é obrigatório.");
        }
        if (cotacao == null || cotacao.signum() <= 0) {
            throw new IllegalArgumentException("A cotação da moeda deve ser positiva.");
        }
        return valorEmReal.divide(cotacao, 2, RoundingMode.HALF_UP);
    }

    private record OpcaoTaxa(String rotulo, BigDecimal taxaMensal) { }

    private record OpcaoMoeda(String codigo, String rotulo, BigDecimal cotacao) { }

    /** Lê a taxa mensal permitindo escolher SELICRA ou CDIR quando carregadas no SGI. */
    public static BigDecimal lerTaxaMensal(EntradaTerminal entrada, SGI sistema) {
        Optional<BigDecimal> selicra = sistema.getCotacao("SELICRA");
        Optional<BigDecimal> cdir = sistema.getCotacao("CDIR");
        if (selicra.isEmpty() && cdir.isEmpty()) {
            return entrada.lerTaxaMensal();
        }

        List<OpcaoTaxa> opcoes = new ArrayList<>();
        opcoes.add(new OpcaoTaxa("1 = informar manualmente", null));
        selicra.ifPresent(valor -> adicionarOpcaoTaxa(opcoes, "SELICRA", valor));
        cdir.ifPresent(valor -> adicionarOpcaoTaxa(opcoes, "CDIR", valor));

        List<String> rotulos = opcoes.stream().map(OpcaoTaxa::rotulo).toList();
        int escolha = entrada.lerInteiroComPadrao(
                "Origem da taxa (" + String.join(", ", rotulos) + ") [1]: ",
                1, opcoes.size(), 1);
        BigDecimal selecionada = opcoes.get(escolha - 1).taxaMensal();
        return selecionada == null ? entrada.lerTaxaMensal() : selecionada;
    }

    private static void adicionarOpcaoTaxa(List<OpcaoTaxa> opcoes, String nome, BigDecimal taxaAnual) {
        BigDecimal mensal = taxaMensalDeAnualPercentual(taxaAnual);
        String rotulo = (opcoes.size() + 1) + " = " + nome + " ("
                + FormatadorTerminal.formatarTaxaAnual(taxaAnual) + " -> " + mensal + "/mês)";
        opcoes.add(new OpcaoTaxa(rotulo, mensal));
    }

    /** Lê um valor positivo em BRL, USD ou EUR (quando cotações ativas), convertendo para BRL. */
    public static BigDecimal lerValorEmReal(EntradaTerminal entrada, SGI sistema, String prompt) {
        Optional<BigDecimal> usd = sistema.getCotacao("USD");
        Optional<BigDecimal> eur = sistema.getCotacao("EUR");
        if (usd.isEmpty() && eur.isEmpty()) {
            return entrada.lerValorPositivo(prompt);
        }

        List<OpcaoMoeda> opcoes = new ArrayList<>();
        opcoes.add(new OpcaoMoeda("BRL", "1 = BRL", null));
        usd.ifPresent(c -> adicionarOpcaoMoeda(opcoes, "USD", c));
        eur.ifPresent(c -> adicionarOpcaoMoeda(opcoes, "EUR", c));

        List<String> rotulos = opcoes.stream().map(OpcaoMoeda::rotulo).toList();
        int escolha = entrada.lerInteiroComPadrao(
                "Moeda (" + String.join(", ", rotulos) + ") [1]: ",
                1, opcoes.size(), 1);
        OpcaoMoeda selecionada = opcoes.get(escolha - 1);
        if (selecionada.cotacao() == null) {
            return entrada.lerValorPositivo(prompt);
        }

        while (true) {
            BigDecimal valorEstrangeiro = entrada.lerValorPositivo(
                    prompt.replace(": ", " em " + selecionada.codigo() + ": "));
            BigDecimal emReal = converterParaReal(valorEstrangeiro, selecionada.cotacao());
            if (emReal.signum() > 0) {
                System.out.printf("Convertido: %s %s -> %s (cotação %s)%n",
                        selecionada.codigo(), valorEstrangeiro, FormatadorTerminal.formatarMoeda(emReal),
                        FormatadorTerminal.formatarCambio(selecionada.cotacao()));
                return emReal;
            }
            System.out.println("O valor convertido em reais é menor que R$ 0.01. Informe um valor maior.");
        }
    }

    private static void adicionarOpcaoMoeda(List<OpcaoMoeda> opcoes, String codigo, BigDecimal cotacao) {
        String rotulo = (opcoes.size() + 1) + " = " + codigo + " (" + FormatadorTerminal.formatarCambio(cotacao) + ")";
        opcoes.add(new OpcaoMoeda(codigo, rotulo, cotacao));
    }

    /** Exibe todas as taxas e cotações recebidas do servidor no formato adequado de cada indicador. */
    public static void exibirCotacoes(Map<String, BigDecimal> cotacoes) {
        System.out.println("Cotações carregadas no SGI:");
        for (Map.Entry<String, BigDecimal> item : cotacoes.entrySet()) {
            String chave = item.getKey();
            String valorFormatado = ("SELICRA".equals(chave) || "CDIR".equals(chave))
                    ? FormatadorTerminal.formatarTaxaAnual(item.getValue())
                    : FormatadorTerminal.formatarCambio(item.getValue());
            System.out.printf("- %s: %s%n", chave, valorFormatado);
        }
    }

    /** Retorna um resumo de 1 linha das cotações ativas para o cabeçalho do menu (ou vazio). */
    public static String resumoStatus(SGI sistema) {
        List<String> partes = new ArrayList<>();
        sistema.getCotacao("SELICRA").ifPresent(v -> partes.add("SELICRA " + FormatadorTerminal.formatarTaxaAnual(v)));
        sistema.getCotacao("CDIR").ifPresent(v -> partes.add("CDIR " + FormatadorTerminal.formatarTaxaAnual(v)));
        sistema.getCotacao("USD").ifPresent(v -> partes.add("USD " + FormatadorTerminal.formatarCambio(v)));
        sistema.getCotacao("EUR").ifPresent(v -> partes.add("EUR " + FormatadorTerminal.formatarCambio(v)));
        return partes.isEmpty() ? "" : "Cotações ativas: " + String.join(" | ", partes);
    }

    /** Exibe equivalência em USD e EUR de um saldo em reais quando as cotações estiverem carregadas. */
    public static void exibirEquivalenteCambial(BigDecimal saldoEmReal, SGI sistema) {
        List<String> equivalentes = new ArrayList<>();
        sistema.getCotacao("USD").filter(c -> c.signum() > 0)
                .ifPresent(c -> equivalentes.add("US$ " + converterDeReal(saldoEmReal, c)));
        sistema.getCotacao("EUR").filter(c -> c.signum() > 0)
                .ifPresent(c -> equivalentes.add("€ " + converterDeReal(saldoEmReal, c)));
        if (!equivalentes.isEmpty()) {
            System.out.println("Equivalente cambial: " + String.join(" | ", equivalentes));
        }
    }
}

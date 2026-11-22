package terminal.util;

import dominio.Moeda;
import servico.SGI;
import terminal.EntradaTerminal;

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

    /** Lê a taxa mensal permitindo escolher SELICRA ou CDIR quando carregadas no SGI. */
    public static BigDecimal lerTaxaMensal(EntradaTerminal entrada, SGI sistema) {
        Optional<BigDecimal> selicra = sistema.getCotacao("SELICRA");
        Optional<BigDecimal> cdir = sistema.getCotacao("CDIR");
        if (selicra.isEmpty() && cdir.isEmpty()) {
            return entrada.lerTaxaMensal();
        }

        List<String> opcoes = new ArrayList<>();
        List<BigDecimal> taxasMensais = new ArrayList<>();
        opcoes.add("1 = informar manualmente");
        taxasMensais.add(null);

        if (selicra.isPresent()) {
            BigDecimal mensal = taxaMensalDeAnualPercentual(selicra.get());
            opcoes.add((opcoes.size() + 1) + " = SELICRA ("
                    + FormatadorTerminal.formatarTaxaAnual(selicra.get()) + " -> " + mensal + "/mês)");
            taxasMensais.add(mensal);
        }
        if (cdir.isPresent()) {
            BigDecimal mensal = taxaMensalDeAnualPercentual(cdir.get());
            opcoes.add((opcoes.size() + 1) + " = CDIR ("
                    + FormatadorTerminal.formatarTaxaAnual(cdir.get()) + " -> " + mensal + "/mês)");
            taxasMensais.add(mensal);
        }

        int escolha = entrada.lerInteiroComPadrao(
                "Origem da taxa (" + String.join(", ", opcoes) + ") [1]: ",
                1, opcoes.size(), 1);
        BigDecimal selecionada = taxasMensais.get(escolha - 1);
        return selecionada == null ? entrada.lerTaxaMensal() : selecionada;
    }

    /** Lê um valor positivo em BRL, USD ou EUR (quando cotações ativas), convertendo para BRL. */
    public static BigDecimal lerValorEmReal(EntradaTerminal entrada, SGI sistema, String prompt) {
        Optional<BigDecimal> usd = sistema.getCotacao("USD");
        Optional<BigDecimal> eur = sistema.getCotacao("EUR");
        if (usd.isEmpty() && eur.isEmpty()) {
            return entrada.lerValorPositivo(prompt);
        }

        List<String> rotulos = new ArrayList<>();
        List<String> codigos = new ArrayList<>();
        List<BigDecimal> cotacoes = new ArrayList<>();
        rotulos.add("1 = BRL");
        codigos.add("BRL");
        cotacoes.add(null);

        if (usd.isPresent()) {
            rotulos.add((rotulos.size() + 1) + " = USD (" + FormatadorTerminal.formatarCambio(usd.get()) + ")");
            codigos.add("USD");
            cotacoes.add(usd.get());
        }
        if (eur.isPresent()) {
            rotulos.add((rotulos.size() + 1) + " = EUR (" + FormatadorTerminal.formatarCambio(eur.get()) + ")");
            codigos.add("EUR");
            cotacoes.add(eur.get());
        }

        int escolha = entrada.lerInteiroComPadrao(
                "Moeda (" + String.join(", ", rotulos) + ") [1]: ",
                1, rotulos.size(), 1);
        BigDecimal cotacao = cotacoes.get(escolha - 1);
        if (cotacao == null) {
            return entrada.lerValorPositivo(prompt);
        }

        String codigo = codigos.get(escolha - 1);
        while (true) {
            BigDecimal valorEstrangeiro = entrada.lerValorPositivo(prompt.replace(": ", " em " + codigo + ": "));
            BigDecimal emReal = converterParaReal(valorEstrangeiro, cotacao);
            if (emReal.signum() > 0) {
                System.out.printf("Convertido: %s %s -> %s (cotação %s)%n",
                        codigo, valorEstrangeiro, FormatadorTerminal.formatarMoeda(emReal),
                        FormatadorTerminal.formatarCambio(cotacao));
                return emReal;
            }
            System.out.println("O valor convertido em reais é menor que R$ 0.01. Informe um valor maior.");
        }
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

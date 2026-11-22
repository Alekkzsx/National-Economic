package test.terminal;

import static test.util.Afirmacao.*;

import dominio.aplicacao.AplicacaoFinanceira;
import dominio.aplicacao.TipoRendimento;
import servico.sistema.SGI;
import terminal.interacao.EntradaTerminal;
import terminal.interacao.TermosInvestimento;
import terminal.abstracoes.InterfaceTerminal;
import terminal.util.AuxiliarCotacoes;
import terminal.util.FormatadorTerminal;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Scanner;

/**
 * Testes autônomos da camada de interface CLI (Item 8 e integração com Item 9):
 * leitura validada, formatação com máscara LGPD, configuração de termos,
 * auxiliar de cotações e {@link InterfaceTerminal}.
 */
public final class TesteEntradaTerminal {

    private TesteEntradaTerminal() {
    }

    public static void main(String[] args) {
        PrintStream saidaOriginal = System.out;
        ByteArrayOutputStream captura = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(captura, true, StandardCharsets.UTF_8));

            testarEntradaBasicaEPadroes();
            testarFormatadorTerminal();
            testarTermosInvestimentoComDominio();
            testarAuxiliarCotacoes();
            testarInterfaceTerminalTemplateMethod();
        } finally {
            System.setOut(saidaOriginal);
        }
    }

    private static void testarEntradaBasicaEPadroes() {
        EntradaTerminal entrada = new EntradaTerminal(new Scanner(
                " \nNome\n1250,50\nsim\nmensal\n\n\n"));

        afirmarIgual("Nome", entrada.lerObrigatorio("Nome: "), "lerObrigatorio ignora linha em branco");
        afirmarIgual(new BigDecimal("1250.50"), entrada.lerValorPositivo("Valor: "), "aceita vírgula decimal");
        afirmarVerdadeiro(entrada.lerSimNao("Continuar? "), "aceita 'sim'");

        TipoRendimento rendimento = entrada.lerOpcaoEnum(
                "Rendimento", TipoRendimento.values(), TipoRendimento::getDescricao);
        afirmarIgual(TipoRendimento.MENSAL, rendimento, "aceita descrição localizada do enum de domínio");

        afirmarIgual("localhost", entrada.lerLinhaComPadrao("Host: ", "localhost"), "Enter aceita texto padrão");
        afirmarIgual(5050, entrada.lerInteiroComPadrao("Porta: ", 1, 65535, 5050), "Enter aceita inteiro padrão");
    }

    private static void testarFormatadorTerminal() {
        String cpf = "123.456.789-09";
        afirmarFalso(FormatadorTerminal.mascararCpf(cpf).contains("123"), "CPF mascarado oculta início");
        String mensagemSegura = FormatadorTerminal.mensagemDe(new IllegalArgumentException("CPF " + cpf + " inválido"));
        afirmarFalso(mensagemSegura.contains(cpf), "mensagemDe remove CPF completo");
        afirmarVerdadeiro(mensagemSegura.contains("***.***.***-**"), "mensagemDe substitui por máscara");
        afirmarIgual("10.50% a.a.", FormatadorTerminal.formatarTaxaAnual(new BigDecimal("10.5")), "taxa anual");
        afirmarIgual("R$ 5.1842", FormatadorTerminal.formatarCambio(new BigDecimal("5.1842")), "câmbio 4 casas");
    }

    private static void testarTermosInvestimentoComDominio() {
        AplicacaoFinanceira app = new AplicacaoFinanceira(
                "Tesouro", "APP-T", new BigDecimal("1000.00"), new BigDecimal("0.01"));
        // 180 dias vencimento, 30 permanência, 6=NO_PRAZO, 2=MENSAL resgate, 1=ISENTO IOF, 1=ISENTO IR
        EntradaTerminal entrada = new EntradaTerminal(new Scanner("180\n30\n6\n2\n1\n1\n"));
        TermosInvestimento.configurar(app, entrada);

        afirmarIgual(180, app.getTempoFinalVencimento(), "vencimento configurado");
        afirmarIgual(30, app.getTempoMinimoPermanencia(), "permanência configurada");
        afirmarIgual(5, app.getTipoRendimento(), "NO_PRAZO mapeado para código 5");
        afirmarIgual(1, app.getTipoResgate(), "resgate mensal mapeado para código 1");
        afirmarIgual(0, app.getIof(), "IOF isento mapeado para código 0");
        afirmarIgual(0, app.getTipoImpostoRenda(), "IR isento mapeado para código 0");
    }

    private static void testarAuxiliarCotacoes() {
        afirmarIgual(new BigDecimal("0.00875"),
                AuxiliarCotacoes.taxaMensalDeAnualPercentual(new BigDecimal("10.50")),
                "10.50% a.a. / 1200 = 0.00875");
        afirmarIgual(new BigDecimal("518.00"),
                AuxiliarCotacoes.converterParaReal(new BigDecimal("100.00"), new BigDecimal("5.18")),
                "USD 100 a 5.18 = R$ 518.00");
        afirmarIgual(new BigDecimal("100.00"),
                AuxiliarCotacoes.converterDeReal(new BigDecimal("518.00"), new BigDecimal("5.18")),
                "R$ 518.00 / 5.18 = USD 100.00");

        SGI sgi = new SGI();
        sgi.atualizarCotacoes(Map.of(
                "SELICRA", new BigDecimal("12.00"),
                "CDIR", new BigDecimal("6.00"),
                "USD", new BigDecimal("5.00"),
                "EUR", new BigDecimal("6.00")
        ));

        // Escolhe 2 (SELICRA = 12% a.a. -> 0.01/mês) e depois moeda 2 (USD) com valor 200 (= R$ 1000.00)
        EntradaTerminal entrada = new EntradaTerminal(new Scanner("2\n2\n200\n"));
        afirmarIgual(new BigDecimal("0.01"), AuxiliarCotacoes.lerTaxaMensal(entrada, sgi), "taxa SELICRA");
        afirmarIgual(new BigDecimal("1000.00"),
                AuxiliarCotacoes.lerValorEmReal(entrada, sgi, "Valor: "), "valor USD convertido para BRL");
        afirmarVerdadeiro(AuxiliarCotacoes.resumoStatus(sgi).contains("SELICRA 12.00% a.a."),
                "resumoStatus inclui SELICRA formatada");
    }

    private static void testarInterfaceTerminalTemplateMethod() {
        int[] chamadas = {0, 0}; // [opcoesProcessadas, encerrarChamado]
        EntradaTerminal entrada = new EntradaTerminal(new Scanner("ok\nerro\n0\n"));
        InterfaceTerminal menu = new InterfaceTerminal(entrada) {
            @Override
            protected void exibirMenu() {
            }

            @Override
            protected boolean processarOpcao(String opcao) {
                chamadas[0]++;
                if ("erro".equals(opcao)) {
                    throw new IllegalArgumentException("CPF 529.982.247-25 falhou");
                }
                return !"0".equals(opcao);
            }

            @Override
            protected void encerrar() {
                chamadas[1]++;
            }
        };
        menu.executar();
        afirmarIgual(3, chamadas[0], "Template Method continuou após RuntimeException até opção 0");
        afirmarIgual(1, chamadas[1], "Template Method chamou encerrar() ao finalizar");
    }
}

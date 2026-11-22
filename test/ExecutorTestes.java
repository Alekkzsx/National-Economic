package test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import test.util.Afirmacao;

/**
 * Executor mestre das 13 suítes de testes autônomos do SGI.
 *
 * <p>Executa sequencialmente todas as suítes organizadas por camada arquitetural
 * (Domínio, Serviço, Mercado, Terminal e Integração), coleta tempo e quantidade de
 * asserções por suíte e retorna código 0 (100% PASS) ou 1 (falha/pendência).</p>
 */
public final class ExecutorTestes {

    /** Registro de execução de uma suíte de teste individual. */
    private static final class ResultadoSuite {
        final String nome;
        final boolean sucesso;
        final long duracaoMs;
        final long assercoes;
        final Throwable erro;

        ResultadoSuite(String nome, boolean sucesso, long duracaoMs, long assercoes, Throwable erro) {
            this.nome = nome;
            this.sucesso = sucesso;
            this.duracaoMs = duracaoMs;
            this.assercoes = assercoes;
            this.erro = erro;
        }
    }

    /** Executa todas as suítes registradas e imprime o relatório consolidado. */
    public static void main(String[] args) {
        System.out.println("================================================================================");
        System.out.println("           SGI — SUÍTE DE TESTES AUTÔNOMOS UNIFICADA (JAVA SE PURO)             ");
        System.out.println("================================================================================");
        System.out.println();

        Afirmacao.zerarContador();
        long inicioGeral = System.currentTimeMillis();
        List<ResultadoSuite> resultados = new ArrayList<>();

        // Suítes agrupadas por camada arquitetural (espelhando src/ + integração)
        String[] suites = new String[] {
            // Domínio (Itens 1 a 6)
            "test.dominio.util.TesteMoeda",
            "test.dominio.calendario.TesteDiaSemana",
            "test.dominio.operacao.TesteOperacaoFinanceira",
            "test.dominio.aplicacao.TesteAplicacaoFinanceira",
            "test.dominio.operacao.TesteMultiplasOperacoes",
            "test.dominio.cliente.TesteDadosPessoais",
            "test.dominio.cliente.TesteCliente",
            // Serviço (Item 7)
            "test.servico.TesteSGI",
            // Mercado / Cotações (Item 9)
            "test.mercado.provedores.TesteProvedorCotacoesBancoCentral",
            "test.mercado.servidor.TesteServidorCotacoes",
            // Terminal CLI (Item 8)
            "test.terminal.TesteEntradaTerminal",
            // Integração ponta a ponta e retrocompatibilidade legado
            "test.integracao.TesteIntegracaoCotacoesSGI",
            "test.integracao.TesteSistemaInvestimentos"
        };

        int executadas = 0;
        int aprovadas = 0;
        int falhadas = 0;
        int ignoradas = 0;

        for (String nomeClasse : suites) {
            Class<?> clazz;
            try {
                clazz = Class.forName(nomeClasse);
            } catch (ClassNotFoundException e) {
                ignoradas++;
                continue;
            }

            executadas++;
            long inicioSuite = System.currentTimeMillis();
            long assercoesAntes = Afirmacao.getTotalAfirmacoes();
            try {
                Method mainMethod = clazz.getMethod("main", String[].class);
                mainMethod.invoke(null, (Object) new String[0]);
                long duracao = System.currentTimeMillis() - inicioSuite;
                long assercoesSuite = Afirmacao.getTotalAfirmacoes() - assercoesAntes;
                resultados.add(new ResultadoSuite(nomeClasse, true, duracao, assercoesSuite, null));
                aprovadas++;
            } catch (Throwable t) {
                long duracao = System.currentTimeMillis() - inicioSuite;
                long assercoesSuite = Afirmacao.getTotalAfirmacoes() - assercoesAntes;
                Throwable causa = t.getCause() != null ? t.getCause() : t;
                resultados.add(new ResultadoSuite(nomeClasse, false, duracao, assercoesSuite, causa));
                falhadas++;
                System.err.printf("[FALHA] Suíte %s falhou após %d ms: %s%n",
                        nomeClasse, duracao, causa.getMessage());
            }
        }

        long duracaoTotal = System.currentTimeMillis() - inicioGeral;

        System.out.println();
        System.out.println("================================================================================");
        System.out.println("                     RESUMO CONSOLIDADO DA EXECUÇÃO                            ");
        System.out.println("================================================================================");
        for (ResultadoSuite res : resultados) {
            String status = res.sucesso ? "[PASSOU]" : "[FALHOU]";
            System.out.printf(" %-8s %-45s %4d asserc. (%d ms)%n",
                    status, res.nome, res.assercoes, res.duracaoMs);
            if (!res.sucesso && res.erro != null) {
                System.out.printf("          Erro: %s%n", res.erro.toString());
            }
        }
        System.out.println("--------------------------------------------------------------------------------");
        System.out.printf("Total de Suítes Executadas: %d | Aprovadas: %d | Falhas: %d | Pendentes: %d%n",
                executadas, aprovadas, falhadas, ignoradas);
        System.out.printf("Total de Asserções Validadas: %d%n", Afirmacao.getTotalAfirmacoes());
        System.out.printf("Tempo Total de Execução: %d ms%n", duracaoTotal);
        System.out.println("================================================================================");

        if (falhadas > 0 || ignoradas > 0) {
            System.err.println(">>> RESULTADO GERAL: FALHA.");
            System.exit(1);
        } else {
            System.out.println(">>> RESULTADO GERAL: SUCESSO INTEGRAL (100% PASS).");
            System.exit(0);
        }
    }
}

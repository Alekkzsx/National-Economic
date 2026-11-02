import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import test.Afirmacao;

/**
 * Executor mestre de todos os testes autônomos do SGI (Requisito R6).
 *
 * <p>Executa de forma unificada e sequencial todas as suítes de teste disponíveis
 * no repositório, coleta estatísticas de tempo e assertividade, e emite um relatório
 * consolidado no terminal, finalizando com código de saída 0 (sucesso) ou 1 (falha).</p>
 */
public final class ExecutorTestes {

    /** Registro de execução de uma suíte de teste individual. */
    private static final class ResultadoSuite {
        final String nome;
        final boolean sucesso;
        final long duracaoMs;
        final Throwable erro;

        ResultadoSuite(String nome, boolean sucesso, long duracaoMs, Throwable erro) {
            this.nome = nome;
            this.sucesso = sucesso;
            this.duracaoMs = duracaoMs;
            this.erro = erro;
        }
    }

    public static void main(String[] args) {
        System.out.println("================================================================================");
        System.out.println("           SGI — SUÍTE DE TESTES AUTÔNOMOS UNIFICADA (JAVA SE PURO)             ");
        System.out.println("================================================================================");
        System.out.println();

        Afirmacao.zerarContador();
        long inicioGeral = System.currentTimeMillis();
        List<ResultadoSuite> resultados = new ArrayList<>();

        // Relação de suítes de teste a serem executadas
        String[] suites = new String[] {
            "test.dominio.TesteMoeda",
            "test.dominio.TesteDiaSemana",
            "test.dominio.TesteOperacaoFinanceira",
            "test.dominio.TesteAplicacaoFinanceira",
            "test.dominio.TesteMultiplasOperacoes",
            "test.dominio.TesteDadosPessoais",
            "test.dominio.TesteCliente",
            "test.servico.TesteSGI",
            "TesteSistemaInvestimentos",
            "terminal.TesteEntradaTerminal"
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
                // Suíte ainda não compilada/implementada (milestones futuros)
                ignoradas++;
                continue;
            }

            executadas++;
            long inicioSuite = System.currentTimeMillis();
            try {
                Method mainMethod = clazz.getMethod("main", String[].class);
                mainMethod.invoke(null, (Object) new String[0]);
                long duracao = System.currentTimeMillis() - inicioSuite;
                resultados.add(new ResultadoSuite(nomeClasse, true, duracao, null));
                aprovadas++;
            } catch (Throwable t) {
                long duracao = System.currentTimeMillis() - inicioSuite;
                Throwable causa = t.getCause() != null ? t.getCause() : t;
                resultados.add(new ResultadoSuite(nomeClasse, false, duracao, causa));
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
            System.out.printf(" %-8s %-45s (%d ms)%n", status, res.nome, res.duracaoMs);
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

        if (falhadas > 0) {
            System.err.println(">>> RESULTADO GERAL: FALHA.");
            System.exit(1);
        } else {
            System.out.println(">>> RESULTADO GERAL: SUCESSO INTEGRAL (100% PASS).");
            System.exit(0);
        }
    }
}

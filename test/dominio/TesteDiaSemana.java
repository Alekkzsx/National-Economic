package test.dominio;

import static test.Afirmacao.*;

import dominio.DiaSemana;
import java.time.LocalDate;

/**
 * Suíte de testes autônomos para a classe {@link DiaSemana} (Item 3, Requisito R1.3).
 *
 * <p>Cobre exaustivamente o algoritmo aritmético canônico de Congruência de Zeller
 * (índices 0=Sábado a 6=Sexta), polimorfismo com métodos sobrecarregados, validação de
 * dias úteis, rejeição de datas civis inválidas, encapsulamento e segurança de herança.</p>
 */
public final class TesteDiaSemana {

    public static void main(String[] args) {
        System.out.println(">>> Executando TesteDiaSemana (Item 3 - Algoritmo Zeller e Calendário)...");
        long inicio = System.currentTimeMillis();
        int testesExecutados = 0;

        testarInstanciacaoEGettersSetters(); testesExecutados++;
        testarAlgoritmoZellerSeteDias(); testesExecutados++;
        testarAlgoritmoZellerJaneiroFevereiro(); testesExecutados++;
        testarAlgoritmoZellerAnosBissextosEViradasDeSeculo(); testesExecutados++;
        testarPolimorfismoDiaSemana(); testesExecutados++;
        testarPolimorfismoNomeDiaSemana(); testesExecutados++;
        testarPolimorfismoEhDiaUtil(); testesExecutados++;
        testarEhDiaUtilFimDeSemana(); testesExecutados++;
        testarSetCadastroDataValida(); testesExecutados++;
        testarSetCadastroDataInvalida(); testesExecutados++;
        testarSobrescritaToString(); testesExecutados++;
        testarHerancaSegura(); testesExecutados++;
        testarSimulacaoCicloMensal(); testesExecutados++;
        testarAliasesLegadosCompatibilidade(); testesExecutados++;

        long duracao = System.currentTimeMillis() - inicio;
        System.out.printf(">>> TesteDiaSemana CONCLUÍDO com SUCESSO: %d métodos verificados (%d ms)%n%n",
                testesExecutados, duracao);
    }

    /**
     * Tier 1: Construtores básicos, getters e setters em conformidade com R1.3.
     */
    public static void testarInstanciacaoEGettersSetters() {
        // Construtor a partir de inteiros (24/10/2026 = Sábado)
        DiaSemana data1 = new DiaSemana(24, 10, 2026);
        afirmarIgual(24, data1.getDia(), "dia deve ser 24");
        afirmarIgual(10, data1.getMes(), "mês deve ser 10");
        afirmarIgual(2026, data1.getAno(), "ano deve ser 2026");
        afirmarIgual(0, data1.getDiaSemana(), "24/10/2026 deve ser Sábado (0)");
        afirmarIgual(LocalDate.of(2026, 10, 24), data1.getData(), "data civil composta deve bater");

        // Construtor a partir de LocalDate (26/10/2026 = Segunda-feira)
        DiaSemana data2 = new DiaSemana(LocalDate.of(2026, 10, 26));
        afirmarIgual(26, data2.getDia(), "dia deve ser 26");
        afirmarIgual(10, data2.getMes(), "mês deve ser 10");
        afirmarIgual(2026, data2.getAno(), "ano deve ser 2026");
        afirmarIgual(2, data2.getDiaSemana(), "26/10/2026 deve ser Segunda-feira (2)");

        // Modificação de setters individuais
        data1.setDiaSemana(3);
        afirmarIgual(3, data1.getDiaSemana(), "setDiaSemana deve atualizar o campo");

        // Setter de data completa
        data1.setData(LocalDate.of(2026, 12, 25));
        afirmarIgual(25, data1.getDia(), "dia deve ser 25");
        afirmarIgual(12, data1.getMes(), "mês deve ser 12");
        afirmarIgual(6, data1.getDiaSemana(), "25/12/2026 é Sexta-feira (6)");
    }

    /**
     * Tier 1: Validação do mapeamento aritmético dos 7 dias consecutivos da semana.
     */
    public static void testarAlgoritmoZellerSeteDias() {
        // Semana de 24/10/2026 a 30/10/2026
        afirmarIgual(0, new DiaSemana(24, 10, 2026).diaSemana(), "24/10/2026 deve ser 0 (Sábado)");
        afirmarIgual(1, new DiaSemana(25, 10, 2026).diaSemana(), "25/10/2026 deve ser 1 (Domingo)");
        afirmarIgual(2, new DiaSemana(26, 10, 2026).diaSemana(), "26/10/2026 deve ser 2 (Segunda-feira)");
        afirmarIgual(3, new DiaSemana(27, 10, 2026).diaSemana(), "27/10/2026 deve ser 3 (Terça-feira)");
        afirmarIgual(4, new DiaSemana(28, 10, 2026).diaSemana(), "28/10/2026 deve ser 4 (Quarta-feira)");
        afirmarIgual(5, new DiaSemana(29, 10, 2026).diaSemana(), "29/10/2026 deve ser 5 (Quinta-feira)");
        afirmarIgual(6, new DiaSemana(30, 10, 2026).diaSemana(), "30/10/2026 deve ser 6 (Sexta-feira)");
    }

    /**
     * Tier 2 (Boundary): Ajuste do algoritmo de Zeller para Janeiro e Fevereiro (meses 13 e 14 do ano anterior).
     */
    public static void testarAlgoritmoZellerJaneiroFevereiro() {
        // 01/01/2026 é Quinta-feira (5) -> mês 1 ajustado para 13 de 2025
        DiaSemana dia1Jan = new DiaSemana(1, 1, 2026);
        afirmarIgual(5, dia1Jan.diaSemana(), "01/01/2026 deve ser 5 (Quinta-feira)");
        afirmarIgual("quinta-feira", dia1Jan.nomeDiaSemana().toLowerCase(), "nome textual de 01/01/2026");

        // 31/01/2026 é Sábado (0)
        DiaSemana dia31Jan = new DiaSemana(31, 1, 2026);
        afirmarIgual(0, dia31Jan.diaSemana(), "31/01/2026 deve ser 0 (Sábado)");

        // 01/02/2026 é Domingo (1) -> mês 2 ajustado para 14 de 2025
        DiaSemana dia1Fev = new DiaSemana(1, 2, 2026);
        afirmarIgual(1, dia1Fev.diaSemana(), "01/02/2026 deve ser 1 (Domingo)");

        // 28/02/2026 é Sábado (0)
        DiaSemana dia28Fev = new DiaSemana(28, 2, 2026);
        afirmarIgual(0, dia28Fev.diaSemana(), "28/02/2026 deve ser 0 (Sábado)");
    }

    /**
     * Tier 2 (Boundary): Anos bissextos e viradas de século.
     */
    public static void testarAlgoritmoZellerAnosBissextosEViradasDeSeculo() {
        // 29/02/2024 (ano bissexto comum) é Quinta-feira (5)
        DiaSemana bis2024 = new DiaSemana(29, 2, 2024);
        afirmarIgual(5, bis2024.diaSemana(), "29/02/2024 deve ser 5 (Quinta-feira)");

        // 29/02/2000 (ano bissexto secular múltiplo de 400) é Terça-feira (3)
        DiaSemana bis2000 = new DiaSemana(29, 2, 2000);
        afirmarIgual(3, bis2000.diaSemana(), "29/02/2000 deve ser 3 (Terça-feira)");

        // 01/01/2000 (virada de milênio) é Sábado (0)
        DiaSemana milenio = new DiaSemana(1, 1, 2000);
        afirmarIgual(0, milenio.diaSemana(), "01/01/2000 deve ser 0 (Sábado)");

        // 31/12/1999 é Sexta-feira (6)
        DiaSemana fim99 = new DiaSemana(31, 12, 1999);
        afirmarIgual(6, fim99.diaSemana(), "31/12/1999 deve ser 6 (Sexta-feira)");
    }

    /**
     * Tier 3: Polimorfismo do método diaSemana(...) com e sem parâmetros.
     */
    public static void testarPolimorfismoDiaSemana() {
        DiaSemana instancia = new DiaSemana(24, 10, 2026); // Sábado (0)
        afirmarIgual(0, instancia.diaSemana(), "método sem argumentos deve usar a data da instância");

        // Sobrecarga com 3 inteiros: calcula para outra data sem alterar o estado da instância
        int calculado = instancia.diaSemana(26, 10, 2026);
        afirmarIgual(2, calculado, "sobrecarga polimórfica deve calcular 2 (Segunda-feira)");
        afirmarIgual(24, instancia.getDia(), "instância deve manter dia original 24");
        afirmarIgual(0, instancia.diaSemana(), "instância deve manter diaSemana original 0");

        // Sobrecarga com LocalDate
        int calculadoLocalDate = instancia.diaSemana(LocalDate.of(2026, 10, 27));
        afirmarIgual(3, calculadoLocalDate, "sobrecarga com LocalDate deve calcular 3 (Terça-feira)");
        afirmarIgual(0, instancia.diaSemana(), "estado interno da instância não deve ser modificado");
    }

    /**
     * Tier 3: Polimorfismo do método nomeDiaSemana(...) em Português do Brasil.
     */
    public static void testarPolimorfismoNomeDiaSemana() {
        DiaSemana instancia = new DiaSemana(24, 10, 2026);
        afirmarIgual("sábado", instancia.nomeDiaSemana().toLowerCase(), "nome textual sem argumentos");

        // Sobrecarga com 3 inteiros
        String nomeSegunda = instancia.nomeDiaSemana(26, 10, 2026);
        afirmarIgual("segunda-feira", nomeSegunda.toLowerCase(), "nome textual com sobrecarga de inteiros");
        afirmarIgual("sábado", instancia.nomeDiaSemana().toLowerCase(), "estado da instância mantido");

        // Sobrecarga com LocalDate
        String nomeQuarta = instancia.nomeDiaSemana(LocalDate.of(2026, 10, 28));
        afirmarIgual("quarta-feira", nomeQuarta.toLowerCase(), "nome textual com sobrecarga LocalDate");
    }

    /**
     * Tier 3: Polimorfismo do método ehDiaUtil(...) com e sem argumentos.
     */
    public static void testarPolimorfismoEhDiaUtil() {
        DiaSemana sabado = new DiaSemana(24, 10, 2026);
        afirmarFalso(sabado.ehDiaUtil(), "sábado não é dia útil");

        // Sobrecarga com inteiros
        afirmarVerdadeiro(sabado.ehDiaUtil(26, 10, 2026), "segunda-feira é dia útil via sobrecarga");
        afirmarFalso(sabado.ehDiaUtil(25, 10, 2026), "domingo não é dia útil via sobrecarga");

        // Sobrecarga com LocalDate
        afirmarVerdadeiro(sabado.ehDiaUtil(LocalDate.of(2026, 10, 30)), "sexta-feira é dia útil via sobrecarga");
    }

    /**
     * Tier 1: Verificação estrita de ehDiaUtil() para dias de semana vs fins de semana.
     */
    public static void testarEhDiaUtilFimDeSemana() {
        // Sábado (0) e Domingo (1) -> false
        afirmarFalso(new DiaSemana(24, 10, 2026).ehDiaUtil(), "Sábado deve retornar false");
        afirmarFalso(new DiaSemana(25, 10, 2026).ehDiaUtil(), "Domingo deve retornar false");

        // Segunda (2) a Sexta (6) -> true
        afirmarVerdadeiro(new DiaSemana(26, 10, 2026).ehDiaUtil(), "Segunda deve retornar true");
        afirmarVerdadeiro(new DiaSemana(27, 10, 2026).ehDiaUtil(), "Terça deve retornar true");
        afirmarVerdadeiro(new DiaSemana(28, 10, 2026).ehDiaUtil(), "Quarta deve retornar true");
        afirmarVerdadeiro(new DiaSemana(29, 10, 2026).ehDiaUtil(), "Quinta deve retornar true");
        afirmarVerdadeiro(new DiaSemana(30, 10, 2026).ehDiaUtil(), "Sexta deve retornar true");
    }

    /**
     * Tier 1: Método setCadastro com datas válidas.
     */
    public static void testarSetCadastroDataValida() {
        DiaSemana dia = new DiaSemana(1, 1, 2026);
        dia.setCadastro(15, 8, 2026);
        afirmarIgual(15, dia.getDia(), "dia deve ser 15");
        afirmarIgual(8, dia.getMes(), "mês deve ser 8");
        afirmarIgual(2026, dia.getAno(), "ano deve ser 2026");
        afirmarIgual(0, dia.getDiaSemana(), "15/08/2026 é Sábado (0)");

        dia.setCadastro(LocalDate.of(2026, 9, 7));
        afirmarIgual(7, dia.getDia(), "dia deve ser 7");
        afirmarIgual(9, dia.getMes(), "mês deve ser 9");
        afirmarIgual(2, dia.getDiaSemana(), "07/09/2026 é Segunda-feira (2)");
    }

    /**
     * Tier 5 (Adversarial): Rejeição de datas civis inexistentes ou inválidas.
     */
    public static void testarSetCadastroDataInvalida() {
        DiaSemana dia = new DiaSemana(24, 10, 2026);

        // 31 de Fevereiro
        afirmarRejeicao(() -> dia.setCadastro(31, 2, 2026), IllegalArgumentException.class,
                "deve rejeitar 31/02/2026");

        // 29 de Fevereiro em ano não-bissexto
        afirmarRejeicao(() -> dia.setCadastro(29, 2, 2025), IllegalArgumentException.class,
                "deve rejeitar 29/02/2025");

        // Mês 13 ou dia 32
        afirmarRejeicao(() -> dia.setCadastro(10, 13, 2026), IllegalArgumentException.class,
                "deve rejeitar mês 13");
        afirmarRejeicao(() -> dia.setCadastro(32, 1, 2026), IllegalArgumentException.class,
                "deve rejeitar dia 32");

        // Construtor com data inválida
        afirmarRejeicao(() -> new DiaSemana(31, 4, 2026), IllegalArgumentException.class,
                "deve rejeitar 31/04 no construtor");
    }

    /**
     * Tier 1: Sobrescrita de toString().
     */
    public static void testarSobrescritaToString() {
        DiaSemana data = new DiaSemana(26, 10, 2026);
        String texto = data.toString();
        afirmarNaoNulo(texto, "toString não pode ser nulo");
        afirmarVerdadeiro(texto.contains("2026") || texto.contains("26"), "toString deve conter referências da data");
        afirmarVerdadeiro(texto.toLowerCase().contains("segunda-feira") || texto.contains("2"),
                "toString deve identificar segunda-feira ou índice 2");
    }

    /**
     * Tier 3 & Tier 5: Segurança de herança garantindo que o construtor da superclasse
     * não despacha chamadas para métodos sobrescrevíveis como setCadastro.
     */
    public static void testarHerancaSegura() {
        class SubclasseDiaSemanaComSobrescrita extends DiaSemana {
            private boolean setCadastroInvocadoNoConstrutor = false;

            SubclasseDiaSemanaComSobrescrita(int dia, int mes, int ano) {
                super(dia, mes, ano);
            }

            @Override
            public void setCadastro(int dia, int mes, int ano) {
                this.setCadastroInvocadoNoConstrutor = true;
                throw new AssertionError("Construtor da superclasse jamais deve invocar setCadastro sobrescrevível!");
            }
        }

        // A instanciação da subclasse deve ter sucesso sem invocar o setCadastro sobrescrito
        SubclasseDiaSemanaComSobrescrita sub = new SubclasseDiaSemanaComSobrescrita(24, 10, 2026);
        afirmarIgual(24, sub.getDia(), "subclasse deve manter dia 24");
        afirmarIgual(0, sub.getDiaSemana(), "subclasse deve manter índice 0 (Sábado)");
    }

    /**
     * Tier 4: Simulação de ciclo civil completo de 30 dias contínuos.
     */
    public static void testarSimulacaoCicloMensal() {
        LocalDate inicio = LocalDate.of(2026, 10, 1);
        int diasUteisContados = 0;
        int finsDeSemanaContados = 0;

        DiaSemana cursor = new DiaSemana(inicio);
        for (int i = 0; i < 30; i++) {
            LocalDate atual = inicio.plusDays(i);
            cursor.setData(atual);

            int ds = cursor.diaSemana();
            boolean util = cursor.ehDiaUtil();

            if (ds >= 2 && ds <= 6) {
                afirmarVerdadeiro(util, "dia " + atual + " com índice " + ds + " deve ser dia útil");
                diasUteisContados++;
            } else {
                afirmarFalso(util, "dia " + atual + " com índice " + ds + " deve ser fim de semana");
                finsDeSemanaContados++;
            }
        }

        afirmarIgual(30, diasUteisContados + finsDeSemanaContados, "total de dias analisados deve ser 30");
        afirmarVerdadeiro(diasUteisContados >= 20, "um mês típico possui pelo menos 20 dias úteis");
    }

    /**
     * Tier 1: Verificação de aliases legados para compatibilidade com testes prévios.
     */
    public static void testarAliasesLegadosCompatibilidade() {
        DiaSemana data = new DiaSemana(24, 10, 2026);
        afirmarIgual(0, data.dayOfWeek(), "dayOfWeek() deve retornar 0");
        afirmarIgual("sábado", data.dayOfWeekName().toLowerCase(), "dayOfWeekName() deve retornar 'sábado'");
        afirmarIgual(1, data.dayOfWeek(25, 10, 2026), "dayOfWeek(overload) deve calcular 1");
        afirmarIgual("segunda-feira", data.dayOfWeekName(26, 10, 2026).toLowerCase(), "dayOfWeekName(overload)");
    }
}

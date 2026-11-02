package dominio;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Representa uma data civil e calcula o dia da semana utilizando a
 * Congruência Aritmética de Zeller.
 *
 * <p>O índice do dia da semana varia de 0 a 6:</p>
 * <ul>
 *   <li>0 = Sábado</li>
 *   <li>1 = Domingo</li>
 *   <li>2 = Segunda-feira</li>
 *   <li>3 = Terça-feira</li>
 *   <li>4 = Quarta-feira</li>
 *   <li>5 = Quinta-feira</li>
 *   <li>6 = Sexta-feira</li>
 * </ul>
 *
 * <p>A classe é passível de herança (não-final), oferece métodos polimórficos
 * de cálculo e verifica se a data corresponde a um dia útil (segunda a sexta-feira).
 * Seus construtores são seguros contra despacho de métodos sobrescrevíveis.</p>
 */
public class DiaSemana {

    /** Nomes por extenso dos dias da semana em português brasileiro indexados de 0 a 6. */
    private static final String[] NOMES_DIA_SEMANA = {
            "sábado",
            "domingo",
            "segunda-feira",
            "terça-feira",
            "quarta-feira",
            "quinta-feira",
            "sexta-feira"
    };

    private int dia;
    private int mes;
    private int ano;
    private int diaSemana;
    private LocalDate data;

    /**
     * Construtor protegido padrão para permitir herança segura por subclasses.
     */
    protected DiaSemana() {
    }

    /**
     * Cria a instância a partir dos componentes numéricos da data civil gregoriana.
     *
     * @param dia o dia do mês (1 a 31)
     * @param mes o mês do ano (1 a 12)
     * @param ano o ano civil (ex.: 2026)
     * @throws IllegalArgumentException se os parâmetros não constituírem data civil válida
     */
    public DiaSemana(int dia, int mes, int ano) {
        LocalDate dataValidada = validarData(dia, mes, ano);
        int indiceCalculado = calcularZeller(dia, mes, ano);
        aplicarCampos(dataValidada, dia, mes, ano, indiceCalculado);
    }

    /**
     * Cria a instância a partir de uma data civil pronta.
     *
     * @param data a data civil não nula
     * @throws NullPointerException se data for nula
     */
    public DiaSemana(LocalDate data) {
        LocalDate dataValidada = Objects.requireNonNull(data, "Data civil é obrigatória.");
        int d = dataValidada.getDayOfMonth();
        int m = dataValidada.getMonthValue();
        int a = dataValidada.getYear();
        int indiceCalculado = calcularZeller(d, m, a);
        aplicarCampos(dataValidada, d, m, a, indiceCalculado);
    }

    /**
     * Atualiza o cadastro da data civil a partir de dia, mês e ano.
     *
     * @param dia o dia do mês (1 a 31)
     * @param mes o mês do ano (1 a 12)
     * @param ano o ano civil (ex.: 2026)
     * @throws IllegalArgumentException se a data for inválida
     */
    public void setCadastro(int dia, int mes, int ano) {
        LocalDate novaData = validarData(dia, mes, ano);
        int novoIndice = calcularZeller(dia, mes, ano);
        aplicarCampos(novaData, dia, mes, ano, novoIndice);
    }

    /**
     * Atualiza o cadastro da data civil a partir de um {@link LocalDate}.
     *
     * @param data a nova data civil
     * @throws NullPointerException se data for nula
     */
    public void setCadastro(LocalDate data) {
        LocalDate novaData = Objects.requireNonNull(data, "Data civil é obrigatória.");
        int d = novaData.getDayOfMonth();
        int m = novaData.getMonthValue();
        int a = novaData.getYear();
        int novoIndice = calcularZeller(d, m, a);
        aplicarCampos(novaData, d, m, a, novoIndice);
    }

    /* =========================================================================
     * Algoritmo Aritmético Canônico (Congruência de Zeller)
     * ========================================================================= */

    /**
     * Executa a fórmula aritmética canônica de Christian Zeller para cálculo do
     * índice de dia da semana no calendário gregoriano.
     *
     * <p>Mapeia: 0=Sábado, 1=Domingo, 2=Segunda-feira, 3=Terça-feira, 4=Quarta-feira,
     * 5=Quinta-feira, 6=Sexta-feira.</p>
     *
     * @param dia o dia do mês (1 a 31)
     * @param mes o mês do ano (1 a 12)
     * @param ano o ano civil gregoriano
     * @return índice de 0 a 6
     */
    public static int calcularZeller(int dia, int mes, int ano) {
        int q = dia;
        int m = mes;
        int a = ano;

        // Janeiro e Fevereiro são tratados como meses 13 e 14 do ano anterior
        if (m == 1 || m == 2) {
            m += 12;
            a -= 1;
        }

        int k = a % 100; // ano do século
        int j = a / 100; // século base zero

        // Fórmula de Zeller com termo +5*j para garantia de positividade em Java
        int h = (q + (13 * (m + 1)) / 5 + k + (k / 4) + (j / 4) + 5 * j) % 7;
        return (h % 7 + 7) % 7;
    }

    private static LocalDate validarData(int dia, int mes, int ano) {
        try {
            return LocalDate.of(ano, mes, dia);
        } catch (DateTimeException ex) {
            throw new IllegalArgumentException(
                    "Data civil inválida: " + dia + "/" + mes + "/" + ano + ".", ex);
        }
    }

    private void aplicarCampos(LocalDate novaData, int d, int m, int a, int ds) {
        this.data = novaData;
        this.dia = d;
        this.mes = m;
        this.ano = a;
        this.diaSemana = ds;
    }

    /* =========================================================================
     * Métodos Polimórficos de Cálculo de Dia da Semana (Item 3)
     * ========================================================================= */

    /**
     * Retorna o índice numérico do dia da semana (0 a 6) da data armazenada nesta instância.
     *
     * @return 0 para Sábado, 1 para Domingo, ..., 6 para Sexta-feira
     */
    public int diaSemana() {
        return this.diaSemana;
    }

    /**
     * Sobrecarga polimórfica que calcula o dia da semana para data arbitrária
     * sem alterar o estado desta instância.
     *
     * @param dia dia do mês
     * @param mes mês do ano
     * @param ano ano civil
     * @return índice de 0 a 6
     * @throws IllegalArgumentException se a data for inválida
     */
    public int diaSemana(int dia, int mes, int ano) {
        validarData(dia, mes, ano);
        return calcularZeller(dia, mes, ano);
    }

    /**
     * Sobrecarga polimórfica que calcula o dia da semana para um {@link LocalDate}
     * sem alterar o estado desta instância.
     *
     * @param data data civil informada
     * @return índice de 0 a 6
     * @throws NullPointerException se data for nula
     */
    public int diaSemana(LocalDate data) {
        Objects.requireNonNull(data, "Data civil é obrigatória.");
        return calcularZeller(data.getDayOfMonth(), data.getMonthValue(), data.getYear());
    }

    /**
     * Retorna o nome por extenso do dia da semana da data armazenada nesta instância.
     *
     * @return nome em português do Brasil (ex.: "sábado", "segunda-feira")
     */
    public String nomeDiaSemana() {
        return NOMES_DIA_SEMANA[this.diaSemana];
    }

    /**
     * Sobrecarga polimórfica que retorna o nome por extenso para uma data arbitrária.
     *
     * @param dia dia do mês
     * @param mes mês do ano
     * @param ano ano civil
     * @return nome em português do Brasil
     * @throws IllegalArgumentException se a data for inválida
     */
    public String nomeDiaSemana(int dia, int mes, int ano) {
        int indice = diaSemana(dia, mes, ano);
        return NOMES_DIA_SEMANA[indice];
    }

    /**
     * Sobrecarga polimórfica que retorna o nome por extenso para um {@link LocalDate}.
     *
     * @param data data civil
     * @return nome em português do Brasil
     * @throws NullPointerException se data for nula
     */
    public String nomeDiaSemana(LocalDate data) {
        int indice = diaSemana(data);
        return NOMES_DIA_SEMANA[indice];
    }

    /* =========================================================================
     * Verificação de Dia Útil (Segunda a Sexta-feira)
     * ========================================================================= */

    /**
     * Informa se a data armazenada na instância é um dia útil (segunda a sexta-feira).
     *
     * @return {@code true} se o índice estiver entre 2 (Segunda) e 6 (Sexta);
     *         {@code false} se for 0 (Sábado) ou 1 (Domingo)
     */
    public boolean ehDiaUtil() {
        return this.diaSemana >= 2 && this.diaSemana <= 6;
    }

    /**
     * Sobrecarga polimórfica que verifica se os parâmetros informados constituem um dia útil.
     *
     * @param dia dia do mês
     * @param mes mês do ano
     * @param ano ano civil
     * @return {@code true} se for segunda a sexta-feira
     * @throws IllegalArgumentException se a data civil for inválida
     */
    public boolean ehDiaUtil(int dia, int mes, int ano) {
        int ds = diaSemana(dia, mes, ano);
        return ds >= 2 && ds <= 6;
    }

    /**
     * Sobrecarga polimórfica que verifica se a data civil informada é um dia útil.
     *
     * @param data data civil
     * @return {@code true} se for segunda a sexta-feira
     * @throws NullPointerException se data for nula
     */
    public boolean ehDiaUtil(LocalDate data) {
        int ds = diaSemana(data);
        return ds >= 2 && ds <= 6;
    }

    /* =========================================================================
     * Getters e Setters
     * ========================================================================= */

    /**
     * Retorna o dia do mês armazenado.
     *
     * @return dia civil (1 a 31)
     */
    public int getDia() {
        return dia;
    }

    /**
     * Altera o dia do mês, recalculando a data e o dia da semana.
     *
     * @param dia o novo dia do mês
     * @throws IllegalArgumentException se o novo dia resultar em data inválida
     */
    public void setDia(int dia) {
        setCadastro(dia, this.mes, this.ano);
    }

    /**
     * Retorna o mês do ano armazenado.
     *
     * @return mês civil (1 a 12)
     */
    public int getMes() {
        return mes;
    }

    /**
     * Altera o mês do ano, recalculando a data e o dia da semana.
     *
     * @param mes o novo mês
     * @throws IllegalArgumentException se o novo mês resultar em data inválida
     */
    public void setMes(int mes) {
        setCadastro(this.dia, mes, this.ano);
    }

    /**
     * Retorna o ano civil armazenado.
     *
     * @return ano (ex.: 2026)
     */
    public int getAno() {
        return ano;
    }

    /**
     * Altera o ano civil, recalculando a data e o dia da semana.
     *
     * @param ano o novo ano
     * @throws IllegalArgumentException se o novo ano resultar em data inválida
     */
    public void setAno(int ano) {
        setCadastro(this.dia, this.mes, ano);
    }

    /**
     * Retorna o índice numérico do dia da semana (0 a 6).
     *
     * @return índice de 0 (Sábado) a 6 (Sexta-feira)
     */
    public int getDiaSemana() {
        return diaSemana;
    }

    /**
     * Define diretamente o índice do dia da semana.
     *
     * @param diaSemana índice de 0 a 6
     * @throws IllegalArgumentException se o índice for menor que 0 ou maior que 6
     */
    public void setDiaSemana(int diaSemana) {
        if (diaSemana < 0 || diaSemana > 6) {
            throw new IllegalArgumentException("Dia da semana deve estar entre 0 (Sábado) e 6 (Sexta).");
        }
        this.diaSemana = diaSemana;
    }

    /**
     * Retorna a representação {@link LocalDate} da data.
     *
     * @return a data civil
     */
    public LocalDate getData() {
        return data;
    }

    /**
     * Atualiza a data civil através de {@link LocalDate}.
     *
     * @param data nova data civil
     * @throws NullPointerException se data for nula
     */
    public void setData(LocalDate data) {
        setCadastro(data);
    }

    /* =========================================================================
     * Métodos de Compatibilidade com Código Legado e Testes
     * ========================================================================= */

    /**
     * Alias de compatibilidade com classes legadas.
     *
     * @return índice de 0 a 6
     */
    public int dayOfWeek() {
        return diaSemana();
    }

    /**
     * Alias polimórfico legado.
     *
     * @param dia dia do mês
     * @param mes mês do ano
     * @param ano ano civil
     * @return índice de 0 a 6
     */
    public int dayOfWeek(int dia, int mes, int ano) {
        return diaSemana(dia, mes, ano);
    }

    /**
     * Alias legado para nome do dia.
     *
     * @return nome do dia
     */
    public String dayOfWeekName() {
        return nomeDiaSemana();
    }

    /**
     * Alias polimórfico legado para nome do dia.
     *
     * @param dia dia do mês
     * @param mes mês do ano
     * @param ano ano civil
     * @return nome do dia
     */
    public String dayOfWeekName(int dia, int mes, int ano) {
        return nomeDiaSemana(dia, mes, ano);
    }

    /**
     * Alias de compatibilidade para obter dia.
     *
     * @return dia do mês
     */
    public int getDay() {
        return dia;
    }

    /**
     * Alias de compatibilidade para obter mês.
     *
     * @return mês do ano
     */
    public int getMonth() {
        return mes;
    }

    /**
     * Alias de compatibilidade para obter ano.
     *
     * @return ano civil
     */
    public int getYear() {
        return ano;
    }

    /**
     * Alias de compatibilidade para obter data.
     *
     * @return data civil
     */
    public LocalDate getDate() {
        return data;
    }

    /**
     * Alias de compatibilidade para definir data.
     *
     * @param data nova data civil
     */
    public void setDate(LocalDate data) {
        setData(data);
    }

    @Override
    public String toString() {
        return "DiaSemana{"
                + "data=" + data
                + ", diaSemana=" + diaSemana + " (" + nomeDiaSemana() + ")"
                + ", diaUtil=" + ehDiaUtil()
                + '}';
    }
}

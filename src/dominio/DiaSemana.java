package dominio;

import dominio.abstracoes.RegistroDatado;
import dominio.util.ValidacaoDominio;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Item 3 — Representa uma data civil e calcula o dia da semana via Congruência de Zeller
 * (0 = Sábado, 1 = Domingo, 2 = Segunda-feira, ..., 6 = Sexta-feira).
 *
 * <p>Herda a gestão sincronizada de data civil de {@link RegistroDatado}, mantendo
 * construtores seguros sem despacho de métodos sobrescrevíveis.</p>
 */
public class DiaSemana extends RegistroDatado {

    private static final String[] NOMES_DIA_SEMANA = {
            "sábado", "domingo", "segunda-feira", "terça-feira",
            "quarta-feira", "quinta-feira", "sexta-feira"
    };

    private int diaSemana;

    /** Construtor protegido para herança segura. */
    protected DiaSemana() {
        super();
    }

    /** Cria a instância a partir de dia, mês e ano válidos. */
    public DiaSemana(int dia, int mes, int ano) {
        super(dia, mes, ano);
        this.diaSemana = calcularZeller(dia, mes, ano);
    }

    /** Cria a instância a partir de um {@link LocalDate} não nulo. */
    public DiaSemana(LocalDate data) {
        super(data, "Data civil é obrigatória.");
        this.diaSemana = calcularZeller(getDia(), getMes(), getAno());
    }

    /** Atualiza atomicamente a data civil a partir de dia, mês e ano. */
    public void setCadastro(int dia, int mes, int ano) {
        LocalDate novaData = ValidacaoDominio.validarData(dia, mes, ano);
        aplicarDataInterno(novaData);
        this.diaSemana = calcularZeller(dia, mes, ano);
    }

    /** Atualiza atomicamente a data civil a partir de um {@link LocalDate}. */
    public void setCadastro(LocalDate data) {
        LocalDate novaData = Objects.requireNonNull(data, "Data civil é obrigatória.");
        aplicarDataInterno(novaData);
        this.diaSemana = calcularZeller(
                novaData.getDayOfMonth(), novaData.getMonthValue(), novaData.getYear());
    }

    /** Recalcula o dia da semana automaticamente quando {@code setDia/setMes/setAno/setData} são usados. */
    @Override
    protected void aoAlterarData(LocalDate novaData) {
        this.diaSemana = calcularZeller(
                novaData.getDayOfMonth(), novaData.getMonthValue(), novaData.getYear());
    }

    /** Congruência de Zeller: retorna 0 (Sábado) a 6 (Sexta-feira). */
    public static int calcularZeller(int dia, int mes, int ano) {
        int m = mes;
        int a = ano;
        if (m == 1 || m == 2) {
            m += 12;
            a -= 1;
        }
        int k = a % 100;
        int j = a / 100;
        int h = (dia + (13 * (m + 1)) / 5 + k + (k / 4) + (j / 4) + 5 * j) % 7;
        return (h % 7 + 7) % 7;
    }

    // -------------------------------------------------------------------------
    // Métodos polimórficos de cálculo de dia da semana e dia útil
    // -------------------------------------------------------------------------

    /** Recalcula e retorna o índice numérico (0 a 6) da data cadastrada na instância. */
    public int calcularDiaSemana() {
        ValidacaoDominio.validarData(getDia(), getMes(), getAno());
        this.diaSemana = calcularZeller(getDia(), getMes(), getAno());
        return this.diaSemana;
    }

    public int calcularDiaSemana(int dia, int mes, int ano) {
        return diaSemana(dia, mes, ano);
    }

    public int calcularDiaSemana(LocalDate data) {
        return diaSemana(data);
    }

    public int diaSemana() {
        return this.diaSemana;
    }

    public int diaSemana(int dia, int mes, int ano) {
        ValidacaoDominio.validarData(dia, mes, ano);
        return calcularZeller(dia, mes, ano);
    }

    public int diaSemana(LocalDate data) {
        Objects.requireNonNull(data, "Data civil é obrigatória.");
        return calcularZeller(data.getDayOfMonth(), data.getMonthValue(), data.getYear());
    }

    public String nomeDiaSemana() {
        return NOMES_DIA_SEMANA[this.diaSemana];
    }

    public String nomeDiaSemana(int dia, int mes, int ano) {
        return NOMES_DIA_SEMANA[diaSemana(dia, mes, ano)];
    }

    public String nomeDiaSemana(LocalDate data) {
        return NOMES_DIA_SEMANA[diaSemana(data)];
    }

    /** Retorna {@code true} se a data for segunda (2) a sexta-feira (6). */
    public boolean ehDiaUtil() {
        return this.diaSemana >= 2 && this.diaSemana <= 6;
    }

    public boolean ehDiaUtil(int dia, int mes, int ano) {
        int ds = diaSemana(dia, mes, ano);
        return ds >= 2 && ds <= 6;
    }

    public boolean ehDiaUtil(LocalDate data) {
        int ds = diaSemana(data);
        return ds >= 2 && ds <= 6;
    }

    public int getDiaSemana() { return diaSemana; }

    public void setDiaSemana(int diaSemana) {
        if (diaSemana < 0 || diaSemana > 6) {
            throw new IllegalArgumentException("Dia da semana deve estar entre 0 (Sábado) e 6 (Sexta).");
        }
        this.diaSemana = diaSemana;
    }

    // Aliases mantidos para compatibilidade de testes
    public int dayOfWeek() { return diaSemana(); }
    public int dayOfWeek(int dia, int mes, int ano) { return diaSemana(dia, mes, ano); }
    public String dayOfWeekName() { return nomeDiaSemana(); }
    public String dayOfWeekName(int dia, int mes, int ano) { return nomeDiaSemana(dia, mes, ano); }

    @Override
    public String toString() {
        return "DiaSemana{data=" + getData()
                + ", diaSemana=" + diaSemana + " (" + nomeDiaSemana() + ")"
                + ", diaUtil=" + ehDiaUtil() + '}';
    }
}

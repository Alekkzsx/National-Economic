package dominio;

import dominio.util.ValidacaoDominio;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Item 3 — Representa uma data civil e calcula o dia da semana via Congruência de Zeller
 * (0 = Sábado, 1 = Domingo, 2 = Segunda-feira, ..., 6 = Sexta-feira).
 *
 * <p>Classe passível de herança, sem chamadas a métodos sobrescrevíveis nos construtores.</p>
 */
public class DiaSemana {

    private static final String[] NOMES_DIA_SEMANA = {
            "sábado", "domingo", "segunda-feira", "terça-feira",
            "quarta-feira", "quinta-feira", "sexta-feira"
    };

    private int dia;
    private int mes;
    private int ano;
    private int diaSemana;
    private LocalDate data;

    /** Construtor protegido para herança segura. */
    protected DiaSemana() {
    }

    /** Cria a instância a partir de dia, mês e ano válidos. */
    public DiaSemana(int dia, int mes, int ano) {
        LocalDate dataValidada = ValidacaoDominio.validarData(dia, mes, ano);
        aplicarCampos(dataValidada, calcularZeller(dia, mes, ano));
    }

    /** Cria a instância a partir de um {@link LocalDate} não nulo. */
    public DiaSemana(LocalDate data) {
        LocalDate dataValidada = Objects.requireNonNull(data, "Data civil é obrigatória.");
        int ds = calcularZeller(dataValidada.getDayOfMonth(), dataValidada.getMonthValue(), dataValidada.getYear());
        aplicarCampos(dataValidada, ds);
    }

    /** Atualiza atomicamente a data civil a partir de dia, mês e ano. */
    public void setCadastro(int dia, int mes, int ano) {
        LocalDate novaData = ValidacaoDominio.validarData(dia, mes, ano);
        aplicarCampos(novaData, calcularZeller(dia, mes, ano));
    }

    /** Atualiza atomicamente a data civil a partir de um {@link LocalDate}. */
    public void setCadastro(LocalDate data) {
        LocalDate novaData = Objects.requireNonNull(data, "Data civil é obrigatória.");
        int ds = calcularZeller(novaData.getDayOfMonth(), novaData.getMonthValue(), novaData.getYear());
        aplicarCampos(novaData, ds);
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

    private void aplicarCampos(LocalDate novaData, int indice) {
        this.data = novaData;
        this.dia = novaData.getDayOfMonth();
        this.mes = novaData.getMonthValue();
        this.ano = novaData.getYear();
        this.diaSemana = indice;
    }

    // -------------------------------------------------------------------------
    // Métodos polimórficos de cálculo de dia da semana e dia útil
    // -------------------------------------------------------------------------

    /** Recalcula e retorna o índice numérico (0 a 6) da data cadastrada na instância. */
    public int calcularDiaSemana() {
        ValidacaoDominio.validarData(this.dia, this.mes, this.ano);
        this.diaSemana = calcularZeller(this.dia, this.mes, this.ano);
        return this.diaSemana;
    }

    /** Calcula o índice numérico (0 a 6) para uma data informada sem alterar a instância. */
    public int calcularDiaSemana(int dia, int mes, int ano) {
        return diaSemana(dia, mes, ano);
    }

    /** Calcula o índice numérico (0 a 6) para um {@link LocalDate} sem alterar a instância. */
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

    // -------------------------------------------------------------------------
    // Getters e Setters
    // -------------------------------------------------------------------------

    public int getDia() { return dia; }
    public void setDia(int dia) { setCadastro(dia, this.mes, this.ano); }

    public int getMes() { return mes; }
    public void setMes(int mes) { setCadastro(this.dia, mes, this.ano); }

    public int getAno() { return ano; }
    public void setAno(int ano) { setCadastro(this.dia, this.mes, ano); }

    public int getDiaSemana() { return diaSemana; }

    public void setDiaSemana(int diaSemana) {
        if (diaSemana < 0 || diaSemana > 6) {
            throw new IllegalArgumentException("Dia da semana deve estar entre 0 (Sábado) e 6 (Sexta).");
        }
        this.diaSemana = diaSemana;
    }

    public LocalDate getData() { return data; }
    public void setData(LocalDate data) { setCadastro(data); }

    // -------------------------------------------------------------------------
    // Aliases mantidos para compatibilidade de testes
    // -------------------------------------------------------------------------

    public int dayOfWeek() { return diaSemana(); }
    public int dayOfWeek(int dia, int mes, int ano) { return diaSemana(dia, mes, ano); }
    public String dayOfWeekName() { return nomeDiaSemana(); }
    public String dayOfWeekName(int dia, int mes, int ano) { return nomeDiaSemana(dia, mes, ano); }

    @Override
    public String toString() {
        return "DiaSemana{data=" + data
                + ", diaSemana=" + diaSemana + " (" + nomeDiaSemana() + ")"
                + ", diaUtil=" + ehDiaUtil() + '}';
    }
}

package domain;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.Objects;

/**
 * Armazena uma data civil válida e oferece a representação didática do dia da
 * semana usada no exercício: sábado começa em zero e sexta-feira termina em seis.
 */
public class CalendarDate {
    /** Locale fixa para que os nomes dos dias sejam sempre apresentados em português do Brasil. */
    private static final Locale PORTUGUESE = Locale.forLanguageTag("pt-BR");
    private LocalDate date;

    /** Cria a instância a partir de uma data já representada por {@link LocalDate}. */
    public CalendarDate(LocalDate date) {
        this.date = Objects.requireNonNull(date, "Data obrigatória.");
    }

    /** Cria a instância validando dia, mês e ano pelo calendário gregoriano. */
    public CalendarDate(int day, int month, int year) {
        this.date = validatedDate(day, month, year);
    }

    /** Retorna a data armazenada. */
    public LocalDate getDate() { return date; }
    /** Retorna o dia do mês, entre 1 e 31 conforme o mês. */
    public int getDay() { return date.getDayOfMonth(); }
    /** Retorna o mês numérico, de 1 a 12. */
    public int getMonth() { return date.getMonthValue(); }
    /** Retorna o ano da data armazenada. */
    public int getYear() { return date.getYear(); }

    /** Substitui a data; {@code null} não é permitido. */
    public void setDate(LocalDate date) {
        this.date = Objects.requireNonNull(date, "Data obrigatória.");
    }

    /** Sobrecarga de cadastro para receber uma data pronta. */
    public void setCadastro(LocalDate date) {
        setDate(date);
    }

    /** Cadastra uma data composta e converte datas civis inválidas em erro de validação. */
    public void setCadastro(int day, int month, int year) {
        try {
            setDate(LocalDate.of(year, month, day));
        } catch (DateTimeException ex) {
            throw new IllegalArgumentException("Data civil inválida.", ex);
        }
    }

    /** Retorna sábado=0, domingo=1, segunda=2, ... sexta=6. */
    public int dayOfWeek() {
        return mapDayOfWeek(date);
    }

    /** Calcula o índice do dia da semana para outra data sem alterar o cadastro atual. */
    public int dayOfWeek(int day, int month, int year) {
        return mapDayOfWeek(validatedDate(day, month, year));
    }

    /** Retorna o nome completo do dia atual, em português brasileiro. */
    public String dayOfWeekName() {
        return date.getDayOfWeek().getDisplayName(TextStyle.FULL, PORTUGUESE);
    }

    /** Retorna o nome do dia de uma data informada sem alterar a instância. */
    public String dayOfWeekName(int day, int month, int year) {
        return validatedDate(day, month, year).getDayOfWeek().getDisplayName(TextStyle.FULL, PORTUGUESE);
    }

    /** Cria uma data temporária e centraliza a validação das sobrecargas. */
    private static LocalDate validatedDate(int day, int month, int year) {
        try {
            return LocalDate.of(year, month, day);
        } catch (DateTimeException ex) {
            throw new IllegalArgumentException("Data civil inválida.", ex);
        }
    }

    /** Traduz o enum ISO do Java para o índice definido pela atividade. */
    private static int mapDayOfWeek(LocalDate date) {
        return switch (date.getDayOfWeek()) {
            case SATURDAY -> 0;
            case SUNDAY -> 1;
            case MONDAY -> 2;
            case TUESDAY -> 3;
            case WEDNESDAY -> 4;
            case THURSDAY -> 5;
            case FRIDAY -> 6;
        };
    }

    /** Inclui a data e o dia da semana em português na representação textual. */
    @Override
    public String toString() {
        return "CalendarDate{date=" + date + ", dayOfWeek='" + dayOfWeekName() + "'}";
    }
}

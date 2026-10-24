package br.com.nationaleconomic.domain;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.Objects;

/** Data civil e utilitários do dia da semana no mapeamento didático solicitado. */
public class CalendarDate {
    private static final Locale PORTUGUESE = Locale.forLanguageTag("pt-BR");
    private LocalDate date;

    public CalendarDate(LocalDate date) {
        setDate(date);
    }

    public CalendarDate(int day, int month, int year) {
        setCadastro(day, month, year);
    }

    public LocalDate getDate() { return date; }
    public int getDay() { return date.getDayOfMonth(); }
    public int getMonth() { return date.getMonthValue(); }
    public int getYear() { return date.getYear(); }

    public void setDate(LocalDate date) {
        this.date = Objects.requireNonNull(date, "Data obrigatória.");
    }

    public void setCadastro(LocalDate date) {
        setDate(date);
    }

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

    public int dayOfWeek(int day, int month, int year) {
        return mapDayOfWeek(validatedDate(day, month, year));
    }

    public String dayOfWeekName() {
        return date.getDayOfWeek().getDisplayName(TextStyle.FULL, PORTUGUESE);
    }

    public String dayOfWeekName(int day, int month, int year) {
        return validatedDate(day, month, year).getDayOfWeek().getDisplayName(TextStyle.FULL, PORTUGUESE);
    }

    private static LocalDate validatedDate(int day, int month, int year) {
        try {
            return LocalDate.of(year, month, day);
        } catch (DateTimeException ex) {
            throw new IllegalArgumentException("Data civil inválida.", ex);
        }
    }

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

    @Override
    public String toString() {
        return "CalendarDate{date=" + date + ", dayOfWeek='" + dayOfWeekName() + "'}";
    }
}

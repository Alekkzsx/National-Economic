package legado.modelo;

import legado.abstracoes.RegistroDatadoLegado;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.Locale;

/**
 * Armazena uma data civil válida e oferece a representação didática do dia da
 * semana usada no exercício: sábado começa em zero e sexta-feira termina em seis.
 */
public class DataCalendario extends RegistroDatadoLegado {
    /** Locale fixa para que os nomes dos dias sejam sempre apresentados em português do Brasil. */
    private static final Locale PORTUGUESE = Locale.forLanguageTag("pt-BR");

    /** Cria a instância a partir de uma data já representada por {@link LocalDate}. */
    public DataCalendario(LocalDate data) {
        super(data);
    }

    /** Cria a instância validando dia, mês e ano pelo calendário gregoriano. */
    public DataCalendario(int day, int month, int year) {
        super(dataValidada(day, month, year));
    }

    /** Retorna o dia do mês, entre 1 e 31 conforme o mês. */
    public int getDia() { return getData().getDayOfMonth(); }
    /** Retorna o mês numérico, de 1 e 12. */
    public int getMes() { return getData().getMonthValue(); }
    /** Retorna o ano da data armazenada. */
    public int getAno() { return getData().getYear(); }

    /** Sobrecarga de cadastro para receber uma data pronta. */
    public void setCadastro(LocalDate data) {
        setData(data);
    }

    /** Cadastra uma data composta e converte datas civis inválidas em erro de validação. */
    public void setCadastro(int day, int month, int year) {
        try {
            setData(LocalDate.of(year, month, day));
        } catch (DateTimeException ex) {
            throw new IllegalArgumentException("Data civil inválida.", ex);
        }
    }

    /** Retorna sábado=0, domingo=1, segunda=2, ... sexta=6. */
    public int diaDaSemana() {
        return mapearDiaDaSemana(getData());
    }

    /** Calcula o índice do dia da semana para outra data sem alterar o cadastro atual. */
    public int diaDaSemana(int day, int month, int year) {
        return mapearDiaDaSemana(dataValidada(day, month, year));
    }

    /** Retorna o nome completo do dia atual, em português brasileiro. */
    public String nomeDoDiaDaSemana() {
        return getData().getDayOfWeek().getDisplayName(TextStyle.FULL, PORTUGUESE);
    }

    /** Retorna o nome do dia de uma data informada sem alterar a instância. */
    public String nomeDoDiaDaSemana(int day, int month, int year) {
        return dataValidada(day, month, year).getDayOfWeek().getDisplayName(TextStyle.FULL, PORTUGUESE);
    }

    /** Cria uma data temporária e centraliza a validação das sobrecargas. */
    private static LocalDate dataValidada(int day, int month, int year) {
        try {
            return LocalDate.of(year, month, day);
        } catch (DateTimeException ex) {
            throw new IllegalArgumentException("Data civil inválida.", ex);
        }
    }

    /** Traduz o enum ISO do Java para o índice definido pela atividade. */
    private static int mapearDiaDaSemana(LocalDate data) {
        return switch (data.getDayOfWeek()) {
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
        return "DataCalendario{data=" + getData() + ", dayOfWeek='" + nomeDoDiaDaSemana() + "'}";
    }
}

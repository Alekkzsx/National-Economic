package dominio.abstracoes;

import dominio.util.ValidacaoDominio;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Base abstrata para entidades do domínio que armazenam uma data civil sincronizada
 * ({@code dia}, {@code mes}, {@code ano} e {@link LocalDate}).
 */
public abstract class RegistroDatado {

    private int dia;
    private int mes;
    private int ano;
    private LocalDate data;

    /** Construtor protegido para herança segura sem despacho virtual. */
    protected RegistroDatado() {
    }

    protected RegistroDatado(int dia, int mes, int ano) {
        aplicarDataInterno(ValidacaoDominio.validarData(dia, mes, ano));
    }

    protected RegistroDatado(LocalDate data, String mensagemNulo) {
        aplicarDataInterno(Objects.requireNonNull(data, mensagemNulo));
    }

    /** Atualiza os 4 campos de data atomicamente (final: seguro para uso em construtores). */
    protected final void aplicarDataInterno(LocalDate novaData) {
        this.data = novaData;
        this.dia = novaData.getDayOfMonth();
        this.mes = novaData.getMonthValue();
        this.ano = novaData.getYear();
    }

    /** Hook opcional chamado após alteração de data pelos setters individuais. */
    protected void aoAlterarData(LocalDate novaData) {
    }

    public int getDia() { return dia; }
    public int getMes() { return mes; }
    public int getAno() { return ano; }
    public LocalDate getData() { return data; }

    public void setDia(int dia) {
        LocalDate novaData = ValidacaoDominio.validarData(dia, this.mes, this.ano);
        aplicarDataInterno(novaData);
        aoAlterarData(novaData);
    }

    public void setMes(int mes) {
        LocalDate novaData = ValidacaoDominio.validarData(this.dia, mes, this.ano);
        aplicarDataInterno(novaData);
        aoAlterarData(novaData);
    }

    public void setAno(int ano) {
        LocalDate novaData = ValidacaoDominio.validarData(this.dia, this.mes, ano);
        aplicarDataInterno(novaData);
        aoAlterarData(novaData);
    }

    public void setData(LocalDate data) {
        LocalDate novaData = Objects.requireNonNull(data, "Data é obrigatória.");
        aplicarDataInterno(novaData);
        aoAlterarData(novaData);
    }
}

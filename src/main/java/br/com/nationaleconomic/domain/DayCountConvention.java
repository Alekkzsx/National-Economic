package br.com.nationaleconomic.domain;

/** Convenções para converter a taxa mensal em taxa diária. */
public enum DayCountConvention {
    THIRTY_360(30),
    ACTUAL_365(365),
    ACTUAL_360(360);

    private final int daysPerYear;
    DayCountConvention(int daysPerYear) { this.daysPerYear = daysPerYear; }
    public int daysPerYear() { return daysPerYear; }
}

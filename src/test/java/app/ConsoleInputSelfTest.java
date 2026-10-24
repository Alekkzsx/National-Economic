package app;

import domain.YieldPeriod;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

/** Verificações simples, sem bibliotecas externas, das entradas e saídas do CLI. */
public final class ConsoleInputSelfTest {
    private ConsoleInputSelfTest() { }

    public static void main(String[] args) {
        PrintStream originalOutput = System.out;
        ByteArrayOutputStream capturedOutput = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(capturedOutput, true, StandardCharsets.UTF_8));
            ConsoleInput input = new ConsoleInput(new Scanner(" \nNome\n1250,50\nsim\na cada 30 dias\n"));

            assert input.readRequired("Nome: ").equals("Nome");
            assert input.readPositiveAmount("Valor: ").compareTo(new BigDecimal("1250.50")) == 0;
            assert input.readYesNo("Continuar? ");
            YieldPeriod period = input.readEnumChoice("Rendimento", YieldPeriod.values(),
                    choice -> choice == YieldPeriod.MONTHLY_30_DAYS ? "a cada 30 dias" : choice.name());
            assert period == YieldPeriod.MONTHLY_30_DAYS;

            String cpf = "123.456.789-09";
            assert !CliFormat.maskCpf(cpf).contains("123");
            String safeMessage = CliFormat.messageOf(new IllegalArgumentException("CPF " + cpf + " inválido"));
            assert !safeMessage.contains(cpf);
            assert safeMessage.contains("***.***.***-**");
        } finally {
            System.setOut(originalOutput);
        }
    }
}

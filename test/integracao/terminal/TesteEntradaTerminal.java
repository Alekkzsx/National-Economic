package terminal;

import modelo.PeriodicidadeRendimento;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

/** Verificações simples, sem bibliotecas externas, das entradas e saídas do CLI. */
public final class TesteEntradaTerminal {
    private TesteEntradaTerminal() { }

    public static void main(String[] args) {
        PrintStream originalOutput = System.out;
        ByteArrayOutputStream capturedOutput = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(capturedOutput, true, StandardCharsets.UTF_8));
            EntradaTerminal input = new EntradaTerminal(new Scanner(" \nNome\n1250,50\nsim\na cada 30 dias\n"));

            assert input.lerObrigatorio("Nome: ").equals("Nome");
            assert input.lerValorPositivo("Valor: ").compareTo(new BigDecimal("1250.50")) == 0;
            assert input.lerSimNao("Continuar? ");
            PeriodicidadeRendimento period = input.lerOpcaoEnum("Rendimento", PeriodicidadeRendimento.values(),
                    choice -> choice == PeriodicidadeRendimento.MONTHLY_30_DAYS ? "a cada 30 dias" : choice.nome());
            assert period == PeriodicidadeRendimento.MONTHLY_30_DAYS;

            String cpf = "123.456.789-09";
            assert !FormatadorTerminal.mascararCpf(cpf).contains("123");
            String safeMessage = FormatadorTerminal.mensagemDe(new IllegalArgumentException("CPF " + cpf + " inválido"));
            assert !safeMessage.contains(cpf);
            assert safeMessage.contains("***.***.***-**");
        } finally {
            System.setOut(originalOutput);
        }
    }
}

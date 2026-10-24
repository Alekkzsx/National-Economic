package app;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.text.Normalizer;
import java.util.Scanner;
import java.util.function.Function;

/** Reúne leituras e validações de entrada usadas pelo terminal. */
final class ConsoleInput {
    private final Scanner scanner;

    ConsoleInput(Scanner scanner) {
        this.scanner = scanner;
    }

    /** Lê um campo sem exigir que tenha conteúdo. */
    String readLine(String prompt) {
        System.out.print(prompt);
        if (!scanner.hasNextLine()) {
            throw new EndOfInputException();
        }
        return scanner.nextLine();
    }

    /** Repete a leitura até receber um valor não vazio. */
    String readRequired(String prompt) {
        while (true) {
            String value = readLine(prompt).trim();
            if (!value.isEmpty()) {
                return value;
            }
            System.out.println("O campo é obrigatório.");
        }
    }

    /** Lê um inteiro dentro dos limites indicados. */
    int readInt(String prompt, int min, int max) {
        while (true) {
            try {
                int value = Integer.parseInt(readLine(prompt).trim());
                if (value >= min && value <= max) {
                    return value;
                }
            } catch (NumberFormatException ignored) {
                // Formato inválido; a mensagem comum informa o intervalo esperado.
            }
            System.out.printf("Informe um número inteiro entre %d e %d.%n", min, max);
        }
    }

    /** Lê um inteiro longo dentro dos limites indicados. */
    long readLong(String prompt, long min, long max) {
        while (true) {
            try {
                long value = Long.parseLong(readLine(prompt).trim());
                if (value >= min && value <= max) {
                    return value;
                }
            } catch (NumberFormatException ignored) {
                // Formato inválido; a mensagem comum informa o intervalo esperado.
            }
            System.out.printf("Informe um número inteiro entre %d e %d.%n", min, max);
        }
    }

    /** Lê um valor monetário positivo. */
    BigDecimal readPositiveAmount(String prompt) {
        while (true) {
            BigDecimal value = readDecimal(prompt);
            if (value.signum() > 0) {
                return value;
            }
            System.out.println("O valor deve ser maior que zero.");
        }
    }

    /** Lê uma taxa mensal entre zero e um, inclusive. */
    BigDecimal readMonthlyRate() {
        while (true) {
            BigDecimal value = readDecimal("Taxa mensal entre 0 e 1 (ex.: 0,05 para 5%): ");
            if (value.signum() >= 0 && value.compareTo(BigDecimal.ONE) <= 0) {
                return value;
            }
            System.out.println("A taxa mensal deve estar entre 0 e 1.");
        }
    }

    /** Aceita ponto ou vírgula como separador decimal. */
    private BigDecimal readDecimal(String prompt) {
        while (true) {
            String value = readLine(prompt).trim().replace(',', '.');
            try {
                return new BigDecimal(value);
            } catch (NumberFormatException ignored) {
                System.out.println("Valor inválido. Informe um número decimal, por exemplo 1250,50.");
            }
        }
    }

    /** Lê uma data no formato ISO (AAAA-MM-DD). */
    LocalDate readDate(String prompt) {
        while (true) {
            try {
                return LocalDate.parse(readLine(prompt).trim());
            } catch (DateTimeParseException ignored) {
                System.out.println("Data inválida. Use o formato AAAA-MM-DD.");
            }
        }
    }

    /** Interpreta respostas afirmativas e negativas em português. */
    boolean readYesNo(String prompt) {
        while (true) {
            String answer = readLine(prompt).trim().toLowerCase();
            if (answer.equals("s") || answer.equals("sim")) {
                return true;
            }
            if (answer.equals("n") || answer.equals("não") || answer.equals("nao")) {
                return false;
            }
            System.out.println("Responda s ou n.");
        }
    }

    /** Lê um caminho local digitado pelo usuário. */
    Path readPath(String prompt) {
        return Path.of(readRequired(prompt));
    }

    /** Lê uma opção por número, nome do enum ou descrição localizada. */
    <E extends Enum<E>> E readEnumChoice(String field, E[] choices, Function<E, String> label) {
        System.out.println(field + " — escolha pelo número ou nome:");
        for (int index = 0; index < choices.length; index++) {
            E choice = choices[index];
            System.out.printf("  %d. %s (%s)%n", index + 1, choice.name(), label.apply(choice));
        }
        while (true) {
            String answer = readRequired(field + ": ").trim();
            try {
                int index = Integer.parseInt(answer);
                if (index >= 1 && index <= choices.length) {
                    return choices[index - 1];
                }
            } catch (NumberFormatException ignored) {
                // Também são aceitos os nomes e rótulos exibidos acima.
            }
            String normalized = normalizeChoice(answer);
            for (E choice : choices) {
                if (normalizeChoice(choice.name()).equals(normalized)
                        || normalizeChoice(label.apply(choice)).equals(normalized)) {
                    return choice;
                }
            }
            System.out.println("Escolha inválida. Informe um dos números ou nomes exibidos.");
        }
    }

    /** Normaliza acentos e pontuação para comparar opções digitadas. */
    private static String normalizeChoice(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^A-Za-z0-9]+", "_")
                .replaceAll("^_+|_+$", "")
                .toUpperCase();
    }

    /** Indica que a entrada padrão foi fechada. */
    static final class EndOfInputException extends RuntimeException {
        private EndOfInputException() { }
    }
}

package terminal;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.text.Normalizer;
import java.util.Scanner;
import java.util.function.Function;

/** Reúne leituras e validações de entrada usadas pelo terminal. */
public final class EntradaTerminal {
    private final Scanner scanner;

    public EntradaTerminal(Scanner scanner) {
        this.scanner = scanner;
    }

    /** Lê um campo sem exigir que tenha conteúdo. */
    public String lerLinha(String prompt) {
        System.out.print(prompt);
        if (!scanner.hasNextLine()) {
            throw new EndOfInputException();
        }
        return scanner.nextLine();
    }

    /** Repete a leitura até receber um valor não vazio. */
    public String lerObrigatorio(String prompt) {
        while (true) {
            String value = lerLinha(prompt).trim();
            if (!value.isEmpty()) {
                return value;
            }
            System.out.println("O campo é obrigatório.");
        }
    }

    /** Lê um inteiro dentro dos limites indicados. */
    public int lerInteiro(String prompt, int min, int max) {
        while (true) {
            try {
                int value = Integer.parseInt(lerLinha(prompt).trim());
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
    public long lerInteiroLongo(String prompt, long min, long max) {
        while (true) {
            try {
                long value = Long.parseLong(lerLinha(prompt).trim());
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
    public BigDecimal lerValorPositivo(String prompt) {
        while (true) {
            BigDecimal value = lerDecimal(prompt);
            if (value.signum() > 0) {
                return value;
            }
            System.out.println("O valor deve ser maior que zero.");
        }
    }

    /** Lê uma taxa mensal entre zero e um, inclusive. */
    public BigDecimal lerTaxaMensal() {
        while (true) {
            BigDecimal value = lerDecimal("Taxa mensal entre 0 e 1 (ex.: 0,05 para 5%): ");
            if (value.signum() >= 0 && value.compareTo(BigDecimal.ONE) <= 0) {
                return value;
            }
            System.out.println("A taxa mensal deve estar entre 0 e 1.");
        }
    }

    /** Aceita ponto ou vírgula como separador decimal. */
    private BigDecimal lerDecimal(String prompt) {
        while (true) {
            String value = lerLinha(prompt).trim().replace(',', '.');
            try {
                return new BigDecimal(value);
            } catch (NumberFormatException ignored) {
                System.out.println("Valor inválido. Informe um número decimal, por exemplo 1250,50.");
            }
        }
    }

    /** Lê uma data no formato ISO (AAAA-MM-DD). */
    public LocalDate lerData(String prompt) {
        while (true) {
            try {
                return LocalDate.parse(lerLinha(prompt).trim());
            } catch (DateTimeParseException ignored) {
                System.out.println("Data inválida. Use o formato AAAA-MM-DD.");
            }
        }
    }

    /** Interpreta respostas afirmativas e negativas em português. */
    public boolean lerSimNao(String prompt) {
        while (true) {
            String answer = lerLinha(prompt).trim().toLowerCase();
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
    public Path lerCaminho(String prompt) {
        return Path.of(lerObrigatorio(prompt));
    }

    /** Lê uma opção por número, nome do enum ou descrição localizada. */
    public <E extends Enum<E>> E lerOpcaoEnum(String field, E[] choices, Function<E, String> label) {
        System.out.println(field + " — escolha pelo número ou nome:");
        for (int index = 0; index < choices.length; index++) {
            E choice = choices[index];
            System.out.printf("  %d. %s (%s)%n", index + 1, choice.nome(), label.apply(choice));
        }
        while (true) {
            String answer = lerObrigatorio(field + ": ").trim();
            try {
                int index = Integer.parseInt(answer);
                if (index >= 1 && index <= choices.length) {
                    return choices[index - 1];
                }
            } catch (NumberFormatException ignored) {
                // Também são aceitos os nomes e rótulos exibidos acima.
            }
            String normalized = normalizarOpcao(answer);
            for (E choice : choices) {
                if (normalizarOpcao(choice.nome()).equals(normalized)
                        || normalizarOpcao(label.apply(choice)).equals(normalized)) {
                    return choice;
                }
            }
            System.out.println("Escolha inválida. Informe um dos números ou nomes exibidos.");
        }
    }

    /** Normaliza acentos e pontuação para comparar opções digitadas. */
    private static String normalizarOpcao(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^A-Za-z0-9]+", "_")
                .replaceAll("^_+|_+$", "")
                .toUpperCase();
    }

    /** Indica que a entrada padrão foi fechada. */
    public static final class EndOfInputException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public EndOfInputException() { }
    }
}

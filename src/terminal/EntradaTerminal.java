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
    private final Scanner leitor;

    public EntradaTerminal(Scanner leitor) {
        this.leitor = leitor;
    }

    /** Lê um campo sem exigir que tenha conteúdo. */
    public String lerLinha(String mensagem) {
        System.out.print(mensagem);
        if (!leitor.hasNextLine()) {
            throw new EndOfInputException();
        }
        return leitor.nextLine();
    }

    /** Repete a leitura até receber um valor não vazio. */
    public String lerObrigatorio(String mensagem) {
        while (true) {
            String valor = lerLinha(mensagem).trim();
            if (!valor.isEmpty()) {
                return valor;
            }
            System.out.println("O campo é obrigatório.");
        }
    }

    /** Lê um inteiro dentro dos limites indicados. */
    public int lerInteiro(String mensagem, int min, int max) {
        while (true) {
            try {
                int valor = Integer.parseInt(lerLinha(mensagem).trim());
                if (valor >= min && valor <= max) {
                    return valor;
                }
            } catch (NumberFormatException ignored) {
                // Formato inválido; a mensagem comum informa o intervalo esperado.
            }
            System.out.printf("Informe um número inteiro entre %d e %d.%n", min, max);
        }
    }

    /** Lê um inteiro longo dentro dos limites indicados. */
    public long lerInteiroLongo(String mensagem, long min, long max) {
        while (true) {
            try {
                long valor = Long.parseLong(lerLinha(mensagem).trim());
                if (valor >= min && valor <= max) {
                    return valor;
                }
            } catch (NumberFormatException ignored) {
                // Formato inválido; a mensagem comum informa o intervalo esperado.
            }
            System.out.printf("Informe um número inteiro entre %d e %d.%n", min, max);
        }
    }

    /** Lê um valor monetário positivo. */
    public BigDecimal lerValorPositivo(String mensagem) {
        while (true) {
            BigDecimal valor = lerDecimal(mensagem);
            if (valor.signum() > 0) {
                return valor;
            }
            System.out.println("O valor deve ser maior que zero.");
        }
    }

    /** Lê uma taxa mensal entre zero e um, inclusive. */
    public BigDecimal lerTaxaMensal() {
        while (true) {
            BigDecimal valor = lerDecimal("Taxa mensal entre 0 e 1 (ex.: 0,05 para 5%): ");
            if (valor.signum() >= 0 && valor.compareTo(BigDecimal.ONE) <= 0) {
                return valor;
            }
            System.out.println("A taxa mensal deve estar entre 0 e 1.");
        }
    }

    /** Aceita ponto ou vírgula como separador decimal. */
    private BigDecimal lerDecimal(String mensagem) {
        while (true) {
            String valor = lerLinha(mensagem).trim().replace(',', '.');
            try {
                return new BigDecimal(valor);
            } catch (NumberFormatException ignored) {
                System.out.println("Valor inválido. Informe um número decimal, por exemplo 1250,50.");
            }
        }
    }

    /** Lê uma data no formato ISO (AAAA-MM-DD). */
    public LocalDate lerData(String mensagem) {
        while (true) {
            try {
                return LocalDate.parse(lerLinha(mensagem).trim());
            } catch (DateTimeParseException ignored) {
                System.out.println("Data inválida. Use o formato AAAA-MM-DD.");
            }
        }
    }

    /** Interpreta respostas afirmativas e negativas em português. */
    public boolean lerSimNao(String mensagem) {
        while (true) {
            String resposta = lerLinha(mensagem).trim().toLowerCase();
            if (resposta.equals("s") || resposta.equals("sim")) {
                return true;
            }
            if (resposta.equals("n") || resposta.equals("não") || resposta.equals("nao")) {
                return false;
            }
            System.out.println("Responda s ou n.");
        }
    }

    /** Lê um caminho local digitado pelo usuário. */
    public Path lerCaminho(String mensagem) {
        return Path.of(lerObrigatorio(mensagem));
    }

    /** Lê uma opção por número, nome do enum ou descrição localizada. */
    public <E extends Enum<E>> E lerOpcaoEnum(String campo, E[] opcoes, Function<E, String> rotulo) {
        System.out.println(campo + " — escolha pelo número ou nome:");
        for (int indice = 0; indice < opcoes.length; indice++) {
            E opcao = opcoes[indice];
            System.out.printf("  %d. %s (%s)%n", indice + 1, opcao.name(), rotulo.apply(opcao));
        }
        while (true) {
            String resposta = lerObrigatorio(campo + ": ").trim();
            try {
                int indice = Integer.parseInt(resposta);
                if (indice >= 1 && indice <= opcoes.length) {
                    return opcoes[indice - 1];
                }
            } catch (NumberFormatException ignored) {
                // Também são aceitos os nomes e rótulos exibidos acima.
            }
            String normalizado = normalizarOpcao(resposta);
            for (E opcao : opcoes) {
                if (normalizarOpcao(opcao.name()).equals(normalizado)
                        || normalizarOpcao(rotulo.apply(opcao)).equals(normalizado)) {
                    return opcao;
                }
            }
            System.out.println("Escolha inválida. Informe um dos números ou nomes exibidos.");
        }
    }

    /** Normaliza acentos e pontuação para comparar opções digitadas. */
    private static String normalizarOpcao(String valor) {
        return Normalizer.normalize(valor, Normalizer.Form.NFD)
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

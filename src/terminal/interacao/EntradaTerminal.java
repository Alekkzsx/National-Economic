package terminal.interacao;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Scanner;
import java.util.function.Function;

/** Reúne leituras e validações simples de entrada usadas pelo terminal. */
public final class EntradaTerminal {

    private final Scanner leitor;

    public EntradaTerminal(Scanner leitor) {
        this.leitor = leitor;
    }

    /** Lê uma linha sem exigir conteúdo. */
    public String lerLinha(String mensagem) {
        System.out.print(mensagem);
        if (!leitor.hasNextLine()) {
            throw new EndOfInputException();
        }
        return leitor.nextLine();
    }

    /** Lê uma linha usando {@code padrao} quando o usuário pressiona Enter em branco. */
    public String lerLinhaComPadrao(String mensagem, String padrao) {
        String valor = lerLinha(mensagem).trim();
        return valor.isEmpty() ? padrao : valor;
    }

    /** Repete a leitura até receber um texto não vazio. */
    public String lerObrigatorio(String mensagem) {
        while (true) {
            String valor = lerLinha(mensagem).trim();
            if (!valor.isEmpty()) {
                return valor;
            }
            System.out.println("O campo é obrigatório.");
        }
    }

    /** Lê um inteiro dentro do intervalo {@code [min, max]}. */
    public int lerInteiro(String mensagem, int min, int max) {
        while (true) {
            Integer valor = tentarLerInteiroNoIntervalo(lerLinha(mensagem).trim(), min, max);
            if (valor != null) {
                return valor;
            }
            System.out.printf("Informe um número inteiro entre %d e %d.%n", min, max);
        }
    }

    /** Lê um inteiro dentro de {@code [min, max]}, aceitando {@code padrao} em entrada vazia. */
    public int lerInteiroComPadrao(String mensagem, int min, int max, int padrao) {
        while (true) {
            String texto = lerLinha(mensagem).trim();
            if (texto.isEmpty()) {
                return padrao;
            }
            Integer valor = tentarLerInteiroNoIntervalo(texto, min, max);
            if (valor != null) {
                return valor;
            }
            System.out.printf("Informe um número inteiro entre %d e %d.%n", min, max);
        }
    }

    private static Integer tentarLerInteiroNoIntervalo(String texto, int min, int max) {
        try {
            int valor = Integer.parseInt(texto);
            return (valor >= min && valor <= max) ? valor : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    /** Lê um inteiro longo dentro do intervalo {@code [min, max]}. */
    public long lerInteiroLongo(String mensagem, long min, long max) {
        while (true) {
            try {
                long valor = Long.parseLong(lerLinha(mensagem).trim());
                if (valor >= min && valor <= max) {
                    return valor;
                }
            } catch (NumberFormatException ignored) {
                // Formato inválido; exibe intervalo esperado abaixo
            }
            System.out.printf("Informe um número inteiro entre %d e %d.%n", min, max);
        }
    }

    /** Lê um valor monetário estritamente positivo (> 0). */
    public BigDecimal lerValorPositivo(String mensagem) {
        while (true) {
            BigDecimal valor = lerDecimal(mensagem);
            if (valor.signum() > 0) {
                return valor;
            }
            System.out.println("O valor deve ser maior que zero.");
        }
    }

    /** Lê uma taxa mensal fracionária entre 0 e 1, inclusive. */
    public BigDecimal lerTaxaMensal() {
        while (true) {
            BigDecimal valor = lerDecimal("Taxa mensal entre 0 e 1 (ex.: 0,05 para 5%): ");
            if (valor.signum() >= 0 && valor.compareTo(BigDecimal.ONE) <= 0) {
                return valor;
            }
            System.out.println("A taxa mensal deve estar entre 0 e 1.");
        }
    }

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

    /** Lê uma data no formato ISO ({@code AAAA-MM-DD}). */
    public LocalDate lerData(String mensagem) {
        while (true) {
            try {
                return LocalDate.parse(lerLinha(mensagem).trim());
            } catch (DateTimeParseException ignored) {
                System.out.println("Data inválida. Use o formato AAAA-MM-DD.");
            }
        }
    }

    /** Interpreta respostas afirmativas (s/sim) e negativas (n/não/nao). */
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

    /** Lê um caminho de arquivo local. */
    public Path lerCaminho(String mensagem) {
        return Path.of(lerObrigatorio(mensagem));
    }

    /**
     * Lê uma opção de enum pelo índice numérico, nome da constante ou rótulo em português.
     *
     * @param <E> tipo do enum exibido
     * @param campo título do campo solicitado ao usuário
     * @param opcoes valores possíveis do enum
     * @param rotulo função que devolve a descrição legível de cada constante
     * @return constante escolhida pelo usuário
     */
    public <E extends Enum<E>> E lerOpcaoEnum(String campo, E[] opcoes, Function<E, String> rotulo) {
        System.out.println(campo + " — escolha pelo número ou nome:");
        for (int i = 0; i < opcoes.length; i++) {
            System.out.printf("  %d. %s (%s)%n", i + 1, opcoes[i].name(), rotulo.apply(opcoes[i]));
        }
        while (true) {
            String resposta = lerObrigatorio(campo + ": ").trim();
            E escolhida = resolverPorIndice(resposta, opcoes);
            if (escolhida == null) {
                escolhida = resolverPorNomeOuRotulo(resposta, opcoes, rotulo);
            }
            if (escolhida != null) {
                return escolhida;
            }
            System.out.println("Escolha inválida. Informe um dos números ou nomes exibidos.");
        }
    }

    private static <E extends Enum<E>> E resolverPorIndice(String resposta, E[] opcoes) {
        Integer indice = tentarLerInteiroNoIntervalo(resposta, 1, opcoes.length);
        return indice == null ? null : opcoes[indice - 1];
    }

    private static <E extends Enum<E>> E resolverPorNomeOuRotulo(
            String resposta, E[] opcoes, Function<E, String> rotulo) {
        String normalizado = normalizarOpcao(resposta);
        for (E opcao : opcoes) {
            if (normalizarOpcao(opcao.name()).equals(normalizado)
                    || normalizarOpcao(rotulo.apply(opcao)).equals(normalizado)) {
                return opcao;
            }
        }
        return null;
    }

    // Normaliza em NFD e remove marcas diacríticas (\p{M}) para aceitar entradas com ou sem acento
    private static String normalizarOpcao(String valor) {
        return Normalizer.normalize(valor, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^A-Za-z0-9]+", "_")
                .replaceAll("^_+|_+$", "")
                .toUpperCase();
    }

    /** Indica encerramento da entrada padrão (EOF), permitindo saída limpa do laço interativo. */
    public static final class EndOfInputException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public EndOfInputException() {
        }
    }
}

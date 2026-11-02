package test.servico;

import static test.Afirmacao.*;

import dominio.AplicacaoFinanceira;
import dominio.Cliente;
import dominio.ConvencaoContagemDias;
import dominio.DADOSPESSOAIS;
import dominio.MultiplasOperacoesFinanceiras;
import dominio.OperacaoFinanceira;
import dominio.RegimeTributario;
import dominio.TipoOperacao;
import servico.ArmazenadorArquivo;
import servico.SGI;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Suíte de testes autônomos para o Sistema de Gestão de Investimentos {@link SGI}
 * e persistência atômica {@link ArmazenadorArquivo} (Item 7 da atividade didática).
 *
 * <p>Cobre exaustivamente os 5 Tiers de qualidade definidos em TEST_INFRA.md:
 * contratos básicos, limites regulamentares (estritamente 100 clientes e rejeição do 101º),
 * busca com e sem máscara, atualização em lote restrita a dias úteis, apuração de saldos,
 * extratos individuais e consolidados polimórficos, persistência atômica TSV v3,
 * permissões POSIX 0600, concorrência multithread e resiliência a arquivos corrompidos.</p>
 */
public final class TesteSGI {

    public static void main(String[] args) {
        System.out.println(">>> Executando TesteSGI (Item 7 - SGI e Persistência de Dados TSV v3)...");
        long inicio = System.currentTimeMillis();
        int testesExecutados = 0;

        // =====================================================================
        // Tier 1: Sanidade, Contratos Básicos e Caminho Principal (Happy Path)
        // =====================================================================
        test01_instanciacaoEGettersIniciais(); testesExecutados++;
        test02_cadastrarClienteComDadosPessoais(); testesExecutados++;
        test03_cadastrarClienteInstanciaPronta(); testesExecutados++;
        test04_cadastrarAplicacaoEBuscar(); testesExecutados++;
        test05_cadastrarOperacaoDepositoERetirada(); testesExecutados++;
        test06_buscarClienteComESemFormatacaoCPF(); testesExecutados++;
        test07_consultaSaldoIndividualEConsolidadoCliente(); testesExecutados++;
        test08_consultaSaldoGlobalSGI(); testesExecutados++;

        // =====================================================================
        // Tier 2: Fronteira, Limites e Casos de Canto (Boundary / Edge Cases)
        // =====================================================================
        test09_limiteExato100Clientes(); testesExecutados++;
        test10_rejeicaoCentoEPrimeiroClienteComIllegalStateException(); testesExecutados++;
        test11_rejeicaoClienteDuplicadoMesmoCPF(); testesExecutados++;
        test12_rejeicaoAplicacaoDuplicadaGlobalmenteNoSGI(); testesExecutados++;
        test13_buscarClienteInexistenteRetornaOptionalEmpty(); testesExecutados++;
        test14_operacoesParaClienteInexistenteLancaIllegalArgumentException(); testesExecutados++;
        test15_operacoesParaAplicacaoInexistenteLancaIllegalArgumentException(); testesExecutados++;
        test16_rejeicaoParametrosNulos(); testesExecutados++;
        test17_imutabilidadeListaClientes(); testesExecutados++;

        // =====================================================================
        // Tier 3: Integração Par a Par (Cross-Feature Pairwise)
        // =====================================================================
        test18_atualizacaoEmLoteRestritaADiaUtil(); testesExecutados++;
        test19_extratoIndividualUltimos30Dias(); testesExecutados++;
        test20_extratoIndividualPorIntervaloPolimorfico(); testesExecutados++;
        test21_extratoConsolidadoClienteUltimos30Dias(); testesExecutados++;
        test22_extratoConsolidadoClientePorIntervaloPolimorfico(); testesExecutados++;
        test23_extratoConsolidadoGlobalSGIUltimos30Dias(); testesExecutados++;
        test24_extratoConsolidadoGlobalSGIPorIntervaloPolimorfico(); testesExecutados++;
        test25_rejeicaoIntervaloDatasInvertido(); testesExecutados++;

        // =====================================================================
        // Tier 4: Cenários do Mundo Real e Aceitação Ponta a Ponta
        // =====================================================================
        test26_cenarioCicloCompletoMultiplosClientesCarteiras(); testesExecutados++;
        test27_persistenciaArquivoSalvarELerDadosCustomPath(); testesExecutados++;
        test28_persistenciaArquivoSalvarELerDadosCaminhoPadrao(); testesExecutados++;
        test29_roundTripMultiplosCiclosIntegridade(); testesExecutados++;

        // =====================================================================
        // Tier 5: Endurecimento Adversarial, Estresse, Concorrência e Robustez
        // =====================================================================
        test30_herancaSeguraSGI(); testesExecutados++;
        test31_concorrenciaMultithreadBasicaThreadSafety(); testesExecutados++;
        test32_resilienciaPermissoesPOSIX0600(); testesExecutados++;
        test33_resilienciaEscapamentoCaracteresEspeciaisTSV(); testesExecutados++;
        test34_rejeicaoArquivoCorrompidoOuInvalido(); testesExecutados++;
        test35_atualizacaoCotacoesIntegracaoM4(); testesExecutados++;

        long duracao = System.currentTimeMillis() - inicio;
        System.out.printf(">>> TesteSGI CONCLUÍDO com SUCESSO: %d métodos verificados (%d ms)%n%n",
                testesExecutados, duracao);
    }

    // =========================================================================
    // Métodos Utilitários de Apoio
    // =========================================================================

    /**
     * Gera um CPF matematicamente válido e determinístico para testes com base em um índice sequencial.
     *
     * @param indice número sequencial de 1 a 999
     * @return CPF com 11 dígitos numéricos com dígitos verificadores rigorosamente calculados
     */
    public static String gerarCpfValido(int indice) {
        int numeroBase = 100000000 + indice;
        String base = String.format("%09d", numeroBase);
        int soma1 = 0;
        for (int i = 0; i < 9; i++) {
            soma1 += (base.charAt(i) - '0') * (10 - i);
        }
        int d1 = (soma1 * 10) % 11;
        if (d1 == 10) d1 = 0;

        int soma2 = 0;
        for (int i = 0; i < 9; i++) {
            soma2 += (base.charAt(i) - '0') * (11 - i);
        }
        soma2 += d1 * 2;
        int d2 = (soma2 * 10) % 11;
        if (d2 == 10) d2 = 0;

        return base + d1 + d2;
    }

    private static Cliente criarClienteValido(int indice) {
        String cpf = gerarCpfValido(indice);
        DADOSPESSOAIS dados = new DADOSPESSOAIS("Cliente Teste " + indice, cpf, "11988887777");
        return new Cliente(dados, 1000 + (indice % 100), 50000L + indice);
    }

    private static MultiplasOperacoesFinanceiras criarAplicacaoPadrao(String numero, BigDecimal valor, LocalDate data) {
        AplicacaoFinanceira af = new AplicacaoFinanceira("CDB " + numero, numero, valor, new BigDecimal("0.01"), data);
        return new MultiplasOperacoesFinanceiras(af);
    }

    // =========================================================================
    // Tier 1: Sanidade, Contratos Básicos e Caminho Principal (Happy Path)
    // =========================================================================

    private static void test01_instanciacaoEGettersIniciais() {
        SGI sgi = new SGI();
        afirmarIgual(0, sgi.getQuantidadeClientes(), "Quantidade inicial de clientes deve ser 0");
        afirmarIgual(new BigDecimal("0.00"), sgi.getSaldo(), "Saldo total inicial deve ser 0.00");
        afirmarIgual(new BigDecimal("0.00"), sgi.getSaldoBruto(), "Saldo bruto total inicial deve ser 0.00");
        afirmarVerdadeiro(sgi.getClientes().isEmpty(), "Lista de clientes deve iniciar vazia");
        afirmarVerdadeiro(sgi.getExtrato().isEmpty(), "Extrato global deve iniciar vazio");
        afirmarVerdadeiro(sgi.getCotacoes().isEmpty(), "Mapa de cotações deve iniciar vazio");

        SGI outro = SGI.criarNovo();
        afirmarNaoNulo(outro, "criarNovo deve instanciar SGI");
        afirmarIgual(0, outro.getQuantidadeClientes(), "Nova instância criada via factory deve ter 0 clientes");
    }

    private static void test02_cadastrarClienteComDadosPessoais() {
        SGI sgi = new SGI();
        DADOSPESSOAIS dados = new DADOSPESSOAIS("Carlos Silva", "52998224725", "11988887777");
        sgi.cadastrarCliente(dados, 1001, 20005L);

        afirmarIgual(1, sgi.getQuantidadeClientes(), "Deve haver 1 cliente cadastrado");
        Optional<Cliente> opt = sgi.buscarCliente("52998224725");
        afirmarVerdadeiro(opt.isPresent(), "Cliente deve ser localizado por CPF");
        Cliente c = opt.get();
        afirmarIgual("Carlos Silva", c.getDadosPessoais().getNome(), "Nome deve coincidir");
        afirmarIgual(1001, c.getNumeroAgencia(), "Agência deve coincidir");
        afirmarIgual(20005L, c.getNumeroConta(), "Conta deve coincidir");
    }

    private static void test03_cadastrarClienteInstanciaPronta() {
        SGI sgi = new SGI();
        String cpf2 = gerarCpfValido(2);
        Cliente c = new Cliente("Mariana Souza", cpf2, "11977776666", 1002, 30010L);
        sgi.cadastrarCliente(c);

        afirmarIgual(1, sgi.getQuantidadeClientes(), "Quantidade deve ser 1");
        Cliente recuperado = sgi.requererCliente(cpf2);
        afirmarIgual("Mariana Souza", recuperado.getDadosPessoais().getNome(), "Nome deve conferir");
    }

    private static void test04_cadastrarAplicacaoEBuscar() {
        SGI sgi = new SGI();
        Cliente c = new Cliente("Roberto Lima", "52998224725", "11988887777", 1001, 20005L);
        sgi.cadastrarCliente(c);

        AplicacaoFinanceira af = new AplicacaoFinanceira("CDB Ouro", "APP-101", new BigDecimal("5000.00"), new BigDecimal("0.01"), LocalDate.of(2026, 1, 1));
        sgi.cadastrarAplicacao("52998224725", af);

        afirmarIgual(1, sgi.getAplicacoes("52998224725").size(), "Cliente deve possuir 1 aplicação");
        Optional<MultiplasOperacoesFinanceiras> buscada = sgi.buscarAplicacao("APP-101");
        afirmarVerdadeiro(buscada.isPresent(), "Aplicação deve ser encontrada globalmente no SGI");
        afirmarIgual("CDB Ouro", buscada.get().getAplicacao().getNomeAplicacao(), "Nome da aplicação confere");
    }

    private static void test05_cadastrarOperacaoDepositoERetirada() {
        SGI sgi = new SGI();
        Cliente c = new Cliente("Ana Paula", "52998224725", "11988887777", 1001, 20005L);
        sgi.cadastrarCliente(c);

        AplicacaoFinanceira af = new AplicacaoFinanceira("CDB DI", "APP-102", new BigDecimal("1000.00"), new BigDecimal("0.01"), LocalDate.of(2026, 1, 1));
        sgi.cadastrarAplicacao("52998224725", af);

        OperacaoFinanceira opDep = new OperacaoFinanceira(LocalDate.of(2026, 1, 5), new BigDecimal("500.00"), "Aporte adicional", TipoOperacao.DEPOSITO);
        sgi.cadastrarOperacao("52998224725", "APP-102", opDep);

        OperacaoFinanceira opRet = new OperacaoFinanceira(LocalDate.of(2026, 1, 10), new BigDecimal("200.00"), "Resgate parcial", TipoOperacao.RETIRADA);
        sgi.cadastrarOperacao("52998224725", "APP-102", opRet);

        BigDecimal saldo = sgi.getSaldo("52998224725", "APP-102");
        afirmarIgual(new BigDecimal("1300.00"), saldo, "Saldo deve ser 1000 + 500 - 200 = 1300.00");
    }

    private static void test06_buscarClienteComESemFormatacaoCPF() {
        SGI sgi = new SGI();
        Cliente c = new Cliente("Beatriz Dias", "52998224725", "11988887777", 1001, 20005L);
        sgi.cadastrarCliente(c);

        afirmarVerdadeiro(sgi.buscarCliente("52998224725").isPresent(), "Busca direta com 11 dígitos");
        afirmarVerdadeiro(sgi.buscarCliente("529.982.247-25").isPresent(), "Busca formatada com pontos e traço");
        afirmarVerdadeiro(sgi.buscarCliente(" 52998224725 ").isPresent(), "Busca com espaços externos");
        afirmarVerdadeiro(sgi.buscarCliente("529.982.247/25").isPresent(), "Busca com caracteres diversos");
    }

    private static void test07_consultaSaldoIndividualEConsolidadoCliente() {
        SGI sgi = new SGI();
        Cliente c = new Cliente("Marcos Souza", "52998224725", "11988887777", 1001, 20005L);
        sgi.cadastrarCliente(c);

        AplicacaoFinanceira af1 = new AplicacaoFinanceira("CDB A", "APP-1", new BigDecimal("1000.00"), new BigDecimal("0.01"), LocalDate.of(2026, 1, 1));
        AplicacaoFinanceira af2 = new AplicacaoFinanceira("CDB B", "APP-2", new BigDecimal("2500.00"), new BigDecimal("0.01"), LocalDate.of(2026, 1, 1));
        sgi.cadastrarAplicacao("52998224725", af1);
        sgi.cadastrarAplicacao("52998224725", af2);

        afirmarIgual(new BigDecimal("1000.00"), sgi.getSaldo("52998224725", "APP-1"), "Saldo individual terminal 1");
        afirmarIgual(new BigDecimal("2500.00"), sgi.getSaldo("52998224725", "APP-2"), "Saldo individual terminal 2");
        afirmarIgual(new BigDecimal("3500.00"), sgi.getSaldo("52998224725"), "Saldo consolidado cliente");
        afirmarIgual(new BigDecimal("3500.00"), sgi.getSaldoBruto("52998224725"), "Saldo bruto consolidado cliente");
    }

    private static void test08_consultaSaldoGlobalSGI() {
        SGI sgi = new SGI();
        String cpf2 = gerarCpfValido(2);
        Cliente c1 = new Cliente("Cliente 1", "52998224725", "11911111111", 1001, 1001L);
        Cliente c2 = new Cliente("Cliente 2", cpf2, "11922222222", 1002, 1002L);
        sgi.cadastrarCliente(c1);
        sgi.cadastrarCliente(c2);

        sgi.cadastrarAplicacao("52998224725", new AplicacaoFinanceira("App1", "1", new BigDecimal("1000.00"), new BigDecimal("0.01"), LocalDate.of(2026, 1, 1)));
        sgi.cadastrarAplicacao("52998224725", new AplicacaoFinanceira("App2", "2", new BigDecimal("2000.00"), new BigDecimal("0.01"), LocalDate.of(2026, 1, 1)));
        sgi.cadastrarAplicacao(cpf2, new AplicacaoFinanceira("App3", "3", new BigDecimal("3000.00"), new BigDecimal("0.01"), LocalDate.of(2026, 1, 1)));
        sgi.cadastrarAplicacao(cpf2, new AplicacaoFinanceira("App4", "4", new BigDecimal("4000.00"), new BigDecimal("0.01"), LocalDate.of(2026, 1, 1)));

        afirmarIgual(new BigDecimal("10000.00"), sgi.getSaldo(), "Saldo total do sistema deve ser 10000.00");
        afirmarIgual(new BigDecimal("10000.00"), sgi.getSaldoBruto(), "Saldo bruto total do sistema deve ser 10000.00");
    }

    // =========================================================================
    // Tier 2: Fronteira, Limites e Casos de Canto (Boundary / Edge Cases)
    // =========================================================================

    private static void test09_limiteExato100Clientes() {
        SGI sgi = new SGI();
        for (int i = 1; i <= 100; i++) {
            sgi.cadastrarCliente(criarClienteValido(i));
        }
        afirmarIgual(100, sgi.getQuantidadeClientes(), "Deve haver exatamente 100 clientes cadastrados");
        afirmarIgual(100, sgi.getClientes().size(), "Lista de clientes deve conter 100 elementos");
    }

    private static void test10_rejeicaoCentoEPrimeiroClienteComIllegalStateException() {
        SGI sgi = new SGI();
        for (int i = 1; i <= 100; i++) {
            sgi.cadastrarCliente(criarClienteValido(i));
        }
        afirmarIgual(100, sgi.getQuantidadeClientes(), "Limite de 100 atingido");

        Cliente centesimoPrimeiro = criarClienteValido(101);
        afirmarRejeicao(() -> sgi.cadastrarCliente(centesimoPrimeiro),
                IllegalStateException.class, "101º cliente via objeto deve disparar IllegalStateException");

        DADOSPESSOAIS dados = new DADOSPESSOAIS("Excedente", gerarCpfValido(102), "11999998888");
        afirmarRejeicao(() -> sgi.cadastrarCliente(dados, 1001, 99999L),
                IllegalStateException.class, "101º cliente via sobrecarga de dados pessoais");

        afirmarRejeicao(() -> sgi.cadastrarCliente("Excedente 2", gerarCpfValido(103), "11999998888", 1001, 99998L),
                IllegalStateException.class, "101º cliente via sobrecarga de literais");

        afirmarIgual(100, sgi.getQuantidadeClientes(), "Quantidade permanece estritamente em 100");
    }

    private static void test11_rejeicaoClienteDuplicadoMesmoCPF() {
        SGI sgi = new SGI();
        Cliente c1 = new Cliente("Fulano", "52998224725", "11911111111", 1001, 1001L);
        sgi.cadastrarCliente(c1);

        Cliente c2 = new Cliente("Ciclano", "529.982.247-25", "11922222222", 1002, 1002L);
        afirmarRejeicao(() -> sgi.cadastrarCliente(c2),
                IllegalArgumentException.class, "Rejeita CPF duplicado mesmo com pontuação");

        afirmarIgual(1, sgi.getQuantidadeClientes(), "Contagem de clientes não deve se alterar");
    }

    private static void test12_rejeicaoAplicacaoDuplicadaGlobalmenteNoSGI() {
        SGI sgi = new SGI();
        String cpf2 = gerarCpfValido(2);
        sgi.cadastrarCliente(new Cliente("C1", "52998224725", "11911111111", 1001, 1001L));
        sgi.cadastrarCliente(new Cliente("C2", cpf2, "11922222222", 1002, 1002L));

        sgi.cadastrarAplicacao("52998224725", new AplicacaoFinanceira("App", "APP-GLOBAL-1", new BigDecimal("1000.00"), new BigDecimal("0.01"), LocalDate.of(2026, 1, 1)));

        afirmarRejeicao(() -> sgi.cadastrarAplicacao(cpf2, new AplicacaoFinanceira("App", "APP-GLOBAL-1", new BigDecimal("2000.00"), new BigDecimal("0.01"), LocalDate.of(2026, 1, 1))),
                IllegalArgumentException.class, "Rejeita colisão global de número de aplicação entre clientes diferentes");
    }

    private static void test13_buscarClienteInexistenteRetornaOptionalEmpty() {
        SGI sgi = new SGI();
        String cpfInexistente = gerarCpfValido(999);
        afirmarVerdadeiro(sgi.buscarCliente(cpfInexistente).isEmpty(), "CPF inexistente retorna Optional.empty()");
        afirmarVerdadeiro(sgi.buscarCliente(null).isEmpty(), "CPF nulo retorna Optional.empty()");
        afirmarVerdadeiro(sgi.buscarCliente("").isEmpty(), "CPF vazio retorna Optional.empty()");
        afirmarVerdadeiro(sgi.buscarCliente("123").isEmpty(), "CPF curto retorna Optional.empty()");
    }

    private static void test14_operacoesParaClienteInexistenteLancaIllegalArgumentException() {
        SGI sgi = new SGI();
        String cpfInexistente = gerarCpfValido(999);

        afirmarRejeicao(() -> sgi.requererCliente(cpfInexistente),
                IllegalArgumentException.class, "requererCliente inexistente");
        afirmarRejeicao(() -> sgi.cadastrarAplicacao(cpfInexistente, new AplicacaoFinanceira("A", "1", new BigDecimal("100.00"), new BigDecimal("0.01"), LocalDate.now())),
                IllegalArgumentException.class, "cadastrarAplicacao titular inexistente");
        afirmarRejeicao(() -> sgi.cadastrarOperacao(cpfInexistente, "1", new OperacaoFinanceira(LocalDate.now(), new BigDecimal("10.00"), "D", TipoOperacao.DEPOSITO)),
                IllegalArgumentException.class, "cadastrarOperacao titular inexistente");
        afirmarRejeicao(() -> sgi.getSaldo(cpfInexistente),
                IllegalArgumentException.class, "getSaldo cliente inexistente");
        afirmarRejeicao(() -> sgi.getSaldo(cpfInexistente, "1"),
                IllegalArgumentException.class, "getSaldo terminal cliente inexistente");
        afirmarRejeicao(() -> sgi.getExtrato(cpfInexistente),
                IllegalArgumentException.class, "getExtrato cliente inexistente");
        afirmarRejeicao(() -> sgi.getExtrato(cpfInexistente, "1"),
                IllegalArgumentException.class, "getExtrato terminal cliente inexistente");
    }

    private static void test15_operacoesParaAplicacaoInexistenteLancaIllegalArgumentException() {
        SGI sgi = new SGI();
        sgi.cadastrarCliente(new Cliente("C1", "52998224725", "11911111111", 1001, 1001L));

        afirmarRejeicao(() -> sgi.getSaldo("52998224725", "APP-999"),
                IllegalArgumentException.class, "Saldo de terminal inexistente");
        afirmarRejeicao(() -> sgi.getExtrato("52998224725", "APP-999"),
                IllegalArgumentException.class, "Extrato de terminal inexistente");
        afirmarRejeicao(() -> sgi.cadastrarOperacao("52998224725", "APP-999", new OperacaoFinanceira(LocalDate.now(), new BigDecimal("10.00"), "D", TipoOperacao.DEPOSITO)),
                IllegalArgumentException.class, "Operação em terminal inexistente");
    }

    private static void test16_rejeicaoParametrosNulos() {
        SGI sgi = new SGI();

        afirmarRejeicao(() -> sgi.cadastrarCliente((Cliente) null),
                NullPointerException.class, "cadastrarCliente(Cliente) null");
        afirmarRejeicao(() -> sgi.cadastrarCliente((DADOSPESSOAIS) null, 1001, 1001L),
                NullPointerException.class, "cadastrarCliente(DADOSPESSOAIS) null");
        afirmarRejeicao(() -> sgi.cadastrarAplicacao(null, new MultiplasOperacoesFinanceiras(new AplicacaoFinanceira("A", "1", new BigDecimal("100"), new BigDecimal("0.01"), LocalDate.now()))),
                NullPointerException.class, "cadastrarAplicacao cpf null");
        afirmarRejeicao(() -> sgi.cadastrarAplicacao("52998224725", (MultiplasOperacoesFinanceiras) null),
                NullPointerException.class, "cadastrarAplicacao terminal null");
        afirmarRejeicao(() -> sgi.cadastrarOperacao(null, "1", new OperacaoFinanceira(LocalDate.now(), new BigDecimal("10"), "D", TipoOperacao.DEPOSITO)),
                NullPointerException.class, "cadastrarOperacao cpf null");
        afirmarRejeicao(() -> sgi.cadastrarOperacao("52998224725", null, new OperacaoFinanceira(LocalDate.now(), new BigDecimal("10"), "D", TipoOperacao.DEPOSITO)),
                NullPointerException.class, "cadastrarOperacao num null");
        afirmarRejeicao(() -> sgi.cadastrarOperacao("52998224725", "1", null),
                NullPointerException.class, "cadastrarOperacao op null");
        afirmarRejeicao(() -> sgi.atualizarAplicacoes(null),
                NullPointerException.class, "atualizarAplicacoes null");
        afirmarRejeicao(() -> sgi.getExtrato((LocalDate) null, LocalDate.now()),
                NullPointerException.class, "getExtrato inicio null");
        afirmarRejeicao(() -> sgi.getExtrato(LocalDate.now(), (LocalDate) null),
                NullPointerException.class, "getExtrato fim null");
        afirmarRejeicao(() -> {
            try {
                sgi.arquivoSalvarDados((Path) null);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }, NullPointerException.class, "arquivoSalvarDados null");
        afirmarRejeicao(() -> {
            try {
                SGI.arquivoLerDados((Path) null);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }, NullPointerException.class, "arquivoLerDados null");
    }

    private static void test17_imutabilidadeListaClientes() {
        SGI sgi = new SGI();
        sgi.cadastrarCliente(criarClienteValido(1));
        List<Cliente> lista = sgi.getClientes();

        afirmarRejeicao(() -> lista.clear(),
                UnsupportedOperationException.class, "Lista de clientes getClientes() deve ser imutável (clear)");
        afirmarRejeicao(() -> lista.add(criarClienteValido(2)),
                UnsupportedOperationException.class, "Lista de clientes getClientes() deve ser imutável (add)");
    }

    // =========================================================================
    // Tier 3: Integração Par a Par (Cross-Feature Pairwise)
    // =========================================================================

    private static void test18_atualizacaoEmLoteRestritaADiaUtil() {
        SGI sgi = new SGI();
        Cliente c = new Cliente("Titular", "52998224725", "11911111111", 1001, 1001L);
        sgi.cadastrarCliente(c);

        LocalDate sexta = LocalDate.of(2026, 11, 6); // Sexta-feira
        LocalDate sabado = LocalDate.of(2026, 11, 7); // Sábado
        LocalDate domingo = LocalDate.of(2026, 11, 8); // Domingo
        LocalDate segunda = LocalDate.of(2026, 11, 9); // Segunda-feira

        AplicacaoFinanceira af = new AplicacaoFinanceira("CDB Flex", "101", new BigDecimal("10000.00"), new BigDecimal("0.01"), sexta);
        sgi.cadastrarAplicacao("52998224725", af);

        BigDecimal saldoInicial = sgi.getSaldo("52998224725", "101");
        afirmarIgual(new BigDecimal("10000.00"), saldoInicial, "Saldo inicial de R$ 10.000,00");

        // Atualização no sábado -> operação no-op
        sgi.atualizarAplicacoes(sabado);
        afirmarIgual(saldoInicial, sgi.getSaldo("52998224725", "101"), "Saldo inalterado no sábado");

        // Atualização no domingo -> operação no-op
        sgi.atualizarAplicacoes(domingo);
        afirmarIgual(saldoInicial, sgi.getSaldo("52998224725", "101"), "Saldo inalterado no domingo");

        // Atualização na segunda-feira -> computa rendimento
        sgi.atualizarAplicacoes(segunda);
        BigDecimal saldoAposSegunda = sgi.getSaldo("52998224725", "101");
        afirmarVerdadeiro(saldoAposSegunda.compareTo(saldoInicial) > 0, "Saldo deve ter rendimento computado na segunda-feira");
    }

    private static void test19_extratoIndividualUltimos30Dias() {
        SGI sgi = new SGI();
        sgi.cadastrarCliente(new Cliente("C1", "52998224725", "11911111111", 1001, 1001L));

        LocalDate base = LocalDate.of(2026, 11, 6); // Sexta-feira (dia útil)
        LocalDate d45Atras = base.minusDays(45);
        LocalDate d20Atras = base.minusDays(20);
        LocalDate d5Atras = base.minusDays(5);

        AplicacaoFinanceira af = new AplicacaoFinanceira("App", "APP-1", new BigDecimal("1000.00"), new BigDecimal("0.01"), d45Atras);
        sgi.cadastrarAplicacao("52998224725", af);

        sgi.cadastrarOperacao("52998224725", "APP-1", new OperacaoFinanceira(d45Atras, new BigDecimal("100.00"), "Op antiga 45d", TipoOperacao.DEPOSITO));
        sgi.cadastrarOperacao("52998224725", "APP-1", new OperacaoFinanceira(d20Atras, new BigDecimal("200.00"), "Op 20d", TipoOperacao.DEPOSITO));
        sgi.cadastrarOperacao("52998224725", "APP-1", new OperacaoFinanceira(d5Atras, new BigDecimal("300.00"), "Op 5d", TipoOperacao.DEPOSITO));

        sgi.atualizarAplicacoes(base);

        List<OperacaoFinanceira> extrato30d = sgi.getExtrato("52998224725", "APP-1");
        afirmarIgual(2, extrato30d.size(), "Extrato de 30 dias deve conter apenas 2 operações (20d e 5d)");
        afirmarIgual("Op 20d", extrato30d.get(0).getDescricao(), "Primeira operação deve ser a de 20d");
        afirmarIgual("Op 5d", extrato30d.get(1).getDescricao(), "Segunda operação deve ser a de 5d");
    }

    private static void test20_extratoIndividualPorIntervaloPolimorfico() {
        SGI sgi = new SGI();
        sgi.cadastrarCliente(new Cliente("C1", "52998224725", "11911111111", 1001, 1001L));

        LocalDate base = LocalDate.of(2026, 5, 1);
        AplicacaoFinanceira af = new AplicacaoFinanceira("App", "APP-1", new BigDecimal("1000.00"), new BigDecimal("0.01"), base);
        sgi.cadastrarAplicacao("52998224725", af);

        sgi.cadastrarOperacao("52998224725", "APP-1", new OperacaoFinanceira(LocalDate.of(2026, 5, 2), new BigDecimal("100.00"), "Op 1", TipoOperacao.DEPOSITO));
        sgi.cadastrarOperacao("52998224725", "APP-1", new OperacaoFinanceira(LocalDate.of(2026, 5, 15), new BigDecimal("200.00"), "Op 2", TipoOperacao.DEPOSITO));
        sgi.cadastrarOperacao("52998224725", "APP-1", new OperacaoFinanceira(LocalDate.of(2026, 5, 28), new BigDecimal("300.00"), "Op 3", TipoOperacao.DEPOSITO));

        List<OperacaoFinanceira> extrato = sgi.getExtrato("52998224725", "APP-1", LocalDate.of(2026, 5, 10), LocalDate.of(2026, 5, 20));
        afirmarIgual(1, extrato.size(), "Deve filtrar apenas 1 operação no período");
        afirmarIgual("Op 2", extrato.get(0).getDescricao(), "Operação no meio do período");

        // Sobrecarga inteira
        List<OperacaoFinanceira> extratoInt = sgi.getExtrato("52998224725", "APP-1", 1, 5, 2026, 31, 5, 2026);
        afirmarIgual(3, extratoInt.size(), "Extrato via inteiros deve conter as 3 operações de maio");
    }

    private static void test21_extratoConsolidadoClienteUltimos30Dias() {
        SGI sgi = new SGI();
        sgi.cadastrarCliente(new Cliente("C1", "52998224725", "11911111111", 1001, 1001L));

        LocalDate base = LocalDate.of(2026, 11, 6); // Sexta-feira dia útil
        sgi.cadastrarAplicacao("52998224725", new AplicacaoFinanceira("App1", "1", new BigDecimal("1000"), new BigDecimal("0.01"), base.minusDays(50)));
        sgi.cadastrarAplicacao("52998224725", new AplicacaoFinanceira("App2", "2", new BigDecimal("2000"), new BigDecimal("0.01"), base.minusDays(50)));

        sgi.cadastrarOperacao("52998224725", "1", new OperacaoFinanceira(base.minusDays(25), new BigDecimal("50.00"), "Op App1 25d", TipoOperacao.DEPOSITO));
        sgi.cadastrarOperacao("52998224725", "2", new OperacaoFinanceira(base.minusDays(10), new BigDecimal("80.00"), "Op App2 10d", TipoOperacao.DEPOSITO));

        sgi.atualizarAplicacoes(base);

        List<OperacaoFinanceira> consolidado = sgi.getExtrato("52998224725");
        afirmarIgual(2, consolidado.size(), "Consolidado de 30 dias deve conter as 2 operações");
        afirmarIgual("Op App1 25d", consolidado.get(0).getDescricao(), "Primeira em ordem cronológica");
        afirmarIgual("Op App2 10d", consolidado.get(1).getDescricao(), "Segunda em ordem cronológica");
    }

    private static void test22_extratoConsolidadoClientePorIntervaloPolimorfico() {
        SGI sgi = new SGI();
        sgi.cadastrarCliente(new Cliente("C1", "52998224725", "11911111111", 1001, 1001L));

        sgi.cadastrarAplicacao("52998224725", new AplicacaoFinanceira("App1", "1", new BigDecimal("1000"), new BigDecimal("0.01"), LocalDate.of(2026, 1, 1)));
        sgi.cadastrarAplicacao("52998224725", new AplicacaoFinanceira("App2", "2", new BigDecimal("2000"), new BigDecimal("0.01"), LocalDate.of(2026, 1, 1)));

        sgi.cadastrarOperacao("52998224725", "1", new OperacaoFinanceira(LocalDate.of(2026, 2, 10), new BigDecimal("100.00"), "Fev App1", TipoOperacao.DEPOSITO));
        sgi.cadastrarOperacao("52998224725", "2", new OperacaoFinanceira(LocalDate.of(2026, 2, 20), new BigDecimal("200.00"), "Fev App2", TipoOperacao.DEPOSITO));
        sgi.cadastrarOperacao("52998224725", "1", new OperacaoFinanceira(LocalDate.of(2026, 3, 5), new BigDecimal("300.00"), "Mar App1", TipoOperacao.DEPOSITO));

        List<OperacaoFinanceira> fev = sgi.getExtrato("52998224725", LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28));
        afirmarIgual(2, fev.size(), "Apenas operações de fevereiro");

        List<OperacaoFinanceira> fevInt = sgi.getExtrato("52998224725", 1, 2, 2026, 28, 2, 2026);
        afirmarIgual(2, fevInt.size(), "Sobrecarga inteira de extrato consolidado de cliente");
    }

    private static void test23_extratoConsolidadoGlobalSGIUltimos30Dias() {
        SGI sgi = new SGI();
        String cpf2 = gerarCpfValido(2);
        sgi.cadastrarCliente(new Cliente("C1", "52998224725", "11911111111", 1001, 1001L));
        sgi.cadastrarCliente(new Cliente("C2", cpf2, "11922222222", 1002, 1002L));

        LocalDate base = LocalDate.of(2026, 11, 6); // Sexta-feira dia útil
        sgi.cadastrarAplicacao("52998224725", new AplicacaoFinanceira("App1", "1", new BigDecimal("1000"), new BigDecimal("0.01"), base.minusDays(50)));
        sgi.cadastrarAplicacao(cpf2, new AplicacaoFinanceira("App2", "2", new BigDecimal("2000"), new BigDecimal("0.01"), base.minusDays(50)));

        sgi.cadastrarOperacao("52998224725", "1", new OperacaoFinanceira(base.minusDays(20), new BigDecimal("100.00"), "Op C1", TipoOperacao.DEPOSITO));
        sgi.cadastrarOperacao(cpf2, "2", new OperacaoFinanceira(base.minusDays(10), new BigDecimal("200.00"), "Op C2", TipoOperacao.DEPOSITO));

        sgi.atualizarAplicacoes(base);

        List<OperacaoFinanceira> global = sgi.getExtrato();
        afirmarIgual(2, global.size(), "Extrato global deve conter as 2 operações");
        afirmarIgual("Op C1", global.get(0).getDescricao(), "Primeira do extrato global");
        afirmarIgual("Op C2", global.get(1).getDescricao(), "Segunda do extrato global");
    }

    private static void test24_extratoConsolidadoGlobalSGIPorIntervaloPolimorfico() {
        SGI sgi = new SGI();
        String cpf2 = gerarCpfValido(2);
        sgi.cadastrarCliente(new Cliente("C1", "52998224725", "11911111111", 1001, 1001L));
        sgi.cadastrarCliente(new Cliente("C2", cpf2, "11922222222", 1002, 1002L));

        sgi.cadastrarAplicacao("52998224725", new AplicacaoFinanceira("App1", "1", new BigDecimal("1000"), new BigDecimal("0.01"), LocalDate.of(2026, 1, 1)));
        sgi.cadastrarAplicacao(cpf2, new AplicacaoFinanceira("App2", "2", new BigDecimal("2000"), new BigDecimal("0.01"), LocalDate.of(2026, 1, 1)));

        sgi.cadastrarOperacao("52998224725", "1", new OperacaoFinanceira(LocalDate.of(2026, 4, 10), new BigDecimal("100.00"), "Op C1 Abril", TipoOperacao.DEPOSITO));
        sgi.cadastrarOperacao(cpf2, "2", new OperacaoFinanceira(LocalDate.of(2026, 4, 20), new BigDecimal("200.00"), "Op C2 Abril", TipoOperacao.DEPOSITO));
        sgi.cadastrarOperacao(cpf2, "2", new OperacaoFinanceira(LocalDate.of(2026, 5, 5), new BigDecimal("300.00"), "Op C2 Maio", TipoOperacao.DEPOSITO));

        List<OperacaoFinanceira> globalAbril = sgi.getExtrato(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30));
        afirmarIgual(2, globalAbril.size(), "Extrato global em abril com 2 operações");

        List<OperacaoFinanceira> globalAbrilInt = sgi.getExtrato(1, 4, 2026, 30, 4, 2026);
        afirmarIgual(2, globalAbrilInt.size(), "Extrato global em abril via sobrecarga de inteiros");
    }

    private static void test25_rejeicaoIntervaloDatasInvertido() {
        SGI sgi = new SGI();
        sgi.cadastrarCliente(criarClienteValido(1));
        sgi.cadastrarAplicacao(gerarCpfValido(1), criarAplicacaoPadrao("APP-1", new BigDecimal("1000"), LocalDate.of(2026, 1, 1)));

        LocalDate ini = LocalDate.of(2026, 5, 20);
        LocalDate fim = LocalDate.of(2026, 5, 10);

        afirmarRejeicao(() -> sgi.getExtrato(ini, fim),
                IllegalArgumentException.class, "Extrato global data invertida");
        afirmarRejeicao(() -> sgi.getExtrato(gerarCpfValido(1), ini, fim),
                IllegalArgumentException.class, "Extrato cliente data invertida");
        afirmarRejeicao(() -> sgi.getExtrato(gerarCpfValido(1), "APP-1", ini, fim),
                IllegalArgumentException.class, "Extrato terminal data invertida");
    }

    // =========================================================================
    // Tier 4: Cenários do Mundo Real e Aceitação Ponta a Ponta
    // =========================================================================

    private static void test26_cenarioCicloCompletoMultiplosClientesCarteiras() {
        SGI sgi = new SGI();

        // Cliente 1: Conservador (CDB Prefixado)
        Cliente c1 = new Cliente("Investidor Conservador", gerarCpfValido(10), "11988881111", 1001, 50001L);
        sgi.cadastrarCliente(c1);
        AplicacaoFinanceira af1 = new AplicacaoFinanceira("CDB Prefixado", "CONS-1", new BigDecimal("50000.00"), new BigDecimal("0.012"), LocalDate.of(2026, 1, 1));
        af1.setConvencaoContagemDias(ConvencaoContagemDias.COMERCIAL_30_360);
        af1.setRegimeTributario(RegimeTributario.PADRAO);
        sgi.cadastrarAplicacao(gerarCpfValido(10), af1);

        // Cliente 2: Moderado (LCI Isenta de IR)
        Cliente c2 = new Cliente("Investidor Moderado", gerarCpfValido(20), "11988882222", 1002, 50002L);
        sgi.cadastrarCliente(c2);
        AplicacaoFinanceira af2 = new AplicacaoFinanceira("LCI Imobiliária", "MOD-1", new BigDecimal("30000.00"), new BigDecimal("0.009"), LocalDate.of(2026, 1, 1));
        af2.setTipoImpostoRenda(0); // Isento
        sgi.cadastrarAplicacao(gerarCpfValido(20), af2);

        // Cliente 3: Dinâmico com Aportes e Resgates
        Cliente c3 = new Cliente("Investidor Dinâmico", gerarCpfValido(30), "11988883333", 1003, 50003L);
        sgi.cadastrarCliente(c3);
        AplicacaoFinanceira af3 = new AplicacaoFinanceira("Fundo Curto Prazo", "DIN-1", new BigDecimal("20000.00"), new BigDecimal("0.015"), LocalDate.of(2026, 1, 1));
        sgi.cadastrarAplicacao(gerarCpfValido(30), af3);

        sgi.cadastrarOperacao(gerarCpfValido(30), "DIN-1", new OperacaoFinanceira(LocalDate.of(2026, 1, 15), new BigDecimal("10000.00"), "Aporte adicional", TipoOperacao.DEPOSITO));
        sgi.cadastrarOperacao(gerarCpfValido(30), "DIN-1", new OperacaoFinanceira(LocalDate.of(2026, 1, 25), new BigDecimal("5000.00"), "Resgate parcial", TipoOperacao.RETIRADA));

        // Avanço temporal e atualização patrimonial
        LocalDate dataAvanco = LocalDate.of(2026, 1, 30); // Sexta-feira
        sgi.atualizarAplicacoes(dataAvanco);

        BigDecimal saldoTotal = sgi.getSaldo();
        afirmarVerdadeiro(saldoTotal.compareTo(new BigDecimal("100000.00")) > 0, "Saldo total com rendimento deve ser superior a R$ 100.000,00");
        afirmarIgual(3, sgi.getQuantidadeClientes(), "3 clientes mantidos");
    }

    private static void test27_persistenciaArquivoSalvarELerDadosCustomPath() {
        SGI sgiOriginal = new SGI();
        Cliente c1 = criarClienteValido(1);
        Cliente c2 = criarClienteValido(2);
        sgiOriginal.cadastrarCliente(c1);
        sgiOriginal.cadastrarCliente(c2);

        LocalDate d = LocalDate.of(2026, 2, 1);
        sgiOriginal.cadastrarAplicacao(c1.getDadosPessoais().getCpf(), new AplicacaoFinanceira("App1", "101", new BigDecimal("10000.00"), new BigDecimal("0.01"), d));
        sgiOriginal.cadastrarAplicacao(c2.getDadosPessoais().getCpf(), new AplicacaoFinanceira("App2", "102", new BigDecimal("20000.00"), new BigDecimal("0.015"), d));

        sgiOriginal.cadastrarOperacao(c1.getDadosPessoais().getCpf(), "101", new OperacaoFinanceira(d.plusDays(5), new BigDecimal("500.00"), "Depósito 1", TipoOperacao.DEPOSITO));
        sgiOriginal.cadastrarOperacao(c2.getDadosPessoais().getCpf(), "102", new OperacaoFinanceira(d.plusDays(10), new BigDecimal("1000.00"), "Retirada 1", TipoOperacao.RETIRADA));

        Path tempPath = null;
        try {
            tempPath = Files.createTempFile("sgi_test_custom_", ".db");
            sgiOriginal.arquivoSalvarDados(tempPath);

            afirmarVerdadeiro(Files.exists(tempPath), "Arquivo persistido deve existir");
            afirmarVerdadeiro(Files.size(tempPath) > 0, "Arquivo deve ter conteúdo gravado");

            SGI restaurado = SGI.arquivoLerDados(tempPath);
            afirmarIgual(sgiOriginal.getQuantidadeClientes(), restaurado.getQuantidadeClientes(), "Quantidade de clientes deve bater");
            afirmarIgual(sgiOriginal.getSaldo(), restaurado.getSaldo(), "Saldo líquido global deve ser idêntico");
            afirmarIgual(sgiOriginal.getSaldoBruto(), restaurado.getSaldoBruto(), "Saldo bruto global deve ser idêntico");

            Cliente c1Restaurado = restaurado.requererCliente(c1.getDadosPessoais().getCpf());
            afirmarIgual(c1.getDadosPessoais().getNome(), c1Restaurado.getDadosPessoais().getNome(), "Nome do cliente preservado");
            afirmarIgual(c1.getNumeroAgencia(), c1Restaurado.getNumeroAgencia(), "Agência preservada");
            afirmarIgual(c1.getNumeroConta(), c1Restaurado.getNumeroConta(), "Conta preservada");
            afirmarIgual(1, c1Restaurado.requererAplicacao("101").getOperacoes().size(), "Operações preservadas");
            afirmarIgual(1, c1Restaurado.getExtrato("101", d, d.plusDays(30)).size(), "Extrato preservado no intervalo");
        } catch (IOException e) {
            throw new AssertionError("Falha de E/S no teste de persistência: " + e.getMessage(), e);
        } finally {
            if (tempPath != null) {
                try {
                    Files.deleteIfExists(tempPath);
                } catch (IOException ignored) {}
            }
        }
    }

    private static void test28_persistenciaArquivoSalvarELerDadosCaminhoPadrao() {
        Path padrao = SGI.CAMINHO_PADRAO_DADOS;
        byte[] backup = null;
        boolean existiaAntes = Files.exists(padrao);
        try {
            if (existiaAntes) {
                backup = Files.readAllBytes(padrao);
            }

            SGI sgi = new SGI();
            Cliente c = criarClienteValido(50);
            sgi.cadastrarCliente(c);
            sgi.cadastrarAplicacao(c.getDadosPessoais().getCpf(), criarAplicacaoPadrao("APP-PADRAO", new BigDecimal("1500.00"), LocalDate.of(2026, 3, 1)));

            sgi.arquivoSalvarDados(); // Grava no caminho padrão
            afirmarVerdadeiro(Files.exists(padrao), "Arquivo no caminho padrão deve existir");

            SGI restaurado = SGI.arquivoLerDados(); // Lê do caminho padrão
            afirmarIgual(1, restaurado.getQuantidadeClientes(), "Deve haver 1 cliente restaurado");
            afirmarIgual(new BigDecimal("1500.00"), restaurado.getSaldo(), "Saldo restaurado confere");

            // Testa carregarDados in-place
            SGI sgiEmMemoria = new SGI();
            sgiEmMemoria.carregarDados();
            afirmarIgual(1, sgiEmMemoria.getQuantidadeClientes(), "carregarDados in-place deve conter 1 cliente");
        } catch (IOException e) {
            throw new AssertionError("Falha no teste com caminho padrão: " + e.getMessage(), e);
        } finally {
            try {
                if (existiaAntes && backup != null) {
                    Files.write(padrao, backup);
                } else {
                    Files.deleteIfExists(padrao);
                }
            } catch (IOException ignored) {}
        }
    }

    private static void test29_roundTripMultiplosCiclosIntegridade() {
        Path tempPath = null;
        try {
            tempPath = Files.createTempFile("sgi_roundtrip_", ".db");
            SGI sgi = new SGI();
            Cliente c = criarClienteValido(1);
            sgi.cadastrarCliente(c);
            sgi.cadastrarAplicacao(c.getDadosPessoais().getCpf(), criarAplicacaoPadrao("APP-1", new BigDecimal("1000.00"), LocalDate.of(2026, 1, 1)));

            // Ciclo 1: Salva e Carrega
            sgi.arquivoSalvarDados(tempPath);
            sgi = SGI.arquivoLerDados(tempPath);
            afirmarIgual(new BigDecimal("1000.00"), sgi.getSaldo(), "Ciclo 1 saldo");

            // Ciclo 2: Adiciona aporte, Salva e Carrega
            sgi.cadastrarOperacao(c.getDadosPessoais().getCpf(), "APP-1", new OperacaoFinanceira(LocalDate.of(2026, 1, 10), new BigDecimal("500.00"), "Aporte C2", TipoOperacao.DEPOSITO));
            sgi.arquivoSalvarDados(tempPath);
            sgi = SGI.arquivoLerDados(tempPath);
            afirmarIgual(new BigDecimal("1500.00"), sgi.getSaldo(), "Ciclo 2 saldo");

            // Ciclo 3: Adiciona novo cliente, Salva e Carrega
            Cliente c2 = criarClienteValido(2);
            sgi.cadastrarCliente(c2);
            sgi.cadastrarAplicacao(c2.getDadosPessoais().getCpf(), criarAplicacaoPadrao("APP-2", new BigDecimal("2000.00"), LocalDate.of(2026, 1, 1)));
            sgi.arquivoSalvarDados(tempPath);
            sgi = SGI.arquivoLerDados(tempPath);
            afirmarIgual(2, sgi.getQuantidadeClientes(), "Ciclo 3 quantidade de clientes");
            afirmarIgual(new BigDecimal("3500.00"), sgi.getSaldo(), "Ciclo 3 saldo");
        } catch (IOException e) {
            throw new AssertionError("Falha no round-trip: " + e.getMessage(), e);
        } finally {
            if (tempPath != null) {
                try {
                    Files.deleteIfExists(tempPath);
                } catch (IOException ignored) {}
            }
        }
    }

    // =========================================================================
    // Tier 5: Endurecimento Adversarial, Estresse, Concorrência e Robustez
    // =========================================================================

    private static class SGIPrivateBanking extends SGI {
        private int contadorOperacoesCustomizadas = 0;

        @Override
        public synchronized void cadastrarOperacao(String cpf, String numeroAplicacao, OperacaoFinanceira operacao) {
            super.cadastrarOperacao(cpf, numeroAplicacao, operacao);
            contadorOperacoesCustomizadas++;
        }

        public int getContadorOperacoesCustomizadas() {
            return contadorOperacoesCustomizadas;
        }
    }

    private static void test30_herancaSeguraSGI() {
        SGIPrivateBanking privateBanking = new SGIPrivateBanking();
        Cliente c = criarClienteValido(1);
        privateBanking.cadastrarCliente(c);
        privateBanking.cadastrarAplicacao(c.getDadosPessoais().getCpf(), criarAplicacaoPadrao("APP-PB", new BigDecimal("10000.00"), LocalDate.of(2026, 1, 1)));

        privateBanking.cadastrarOperacao(c.getDadosPessoais().getCpf(), "APP-PB",
                new OperacaoFinanceira(LocalDate.of(2026, 1, 2), new BigDecimal("500.00"), "Dep PB", TipoOperacao.DEPOSITO));

        afirmarIgual(1, privateBanking.getContadorOperacoesCustomizadas(), "Hook de herança em cadastrarOperacao executado");
        afirmarIgual(new BigDecimal("10500.00"), privateBanking.getSaldo(), "Saldo do Private Banking confere");
    }

    private static void test31_concorrenciaMultithreadBasicaThreadSafety() {
        SGI sgi = new SGI();
        int qtdInicial = 10;
        for (int i = 1; i <= qtdInicial; i++) {
            Cliente c = criarClienteValido(i);
            sgi.cadastrarCliente(c);
            sgi.cadastrarAplicacao(c.getDadosPessoais().getCpf(), criarAplicacaoPadrao("APP-" + i, new BigDecimal("1000.00"), LocalDate.of(2026, 1, 1)));
        }

        int threads = 4;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        AtomicInteger erros = new AtomicInteger(0);

        // Thread 1: Consulta saldos
        executor.submit(() -> {
            try {
                for (int i = 0; i < 50; i++) {
                    sgi.getSaldo();
                    sgi.getClientes();
                }
            } catch (Exception e) {
                erros.incrementAndGet();
            } finally {
                latch.countDown();
            }
        });

        // Thread 2: Consulta extratos
        executor.submit(() -> {
            try {
                for (int i = 0; i < 50; i++) {
                    sgi.getExtrato();
                }
            } catch (Exception e) {
                erros.incrementAndGet();
            } finally {
                latch.countDown();
            }
        });

        // Thread 3: Lança operações
        executor.submit(() -> {
            try {
                for (int i = 0; i < 30; i++) {
                    String cpf = gerarCpfValido(1);
                    sgi.cadastrarOperacao(cpf, "APP-1", new OperacaoFinanceira(LocalDate.of(2026, 1, 2), new BigDecimal("1.00"), "Op conc " + i, TipoOperacao.DEPOSITO));
                }
            } catch (Exception e) {
                erros.incrementAndGet();
            } finally {
                latch.countDown();
            }
        });

        // Thread 4: Cadastra novos clientes
        executor.submit(() -> {
            try {
                for (int i = 11; i <= 25; i++) {
                    sgi.cadastrarCliente(criarClienteValido(i));
                }
            } catch (Exception e) {
                erros.incrementAndGet();
            } finally {
                latch.countDown();
            }
        });

        try {
            boolean terminou = latch.await(10, TimeUnit.SECONDS);
            afirmarVerdadeiro(terminou, "Todas as threads devem concluir dentro do tempo limite");
            afirmarIgual(0, erros.get(), "Nenhuma exceção concorrente deve ter ocorrido");
            afirmarIgual(25, sgi.getQuantidadeClientes(), "25 clientes cadastrados com sucesso");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Thread interrompida no teste de concorrência", e);
        } finally {
            executor.shutdownNow();
        }
    }

    private static void test32_resilienciaPermissoesPOSIX0600() {
        Path tempPath = null;
        try {
            tempPath = Files.createTempFile("sgi_posix_check_", ".db");
            SGI sgi = new SGI();
            sgi.cadastrarCliente(criarClienteValido(1));
            sgi.arquivoSalvarDados(tempPath);

            try {
                Set<PosixFilePermission> perms = Files.getPosixFilePermissions(tempPath);
                String permStr = PosixFilePermissions.toString(perms);
                afirmarIgual("rw-------", permStr, "Permissão do arquivo persistido deve ser estritamente 0600 (rw-------)");
            } catch (UnsupportedOperationException e) {
                // Sistema de arquivos sem suporte a POSIX (ex.: Windows FAT32/NTFS)
                System.out.println("  [INFO] Ambiente não-POSIX detectado, teste de permissão estrita ignorado.");
            }
        } catch (IOException e) {
            throw new AssertionError("Erro no teste de permissões POSIX: " + e.getMessage(), e);
        } finally {
            if (tempPath != null) {
                try {
                    Files.deleteIfExists(tempPath);
                } catch (IOException ignored) {}
            }
        }
    }

    private static void test33_resilienciaEscapamentoCaracteresEspeciaisTSV() {
        Path tempPath = null;
        try {
            tempPath = Files.createTempFile("sgi_escape_check_", ".db");
            SGI sgi = new SGI();

            String nomeComCaracteres = "Empresa\tComércio & Serviços\nLtda\\Filial\rSP";
            String descComCaracteres = "Aporte\tcom\\barra e\nquebra de linha";

            Cliente c = new Cliente(nomeComCaracteres, gerarCpfValido(77), "11988887777", 1001, 8888L);
            sgi.cadastrarCliente(c);
            sgi.cadastrarAplicacao(gerarCpfValido(77), criarAplicacaoPadrao("APP-ESC", new BigDecimal("5000.00"), LocalDate.of(2026, 1, 1)));
            sgi.cadastrarOperacao(gerarCpfValido(77), "APP-ESC", new OperacaoFinanceira(LocalDate.of(2026, 1, 2), new BigDecimal("100.00"), descComCaracteres, TipoOperacao.DEPOSITO));

            sgi.arquivoSalvarDados(tempPath);

            SGI restaurado = SGI.arquivoLerDados(tempPath);
            Cliente cRestaurado = restaurado.requererCliente(gerarCpfValido(77));

            afirmarIgual(nomeComCaracteres, cRestaurado.getDadosPessoais().getNome(), "Nome com caracteres especiais deve ser fielmente recuperado");
            List<OperacaoFinanceira> ops = cRestaurado.requererAplicacao("APP-ESC").getOperacoes();
            afirmarIgual(descComCaracteres, ops.get(0).getDescricao(), "Descrição de operação com escapes deve ser fielmente recuperada");
        } catch (IOException e) {
            throw new AssertionError("Falha no teste de escapamento: " + e.getMessage(), e);
        } finally {
            if (tempPath != null) {
                try {
                    Files.deleteIfExists(tempPath);
                } catch (IOException ignored) {}
            }
        }
    }

    private static void test34_rejeicaoArquivoCorrompidoOuInvalido() {
        Path tempPath = null;
        try {
            tempPath = Files.createTempFile("sgi_corrompido_", ".db");

            // Caso 1: Arquivo vazio
            Files.writeString(tempPath, "");
            Path finalTempPath = tempPath;
            afirmarRejeicao(() -> {
                try {
                    SGI.arquivoLerDados(finalTempPath);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }, IllegalArgumentException.class, "Arquivo vazio");

            // Caso 2: Cabeçalho desconhecido
            Files.writeString(tempPath, "FORMATO-ESTRANHO\t1\n");
            afirmarRejeicao(() -> {
                try {
                    SGI.arquivoLerDados(finalTempPath);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }, IllegalArgumentException.class, "Cabeçalho desconhecido");

            // Caso 3: Registro I antes de C
            Files.writeString(tempPath, "NATIONAL-ECONOMIC\t3\nI\t52998224725\t101\tApp\t1000\t0.01\t2026-01-01\t1000\t1000\t\t0\t0\t0\tCOMERCIAL_30_360\tPADRAO\t0\t0\t0\t0\t0\t0\t0\t1000\t0\t0\t0\n");
            afirmarRejeicao(() -> {
                try {
                    SGI.arquivoLerDados(finalTempPath);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }, IllegalArgumentException.class, "Registro I antes de C");

            // Caso 4: Registro O antes de I
            Files.writeString(tempPath, "NATIONAL-ECONOMIC\t3\nC\t52998224725\tNome\t11988887777\t1001\t1001\nO\t52998224725\t101\t2026-01-01\t100\tDesc\tDEPOSITO\n");
            afirmarRejeicao(() -> {
                try {
                    SGI.arquivoLerDados(finalTempPath);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }, IllegalArgumentException.class, "Registro O antes de I");

            // Caso 5: Registro C truncado (< 6 colunas)
            Files.writeString(tempPath, "NATIONAL-ECONOMIC\t3\nC\t52998224725\tNome\t11988887777\n");
            afirmarRejeicao(() -> {
                try {
                    SGI.arquivoLerDados(finalTempPath);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }, IllegalArgumentException.class, "Registro C truncado");

            // Caso 6: Registro I truncado (< 23 colunas)
            Files.writeString(tempPath, "NATIONAL-ECONOMIC\t3\nC\t52998224725\tNome\t11988887777\t1001\t1001\nI\t52998224725\t101\tApp\t1000\n");
            afirmarRejeicao(() -> {
                try {
                    SGI.arquivoLerDados(finalTempPath);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }, IllegalArgumentException.class, "Registro I truncado");

            // Caso 7: Registro O truncado (< 7 colunas)
            Files.writeString(tempPath, "NATIONAL-ECONOMIC\t3\nC\t52998224725\tNome\t11988887777\t1001\t1001\nI\t52998224725\t101\tApp\t1000\t0.01\t2026-01-01\t1000\t1000\t\t0\t0\t0\tCOMERCIAL_30_360\tPADRAO\t0\t0\t0\t0\t0\t0\t0\t1000\t0\t0\t0\nO\t52998224725\t101\t2026-01-01\n");
            afirmarRejeicao(() -> {
                try {
                    SGI.arquivoLerDados(finalTempPath);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }, IllegalArgumentException.class, "Registro O truncado");
        } catch (IOException e) {
            throw new AssertionError("Erro na escrita do arquivo de teste de corrupção: " + e.getMessage(), e);
        } finally {
            if (tempPath != null) {
                try {
                    Files.deleteIfExists(tempPath);
                } catch (IOException ignored) {}
            }
        }
    }

    private static void test35_atualizacaoCotacoesIntegracaoM4() {
        SGI sgi = new SGI();
        Map<String, BigDecimal> taxas = Map.of(
                "SELICRA", new BigDecimal("10.75"),
                "CDIR", new BigDecimal("10.65"),
                "DOLAR", new BigDecimal("5.2500"),
                "EURO", new BigDecimal("5.6500")
        );

        sgi.atualizarCotacoes(taxas);

        afirmarIgual(new BigDecimal("10.75"), sgi.getCotacao("SELICRA").orElse(null), "SELICRA confere");
        afirmarIgual(new BigDecimal("10.65"), sgi.getCotacao("CDIR").orElse(null), "CDIR confere");
        afirmarIgual(new BigDecimal("5.2500"), sgi.getCotacao("DOLAR").orElse(null), "DOLAR confere");
        afirmarIgual(new BigDecimal("5.2500"), sgi.getCotacao("USD").orElse(null), "USD espelhado confere");
        afirmarIgual(new BigDecimal("5.6500"), sgi.getCotacao("EURO").orElse(null), "EURO confere");
        afirmarIgual(new BigDecimal("5.6500"), sgi.getCotacao("EUR").orElse(null), "EUR espelhado confere");

        afirmarVerdadeiro(sgi.getCotacao("INEXISTENTE").isEmpty(), "Cotação desconhecida retorna Optional.empty()");
        afirmarVerdadeiro(sgi.getCotacao(null).isEmpty(), "Cotação nula retorna Optional.empty()");

        Map<String, BigDecimal> cotacoesAtivas = sgi.getCotacoes();
        afirmarVerdadeiro(cotacoesAtivas.containsKey("SELICRA"), "Mapa contém SELICRA");
        afirmarVerdadeiro(cotacoesAtivas.containsKey("DOLAR"), "Mapa contém DOLAR");
        afirmarVerdadeiro(cotacoesAtivas.containsKey("USD"), "Mapa contém USD");
    }
}

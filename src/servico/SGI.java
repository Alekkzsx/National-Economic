package servico;

import dominio.AplicacaoFinanceira;
import dominio.Cliente;
import dominio.DADOSPESSOAIS;
import dominio.DiaSemana;
import dominio.Moeda;
import dominio.MultiplasOperacoesFinanceiras;
import dominio.OperacaoFinanceira;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Fachada principal e consolidadora do Sistema de Gestão de Investimentos (SGI),
 * em conformidade com o Item 7 da Atividade Prática Acadêmica (Requisito R3.1).
 *
 * <p>Esta classe gerencia contas bancárias e carteiras de investimento de até 100 clientes
 * ({@link Cliente}), coordena atualizações de saldo restritas exclusivamente aos dias úteis
 * (segunda a sexta-feira via {@link DiaSemana}), centraliza consultas de saldos individuais
 * e consolidados, gera extratos polimórficos de movimentação e orquestra a persistência
 * atômica do estado global em arquivo TSV versionado ({@link ArmazenadorArquivo}).</p>
 *
 * <p>Principais responsabilidades corporativas:</p>
 * <ul>
 *   <li>Gestão regulamentar de até 100 clientes com bloqueio defensivo da 101ª inserção
 *       disparando {@link IllegalStateException};</li>
 *   <li>Cadastro e busca de titulares através de CPF canônico validado e normalizado;</li>
 *   <li>Garantia de unicidade global de identificadores de aplicações financeiras;</li>
 *   <li>Atualização de rendimentos restrita estritamente a dias úteis (fins de semana ignorados);</li>
 *   <li>Apuração de saldos líquidos e brutos em três níveis: individual por aplicação,
 *       consolidado por titular e consolidado global do sistema;</li>
 *   <li>Geração polimórfica de extratos em três níveis: individual por aplicação, consolidado
 *       por titular e consolidado global do sistema, com ordenação cronológica garantida;</li>
 *   <li>Persistência autônoma via {@link #arquivoSalvarDados} e {@link #arquivoLerDados};</li>
 *   <li>Ponto de acoplamento com o ecossistema de cotações através de {@link #atualizarCotacoes}.</li>
 * </ul>
 *
 * <p>A classe é passível de herança (não-final), adota construtores seguros que não disparam
 * métodos sobrescrevíveis durante a inicialização e é estritamente segura para acesso concorrente
 * multi-thread ({@code synchronized}).</p>
 */
public class SGI {

    /** Limite regulamentar máximo de clientes suportado pelo sistema. */
    public static final int LIMITE_CLIENTES = 100;

    /** Alias didático de compatibilidade para o limite máximo de clientes. */
    public static final int MAX_CLIENTS = LIMITE_CLIENTES;

    /** Rota padrão para armazenamento dos dados persistidos em disco. */
    public static final Path CAMINHO_PADRAO_DADOS = Path.of("data", "sgi.db");

    /** Mapa de clientes cadastrados indexados pelo CPF normalizado (11 dígitos). */
    private final Map<String, Cliente> clientes = new LinkedHashMap<>();

    /** Mapa das últimas cotações e taxas de mercado recebidas do servidor de cotações. */
    private final Map<String, BigDecimal> ultimasCotacoes = new LinkedHashMap<>();

    // =========================================================================
    // Construtores Seguros contra Despacho Virtual
    // =========================================================================

    /**
     * Construtor público padrão.
     *
     * <p>Seguro contra despacho virtual: não invoca métodos polimórficos durante
     * a inicialização do objeto.</p>
     */
    public SGI() {
        // Construtor monomórfico seguro: não despacha métodos virtuais
    }

    /**
     * Cria uma nova instância vazia e operacional do Sistema de Gestão de Investimentos.
     *
     * @return nova instância do SGI
     */
    public static SGI criarNovo() {
        return new SGI();
    }

    // =========================================================================
    // Gestão de Clientes (Item 7, R3.1)
    // =========================================================================

    /**
     * Cadastra um novo cliente no SGI validando duplicidade de CPF, unicidade de aplicações
     * e o limite de até 100 titulares.
     *
     * @param cliente instância de cliente devidamente preenchida (não nulo)
     * @throws NullPointerException se o cliente for nulo
     * @throws IllegalArgumentException se o CPF já estiver cadastrado ou se houver colisão de número de aplicação
     * @throws IllegalStateException se a capacidade máxima de 100 clientes já tiver sido atingida
     */
    public synchronized void cadastrarCliente(Cliente cliente) {
        Objects.requireNonNull(cliente, "Cliente não pode ser nulo.");
        Objects.requireNonNull(cliente.getDadosPessoais(), "Dados pessoais do cliente são obrigatórios.");

        String cpf = normalizarCpf(cliente.getDadosPessoais().getCpf());
        if (clientes.containsKey(cpf)) {
            throw new IllegalArgumentException("Cliente já cadastrado para o CPF informado: " + cpf);
        }

        if (clientes.size() >= LIMITE_CLIENTES) {
            throw new IllegalStateException("Limite de 100 clientes atingido.");
        }

        // Valida unicidade global dos números de aplicações contidos na carteira do cliente
        for (MultiplasOperacoesFinanceiras terminal : cliente.getAplicacoes()) {
            String num = terminal.getNumeroAplicacao();
            if (num != null && buscarAplicacao(num).isPresent()) {
                throw new IllegalArgumentException("Número de aplicação já cadastrado no sistema: " + num);
            }
        }

        clientes.put(cpf, cliente);
    }

    /**
     * Sobrecarga polimórfica que cadastra um cliente a partir dos seus dados pessoais e coordenadas bancárias.
     *
     * @param dados dados pessoais contendo nome, CPF e celular
     * @param agencia número positivo da agência bancária
     * @param conta número positivo da conta bancária
     * @throws NullPointerException se dados forem nulos
     * @throws IllegalArgumentException se parâmetros forem inválidos ou CPF duplicado
     * @throws IllegalStateException se atingido o limite de 100 clientes
     */
    public synchronized void cadastrarCliente(DADOSPESSOAIS dados, int agencia, long conta) {
        Objects.requireNonNull(dados, "Dados pessoais são obrigatórios.");
        cadastrarCliente(new Cliente(dados, agencia, conta));
    }

    /**
     * Sobrecarga de conveniência que cadastra um cliente a partir dos atributos literais básicos.
     *
     * @param nome nome completo do titular
     * @param cpf cadastro de pessoa física com 11 dígitos válidos
     * @param celular telefone celular com DDD
     * @param agencia número da agência bancária (&gt; 0)
     * @param conta número da conta corrente (&gt; 0)
     * @throws NullPointerException se parâmetros forem nulos
     * @throws IllegalArgumentException se parâmetros forem inválidos ou CPF duplicado
     * @throws IllegalStateException se atingido o limite de 100 clientes
     */
    public synchronized void cadastrarCliente(String nome, String cpf, String celular, int agencia, long conta) {
        Objects.requireNonNull(nome, "Nome do cliente é obrigatório.");
        Objects.requireNonNull(cpf, "CPF do cliente é obrigatório.");
        Objects.requireNonNull(celular, "Celular do cliente é obrigatório.");
        cadastrarCliente(new Cliente(nome, cpf, celular, agencia, conta));
    }

    /**
     * Localiza um cliente através de seu CPF (formatado ou apenas com dígitos).
     *
     * @param cpf documento a ser buscado
     * @return {@link Optional} com o cliente se localizado, ou vazio se não cadastrado ou formato inválido
     */
    public synchronized Optional<Cliente> buscarCliente(String cpf) {
        if (cpf == null || cpf.isBlank()) {
            return Optional.empty();
        }
        String digitos = cpf.replaceAll("\\D", "");
        if (digitos.length() != 11) {
            return Optional.empty();
        }
        return Optional.ofNullable(clientes.get(digitos));
    }

    /**
     * Localiza obrigatoriamente um cliente pelo CPF, lançando exceção se não existir.
     *
     * @param cpf documento do cliente
     * @return o cliente correspondente
     * @throws NullPointerException se o CPF for nulo
     * @throws IllegalArgumentException se o cliente não for localizado
     */
    public synchronized Cliente requererCliente(String cpf) {
        Objects.requireNonNull(cpf, "CPF do cliente é obrigatório.");
        return buscarCliente(cpf).orElseThrow(() ->
                new IllegalArgumentException("Cliente não encontrado para o CPF informado: " + cpf));
    }

    /**
     * Retorna a quantidade total de clientes cadastrados no sistema.
     *
     * @return contagem de clientes (0 a 100)
     */
    public synchronized int getQuantidadeClientes() {
        return clientes.size();
    }

    /**
     * Retorna uma cópia defensiva imutável da relação de clientes cadastrados.
     *
     * @return lista imutável de clientes preservando a ordem de cadastro
     */
    public synchronized List<Cliente> getClientes() {
        return Collections.unmodifiableList(new ArrayList<>(clientes.values()));
    }

    /** Atualiza nome e celular sem alterar o CPF que identifica a conta. */
    public synchronized void alterarCliente(String cpf, String nome, String celular) {
        requererCliente(cpf).getDadosPessoais().alterarDados(nome, celular);
    }

    /** Exclui um cliente e todas as aplicações e operações vinculadas. */
    public synchronized void excluirCliente(String cpf) {
        String chave = normalizarCpf(cpf);
        if (clientes.remove(chave) == null) {
            throw new IllegalArgumentException("Cliente não encontrado para o CPF informado.");
        }
    }

    // =========================================================================
    // Gestão de Aplicações Financeiras e Operações (Item 7, R3.1)
    // =========================================================================

    /**
     * Vincula uma nova aplicação financeira à carteira do titular identificado pelo CPF.
     *
     * <p>Garante que o identificador unívoco da aplicação não colida com nenhuma outra
     * aplicação previamente cadastrada em todo o SGI.</p>
     *
     * @param cpf documento do titular da conta
     * @param aplicacao instância contendo aplicação, histórico e calendário
     * @throws NullPointerException se o cpf ou a aplicação forem nulos
     * @throws IllegalArgumentException se o titular não for encontrado ou se o número de aplicação já existir
     * @throws IllegalStateException se o cliente já atingiu o teto de 50 aplicações
     */
    public synchronized void cadastrarAplicacao(String cpf, MultiplasOperacoesFinanceiras aplicacao) {
        Objects.requireNonNull(cpf, "CPF do titular é obrigatório.");
        Objects.requireNonNull(aplicacao, "Aplicação financeira é obrigatória.");
        Cliente titular = requererCliente(cpf);

        String numero = aplicacao.getNumeroAplicacao();
        if (numero != null && buscarAplicacao(numero).isPresent()) {
            throw new IllegalArgumentException("Número de aplicação já cadastrado no sistema: " + numero);
        }

        titular.adicionarAplicacao(aplicacao);
    }

    /**
     * Sobrecarga de conveniência que encapsula uma {@link AplicacaoFinanceira} pura antes do cadastro.
     *
     * @param cpf documento do titular
     * @param aplicacaoBase aplicação financeira pura
     * @throws NullPointerException se aplicacaoBase for nula
     * @throws IllegalArgumentException se titular não encontrado ou aplicação duplicada
     */
    public synchronized void cadastrarAplicacao(String cpf, AplicacaoFinanceira aplicacaoBase) {
        Objects.requireNonNull(cpf, "CPF do titular é obrigatório.");
        Objects.requireNonNull(aplicacaoBase, "Aplicação financeira é obrigatória.");
        cadastrarAplicacao(cpf, new MultiplasOperacoesFinanceiras(aplicacaoBase));
    }

    /**
     * Localiza globalmente uma aplicação financeira em todo o sistema a partir de seu número.
     *
     * @param numeroAplicacao identificador único da aplicação
     * @return {@link Optional} com a aplicação se encontrada em qualquer titular, ou vazio caso contrário
     */
    public synchronized Optional<MultiplasOperacoesFinanceiras> buscarAplicacao(String numeroAplicacao) {
        if (numeroAplicacao == null || numeroAplicacao.isBlank()) {
            return Optional.empty();
        }
        for (Cliente cliente : clientes.values()) {
            Optional<MultiplasOperacoesFinanceiras> encontrada = cliente.buscarAplicacao(numeroAplicacao);
            if (encontrada.isPresent()) {
                return encontrada;
            }
        }
        return Optional.empty();
    }

    /**
     * Retorna a lista de aplicações cadastradas para o cliente informado.
     *
     * @param cpf documento do titular
     * @return lista imutável de aplicações do titular
     * @throws NullPointerException se o CPF for nulo
     * @throws IllegalArgumentException se o cliente não for localizado
     */
    public synchronized List<MultiplasOperacoesFinanceiras> getAplicacoes(String cpf) {
        return requererCliente(cpf).getAplicacoes();
    }

    /** Altera os campos descritivos de uma aplicação sem modificar seu histórico financeiro. */
    public synchronized void alterarAplicacao(
            String cpf,
            String numeroAplicacao,
            String nome,
            BigDecimal taxaMensal
    ) {
        if (nome == null || nome.isBlank()) {
            throw new IllegalArgumentException("Nome da aplicação é obrigatório.");
        }
        Objects.requireNonNull(taxaMensal, "Taxa mensal é obrigatória.");
        if (taxaMensal.signum() < 0 || taxaMensal.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("A taxa mensal deve estar entre zero e um.");
        }
        MultiplasOperacoesFinanceiras carteira =
                requererCliente(cpf).requererAplicacao(numeroAplicacao);
        carteira.getAplicacao().setNomeAplicacao(nome);
        carteira.getAplicacao().setTaxaMensal(taxaMensal);
    }

    /** Exclui uma aplicação da carteira do titular. */
    public synchronized void excluirAplicacao(String cpf, String numeroAplicacao) {
        requererCliente(cpf).removerAplicacao(numeroAplicacao);
    }

    /** Substitui um lançamento e valida novamente o saldo e as regras da aplicação. */
    public synchronized void alterarOperacao(
            String cpf,
            String numeroAplicacao,
            int indice,
            OperacaoFinanceira novaOperacao
    ) {
        Objects.requireNonNull(novaOperacao, "Nova operação é obrigatória.");
        requererCliente(cpf).requererAplicacao(numeroAplicacao).alterarOperacao(indice, novaOperacao);
    }

    /** Exclui um lançamento e reconstrói o saldo sem seu efeito financeiro. */
    public synchronized void excluirOperacao(String cpf, String numeroAplicacao, int indice) {
        requererCliente(cpf).requererAplicacao(numeroAplicacao).alterarOperacao(indice, null);
    }

    /**
     * Registra e executa um lançamento de fluxo de caixa em uma aplicação financeira de um cliente.
     *
     * @param cpf documento do titular da aplicação
     * @param numeroAplicacao identificador da aplicação de destino
     * @param operacao operação de depósito ou retirada a ser aplicada
     * @throws NullPointerException se cpf, numeroAplicacao ou operacao forem nulos
     * @throws IllegalArgumentException se cliente ou aplicação não existirem, ou se violar regras contábeis
     */
    public synchronized void cadastrarOperacao(String cpf, String numeroAplicacao, OperacaoFinanceira operacao) {
        Objects.requireNonNull(cpf, "CPF do cliente é obrigatório.");
        Objects.requireNonNull(numeroAplicacao, "Número da aplicação é obrigatório.");
        Objects.requireNonNull(operacao, "Operação financeira é obrigatória.");
        Cliente cliente = requererCliente(cpf);
        MultiplasOperacoesFinanceiras terminal = cliente.requererAplicacao(numeroAplicacao);
        terminal.adicionarOperacao(operacao);
    }

    // =========================================================================
    // Atualização em Lote Restrita a Dias Úteis (Item 7, R3.1)
    // =========================================================================

    /**
     * Processa a atualização de rendimentos de todas as aplicações cadastradas no SGI na data civil informada.
     *
     * <p>Em conformidade estrita com o Item 7 e o Item 3, a atualização de rentabilidade opera
     * exclusivamente nos dias de semana (segunda a sexta-feira). Em sábados e domingos, a rotina
     * não processa rendimentos patrimoniais (operação no-op segura).</p>
     *
     * @param data data de referência para a atualização em lote
     * @throws NullPointerException se a data for nula
     */
    public synchronized void atualizarAplicacoes(LocalDate data) {
        Objects.requireNonNull(data, "Data de atualização é obrigatória.");
        DiaSemana calendario = new DiaSemana(data);
        if (!calendario.ehDiaUtil()) {
            return;
        }

        for (Cliente cliente : clientes.values()) {
            cliente.atualizarAplicacoes(data);
        }
    }

    /**
     * Atualiza todas as aplicações cadastradas no SGI considerando a data corrente do sistema.
     */
    public synchronized void atualizarAplicacoes() {
        atualizarAplicacoes(LocalDate.now());
    }

    // =========================================================================
    // Consultas de Saldos: Individual, Consolidado e Global (Item 7, R3.1)
    // =========================================================================

    /**
     * Retorna o saldo patrimonial líquido de uma aplicação específica de um cliente.
     *
     * @param cpf documento do titular
     * @param numeroAplicacao identificador da aplicação
     * @return saldo líquido em reais
     * @throws NullPointerException se o cpf ou o numeroAplicacao forem nulos
     * @throws IllegalArgumentException se o titular ou a aplicação não existirem
     */
    public synchronized BigDecimal getSaldo(String cpf, String numeroAplicacao) {
        Objects.requireNonNull(cpf, "CPF do cliente é obrigatório.");
        Objects.requireNonNull(numeroAplicacao, "Número da aplicação é obrigatório.");
        return requererCliente(cpf).getSaldo(numeroAplicacao);
    }

    /**
     * Retorna o saldo patrimonial líquido consolidado de todas as aplicações do cliente.
     *
     * @param cpf documento do titular
     * @return somatório líquido das aplicações do cliente
     * @throws NullPointerException se o cpf for nulo
     * @throws IllegalArgumentException se o titular não for localizado
     */
    public synchronized BigDecimal getSaldo(String cpf) {
        Objects.requireNonNull(cpf, "CPF do cliente é obrigatório.");
        return requererCliente(cpf).getSaldoConsolidado();
    }

    /**
     * Retorna o saldo patrimonial líquido total de todo o Sistema de Gestão de Investimentos.
     *
     * @return somatório dos saldos consolidados de todos os clientes cadastrados
     */
    public synchronized BigDecimal getSaldo() {
        BigDecimal consolidado = BigDecimal.ZERO;
        for (Cliente cliente : clientes.values()) {
            BigDecimal saldoCliente = cliente.getSaldoConsolidado();
            if (saldoCliente != null) {
                consolidado = consolidado.add(saldoCliente);
            }
        }
        return Moeda.arredondar(consolidado);
    }

    /**
     * Retorna o saldo bruto de uma aplicação específica do cliente.
     *
     * @param cpf documento do titular
     * @param numeroAplicacao identificador da aplicação
     * @return saldo bruto atual da aplicação
     * @throws NullPointerException se o cpf ou o numeroAplicacao forem nulos
     * @throws IllegalArgumentException se o titular ou a aplicação não existirem
     */
    public synchronized BigDecimal getSaldoBruto(String cpf, String numeroAplicacao) {
        Objects.requireNonNull(cpf, "CPF do cliente é obrigatório.");
        Objects.requireNonNull(numeroAplicacao, "Número da aplicação é obrigatório.");
        return requererCliente(cpf).requererAplicacao(numeroAplicacao).getSaldoBruto();
    }

    /**
     * Retorna o saldo bruto consolidado de todas as aplicações do cliente.
     *
     * @param cpf documento do titular
     * @return somatório dos saldos brutos da carteira do cliente
     * @throws NullPointerException se o cpf for nulo
     * @throws IllegalArgumentException se o titular não for localizado
     */
    public synchronized BigDecimal getSaldoBruto(String cpf) {
        Objects.requireNonNull(cpf, "CPF do cliente é obrigatório.");
        BigDecimal total = BigDecimal.ZERO;
        for (MultiplasOperacoesFinanceiras terminal : requererCliente(cpf).getAplicacoes()) {
            BigDecimal bruto = terminal.getSaldoBruto();
            if (bruto != null) {
                total = total.add(bruto);
            }
        }
        return Moeda.arredondar(total);
    }

    /**
     * Retorna o saldo patrimonial bruto global consolidado de todo o sistema.
     *
     * @return somatório de saldos brutos de todos os clientes
     */
    public synchronized BigDecimal getSaldoBruto() {
        BigDecimal total = BigDecimal.ZERO;
        for (Cliente cliente : clientes.values()) {
            for (MultiplasOperacoesFinanceiras terminal : cliente.getAplicacoes()) {
                BigDecimal bruto = terminal.getSaldoBruto();
                if (bruto != null) {
                    total = total.add(bruto);
                }
            }
        }
        return Moeda.arredondar(total);
    }

    // =========================================================================
    // Extratos: Individual, Consolidado por Cliente e Global do SGI (Item 7)
    // =========================================================================

    /**
     * Retorna o extrato dos últimos 30 dias de uma aplicação específica do cliente.
     *
     * @param cpf documento do titular
     * @param numeroAplicacao identificador da aplicação
     * @return lista de operações dos últimos 30 dias
     * @throws NullPointerException se cpf ou numeroAplicacao forem nulos
     * @throws IllegalArgumentException se o cliente ou aplicação não existirem
     */
    public synchronized List<OperacaoFinanceira> getExtrato(String cpf, String numeroAplicacao) {
        Objects.requireNonNull(cpf, "CPF do cliente é obrigatório.");
        Objects.requireNonNull(numeroAplicacao, "Número da aplicação é obrigatório.");
        return requererCliente(cpf).getExtrato(numeroAplicacao);
    }

    /**
     * Retorna o extrato polimórfico de uma aplicação específica entre duas datas civis.
     *
     * @param cpf documento do titular
     * @param numeroAplicacao identificador da aplicação
     * @param inicio data inicial inclusiva
     * @param fim data final inclusiva
     * @return lista ordenada cronologicamente de operações no intervalo
     * @throws NullPointerException se parâmetros forem nulos
     * @throws IllegalArgumentException se fim for anterior a inicio, ou cliente/aplicação inexistentes
     */
    public synchronized List<OperacaoFinanceira> getExtrato(String cpf, String numeroAplicacao,
                                                           LocalDate inicio, LocalDate fim) {
        Objects.requireNonNull(cpf, "CPF do cliente é obrigatório.");
        Objects.requireNonNull(numeroAplicacao, "Número da aplicação é obrigatório.");
        Objects.requireNonNull(inicio, "Data inicial é obrigatória.");
        Objects.requireNonNull(fim, "Data final é obrigatória.");
        return requererCliente(cpf).getExtrato(numeroAplicacao, inicio, fim);
    }

    /**
     * Sobrecarga inteira de extrato individual entre datas expressas em dia, mês e ano.
     *
     * @param cpf documento do titular
     * @param numeroAplicacao identificador da aplicação
     * @param diaIni dia inicial
     * @param mesIni mês inicial
     * @param anoIni ano inicial
     * @param diaFim dia final
     * @param mesFim mês final
     * @param anoFim ano final
     * @return lista cronológica de lançamentos no período
     */
    public synchronized List<OperacaoFinanceira> getExtrato(String cpf, String numeroAplicacao,
                                                           int diaIni, int mesIni, int anoIni,
                                                           int diaFim, int mesFim, int anoFim) {
        return getExtrato(cpf, numeroAplicacao, LocalDate.of(anoIni, mesIni, diaIni), LocalDate.of(anoFim, mesFim, diaFim));
    }

    /**
     * Retorna o extrato consolidado de todas as aplicações do titular nos últimos 30 dias.
     *
     * @param cpf documento do titular
     * @return lista unificada de operações em ordem cronológica
     * @throws NullPointerException se o cpf for nulo
     * @throws IllegalArgumentException se o cliente não existir
     */
    public synchronized List<OperacaoFinanceira> getExtrato(String cpf) {
        Objects.requireNonNull(cpf, "CPF do cliente é obrigatório.");
        return requererCliente(cpf).getExtratoConsolidado();
    }

    /**
     * Retorna o extrato consolidado de todas as aplicações do titular entre duas datas civis.
     *
     * @param cpf documento do titular
     * @param inicio data inicial inclusiva
     * @param fim data final inclusiva
     * @return lista unificada de operações do titular no período
     * @throws NullPointerException se parâmetros forem nulos
     * @throws IllegalArgumentException se fim for anterior a inicio, ou cliente não existir
     */
    public synchronized List<OperacaoFinanceira> getExtrato(String cpf, LocalDate inicio, LocalDate fim) {
        Objects.requireNonNull(cpf, "CPF do cliente é obrigatório.");
        Objects.requireNonNull(inicio, "Data inicial é obrigatória.");
        Objects.requireNonNull(fim, "Data final é obrigatória.");
        return requererCliente(cpf).getExtratoConsolidado(inicio, fim);
    }

    /**
     * Sobrecarga inteira de extrato consolidado de cliente entre datas expressas em dia, mês e ano.
     *
     * @param cpf documento do titular
     * @param diaIni dia inicial
     * @param mesIni mês inicial
     * @param anoIni ano inicial
     * @param diaFim dia final
     * @param mesFim mês final
     * @param anoFim ano final
     * @return lista unificada de operações do titular no período
     */
    public synchronized List<OperacaoFinanceira> getExtrato(String cpf,
                                                           int diaIni, int mesIni, int anoIni,
                                                           int diaFim, int mesFim, int anoFim) {
        return getExtrato(cpf, LocalDate.of(anoIni, mesIni, diaIni), LocalDate.of(anoFim, mesFim, diaFim));
    }

    /**
     * Retorna o extrato consolidado de todas as movimentações financeiras do SGI nos últimos 30 dias.
     *
     * @return lista unificada de operações de todo o sistema em ordem cronológica
     */
    public synchronized List<OperacaoFinanceira> getExtrato() {
        List<OperacaoFinanceira> unificado = new ArrayList<>();
        for (Cliente cliente : clientes.values()) {
            unificado.addAll(cliente.getExtratoConsolidado());
        }
        unificado.sort(Comparator.comparing(OperacaoFinanceira::getData));
        return Collections.unmodifiableList(unificado);
    }

    /**
     * Retorna o extrato consolidado global de todo o SGI compreendido entre duas datas civis.
     *
     * @param inicio data inicial inclusiva do intervalo
     * @param fim data final inclusiva do intervalo
     * @return lista unificada e cronológica de lançamentos de todo o sistema no período
     * @throws NullPointerException se inicio ou fim forem nulos
     * @throws IllegalArgumentException se a data final anteceder a inicial
     */
    public synchronized List<OperacaoFinanceira> getExtrato(LocalDate inicio, LocalDate fim) {
        Objects.requireNonNull(inicio, "Data inicial é obrigatória.");
        Objects.requireNonNull(fim, "Data final é obrigatória.");
        if (fim.isBefore(inicio)) {
            throw new IllegalArgumentException("A data final do período não pode anteceder a data inicial.");
        }

        List<OperacaoFinanceira> unificado = new ArrayList<>();
        for (Cliente cliente : clientes.values()) {
            unificado.addAll(cliente.getExtratoConsolidado(inicio, fim));
        }
        unificado.sort(Comparator.comparing(OperacaoFinanceira::getData));
        return Collections.unmodifiableList(unificado);
    }

    /**
     * Sobrecarga inteira de extrato consolidado global do SGI entre datas expressas em dia, mês e ano.
     *
     * @param diaIni dia inicial
     * @param mesIni mês inicial
     * @param anoIni ano inicial
     * @param diaFim dia final
     * @param mesFim mês final
     * @param anoFim ano final
     * @return lista unificada de operações de todo o sistema no período
     */
    public synchronized List<OperacaoFinanceira> getExtrato(int diaIni, int mesIni, int anoIni,
                                                           int diaFim, int mesFim, int anoFim) {
        return getExtrato(LocalDate.of(anoIni, mesIni, diaIni), LocalDate.of(anoFim, mesFim, diaFim));
    }

    // =========================================================================
    // Persistência em Disco: arquivoSalvarDados e arquivoLerDados (Item 7)
    // =========================================================================

    /**
     * Salva o estado completo de todos os clientes, aplicações e operações do SGI no arquivo informado.
     *
     * <p>Delega a gravação atômica via arquivo temporário com permissões seguras POSIX 0600
     * à classe {@link ArmazenadorArquivo}.</p>
     *
     * @param caminho rota do arquivo no sistema de arquivos
     * @throws IOException se ocorrer falha na gravação em disco
     * @throws NullPointerException se o caminho for nulo
     */
    public synchronized void arquivoSalvarDados(Path caminho) throws IOException {
        Objects.requireNonNull(caminho, "Caminho do arquivo é obrigatório.");
        ArmazenadorArquivo.salvarDados(caminho, this.clientes.values());
    }

    /**
     * Sobrecarga de gravação que aceita representação em texto do caminho.
     *
     * @param caminho texto com a rota do arquivo
     * @throws IOException se falhar a gravação
     * @throws NullPointerException se o caminho for nulo
     */
    public synchronized void arquivoSalvarDados(String caminho) throws IOException {
        Objects.requireNonNull(caminho, "Caminho do arquivo é obrigatório.");
        arquivoSalvarDados(Path.of(caminho));
    }

    /**
     * Salva o estado do SGI no caminho de dados padrão do projeto (data/sgi.db).
     *
     * @throws IOException se ocorrer erro de gravação
     */
    public synchronized void arquivoSalvarDados() throws IOException {
        arquivoSalvarDados(CAMINHO_PADRAO_DADOS);
    }

    /**
     * Reconstrói o estado completo de um SGI a partir de um arquivo em formato TSV versionado.
     *
     * @param caminho rota do arquivo persistido
     * @return instância de {@link SGI} contendo todos os clientes, carteiras e histórico de lançamentos
     * @throws IOException se o arquivo não existir ou estiver corrompido
     * @throws NullPointerException se o caminho for nulo
     */
    public static SGI arquivoLerDados(Path caminho) throws IOException {
        Objects.requireNonNull(caminho, "Caminho do arquivo é obrigatório.");
        return ArmazenadorArquivo.carregarDados(caminho);
    }

    /**
     * Sobrecarga de restauração a partir de rota em texto.
     *
     * @param caminho texto do caminho
     * @return instância reconstituída
     * @throws IOException se falhar a leitura
     * @throws NullPointerException se o caminho for nulo
     */
    public static SGI arquivoLerDados(String caminho) throws IOException {
        Objects.requireNonNull(caminho, "Caminho do arquivo é obrigatório.");
        return arquivoLerDados(Path.of(caminho));
    }

    /**
     * Restaura o SGI a partir da base padrão (data/sgi.db).
     *
     * @return instância reconstituída do banco padrão
     * @throws IOException se ocorrer falha de leitura
     */
    public static SGI arquivoLerDados() throws IOException {
        return arquivoLerDados(CAMINHO_PADRAO_DADOS);
    }

    /**
     * Recarrega e substitui atomicamente o estado interno desta instância a partir do arquivo informado.
     *
     * @param caminho caminho do arquivo a ser lido
     * @throws IOException se houver erro de leitura
     * @throws NullPointerException se o caminho for nulo
     */
    public synchronized void carregarDados(Path caminho) throws IOException {
        Objects.requireNonNull(caminho, "Caminho do arquivo é obrigatório.");
        SGI restaurado = arquivoLerDados(caminho);
        this.clientes.clear();
        for (Cliente cliente : restaurado.getClientes()) {
            this.clientes.put(normalizarCpf(cliente.getDadosPessoais().getCpf()), cliente);
        }
        this.ultimasCotacoes.clear();
        this.ultimasCotacoes.putAll(restaurado.getCotacoes());
    }

    /**
     * Sobrecarga de recarregamento a partir de representação textual do caminho.
     *
     * @param caminho rota em texto
     * @throws IOException se falhar a leitura
     * @throws NullPointerException se o caminho for nulo
     */
    public synchronized void carregarDados(String caminho) throws IOException {
        Objects.requireNonNull(caminho, "Caminho do arquivo é obrigatório.");
        carregarDados(Path.of(caminho));
    }

    /**
     * Recarrega o estado a partir do arquivo padrão (data/sgi.db).
     *
     * @throws IOException se falhar a leitura
     */
    public synchronized void carregarDados() throws IOException {
        carregarDados(CAMINHO_PADRAO_DADOS);
    }

    // =========================================================================
    // Ponto de Acoplamento com Cotações de Mercado (Item 7 & Item 9)
    // =========================================================================

    /**
     * Atualiza as taxas e cotações de mercado ativas recebidas do servidor de cotações TCP.
     *
     * <p>Normaliza as chaves para maiúsculas e cria mapeamentos bidirecionais automáticos
     * entre códigos em moeda e identificadores descritivos em português (ex.: USD &lt;-&gt; DOLAR,
     * EUR &lt;-&gt; EURO, SELICRA e CDIR).</p>
     *
     * @param cotacoes mapa associativo contendo os códigos e os respectivos valores em BigDecimal
     * @throws NullPointerException se o mapa de cotações for nulo
     */
    public synchronized void atualizarCotacoes(Map<String, BigDecimal> cotacoes) {
        Objects.requireNonNull(cotacoes, "Mapa de cotações é obrigatório.");
        for (Map.Entry<String, BigDecimal> entry : cotacoes.entrySet()) {
            String chave = entry.getKey();
            BigDecimal valor = entry.getValue();
            if (chave == null || valor == null) {
                continue;
            }

            String codigo = chave.trim().toUpperCase();
            this.ultimasCotacoes.put(codigo, valor);

            // Mapeamentos bidirecionais de compatibilidade
            if ("USD".equals(codigo)) {
                this.ultimasCotacoes.put("DOLAR", valor);
            } else if ("DOLAR".equals(codigo)) {
                this.ultimasCotacoes.put("USD", valor);
            } else if ("EUR".equals(codigo)) {
                this.ultimasCotacoes.put("EURO", valor);
            } else if ("EURO".equals(codigo)) {
                this.ultimasCotacoes.put("EUR", valor);
            }
        }
    }

    /**
     * Retorna uma cópia defensiva imutável das cotações e taxas de mercado ativas no sistema.
     *
     * @return mapa imutável das cotações correntes
     */
    public synchronized Map<String, BigDecimal> getCotacoes() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(this.ultimasCotacoes));
    }

    /**
     * Consulta o valor de uma cotação ou taxa de mercado específica.
     *
     * @param codigoOuNome código identificador (ex.: SELICRA, CDIR, DOLAR, USD, EURO, EUR)
     * @return {@link Optional} contendo a cotação se presente, ou vazio caso contrário
     */
    public synchronized Optional<BigDecimal> getCotacao(String codigoOuNome) {
        if (codigoOuNome == null || codigoOuNome.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(this.ultimasCotacoes.get(codigoOuNome.trim().toUpperCase()));
    }

    // =========================================================================
    // Métodos Auxiliares Privados de Validação
    // =========================================================================

    /**
     * Normaliza e valida um CPF, garantindo que contenha 11 dígitos numéricos válidos.
     */
    private static String normalizarCpf(String cpf) {
        if (cpf == null) {
            throw new IllegalArgumentException("CPF é obrigatório.");
        }
        String digitos = cpf.replaceAll("\\D", "");
        if (digitos.length() != 11) {
            throw new IllegalArgumentException("CPF deve conter exatamente 11 dígitos. Informado: " + cpf);
        }
        return digitos;
    }

    // =========================================================================
    // Aliases Transitórios de Compatibilidade Legada (servicos.SistemaInvestimentos)
    // =========================================================================

    /**
     * Alias de compatibilidade com o legado para busca de cliente.
     *
     * @param cpf documento do cliente
     * @return optional do cliente
     */
    public synchronized Optional<Cliente> findClient(String cpf) {
        return buscarCliente(cpf);
    }

    /**
     * Alias de compatibilidade com o legado para saldo do cliente.
     *
     * @param cpf documento do cliente
     * @return saldo consolidado
     */
    public synchronized BigDecimal getClientBalance(String cpf) {
        return getSaldo(cpf);
    }

    /**
     * Alias de compatibilidade com o legado para saldo total do sistema.
     *
     * @return saldo global
     */
    public synchronized BigDecimal getSystemBalance() {
        return getSaldo();
    }

    /**
     * Alias de compatibilidade com o legado para saldo de aplicação.
     *
     * @param cpf documento
     * @param numero identificador da aplicação
     * @return saldo líquido da aplicação
     */
    public synchronized BigDecimal getBalance(String cpf, String numero) {
        return getSaldo(cpf, numero);
    }

    /**
     * Alias de compatibilidade com o legado para atualização em lote.
     *
     * @param data data de atualização
     */
    public synchronized void updateAll(LocalDate data) {
        atualizarAplicacoes(data);
    }

    /**
     * Alias de compatibilidade com o legado para gravação em arquivo.
     *
     * @param caminho rota do arquivo
     * @throws IOException em caso de erro de gravação
     */
    public synchronized void save(Path caminho) throws IOException {
        arquivoSalvarDados(caminho);
    }

    /**
     * Alias de compatibilidade com o legado para carregamento de arquivo.
     *
     * @param caminho rota do arquivo
     * @return instância de SGI restaurada
     * @throws IOException se falhar a leitura
     */
    public static SGI load(Path caminho) throws IOException {
        return arquivoLerDados(caminho);
    }
}

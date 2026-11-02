package test.dominio;

import static test.Afirmacao.*;

import dominio.AplicacaoFinanceira;
import dominio.DADOSPESSOAIS;
import dominio.DiaSemana;
import dominio.MultiplasOperacoesFinanceiras;
import dominio.OperacaoFinanceira;
import dominio.TipoOperacao;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Suíte de Testes Adversariais e Estresse Empírico Hostil para o Milestone 2 (Itens 4 e 5).
 *
 * <p>Submete {@link MultiplasOperacoesFinanceiras} e {@link DADOSPESSOAIS} a cenários
 * destrutivos de fronteira, mutações inválidas, ataques de injeção, corrupção de persistência,
 * quebra de atomicidade, estresse temporal e herança hostil.</p>
 */
public final class TesteDesafioHostilM2 {

    public static void main(String[] args) {
        System.out.println("================================================================================");
        System.out.println("   DESAFIO EMPÍRICO ADVERSARIAL — MILESTONE 2 (MOF & DADOSPESSOAIS)             ");
        System.out.println("================================================================================");
        long inicio = System.currentTimeMillis();
        int testes = 0;

        // ---------------------------------------------------------------------
        // PARTE 1: MultiplasOperacoesFinanceiras
        // ---------------------------------------------------------------------
        System.out.println("\n[CHALLENGER 1.1] Estresse do Limite de 100 Operações...");
        testarEstresseLimite100Operacoes(); testes++;

        System.out.println("[CHALLENGER 1.2] Estresse de Atualização em Fins de Semana em Série...");
        testarEstresseFinsDeSemanaEmSerie(); testes++;

        System.out.println("[CHALLENGER 1.3] Operações com Datas Retroativas Inválidas...");
        testarOperacoesDatasRetroativasInvalidas(); testes++;

        System.out.println("[CHALLENGER 1.4] Operações com Descrições Bizarras, Escape e Persistência...");
        testarOperacoesDescricoesBizarrasEPersistencia(); testes++;

        System.out.println("[CHALLENGER 1.5] Saques Hostis com Saldo Insuficiente...");
        testarSaquesSaldoInsuficiente(); testes++;

        System.out.println("[CHALLENGER 1.6] Persistência sob Corrupção de Disco e Permissões...");
        testarPersistenciaCorrupcaoDiscoEPermissoes(); testes++;

        System.out.println("[CHALLENGER 1.7] Subclasse Hostil contra Escape Virtual no Construtor de MOF...");
        testarSubclasseHostilAusenciaEscapeVirtualMOF(); testes++;

        // ---------------------------------------------------------------------
        // PARTE 2: DADOSPESSOAIS
        // ---------------------------------------------------------------------
        System.out.println("\n[CHALLENGER 2.1] Bateria dos 10 Casos de CPFs com 11 Dígitos Repetidos...");
        testarBateria10CpfsDigitosRepetidos(); testes++;

        System.out.println("[CHALLENGER 2.2] Bateria de CPFs com Dígitos Verificadores Invertidos e Adulterados...");
        testarCpfsComDVsInvertidosEAdulterados(); testes++;

        System.out.println("[CHALLENGER 2.3] Bateria de CPFs Alfanuméricos e Tamanhos Anômalos...");
        testarCpfsAlfanumericosETamanhosAnomalos(); testes++;

        System.out.println("[CHALLENGER 2.4] Quebra de Atomicidade em Falhas Cadastrais...");
        testarQuebraAtomicidadeSobFalha(); testes++;

        System.out.println("[CHALLENGER 2.5] Mascaramento Estrito em toString (Vazamento Zero de CPF)...");
        testarMascaramentoEstritoToStringSemVazamento(); testes++;

        System.out.println("[CHALLENGER 2.6] Subclasse Hostil contra Escape Virtual no Construtor de DADOSPESSOAIS...");
        testarSubclasseHostilAusenciaEscapeVirtualDadosPessoais(); testes++;

        System.out.println("[CHALLENGER 2.7] Análise de Sanitização e Permissividade de Caracteres Não-Dígitos...");
        testarComportamentoSanitizacaoLeniente(); testes++;

        long duracao = System.currentTimeMillis() - inicio;
        System.out.printf("%n================================================================================%n");
        System.out.printf(">>> TODOS OS %d TESTES HOSTIS PASSARAM INTEGRALMENTE (%d ms)%n", testes, duracao);
        System.out.printf("================================================================================%n");
    }

    private static AplicacaoFinanceira criarAplicacao(LocalDate dataInicio, BigDecimal saldoInicial) {
        return new AplicacaoFinanceira(
                "CDB Agressivo Hostil", "APP-CHALLENGE-01",
                saldoInicial, new BigDecimal("0.0300"),
                0, 0, 0, 0, 0, 0, dataInicio
        );
    }

    // =========================================================================
    // 1.1 Limite estrito de 100 operações
    // =========================================================================
    public static void testarEstresseLimite100Operacoes() {
        LocalDate data = LocalDate.of(2026, 9, 1);
        AplicacaoFinanceira terminal = criarAplicacao(data, new BigDecimal("10000.00"));
        MultiplasOperacoesFinanceiras mof = new MultiplasOperacoesFinanceiras(terminal);

        // Adiciona exatamente 100 operações válidas
        for (int i = 1; i <= 100; i++) {
            OperacaoFinanceira op = new OperacaoFinanceira(
                    data, new BigDecimal("10.00"), "Aporte estresse #" + i, TipoOperacao.DEPOSITO
            );
            mof.adicionarOperacao(op);
            afirmarIgual(i, mof.getQuantidadeOperacoes(), "quantidade de operações deve ser " + i);
        }

        afirmarIgual(100, mof.getQuantidadeOperacoes(), "atingiu exatamente 100 operações");
        afirmarIgual(new BigDecimal("11000.00"), mof.getSaldo(), "saldo acumulado dos 100 depósitos");

        // 101ª Operação: deve lançar IllegalStateException
        OperacaoFinanceira op101 = new OperacaoFinanceira(
                data, new BigDecimal("10.00"), "Aporte 101 proibido", TipoOperacao.DEPOSITO
        );
        afirmarRejeicao(() -> mof.adicionarOperacao(op101),
                IllegalStateException.class, "101ª operação deve ser barrada com IllegalStateException");

        // Tentativa de 101ª operação com retirada
        OperacaoFinanceira opRetirada101 = new OperacaoFinanceira(
                data, new BigDecimal("5.00"), "Resgate 101 proibido", TipoOperacao.RETIRADA
        );
        afirmarRejeicao(() -> mof.adicionarOperacao(opRetirada101),
                IllegalStateException.class, "101ª operação de resgate deve ser barrada com IllegalStateException");

        // Tentativa de vincular operação restaurada na capacidade máxima
        afirmarRejeicao(() -> mof.vincularOperacaoRestaurada(op101),
                IllegalStateException.class, "vincularOperacaoRestaurada além de 100 deve lançar IllegalStateException");

        // Estado interno deve permanecer intacto com 100 operações e saldo inalterado
        afirmarIgual(100, mof.getQuantidadeOperacoes(), "quantidade deve permanecer em 100");
        afirmarIgual(new BigDecimal("11000.00"), mof.getSaldo(), "saldo não deve sofrer mutação");

        // Imutabilidade da visão de lista
        afirmarRejeicao(() -> mof.getOperacoes().add(op101),
                UnsupportedOperationException.class, "lista retornada por getOperacoes deve ser estritamente imutável");
        afirmarRejeicao(() -> mof.getOperacoes().clear(),
                UnsupportedOperationException.class, "lista de operações não pode ser limpa externamente");
    }

    // =========================================================================
    // 1.2 Atualização em fins de semana em série
    // =========================================================================
    public static void testarEstresseFinsDeSemanaEmSerie() {
        // Sexta-feira: 2026-08-07
        LocalDate sexta = LocalDate.of(2026, 8, 7);
        LocalDate sabado = LocalDate.of(2026, 8, 8);
        LocalDate domingo = LocalDate.of(2026, 8, 9);
        LocalDate segunda = LocalDate.of(2026, 8, 10);

        AplicacaoFinanceira terminal = new AplicacaoFinanceira(
                "LCI Fim de Semana", "APP-FDS",
                new BigDecimal("10000.00"), new BigDecimal("0.0300"), // 3% ao mês
                0, 0, 0, 0, 0, 0, sexta
        );
        MultiplasOperacoesFinanceiras mof = new MultiplasOperacoesFinanceiras(terminal);

        // Atualiza na sexta-feira (dia útil)
        mof.atualizarAplicacao(sexta);
        BigDecimal saldoSexta = mof.getSaldo();
        BigDecimal rendBrutoSexta = mof.getRendimentoBruto();
        int diasCorridosSexta = terminal.getDiasCorridos();
        int diasEfetivosSexta = terminal.getDiasEfetivosCalculo();

        // Estresse: Chama atualizarAplicacao no sábado repetidas vezes
        for (int i = 0; i < 5; i++) {
            mof.atualizarAplicacao(sabado);
            afirmarFalso(mof.getCalendario().ehDiaUtil(), "sábado não é dia útil");
            afirmarIgual(saldoSexta, mof.getSaldo(), "saldo não pode alterar no sábado");
            afirmarIgual(rendBrutoSexta, mof.getRendimentoBruto(), "rendimento bruto não pode alterar no sábado");
            afirmarIgual(diasCorridosSexta, terminal.getDiasCorridos(), "dias corridos inalterados no sábado");
            afirmarIgual(diasEfetivosSexta, terminal.getDiasEfetivosCalculo(), "dias efetivos inalterados no sábado");
        }

        // Estresse: Chama atualizarAplicacao no domingo repetidas vezes
        for (int i = 0; i < 5; i++) {
            mof.atualizarAplicacao(domingo);
            afirmarFalso(mof.getCalendario().ehDiaUtil(), "domingo não é dia útil");
            afirmarIgual(saldoSexta, mof.getSaldo(), "saldo não pode alterar no domingo");
            afirmarIgual(rendBrutoSexta, mof.getRendimentoBruto(), "rendimento bruto não pode alterar no domingo");
            afirmarIgual(diasCorridosSexta, terminal.getDiasCorridos(), "dias corridos inalterados no domingo");
            afirmarIgual(diasEfetivosSexta, terminal.getDiasEfetivosCalculo(), "dias efetivos inalterados no domingo");
        }

        // Retomada na segunda-feira (dia útil)
        mof.atualizarAplicacao(segunda);
        afirmarVerdadeiro(mof.getCalendario().ehDiaUtil(), "segunda-feira deve ser dia útil");
        afirmarVerdadeiro(mof.getSaldo().compareTo(saldoSexta) > 0, "saldo deve evoluir na segunda-feira");
        afirmarVerdadeiro(terminal.getDiasCorridos() > diasCorridosSexta, "dias corridos devem progredir");
        afirmarVerdadeiro(terminal.getDiasEfetivosCalculo() > diasEfetivosSexta, "dias efetivos devem progredir");
    }

    // =========================================================================
    // 1.3 Operações com datas retroativas inválidas
    // =========================================================================
    public static void testarOperacoesDatasRetroativasInvalidas() {
        LocalDate dataInicio = LocalDate.of(2026, 6, 1);
        AplicacaoFinanceira terminal = criarAplicacao(dataInicio, new BigDecimal("5000.00"));
        MultiplasOperacoesFinanceiras mof = new MultiplasOperacoesFinanceiras(terminal);

        // Operação antes da criação da aplicação
        LocalDate dataAntes = dataInicio.minusDays(1);
        OperacaoFinanceira opRetroativa = new OperacaoFinanceira(
                dataAntes, new BigDecimal("100.00"), "Aporte no Passado", TipoOperacao.DEPOSITO
        );
        afirmarRejeicao(() -> mof.adicionarOperacao(opRetroativa),
                IllegalArgumentException.class, "operação com data anterior à aplicação deve falhar");

        // Atualização patrimonial com data anterior
        afirmarRejeicao(() -> mof.atualizarAplicacao(dataInicio.minusDays(10)),
                IllegalArgumentException.class, "atualização com data anterior à aplicação deve falhar");

        // Atualização com data nula
        afirmarRejeicao(() -> mof.atualizarAplicacao(null),
                IllegalArgumentException.class, "atualização com data nula deve falhar");

        // Operação nula
        afirmarRejeicao(() -> mof.adicionarOperacao(null),
                IllegalArgumentException.class, "operação nula deve falhar");

        // Extrato com datas invertidas
        afirmarRejeicao(() -> mof.getExtrato(dataInicio.plusDays(10), dataInicio),
                IllegalArgumentException.class, "extrato com fim anterior a início deve falhar");

        // Quantidade de operações deve permanecer 0
        afirmarIgual(0, mof.getQuantidadeOperacoes(), "nenhuma operação espúria deve ser cadastrada");
    }

    // =========================================================================
    // 1.4 Descrições bizarras, sanitização e round-trip de persistência
    // =========================================================================
    public static void testarOperacoesDescricoesBizarrasEPersistencia() {
        LocalDate data = LocalDate.of(2026, 7, 1);
        AplicacaoFinanceira terminal = criarAplicacao(data, new BigDecimal("20000.00"));
        MultiplasOperacoesFinanceiras mof = new MultiplasOperacoesFinanceiras(terminal);

        String[] descricoesHostis = new String[] {
            "Aporte com emojis: 💰🚀🎯💵 e acentuação: ÀÁÂÃÉÊÍÓÔÕÚÇ àáâãéêíóôõúç",
            "Descricao com tabs\te\ttabulacoes\tmultiplas",
            "Descricao com quebras de linha\nsegunda linha\r\nterceira linha",
            "C:\\Users\\alekkzsx\\AppData\\Local\\Temp\\sgi_data.tsv\\\\teste",
            "<script>alert('xss');</script> ' OR '1'='1' -- DROP TABLE operacoes;",
            "Descricao com aspas simples '' e duplas \"\" e pipes | e arrobas @#$%",
            "Repeticao extensa: " + "X".repeat(1000)
        };

        for (int i = 0; i < descricoesHostis.length; i++) {
            OperacaoFinanceira op = new OperacaoFinanceira(
                    data.plusDays(i), new BigDecimal("100.00"), descricoesHostis[i], TipoOperacao.DEPOSITO
            );
            mof.adicionarOperacao(op);
        }

        afirmarIgual(descricoesHostis.length, mof.getQuantidadeOperacoes(), "todas as descrições foram aceitas");

        // Salva e restaura do disco para verificar integridade do escape TSV
        Path arquivoTeste = Path.of("data/teste_hostil_mof.db");
        try {
            mof.arquivoSalvarDados(arquivoTeste);
            afirmarVerdadeiro(Files.exists(arquivoTeste), "arquivo foi gravado no disco");

            MultiplasOperacoesFinanceiras restaurado = MultiplasOperacoesFinanceiras.arquivoLerDados(arquivoTeste);
            afirmarIgual(mof.getQuantidadeOperacoes(), restaurado.getQuantidadeOperacoes(), "quantidade restaurada");
            afirmarIgual(mof.getSaldo(), restaurado.getSaldo(), "saldo restaurado");

            List<OperacaoFinanceira> opsRestauradas = restaurado.getOperacoes();
            for (int i = 0; i < descricoesHostis.length; i++) {
                afirmarIgual(descricoesHostis[i], opsRestauradas.get(i).getDescricao(),
                        "descrição hostil #" + i + " restaurada com fidelidade de 100%");
            }
        } catch (IOException e) {
            throw new AssertionError("Falha na persistência com descrições hostis", e);
        } finally {
            try {
                Files.deleteIfExists(arquivoTeste);
            } catch (IOException ignored) {
            }
        }
    }

    // =========================================================================
    // 1.5 Saques hostis com saldo líquido insuficiente
    // =========================================================================
    public static void testarSaquesSaldoInsuficiente() {
        LocalDate data = LocalDate.of(2026, 5, 4); // Segunda-feira
        AplicacaoFinanceira terminal = criarAplicacao(data, new BigDecimal("1000.00"));
        MultiplasOperacoesFinanceiras mof = new MultiplasOperacoesFinanceiras(terminal);

        // Saldo atual líquido é 1000.00
        afirmarIgual(new BigDecimal("1000.00"), mof.getSaldo(), "saldo inicial 1000.00");

        // Tentativa de resgate com centavo a mais (1000.01)
        OperacaoFinanceira saqueExcessivo1 = new OperacaoFinanceira(
                data, new BigDecimal("1000.01"), "Saque 1 centavo a mais", TipoOperacao.RETIRADA
        );
        afirmarRejeicao(() -> mof.adicionarOperacao(saqueExcessivo1),
                IllegalArgumentException.class, "saque com 1 centavo acima do saldo líquido deve ser rejeitado");

        // Tentativa de resgate astronômico (999.999.999,00)
        OperacaoFinanceira saqueAstronomico = new OperacaoFinanceira(
                data, new BigDecimal("999999999.00"), "Saque Astronomico", TipoOperacao.RETIRADA
        );
        afirmarRejeicao(() -> mof.adicionarOperacao(saqueAstronomico),
                IllegalArgumentException.class, "saque astronômico deve ser rejeitado");

        // Tentativa de resgate antes da carência mínima (se configurada)
        AplicacaoFinanceira appComCarencia = new AplicacaoFinanceira(
                "CDB Com Carência", "APP-CAR",
                new BigDecimal("5000.00"), new BigDecimal("0.0200"),
                0, 360, 60, 0, 0, 0, data // 60 dias de permanência mínima
        );
        MultiplasOperacoesFinanceiras mofCarencia = new MultiplasOperacoesFinanceiras(appComCarencia);
        OperacaoFinanceira saqueCarencia = new OperacaoFinanceira(
                data.plusDays(10), new BigDecimal("500.00"), "Resgate antes de 60 dias", TipoOperacao.RETIRADA
        );
        afirmarRejeicao(() -> mofCarencia.adicionarOperacao(saqueCarencia),
                IllegalArgumentException.class, "resgate durante prazo de carência deve ser rejeitado");

        // Estado das aplicações deve permanecer 100% íntegro
        afirmarIgual(0, mof.getQuantidadeOperacoes(), "quantidade de operações deve permanecer 0");
        afirmarIgual(new BigDecimal("1000.00"), mof.getSaldo(), "saldo deve permanecer 1000.00");
    }

    // =========================================================================
    // 1.6 Persistência sob corrupção de disco e permissões
    // =========================================================================
    public static void testarPersistenciaCorrupcaoDiscoEPermissoes() {
        Path tempDir;
        try {
            tempDir = Files.createTempDirectory("sgi_teste_corrupcao");
        } catch (IOException e) {
            throw new AssertionError("Não foi possível criar diretório temporário", e);
        }

        try {
            // Caso A: Arquivo inexistente
            afirmarRejeicao(() -> {
                try {
                    MultiplasOperacoesFinanceiras.arquivoLerDados(tempDir.resolve("inexistente.db"));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }, RuntimeException.class, "leitura de arquivo inexistente deve lançar IOException");

            // Caso B: Arquivo vazio (0 bytes)
            Path arqVazio = tempDir.resolve("vazio.db");
            Files.writeString(arqVazio, "");
            afirmarRejeicao(() -> {
                try {
                    MultiplasOperacoesFinanceiras.arquivoLerDados(arqVazio);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }, RuntimeException.class, "leitura de arquivo vazio deve falhar");

            // Caso C: Cabeçalho corrompido
            Path arqCabecalhoInvalido = tempDir.resolve("cabecalho_invalido.db");
            Files.writeString(arqCabecalhoInvalido, "FORMATO_DESCONHECIDO\t99\nI\tdados...");
            afirmarRejeicao(() -> {
                try {
                    MultiplasOperacoesFinanceiras.arquivoLerDados(arqCabecalhoInvalido);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }, RuntimeException.class, "leitura com cabeçalho incompatível deve falhar");

            // Caso D: Sem registro de aplicação
            Path arqSemAplicacao = tempDir.resolve("sem_app.db");
            Files.writeString(arqSemAplicacao, "SGI-MOF\t1\nK\t1\t8\t2026\t0\n");
            afirmarRejeicao(() -> {
                try {
                    MultiplasOperacoesFinanceiras.arquivoLerDados(arqSemAplicacao);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }, RuntimeException.class, "leitura sem registro 'I' de aplicação deve falhar");

            // Caso E: Registro 'I' incompleto
            Path arqIncompleto = tempDir.resolve("app_incompleta.db");
            Files.writeString(arqIncompleto, "SGI-MOF\t1\nI\tAPP-01\tNome\t1000\n");
            afirmarRejeicao(() -> {
                try {
                    MultiplasOperacoesFinanceiras.arquivoLerDados(arqIncompleto);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }, RuntimeException.class, "leitura com registro 'I' incompleto deve falhar");

            // Caso F: Registro de operação 'O' incompleto
            Path arqOpIncompleta = tempDir.resolve("op_incompleta.db");
            Files.writeString(arqOpIncompleta, "SGI-MOF\t1\n" +
                    "I\tAPP-01\tNome\t1000\t0.01\t0.000333333333\t0\t0\t0\t0\t0\t0\t2026-08-01\t1000\t0\t0\t2026-08-01\t0\t0\t0\n" +
                    "O\t1\t8\t2026\n");
            afirmarRejeicao(() -> {
                try {
                    MultiplasOperacoesFinanceiras.arquivoLerDados(arqOpIncompleta);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }, RuntimeException.class, "leitura com registro 'O' incompleto deve falhar");

            // Caso G: Registro desconhecido
            Path arqRegistroDesconhecido = tempDir.resolve("reg_desconhecido.db");
            Files.writeString(arqRegistroDesconhecido, "SGI-MOF\t1\n" +
                    "I\tAPP-01\tNome\t1000\t0.01\t0.000333333333\t0\t0\t0\t0\t0\t0\t2026-08-01\t1000\t0\t0\t2026-08-01\t0\t0\t0\n" +
                    "Z\tregistro\tinvalido\n");
            afirmarRejeicao(() -> {
                try {
                    MultiplasOperacoesFinanceiras.arquivoLerDados(arqRegistroDesconhecido);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }, RuntimeException.class, "leitura com registro desconhecido deve falhar");

        } catch (IOException e) {
            throw new AssertionError("Erro durante preparação de arquivos corrompidos", e);
        } finally {
            try {
                // Limpeza do diretório temporário
                try (var stream = Files.walk(tempDir)) {
                    stream.sorted((a, b) -> b.compareTo(a)).forEach(p -> {
                        try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                    });
                }
            } catch (IOException ignored) {}
        }
    }

    // =========================================================================
    // 1.7 Subclasse hostil para teste de escape virtual em MOF
    // =========================================================================
    private static class SubclasseHostilMOF extends MultiplasOperacoesFinanceiras {
        private String campoSubclasse = "INICIALIZADO";
        private boolean escapeVirtualOcorreu = false;

        public SubclasseHostilMOF(AplicacaoFinanceira terminal) {
            super(terminal);
        }

        public SubclasseHostilMOF(AplicacaoFinanceira terminal, DiaSemana cal) {
            super(terminal, cal);
        }

        @Override
        public void setAplicacao(AplicacaoFinanceira terminal) {
            if (this.campoSubclasse == null) {
                this.escapeVirtualOcorreu = true;
            }
            super.setAplicacao(terminal);
        }

        @Override
        public void setCalendario(DiaSemana cal) {
            if (this.campoSubclasse == null) {
                this.escapeVirtualOcorreu = true;
            }
            super.setCalendario(cal);
        }

        @Override
        public void adicionarOperacao(OperacaoFinanceira op) {
            if (this.campoSubclasse == null) {
                this.escapeVirtualOcorreu = true;
            }
            super.adicionarOperacao(op);
        }

        @Override
        public BigDecimal getSaldo() {
            if (this.campoSubclasse == null) {
                this.escapeVirtualOcorreu = true;
            }
            return super.getSaldo();
        }

        public boolean houveEscapeVirtual() {
            return escapeVirtualOcorreu;
        }
    }

    public static void testarSubclasseHostilAusenciaEscapeVirtualMOF() {
        LocalDate data = LocalDate.of(2026, 8, 3);
        AplicacaoFinanceira terminal = criarAplicacao(data, new BigDecimal("1000.00"));
        DiaSemana cal = new DiaSemana(data);

        SubclasseHostilMOF instancia1 = new SubclasseHostilMOF(terminal);
        afirmarFalso(instancia1.houveEscapeVirtual(),
                "construtor (terminal) de MOF não deve disparar métodos virtuais sobrescrevíveis");

        SubclasseHostilMOF instancia2 = new SubclasseHostilMOF(terminal, cal);
        afirmarFalso(instancia2.houveEscapeVirtual(),
                "construtor (terminal, cal) de MOF não deve disparar métodos virtuais sobrescrevíveis");
    }

    // =========================================================================
    // 2.1 Bateria de CPFs com 11 dígitos repetidos
    // =========================================================================
    public static void testarBateria10CpfsDigitosRepetidos() {
        String[] cpfsRepetidos = new String[] {
            "00000000000", "11111111111", "22222222222", "33333333333", "44444444444",
            "55555555555", "66666666666", "77777777777", "88888888888", "99999999999"
        };

        for (String cpf : cpfsRepetidos) {
            afirmarFalso(DADOSPESSOAIS.validarCpf(cpf), "validarCpf deve rejeitar: " + cpf);
            afirmarFalso(DADOSPESSOAIS.ehCpfValido(cpf), "ehCpfValido deve rejeitar: " + cpf);

            afirmarRejeicao(() -> new DADOSPESSOAIS("Teste", cpf, "11987654321"),
                    IllegalArgumentException.class, "construtor(3) deve rejeitar: " + cpf);
            afirmarRejeicao(() -> new DADOSPESSOAIS("Teste", cpf),
                    IllegalArgumentException.class, "construtor(2) deve rejeitar: " + cpf);

            DADOSPESSOAIS d = new DADOSPESSOAIS("Valido", "52998224725", "11987654321");
            afirmarRejeicao(() -> d.setCadastro("Novo", cpf, "11987654321"),
                    IllegalArgumentException.class, "setCadastro(3) deve rejeitar: " + cpf);
            afirmarRejeicao(() -> d.setCadastro("Novo", cpf),
                    IllegalArgumentException.class, "setCadastro(2) deve rejeitar: " + cpf);
            afirmarRejeicao(() -> d.alterarDados("Novo", cpf, "11987654321"),
                    IllegalArgumentException.class, "alterarDados(3) deve rejeitar: " + cpf);
            afirmarRejeicao(() -> d.setCpf(cpf),
                    IllegalArgumentException.class, "setCpf deve rejeitar: " + cpf);
        }
    }

    // =========================================================================
    // 2.2 CPFs com DVs invertidos e adulterados
    // =========================================================================
    public static void testarCpfsComDVsInvertidosEAdulterados() {
        // CPFs válidos de referência e variantes com DVs invertidos/adulterados
        // Base 1: 529.982.247-25 (D1=2, D2=5) -> Invertido: 529.982.247-52
        // Base 2: 000.000.001-91 (D1=9, D2=1) -> Invertido: 000.000.001-19
        // Base 3: 111.444.777-35 (D1=3, D2=5) -> Invertido: 111.444.777-53
        // Base 4: 123.456.789-09 (D1=0, D2=9) -> Invertido: 123.456.789-90
        String[][] pares = new String[][] {
            {"52998224725", "52998224752", "52998224724", "52998224735"},
            {"00000000191", "00000000119", "00000000190", "00000000181"},
            {"11144477735", "11144477753", "11144477736", "11144477725"},
            {"12345678909", "12345678990", "12345678908", "12345678919"}
        };

        for (String[] grupo : pares) {
            String valido = grupo[0];
            afirmarVerdadeiro(DADOSPESSOAIS.validarCpf(valido), "base deve ser válida: " + valido);

            for (int i = 1; i < grupo.length; i++) {
                String adulterado = grupo[i];
                afirmarFalso(DADOSPESSOAIS.validarCpf(adulterado), "adulterado deve falhar: " + adulterado);
                afirmarRejeicao(() -> new DADOSPESSOAIS("Teste", adulterado),
                        IllegalArgumentException.class, "construtor deve rejeitar DV adulterado: " + adulterado);
            }
        }
    }

    // =========================================================================
    // 2.3 CPFs alfanuméricos e tamanhos anômalos
    // =========================================================================
    public static void testarCpfsAlfanumericosETamanhosAnomalos() {
        String[] invalidos = new String[] {
            null,
            "",
            "   ",
            "1",
            "12",
            "123",
            "1234567890",       // 10 dígitos
            "123456789012",     // 12 dígitos
            "123456789012345",  // 15 dígitos
            "123.456.78A-90",   // Alfanumérico substituindo dígito
            "529.982.24X-25",   // Letra no meio do CPF
            "ABC.DEF.GHI-JK",   // Apenas letras
            "!@#.###.###-##",   // Apenas símbolos
            "529.982.247-2A",   // Letra no DV2
            "A29.982.247-25",   // Letra no início
            "529.98B.247-25"    // Letra no meio
        };

        for (String cpf : invalidos) {
            afirmarFalso(DADOSPESSOAIS.validarCpf(cpf), "validarCpf deve retornar false para: " + cpf);
            afirmarRejeicao(() -> new DADOSPESSOAIS("Teste", cpf),
                    IllegalArgumentException.class, "construtor deve lançar IllegalArgumentException para: " + cpf);
        }
    }

    // =========================================================================
    // 2.4 Quebra de atomicidade em falhas cadastrais
    // =========================================================================
    public static void testarQuebraAtomicidadeSobFalha() {
        DADOSPESSOAIS dados = new DADOSPESSOAIS("Carlos Drummond", "52998224725", "11987654321");

        // Caso 1: Chamar setCadastro com nome válido, CPF válido e celular inválido
        // Deve falhar e NÃO MODIFICAR NENHUM CAMPO
        afirmarRejeicao(() -> dados.setCadastro("Clarice Lispector", "00000000191", "celular_invalido"),
                IllegalArgumentException.class, "setCadastro com celular inválido deve falhar");

        afirmarIgual("Carlos Drummond", dados.getNome(), "nome deve ser rigorosamente preservado");
        afirmarIgual("52998224725", dados.getCpf(), "CPF deve ser rigorosamente preservado");
        afirmarIgual("11987654321", dados.getCelular(), "celular deve ser rigorosamente preservado");

        // Caso 2: Chamar setCadastro com nome válido, CPF inválido e celular válido
        afirmarRejeicao(() -> dados.setCadastro("Guimarães Rosa", "00000000000", "21998877665"),
                IllegalArgumentException.class, "setCadastro com CPF inválido deve falhar");

        afirmarIgual("Carlos Drummond", dados.getNome(), "nome inalterado após falha de CPF");
        afirmarIgual("52998224725", dados.getCpf(), "CPF inalterado após falha de CPF");
        afirmarIgual("11987654321", dados.getCelular(), "celular inalterado após falha de CPF");

        // Caso 3: Chamar alterarDados(nome, celular) com celular inválido
        afirmarRejeicao(() -> dados.alterarDados("Novo Nome", "999"),
                IllegalArgumentException.class, "alterarDados com celular curto deve falhar");

        afirmarIgual("Carlos Drummond", dados.getNome(), "nome inalterado após falha de alterarDados");
        afirmarIgual("52998224725", dados.getCpf(), "CPF inalterado após falha de alterarDados");
        afirmarIgual("11987654321", dados.getCelular(), "celular inalterado após falha de alterarDados");
    }

    // =========================================================================
    // 2.5 Mascaramento estrito em toString (vazamento zero do documento)
    // =========================================================================
    public static void testarMascaramentoEstritoToStringSemVazamento() {
        String[][] casos = new String[][] {
            {"52998224725", "25"},
            {"00000000191", "91"},
            {"11144477735", "35"},
            {"12345678909", "09"}
        };

        for (String[] caso : casos) {
            String cpfCompleto = caso[0];
            String dvFinal = caso[1];

            DADOSPESSOAIS d = new DADOSPESSOAIS("Titular Protegido", cpfCompleto, "11999998888");
            String representacao = d.toString();

            // Comprova formato mascarado estrito
            String mascaraEsperada = "***.***.***-" + dvFinal;
            afirmarVerdadeiro(representacao.contains("cpf='" + mascaraEsperada + "'"),
                    "toString deve conter máscara '" + mascaraEsperada + "'");

            // Comprova que o CPF completo NUNCA vaza
            afirmarFalso(representacao.contains(cpfCompleto),
                    "CPF completo em dígitos não pode vazar no toString()");

            String cpfFormatado = DADOSPESSOAIS.formatarCpf(cpfCompleto);
            afirmarFalso(representacao.contains(cpfFormatado),
                    "CPF formatado não pode vazar no toString()");

            // Comprova que os 9 primeiros dígitos não vazam em bloco
            afirmarFalso(representacao.contains(cpfCompleto.substring(0, 9)),
                    "primeiros 9 dígitos não podem vazar no toString()");
        }
    }

    // =========================================================================
    // 2.6 Subclasse hostil para teste de escape virtual em DADOSPESSOAIS
    // =========================================================================
    private static class SubclasseHostilDadosPessoais extends DADOSPESSOAIS {
        private String campoSubclasse = "SUBCLASSE_OK";
        private boolean escapeVirtualOcorreu = false;

        public SubclasseHostilDadosPessoais(String nome, String cpf, String celular) {
            super(nome, cpf, celular);
        }

        public SubclasseHostilDadosPessoais(String nome, String cpf) {
            super(nome, cpf);
        }

        @Override
        public void setNome(String nome) {
            if (this.campoSubclasse == null) {
                this.escapeVirtualOcorreu = true;
            }
            super.setNome(nome);
        }

        @Override
        public void setCpf(String cpf) {
            if (this.campoSubclasse == null) {
                this.escapeVirtualOcorreu = true;
            }
            super.setCpf(cpf);
        }

        @Override
        public void setCelular(String celular) {
            if (this.campoSubclasse == null) {
                this.escapeVirtualOcorreu = true;
            }
            super.setCelular(celular);
        }

        @Override
        public void setCadastro(String nome, String cpf, String celular) {
            if (this.campoSubclasse == null) {
                this.escapeVirtualOcorreu = true;
            }
            super.setCadastro(nome, cpf, celular);
        }

        @Override
        public void alterarDados(String nome, String cpf, String celular) {
            if (this.campoSubclasse == null) {
                this.escapeVirtualOcorreu = true;
            }
            super.alterarDados(nome, cpf, celular);
        }

        public boolean houveEscapeVirtual() {
            return escapeVirtualOcorreu;
        }
    }

    public static void testarSubclasseHostilAusenciaEscapeVirtualDadosPessoais() {
        SubclasseHostilDadosPessoais h1 = new SubclasseHostilDadosPessoais(
                "Teste Hostil 1", "52998224725", "11987654321"
        );
        afirmarFalso(h1.houveEscapeVirtual(),
                "construtor(3) de DADOSPESSOAIS não deve despachar métodos virtuais");

        SubclasseHostilDadosPessoais h2 = new SubclasseHostilDadosPessoais(
                "Teste Hostil 2", "52998224725"
        );
        afirmarFalso(h2.houveEscapeVirtual(),
                "construtor(2) de DADOSPESSOAIS não deve despachar métodos virtuais");
    }

    public static void testarComportamentoSanitizacaoLeniente() {
        // Observação empírica: DADOSPESSOAIS utiliza apenasDigitos (replaceAll("\\D", ""))
        // Quando uma string contém exatamente 11 dígitos válidos acompanhados de caracteres textuais:
        // ex: "CPF: 529.982.247-25 (titular)"
        // A sanitização extrai "52998224725" e normaliza com sucesso.
        DADOSPESSOAIS comRotulo = new DADOSPESSOAIS("Titular", "CPF: 529.982.247-25 (titular)");
        afirmarIgual("52998224725", comRotulo.getCpf(), "sanitização extrai 11 dígitos limpos");
        afirmarIgual("529.982.247-25", comRotulo.getCpfFormatado(), "formatação oficial padronizada");

        // Já quando o texto contém outros dígitos numéricos espúrios:
        // ex: "529.982.247-25 (ano 2026)" -> total 15 dígitos -> REJEITA
        afirmarFalso(DADOSPESSOAIS.validarCpf("529.982.247-25 (ano 2026)"),
                "texto com dígitos adicionais excede 11 e deve ser rejeitado");
    }
}

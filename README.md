# SGI — Sistema de Gestão de Investimentos

Aplicação de terminal em **Java 21 SE puro** (zero dependências externas ou gerenciadores de build) para cadastrar e operar clientes, aplicações financeiras, lançamentos, extratos, saldos e cotações de mercado (`SELICRA`, `CDIR`, `USD` e `EUR`).

## Requisitos

- JDK 21 ou superior (`java` e `javac` disponíveis no `PATH`)

## Compilar e executar

```sh
rm -rf compilado && mkdir -p compilado
javac -encoding UTF-8 -d compilado $(find src -name '*.java')
java -cp compilado Main
```

### Banco local (`data/sgi.db`)
- O arquivo `data/sgi.db` é gerado em **tempo de execução** no formato TSV v3 (ignorado no controle de versão pelo `.gitignore`).
- Ao iniciar, `Main` carrega automaticamente `data/sgi.db` (quando existir) e salva as alterações ao sair pela opção `0`.
- Caso o arquivo esteja em formato antigo, ele é migrado automaticamente para TSV v3, preservando uma cópia byte a byte em `data/sgi.db.antes-da-migracao.bak`.
- Se ocorrer falha ao abrir o arquivo padrão na inicialização, o programa inicia vazio com o salvamento automático desativado para proteger os dados originais em disco.

## Servidor de cotações (Item 9 — TCP multithread + API PTAX do Banco Central)

Em outro terminal, após compilar `src/`:

```sh
java -cp compilado mercado.servidor.PrincipalServidorCotacoes 5050 10.50 10.40
```

- **Argumentos:** `[porta] SELICRA CDIR` (porta padrão `5050`; `SELICRA` e `CDIR` em **% ao ano**).
- **Câmbio oficial (`USD` e `EUR`):** consultado na API PTAX OData do Banco Central do Brasil (`ProvedorCotacoesBancoCentral`), usando a observação mais recente dos últimos 10 dias (boletim `Fechamento` no caso do Euro).
- **Protocolo ASCII:** o cliente envia `GET_QUOTES\n` e recebe `OK|SELICRA=...|CDIR=...|USD=...|EUR=...\n` (ou `ERR|INVALID_REQUEST\n`).
- **Uso no CRUD (`Main`):** após consultar o servidor na opção **19**, as cotações permanecem ativas no `SGI`:
  - `SELICRA` e `CDIR` podem ser selecionadas ao cadastrar/alterar aplicações (`taxaMensal = taxaAnual / 1200`);
  - `USD` e `EUR` podem ser usados ao informar aportes, depósitos ou retiradas (convertidos para `R$`) e exibidos como equivalência cambial nas consultas de saldo.

## Testes autônomos

```sh
./executar_testes.sh
```

Compila `src/` e `test/` do zero em `compilado/` e executa as **13 suítes** em `test.ExecutorTestes` com `-ea` (Java SE puro via `test.util.Afirmacao`, sem JUnit).

## Organização do projeto ("o que pertence ao quê")

Cada pasta principal segue uma estrutura uniforme: **`abstracoes/`** para classes abstratas, **`util/`** para utilitários estáticos sem estado, e **subpastas por assunto comum** reunindo entidades, regras e enumerações correlatas (tanto em `src/` quanto em `test/`):

```text
National-Economic/
├── executar_testes.sh                                  # Compilação limpa e execução das 13 suítes (-ea)
├── data/
│   └── sgi.db                                          # Estado persistido em runtime (ignorado no git)
├── docs/
│   ├── ARQUITETURA.md                                  # Camadas, protocolos, TSV v3 e padrão Javadoc
│   └── REGRAS_E_TESTES.md                              # Regras financeiras didáticas e guia das 13 suítes
├── src/
│   ├── Main.java                                       # Itens 8 e 9 — Menu CRUD interativo (extends InterfaceTerminal)
│   ├── dominio/                                        # Itens 1 a 6 — Regras de negócio (passíveis de herança)
│   │   ├── abstracoes/                                 # CarteiraFinanceira, RegistroDatado
│   │   ├── aplicacao/                                  # Item 1: AplicacaoFinanceira, ConvencaoContagemDias, TipoRendimento, TipoResgate
│   │   ├── tributacao/                                 # Item 1 (tributos): CalculadoraTributaria, OpcaoIOF, OpcaoIR, RegimeTributario
│   │   ├── operacao/                                   # Itens 2 e 4: OperacaoFinanceira, MultiplasOperacoesFinanceiras, TipoOperacao
│   │   ├── cliente/                                    # Itens 5 e 6: Cliente, DADOSPESSOAIS
│   │   ├── calendario/                                 # Item 3: DiaSemana (Congruência de Zeller 0..6 e dia útil)
│   │   └── util/                                       # ArquivoTSV, Moeda, ValidacaoDominio
│   ├── servico/                                        # Item 7 — Fachada global sincronizada e persistência
│   │   ├── abstracoes/                                 # ArmazenadorBase<T>
│   │   ├── sistema/                                    # SGI, ArmazenadorArquivo
│   │   └── util/                                       # CotacoesUtil (USD<->DOLAR, EUR<->EURO)
│   ├── terminal/                                       # Itens 8 e 9 — Interface CLI
│   │   ├── abstracoes/                                 # InterfaceTerminal (Template Method do menu)
│   │   ├── interacao/                                  # EntradaTerminal, TermosInvestimento
│   │   └── util/                                       # AuxiliarCotacoes, FormatadorTerminal
│   ├── mercado/                                        # Item 9 — Servidor TCP multithread e API BCB PTAX
│   │   ├── abstracoes/                                 # ProvedorCotacoes
│   │   ├── provedores/                                 # ClienteCotacoes (TCP), ProvedorCotacoesBancoCentral (PTAX)
│   │   ├── servidor/                                   # PrincipalServidorCotacoes, ServidorCotacoes
│   │   └── util/                                       # ProtocoloCotacoes (wire ASCII e validação simétrica)
│   └── legado/                                         # Isolado — somente migração retrocompatível e regressão
│       ├── abstracoes/                                 # RegistroDatadoLegado
│       ├── modelo/                                     # Subdividido em aplicacao/, calendario/, cliente/, operacao/, tributacao/
│       ├── servicos/                                   # SistemaInvestimentos, ArmazenadorInvestimentos, MigradorSistemaInvestimentos
│       └── util/                                       # ConversorLegado, Moeda
└── test/                                               # Espelhando src/ por assunto + integracao/
    ├── ExecutorTestes.java                             # Executor unificado (test.ExecutorTestes)
    ├── util/                                           # Afirmacao (Asserções Java SE puro + AcaoComExcecao)
    ├── dominio/                                        # aplicacao/, calendario/, cliente/, operacao/, util/ (7 suítes)
    ├── servico/                                        # TesteSGI (Item 7)
    ├── terminal/                                       # TesteEntradaTerminal (Item 8)
    ├── mercado/                                        # provedores/, servidor/ (Item 9 — 2 suítes)
    └── integracao/                                     # TesteIntegracaoCotacoesSGI, TesteSistemaInvestimentos
```

## Rastreabilidade dos Itens Acadêmicos (1 a 9)

| Item | Escopo Acadêmico | Implementação (`src/`) | Cobertura de Testes (`test/`) |
|---|---|---|---|
| **1** | Aplicação Financeira (19 atributos, taxa 30d, IOF, IR regressivo, rendimentos e resgates) | `dominio/aplicacao/*`, `dominio/tributacao/*`, `dominio/util/Moeda.java` | `test.dominio.aplicacao.TesteAplicacaoFinanceira`, `test.dominio.util.TesteMoeda` |
| **2** | Operação Financeira (depósito/retirada, data civil, valor, descrição) | `dominio/operacao/OperacaoFinanceira.java`, `dominio/operacao/TipoOperacao.java`, `dominio/abstracoes/RegistroDatado.java` | `test.dominio.operacao.TesteOperacaoFinanceira` |
| **3** | Dia da Semana (Congruência de Zeller 0..6, nomes e dia útil) | `dominio/calendario/DiaSemana.java`, `dominio/abstracoes/RegistroDatado.java` | `test.dominio.calendario.TesteDiaSemana` |
| **4** | Múltiplas Operações (1 aplicação + até 100 operações, extratos, TSV) | `dominio/operacao/MultiplasOperacoesFinanceiras.java`, `dominio/abstracoes/CarteiraFinanceira.java`, `dominio/util/ArquivoTSV.java` | `test.dominio.operacao.TesteMultiplasOperacoes` |
| **5** | Dados Pessoais (nome, CPF Receita Federal com máscara LGPD, celular) | `dominio/cliente/DADOSPESSOAIS.java`, `dominio/util/ValidacaoDominio.java` | `test.dominio.cliente.TesteDadosPessoais` |
| **6** | Cliente (titular + até 50 aplicações, extratos, saldo, TSV) | `dominio/cliente/Cliente.java`, `dominio/abstracoes/CarteiraFinanceira.java` | `test.dominio.cliente.TesteCliente` |
| **7** | SGI (até 100 clientes, atualização em lote, saldos, extratos, TSV v3 e migração legado) | `servico/sistema/*`, `servico/abstracoes/ArmazenadorBase.java`, `servico/util/CotacoesUtil.java`, `legado/*` | `test.servico.TesteSGI`, `test.integracao.TesteSistemaInvestimentos` |
| **8** | Interface CRUD interativa no terminal | `Main.java`, `terminal/interacao/*`, `terminal/abstracoes/InterfaceTerminal.java`, `terminal/util/FormatadorTerminal.java` | `test.terminal.TesteEntradaTerminal`, `test.integracao.TesteSistemaInvestimentos` |
| **9** | Cotações (`SELICRA`, `CDIR`, `USD`, `EUR`), servidor TCP multithread e BCB PTAX | `mercado/*`, `terminal/util/AuxiliarCotacoes.java` | `test.mercado.provedores.TesteProvedorCotacoesBancoCentral`, `test.mercado.servidor.TesteServidorCotacoes`, `test.integracao.TesteIntegracaoCotacoesSGI` |

## Documentação complementar
- [Arquitetura, Protocolos e Padrão de Código](file:///home/alekkzsx/Documents/Github/National-Economic/docs/ARQUITETURA.md)
- [Regras Financeiras e Guia das 13 Suítes de Teste](file:///home/alekkzsx/Documents/Github/National-Economic/docs/REGRAS_E_TESTES.md)

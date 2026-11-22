# SGI — Sistema de Gestão de Investimentos

Aplicação de terminal em Java 21 puro (sem bibliotecas externas nem gerenciador de build)
para gerenciar clientes, aplicações financeiras, operações, extratos, saldos e cotações
de mercado (`SELICRA`, `CDIR`, `USD` e `EUR`).

## Requisitos

- JDK 21 ou superior (`java` e `javac` no `PATH`)

## Compilar e executar

```sh
rm -rf compilado && mkdir -p compilado
javac -encoding UTF-8 -d compilado $(find src -name '*.java')
java -cp compilado Main
```

O sistema carrega automaticamente `data/sgi.db` (quando existir) e salva ao sair.
Arquivos do formato anterior são migrados automaticamente para o TSV v3 do SGI,
preservando um backup byte a byte em `data/sgi.db.antes-da-migracao.bak`.

## Servidor de cotações (Item 9 — API PTAX do Banco Central + TCP multithread)

Em outro terminal:

```sh
java -cp compilado mercado.PrincipalServidorCotacoes 5050 10.50 10.40
```

- **Argumentos:** `[porta] SELICRA CDIR` (porta padrão `5050`; `SELICRA` e `CDIR` em **% ao ano**).
- **Câmbio real (`USD` e `EUR`):** obtido automaticamente na API oficial PTAX do Banco Central do Brasil
  (`ProvedorCotacoesBancoCentral`), usando a observação mais recente dos últimos 10 dias em fins de semana/feriados.
- **Protocolo TCP:** o cliente envia `GET_QUOTES` e recebe `OK|SELICRA=...|CDIR=...|USD=...|EUR=...`.
- **Uso no CRUD (`Main`):** após consultar o servidor na opção **19**, as cotações ficam ativas no `SGI`:
  - `SELICRA` e `CDIR` podem ser escolhidas ao cadastrar/alterar aplicações (`taxaMensal = taxaAnual / 1200`);
  - `USD` e `EUR` podem ser usados ao informar valores de aportes, depósitos ou retiradas (convertidos para `R$`)
    e exibidos como equivalência cambial nas consultas de saldo.

## Testes autônomos

```sh
./executar_testes.sh
```

Compila `src/` e `test/` do zero em `compilado/` e executa todas as **13 suítes** em `ExecutorTestes`
(Java SE puro com `test.Afirmacao`, sem JUnit).

## Organização do projeto ("o que pertence ao quê")

Cada pasta principal segue a mesma estrutura simétrica: classes concretas na raiz da pasta,
`abstracoes/` com as classes abstratas da camada e `util/` com os códigos reutilizáveis:

```text
src/
├── Main.java                                # Itens 8 e 9 — Menu CRUD interativo (extends InterfaceTerminal)
├── dominio/                                 # Itens 1 a 6 — Regras de negócio (passíveis de herança)
│   ├── AplicacaoFinanceira.java             # Item 1 — 19 atributos, taxas 30d, juros e resgates
│   ├── CalculadoraTributaria.java           # Item 1 (apoio) — IOF e IR regressivo
│   ├── OperacaoFinanceira.java              # Item 2 — Lançamento de depósito/retirada (extends RegistroDatado)
│   ├── DiaSemana.java                       # Item 3 — Algoritmo de Zeller (0..6) e dia útil (extends RegistroDatado)
│   ├── MultiplasOperacoesFinanceiras.java   # Item 4 — 1 Aplicação + 100 Operações (extends CarteiraFinanceira)
│   ├── DADOSPESSOAIS.java                   # Item 5 — Nome, CPF (Receita Federal/LGPD) e celular
│   ├── Cliente.java                         # Item 6 — Titular + 50 Aplicações (extends CarteiraFinanceira)
│   ├── Moeda.java                           # Apoio — Parsing BR/US, arredondamento e formatação
│   ├── [7 Enums de domínio]                 # TipoOperacao, TipoRendimento, TipoResgate, OpcaoIOF,
│   │                                        # OpcaoIR, ConvencaoContagemDias, RegimeTributario
│   ├── abstracoes/                          # Classes abstratas do domínio
│   │   ├── RegistroDatado.java              # Base abstrata de data civil (DiaSemana, OperacaoFinanceira)
│   │   └── CarteiraFinanceira.java          # Base abstrata de gestão financeira (MOF, Cliente, SGI)
│   └── util/                                # Códigos reutilizáveis do domínio
│       ├── ArquivoTSV.java                  # Escrita atômica, fsync, POSIX 0600 e codecs TSV v3
│       └── ValidacaoDominio.java            # Validações comuns (texto, data civil, CPF)
├── servico/                                 # Item 7 — Fachada global do sistema
│   ├── SGI.java                             # Item 7 — Até 100 Clientes (extends CarteiraFinanceira)
│   ├── ArmazenadorArquivo.java              # Persistência global TSV v3 (extends ArmazenadorBase<SGI>)
│   ├── abstracoes/                          # Classes abstratas de serviço
│   │   └── ArmazenadorBase.java             # Base abstrata genérica de persistência em arquivo
│   └── util/                                # Códigos reutilizáveis de serviço
│       └── CotacoesUtil.java                # Normalização USD<->DOLAR, EUR<->EURO e consulta
├── terminal/                                # Itens 8 e 9 — Interação de console
│   ├── EntradaTerminal.java                 # Leitura validada com padrões via Enter
│   ├── TermosInvestimento.java              # Configuração personalizada usando enums de dominio/
│   ├── abstracoes/                          # Classes abstratas de terminal
│   │   └── InterfaceTerminal.java           # Template Method do laço interativo de menu (Main)
│   └── util/                                # Códigos reutilizáveis do terminal
│       ├── FormatadorTerminal.java          # Formatação de moeda, taxas, câmbio e máscara CPF
│       └── AuxiliarCotacoes.java            # Conversão SELICRA/CDIR e BRL<->USD/EUR no CRUD
├── mercado/                                 # Item 9 — Cliente/Servidor TCP + BCB PTAX
│   ├── PrincipalServidorCotacoes.java       # Inicialização do servidor ([porta] SELICRA CDIR)
│   ├── ServidorCotacoes.java                # Servidor TCP multithread (ThreadPoolExecutor)
│   ├── ClienteCotacoes.java                 # Cliente TCP síncrono (extends ProvedorCotacoes)
│   ├── ProvedorCotacoesBancoCentral.java    # Consulta PTAX oficial do BCB (extends ProvedorCotacoes)
│   ├── abstracoes/                          # Classes abstratas de mercado
│   │   └── ProvedorCotacoes.java            # Base abstrata de origens de cotações (TCP e BCB PTAX)
│   └── util/                                # Códigos reutilizáveis de mercado/rede
│       └── ProtocoloCotacoes.java           # Protocolo ASCII, validação simétrica e fechamento seguro
└── legado/                                  # Isolado: compatibilidade e migração de arquivos antigos
    ├── modelo/                              # Modelo legado mantido para leitura de arquivos antigos
    ├── servicos/                            # SistemaInvestimentos, ArmazenadorInvestimentos e Migrador
    ├── abstracoes/                          # Classes abstratas do legado
    │   └── RegistroDatadoLegado.java        # Base abstrata de data civil legada
    └── util/                                # Códigos reutilizáveis de migração
        └── ConversorLegado.java             # Conversão legado.modelo -> dominio

test/
├── ExecutorTestes.java                      # Executor das 13 suítes autônomas
├── Afirmacao.java                           # Utilitário de asserções em Java SE puro
├── dominio/                                 # Testes dos Itens 1 a 6 e Moeda
├── servico/                                 # Testes do Item 7 (SGI e ArmazenadorArquivo)
├── mercado/                                 # Testes do Item 9 (PTAX, protocolo e servidor TCP)
└── integracao/                              # Testes ponta a ponta (terminal, legado/migração e cotações)
```

Valores monetários usam `BigDecimal`; datas usam `java.time`. Atualizações financeiras operam
somente em dias úteis (segunda a sexta), com limites de até **100 clientes**, **50 aplicações por cliente**
e **100 operações por aplicação**.

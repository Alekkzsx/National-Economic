# Regras Financeiras e Guia das Suítes de Teste

Este guia resume as regras de negócio implementadas nas camadas `dominio/`, `servico/`, `terminal/` e `mercado/`, além do mapa completo das **13 suítes de testes autônomos**.

---

## 1. Resumo Didático das Regras de Negócio

### 1.1 Aplicação Financeira e Evolução Temporal (Item 1)
- **Dia zero:** a data de cadastro (`dataAplicacao`) não gera rendimento. A apuração em `atualizarSaldo(data)` começa em `dataAplicacao.plusDays(1)`.
- **Sincronização de taxas (mês comercial de 30 dias):**
  - Ao definir `taxaMensal`: `taxaDiaria = taxaMensal / 30` (precisão `DECIMAL128`).
  - Ao definir `taxaDiaria`: `taxaMensal = taxaDiaria * 30`.
  - Na integração com cotações anuais (`SELICRA` / `CDIR` em `% a.a.`): `taxaMensal = taxaAnual / 1200`.
- **Dias corridos vs. dias úteis efetivos:**
  - `diasCorridos` avança em todos os dias civis (`incrementarDia()`).
  - `diasEfetivosCalculo` avança apenas de **segunda a sexta-feira** (`incrementarDiaAplicação()`), sendo somente estes os dias que apuram rendimento diário enquanto `tempoFinalVencimento == 0` ou `diasCorridos <= tempoFinalVencimento`.
- **Capitalização por `TipoRendimento`:**
  - `0` (Diário = 1d), `1` (Mensal = 30d), `2` (Trimestral = 90d), `3` (Semestral = 180d), `4` (Anual = 365d), `5` (No prazo: credita somente quando `diasCorridos >= tempoFinalVencimento` com vencimento > 0).
  - Rendimentos aguardam em `rendimentosPendentes` até completar o ciclo e então são incorporados ao `saldo` (`rendimentosCreditados`).

### 1.2 Tributação: IOF e Imposto de Renda Regressivo (Item 1)
Ordem oficial de cálculo em cada recálculo: `Rendimento Bruto -> IOF -> IR (sobre Bruto - IOF) -> Rendimento Líquido`.

- **IOF (`OpcaoIOF`):**
  - `0` — Isento (`0%`);
  - `1` — `3,5%` até 29 dias corridos (`diasCorridos < 30`), isento a partir do 30º dia;
  - `2` — `3,5%` sempre incidente sobre o rendimento bruto.
- **IR (`OpcaoIR`):** incide sobre `max(0, rendimentoBruto - iof)`:
  - `0` — Isento;
  - `1` — Tabela Regressiva:
    - `0 a 180 dias`: **22,50%**
    - `181 a 360 dias`: **20,00%**
    - `361 a 720 dias`: **17,50%**
    - `acima de 720 dias`: **15,00%**

### 1.3 Calendário e Dia da Semana via Congruência de Zeller (Item 3)
- Janeiro e fevereiro são tratados como meses 13 e 14 do ano anterior (`ano - 1`).
- Mapeamento fixo do trabalho: `0 = Sábado`, `1 = Domingo`, `2 = Segunda-feira`, `3 = Terça-feira`, `4 = Quarta-feira`, `5 = Quinta-feira`, `6 = Sexta-feira`.
- Apenas códigos `2 a 6` são dias úteis (`ehDiaUtil()`), bloqueando atualizações financeiras em sábados e domingos em `MultiplasOperacoesFinanceiras`, `Cliente` e `SGI`.

### 1.4 Limites Regulatórios, Extrato e Validações (Itens 4, 5, 6 e 7)
- **Limites máximos:** `100` clientes por `SGI`, `50` aplicações por `Cliente` e `100` operações por aplicação (`MultiplasOperacoesFinanceiras`), com unicidade global de CPF e `numeroAplicacao`.
- **Extrato padrão de 30 dias:** janela inclusiva `[fim.minusDays(29), fim]` ordenada cronologicamente.
- **Resgates:** bloqueados se `dias < tempoMinimoPermanencia` ou fora da periodicidade de `TipoResgate`; consolida `rendimentosPendentes` antes de deduzir do `saldo`.
- **Alteração/Exclusão de operação:** permitida somente antes de qualquer apuração de rendimento (`diasEfetivosCalculo == 0 && rendimentoAtualBruto == 0`), com simulação prévia para rollback seguro caso viole saldo ou carência.
- **CPF (`DADOSPESSOAIS` / `ValidacaoDominio`):** exige 11 dígitos, rejeita dígitos repetidos e valida os dois DVs em módulo 11 da Receita Federal; `toString()` e o terminal mascaram os 9 primeiros dígitos (`***.***.***-DD`).

---

## 2. Guia das 13 Suítes de Teste (`test/`)

Execute tudo de uma vez com:

```sh
./executar_testes.sh
```

| Camada | Classe de Teste | Escopo Verificado |
|---|---|---|
| **Domínio — Utilitários** | `test.dominio.util.TesteMoeda` | Parsing BR/US, desambiguação `"1.500"` vs `"0.125"`, arredondamento `HALF_UP`/`HALF_EVEN` |
| **Domínio — Calendário** | `test.dominio.calendario.TesteDiaSemana` | Algoritmo de Zeller (`0..6`), anos bissextos/séculos, `ehDiaUtil`, `RegistroDatado`, herança segura |
| **Domínio — Operação** | `test.dominio.operacao.TesteOperacaoFinanceira` | Depósito/retirada, data civil, arredondamento, atomicidade em `setCadastro`, `RegistroDatado` |
| **Domínio — Aplicação** | `test.dominio.aplicacao.TesteAplicacaoFinanceira` | 19 atributos, taxas 30d, IOF 30d, IR 4 faixas, `atualizarSaldo` em dia zero/fds/vencimento, resgates |
| **Domínio — Operação** | `test.dominio.operacao.TesteMultiplasOperacoes` | Limite 100 ops, carência/frequência de resgate, extrato 30d inclusivo, TSV `SGI-MOF` v1/v2, `CarteiraFinanceira` |
| **Domínio — Cliente** | `test.dominio.cliente.TesteDadosPessoais` | Validação CPF Receita Federal, rejeição de dígitos repetidos, celular, máscara LGPD e aliases |
| **Domínio — Cliente** | `test.dominio.cliente.TesteCliente` | Limite 50 aplicações, unicidade, atualização em dias úteis, saldo/extrato consolidado, TSV v3 |
| **Serviço** | `test.servico.TesteSGI` | Limite 100 clientes, CRUD completo, 9 sobrecargas de `getExtrato`, `synchronized`, POSIX 0600, TSV v3, cotações |
| **Mercado — Provedores** | `test.mercado.provedores.TesteProvedorCotacoesBancoCentral` | Parsing JSON OData PTAX (USD e EUR `Fechamento`), rejeição de payload vazio e entrega via TCP |
| **Mercado — Servidor** | `test.mercado.servidor.TesteServidorCotacoes` | Protocolo ASCII (`GET_QUOTES`, `ERR|INVALID_REQUEST`), limites de byte/CR/UTF-8, concorrência 8 threads, polimorfismo `ProvedorCotacoes` |
| **Terminal** | `test.terminal.TesteEntradaTerminal` | Leituras validadas com padrões via Enter, `FormatadorTerminal` LGPD, `TermosInvestimento`, `AuxiliarCotacoes` e `InterfaceTerminal` |
| **Integração** | `test.integracao.TesteIntegracaoCotacoesSGI` | Ponta a ponta: `ServidorCotacoes -> ClienteCotacoes -> SGI -> AuxiliarCotacoes -> TSV v3` e resiliência a falha de rede |
| **Integração** | `test.integracao.TesteSistemaInvestimentos` | Regressão da camada `legado/`, migração automática para TSV v3 com backup `.antes-da-migracao.bak` e paridade CRUD do `SGI` |

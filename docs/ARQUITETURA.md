# Arquitetura do Sistema de Gestão de Investimentos (SGI)

Este documento descreve a organização por camadas e subpacotes de assunto comum do projeto, os contratos entre pacotes, os formatos de comunicação/persistência e o padrão de documentação em português (`pt-BR`).

---

## 1. Camadas e Subdivisão por Assuntos Comuns

Todas as pastas principais sob `src/` seguem a mesma convenção em Java 21 SE puro:

- **`abstracoes/`**: classes abstratas compartilhadas por herança (Template Method, hooks protegidos e contratos polimórficos).
- **`util/`**: classes utilitárias finais com métodos estáticos sem estado (construtor privado).
- **Subpastas por assunto comum**: agrupam entidades, enumerações e serviços correlatos evitando tanto acúmulo de muitos arquivos em uma só pasta quanto pastinhas isoladas sem coesão:
  - `dominio/`: `aplicacao/`, `tributacao/`, `operacao/`, `cliente/`, `calendario/`, `abstracoes/`, `util/`
  - `servico/`: `sistema/` (`SGI`, `ArmazenadorArquivo`), `abstracoes/`, `util/`
  - `terminal/`: `interacao/` (`EntradaTerminal`, `TermosInvestimento`), `abstracoes/`, `util/`
  - `mercado/`: `provedores/` (`ClienteCotacoes`, `ProvedorCotacoesBancoCentral`), `servidor/` (`PrincipalServidorCotacoes`, `ServidorCotacoes`), `abstracoes/`, `util/`
  - `legado/`: `modelo/{aplicacao,tributacao,operacao,cliente,calendario}/`, `servicos/`, `abstracoes/`, `util/`

### Fluxo de dependências

```text
Main (default package)
 ├──> terminal/       (interacao, util, abstracoes)
 ├──> mercado/        (provedores, servidor, util, abstracoes)
 ├──> servico/        (sistema, util, abstracoes)
 ├──> legado/         (exclusivo na inicialização/carga via MigradorSistemaInvestimentos)
 └──> dominio/        (aplicacao, tributacao, operacao, cliente, calendario, util, abstracoes)
```

> **Invariante de herança segura:** nenhuma entidade acadêmica (`AplicacaoFinanceira`, `OperacaoFinanceira`, `DiaSemana`, `MultiplasOperacoesFinanceiras`, `DADOSPESSOAIS`, `Cliente`, `SGI`, `ArmazenadorArquivo`) é `final`, e seus construtores nunca invocam métodos públicos ou protegidos sobrescrevíveis. Campos derivados são inicializados por helpers `private` ou `protected final` (como `RegistroDatado.aplicarDataInterno`).

---

## 2. Protocolo TCP de Cotações (`mercado/`)

O módulo `mercado` implementa o Item 9 usando sockets TCP padrão do JDK:

- **Servidor (`mercado.servidor.ServidorCotacoes`)**:
  - Thread aceitadora dedicada (`mercado-accept`) aguardando em `ServerSocket`.
  - Pool fixo (`ThreadPoolExecutor`, 2 a 16 threads `mercado-cliente`) com fila limitada (`ArrayBlockingQueue` de capacidade 64) e `AbortPolicy`.
  - Timeout de leitura de `5000 ms` por socket cliente e controle concorrente de encerramento (`volatile boolean encerrado` + conjunto `socketsClientes`) garantindo fechamento idempotente em `close()`.
- **Fio ASCII (`mercado.util.ProtocoloCotacoes`)**:
  - **Requisição:** `GET_QUOTES\n` (US-ASCII estrito, máximo `512` bytes por linha, rejeição de `\r` e caracteres não-ASCII).
  - **Resposta de sucesso:** `OK|SELICRA=<taxa>|CDIR=<taxa>|USD=<cambio>|EUR=<cambio>\n`.
  - **Resposta de erro:** `ERR|INVALID_REQUEST\n`.
  - **Validação simétrica** (servidor e cliente): `SELICRA` e `CDIR` `>= 0`; `USD` e `EUR` `> 0`.
- **Cotações PTAX do Banco Central (`mercado.provedores.ProvedorCotacoesBancoCentral`)**:
  - Consulta a API OData PTAX oficial do BCB usando uma **janela móvel de 10 dias civis** para tolerar fins de semana e feriados.
  - Para `EUR`, considera apenas registros com `tipoBoletim == "Fechamento"`.
  - Impõe teto de 1 MB de resposta JSON e interpreta valores via expressões regulares (zero dependências externas).

---

## 3. Persistência Atômica e Formato TSV v3 (`ArquivoTSV` / `ArmazenadorArquivo`)

Toda gravação crítica de clientes e aplicações usa escrita atômica com proteção LGPD:

1. Cria arquivo temporário no mesmo diretório do destino (para preservar o mesmo filesystem no `ATOMIC_MOVE`).
2. Escreve o conteúdo em `UTF-8`, executa `flush()` e `FileDescriptor.sync()` (`fsync`).
3. Aplica permissões POSIX `0600` (`rw-------`) antes e depois da substituição atômica.

### Estrutura de registros do arquivo global (`NATIONAL-ECONOMIC\t3`)

- **Cabeçalho:** `NATIONAL-ECONOMIC\t3` (também lê versões anteriores `1` e `2` com valores padrão documentados).
- **Cliente (`C` — 6 colunas):**
  `C | cpf | nome | celular | agencia | conta`
- **Aplicação (`I` — 26 colunas no v3):**
  `I | cpf | numero | nome | valorAplicado | taxaMensal | dataApp | saldo | valorAtualBruto | dataUltimaAtualizacao | diasCorridos | diasEfetivos | qtdOps | convencao | regime | tipoRend | vencimento | permanencia | tipoResgate | iof | tipoIR | valorResgate | valorAtualLiquido | rendAtualBruto | rendAtualLiquido | impostoDevido`
- **Operação (`O` — 7 colunas):**
  `O | cpf | numeroApp | data | valor | descricao | tipoOperacao`

Na leitura (`ArmazenadorArquivo`), o `ContextoLeitura` valida obrigatoriamente a ordem hierárquica (`C -> I -> O`), compatibilidade de CPF/número de aplicação, datas não anteriores à contratação e **consistência entre `qtdOps` declarado em `I` e o total de linhas `O` restauradas**.

### Camada `legado/`
O pacote `src/legado/` é mantido **exclusivamente** para:
1. Carregar e migrar arquivos antigos automaticamente via `legado.servicos.MigradorSistemaInvestimentos.carregarComMigracao(...)` (gerando backup `.antes-da-migracao.bak`);
2. Garantir cobertura de regressão histórica em `test.integracao.TesteSistemaInvestimentos`.

---

## 4. Padrão de Javadoc e Comentários (`pt-BR`)

- **Cabeçalho de classe/enum/interface:** responsabilidade da classe, vínculo com o Item (1 a 9) e invariantes estruturais (ex.: herança segura, `synchronized`, protocolo ASCII, formato TSV).
- **Métodos públicos/protegidos:** Javadoc conciso em pt-BR com `@param`, `@return` e `@throws` nos pontos de regra de negócio, cálculo financeiro, I/O ou validação; getters/setters triviais em linha única.
- **Comentários inline (`//`):** apenas para explicar **o porquê** de regras não óbvias (Zeller 0..6, dia zero, carência de IOF, janela inclusiva de 30 dias, módulo 11 do CPF, heurística `Moeda`, janela de 10 dias PTAX, escrita atômica).
- **Aliases de compatibilidade:** agrupados no rodapé da classe sob `// Aliases de compatibilidade (mantidos para os testes)`.

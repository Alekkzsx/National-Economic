# SGI — Sistema de Gestão de Investimentos

Aplicação de terminal em Java 21 para gerenciar clientes, aplicações financeiras,
operações, extratos e saldos. Inclui um servidor TCP de cotações para SELICRA,
CDIR, dólar e euro.

## Requisitos

- JDK 21 ou superior (`java` e `javac` no `PATH`)
- Terminal compatível com comandos Unix para os exemplos abaixo

O código não depende de bibliotecas externas nem de um gerenciador de build. O
JDK é suficiente para compilar e executar a aplicação.

## Compilar

Na raiz do repositório:

```sh
mkdir -p compilado
javac -encoding UTF-8 -d compilado $(find src -name '*.java')
```

## Executar o sistema

```sh
java -cp compilado Principal
```

O sistema carrega os dados de `data/sgi.db`, quando o arquivo existe, e salva
as alterações ao sair. O arquivo de dados é texto UTF-8; não o edite enquanto
o programa estiver em execução.

## Servidor de cotações

Em outro terminal, execute:

```sh
java -cp compilado mercado.PrincipalServidorCotacoes 5050 10.50 1.25
```

Argumentos: `porta SELICRA CDIR`; a porta pode ser omitida, mantendo o padrão
5050. As taxas SELICRA e CDIR são configuradas no início do servidor. As
cotações de venda de dólar e euro são consultadas no serviço PTAX do Banco
Central do Brasil; em fins de semana e feriados, usa-se a observação mais
recente publicada pelo Banco Central. Se o serviço estiver indisponível ou não
retornar uma cotação válida, o servidor informa o erro e não inicia com valores
fictícios. O cliente envia `GET_QUOTES` e recebe uma resposta em uma linha com
`SELICRA`, `CDIR`, `USD` e `EUR`.

O menu permite cadastrar, consultar, alterar e excluir clientes, aplicações e
operações. A exclusão de clientes e aplicações remove também os dados e
lançamentos vinculados; a exclusão de uma operação reverte seu efeito no saldo.
O item “Listar clientes e aplicações” exibe a carteira completa em uma única
consulta.

No cadastro de uma aplicação, escolha os termos padrão ou configure
periodicidade de rendimento, vencimento, permanência mínima, frequência de
resgate e opções de IOF/IR. Valores monetários aceitam ponto ou vírgula como
separador decimal.

## Testes autônomos

Compile e execute a suíte unificada:

```sh
./executar_testes.sh
```

O script compila os arquivos de `src/` e `test/` e executa `ExecutorTestes`.
Os testes autônomos não precisam de JUnit nem de outras bibliotecas externas.

## Organização

```text
src/
├── Principal.java                 # Entrada e menu do sistema
├── terminal/                      # Leitura e formatação do terminal
├── modelo/                        # Modelo usado pela aplicação de terminal
├── mercado/                       # Protocolo TCP, cliente e servidor de cotações
├── servicos/                      # Serviços e persistência da aplicação de terminal
├── dominio/                       # Modelo acadêmico em português
└── servico/                       # Serviços acadêmicos e persistência

test/
├── ExecutorTestes.java             # Executor das suítes autônomas
├── dominio/                        # Testes do modelo acadêmico
├── servico/                        # Testes dos serviços acadêmicos
└── integracao/                     # Testes de integração da aplicação de terminal
```

As pastas `dominio`/`servico` e `modelo`/`servicos` mantêm APIs distintas,
exercitadas por conjuntos de testes próprios. Por isso, elas não são duplicatas
descartáveis. Os nomes de pacotes e classes da aplicação de terminal estão em
português; nomes de protocolos, formatos persistidos e tipos da biblioteca Java
permanecem inalterados para preservar compatibilidade.

Valores monetários e taxas são representados com `BigDecimal`. Datas usam
`java.time`. O sistema limita atualizações financeiras a dias úteis e permite
até 100 clientes e 50 aplicações por cliente.

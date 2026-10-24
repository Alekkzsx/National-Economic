# SGI — Sistema de Gestão de Investimentos

Aplicação de terminal em Java 21 para gerenciar clientes, aplicações financeiras,
operações, extratos e saldos. Inclui também um servidor TCP de cotações para
SELICRA, CDIR, dólar e euro.

## Requisitos

- JDK 21 ou superior (`java` e `javac` no `PATH`)
- Terminal compatível com comandos Unix para os exemplos abaixo

O código principal não depende de bibliotecas externas nem de um gerenciador de
build. O JDK é suficiente para compilar e executar a aplicação.

## Compilar

Na raiz do repositório:

```sh
mkdir -p out
javac -encoding UTF-8 -d out $(find src/main/java -name '*.java')
```

## Executar o sistema

```sh
java -cp out app.Main
```

O sistema carrega os dados de `data/sgi.db`, quando o arquivo existe, e salva
as alterações ao sair. O arquivo de dados é texto UTF-8; não o edite enquanto
o programa estiver em execução.

## Servidor de cotações

Em outro terminal, execute:

```sh
java -cp out market.MarketServerMain 5050 10.50 1.25 5.10 5.55
```

Argumentos: `porta SELICRA CDIR USD EUR`. Sem argumentos, a porta padrão é
5050 e os valores padrão são `0.00`. As cotações são configuradas localmente;
não são cotações financeiras em tempo real. O cliente envia `GET_QUOTES` e
recebe uma resposta em uma linha com `SELICRA`, `CDIR`, `USD` e `EUR`.

No cadastro de uma aplicação, escolha os termos padrão ou configure
periodicidade de rendimento, vencimento, permanência mínima, frequência de
resgate e opções de IOF/IR. Valores monetários usam ponto ou vírgula como
separador decimal.

## Testes autônomos

Os testes executáveis do domínio e do terminal não exigem JUnit:

```sh
javac -encoding UTF-8 -d out $(find src/main/java src/test/java -name '*.java' \
  ! -path 'src/test/java/market/*')
java -ea -cp out InvestmentSystemSelfTest
java -ea -cp out app.ConsoleInputSelfTest
```

Os testes de integração de protocolo em `src/test/java/market` usam JUnit 5;
este repositório não inclui um gerenciador de build nem as dependências de
teste.

## Organização

Cada módulo fica em uma pasta direta, sem a cadeia de diretórios `br/com/...`:

```text
src/main/java/
├── app/       # Menu e interação com o usuário
├── domain/    # Entidades e regras financeiras
├── market/    # Protocolo TCP, cliente e servidor de cotações
└── service/   # Gestão de clientes, aplicações e persistência

src/test/java/
├── InvestmentSystemSelfTest.java
├── app/        # Testes autônomos de interação com o terminal
└── market/    # Testes do protocolo de cotações
```

Valores monetários e taxas são representados com `BigDecimal`. Datas usam
`java.time`. A atualização de aplicações é limitada a dias úteis, e o sistema
aplica limites de 100 clientes e 50 aplicações por cliente.

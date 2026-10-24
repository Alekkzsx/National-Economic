# SGI — Sistema de Gestão de Investimentos

Aplicação de terminal em Java 21 para gerenciar clientes, aplicações financeiras,
operações, extratos e saldos. Inclui também um servidor TCP de cotações para
SELICRA, CDIR, dólar e euro.

## Requisitos

- JDK 21 ou superior (`java` e `javac` no `PATH`)
- Terminal compatível com comandos Unix para os exemplos abaixo

O projeto não depende de bibliotecas externas nem de um gerenciador de build.

## Compilar

Na raiz do repositório:

```sh
mkdir -p out
javac -encoding UTF-8 -d out $(find src/main/java -name '*.java')
```

## Executar o sistema

```sh
java -cp out br.com.nationaleconomic.app.Main
```

O sistema carrega os dados de `data/sgi.db`, quando o arquivo existe, e salva
as alterações ao sair. O arquivo de dados é texto UTF-8; não o edite enquanto
o programa estiver em execução.

## Servidor de cotações

Em outro terminal, execute:

```sh
java -cp out br.com.nationaleconomic.market.MarketServerMain
```

O protocolo TCP e os argumentos/configuração de valores são descritos na
mensagem de inicialização do servidor. As cotações são configuradas localmente;
não são cotações financeiras em tempo real.

## Organização

- `domain`: entidades e regras do negócio financeiro.
- `service`: gestão de clientes/aplicações e persistência.
- `app`: menu e interação de terminal.
- `market`: protocolo TCP, servidor e cliente de cotações.

Valores monetários e taxas são representados com `BigDecimal`. Datas usam
`java.time`. A atualização de aplicações é limitada a dias úteis, e o sistema
aplica limites de 100 clientes e 50 aplicações por cliente.

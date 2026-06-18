# Transaction Authorization API

API de autorização de transações financeiras (crédito/débito) para core banking,
em **Java 21 + Spring Boot 3**, com arquitetura **MVC em camadas**.

O serviço consome eventos de **abertura de conta** de uma fila **AWS SQS**
(`conta-bancaria-criada`) e — nas próximas fases — exporá um endpoint REST para
autorizar transações. **Esta fase (1)** entrega apenas a infraestrutura local e o
wiring do cliente SQS; nenhuma regra de negócio foi implementada ainda.

---

## Stack

- Java 21, Spring Boot 3.3
- PostgreSQL 16 (via Docker)
- AWS SQS via LocalStack (AWS SDK v2)
- Flyway (habilitado; migrations virão nas próximas fases)
- Actuator + Micrometer/Prometheus
- springdoc-openapi (Swagger UI)

## Estrutura de pacotes (MVC)

```
br.com.renan.transactionauthorization
├── config          # configurações (ex.: AwsSqsConfig, SqsProperties)
├── controller      # endpoints REST
├── dto             # objetos de transporte da API
├── entity          # entidades JPA
├── enums           # enumerações de domínio
├── exception       # exceções e handlers
├── mapper          # conversões entity <-> dto
├── repository      # repositórios Spring Data
├── service         # regras de negócio
├── sqs             # consumidor/integração SQS
└── observability   # métricas / health customizados
```

---

## Pré-requisitos

- **JDK 21**
- **Maven 3.9+** (ou use o wrapper, se presente)
- **Docker** + **Docker Compose v2**
- (Opcional) **AWS CLI** para inspecionar a fila SQS

---

## 1. Subir a infraestrutura local (Docker Compose)

O `docker-compose.yml` sobe três serviços:

| Serviço            | Descrição                                                        |
|--------------------|------------------------------------------------------------------|
| `postgres`         | PostgreSQL 16 (`transaction_authorization`, user/pass `app`/`app`) |
| `localstack`       | AWS SQS local (porta `4566`)                                     |
| `message-generator`| Cria a fila `conta-bancaria-criada` e publica **100.000** contas |

```bash
docker compose up
```

Aguarde até ver no terminal:

```
message-generator exited with code 0
```

Isso indica que a fila foi populada com 100.000 contas. O PostgreSQL e o
LocalStack permanecem rodando.

> Para rodar em segundo plano: `docker compose up -d`
> Para derrubar e limpar o volume: `docker compose down -v`

---

## 2. Verificar saúde do LocalStack

```bash
curl -s http://localhost:4566/_localstack/health | jq .
```

O serviço `sqs` deve aparecer como `available`/`running`.

---

## 3. Verificar a fila SQS via AWS CLI

```bash
export AWS_DEFAULT_REGION=sa-east-1
export AWS_ACCESS_KEY_ID=test
export AWS_SECRET_ACCESS_KEY=test

# Listar filas
aws --endpoint-url=http://localhost:4566 sqs list-queues

# Ver atributos (ApproximateNumberOfMessages deve refletir as 100.000 contas)
aws --endpoint-url=http://localhost:4566 sqs get-queue-attributes \
  --queue-url http://localhost:4566/000000000000/conta-bancaria-criada \
  --attribute-names All

# Espiar algumas mensagens
aws --endpoint-url=http://localhost:4566 --region sa-east-1 sqs receive-message \
  --queue-url http://localhost:4566/000000000000/conta-bancaria-criada \
  --max-number-of-messages 10
```

Exemplo de payload de uma mensagem:

```json
{
  "account": {
    "id": "5b19c8b6-0cc4-4c72-a989-0c2ee15fa975",
    "owner": "315e3cfe-f4af-4cd2-b298-a449e614349a",
    "created_at": "1634874339",
    "status": "ENABLED"
  }
}
```

---

## 4. Rodar a aplicação localmente (profile `local`)

Com a infraestrutura no ar:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

Ou empacotando o jar:

```bash
mvn clean package
java -jar target/transaction-authorization.jar --spring.profiles.active=local
```

O profile `local` aponta o datasource para o PostgreSQL do compose e o cliente
SQS para o LocalStack (`http://localhost:4566`).

---

## 5. Swagger e Actuator

Com a aplicação no ar (`http://localhost:8080`):

- **Swagger UI:** http://localhost:8080/swagger-ui.html
- **OpenAPI JSON:** http://localhost:8080/v3/api-docs
- **Health (Actuator):** http://localhost:8080/actuator/health
- **Health customizado:** http://localhost:8080/health
- **Métricas Prometheus:** http://localhost:8080/actuator/prometheus

---

## Modelo de dados

Duas tabelas, criadas via Flyway (`V1` e `V2`):

- **`accounts`** — armazena o **saldo atual** (`balance_amount`) e o **status** da
  conta (`ENABLED`/`DISABLED`/`BLOCKED`), além de owner, moeda e timestamps de
  origem/importação. Cada conta é importada com saldo **zero** e moeda **BRL**.
- **`transactions`** — armazena **cada tentativa de autorização** (o "ledger"):
  tipo (`CREDIT`/`DEBIT`), valor, moeda, status (`SUCCEEDED`/`FAILED`) e, quando
  recusada, o `failure_reason`.

Decisões relevantes:

- O **`transaction id`** é a chave primária da transação e, em fase posterior,
  será usado como **chave de idempotência** do endpoint de autorização.
- Valores monetários usam **`BigDecimal` / `NUMERIC(19,2)`** — aritmética decimal
  exata; `double` nunca é usado para dinheiro.
- A coluna **`version`** da conta existe para **controle de concorrência via
  optimistic locking** em caminhos não-críticos; a autorização crítica (débito)
  usará **lock pessimista** numa fase posterior.
- A transação referencia a conta apenas por `account_id` (sem `@ManyToOne`),
  mantendo o caminho de escrita simples e performático.

### Persistência

A aplicação usa PostgreSQL como banco relacional ACID. A tabela `accounts` mantém o saldo atual da conta e a tabela `transactions` registra cada tentativa de autorização. A busca da conta para autorização usa lock pessimista, garantindo consistência em cenários concorrentes.

> **Testes de integração:** usam **PostgreSQL real via Testcontainers** (nunca H2),
> portanto exigem **Docker em execução**. Rode com `mvn test`. Sem Docker, apenas
> os testes unitários executam; os de integração falham ao não encontrar o daemon.

## Roadmap (próximas fases)

2. Domínio: enums, entidades JPA, mapeamentos
3. Persistência: migrations Flyway, repositórios
4. Consumidor SQS de abertura de contas (idempotente, resiliente)
5. Endpoint `POST /transactions/{transactionId}` e regra de saldo
6. Observabilidade e padrões de resiliência (retry, backoff, circuit breaker)
7. Documentação: ADRs, diagramas (C4 + cloud), pipeline CI/CD, coleção de requisições

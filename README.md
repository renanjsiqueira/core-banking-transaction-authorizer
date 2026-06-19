# Core Banking — Transaction Authorizer

Solução do desafio técnico de **autorização de transações financeiras** para core
banking, em **Java 21 + Spring Boot 3**, organizada como um **monorepo Maven
multi-módulo** com **dois serviços** e um kernel compartilhado.

## Serviços

| Módulo | Tipo | Responsabilidade | Porta |
|---|---|---|---|
| `transaction-authorization-api` | síncrono | `POST /transactions/{transactionId}` — autorização CREDIT/DEBIT. **Owner do schema (Flyway).** | 8080 |
| `account-onboarding-listener` | assíncrono | Consome a fila SQS `conta-bancaria-criada` e importa contas. **Flyway desabilitado.** | 8081 |
| `shared-kernel` | biblioteca | Tipos comuns: enums, convenções de dinheiro. Sem regra de negócio pesada. | — |

Ambos compartilham **um único PostgreSQL** (mesmo bounded context). A decisão
está documentada em [docs/adr/0001-two-services-architecture.md](docs/adr/0001-two-services-architecture.md).

Documentação adicional: [arquitetura](docs/architecture.md) ·
[deploy em cloud](docs/cloud-deployment.md) · [pipeline CI/CD](docs/pipeline.md).

## Estrutura

```
core-banking-transaction-authorizer/
├── pom.xml                       # parent/aggregator
├── docker-compose.yml
├── shared-kernel/
├── transaction-authorization-api/
├── account-onboarding-listener/
└── docs/  (architecture, cloud-deployment, pipeline, adr/)
```

---

## Pré-requisitos

- **JDK 21**, **Maven 3.9+**
- **Docker** + **Docker Compose v2**
- (Opcional) **AWS CLI** para inspecionar a fila SQS

## Build e testes

```bash
mvn clean test
```

> Os testes de integração usam **PostgreSQL real via Testcontainers** (nunca H2)
> e exigem **Docker em execução**. Sem Docker, rode apenas os unitários:
> `mvn clean test -Dtest='!*IntegrationTest' -Dsurefire.failIfNoSpecifiedTests=false`.

---

## Subir tudo com Docker Compose

O `docker-compose.yml` sobe: `postgres`, `localstack` (SQS), `message-generator`
(cria a fila e publica **100.000** contas), `transaction-authorization-api` e
`account-onboarding-listener`.

```bash
docker compose up --build
```

Aguarde `message-generator exited with code 0` (fila populada). A API roda as
migrations no startup; o listener sobe depois da API (via `depends_on` healthy) e
começa a drenar a fila.

- API health: http://localhost:8080/actuator/health
- Listener health: http://localhost:8081/actuator/health
- Swagger (API): http://localhost:8080/swagger-ui.html

> Derrubar e limpar o volume: `docker compose down -v`.

---

## Rodar os serviços localmente (sem Docker para a app)

Suba só a infraestrutura e rode cada serviço com o profile `local`:

```bash
docker compose up -d postgres localstack message-generator

# Terminal 1 — API (porta 8080, roda Flyway)
mvn -pl transaction-authorization-api -am spring-boot:run \
  -Dspring-boot.run.profiles=local

# Terminal 2 — Listener (porta 8081, consome SQS)
mvn -pl account-onboarding-listener -am spring-boot:run \
  -Dspring-boot.run.profiles=local
```

> Inicie a **API antes** do listener: a API cria o schema (Flyway) que o listener
> apenas valida.

### Verificar LocalStack e a fila SQS

```bash
curl -s http://localhost:4566/_localstack/health | jq .

export AWS_DEFAULT_REGION=sa-east-1
export AWS_ACCESS_KEY_ID=test
export AWS_SECRET_ACCESS_KEY=test

aws --endpoint-url=http://localhost:4566 --region sa-east-1 sqs receive-message \
  --queue-url http://localhost:4566/000000000000/conta-bancaria-criada \
  --max-number-of-messages 10
```

---

## API de autorização

`POST /transactions/{transactionId}`

**Request**

```json
{
  "accountId": "5b19c8b6-0cc4-4c72-a989-0c2ee15fa975",
  "type": "CREDIT",
  "amount": { "value": 97.07, "currency": "BRL" }
}
```

**Response** — `200 OK`

```json
{
  "transaction": {
    "id": "8e8ae808-b154-48b5-9f3e-553935cc4543",
    "type": "CREDIT",
    "amount": { "value": 97.07, "currency": "BRL" },
    "status": "SUCCEEDED",
    "timestamp": "2025-07-08T15:57:55-03:00"
  },
  "account": {
    "id": "5b19c8b6-0cc4-4c72-a989-0c2ee15fa975",
    "balance": { "amount": 183.12, "currency": "BRL" }
  }
}
```

| Situação | HTTP |
|---|---|
| `SUCCEEDED` ou `FAILED` por regra de negócio (saldo insuficiente / conta desabilitada) | `200 OK` |
| Validação / `transactionId` inválido / moeda ≠ BRL | `400 Bad Request` |
| Conta não encontrada | `404 Not Found` |
| Conflito de idempotência | `409 Conflict` |
| Erro inesperado | `500 Internal Server Error` |

### Regras de negócio

- Conta inexistente → `404`; conta não-`ENABLED` → `FAILED (ACCOUNT_DISABLED)`, saldo intacto.
- `CREDIT` soma; `DEBIT` subtrai. `DEBIT` que resultaria em saldo negativo → `FAILED (INSUFFICIENT_FUNDS)`, saldo intacto.
- Saldo em `BigDecimal` / `NUMERIC(19,2)`; moeda suportada: `BRL`.

---

## Consistência transacional e idempotência

A autorização roda numa transação ACID no PostgreSQL. A conta é lida com **lock
pessimista** (`PESSIMISTIC_WRITE`) antes de alterar o saldo, serializando
operações concorrentes na mesma conta. O `transactionId` é a **chave de
idempotência**: replay com mesmo payload retorna o resultado anterior (sem
reaplicar saldo); com payload diferente retorna `409 CONFLICT`.

## Importação de contas via SQS

O listener consome `conta-bancaria-criada` com long polling. Contas são
importadas com saldo zero e moeda BRL; o processamento é **idempotente** (não
duplica) e a mensagem só é **deletada após sucesso** (entrega at-least-once).

## Persistência e migrations

O PostgreSQL é o banco relacional ACID compartilhado. As **migrations Flyway
ficam apenas na `transaction-authorization-api`** (owner do schema); o listener
usa `spring.flyway.enabled=false` e apenas valida o mapeamento. Em produção, as
migrations seriam executadas por um **job/step de pipeline antes do deploy** dos
serviços (ver [docs/pipeline.md](docs/pipeline.md)).

## Observabilidade

Ambos expõem Actuator (`/actuator/health`, `/actuator/prometheus`) e métricas
Micrometer. O listener publica `accounts.imported.total`,
`accounts.duplicates.total`, `sqs.account-created.messages.processed.total`,
`sqs.account-created.messages.failed.total`.

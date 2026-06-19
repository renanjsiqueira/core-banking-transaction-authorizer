# Arquitetura

## Visão geral

Dois serviços Spring Boot em um monorepo Maven multi-módulo, compartilhando um
único PostgreSQL e um `core-banking-commons-domain` enxuto de tipos comuns.

```
                 ┌────────────────────────────────────┐
   cliente  ──▶  │ core-banking-transaction-          │ ── POST /transactions/{id}
   (síncrono)    │ authorizer-api :8080               │
                 │  - regras de autorização           │
                 │  - Flyway (owner schema)           │
                 └─────────────────┬──────────────────┘
                                   │ JDBC (lock pessimista na conta)
                                   ▼
                          ┌──────────────────┐
                          │   PostgreSQL     │  accounts, transactions
                          └──────────────────┘
                                   ▲
                                   │ JDBC (insere novas contas)
                 ┌─────────────────┴──────────────────┐
   SQS  ──────▶  │ core-banking-account-created-       │
 conta-bancaria- │ listener :8081                      │
   criada        │  - consumidor long-poll             │
   (assíncrono)  │  - Flyway DESABILITADO              │
                 └────────────────────────────────────┘
```

## Módulos

| Módulo | Responsabilidade | Pontos-chave |
|---|---|---|
| `core-banking-commons-domain` | Enums + convenções de dinheiro | Java puro, sem Spring/JPA |
| `core-banking-transaction-authorizer-api` | Autorização síncrona | REST, JPA, **owner do Flyway**, Actuator, OpenAPI |
| `core-banking-account-created-listener` | Importação assíncrona de contas | SQS (AWS SDK v2), JPA, **Flyway desabilitado**, Actuator |

## Fluxo de autorização (API)

O Redis lock, quando habilitado, é adquirido antes da transação do banco para
evitar segurar conexão JDBC enquanto a requisição aguarda uma conta ocupada. O
processamento de negócio executa em uma transação ACID:

1. Valida moeda (`BRL`) e valor positivo.
2. Adquire locks temporários no Redis, sempre na ordem `transactionId` →
   `accountId`, aguardando até `app.redis-lock.wait-timeout`.
3. Carrega a conta **com lock pessimista de escrita** (`SELECT … FOR UPDATE`).
   - não encontrada → `404`; status diferente de `ENABLED` → `FAILED (ACCOUNT_DISABLED)`.
4. Idempotência: busca a transação pelo `transactionId` (PK).
   - mesmo payload lógico → retorna o resultado salvo (sem reaplicar saldo);
   - payload diferente → `409 CONFLICT`.
5. `CREDIT` soma; `DEBIT` subtrai (recusado como `FAILED (INSUFFICIENT_FUNDS)` se
   resultaria em saldo negativo — saldo intacto).
6. Persiste a transação no ledger e retorna o response do contrato.

A concorrência na mesma conta é serializada pelo lock de linha; contas diferentes
seguem em paralelo.

## Fluxo de contas criadas (Listener)

Um poller `@Scheduled` faz long polling no SQS (lote de 10), parseia cada
mensagem `account-created` e importa a conta (saldo zero em `BRL`). Idempotente
por `accountId`; a mensagem só é deletada **após** o sucesso da importação
(entrega at-least-once + processamento idempotente).

## Propriedade dos dados

A API é dona do schema via Flyway (`accounts`, `transactions`). O listener usa
`spring.flyway.enabled=false` e apenas `valida` (`validate`) o mapeamento JPA
contra o schema existente. Ver [ADR 0001](adr/0001-two-services-architecture.md).

## Dinheiro e consistência

- Dinheiro é `BigDecimal` / `NUMERIC(19,2)` — nunca `double`.
- O banco é a fronteira de consistência (ACID + lock pessimista de linha).
- Racional sobre CAP/ACID na [ADR 0001](adr/0001-two-services-architecture.md).

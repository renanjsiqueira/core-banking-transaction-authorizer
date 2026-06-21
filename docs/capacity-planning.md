# Guia de dimensionamento e capacidade

Este guia orienta como dimensionar a API síncrona de autorização e o listener de
contas criadas em um cenário de alta volumetria.

## Teste de carga local

O script [scripts/run-load-test.sh](../scripts/run-load-test.sh) executa um teste
k6 contra `POST /transactions/{transactionId}` usando contas reais importadas no
PostgreSQL local.

```bash
./scripts/start-local.sh --detached

# Aguarde a API ficar healthy e o listener importar algumas contas.
./scripts/run-load-test.sh --vus 20 --duration 2m
```

O wrapper busca contas `ENABLED` no container `core-banking-postgres`. Também é
possível passar contas manualmente:

```bash
./scripts/run-load-test.sh \
  --account-ids 11111111-1111-4111-8111-111111111111,22222222-2222-4222-8222-222222222222 \
  --vus 50 \
  --duration 5m
```

O resumo do k6 mostra:

- RPS total.
- Latência média, p95 e p99.
- Taxa de falha HTTP.
- Quantidade de lock timeouts (`HTTP 409`).
- Transações recusadas por regra de negócio.

O JSON completo fica em `load-tests/results/transaction-authorization-summary.json`.

## Cenários recomendados

| Cenário | Comando | Objetivo |
|---|---|---|
| Baseline distribuído | `./scripts/run-load-test.sh --vus 20 --duration 2m --hot-account-ratio 0.0` | Medir throughput com baixa contenção por conta |
| Contenção realista | `./scripts/run-load-test.sh --vus 50 --duration 5m --hot-account-ratio 0.2` | Simular parte do tráfego concentrada em poucas contas |
| Conta quente | `./scripts/run-load-test.sh --vus 30 --duration 1m --hot-account-ratio 1.0` | Validar p99 e lock timeout sob serialização forte por `accountId` |
| Débito intenso | `./scripts/run-load-test.sh --vus 30 --duration 2m --credit-ratio 0.3` | Observar recusas por saldo insuficiente e consistência do saldo |

## Pool de conexões

Cada pod consome conexões conforme seu pool Hikari:

```text
conexões_estimadas =
  replicas_api * DB_POOL_MAX_SIZE_API
  + replicas_listener * DB_POOL_MAX_SIZE_LISTENER
  + margem_operacional
```

Exemplo para um patamar de produção:

```text
API:      6 replicas * 40 conexões = 240
Listener: 3 replicas * 20 conexões = 60
Margem operacional:               20
Total estimado:                  320
```

Esse total deve ficar abaixo do limite efetivo do RDS Proxy/Aurora, deixando
folga para migrations, sessões administrativas e failover Multi-AZ. Se o banco
ficar saturado, aumentar réplicas da API piora o problema; nesse caso reduza o
pool por pod, aumente capacidade do Aurora/RDS Proxy ou investigue queries lentas.

## HPA

Escalar apenas por CPU é insuficiente para autorização financeira. Recomenda-se
combinar:

- CPU e memória como sinais básicos.
- RPS por pod para manter distribuição saudável.
- p95/p99 de `POST /transactions/{transactionId}`.
- `transactions_locks_timeouts_total` como sinal de contenção.
- Utilização do pool Hikari.

Para o listener, a escala deve considerar backlog SQS:

- `ApproximateNumberOfMessagesVisible`.
- `ApproximateAgeOfOldestMessage`.
- taxa de processamento `sqs_account_created_messages_processed_total`.

## Contenção por conta

Operações sobre a mesma conta são serializadas por lock Redis-compatible/Valkey e
por lock pessimista no PostgreSQL. Escalar horizontalmente aumenta throughput
quando o tráfego está distribuído entre muitas contas, mas não elimina a fila de
uma conta quente.

Sinais de conta quente:

- p99 sobe, mas CPU não está saturada.
- lock timeout aumenta.
- RPS total não cresce proporcionalmente às réplicas.

Ações possíveis:

- Aumentar `app.redis-lock.wait-timeout` apenas se o cliente tolerar maior
  latência.
- Reduzir `HOT_ACCOUNT_RATIO` no teste para comparar gargalo de contenção vs.
  capacidade geral.
- Avaliar modelo assíncrono com fila FIFO por `accountId` se a serialização por
  conta virar requisito dominante.

## Critérios iniciais de aceitação

Valores realistas dependem da máquina local e do ambiente cloud, mas bons alvos
iniciais para produção são:

- p95 abaixo de 500 ms.
- p99 abaixo de 1000 ms em tráfego distribuído.
- HTTP 5xx abaixo de 1%.
- lock timeout abaixo de 1% do volume total.
- zero mensagens em DLQ.
- utilização média do pool de conexões abaixo de 80%.

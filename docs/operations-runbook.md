# Runbook operacional

Este runbook resume como operar, diagnosticar e explicar a aplicação em um
ambiente parecido com produção.

## Endpoints de saúde

Ambos os serviços expõem Actuator:

| Serviço | Health geral | Readiness | Liveness | Métricas |
|---|---|---|---|---|
| API | `/actuator/health` | `/actuator/health/readiness` | `/actuator/health/liveness` | `/actuator/prometheus` |
| Listener | `/actuator/health` | `/actuator/health/readiness` | `/actuator/health/liveness` | `/actuator/prometheus` |

No Kubernetes, os manifests usam:

- `startupProbe` em `/actuator/health/readiness`, dando tempo para a JVM e o
  Spring inicializarem.
- `readinessProbe` em `/actuator/health/readiness`, removendo o pod do tráfego
  quando ele não estiver pronto.
- `livenessProbe` em `/actuator/health/liveness`, reiniciando o pod se o
  processo ficar travado.
- ALB health check apontando para `/actuator/health/readiness`.

## Shutdown e rollout

Os serviços usam `server.shutdown=graceful`. Nos manifests Kubernetes há
`terminationGracePeriodSeconds` e `preStop` curto para reduzir a chance de o pod
receber novas requisições enquanto está encerrando.

Durante o rollout:

1. O Kubernetes cria novos pods.
2. `startupProbe` evita matar pods lentos durante o bootstrap.
3. `readinessProbe` só envia tráfego quando o pod está pronto.
4. O workflow aguarda `kubectl rollout status`.
5. Se o rollout falhar, o workflow executa `kubectl rollout undo`.

## Logs

Campos importantes:

- `correlationId`: propagado/gerado pelo header `X-Correlation-Id`.
- `transactionId`: extraído de `/transactions/{transactionId}`.
- `event=authorization_decision`: log estruturado de decisão financeira.

Exemplo:

```text
event=authorization_decision transactionId=... accountId=... type=DEBIT status=FAILED failureReason=INSUFFICIENT_FUNDS latencyMs=12
```

Esse log permite explicar uma decisão sem consultar o banco imediatamente.

## Métricas principais

API:

- `transactions.authorizations.total`
- `transactions.idempotency.replays.total`
- `transactions.idempotency.conflicts.total`
- `transactions.accounts.not_found.total`
- `transactions.locks.acquired.total`
- `transactions.locks.timeouts.total`
- `transactions.locks.wait.duration`
- `transactions.locks.circuit.opened.total`
- `transactions.locks.bypassed.total`

Listener:

- `accounts.imported.total`
- `accounts.duplicates.total`
- `sqs.account-created.messages.processed.total`
- `sqs.account-created.messages.failed.total`

Alertas sugeridos estão em [alerts.md](alerts.md).

## DLQ local

O LocalStack cria automaticamente:

- fila principal: `conta-bancaria-criada`
- DLQ: `conta-bancaria-criada-dlq`
- redrive policy com `maxReceiveCount=5`

Comandos úteis:

```bash
aws --endpoint-url=http://localhost:4566 --region sa-east-1 \
  sqs get-queue-attributes \
  --queue-url http://localhost:4566/000000000000/conta-bancaria-criada \
  --attribute-names RedrivePolicy ApproximateNumberOfMessages ApproximateNumberOfMessagesNotVisible

aws --endpoint-url=http://localhost:4566 --region sa-east-1 \
  sqs receive-message \
  --queue-url http://localhost:4566/000000000000/conta-bancaria-criada-dlq \
  --max-number-of-messages 10
```

## Containers e runtime

Os Dockerfiles usam a imagem de runtime `eclipse-temurin:21-jre` e criam o
usuário não-root `app` com UID/GID `10001`, alinhado aos manifests Kubernetes
(`runAsUser: 10001`).

Avaliamos imagens menores:

| Alternativa | Benefício | Tradeoff |
|---|---|---|
| `eclipse-temurin:21-jre-alpine` | menor tamanho | base `musl`, possíveis diferenças de DNS/locale e necessidade de revalidar a JVM |
| Distroless Java | menor superfície e melhor segurança | dificulta o debug e exige remover o healthcheck baseado em `curl` no Docker Compose |
| Manter `eclipse-temurin:21-jre` | previsibilidade e compatibilidade | imagem maior |

Decisão atual: manter `eclipse-temurin:21-jre` pela previsibilidade no case e
porque o Compose usa `curl` nos healthchecks. Em produção, uma evolução natural
seria distroless ou Temurin Alpine após ajustar os healthchecks e validar DNS,
certificados, observabilidade e troubleshooting.

## Troubleshooting rápido

| Sintoma | Verificar |
|---|---|
| API retorna `409 Resource locked` | `transactions.locks.timeouts.total`, conta quente, `app.redis-lock.wait-timeout` |
| API retorna `503 Lock unavailable` | fallback `FAIL_CLOSED`, conectividade com o Valkey |
| Listener não drena a fila | `SQS_POLLING_ENABLED`, credenciais/IRSA, `ApproximateNumberOfMessagesVisible`, logs do listener |
| Mensagens na DLQ | payload inválido, erro persistente no banco, redrive policy |
| Latência p99 alta | pool Hikari, locks por conta, CPU/memória, Aurora/RDS Proxy |
| Rollout travado | `kubectl describe pod`, readiness/liveness, variáveis e secrets |

# Runbook operacional

Este runbook resume como operar, diagnosticar e explicar a aplicacao em um
ambiente parecido com producao.

## Endpoints de saude

Ambos os servicos expõem Actuator:

| Servico | Health geral | Readiness | Liveness | Metricas |
|---|---|---|---|---|
| API | `/actuator/health` | `/actuator/health/readiness` | `/actuator/health/liveness` | `/actuator/prometheus` |
| Listener | `/actuator/health` | `/actuator/health/readiness` | `/actuator/health/liveness` | `/actuator/prometheus` |

No Kubernetes, os manifests usam:

- `startupProbe` em `/actuator/health/readiness`, dando tempo para a JVM e o
  Spring inicializarem.
- `readinessProbe` em `/actuator/health/readiness`, removendo o pod do trafego
  quando ele nao estiver pronto.
- `livenessProbe` em `/actuator/health/liveness`, reiniciando o pod se o
  processo ficar travado.
- ALB health check apontando para `/actuator/health/readiness`.

## Shutdown e rollout

Os servicos usam `server.shutdown=graceful`. Nos manifests Kubernetes ha
`terminationGracePeriodSeconds` e `preStop` curto para reduzir a chance de o pod
receber novas requisicoes enquanto esta encerrando.

Durante rollout:

1. Kubernetes cria novos pods.
2. `startupProbe` evita matar pods lentos durante bootstrap.
3. `readinessProbe` so envia trafego quando o pod esta pronto.
4. O workflow aguarda `kubectl rollout status`.
5. Se o rollout falhar, o workflow executa `kubectl rollout undo`.

## Logs

Campos importantes:

- `correlationId`: propagado/gerado pelo header `X-Correlation-Id`.
- `transactionId`: extraido de `/transactions/{transactionId}`.
- `event=authorization_decision`: log estruturado de decisao financeira.

Exemplo:

```text
event=authorization_decision transactionId=... accountId=... type=DEBIT status=FAILED failureReason=INSUFFICIENT_FUNDS latencyMs=12
```

Esse log permite explicar uma decisao sem consultar o banco imediatamente.

## Metricas principais

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

Alertas sugeridos estao em [alerts.md](alerts.md).

## DLQ local

O LocalStack cria automaticamente:

- fila principal: `conta-bancaria-criada`
- DLQ: `conta-bancaria-criada-dlq`
- redrive policy com `maxReceiveCount=5`

Comandos uteis:

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

Os Dockerfiles usam imagem runtime `eclipse-temurin:21-jre` e criam usuario
nao-root `app` com UID/GID `10001`, alinhado aos manifests Kubernetes
(`runAsUser: 10001`).

Avaliamos imagens menores:

| Alternativa | Beneficio | Tradeoff |
|---|---|---|
| `eclipse-temurin:21-jre-alpine` | menor tamanho | base `musl`, possiveis diferencas de DNS/locale e necessidade de revalidar JVM |
| Distroless Java | menor superficie e melhor seguranca | dificulta debug e exige remover healthcheck baseado em `curl` no Docker Compose |
| Manter `eclipse-temurin:21-jre` | previsibilidade e compatibilidade | imagem maior |

Decisao atual: manter `eclipse-temurin:21-jre` para previsibilidade no case e
porque o Compose usa `curl` nos healthchecks. Em producao, uma evolucao natural
seria distroless ou Temurin Alpine apos ajustar healthchecks e validar DNS,
certificados, observabilidade e troubleshooting.

## Troubleshooting rapido

| Sintoma | Verificar |
|---|---|
| API retorna `409 Resource locked` | `transactions.locks.timeouts.total`, conta quente, `app.redis-lock.wait-timeout` |
| API retorna `503 Lock unavailable` | fallback `FAIL_CLOSED`, conectividade com Valkey |
| Listener nao drena fila | `SQS_POLLING_ENABLED`, credenciais/IRSA, `ApproximateNumberOfMessagesVisible`, logs do listener |
| Mensagens na DLQ | payload invalido, erro persistente no banco, redrive policy |
| Latencia p99 alta | pool Hikari, locks por conta, CPU/memoria, Aurora/RDS Proxy |
| Rollout travado | `kubectl describe pod`, readiness/liveness, variaveis e secrets |

# Alertas sugeridos

Os alertas abaixo partem da instrumentação atual da aplicação, métricas padrão do
Spring Boot/Micrometer e métricas gerenciadas da AWS.

## API de autorização

| Alerta | Expressão sugerida | Severidade | Ação inicial |
|---|---|---|---|
| Taxa de 5xx alta | `sum(rate(http_server_requests_seconds_count{uri="/transactions/{transactionId}",status=~"5.."}[5m])) / sum(rate(http_server_requests_seconds_count{uri="/transactions/{transactionId}"}[5m])) > 0.01` | Crítica | Verificar logs `event=authorization_decision`, exceptions e saúde de Postgres/Valkey |
| p99 alto | `histogram_quantile(0.99, sum(rate(http_server_requests_seconds_bucket{uri="/transactions/{transactionId}"}[5m])) by (le)) > 1` | Alta | Checar contenção por conta, pool Hikari e latência do banco |
| Lock timeout alto | `sum(rate(transactions_locks_timeouts_total[5m])) / sum(rate(transactions_authorizations_total[5m])) > 0.01` | Alta | Identificar conta quente, revisar `wait-timeout` e capacidade do banco |
| Circuit breaker de Valkey abriu | `increase(transactions_locks_circuit_opened_total[5m]) > 0` | Alta | Validar ElastiCache/Valkey, rede e política fail-open/fail-closed |
| Conflito de idempotência anormal | `sum(rate(transactions_idempotency_conflicts_total[5m])) > 1` | Média | Verificar clientes reutilizando `transactionId` com payload divergente |

## Listener SQS

| Alerta | Expressão sugerida | Severidade | Ação inicial |
|---|---|---|---|
| Falha de processamento | `sum(rate(sqs_account_created_messages_failed_total[5m])) > 0` | Alta | Verificar payloads inválidos, erros de banco e logs do listener |
| Importação parou | `sum(rate(sqs_account_created_messages_processed_total[10m])) == 0` com backlog > 0 | Alta | Verificar polling, credenciais AWS/IRSA e conectividade com SQS |
| Duplicidade alta | `sum(rate(accounts_duplicates_total[5m])) / sum(rate(sqs_account_created_messages_processed_total[5m])) > 0.05` | Média | Avaliar reentrega excessiva ou timeout de visibilidade curto |

## AWS SQS

| Alerta | Métrica CloudWatch | Severidade | Ação inicial |
|---|---|---|---|
| DLQ recebeu mensagem | `ApproximateNumberOfMessagesVisible > 0` na DLQ | Crítica | Inspecionar payload, corrigir causa e planejar redrive |
| Backlog crescente | `ApproximateNumberOfMessagesVisible` crescendo por 10 min | Alta | Escalar listener, checar banco e taxa de publicação |
| Mensagem antiga | `ApproximateAgeOfOldestMessage > 300s` | Alta | Verificar se consumidores estão processando e deletando mensagens |

## Banco de dados

| Alerta | Expressão sugerida | Severidade | Ação inicial |
|---|---|---|---|
| Pool Hikari saturado | `hikaricp_connections_active / hikaricp_connections_max > 0.8` por 5 min | Alta | Revisar `DB_POOL_MAX_SIZE`, réplicas e performance do Aurora |
| Timeout de conexão | `increase(hikaricp_connections_timeout_total[5m]) > 0` | Crítica | Reduzir pressão no banco, verificar RDS Proxy/Aurora e queries lentas |
| Uso de CPU/IO alto no Aurora | CloudWatch `CPUUtilization`, `ReadLatency`, `WriteLatency` acima do baseline | Alta | Analisar plano de queries, locks e necessidade de escala vertical |

## Regras práticas

- Alertas de sintoma do usuário, como 5xx e p99, têm prioridade sobre alertas de
  causa.
- DLQ maior que zero é sempre investigável: pode representar perda funcional de
  importação de contas.
- Lock timeout isolado pode ser esperado sob conta quente; lock timeout
  sustentado indica contenção de negócio ou timeout agressivo.
- Escalar pods sem observar pool de conexões pode transferir o gargalo para o
  Aurora.

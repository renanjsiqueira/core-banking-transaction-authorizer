# Backlog de production readiness

Este backlog organiza os próximos incrementos para deixar o case mais forte em
alta volumetria, resiliência, observabilidade e facilidade de revisão. A ordem
prioriza itens com maior impacto na avaliação e menor risco de mexer no caminho
crítico de autorização.

## Fase 0 — Polimento obrigatório do case

Objetivo: cobrir itens explicitamente pedidos no enunciado e eliminar pequenas
inconsistências de documentação.

| Status | Item | Motivador | Critério de aceite |
|---|---|---|---|
| Concluido | Adicionar coleção de requisições (`requests/transaction-authorizer.http` ou Postman) | O case pede coleção de requisições para facilitar revisão | Coleção com health checks, crédito aprovado, débito aprovado, saldo insuficiente, conta inexistente, moeda inválida, replay idempotente e conflito de idempotência |
| Concluido | Corrigir diagrama/texto de arquitetura sobre lock | A conta é bloqueada no fluxo de autorização, não apenas no débito | `docs/architecture.md` descreve lock pessimista na conta para CREDIT/DEBIT/replay |
| Concluido | Documentar lacunas intencionais | O enunciado aceita patterns não implementados se motivados | README ou ADR lista o que foi implementado, o que ficou como evolução e por quê |

## Fase 1 — Observabilidade de negócio

Objetivo: tornar o comportamento financeiro e operacional visível em produção.

| Status | Item | Motivador | Critério de aceite |
|---|---|---|---|
| Concluido | Métricas customizadas na API | Actuator expõe métricas técnicas, mas faltam sinais de negócio | Counters por status (`SUCCEEDED`/`FAILED`), motivo de falha, conflitos de idempotência e conta não encontrada |
| Concluido | Métricas do lock Redis-compatible/Valkey | Alta contenção em `accountId` precisa aparecer antes de virar incidente | Counters/timers para lock adquirido, timeout, tempo de espera e chave por tipo (`account`/`transaction`) |
| Concluido | Correlation ID / MDC | Investigar uma transação exige seguir `transactionId` nos logs | Filtro/interceptor coloca `transactionId` e `X-Correlation-Id` no MDC e retorna header de correlação |
| Concluido | Logs estruturados de decisão | Auditoria e suporte precisam entender a decisão sem consultar o banco sempre | Logs de autorização incluem `transactionId`, `accountId`, `type`, `status`, `failureReason` e latência |

## Fase 2 — Resiliência explícita

Objetivo: evoluir retries simples para padrões mais robustos e documentar
decisões de fail-open/fail-closed.

| Status | Item | Motivador | Critério de aceite |
|---|---|---|---|
| Concluido | Backoff com full jitter no lock Redis-compatible | Evita sincronização de threads competindo pela mesma conta | Retry do lock usa delay crescente com jitter e mantém timeout total configurável |
| Concluido | Circuit breaker para Redis-compatible/Valkey | Redis-compatible/Valkey é camada auxiliar; indisponibilidade não deve derrubar o banco de verdade sem decisão explícita | Circuit breaker documenta e implementa política escolhida: fail-open para seguir só com Postgres ou fail-closed para proteger latência |
| Concluido | Timeouts explícitos em integrações | Falhas lentas são piores que falhas rápidas em alta volumetria | Valkey/SQS/Postgres têm timeouts documentados e configuráveis por ambiente |
| Concluido | DLQ local no LocalStack | Produção prevê DLQ, mas o ambiente local ainda não simula poison messages | `docker-compose` cria fila principal + DLQ + redrive policy; README mostra como inspecionar a DLQ |

## Fase 3 — Testes e corner cases adicionais

Objetivo: aumentar confiança nos pontos de maior risco: concorrência, lock,
idempotência e falhas transitórias.

| Status | Item | Motivador | Critério de aceite |
|---|---|---|---|
| Concluido | Teste de timeout do lock por conta | Garante que uma request concorrente não persiste transação ao estourar espera | Teste valida `ResourceLockedException`, ausência de escrita e retry seguro com mesmo `transactionId` |
| Concluido | Teste de Redis indisponível | Define comportamento real do sistema quando a camada auxiliar cai | Teste cobre a política do circuit breaker/fallback |
| Pendente | Testes de DLQ / mensagem inválida | Poison messages não devem travar drenagem da fila | Testes validam retry pelo SQS e envio para DLQ após limite configurado |
| Pendente | Teste de contrato da coleção | Evita coleção desatualizada | Smoke test ou script valida os requests principais contra app local |

## Fase 4 — Hardening de container e segurança

Objetivo: aproximar os containers de uma operação real com menor superfície de
risco.

| Item | Motivador | Critério de aceite |
|---|---|---|
| Rodar containers como usuário não-root | Boa prática básica de segurança em produção | Dockerfiles criam usuário de runtime e não executam como root |
| Imagem runtime menor | Reduz superfície e tempo de pull | Avaliar `eclipse-temurin:21-jre-alpine` ou distroless, mantendo healthcheck viável |
| Health/readiness específicos | Orquestrador precisa distinguir app vivo de app pronto | Actuator readiness/liveness documentados para API e listener |
| Scans no pipeline | O case pede produção e mitigação de risco | Pipeline documenta Trivy/OWASP/Sonar com falha por severidade configurada |

## Fase 5 — Escala e capacidade

Objetivo: provar que o desenho aguenta alta volumetria e orientar operação.

| Item | Motivador | Critério de aceite |
|---|---|---|
| Teste de carga local (`k6` ou Gatling) | Concorrência e p99 precisam ser medidos, não apenas presumidos | Script executa créditos/débitos concorrentes e reporta RPS, p95/p99, erro e lock timeout |
| Guia de dimensionamento | Facilita discussão de HPA, pool de conexões e Redis | Documento relaciona `DB_POOL_MAX_SIZE`, réplicas, HPA, latência e contenção por conta |
| Alertas sugeridos | Sem alertas, métricas viram painel decorativo | Backlog ou docs listam alertas para erro 5xx, p99, lock timeout, DLQ > 0, SQS backlog e conexão DB |

## Ordem recomendada

1. Fase 0 primeiro: fecha requisitos explícitos e melhora a revisão.
2. Fase 1 em seguida: observabilidade de negócio dá muito valor com baixo risco.
3. Fase 2 depois: resiliência muda comportamento sob falha, então pede mais teste.
4. Fases 3, 4 e 5 conforme tempo: consolidam qualidade, segurança e capacidade.

## Itens assumidamente futuros

- Outbox/CDC e banco por serviço: úteis se o listener ganhar domínio próprio,
  mas hoje adicionariam complexidade desproporcional.
- Kafka/SQS FIFO para serialização forte por `accountId`: interessante para um
  modelo assíncrono, mas o endpoint atual é síncrono.
- Tracing distribuído completo com OpenTelemetry: valioso em produção, mas pode
  ficar como evolução se correlação por log já estiver implementada.

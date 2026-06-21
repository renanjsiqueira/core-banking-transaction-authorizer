# Guia de avaliacao do case

Este guia conecta os requisitos do desafio com as decisoes, implementacoes e
documentos do projeto. A ideia e facilitar tanto a revisao do codigo quanto a
apresentacao tecnica.

## Resumo executivo

A solucao implementa um core banking de autorizacao de transacoes em Java 21 e
Spring Boot 3, com dois servicos em um monorepo Maven:

- `core-banking-transaction-authorizer-api`: API sincrona para autorizar
  `CREDIT` e `DEBIT`.
- `core-banking-account-created-listener`: consumidor SQS para importar contas
  criadas.
- `core-banking-commons-domain`: shared kernel de dominio, sem Spring/JPA.

O PostgreSQL e a fonte da verdade financeira. Redis-compatible/Valkey e usado
apenas como camada auxiliar de lock distribuido; se ele falhar, a consistencia
continua garantida pelo lock pessimista no banco e pela chave unica de
`transactions.id`.

## Requisitos do case x entrega

| Requisito | Como foi atendido | Onde revisar |
|---|---|---|
| Alta volumetria, escalabilidade, disponibilidade, resiliencia e consistencia | Dois servicos escalaveis separadamente, EKS, Aurora Multi-AZ, Valkey Multi-AZ, SQS + DLQ, HPA/KEDA, lock pessimista no banco e idempotencia por `transactionId` | [architecture.md](architecture.md), [cloud-deployment.md](cloud-deployment.md), [capacity-planning.md](capacity-planning.md) |
| Registrar decisoes, motivadores e tradeoffs | ADRs para arquitetura de dois servicos/banco compartilhado e locks Redis-compatible/Valkey | [adr/0001-two-services-architecture.md](adr/0001-two-services-architecture.md), [adr/0007-redis-distributed-locks.md](adr/0007-redis-distributed-locks.md) |
| Production ready: logs, metricas, conteinerizacao | Actuator/Prometheus, logs com correlation id, logs estruturados de decisao, Dockerfiles non-root, docker-compose, manifests Kubernetes | [README.md](../README.md), [operations-runbook.md](operations-runbook.md), [alerts.md](alerts.md) |
| Resiliencia: retries, backoff, full jitter, circuit breaker | Lock distribuido aguarda internamente com backoff exponencial + full jitter; circuit breaker fail-open/fail-closed para Redis/Valkey | [adr/0007-redis-distributed-locks.md](adr/0007-redis-distributed-locks.md) |
| Testes e corner cases | Unitarios, controller, integracao com PostgreSQL real, concorrencia, idempotencia, Redis indisponivel, timeout de lock por conta | [README.md](../README.md#estrategia-de-testes), `src/test` |
| Documentacao, execucao local, docker-compose e colecao | README com execucao local, scripts, docker-compose com Postgres/Redis/LocalStack/DLQ, colecao HTTP | [README.md](../README.md), [requests/transaction-authorizer.http](../requests/transaction-authorizer.http) |
| Diagrama de deploy em cloud publica | Diagrama AWS/EKS com API Gateway, ALB, EKS, Aurora, Valkey, SQS, DLQ, Secrets Manager, observabilidade | [cloud-deployment.md](cloud-deployment.md), [cloud-deployment.drawio](cloud-deployment.drawio), [cloud-deployment.png](cloud-deployment.png) |
| Pipeline e estrategia para reduzir risco de bug | GitHub Actions proposto, build/test, ECR, deploy EKS, rollback, canary/blue-green documentado | [pipeline.md](pipeline.md), [.github/workflows/deploy-prod.yml](../.github/workflows/deploy-prod.yml) |
| Patterns nao implementados por tempo | Backlog documenta lacunas e evolucoes, sem esconder tradeoffs | [production-readiness-backlog.md](production-readiness-backlog.md) |

## Fluxo de autorizacao

1. Cliente chama `POST /transactions/{transactionId}`.
2. API valida moeda `BRL` e valor positivo.
3. Se habilitado, a API tenta lock Redis-compatible por `transactionId` e
   `accountId`, nessa ordem.
4. Se o lock estiver ocupado, a request nao e rejeitada imediatamente: ela aguarda
   internamente ate o timeout configurado, com full jitter.
5. Dentro da transacao do banco, a conta e carregada com lock pessimista.
6. Se o `transactionId` ja existe:
   - mesmo payload: replay idempotente, retorna o resultado salvo;
   - payload diferente: `409 CONFLICT`.
7. `CREDIT` soma saldo; `DEBIT` subtrai se houver saldo.
8. A transacao e persistida no ledger e a resposta e retornada.

Ponto principal para explicar: Redis/Valkey melhora coordenacao entre pods, mas
nao e a garantia final. A garantia final e PostgreSQL ACID + `SELECT FOR UPDATE`
+ primary key em `transactions.id`.

## Fluxo de importacao de contas

1. O listener faz long polling na fila SQS `conta-bancaria-criada`.
2. Cada mensagem e parseada para DTO SQS.
3. A conta e inserida com saldo zero e moeda `BRL`.
4. Se a conta ja existe, o processamento e idempotente.
5. A mensagem so e deletada apos sucesso.
6. Em ambiente local, LocalStack cria fila principal + DLQ + redrive policy.

## Decisoes principais para defender

| Decisao | Motivador | Tradeoff |
|---|---|---|
| Dois servicos no mesmo monorepo | Separar carga sincrona de autorizacao da carga assincrona de ingestao | Mais modulos e pipelines, mas melhor isolamento operacional |
| Banco compartilhado | Mesmo bounded context e case simples; evita consistencia eventual prematura | Acoplamento por schema, mitigado por dono unico do Flyway |
| Shared kernel pequeno | Reuso apenas de linguagem de dominio estavel | Evita virar biblioteca generica acoplada |
| PostgreSQL como fonte da verdade | ACID, lock pessimista e ledger transacional | Escala de escrita exige cuidado com pool, indices e contas quentes |
| Redis/Valkey para lock auxiliar | Reduz concorrencia duplicada antes do banco | Nao pode armazenar saldo; precisa fallback |
| Fail-open padrao no lock distribuido | Redis/Valkey e auxiliar; banco continua correto | Pode aumentar latencia no banco durante falha do Redis |
| SQS at-least-once | Simples, resiliente e escalavel para ingestao de contas | Exige idempotencia e DLQ para poison messages |
| EKS + Aurora + Valkey | Controle operacional e escala horizontal em cloud publica | Mais componentes que uma PaaS simples |

## Perguntas provaveis

**Por que nao usar apenas rate limit?**  
Porque o problema principal nao e limitar volume global; e serializar operacoes
financeiras por conta. O lock por `accountId` aguarda por um tempo configuravel
antes de falhar, evitando perder transacao por rejeicao imediata.

**Por que Redis/Valkey se o PostgreSQL ja bloqueia a conta?**  
PostgreSQL garante correcao. Redis/Valkey reduz disputa entre pods antes de
segurar conexao JDBC e linha do banco. Se Redis falhar, a aplicacao continua
correta pelo banco.

**Por que banco compartilhado entre dois servicos?**  
Os servicos pertencem ao mesmo bounded context. Separar bancos agora criaria
outbox/CDC/consistencia eventual sem necessidade para o case.

**Por que pular testes de integracao no workflow de producao?**  
O workflow proposto representa deploy em runner efemero. Os testes de integracao
com Testcontainers continuam no build local completo e podem virar um job
separado com runner Docker dedicado. O deploy workflow roda unitarios/build
rapidos para nao acoplar publicacao a instabilidade de infraestrutura do runner.

**Como um bug nao impacta todos os clientes?**  
Pipeline documenta canary/blue-green, `environment: prod`, rollout status e
rollback. Em cloud, a API pode receber trafego progressivo enquanto metricas de
erro/p99 sao avaliadas.

## Roteiro de apresentacao

1. Mostrar o README e a divisao dos servicos.
2. Explicar o fluxo de autorizacao e a consistencia no PostgreSQL.
3. Explicar Redis/Valkey como lock auxiliar com fallback.
4. Mostrar SQS listener, idempotencia e DLQ local.
5. Abrir o diagrama AWS/EKS e justificar Aurora Multi-AZ, RDS Proxy, Valkey,
   SQS, API Gateway e observabilidade.
6. Mostrar pipeline/deploy e a estrategia de mitigacao de risco.
7. Mostrar testes, carga local, alertas e backlog.
8. Fechar com evolucoes futuras documentadas.

## O que ainda e evolucao

Os principais pontos ainda tratados como evolucao sao testes automatizados de DLQ
end-to-end, teste automatizado da colecao HTTP contra app local e automacao real
dos quality gates no GitHub Actions. Eles estao documentados no backlog para
mostrar consciencia de risco e caminho de evolucao.

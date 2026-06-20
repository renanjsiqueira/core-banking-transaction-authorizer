# ADR 0007 — Redis-compatible/Valkey para locks distribuídos

- Status: Aceita
- Data: 2026-06-19

## Contexto

A `core-banking-transaction-authorizer-api` roda com múltiplas instâncias (pods) atrás de
um load balancer. Requisições concorrentes para a mesma conta — ou replays do
mesmo `transactionId` — podem chegar a pods diferentes ao mesmo tempo. O
PostgreSQL já garante a consistência final (transação ACID, lock pessimista na
linha da conta e PK única em `transactions.id`), mas, sob alta contenção, vale
reduzir o trabalho duplicado e a disputa pela mesma linha antes de chegar ao
banco.

## Decisão

Adotar uma solução **Redis-compatible** como camada auxiliar de coordenação
distribuída na API, com Redis localmente e **Amazon ElastiCache for Valkey** na
proposta de produção:

1. **Lock distribuído por `accountId`** — reduz processamento simultâneo da
   mesma conta entre pods.
2. **Lock/idempotência temporária por `transactionId`** — reduz duplicação
   rápida do mesmo id.

Os locks usam `SET key value NX PX ttl` para aquisição e um **Lua script** para
liberação segura (só remove a chave se o *owner* conferir). A ordem de aquisição
é **sempre** `transactionId` → `accountId`, nunca invertida, evitando deadlock
lógico. Se o lock estiver ocupado, a requisição aguarda e tenta novamente por um
tempo configurável antes de falhar.

## Motivadores

- Menos contenção na linha da conta no PostgreSQL sob carga.
- Menos trabalho duplicado por replays simultâneos do mesmo `transactionId`.
- Coordenação entre múltiplos pods sem introduzir um broker pesado.

## Por que Redis-compatible/Valkey

- Latência muito baixa, primitivas atômicas (`SET NX`, `INCR`, `EXPIRE`) e
  scripting Lua para operações compostas atômicas.
- Já é um componente comum/gerenciado em cloud. Em produção, Valkey via
  ElastiCache mantém compatibilidade com o protocolo usado pela aplicação.

## Por que Redis-compatible/Valkey NÃO armazena saldo

- Essa camada não é durável o suficiente para ser fonte da verdade financeira e
  pode perder dados em falha/expiração.
- Saldo exige ACID e aritmética exata (`NUMERIC`/`BigDecimal`) — papel do
  PostgreSQL. Redis/Valkey guarda apenas chaves de lock com TTL curto.

## Por que o lock pessimista do banco foi mantido

Redis/Valkey pode falhar, expirar o lock antes do fim do processamento ou ficar
indisponível. Por isso o `accountRepository.findByIdForUpdate(accountId)`
(`PESSIMISTIC_WRITE`) permanece no fluxo: é a **garantia final** de consistência,
independente dessa camada. O lock distribuído é otimização; o lock do banco é
correção.

## Riscos e mitigações

| Risco | Mitigação |
|---|---|
| Lock expirar antes do fim do processamento | TTL curto + o lock pessimista do PostgreSQL garante a consistência mesmo se o lock Redis-compatible expirar |
| Indisponibilidade do Redis-compatible/Valkey | `app.redis-lock.enabled=false` desativa a camada; o banco continua garantindo a correção. (Evolução: circuit breaker / *fail-open*) |
| Falsa contenção (unlock indevido) | Unlock seguro via Lua com verificação de *owner* (`instanceId:uuid`); nunca remove lock de outro pod/request |
| Deadlock lógico | Ordem fixa de aquisição: `transactionId` → `accountId` |
| Lock ocupado por mais tempo que o limite configurado | A requisição falha sem persistir a transação; o cliente pode repetir o mesmo `transactionId` com segurança |
| Observabilidade | Logs nos caminhos de lock/recusa; métricas via Micrometer/Actuator |

## Alternativas consideradas

- **Apenas lock no PostgreSQL:** correto, mas concentra toda a contenção na
  linha da conta; sob carga, mais espera no banco. Redis alivia antes do banco.
- **Kafka com partição por `accountId`:** serializa por conta de forma forte,
  mas adiciona um broker e muda o modelo para assíncrono — excessivo para o
  endpoint síncrono do case.
- **SQS FIFO com `MessageGroupId` por `accountId`:** serialização por grupo,
  porém também torna o fluxo assíncrono e não cabe na autorização síncrona.

## Evolução futura

- *Fail-open* explícito com circuit breaker quando o Redis-compatible/Valkey estiver indisponível.
- Possível uso de lock por `accountId` no listener caso a importação evolua para
  além de um simples upsert idempotente por PK.

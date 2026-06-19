# ADR 0001 — Arquitetura de dois serviços (API + Listener) com banco compartilhado

- Status: Aceita
- Data: 2026-06-19

## Contexto

O desafio possui duas cargas de trabalho distintas no mesmo bounded context
(contas e autorização):

1. Um fluxo **síncrono** request/response que autoriza transações CREDIT/DEBIT
   (`POST /transactions/{transactionId}`). Sensível à latência, com picos,
   voltado ao usuário.
2. Um fluxo **assíncrono** que ingere eventos de abertura de conta de uma fila
   AWS SQS (`conta-bancaria-criada`) — um consumidor de background, orientado a
   throughput, que precisa drenar a fila 24/7.

Um único deployable poderia atender ambos, mas os dois têm perfis muito
diferentes de escala, falha e operação.

## Decisão

Dividir a aplicação em um monorepo Maven multi-módulo com três módulos:

- **`shared-kernel`** — apenas tipos compartilhados (enums, convenções de
  dinheiro). Sem regra de negócio pesada, sem Spring, sem persistência. Mantém os
  dois serviços consistentes sem acoplá-los por comportamento.
- **`transaction-authorization-api`** — a API síncrona. Dona do schema do banco
  (Flyway) e das regras de negócio de autorização.
- **`account-onboarding-listener`** — o consumidor SQS assíncrono. Importa
  contas; Flyway desabilitado.

Ambos compartilham um **único banco PostgreSQL** neste desafio.

## Motivadores

- **Escalabilidade independente:** escalar a API horizontalmente para picos de
  requisição sem escalar o consumidor da fila, e vice-versa.
- **Isolamento operacional:** um backlog/incidente no onboarding não degrada o
  caminho crítico de autorização; deploys são independentes.
- **Propriedade clara dos modos de falha:** a API ajusta preocupações
  web/HTTP; o listener ajusta polling, backoff e entrega at-least-once.
- **Mesmo bounded context:** ambos pertencem a "contas & autorização", então um
  schema compartilhado evita complexidade prematura de dados distribuídos.

## Trade-offs

- **Acoplamento por banco compartilhado:** dois serviços escrevendo nas mesmas
  tabelas compartilham um contrato de schema. Mitigado por um único dono do
  schema (a API) e migrations aditivas e retrocompatíveis.
- **Build de monorepo:** o reactor builda todos os módulos juntos; as imagens
  Docker por serviço usam `-pl <módulo> -am` para buildar só o necessário.
- **Alguma duplicação:** cada serviço mapeia seu próprio `AccountEntity`. É
  intencional — mantém os modelos de persistência independentes e evita colocar
  entidades JPA no shared-kernel.

## Por que o banco compartilhado foi aceito aqui

- Ambos os serviços estão no **mesmo bounded context**; ainda não há uma
  fronteira de propriedade que justifique datastores separados.
- Introduzir um banco-por-serviço agora forçaria transações distribuídas ou um
  mecanismo de consistência eventual/outbox entre "conta criada" e "autorizar" —
  **complexidade desproporcional** para o case.
- O schema é **simples e estável** (accounts, transactions) e tem um dono claro
  (a API), então o acoplamento é de baixo risco e explícito.

## Como evoluiria em produção

- As **migrations de schema** rodariam como um pipeline/job dedicado (ex.: um
  step de migration Flyway) **antes** de publicar as versões dos serviços — não
  no startup da aplicação — para que os deploys fiquem desacoplados das migrations
  e seguros sob blue/green ou canary.
- Se o listener desenvolver seu próprio write model, migrar para
  **banco-por-serviço** com **outbox + eventos** (ou CDC) para integrar,
  aceitando consistência eventual.
- Adicionar uma **DLQ + redrive policy** para poison messages e autoscaling por
  profundidade da fila no listener; autoscaling por RPS/latência na API.
- Promover o contrato de schema compartilhado a um artefato versionado
  explicitamente caso surjam mais consumidores.

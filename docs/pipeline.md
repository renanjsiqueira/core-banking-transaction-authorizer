# Pipeline CI/CD e estratégia de deploy

Objetivo: publicar com segurança e **limitar o raio de impacto de um release
ruim**, de forma que um bug nunca atinja 100% dos clientes de uma vez.

## Estágios do pipeline

1. **Build & testes unitários** — `mvn verify` (o reactor builda os 3 módulos).
2. **Testes de integração** — Testcontainers (PostgreSQL real); SQS via LocalStack.
3. **Quality gates** — threshold de cobertura, análise estática (SpotBugs/Sonar),
   scan de dependências/vulnerabilidades (OWASP / Trivy nas imagens).
4. **Empacotamento** — build das imagens de container por serviço, tag com o git
   SHA, push para o registry (ECR).
5. **Job de migration de schema** — roda o **Flyway** no banco alvo como um job
   separado e idempotente. As migrations são **aditivas/retrocompatíveis**, de
   forma que a versão antiga da aplicação continue funcionando durante o rollout.
6. **Deploy** — rollout progressivo por serviço (abaixo).
7. **Verificação pós-deploy** — health/smoke checks + métricas de SLO chave;
   rollback automático em caso de violação.

## Estratégia de deploy (mitigação de risco)

- **Canary releases na API:** direciona uma fatia pequena de tráfego (ex.: 5% →
  25% → 50% → 100%) para a nova versão, observando taxa de erro, latência p99 e
  KPIs de negócio em cada passo. Rollback automático se um threshold for
  violado, de modo que só uma pequena fração de clientes seja exposta a uma
  regressão.
- **Blue/green como baseline / fallback:** mantém a versão anterior aquecida para
  troca instantânea; permite releases sem downtime e rollback rápido.
- **Rollout do listener:** rolling update com `maxUnavailable` saudável. Como o
  consumo é **idempotente e at-least-once**, a sobreposição de consumidores
  antigo/novo durante o rollout é segura.
- **Mudanças de banco retrocompatíveis** (expand/contract): nunca quebram a
  versão em execução; mudanças destrutivas só ocorrem após todas as instâncias
  serem atualizadas.

## Pipelines independentes

Cada serviço faz deploy de forma independente (imagens separadas, rollouts
separados). O schema compartilhado é o único acoplamento — protegido pelo job de
migration rodando primeiro e por migrations retrocompatíveis.

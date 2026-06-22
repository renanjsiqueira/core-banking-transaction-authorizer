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

## Quality gates e scans

Para uma esteira de produção, os gates recomendados são:

| Gate | Ferramenta sugerida | Politica |
|---|---|---|
| Build e unit tests | Maven/Surefire | Falha bloqueia merge/deploy |
| Integração com infraestrutura | Testcontainers + LocalStack | Job separado com runner Docker dedicado |
| Cobertura e qualidade | SonarCloud/SonarQube ou JaCoCo + SpotBugs | Bloquear queda de cobertura e bugs críticos |
| Dependências vulneráveis | OWASP Dependency-Check ou Snyk | Bloquear CVE High/Critical sem exceção aprovada |
| Imagens vulneráveis | Trivy ou Grype | Bloquear High/Critical em imagem runtime |
| IaC/Kubernetes | kubeconform, kube-score ou Checkov | Bloquear manifest inválido e riscos básicos |

Há dois workflows:

- [`.github/workflows/ci.yml`](../.github/workflows/ci.yml) roda em **pull request**
  para `master` (e em pushes de branches de feature) e executa a **suíte completa**
  (`mvn clean verify`), incluindo os testes de integração com Testcontainers
  (PostgreSQL + Redis), que rodam no runner `ubuntu-latest` por ter Docker. É o
  gate que protege o `master`.
- [`.github/workflows/deploy-prod.yml`](../.github/workflows/deploy-prod.yml) roda
  no push para `master`: build/testes, build das imagens, push no ECR e rollout no
  EKS.

Para ativar os scans em uma conta real, entrariam jobs antes de `build-and-push`,
por exemplo:

```text
verify -> dependency-scan -> static-analysis -> image-scan -> deploy-prod
```

Secrets/variáveis típicas:

- `SONAR_TOKEN`, se usar SonarCloud/SonarQube.
- `SNYK_TOKEN`, se usar Snyk em vez de OWASP Dependency-Check.
- Nenhuma secret obrigatória para Trivy em modo básico, apenas acesso ao registry
  depois do login ECR.

A decisão de deixar os scans como proposta documentada evita criar uma pipeline
que falhe em repositório de avaliação sem tokens externos, mas deixa claro quais
gates seriam obrigatórios antes de produção real.

## GitHub Actions proposto

O workflow [`.github/workflows/deploy-prod.yml`](../.github/workflows/deploy-prod.yml)
representa a esteira automatizada para `master`:

1. Executa `./scripts/build-all.sh -q -Dtest='!*IntegrationTest' -Dsurefire.failIfNoSpecifiedTests=false`.
2. Assume uma IAM Role na AWS via OIDC.
3. Builda as imagens Docker dos dois serviços.
4. Publica as imagens no Amazon ECR com tag do commit SHA.
5. Renderiza o manifest `k8s/prod.yaml` com os valores reais do ambiente.
6. Aplica o deploy no EKS e aguarda `rollout status`.
7. Executa rollback dos deployments se o rollout falhar.

Nesta primeira versão, a pipeline de deploy pula os testes `*IntegrationTest`
porque eles dependem de Testcontainers/PostgreSQL/Redis e podem ficar instáveis
em runner efêmero. Eles continuam disponíveis no build local completo via
`./scripts/build-all.sh` e podem virar um job separado de integração quando a
pipeline tiver runner/cache Docker dedicados.

Para funcionar em uma conta real, faltaria configurar no GitHub:

**Repository variables**

- `AWS_REGION`
- `AWS_ACCOUNT_ID`
- `EKS_CLUSTER_NAME`
- `PROD_RDS_PROXY_ENDPOINT`
- `PROD_VALKEY_ENDPOINT`
- `PROD_SQS_QUEUE_URL`

**Repository secrets**

- `AWS_ROLE_TO_ASSUME`
- `PROD_DB_USERNAME`
- `PROD_DB_PASSWORD`

A role `AWS_ROLE_TO_ASSUME` deve confiar no OIDC do GitHub Actions e restringir
o `sub` para este repositório/branch ou para o environment `prod`. O environment
`prod` pode ter aprovação manual para reduzir risco antes de publicar em
produção.

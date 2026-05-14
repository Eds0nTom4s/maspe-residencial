# Tenant Core — Preparação (Flyway + Testes + Baseline Real) — Prompt 0.1 / 0.2

Data: 2026-05-14

## Objetivo

Preparar a base técnica para evolução estrutural controlada do backend CONSUMA:

- Introduzir Flyway (migrations versionadas)
- Criar estrutura mínima de testes automatizados com PostgreSQL real (Testcontainers)
- Preservar compatibilidade com o estado atual (sem implementar Tenant Core ainda)

## Decisão: Flyway + baseline

### Prompt 0.1 (histórico): baseline marker

O projeto ainda não está em produção e o schema atual é volátil (até hoje dependente de `ddl-auto=create-drop` em DEV, e sem diretório de migrations).

Por isso, foi escolhido um baseline controlado:

- Criar `src/main/resources/db/migration/V1__baseline_schema.sql` como **marker** (sem criar o schema completo atual).
- A partir da Fase 1, todas as novas mudanças estruturais (Tenant, Plano, Subscricao, TenantUser etc.) devem ser criadas como migrations Flyway reais.

Motivo (naquele momento): evitar custo/risco de congelar um snapshot de schema ainda volátil.

### Prompt 0.2 (estratégia final): baseline real do schema atual

Decisão final: **abandonar o marker vazio** e gerar um baseline real completo.

Objetivo:
- `V1__baseline_schema.sql` representa o schema atual completo (PostgreSQL-native)
- Flyway passa a ser a fonte versionada de evolução do banco
- Hibernate deixa de criar schema em ambientes controlados (passa a validar)

Arquivo:
- `src/main/resources/db/migration/V1__baseline_schema.sql`

Como foi gerado neste repositório:
- Fallback reproduzível sem Docker: `mvn -q -Dtest=GenerateFlywayBaselineSchemaTest test`
  - Classe: `src/test/java/com/restaurante/tools/GenerateFlywayBaselineSchemaTest.java`

Como deve ser gerado em ambiente ideal (preferido, com PostgreSQL real + pg_dump):
1) Criar um banco temporário limpo (ex.: `consuma_baseline`)
2) Subir a aplicação apontando para esse banco com `ddl-auto=create` (apenas para gerar schema)
3) Extrair o schema:
   - `pg_dump --schema-only --no-owner --no-privileges -d consuma_baseline > V1__baseline_schema.sql`
4) Limpar do dump:
   - sem `CREATE DATABASE`
   - sem `ALTER OWNER`
   - sem privilégios/grants
5) Validar:
   - banco limpo + `flyway migrate`
   - aplicação sobe com `ddl-auto=validate`

## Configuração recomendada de `ddl-auto`

Estado após Prompt 0.2:
- DEV (PostgreSQL): `validate` + Flyway migrando schema
- PROD: `validate` + Flyway migrando schema
- TEST unitário (H2): `create-drop` (Flyway desabilitado)
- TEST integração (PostgreSQL/Testcontainers): `validate` + Flyway

Objetivo final (após o Tenant Core estar migrado):
- DEV: `validate` (e opcionalmente `clean + migrate` em ambiente local controlado)
- TEST: `validate` + migrations completas (produção-like)
- PROD: `validate` + Flyway como fonte de verdade do schema

## Como rodar testes

Requisitos:
- Para testes de integração (PostgreSQL real): Docker disponível (Testcontainers)

Comandos:
- `mvn -q -DskipTests compile`
- `mvn test`

## Arquivos adicionados/alterados no Prompt 0.1 / 0.2

- `pom.xml` (dependências Flyway + Testcontainers)
- `src/main/resources/db/migration/V1__baseline_schema.sql`
- `src/main/resources/application.properties` (propriedades Flyway)
- `src/test/resources/application-test.properties`
- `src/test/java/com/restaurante/testsupport/PostgresTestcontainersConfig.java`
- `src/test/java/com/restaurante/ApplicationContextTest.java`
- `src/test/java/com/restaurante/TenantCorePreparationTest.java`
- `src/test/java/com/restaurante/MultiTenantIsolationPlaceholderTest.java`
- `src/test/java/com/restaurante/tools/GenerateFlywayBaselineSchemaTest.java`
- `src/test/resources/application-it-postgres.properties`
- `src/test/java/com/restaurante/FlywayBaselinePostgresIT.java`

## Riscos restantes

- O baseline real deve ser revalidado em PostgreSQL real (ideal: `pg_dump --schema-only`) quando Docker/PG estiverem disponíveis no ambiente.
- Unit tests com H2 não validam a “exatidão PostgreSQL” do baseline; isso fica para os testes de integração (`it-postgres`).

## Próximos passos (Prompt 1)

- Fase 1: criar entidades SaaS (Tenant/Plano/Subscricao/TenantUser) com migrations Flyway reais.
- Adotar um padrão de migrações por sprint (V2, V3, …), com revisão obrigatória de índices e constraints.

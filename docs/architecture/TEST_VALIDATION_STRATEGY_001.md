# TEST_VALIDATION_STRATEGY_001

Data: 2026-07-06
Escopo: estrategia oficial de validacao local e integrada para proximas fases CONSUMA.

## 1. Objetivo

Definir uma base de validacao confiavel para frontend, backend e fluxos fullstack sem alterar regras de produto. A estrategia separa testes rapidos, testes focados, E2E local com mocks controlados e verify amplo de integracao.

## 2. Comandos frontend

- `npm run typecheck`
- `npm run lint`
- `npm run test`
- `npm run build`
- `npm run test:e2e`

## 3. Comandos backend

- `mvn -DskipTests compile`
- `mvn test`
- `mvn -Pit verify`

## 4. O que cada comando valida

Frontend:
- `npm run typecheck`: contratos TypeScript/Vue.
- `npm run lint`: padrao estatico e problemas de codigo.
- `npm run test`: unidade/composables/componentes via Vitest.
- `npm run build`: empacotamento Vite e regressao de bundling.
- `npm run test:e2e`: webServer, renderizacao real no browser, smoke publico e smoke app com mocks controlados.

Backend:
- `mvn -DskipTests compile`: compilacao Java, annotation processing e wiring basico.
- `mvn test`: testes unitarios e slices de baixo custo via Surefire.
- `mvn -Pit verify`: integracao ampla via Failsafe, contextos Spring, Flyway, banco/Testcontainers e contratos operacionais.

## 5. Quando usar teste focado

Usar teste focado durante desenvolvimento quando a mudanca atinge uma area delimitada, por exemplo uma policy, um controller ou uma migration especifica. O comando focado nao substitui o verify amplo para fases com risco operacional, pagamento, caixa, KDS, PDV, public-menu, OTP, migrations ou contratos fullstack.

## 6. Quando usar verify amplo

`mvn -Pit verify` e obrigatorio antes de aprovar mudancas backend de integracao, migrations, pagamento, origem/estado de pedido, allowedActions, tenant safety, caixa, KDS, PDV, fiscalidade, public-menu/OTP ou qualquer alteracao que toque contexto Spring/Flyway/Testcontainers.

## 7. Como executar E2E local

Comando padrao:

```bash
npm run test:e2e
```

O Playwright sobe Vite com host e porta explicitos:

```text
npm run dev -- --host ${E2E_HOST:-127.0.0.1} --port ${E2E_PORT:-5173} --strictPort
```

A URL de espera e `/login`. Em ambiente com isolamento de rede local, executar fora do sandbox para permitir que o browser acesse `127.0.0.1:5173`.

## 8. Dependencias de ambiente

Frontend:
- Node local em `.tooling/node-v24.16.0-linux-x64/bin` via scripts npm.
- Browser Playwright instalado.
- Porta 5173 livre em `127.0.0.1`.

Backend:
- JDK compativel com o projeto.
- Maven.
- Docker/Testcontainers disponivel para o profile `it`.
- Banco/container acessivel no ambiente de teste.

## 9. Portas usadas

- Frontend Vite/E2E: `127.0.0.1:5173` por padrao.
- API fake default para E2E mockado: `127.0.0.1:18080/api`.
- Backend real: definido pelo ambiente de execucao; nao e necessario para os smokes mockados.

## 10. Estrategia de credenciais

Credenciais reais nunca devem ser versionadas. Testes que exigem login real devem usar variaveis de ambiente, por exemplo `CONSUMA_E2E_USERNAME`, `CONSUMA_E2E_PASSWORD` e `CONSUMA_E2E_TENANT_ID`. Sem variaveis, o teste integrado real deve ficar skipped com justificativa explicita.

## 11. Estrategia de mocks

Mocks sao permitidos para:
- smoke de renderizacao local;
- sessao tenant fake em localStorage;
- APIs tenant controladas por Playwright route mocks;
- isolamento do frontend contra indisponibilidade do backend.

Mocks nao devem alterar codigo de producao nem mascarar contratos criticos em testes integrados obrigatorios.

## 12. Como lidar com webServer

O webServer do Playwright deve:
- usar host/porta explicitos;
- usar `--strictPort`;
- aguardar rota publica real `/login`;
- ter timeout suficiente para Vite iniciar;
- reutilizar servidor local apenas fora de CI;
- falhar de forma clara quando a porta estiver indisponivel.

## 13. Como lidar com backend real

E2E local padrao nao depende de backend real. Fluxos fullstack reais devem ter perfil/ambiente proprio, credenciais controladas e backend inicializado antes do Playwright. Falha de backend real nao deve ser confundida com falha de smoke mockado.

## 14. O que nao deve ser mockado em producao

Nao mockar em validacoes finais de produto:
- autorizacao real;
- allowedActions;
- PedidoOrigem persistido;
- transicoes de pedido;
- pagamento apos aceite;
- callbacks financeiros;
- migrations/Flyway;
- contratos de tenant safety;
- regras de caixa, KDS, PDV e fiscalidade.

## 15. Criterio para aprovar fase futura

A fase pode ser aprovada quando os comandos obrigatorios do escopo retornarem codigo 0, os skips estiverem justificados, os riscos residuais estiverem documentados e nenhuma regra de produto tiver sido contornada para fazer teste passar.

## 16. Criterio para bloquear fase futura

Bloquear quando:
- `mvn -Pit verify` falhar ou nao concluir em mudanca backend critica;
- `npm run test:e2e` falhar por webServer/config em mudanca frontend funcional;
- credenciais/secrets forem introduzidos no repositorio;
- um teste critico for removido/skipped sem justificativa;
- uma regra de produto for alterada para satisfazer teste;
- migrations, pagamento, caixa, KDS, PDV, public-menu ou OTP avancarem sem teste integrado aplicavel.

## 17. Matriz por tipo de mudanca

- UI visual isolada: `npm run typecheck`, `npm run lint`, `npm run test`, `npm run build`; E2E quando a rota/tela afetada tiver smoke.
- Frontend funcional: todos os comandos frontend; E2E obrigatorio.
- Backend service: `mvn -DskipTests compile`, `mvn test`; teste focado permitido no desenvolvimento; `mvn -Pit verify` antes da aprovacao se houver integracao.
- Backend migration: `mvn -DskipTests compile`, `mvn test`, `mvn -Pit verify` obrigatorio.
- Fullstack operacional: todos os comandos frontend e backend; E2E obrigatorio.
- Pagamento/caixa: verify amplo obrigatorio e teste integrado especifico; nao aprovar apenas com mock frontend.
- KDS/producao: verify amplo, teste operacional focado e E2E/validador do fluxo quando disponivel.
- PDV: verify amplo e E2E/fluxo integrado; nao aprovar sem validar transicao operacional principal.
- Public-menu/OTP: verify amplo, teste publico/seguranca focado e E2E quando houver rota publica afetada.

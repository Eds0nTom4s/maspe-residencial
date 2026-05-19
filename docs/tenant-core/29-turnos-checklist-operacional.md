# Prompt 29 — Turnos Operacionais + Checklists (Abertura/Fecho)

## 1) Objetivo da fase

Introduzir disciplina operacional diária para pilotos reais, criando uma janela formal de operação (**TurnoOperacional**) com:

- abertura com **checklist obrigatório**
- fecho com **pré-fecho** (pendências/bloqueios vs avisos)
- fecho com **checklist obrigatório**
- auditoria operacional via `OperationalEventLog`
- associação opcional `Pedido → TurnoOperacional` (sem quebrar o fluxo público por QR por default)

Não é caixa financeiro completo. Não altera AppyPay/callback.

## 2) Conceito — `TurnoOperacional`

Representa uma janela formal de operação por `tenant + instituicao + unidadeAtendimento`.

Regras principais (MVP):
- `unidade_atendimento_id` é **obrigatório** (evita ambiguidades)
- apenas **1 turno ABERTO/EM_FECHO** por `tenant + instituicao + unidadeAtendimento`
- turno não é apagado

Status (`TurnoOperacionalStatus`):
- `ABERTO`
- `EM_FECHO`
- `FECHADO`
- `CANCELADO`

## 3) Checklists (templates + runs)

Estrutura:
- `ChecklistOperacionalTemplate` + `ChecklistOperacionalItemTemplate` (definição)
- `ChecklistOperacionalRun` + `ChecklistOperacionalItemRun` (execução)

Tipos (`ChecklistTipo`):
- `ABERTURA`
- `FECHO`

Nesta fase, os templates default são criados **automaticamente no backend** (sem CRUD completo).

### Itens default

Abertura (exemplos):
- `DEVICE_ONLINE` (obrigatório)
- `QR_VISIVEL` (obrigatório)
- `CATALOGO_ATUALIZADO` (obrigatório)
- `UNIDADE_PRODUCAO_ATIVA` (obrigatório)
- `OPERADOR_CONFIRMOU` (obrigatório)

Fecho (exemplos):
- `PEDIDOS_PENDENTES_VERIFICADOS` (obrigatório)
- `PAGAMENTOS_PENDENTES_VERIFICADOS` (obrigatório)
- `SUBPEDIDOS_EM_ABERTO_VERIFICADOS` (obrigatório)

## 4) Abertura

Endpoint:
- `POST /tenant/operacao/turnos/abrir`

Fluxo:
1. valida RBAC
2. valida inexistência de turno aberto para a mesma unidade
3. cria `TurnoOperacional` (ABERTO)
4. valida e registra `ChecklistOperacionalRun` (ABERTURA)
5. registra eventos operacionais:
   - `CHECKLIST_ABERTURA_CONCLUIDO`
   - `TURNO_ABERTO`

## 5) Pré-fecho e Fecho

Pré-fecho:
- `GET /tenant/operacao/turnos/{turnoId}/pre-fecho`

Retorna:
- contagens por status (pedido/subpedido/pagamento)
- sessões abertas
- dispositivos “offline” (heartbeat stale)
- `bloqueios` (impedem fecho)
- `avisos` (não bloqueiam)

Fecho:
- `POST /tenant/operacao/turnos/{turnoId}/fechar`

Regras:
- se existem bloqueios, retorna `409` (a menos que `forcarFecho=true`)
- `forcarFecho=true` exige OWNER/ADMIN + observação obrigatória
- registra checklist de fecho e eventos:
  - `CHECKLIST_FECHO_CONCLUIDO`
  - `TURNO_FECHADO` ou `TURNO_FECHADO_FORCADO`

## 6) Associação `Pedido → TurnoOperacional`

Nesta fase, `pedidos.turno_operacional_id` foi introduzido como **nullable**.

Ao criar pedido público por QR:
- se existe turno aberto para a unidade, o pedido é associado ao turno
- se não existe:
  - por default **não bloqueia** (compatibilidade)
  - registra evento `PEDIDO_SEM_TURNO_ABERTO`

Property:
- `consuma.operacao.require-open-turno-for-orders=false`

Quando `true`, bloqueia criação de pedido sem turno aberto (retorna `409`).

## 7) RBAC

Abrir/Fechar turno:
- `TENANT_OWNER`, `TENANT_ADMIN`, `TENANT_OPERATOR`, `TENANT_CASHIER`

Forçar fecho / cancelar:
- `TENANT_OWNER`, `TENANT_ADMIN`

Leitura (turno atual/listagem/detalhe/pre-fecho):
- `TENANT_OWNER`, `TENANT_ADMIN`, `TENANT_OPERATOR`, `TENANT_FINANCE`, `TENANT_CASHIER`, `TENANT_KITCHEN`

## 8) Eventos operacionais

Eventos adicionados em `OperationalEventType`:
- `TURNO_ABERTO`
- `TURNO_FECHO_INICIADO`
- `TURNO_FECHADO`
- `TURNO_FECHADO_FORCADO`
- `TURNO_CANCELADO`
- `CHECKLIST_ABERTURA_CONCLUIDO`
- `CHECKLIST_FECHO_CONCLUIDO`
- `PEDIDO_SEM_TURNO_ABERTO`

## 9) O que fica para futuro

- caixa financeiro completo / reconciliação final contábil
- abertura/fecho por device
- relatório/print de fecho
- disciplina de rotinas por template customizável no painel
- POS criando pedido (online) exigindo turno aberto por policy


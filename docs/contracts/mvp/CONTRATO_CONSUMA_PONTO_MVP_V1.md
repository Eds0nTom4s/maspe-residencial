# CONTRATO CONSUMA PONTO MVP V1

Estado: congelado para implementação dos Passos 2, 3 e 4  
Versão: 1.0.0  
Backend proprietário: `Eds0nTom4s/maspe-residencial@813503ea0513a5e2761ecb7a092b0a9ff5cb5cb0`  
Schema: `V20260722.01`  
Frontend consumidor: `Eds0nTom4s/CONSUMA-UI@f8bec74c822bbeacbefdde303c8e66fd325f5035`

## 1. Decisão de produto

O primeiro Release Candidate publica exclusivamente `CONSUMA_PONTO`. `CONSUMA_REST` e contratos de mesa/sessão permanecem preservados, mas com exposição `PRESERVED_NOT_RELEASED`. O selector deve ocultar ou desabilitar REST no RC (Passo 10); esta fase não altera o selector.

## 2. Fontes normativas

O manifesto `CONSUMA_PONTO_MVP_V1.contract.json` é a fonte estruturada e o OpenAPI é o subconjunto HTTP verificável. Em conflito: invariantes de produto aprovadas, este contrato, implementação backend-base, testes backend, snapshot frontend. Uma branch histórica nunca é fonte de verdade.

## 3. Autenticação e isolamento

- `NONE`: login e recursos públicos resolvidos por token QR/pagamento não enumerável.
- `GLOBAL`: `GET /auth/tenants` e `POST /auth/tenant/select`.
- `TENANT`: `/tenant/**`; o tenant provém exclusivamente dos claims do JWT TENANT.
- `X-Tenant-Id`, `X-Tenant-Code` e `X-Business-Id` são proibidos no caminho canónico.
- Um JWT TENANT em discovery/select ou GLOBAL em operação tenant é erro de scope.
- `TENANT_TOKEN_STALE` obriga a descartar o JWT TENANT e seleccionar novamente o negócio.

## 4. Envelopes e propriedade

Sucesso usa `ApiResponse(success,message,data)`. O erro de domínio visado usa `ErrorResponse(timestamp,status,error,message,code,path,validationErrors,additionalData)`. A emissão inconsistente de `code` é o gap `CG-05`, não autorização para a UI interpretar texto.

IDs, timestamps, `tenantId`, `businessAccountId`, `orderCode`, estados, valores derivados e `allowedActions` pertencem ao backend. A UI fornece comandos e dados editáveis declarados nos DTOs; não pode derivar autorizações a partir de enums locais. Paginação é Spring `Page`, índice zero; datas são ISO-8601; moeda do MVP é AOA e valores são `BigDecimal` no servidor.

## 5. Invariantes de pedido e pagamento

1. Pedido que exige aceite nasce `CRIADO`.
2. Antes de aceite válido não pode existir ordem de pagamento.
3. Aceite exige estado `CRIADO`, turno aberto quando a policy o exige e subpedidos `CRIADO`.
4. O aceite muda os subpedidos para `PENDENTE`, deriva o pedido para `EM_ANDAMENTO` e garante no máximo uma ordem de pagamento.
5. Pagamento só é oferecido quando `allowedActions` o permite; a UI não reconstrói a policy.
6. Entrega exige estado financeiro `PAGO` e `MARK_DELIVERED` permitido.
7. Pedido pago e pedido concluído são estados diferentes.

Pedidos e pagamentos públicos exigem `Idempotency-Key` (o body é apenas fallback compatível). Repetição com o mesmo fingerprint devolve o mesmo resultado; reutilização com payload diferente conflita. Aceite, confirmação manual e fecho de turno possuem apenas guarda de estado na base e permanecem gap `CG-07`.

## 6. Estados congelados

- Pedido: `CRIADO`, `EM_ANDAMENTO`, `FINALIZADO`, `CANCELADO`.
- Subpedido/fulfilment: `CRIADO`, `PENDENTE`, `EM_PREPARACAO`, `PRONTO`, `ENTREGUE`, `CANCELADO`.
- Financeiro do pedido: `NAO_PAGO`, `PENDENTE_PAGAMENTO`, `PAGO`, `ESTORNADO`.
- Pagamento: `PENDENTE`, `PROCESSANDO`, `APROVADO`, `RECUSADO`, `CANCELADO`, `ESTORNADO`.
- Gateway: `PENDENTE`, `CONFIRMADO`, `FALHOU`, `ESTORNADO`.
- Turno: `ABERTO`, `EM_FECHO`, `FECHADO`, `CANCELADO`.

As transições e actores constam do manifesto. Não se adiciona estado sem nova versão contratual.

## 7. Turnos, catálogo e menu público

O turno aberto é gate de vendas onde `OperacaoConfig` o determina. A sequência de fecho é `ABERTO -> EM_FECHO -> FECHADO`; fecho exige revalidação e bloqueadores resolvidos. Fecho forçado não é endpoint do backend-base e não integra este contrato.

Produto aparece no menu quando pertence ao tenant do QR, está activo/disponível, satisfaz a elegibilidade de catálogo e o cardápio está publicado. A mutação operacional é `POST /tenant/cardapio/publicar`; a leitura comprovadora é `GET /public/q/{token}/cardapio`.

PONTO público está em escopo. REST público e REST mesa são `PRESERVED_NOT_RELEASED`.

## 8. OTP, gateway, devices e documentos

OTP/recovery possuem handlers e regras lógicas reais, mas o provider de produção não foi comprovado. Gateway AppyPay possui callback assinado e máquina lógica; configuração-base usa mock e não prova liquidação real. Devices são opcionais ao browser PONTO inicial. `/tenant/fiscal/documents` produz documento técnico interno; `DisabledOfficialFiscalClient` e `FakeOfficialFiscalSigningService` impedem promessa fiscal oficial.

## 9. Correlation ID e suporte

`X-Correlation-Id` é opcional no contrato presente porque a cobertura não é uniforme. A meta do Passo 9 é gerar quando ausente, retornar ao cliente e propagar com tenant seguro nos logs. O identificador pode ser mostrado ao utilizador como referência de suporte, sem claims nem dados pessoais.

## 10. Branches históricas

Não se faz merge nem cherry-pick integral. Código já ancestral da main é `KEEP`. Módulos operacionais úteis em `ui/tenant-operations-001`, `ui/pdv-invoice-delivery-001` e `ui/app-ops-safe-visuals-001` são candidatos `PORT` por ficheiro/símbolo, migrando para `authScope: "TENANT"`, `/app/ponto/**` e este contrato. Rotas antigas, headers manuais, stores de token e autoridade de estado local são `DROP`/`DEPRECATE`.

Branches backend posteriores à base (`backend/pdv-order-creation-001`, `backend/pdv-payment-integrity-001`, `backend/pdv-invoice-delivery-001`) não são contrato vigente. Os respectivos commits serão portados ou reimplementados somente nos passos responsáveis, com migrations e testes próprios.

## 11. Gaps e mudança

Os gaps `CG-01` a `CG-08` constam do manifesto. Um gap esperado não é PASS funcional. Qualquer mudança em path, DTO, auth, header, enum, transição, erro ou invariável exige nova versão e sincronização byte-a-byte do snapshot frontend.

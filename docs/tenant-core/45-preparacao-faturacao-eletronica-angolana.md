# Prompt 45 — Preparação para faturação eletrónica angolana (AGT-ready architecture)

## Objetivo
Preparar a CONSUMA para futura integração com **faturação eletrónica angolana** (AGT/MINFIN), **sem**:
- afirmar certificação;
- fazer submissão real;
- usar endpoints/credenciais reais;
- quebrar WORM/Evidence Bundle;
- acoplar pagamento/caixa à integração oficial.

O Prompt 45 cria uma camada “AGT-ready” **desativada por padrão**, com:
- perfil oficial por tenant;
- modelo de submissão oficial (requestId/estados/tentativas);
- mapeamento `FiscalDocument` → payload oficial **abstrato**;
- assinatura “placeholder” testável (sem chave real);
- worker/job desativado por padrão;
- integração no `taxEvidence` (Evidence Bundle) para bundles novos.

## Escopo implementado
- `TenantOfficialFiscalProfile` (configuração oficial futura por tenant).
- `FiscalSigningProfile` (referência de signing, sem armazenar chave privada).
- `OfficialFiscalSubmission` e `OfficialFiscalSubmissionAttempt` (modelo de submissão/tentativas).
- `OfficialFiscalDocumentMapper` + `OfficialFiscalPayloadCanonicalService` (payload + hash determinístico).
- `OfficialFiscalSigningService` (implementação fake/placeholder).
- `OfficialFiscalClient` (implementação desativada/placeholder).
- `OfficialFiscalSubmissionService`:
  - criação idempotente de submissão para documento interno `ISSUED`;
  - simulação SANDBOX (submit/accept/reject) quando habilitado por properties;
  - retry/cancel;
  - preview do payload.
- Listener AFTER_COMMIT:
  - emite evento no `FiscalDocumentService` ao emitir documento interno;
  - listener decide criação automática **apenas** se `submissionMode=AUTO_AFTER_INTERNAL_ISSUE`.
- `TaxEvidenceService` enriquecido com estado oficial por documento/correção e métricas agregadas.

## Não escopo (mantido)
- Submissão real à AGT.
- Certificação do software produtor.
- Gestão/armazenamento de chave privada em base.
- JWS real com certificados reais.
- SAF‑T/QR fiscal oficial/assinatura oficial.
- Emissão fiscal offline.
- Recalcular Evidence Bundles antigos.
- Alterar AppyPay / confirmação de pagamento / caixa / divergências.

## Separação de camadas (princípio)
`FiscalDocument` interno (`ISSUED`) **não** é documento oficial aceito.

Fluxo esperado:
`FiscalDocument` interno → `OfficialFiscalSubmission` → (mapper + hash + signing) → client oficial → `ACCEPTED/REJECTED` → `taxEvidence`.

O core fiscal interno continua operando mesmo com oficial desativado.

## Entidades principais
- `TenantOfficialFiscalProfile`
  - `officialEnabled`, `environment`, `submissionMode`, `authority`, `signingProfile`, metadados (taxpayer/software).
- `FiscalSigningProfile`
  - `keyProvider`, `publicKeyFingerprint`, `algorithm` (sem chave privada em claro).
- `OfficialFiscalSubmission`
  - `status`, `requestId`, `payloadHash`, `signedPayloadHash`, timestamps e lock/retry.
- `OfficialFiscalSubmissionAttempt`
  - trilha de tentativas (hashes/status).

## Estados (MVP)
`OfficialFiscalSubmissionStatus` cobre:
- `DRAFT`, `SIGNED`, `SUBMITTED`, `PENDING_RESULT`, `ACCEPTED`, `REJECTED`,
- `FAILED_RETRYABLE`, `FAILED_PERMANENT`, `CANCELLED`, `SKIPPED`.

## Segurança (MVP)
- `OfficialFiscalSigningService` é **placeholder**: não cria JWS real.
- `PRODUCTION` é bloqueado se `consuma.fiscal.official.allow-production=false`.
- Simulação é bloqueada em `PRODUCTION`.
- Nunca persistir/auditar chave privada ou JWS completo.

## Endpoints (tenant/admin)
Base: `/tenant/fiscal/official`

- `GET /profile`
- `POST /profile`
- `PUT /profile`

- `GET /submissions`
- `GET /submissions/{submissionId}`
- `POST /submissions/create-for-document/{documentId}`
- `POST /submissions/{submissionId}/retry`
- `POST /submissions/{submissionId}/cancel`
- `POST /submissions/{submissionId}/simulate-submit`
- `POST /submissions/{submissionId}/simulate-accept`
- `POST /submissions/{submissionId}/simulate-reject`
- `GET /submissions/payload-preview?documentId=...`

Roles: `TENANT_OWNER`, `TENANT_ADMIN`, `TENANT_FINANCE`.

## Endpoints (device/POS)
Somente leitura:
- `GET /device/fiscal/official-status/document/{documentId}`

Capability: `VIEW_FISCAL_DOCUMENT`.

## taxEvidence (Evidence Bundle)
`TaxEvidenceSectionDTO` e `TaxEvidenceDocumentItemDTO` passam a incluir campos “official” (placeholder), por documento:
- `officialSubmissionId`, `officialSubmissionStatus`, `officialRequestId`,
- `officialStatusCode`, `officialStatusMessage`,
- `officialAcceptedAt`, `officialRejectedAt`,
- `officialPayloadHash`, `officialSignedPayloadHash`.

E métricas agregadas:
- `totalOfficialSubmissions`, `accepted/rejected/pending/failed`,
- `documentsIssuedNotSubmittedOfficially`,
- warnings (ex.: `FISCAL_DOCUMENT_NOT_SUBMITTED_OFFICIALLY` quando officialEnabled=true).

Bundles antigos continuam legíveis (sem recalcular).

## Properties (defaults)
- `consuma.fiscal.official.enabled=false`
- `consuma.fiscal.official.worker-enabled=false`
- `consuma.fiscal.official.allow-production=false`
- `consuma.fiscal.official.simulation-enabled=false`

## Auditoria (sanitizada)
Eventos (ver `OperationalEventType`):
- `OFFICIAL_FISCAL_PROFILE_CREATED/UPDATED`
- `OFFICIAL_FISCAL_SUBMISSION_CREATED`
- `OFFICIAL_FISCAL_PAYLOAD_SIGNED`
- `OFFICIAL_FISCAL_SIMULATION_ACCEPTED/REJECTED`

Sem payload bruto sensível / sem chaves.

## Comandos executados
- `mvn -q -DskipTests compile`
- `mvn test`

## Próximos passos recomendados (fora do escopo)
- Implementar client real (quando houver documentação/credenciais/certificação), mantendo adapter.
- Implementar assinatura JWS/RS256 real via KMS/HSM (sem chave em DB).
- Implementar polling/callback real com requestId.
- Implementar políticas/fluxos de re-submissão após rejeição.


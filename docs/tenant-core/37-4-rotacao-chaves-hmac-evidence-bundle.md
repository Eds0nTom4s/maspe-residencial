# Prompt 37.4 — Rotação de chaves HMAC + Evidence Bundle do snapshot financeiro

## Objetivo

1) Permitir rotação segura de chaves HMAC para assinatura do snapshot financeiro:
- uma chave **ACTIVE** para assinar novos snapshots;
- chaves **DEPRECATED** para verificar snapshots antigos;
- chaves **DISABLED** não verificam e não assinam;
- verificação é feita usando o `signatureKeyId` persistido no snapshot.

2) Expor um **Evidence Bundle** (JSON lógico) para auditoria operacional:
- snapshot congelado + integridade + verificação
- metadados do turno/tenant/unidade
- eventos operacionais relevantes do turno

Sem PDF/Excel/CSV e sem recálculo financeiro.

## Keyring HMAC (properties)

Configuração recomendada:

- `consuma.financeiro.snapshot-integridade.active-key-id=platform-snapshot-key-v2`
- `consuma.financeiro.snapshot-integridade.keys.<keyId>.status=ACTIVE|DEPRECATED|DISABLED`
- `consuma.financeiro.snapshot-integridade.keys.<keyId>.secret=${ENV_SECRET}`

Regras:
- exatamente 1 chave ACTIVE;
- ACTIVE assina e verifica;
- DEPRECATED verifica, mas não deve assinar novos snapshots;
- DISABLED não assina e não verifica;
- em `prod`, o boot falha se o keyring for inválido ou secrets forem ausentes/curtos.

## Assinatura e verificação

- `snapshotHash`: SHA-256 do JSON canônico do snapshot (sem `integridade`).
- `snapshotSignature`: HMAC-SHA256(secretDaChave, snapshotHash).

No export:
- valida hash
- valida assinatura usando `signatureKeyId` do snapshot e o keyring atual
- retorna motivos claros (`KEY_NOT_FOUND`, `KEY_DISABLED`, `SIGNATURE_MISMATCH`, etc.)

## Evidence Bundle (JSON lógico)

Endpoint:

`GET /tenant/operacao/turnos/{turnoId}/snapshot/evidence-bundle`

Conteúdo:
- snapshot exportado (snapshot + integridade + verificação)
- metadados do turno/tenant/instituição/unidade
- eventos operacionais relevantes (filtrados por tipo)
- resumo de pagamentos derivado do snapshot (sem recálculo)

O bundle é “evidência”, não novo cálculo.

## Auditoria

Eventos adicionais:
- `SNAPSHOT_FINANCEIRO_EVIDENCE_BUNDLE_EXPORTADO`
- `SNAPSHOT_FINANCEIRO_KEY_ID_DESCONHECIDO`
- `SNAPSHOT_FINANCEIRO_KEY_DISABLED`

Sem secrets, sem payload bruto de gateway.

## Limitações

- Não é assinatura certificada.
- Não substitui controle de acesso ao banco.
- Não é conciliação bancária, settlement ou documento fiscal.


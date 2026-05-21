# Prompt 37.5/37.6 — Evidence Bundle Persistido + Cadeia de Custódia + Retenção/WORM Lógico

## Objetivo
Transformar o **Evidence Bundle** (antes apenas exportável sob demanda) em **evidência persistida e verificável**, com:

- persistência interna append-only;
- hash canônico (SHA-256);
- assinatura HMAC (keyring com rotação por `keyId`);
- **cadeia de custódia** (encadeamento por turno);
- retenção e WORM lógico (sem delete e sem update destrutivo via API);
- auditoria sanitizada + access log.

## Conceitos (4 níveis)
1. **Relatório** (recalculável): visão operacional atual do turno.
2. **Snapshot financeiro** (congelado no fecho): `turnos_operacionais.resumo_json.financeiro`.
3. **Evidence bundle lógico** (exportável): snapshot + metadados + eventos sanitizados.
4. **Evidence bundle persistido** (WORM lógico): bundle salvo em tabela própria + integridade + cadeia.

## Tabelas
- `turno_evidence_bundles`: evidência persistida (bundle JSON + integridade + cadeia).
- `turno_evidence_bundle_access_logs`: trilha de acessos (CREATED/EXPORTED/VERIFIED).

## Cadeia de custódia
Para cada `turnoId`:
- `sequenceNumber` cresce a cada persistência (1..N).
- `previousBundleHash` referencia o bundle anterior.
- `chainHash` é calculado sobre um JSON canônico com `{tenantId, turnoId, sequenceNumber, previousBundleHash, bundleHash, canonicalizationVersion}`.
- `chainSignature` assina o `chainHash` via HMAC com a chave ACTIVE.

## Integridade do bundle
- `bundleHash`: SHA-256 do JSON canônico do bundle persistido.
- `bundleSignature`: HMAC do `bundleHash` (com `signatureKeyId`).
- Verificação recalcula `bundleHash` e valida assinatura e cadeia.

## Retenção / WORM lógico (MVP)
- `wormLocked=true` por padrão.
- `retentionUntil = generatedAt + defaultRetentionDays` (padrão 1825 dias = 5 anos).
- Não existe endpoint de delete nesta fase.
- A API não oferece update do bundle persistido (apenas leitura/verificação e geração de novo bundle em sequência).

## Endpoints
- `POST /tenant/operacao/turnos/{turnoId}/snapshot/evidence-bundles`
  - Gera e persiste um novo bundle (apenas OWNER/ADMIN/FINANCE).
- `GET /tenant/operacao/turnos/{turnoId}/snapshot/evidence-bundles`
  - Lista bundles persistidos (OWNER/ADMIN/FINANCE/CASHIER).
- `GET /tenant/operacao/turnos/{turnoId}/snapshot/evidence-bundles/{bundleId}`
  - Retorna bundle completo persistido + verificação atual.
- `POST /tenant/operacao/turnos/{turnoId}/snapshot/evidence-bundles/{bundleId}/verify`
  - Revalida hash/assinaturas/cadeia e retorna resultado.

## Limitações
- Não é WORM físico (S3 Object Lock/MinIO) nesta fase.
- Não é documento fiscal, não é conciliação bancária, não é settlement.
- Não exporta PDF/Excel/CSV.
- Não corrige automaticamente bundles inválidos.

## Próximo passo recomendado
- Storage externo WORM (opcional) e trilha externa (WORM/WAL/immutability) para hardening de auditoria.


# Prompt 37.3 — Assinatura lógica HMAC do snapshot financeiro

## Objetivo

Reforçar a integridade do snapshot financeiro do turno contra adulteração intencional com acesso ao banco.

O Prompt 37.2 já garante:
- hash SHA-256 do snapshot (tamper-evident, mas recomputável por quem edita DB)

O Prompt 37.3 adiciona:
- assinatura lógica HMAC-SHA256 sobre o `snapshotHash` usando um segredo **fora do banco**.

Assim, mesmo que alguém altere o snapshot e recompute o SHA-256 no banco, sem o segredo HMAC a assinatura não confere.

## Diferença entre hash e HMAC

- **SHA-256 do snapshot:** prova consistência do conteúdo, mas não prova autoria (qualquer um pode recomputar).
- **HMAC-SHA256 do hash:** prova que a aplicação (com segredo) validou aquele hash; sem segredo não dá para falsificar.

## O que é assinado (escopo)

Assinamos o `snapshotHash`:

`snapshotSignature = HMAC_SHA256(secret, snapshotHash)`

Justificativa:
- evita dependência de serialização;
- reusa o JSON canônico do Prompt 37.2;
- mantém assinatura estável.

## Onde é persistido

Em `TurnoOperacional.resumo_json.financeiro.integridade`:

- `hashAlgorithm`, `snapshotHash`, `canonicalizationVersion`, `hashGeneratedAt`, `hashScope`
- `signatureAlgorithm` = `HMAC-SHA256`
- `snapshotSignature` (hex lowercase)
- `signatureGeneratedAt`
- `signatureKeyId` (ex.: `platform-snapshot-key-v1`)
- `signatureScope` = `snapshotHash`

## Configuração (segredo)

Properties:

- `consuma.financeiro.snapshot-integridade.signature-enabled` (default `true`)
- `consuma.financeiro.snapshot-integridade.signature-algorithm` (default `HMAC-SHA256`)
- `consuma.financeiro.snapshot-integridade.signature-key-id` (default `platform-snapshot-key-v1`)
- `consuma.financeiro.snapshot-integridade.signature-secret` (env: `CONSUMA_SNAPSHOT_HMAC_SECRET`)

Regras:
- Nunca hardcode o segredo.
- Nunca logar o segredo.
- Nunca retornar o segredo em endpoints.
- Nunca persistir o segredo no banco.

Em `prod`, se `signature-enabled=true` e o segredo estiver ausente/curto, o boot falha.

## Export e verificação

Endpoint:

`GET /tenant/financeiro/turnos/{turnoId}/snapshot/export`

Retorna:
- `integridade` (hash + assinatura)
- `verificacao` com:
  - `hashValido`
  - `assinaturaValida`
  - `valido = hashValido && assinaturaValida` (quando assinatura habilitada)

Se assinatura desativada por configuração:
- `valido` depende apenas do hash
- `assinaturaValida` pode ser `null`

## Compatibilidade com snapshots antigos

No primeiro export:
- se não houver hash: gera hash (+ assinatura, se habilitada) e persiste (sem alterar valores)
- se houver hash válido, mas assinatura ausente: gera assinatura e persiste
- se assinatura existe e está inválida: **não sobrescreve** automaticamente; reporta e registra evento

## Auditoria

Eventos:
- `SNAPSHOT_FINANCEIRO_ASSINATURA_GERADA` (fecho e/ou on-demand)
- `SNAPSHOT_FINANCEIRO_ASSINATURA_INVALIDA`

Sem segredo e sem snapshot completo no event log.

## Limitações

- Não é assinatura digital certificada.
- Não substitui controles de acesso ao banco.
- Não é documento fiscal, settlement ou conciliação bancária.


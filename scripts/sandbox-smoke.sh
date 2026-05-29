#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT_DIR}"

if [[ -f ".env.sandbox" ]]; then
  set -a
  # shellcheck disable=SC1091
  source ".env.sandbox"
  set +a
fi

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || { echo "Missing dependency: $1" >&2; exit 2; }
}

require_cmd curl
require_cmd jq
require_cmd openssl

RUN_ID="${RUN_ID:-$(date -u +%Y%m%dT%H%M%SZ)}"
SANDBOX_API_PORT="${SANDBOX_API_PORT:-8080}"
API_BASE_DEFAULT="http://localhost:${SANDBOX_API_PORT}/api"
API_BASE="${APP_BASE_URL:-${API_BASE_DEFAULT}}"
# Conveniência: se APP_BASE_URL ficou no default antigo e a porta mudou, usar a porta atual.
if [[ -n "${APP_BASE_URL:-}" && "${APP_BASE_URL}" == "http://localhost:8080/api" && "${SANDBOX_API_PORT}" != "8080" ]]; then
  API_BASE="${API_BASE_DEFAULT}"
fi
OUT_DIR="${OUT_DIR:-/tmp/consuma-sandbox-smoke-${RUN_ID}}"

mkdir -p "${OUT_DIR}"

log() { printf "%s\n" "$*" >&2; }

curl_json() {
  local method="$1" url="$2" data="${3:-}" auth="${4:-}"
  if [[ -n "${auth}" ]]; then
    if [[ -n "${data}" ]]; then
      curl -sS -X "${method}" "${url}" -H "Content-Type: application/json" -H "Authorization: Bearer ${auth}" -d "${data}"
    else
      curl -sS -X "${method}" "${url}" -H "Authorization: Bearer ${auth}"
    fi
  else
    if [[ -n "${data}" ]]; then
      curl -sS -X "${method}" "${url}" -H "Content-Type: application/json" -d "${data}"
    else
      curl -sS -X "${method}" "${url}"
    fi
  fi
}

to_cents() {
  local v="${1:-0}"
  v="${v/,/.}"
  if [[ "${v}" == *.* ]]; then
    local whole="${v%.*}"
    local frac="${v#*.}"
    frac="${frac}00"
    frac="${frac:0:2}"
    whole="${whole:-0}"
    echo $((10#${whole} * 100 + 10#${frac}))
  else
    echo $((10#${v} * 100))
  fi
}

log "OUT_DIR=${OUT_DIR}"
log "API_BASE=${API_BASE}"

# ------------------------------------------------------------------------------
# 1) Healthchecks
# ------------------------------------------------------------------------------
log "--- healthcheck"
curl -sS -i "${API_BASE}/actuator/health" | head -n 12 | tee "${OUT_DIR}/health.http" >/dev/null
curl -sS -i "${API_BASE}/actuator/health/readiness" | head -n 12 | tee "${OUT_DIR}/readiness.http" >/dev/null || true
curl -sS -i "${API_BASE}/actuator/health/liveness" | head -n 12 | tee "${OUT_DIR}/liveness.http" >/dev/null || true

# ------------------------------------------------------------------------------
# 2) Login PLATFORM_ADMIN (seed local: admin/admin123)
# ------------------------------------------------------------------------------
SANDBOX_ADMIN_USERNAME="${SANDBOX_ADMIN_USERNAME:-admin}"
SANDBOX_ADMIN_PASSWORD="${SANDBOX_ADMIN_PASSWORD:-admin123}"

log "--- login platform admin (username=${SANDBOX_ADMIN_USERNAME})"
LOGIN_BODY="$(jq -c -n --arg u "${SANDBOX_ADMIN_USERNAME}" --arg p "${SANDBOX_ADMIN_PASSWORD}" '{username:$u,password:$p}')"
LOGIN_RESP="$(curl_json POST "${API_BASE}/auth/jwt/login" "${LOGIN_BODY}")"
echo "${LOGIN_RESP}" > "${OUT_DIR}/login.json"
PLATFORM_TOKEN="$(jq -r '.data.accessToken // empty' < "${OUT_DIR}/login.json")"
[[ -n "${PLATFORM_TOKEN}" ]] || { log "login failed: no token"; exit 1; }
log "platformToken.len=${#PLATFORM_TOKEN} platformToken.prefix=$(printf '%.12s' "${PLATFORM_TOKEN}")"

# ------------------------------------------------------------------------------
# 3) Templates: preview/provision PONTO + REST
# ------------------------------------------------------------------------------
PONTO_SLUG="ponto-sandbox-${RUN_ID,,}"
REST_SLUG="rest-sandbox-${RUN_ID,,}"

log "--- template preview PONTO (${PONTO_SLUG})"
PONTO_PREVIEW_BODY="$(jq -c -n --arg slug "${PONTO_SLUG}" '{
  tenant:{nomeNegocio:"Ponto Sandbox",slug:$slug,tipo:"VENDEDOR_RUA"},
  owner:{nome:"Owner Sandbox",telefone:"+244900000001",email:("op."+ $slug +"@example.com")},
  ponto:{entregaManual:true,allowPickup:true}
}')"
curl_json POST "${API_BASE}/platform/templates/CONSUMA_PONTO_V1/preview" "${PONTO_PREVIEW_BODY}" "${PLATFORM_TOKEN}" > "${OUT_DIR}/ponto_preview.json"

log "--- template provision PONTO (${PONTO_SLUG})"
curl_json POST "${API_BASE}/platform/templates/CONSUMA_PONTO_V1/provision" "${PONTO_PREVIEW_BODY}" "${PLATFORM_TOKEN}" > "${OUT_DIR}/ponto_provision.json"
PONTO_TENANT_ID="$(jq -r '.data.tenantId' < "${OUT_DIR}/ponto_provision.json")"
PONTO_TENANT_CODE="$(jq -r '.data.tenantCode' < "${OUT_DIR}/ponto_provision.json")"
PONTO_QR_TOKEN="$(jq -r '.data.qrPrincipal.qrToken' < "${OUT_DIR}/ponto_provision.json")"
log "PONTO tenantId=${PONTO_TENANT_ID} tenantCode=${PONTO_TENANT_CODE} qrToken.prefix=$(printf '%.12s' "${PONTO_QR_TOKEN}")"

log "--- template preview REST (${REST_SLUG})"
REST_PREVIEW_BODY="$(jq -c -n --arg slug "${REST_SLUG}" '{
  tenant:{nomeNegocio:"Restaurante Sandbox",slug:$slug,tipo:"RESTAURANTE"},
  owner:{nome:"Owner Restaurante",telefone:"+244911111111",email:("or."+ $slug +"@example.com")},
  rest:{temMesas:true,quantidadeMesas:10,gerarQrPorMesa:true,temBarSeparado:true,exigeTurnoAberto:true,entrega:"MANUAL"}
}')"
curl_json POST "${API_BASE}/platform/templates/CONSUMA_REST_V1/preview" "${REST_PREVIEW_BODY}" "${PLATFORM_TOKEN}" > "${OUT_DIR}/rest_preview.json"

log "--- template provision REST (${REST_SLUG})"
curl_json POST "${API_BASE}/platform/templates/CONSUMA_REST_V1/provision" "${REST_PREVIEW_BODY}" "${PLATFORM_TOKEN}" > "${OUT_DIR}/rest_provision.json"
REST_TENANT_ID="$(jq -r '.data.tenantId' < "${OUT_DIR}/rest_provision.json")"
REST_TENANT_CODE="$(jq -r '.data.tenantCode' < "${OUT_DIR}/rest_provision.json")"
REST_QR_TOKEN="$(jq -r '.data.qrPrincipal.qrToken' < "${OUT_DIR}/rest_provision.json")"
REST_INSTITUICAO_ID="$(jq -r '.data.instituicaoId' < "${OUT_DIR}/rest_provision.json")"
REST_UNIDADE_ID="$(jq -r '.data.unidadeAtendimentoId' < "${OUT_DIR}/rest_provision.json")"
log "REST tenantId=${REST_TENANT_ID} tenantCode=${REST_TENANT_CODE} instituicaoId=${REST_INSTITUICAO_ID} unidadeAtendimentoId=${REST_UNIDADE_ID}"

# ------------------------------------------------------------------------------
# 4) Select tenants (tenant-scoped tokens)
# ------------------------------------------------------------------------------
log "--- select tenant PONTO"
curl_json POST "${API_BASE}/auth/tenant/select" "$(jq -c -n --argjson tid "${PONTO_TENANT_ID}" '{tenantId:$tid}')" "${PLATFORM_TOKEN}" > "${OUT_DIR}/ponto_select_tenant.json"
PONTO_TENANT_TOKEN="$(jq -r '.data.accessToken' < "${OUT_DIR}/ponto_select_tenant.json")"
log "pontoTenantToken.len=${#PONTO_TENANT_TOKEN} prefix=$(printf '%.12s' "${PONTO_TENANT_TOKEN}")"

log "--- select tenant REST"
curl_json POST "${API_BASE}/auth/tenant/select" "$(jq -c -n --argjson tid "${REST_TENANT_ID}" '{tenantId:$tid}')" "${PLATFORM_TOKEN}" > "${OUT_DIR}/rest_select_tenant.json"
REST_TENANT_TOKEN="$(jq -r '.data.accessToken' < "${OUT_DIR}/rest_select_tenant.json")"
log "restTenantToken.len=${#REST_TENANT_TOKEN} prefix=$(printf '%.12s' "${REST_TENANT_TOKEN}")"

# ------------------------------------------------------------------------------
# 5) Create products (PONTO/REST) using tenant tokens
# ------------------------------------------------------------------------------
log "--- list categorias (PONTO)"
curl_json GET "${API_BASE}/tenant/categorias-produto" "" "${PONTO_TENANT_TOKEN}" > "${OUT_DIR}/ponto_categorias.json"
PONTO_CAT_ID="$(jq -r '.data[0].id' < "${OUT_DIR}/ponto_categorias.json")"

log "--- create produtos (PONTO)"
PONTO_PROD_IDS=()
for i in 1 2 3; do
  BODY="$(jq -c -n --arg cod "PONTO-${RUN_ID}-${i}" --arg nome "Produto Ponto ${i}" --argjson preco "10.00" --argjson catId "${PONTO_CAT_ID}" '{codigo:$cod,nome:$nome,preco:$preco,disponivel:true,categoriaProdutoId:$catId}')"
  curl_json POST "${API_BASE}/tenant/produtos" "${BODY}" "${PONTO_TENANT_TOKEN}" > "${OUT_DIR}/ponto_prod_${i}.json"
  PONTO_PROD_IDS+=("$(jq -r '.data.id' < "${OUT_DIR}/ponto_prod_${i}.json")")
done
log "PONTO produtos=${PONTO_PROD_IDS[*]}"

log "--- list categorias (REST)"
curl_json GET "${API_BASE}/tenant/categorias-produto" "" "${REST_TENANT_TOKEN}" > "${OUT_DIR}/rest_categorias.json"
REST_CAT_ID="$(jq -r '.data[0].id' < "${OUT_DIR}/rest_categorias.json")"

log "--- create produtos (REST)"
REST_PROD_FOOD_BODY="$(jq -c -n --arg cod "REST-FOOD-${RUN_ID}" --arg nome "Comida Sandbox" --argjson preco "25.00" --argjson catId "${REST_CAT_ID}" '{codigo:$cod,nome:$nome,preco:$preco,disponivel:true,categoriaProdutoId:$catId,tempoPreparoMinutos:10}')"
curl_json POST "${API_BASE}/tenant/produtos" "${REST_PROD_FOOD_BODY}" "${REST_TENANT_TOKEN}" > "${OUT_DIR}/rest_prod_food.json"
REST_PROD_FOOD_ID="$(jq -r '.data.id' < "${OUT_DIR}/rest_prod_food.json")"

REST_PROD_DRINK_BODY="$(jq -c -n --arg cod "REST-DRINK-${RUN_ID}" --arg nome "Bebida Sandbox" --argjson preco "7.50" --argjson catId "${REST_CAT_ID}" '{codigo:$cod,nome:$nome,preco:$preco,disponivel:true,categoriaProdutoId:$catId}')"
curl_json POST "${API_BASE}/tenant/produtos" "${REST_PROD_DRINK_BODY}" "${REST_TENANT_TOKEN}" > "${OUT_DIR}/rest_prod_drink.json"
REST_PROD_DRINK_ID="$(jq -r '.data.id' < "${OUT_DIR}/rest_prod_drink.json")"
log "REST produtos foodId=${REST_PROD_FOOD_ID} drinkId=${REST_PROD_DRINK_ID}"

# ------------------------------------------------------------------------------
# 6) Public QR: cardápio + pedido + pagamento + callback (PONTO)
# ------------------------------------------------------------------------------
log "--- public cardapio (PONTO)"
curl_json GET "${API_BASE}/public/q/${PONTO_QR_TOKEN}/cardapio" > "${OUT_DIR}/ponto_cardapio.json"

log "--- public pedido (PONTO)"
PUB_PEDIDO_BODY="$(jq -c -n --arg nome "Cliente Sandbox" --arg tel "+244900000999" --argjson p1 "${PONTO_PROD_IDS[0]}" --argjson p2 "${PONTO_PROD_IDS[1]}" '{
  clienteNome:$nome,
  clienteTelefone:$tel,
  itens:[{produtoId:$p1,quantidade:1},{produtoId:$p2,quantidade:1}]
}')"
curl -sS -X POST "${API_BASE}/public/q/${PONTO_QR_TOKEN}/pedidos" -H "Content-Type: application/json" -H "Idempotency-Key: pedido-${RUN_ID}" -d "${PUB_PEDIDO_BODY}" > "${OUT_DIR}/ponto_pedido.json"
PONTO_PEDIDO_ID="$(jq -r '.data.pedidoId' < "${OUT_DIR}/ponto_pedido.json")"
log "PONTO pedidoId=${PONTO_PEDIDO_ID}"

log "--- public iniciar pagamento (PONTO)"
PAY_BODY="$(jq -c -n '{metodoPagamento:"REF",telefone:"+244900000999"}')"
curl -sS -X POST "${API_BASE}/public/q/${PONTO_QR_TOKEN}/pedidos/${PONTO_PEDIDO_ID}/pagamentos" -H "Content-Type: application/json" -H "Idempotency-Key: pay-${RUN_ID}" -d "${PAY_BODY}" > "${OUT_DIR}/ponto_pagamento_start.json"
PAG_EXT_REF="$(jq -r '.data.externalReference' < "${OUT_DIR}/ponto_pagamento_start.json")"
PAG_VALOR="$(jq -r '.data.valor' < "${OUT_DIR}/ponto_pagamento_start.json")"
PAG_CENTS="$(to_cents "${PAG_VALOR}")"
log "PONTO pagamento externalReference=${PAG_EXT_REF} valor=${PAG_VALOR} cents=${PAG_CENTS}"

APPYPAY_WEBHOOK_SECRET="${APPYPAY_WEBHOOK_SECRET:-}"
if [[ -z "${APPYPAY_WEBHOOK_SECRET}" ]]; then
  log "WARN: APPYPAY_WEBHOOK_SECRET vazio; callback pode ser aceito apenas se signatureRequired=false (não recomendado)."
fi

log "--- callback CONFIRMED (PONTO)"
CONFIRMED_AT="$(date -u +%FT%TZ)"
CALLBACK_BODY="$(jq -c -n --arg chargeId "charge-${RUN_ID}" --arg mtid "${PAG_EXT_REF}" --arg confirmedAt "${CONFIRMED_AT}" --argjson amount "${PAG_CENTS}" '{
  chargeId:$chargeId,
  merchantTransactionId:$mtid,
  status:"CONFIRMED",
  amount:$amount,
  paymentMethod:"REF",
  confirmedAt:$confirmedAt
}')"
SIG="$(printf '%s' "${CALLBACK_BODY}" | openssl dgst -sha256 -hmac "${APPYPAY_WEBHOOK_SECRET}" -hex | awk '{print $2}')"
curl -sS -i -X POST "${API_BASE}/pagamentos/callback" -H "Content-Type: application/json" -H "X-AppyPay-Signature: ${SIG}" -d "${CALLBACK_BODY}" | head -n 20 > "${OUT_DIR}/ponto_callback_confirmed.http"

# ------------------------------------------------------------------------------
# 7) Devices: listar → activation code → activate → heartbeat/bootstrap/catalog (REST)
# ------------------------------------------------------------------------------
log "--- list dispositivos (REST)"
curl_json GET "${API_BASE}/tenant/dispositivos?page=0&size=50" "" "${REST_TENANT_TOKEN}" > "${OUT_DIR}/rest_dispositivos.json"
REST_POS_ID="$(jq -r '.data.content[] | select(.tipo=="POS") | .id' < "${OUT_DIR}/rest_dispositivos.json" | head -n 1)"
REST_KDS_ID="$(jq -r '.data.content[] | select(.tipo=="KDS") | .id' < "${OUT_DIR}/rest_dispositivos.json" | head -n 1)"
log "REST dispositivo POS.id=${REST_POS_ID} KDS.id=${REST_KDS_ID}"

log "--- activation-code POS"
curl_json POST "${API_BASE}/tenant/dispositivos/${REST_POS_ID}/activation-code" "" "${REST_TENANT_TOKEN}" > "${OUT_DIR}/pos_activation_code.json"
POS_ACTIVATION_CODE="$(jq -r '.data.activationCode' < "${OUT_DIR}/pos_activation_code.json")"

log "--- activation-code KDS"
curl_json POST "${API_BASE}/tenant/dispositivos/${REST_KDS_ID}/activation-code" "" "${REST_TENANT_TOKEN}" > "${OUT_DIR}/kds_activation_code.json"
KDS_ACTIVATION_CODE="$(jq -r '.data.activationCode' < "${OUT_DIR}/kds_activation_code.json")"

log "--- device activate POS"
POS_ACTIVATE_BODY="$(jq -c -n --arg code "${POS_ACTIVATION_CODE}" '{activationCode:$code,appVersion:"sandbox-smoke",platform:"linux"}')"
curl_json POST "${API_BASE}/device/activate" "${POS_ACTIVATE_BODY}" > "${OUT_DIR}/pos_activate.json"
POS_DEVICE_TOKEN="$(jq -r '.data.deviceToken' < "${OUT_DIR}/pos_activate.json")"
log "posDeviceToken.len=${#POS_DEVICE_TOKEN} prefix=$(printf '%.12s' "${POS_DEVICE_TOKEN}")"

log "--- device activate KDS"
KDS_ACTIVATE_BODY="$(jq -c -n --arg code "${KDS_ACTIVATION_CODE}" '{activationCode:$code,appVersion:"sandbox-smoke",platform:"linux"}')"
curl_json POST "${API_BASE}/device/activate" "${KDS_ACTIVATE_BODY}" > "${OUT_DIR}/kds_activate.json"
KDS_DEVICE_TOKEN="$(jq -r '.data.deviceToken' < "${OUT_DIR}/kds_activate.json")"
log "kdsDeviceToken.len=${#KDS_DEVICE_TOKEN} prefix=$(printf '%.12s' "${KDS_DEVICE_TOKEN}")"

log "--- device heartbeat POS"
curl -sS -X POST "${API_BASE}/device/heartbeat" -H "Authorization: Device ${POS_DEVICE_TOKEN}" -H "Content-Type: application/json" -d '{}' > "${OUT_DIR}/pos_heartbeat.json"

log "--- device bootstrap POS"
curl -sS -X GET "${API_BASE}/device/sync/bootstrap" -H "Authorization: Device ${POS_DEVICE_TOKEN}" > "${OUT_DIR}/pos_bootstrap.json"

log "--- device catalogo POS"
curl -sS -X GET "${API_BASE}/device/sync/catalogo?limit=50" -H "Authorization: Device ${POS_DEVICE_TOKEN}" > "${OUT_DIR}/pos_catalogo.json"

log "--- device heartbeat KDS"
curl -sS -X POST "${API_BASE}/device/heartbeat" -H "Authorization: Device ${KDS_DEVICE_TOKEN}" -H "Content-Type: application/json" -d '{}' > "${OUT_DIR}/kds_heartbeat.json"

# ------------------------------------------------------------------------------
# 8) Turno (REST): validar handler 400 e abrir turno com checklist automático
# ------------------------------------------------------------------------------
log "--- handler: /tenant/operacao/turnos/atual sem query params (esperado 400)"
curl -sS -i -X GET "${API_BASE}/tenant/operacao/turnos/atual" -H "Authorization: Bearer ${REST_TENANT_TOKEN}" | head -n 30 > "${OUT_DIR}/turnos_atual_sem_params.http"

log "--- checklists ABERTURA (REST)"
curl_json GET "${API_BASE}/tenant/operacao/checklists/templates?tipo=ABERTURA" "" "${REST_TENANT_TOKEN}" > "${OUT_DIR}/checklists_abertura.json"
CHECKLIST_ABERTURA="$(jq -c '[.data[].itens[] | {codigo:.codigo} + (if .tipoResposta=="BOOLEAN" then {valorBoolean:true} elif .tipoResposta=="NUMERO" then {valorNumero:0} else {valorTexto:"OK"} end)]' < "${OUT_DIR}/checklists_abertura.json")"

log "--- abrir turno (REST)"
ABRIR_TURNO_BODY="$(jq -c -n --argjson inst "${REST_INSTITUICAO_ID}" --argjson ua "${REST_UNIDADE_ID}" --arg nome "Turno Sandbox ${RUN_ID}" --argjson checklist "${CHECKLIST_ABERTURA}" '{
  instituicaoId:$inst,
  unidadeAtendimentoId:$ua,
  tipo:"DIARIO",
  nome:$nome,
  checklist:$checklist
}')"
curl_json POST "${API_BASE}/tenant/operacao/turnos/abrir" "${ABRIR_TURNO_BODY}" "${REST_TENANT_TOKEN}" > "${OUT_DIR}/turno_aberto.json"
TURNO_ID="$(jq -r '.data.id' < "${OUT_DIR}/turno_aberto.json")"
log "turnoId=${TURNO_ID}"

# ------------------------------------------------------------------------------
# 9) Pedido por POS + KDS fila (REST)
# ------------------------------------------------------------------------------
log "--- device criar pedido POS (REST)"
DEVICE_PEDIDO_BODY="$(jq -c -n --arg crid "pos-${RUN_ID}" --argjson food "${REST_PROD_FOOD_ID}" --argjson drink "${REST_PROD_DRINK_ID}" '{
  clientRequestId:$crid,
  itens:[{produtoId:$food,quantidade:1},{produtoId:$drink,quantidade:1}]
}')"
curl -sS -X POST "${API_BASE}/device/pedidos" \
  -H "Authorization: Device ${POS_DEVICE_TOKEN}" \
  -H "Idempotency-Key: pos-pedido-${RUN_ID}" \
  -H "Content-Type: application/json" \
  -d "${DEVICE_PEDIDO_BODY}" > "${OUT_DIR}/pos_pedido.json"
REST_PEDIDO_ID="$(jq -r '.data.pedidoId // .data.id' < "${OUT_DIR}/pos_pedido.json")"
log "REST pedidoId=${REST_PEDIDO_ID}"

log "--- KDS fila (REST)"
curl -sS -X GET "${API_BASE}/device/sync/producao/fila?status=CRIADO&size=50" -H "Authorization: Device ${KDS_DEVICE_TOKEN}" > "${OUT_DIR}/kds_fila.json"
SUBPEDIDOS_COUNT="$(jq -r '.data.content | length' < "${OUT_DIR}/kds_fila.json" 2>/dev/null || echo 0)"
log "kds.fila.count=${SUBPEDIDOS_COUNT}"

log "DONE. Evidências em: ${OUT_DIR}"
log "Nota: o script não fecha turno/snapshot/evidence automaticamente; ver docs/sandbox/SMOKE_TEST_SANDBOX.md para passos manuais."

package com.restaurante.financeiro.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.exception.BusinessException;
import com.restaurante.financeiro.enums.StatusPagamentoGateway;
import com.restaurante.financeiro.enums.TipoEventoFinanceiro;
import com.restaurante.financeiro.gateway.appypay.AppyPayHmacValidator;
import com.restaurante.financeiro.gateway.appypay.AppyPayStatusMapper;
import com.restaurante.financeiro.gateway.appypay.AppyPayWebhookPayload;
import com.restaurante.financeiro.repository.PagamentoEventLogRepository;
import com.restaurante.financeiro.repository.PagamentoGatewayRepository;
import com.restaurante.model.entity.Pagamento;
import com.restaurante.model.entity.PagamentoEventLog;
import com.restaurante.notificacao.service.NotificacaoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * Serviço para processar callbacks da AppyPay.
 *
 * <p>Responsabilidades:
 * <ul>
 *   <li>Validar assinatura HMAC-SHA256 do payload recebido.</li>
 *   <li>Desserializar e localizar pagamento por merchantTransactionId.</li>
 *   <li>Confirmar, falhar ou cancelar o pagamento conforme status.</li>
 *   <li>Idempotência: callbacks duplicados são silenciosamente ignorados.</li>
 *   <li>Registrar auditoria de todos os eventos.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PagamentoCallbackService {

    private final PagamentoGatewayRepository pagamentoRepository;
    private final PagamentoEventLogRepository eventLogRepository;
    private final PagamentoGatewayService pagamentoGatewayService;
    private final com.restaurante.store.service.StorePaymentService storePaymentService;
    private final NotificacaoService notificacaoService;
    private final AppyPayHmacValidator hmacValidator;
    private final ObjectMapper objectMapper;

    /**
     * Processa callback da AppyPay.
     *
     * <p>Fluxo:
     * <ol>
     *   <li>Valida assinatura HMAC-SHA256.</li>
     *   <li>Desserializa payload preservando tolerância a formatos reais da AppyPay.</li>
     *   <li>Busca pagamento por externalReference.</li>
     *   <li>Processa conforme status (CONFIRMED / FAILED / CANCELLED).</li>
     *   <li>Registra evento de auditoria.</li>
     * </ol>
     *
     * @param rawBody   corpo bruto da requisição HTTP
     * @param signature valor do header {@code X-AppyPay-Signature}
     * @throws BusinessException se assinatura inválida ou pagamento não encontrado
     */
    @Transactional
    public void processarCallback(String rawBody, String signature) {

        // 1. Validação HMAC — rejeita qualquer payload não assinado pela AppyPay
        if (!hmacValidator.validar(rawBody, signature)) {
            log.error("[CALLBACK] Assinatura HMAC inválida. Callback rejeitado.");
            throw new BusinessException("Assinatura do callback inválida.");
        }

        Map<String, Object> payload;
        try {
            payload = objectMapper.readValue(rawBody, new com.fasterxml.jackson.core.type.TypeReference<>() {});
        } catch (Exception e) {
            log.error("[CALLBACK] Erro ao desserializar payload: {}", e.getMessage());
            throw new BusinessException("Payload do callback inválido: " + e.getMessage());
        }

        String merchantTransactionId = AppyPayWebhookPayload.extractMerchantTransactionId(payload);
        String chargeId = AppyPayWebhookPayload.extractTransactionId(payload);
        String referencia = AppyPayWebhookPayload.extractReference(payload);
        String status = AppyPayWebhookPayload.extractStatus(payload);
        String amount = AppyPayWebhookPayload.extractAmount(payload);

        if (!AppyPayWebhookPayload.hasIdentifier(payload)) {
            throw new BusinessException("Payload AppyPay sem identificador de pagamento");
        }

        log.info("[CALLBACK] Processando: merchantTxId={}, chargeId={}, referencia={}, status={}",
                merchantTransactionId, mask(chargeId), mask(referencia), status);

        // 3. Busca pagamento
        Pagamento pagamento = localizarPagamento(merchantTransactionId, chargeId, referencia);

        // 4. Registra recebimento do callback
        registrarEvento(
                TipoEventoFinanceiro.CALLBACK_RECEBIDO,
                pagamento,
                "Callback AppyPay: status=" + status
                        + " | merchantTxId=" + merchantTransactionId
                        + " | chargeId=" + chargeId
                        + " | referencia=" + referencia,
                merchantTransactionId,
                chargeId,
                status);

        // 5. Processa conforme status
        StatusPagamentoGateway statusGateway = AppyPayStatusMapper.toGatewayStatus(status);
        if (statusGateway == StatusPagamentoGateway.CONFIRMADO) {
            validarValorQuandoInformado(pagamento, amount);
            confirmarPagamento(pagamento);
        } else if (statusGateway == StatusPagamentoGateway.FALHOU) {
            if (!pagamento.getStatus().isTerminal()) {
                pagamento.marcarComoFalho("Gateway: " + status);
                pagamentoRepository.save(pagamento);
                registrarEvento(
                        TipoEventoFinanceiro.PAGAMENTO_FALHOU,
                        pagamento,
                        "Pagamento falhou via callback: " + status
                                + " | merchantTxId=" + merchantTransactionId
                                + " | chargeId=" + chargeId,
                        merchantTransactionId,
                        chargeId,
                        status);
            }
        } else {
            log.info("[CALLBACK] Status pendente/desconhecido ignorado: {}", status);
        }

        log.info("[CALLBACK] Processado com sucesso: merchantTxId={}", merchantTransactionId);
    }

    // ─────────────────────────────────────────────────────────────────────────────

    private void registrarEvento(TipoEventoFinanceiro tipo, Pagamento pagamento, String motivo) {
        registrarEvento(tipo, pagamento, motivo, null, null, null);
    }

    private void registrarEvento(TipoEventoFinanceiro tipo, Pagamento pagamento, String motivo,
                                 String merchantTransactionId, String chargeId, String status) {
        PagamentoEventLog event = PagamentoEventLog.builder()
                .tipoEvento(tipo)
                .pagamento(pagamento)
                .pedido(pagamento.getPedido())
                .usuario("APPYPAY_CALLBACK")
                .role("SYSTEM")
                .ip(null)
                .motivo(motivo)
                .dadosAdicionais("{\"merchantTransactionId\":\"" + safe(merchantTransactionId)
                        + "\",\"chargeId\":\"" + safe(chargeId)
                        + "\",\"status\":\"" + safe(status) + "\"}")
                .build();
        eventLogRepository.save(event);
    }

    private Pagamento localizarPagamento(String merchantTransactionId, String chargeId, String referencia) {
        if (merchantTransactionId != null) {
            var found = pagamentoRepository.findByExternalReferenceWithLock(merchantTransactionId);
            if (found.isPresent()) return found.get();
        }
        if (chargeId != null) {
            var found = pagamentoRepository.findByGatewayChargeIdWithLock(chargeId);
            if (found.isPresent()) return found.get();
        }
        if (referencia != null) {
            var found = pagamentoRepository.findByReferenciaWithLock(referencia);
            if (found.isPresent()) return found.get();
        }
        throw new BusinessException("Pagamento AppyPay não encontrado");
    }

    private void confirmarPagamento(Pagamento pagamento) {
        if (pagamento.isConfirmado()) {
            log.info("[CALLBACK] Pagamento já confirmado anteriormente. Callback duplicado ignorado.");
            return;
        }
        if (pagamento.isPrePago()) {
            pagamentoGatewayService.confirmarPagamentoRecargaFundo(
                    pagamento.getId(),
                    "APPYPAY_CALLBACK",
                    "SYSTEM",
                    null);
        } else {
            storePaymentService.confirmStorePayment(pagamento);
        }
    }

    private void validarValorQuandoInformado(Pagamento pagamento, String amount) {
        if (amount == null || amount.isBlank()) return;
        try {
            BigDecimal recebido = new BigDecimal(amount.trim()).setScale(2, RoundingMode.HALF_UP);
            BigDecimal esperado = pagamento.getAmount().setScale(2, RoundingMode.HALF_UP);
            BigDecimal esperadoMinorUnits = esperado.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
            boolean valorValido = recebido.compareTo(esperado) == 0 || recebido.compareTo(esperadoMinorUnits) == 0;
            if (!valorValido) {
                throw new BusinessException("Valor do callback AppyPay diverge do pagamento local");
            }
        } catch (NumberFormatException e) {
            throw new BusinessException("Valor do callback AppyPay inválido");
        }
    }

    private String safe(String value) {
        return value == null ? "" : value.replace("\"", "\\\"");
    }

    private String mask(String value) {
        if (value == null || value.length() <= 6) return "***";
        return value.substring(0, 3) + "***" + value.substring(value.length() - 3);
    }
}

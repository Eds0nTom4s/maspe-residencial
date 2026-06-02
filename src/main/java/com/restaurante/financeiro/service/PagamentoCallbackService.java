package com.restaurante.financeiro.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.financeiro.enums.TipoEventoFinanceiro;
import com.restaurante.financeiro.exception.InvalidCallbackSignatureException;
import com.restaurante.financeiro.gateway.appypay.AppyPayHmacValidator;
import com.restaurante.financeiro.gateway.appypay.dto.AppyPayCallback;
import com.restaurante.financeiro.repository.PagamentoCallbackLogRepository;
import com.restaurante.financeiro.repository.PagamentoEventLogRepository;
import com.restaurante.financeiro.repository.PagamentoGatewayRepository;
import com.restaurante.financeiro.polling.PagamentoConfirmacaoService;
import com.restaurante.model.entity.Pagamento;
import com.restaurante.model.entity.PagamentoCallbackLog;
import com.restaurante.model.entity.PagamentoEventLog;
import com.restaurante.model.entity.Pedido;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.enums.CallbackProcessingStatus;
import com.restaurante.model.enums.StatusFinanceiroPedido;
import com.restaurante.repository.PedidoRepository;
import com.restaurante.service.SessaoConsumoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Processa callbacks (webhooks) do gateway AppyPay.
 *
 * <p>Requisitos críticos:
 * <ul>
 *   <li>Segurança: validação HMAC/secret antes de alterar estado financeiro.</li>
 *   <li>Auditoria: persistir log bruto do callback para suporte/reconciliação.</li>
 *   <li>Idempotência: callbacks duplicados não podem confirmar duas vezes.</li>
 *   <li>Concorrência: lock pessimista por externalReference.</li>
 *   <li>Tenant safety: pagamento deve ter tenant e (se houver pedido) tenant deve bater.</li>
 *   <li>Validação de valor: amount recebido (centavos) deve bater com pagamento.amount.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PagamentoCallbackService {

    private final PagamentoGatewayRepository pagamentoRepository;
    private final PagamentoEventLogRepository eventLogRepository;
    private final PagamentoCallbackLogRepository callbackLogRepository;
    private final PagamentoGatewayService pagamentoGatewayService;
    private final AppyPayHmacValidator hmacValidator;
    private final ObjectMapper objectMapper;
    private final PedidoRepository pedidoRepository;
    private final PagamentoConfirmacaoService pagamentoConfirmacaoService;

    // Injectado via setter para quebrar ciclo SessaoConsumoService ↔ PagamentoCallbackService
    private SessaoConsumoService sessaoConsumoService;

    @org.springframework.beans.factory.annotation.Autowired
    @org.springframework.context.annotation.Lazy
    public void setSessaoConsumoService(SessaoConsumoService sessaoConsumoService) {
        this.sessaoConsumoService = sessaoConsumoService;
    }

    @Transactional(noRollbackFor = InvalidCallbackSignatureException.class)
    public void processarCallback(String rawBody, String signature) {
        processarCallback(rawBody, signature, Map.of());
    }

    @Transactional(noRollbackFor = InvalidCallbackSignatureException.class)
    public void processarCallback(String rawBody, String signature, Map<String, String> headers) {
        PagamentoCallbackLog callbackLog = criarLogRecebido(rawBody, headers);

        // 1) Validação HMAC (assinatura/secret)
        if (!hmacValidator.validar(rawBody, signature)) {
            callbackLog.setSignatureValid(false);
            finalizarLog(callbackLog, CallbackProcessingStatus.INVALID_SIGNATURE, null);
            throw new InvalidCallbackSignatureException("Assinatura do callback inválida.");
        }
        callbackLog.setSignatureValid(true);
        callbackLogRepository.save(callbackLog);

        // 2) Parse do payload
        AppyPayCallback callback;
        try {
            callback = objectMapper.readValue(rawBody, AppyPayCallback.class);
        } catch (Exception e) {
            finalizarLog(callbackLog, CallbackProcessingStatus.FAILED, "Payload inválido: " + e.getMessage());
            return;
        }

        callbackLog.setExternalReference(callback.getMerchantTransactionId());
        callbackLog.setGatewayChargeId(callback.getChargeId());
        callbackLog.setStatusRecebido(callback.getStatus());
        callbackLog.setPayloadJson(serializeSilently(callback));
        callbackLogRepository.save(callbackLog);

        // 3) Resolver pagamento por externalReference, com lock pessimista
        Pagamento pagamento = pagamentoRepository
                .findForUpdateByExternalReference(callback.getMerchantTransactionId())
                .orElse(null);

        if (pagamento == null) {
            finalizarLog(callbackLog, CallbackProcessingStatus.PAYMENT_NOT_FOUND, null);
            return;
        }

        Tenant tenant = pagamento.getTenant();
        callbackLog.setPagamento(pagamento);
        callbackLog.setTenant(tenant);
        callbackLogRepository.save(callbackLog);

        if (tenant == null) {
            finalizarLog(callbackLog, CallbackProcessingStatus.FAILED, "Pagamento sem tenant vinculado.");
            return;
        }

        // Defesa em profundidade: se for pagamento de pedido, tenant deve bater
        if (pagamento.getPedido() != null && pagamento.getPedido().getTenant() != null) {
            if (!pagamento.getPedido().getTenant().getId().equals(tenant.getId())) {
                finalizarLog(callbackLog, CallbackProcessingStatus.FAILED, "Tenant mismatch: pagamento.tenant != pedido.tenant");
                return;
            }
        }

        // 4) Auditoria de recebimento
        registrarEvento(TipoEventoFinanceiro.CALLBACK_RECEBIDO, pagamento, "Callback AppyPay: status=" + callback.getStatus());

        // 5) Processamento idempotente por status do gateway
        switch (callback.getStatus()) {
            case "CONFIRMED" -> processarConfirmed(callbackLog, callback, pagamento);
            case "FAILED", "CANCELLED" -> processarFailedOrCancelled(callbackLog, callback, pagamento);
            default -> finalizarLog(callbackLog, CallbackProcessingStatus.FAILED, "Status desconhecido: " + callback.getStatus());
        }
    }

    private void processarConfirmed(PagamentoCallbackLog callbackLog, AppyPayCallback callback, Pagamento pagamento) {
        if (pagamento.isConfirmado()) {
            finalizarLog(callbackLog, CallbackProcessingStatus.IGNORED_DUPLICATE, null);
            return;
        }

        // Validação de valor (amount vem em centavos)
        if (callback.getAmount() != null) {
            long expectedCents = toCentavos(pagamento.getAmount());
            if (!callback.getAmount().equals(expectedCents)) {
                finalizarLog(callbackLog, CallbackProcessingStatus.FAILED,
                        "Valor divergente. Esperado=" + expectedCents + " recebido=" + callback.getAmount());
                return;
            }
        }

        if (pagamento.isPrePago()) {
            pagamentoGatewayService.confirmarPagamentoRecargaFundo(pagamento.getId(), "APPYPAY_CALLBACK", "SYSTEM", null);
            if (pagamento.getFundoConsumo() != null && pagamento.getFundoConsumo().getSessaoConsumo() != null) {
                sessaoConsumoService.registrarAtividade(
                        pagamento.getFundoConsumo().getSessaoConsumo().getId(),
                        "Pagamento AppyPay confirmado: " + callback.getMerchantTransactionId());
            }
        } else if (pagamento.isPosPago()) {
            confirmarPagamentoPedidoQr(pagamento);
        } else {
            finalizarLog(callbackLog, CallbackProcessingStatus.FAILED, "Tipo de pagamento não suportado no callback: " + pagamento.getTipoPagamento());
            return;
        }

        finalizarLog(callbackLog, CallbackProcessingStatus.PROCESSED, null);
    }

    private void processarFailedOrCancelled(PagamentoCallbackLog callbackLog, AppyPayCallback callback, Pagamento pagamento) {
        if (!pagamento.getStatus().isTerminal()) {
            pagamento.marcarComoFalho("Gateway: " + callback.getStatus());
            pagamentoRepository.save(pagamento);
            registrarEvento(TipoEventoFinanceiro.PAGAMENTO_FALHOU, pagamento, "Pagamento falhou via callback: " + callback.getStatus());
        }
        finalizarLog(callbackLog, CallbackProcessingStatus.PROCESSED, null);
    }

    private void confirmarPagamentoPedidoQr(Pagamento pagamento) {
        Long paymentId = pagamento.getId();
        if (paymentId == null) throw new IllegalStateException("Pagamento inválido.");
        pagamentoConfirmacaoService.confirmarPosPagoPorGateway(
                paymentId,
                "APPYPAY_CALLBACK",
                "SYSTEM",
                null,
                null
        );
    }

    private PagamentoCallbackLog criarLogRecebido(String rawBody, Map<String, String> headers) {
        PagamentoCallbackLog logEntity = new PagamentoCallbackLog();
        logEntity.setProvider("APPYPAY");
        logEntity.setRawBody(rawBody);
        logEntity.setHeadersJson(serializeSilently(headers));
        logEntity.setProcessingStatus(CallbackProcessingStatus.RECEIVED);
        logEntity.setProcessed(false);
        logEntity.setReceivedAt(LocalDateTime.now());
        return callbackLogRepository.save(logEntity);
    }

    private void finalizarLog(PagamentoCallbackLog logEntity, CallbackProcessingStatus status, String error) {
        logEntity.setProcessingStatus(status);
        logEntity.setProcessed(true);
        logEntity.setProcessedAt(LocalDateTime.now());
        logEntity.setProcessingError(error);
        callbackLogRepository.save(logEntity);
    }

    private void registrarEvento(TipoEventoFinanceiro tipo, Pagamento pagamento, String motivo) {
        PagamentoEventLog event = PagamentoEventLog.builder()
                .tipoEvento(tipo)
                .pagamento(pagamento)
                .pedido(pagamento.getPedido())
                .usuario("APPYPAY_CALLBACK")
                .role("SYSTEM")
                .ip(null)
                .motivo(motivo)
                .build();
        eventLogRepository.save(event);
    }

    private String serializeSilently(Object value) {
        if (value == null) return null;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return null;
        }
    }

    private static long toCentavos(BigDecimal amount) {
        if (amount == null) return 0L;
        return amount
                .movePointRight(2)
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
    }
}

package com.restaurante.financeiro.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.exception.BusinessException;
import com.restaurante.financeiro.enums.TipoEventoFinanceiro;
import com.restaurante.financeiro.gateway.appypay.AppyPayHmacValidator;
import com.restaurante.financeiro.gateway.appypay.dto.AppyPayCallback;
import com.restaurante.financeiro.repository.PagamentoEventLogRepository;
import com.restaurante.financeiro.repository.PagamentoGatewayRepository;
import com.restaurante.model.entity.Pagamento;
import com.restaurante.model.entity.PagamentoEventLog;
import com.restaurante.notificacao.service.NotificacaoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final NotificacaoService notificacaoService;
    private final AppyPayHmacValidator hmacValidator;
    private final ObjectMapper objectMapper;

    /**
     * Processa callback da AppyPay.
     *
     * <p>Fluxo:
     * <ol>
     *   <li>Valida assinatura HMAC-SHA256.</li>
     *   <li>Desserializa payload em {@link AppyPayCallback}.</li>
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

        // 2. Desserializa payload
        AppyPayCallback callback;
        try {
            callback = objectMapper.readValue(rawBody, AppyPayCallback.class);
        } catch (Exception e) {
            log.error("[CALLBACK] Erro ao desserializar payload: {}", e.getMessage());
            throw new BusinessException("Payload do callback inválido: " + e.getMessage());
        }

        log.info("[CALLBACK] Processando: merchantTxId={}, status={}",
                callback.getMerchantTransactionId(), callback.getStatus());

        // 3. Busca pagamento
        Pagamento pagamento = pagamentoRepository
                .findByExternalReference(callback.getMerchantTransactionId())
                .orElseThrow(() -> new BusinessException(
                        "Pagamento não encontrado: " + callback.getMerchantTransactionId()));

        // 4. Registra recebimento do callback
        registrarEvento(
                TipoEventoFinanceiro.CALLBACK_RECEBIDO,
                pagamento,
                "Callback AppyPay: status=" + callback.getStatus());

        // 5. Processa conforme status
        switch (callback.getStatus()) {
            case "CONFIRMED" -> {
                if (!pagamento.isConfirmado()) {
                    if (pagamento.isPrePago()) {
                        pagamentoGatewayService.confirmarPagamentoRecargaFundo(
                                pagamento.getId(),
                                "APPYPAY_CALLBACK",
                                "SYSTEM",
                                null);
                    } else {
                        // TODO: Implementar confirmação pós-pago
                        log.warn("[CALLBACK] Callback para pós-pago ainda não implementado.");
                    }
                } else {
                    log.info("[CALLBACK] Pagamento já confirmado anteriormente. Callback duplicado ignorado.");
                }
            }
            case "FAILED", "CANCELLED" -> {
                if (!pagamento.getStatus().isTerminal()) {
                    pagamento.marcarComoFalho("Gateway: " + callback.getStatus());
                    pagamentoRepository.save(pagamento);
                    registrarEvento(
                            TipoEventoFinanceiro.PAGAMENTO_FALHOU,
                            pagamento,
                            "Pagamento falhou via callback: " + callback.getStatus());
                }
            }
            default -> log.warn("[CALLBACK] Status desconhecido: {}", callback.getStatus());
        }

        log.info("[CALLBACK] Processado com sucesso: merchantTxId={}",
                callback.getMerchantTransactionId());
    }

    // ─────────────────────────────────────────────────────────────────────────────

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
}

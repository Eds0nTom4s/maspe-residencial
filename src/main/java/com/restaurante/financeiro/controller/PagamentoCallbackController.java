package com.restaurante.financeiro.controller;

import com.restaurante.financeiro.gateway.appypay.dto.AppyPayCallback;
import com.restaurante.financeiro.service.PagamentoCallbackService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller para callbacks do gateway AppyPay
 * 
 * ENDPOINT PÚBLICO (sem autenticação JWT)
 * Validação via signature HMAC
 * 
 * AppyPay chama POST /api/pagamentos/callback quando:
 * - Pagamento REF é confirmado
 * - Pagamento falha
 * - Pagamento é cancelado
 */
@RestController
@RequestMapping("/api/pagamentos")
@RequiredArgsConstructor
@Slf4j
public class PagamentoCallbackController {
    
    private final PagamentoCallbackService callbackService;
    
    /**
     * Processa callback da AppyPay
     * 
     * IDEMPOTENTE: callbacks duplicados são ignorados
     * SEGURO: valida signature HMAC
     * 
     * @param callback Dados do callback
     * @return 200 OK (sempre, mesmo se já processado)
     */
    @PostMapping("/callback")
    public ResponseEntity<Void> processarCallback(@RequestBody AppyPayCallback callback) {
        log.info("Callback recebido: merchantTxId={}, status={}", 
            callback.getMerchantTransactionId(), 
            callback.getStatus());
        
        try {
            callbackService.processarCallback(callback);
            return ResponseEntity.ok().build();
            
        } catch (Exception e) {
            log.error("Erro ao processar callback: {}", e.getMessage(), e);
            // Retorna 200 mesmo com erro para evitar retry infinito do gateway
            return ResponseEntity.ok().build();
        }
    }
}

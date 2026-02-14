package com.restaurante.financeiro.gateway.appypay.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Callback da AppyPay quando status de pagamento muda
 * 
 * BASEADO EM ARENATICKET (VALIDADO EM PRODUÇÃO)
 * 
 * AppyPay envia POST para webhookUrl configurada
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppyPayCallback {
    
    /**
     * ID da cobrança
     */
    @JsonProperty("chargeId")
    private String chargeId;
    
    /**
     * Merchant Transaction ID
     * Usar para localizar pagamento no sistema
     */
    @JsonProperty("merchantTransactionId")
    private String merchantTransactionId;
    
    /**
     * Novo status
     * CONFIRMED, FAILED, CANCELLED
     */
    @JsonProperty("status")
    private String status;
    
    /**
     * Valor confirmado
     */
    @JsonProperty("amount")
    private Long amount;
    
    /**
     * Método de pagamento
     */
    @JsonProperty("paymentMethod")
    private String paymentMethod;
    
    /**
     * Data de confirmação
     * ISO 8601
     */
    @JsonProperty("confirmedAt")
    private String confirmedAt;
    
    /**
     * Assinatura HMAC para validação
     * Verificar com webhookSecret
     */
    @JsonProperty("signature")
    private String signature;
}

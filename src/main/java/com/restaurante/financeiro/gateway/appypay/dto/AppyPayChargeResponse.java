package com.restaurante.financeiro.gateway.appypay.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response da criação de cobrança na AppyPay
 * 
 * BASEADO EM ARENATICKET (VALIDADO EM PRODUÇÃO)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppyPayChargeResponse {
    
    /**
     * ID da cobrança gerado pela AppyPay
     * Usar para consultas futuras
     */
    @JsonProperty("chargeId")
    private String chargeId;
    
    /**
     * Merchant Transaction ID (eco do request)
     */
    @JsonProperty("merchantTransactionId")
    private String merchantTransactionId;
    
    /**
     * Status da cobrança
     * PENDING, CONFIRMED, FAILED, CANCELLED
     */
    @JsonProperty("status")
    private String status;
    
    /**
     * Valor em Kwanzas (eco)
     */
    @JsonProperty("amount")
    private Long amount;
    
    /**
     * Método de pagamento (eco)
     */
    @JsonProperty("paymentMethod")
    private String paymentMethod;
    
    /**
     * Entidade bancária (apenas REF)
     * Exemplo: "10100" (BAI)
     */
    @JsonProperty("entity")
    private String entity;
    
    /**
     * Referência de pagamento (apenas REF)
     * Exemplo: "999 123 456"
     */
    @JsonProperty("reference")
    private String reference;
    
    /**
     * Data de validade da referência (apenas REF)
     * ISO 8601
     */
    @JsonProperty("expiresAt")
    private String expiresAt;
    
    /**
     * URL para pagamento (apenas GPO)
     */
    @JsonProperty("paymentUrl")
    private String paymentUrl;
    
    /**
     * Data de criação
     * ISO 8601
     */
    @JsonProperty("createdAt")
    private String createdAt;
    
    /**
     * Mensagem de erro (se houver)
     */
    @JsonProperty("errorMessage")
    private String errorMessage;
}

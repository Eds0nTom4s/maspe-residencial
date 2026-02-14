package com.restaurante.financeiro.gateway.appypay.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request para criar cobrança na AppyPay
 * 
 * BASEADO EM ARENATICKET (VALIDADO EM PRODUÇÃO)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppyPayChargeRequest {
    
    /**
     * Identificador único da transação no merchant
     * Máximo 15 caracteres
     * DEVE SER ÚNICO E CURTO
     */
    @JsonProperty("merchantTransactionId")
    private String merchantTransactionId;
    
    /**
     * Valor em Kwanzas (AOA)
     * Sem decimais (centavos)
     * Exemplo: 1000 = 1000,00 AOA
     */
    @JsonProperty("amount")
    private Long amount;
    
    /**
     * Método de pagamento
     * GPO ou REF
     */
    @JsonProperty("paymentMethod")
    private String paymentMethod;
    
    /**
     * Descrição da cobrança
     * Visível para o cliente
     */
    @JsonProperty("description")
    private String description;
    
    /**
     * URL de retorno após pagamento (opcional)
     */
    @JsonProperty("returnUrl")
    private String returnUrl;
    
    /**
     * URL de callback para notificação (opcional)
     * AppyPay notifica quando status muda
     */
    @JsonProperty("callbackUrl")
    private String callbackUrl;
}

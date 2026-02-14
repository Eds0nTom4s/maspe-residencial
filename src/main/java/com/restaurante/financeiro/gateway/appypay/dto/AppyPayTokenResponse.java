package com.restaurante.financeiro.gateway.appypay.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response do token OAuth2 da AppyPay
 * 
 * BASEADO EM ARENATICKET (VALIDADO EM PRODUÇÃO)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppyPayTokenResponse {
    
    /**
     * Access token (Bearer)
     * Usar em todas as requisições subsequentes
     */
    @JsonProperty("access_token")
    private String accessToken;
    
    /**
     * Tipo do token (sempre "Bearer")
     */
    @JsonProperty("token_type")
    private String tokenType;
    
    /**
     * Tempo de expiração em segundos
     * Exemplo: 3600 = 1 hora
     */
    @JsonProperty("expires_in")
    private Long expiresIn;
    
    /**
     * Escopos concedidos
     */
    @JsonProperty("scope")
    private String scope;
}

package com.restaurante.financeiro.gateway.appypay;

import com.restaurante.financeiro.gateway.appypay.dto.AppyPayTokenResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;

/**
 * Serviço de autenticação OAuth2 com AppyPay
 * 
 * RESPONSABILIDADES:
 * - Obter access_token via client_credentials
 * - Cachear token até expiração
 * - Renovar automaticamente quando expira
 * - NUNCA logar client_secret
 * 
 * BASEADO EM ARENATICKET (VALIDADO EM PRODUÇÃO)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AppyPayAuthService {
    
    private final AppyPayProperties properties;
    private final RestTemplate restTemplate;
    
    // Cache do token
    private String cachedToken;
    private Instant tokenExpiresAt;
    
    /**
     * Obtém token válido (usa cache se disponível)
     * 
     * THREAD-SAFE: synchronized para evitar múltiplas chamadas simultâneas
     */
    public synchronized String getAccessToken() {
        if (properties.isMock()) {
            log.debug("Modo MOCK ativado - retornando token fake");
            return "MOCK_TOKEN_" + System.currentTimeMillis();
        }
        
        // Verifica se tem token válido em cache
        if (cachedToken != null && tokenExpiresAt != null) {
            if (Instant.now().isBefore(tokenExpiresAt)) {
                log.debug("Usando token em cache (expira em {})", tokenExpiresAt);
                return cachedToken;
            } else {
                log.info("Token expirado - renovando...");
            }
        }
        
        // Busca novo token
        return renovarToken();
    }
    
    /**
     * Renova token OAuth2
     * 
     * SEGURANÇA: client_secret NUNCA é logado
     */
    private String renovarToken() {
        log.info("Solicitando novo token OAuth2 da AppyPay");
        
        try {
            // Monta request OAuth2 client_credentials
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            
            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("grant_type", "client_credentials");
            body.add("client_id", properties.getClientId());
            body.add("client_secret", properties.getClientSecret()); // NUNCA LOGAR
            body.add("resource", properties.getResource());
            
            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);
            
            // Chama API
            ResponseEntity<AppyPayTokenResponse> response = restTemplate.exchange(
                properties.getTokenUrl(),
                HttpMethod.POST,
                request,
                AppyPayTokenResponse.class
            );
            
            if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
                throw new RuntimeException("Falha ao obter token: " + response.getStatusCode());
            }
            
            AppyPayTokenResponse tokenResponse = response.getBody();
            
            // Cacheia token (expira 5 minutos antes para segurança)
            cachedToken = tokenResponse.getAccessToken();
            long expiresInSeconds = tokenResponse.getExpiresIn() != null ? tokenResponse.getExpiresIn() : 3600;
            tokenExpiresAt = Instant.now().plusSeconds(expiresInSeconds - 300);
            
            log.info("Token OAuth2 obtido com sucesso (expira em {} segundos)", expiresInSeconds);
            
            return cachedToken;
            
        } catch (Exception e) {
            log.error("ERRO ao obter token OAuth2: {}", e.getMessage());
            throw new RuntimeException("Falha na autenticação com AppyPay: " + e.getMessage(), e);
        }
    }
    
    /**
     * Invalida cache (força renovação no próximo uso)
     */
    public void invalidateCache() {
        log.info("Cache de token invalidado manualmente");
        cachedToken = null;
        tokenExpiresAt = null;
    }
    
    /**
     * Verifica se tem token válido em cache
     */
    public boolean hasValidToken() {
        return cachedToken != null && 
               tokenExpiresAt != null && 
               Instant.now().isBefore(tokenExpiresAt);
    }
}

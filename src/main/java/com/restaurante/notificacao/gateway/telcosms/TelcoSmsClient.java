package com.restaurante.notificacao.gateway.telcosms;

import com.restaurante.notificacao.gateway.telcosms.dto.TelcoSmsRequest;
import com.restaurante.notificacao.gateway.telcosms.dto.TelcoSmsResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

/**
 * Cliente HTTP para comunicação com a API TelcoSMS
 * Responsável por enviar SMS através do gateway TelcoSMS
 */
@Component
public class TelcoSmsClient {
    
    private static final Logger log = LoggerFactory.getLogger(TelcoSmsClient.class);
    
    private final TelcoSmsProperties properties;
    private final RestTemplate restTemplate;
    
    public TelcoSmsClient(TelcoSmsProperties properties, @org.springframework.beans.factory.annotation.Qualifier("smsRestTemplate") RestTemplate restTemplate) {
        this.properties = properties;
        this.restTemplate = restTemplate;
    }
    
    /**
     * Envia SMS para o número especificado
     * 
     * @param phoneNumber Número de telefone (com ou sem código do país)
     * @param message Mensagem a ser enviada
     * @return Response do TelcoSMS
     */
    public TelcoSmsResponse sendSms(String phoneNumber, String message) {
        // Normaliza o número de telefone
        String normalizedPhone = normalizePhoneNumber(phoneNumber);
        
        if (properties.getMock()) {
            return enviarSmsMock(normalizedPhone, message);
        }
        
        return enviarSmsReal(normalizedPhone, message);
    }
    
    /**
     * Envia SMS real através da API TelcoSMS
     */
    private TelcoSmsResponse enviarSmsReal(String phoneNumber, String message) {
        try {
            String url = properties.getBaseUrl() + "/send_message";
            
            // Cria request
            TelcoSmsRequest request = new TelcoSmsRequest(
                properties.getApiKey(),
                phoneNumber,
                message
            );
            
            // Configura headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<TelcoSmsRequest> entity = new HttpEntity<>(request, headers);
            
            if (properties.getDebug()) {
                log.debug("Enviando SMS via TelcoSMS para {}: {}", phoneNumber, message);
            }
            
            // Envia requisição
            ResponseEntity<TelcoSmsResponse> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                entity,
                TelcoSmsResponse.class
            );
            
            TelcoSmsResponse result = response.getBody();
            
            if (result != null && result.isSuccess()) {
                log.info("SMS enviado com sucesso para {} - ID: {}", phoneNumber, result.getMessageId());
            } else {
                log.warn("Falha ao enviar SMS para {}: {}", phoneNumber, 
                    result != null ? result.getMessage() : "Resposta nula");
            }
            
            return result;
            
        } catch (Exception e) {
            log.error("Erro ao enviar SMS para {}: {}", phoneNumber, e.getMessage(), e);
            
            TelcoSmsResponse errorResponse = new TelcoSmsResponse("error", e.getMessage());
            errorResponse.setErrorCode("SEND_FAILED");
            return errorResponse;
        }
    }
    
    /**
     * Simula envio de SMS (modo mock para desenvolvimento)
     */
    private TelcoSmsResponse enviarSmsMock(String phoneNumber, String message) {
        log.info("📱 [MOCK] SMS simulado para {}: {}", phoneNumber, message);
        
        TelcoSmsResponse response = new TelcoSmsResponse("success", "SMS enviado com sucesso (modo mock)");
        response.setMessageId("MOCK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        
        return response;
    }
    
    /**
     * Normaliza número de telefone para o formato esperado pela TelcoSMS
     * Remove espaços, hífens e adiciona código do país se necessário
     */
    private String normalizePhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            throw new IllegalArgumentException("Número de telefone não pode ser vazio");
        }
        
        // Remove espaços, hífens, parênteses
        String normalized = phoneNumber.replaceAll("[\\s\\-()]", "");
        
        // Remove o símbolo + se existir
        if (normalized.startsWith("+")) {
            normalized = normalized.substring(1);
        }
        
        // Se não começar com código do país, adiciona o padrão de Angola
        if (!normalized.startsWith("244")) {
            // Remove zero inicial se existir (ex: 0923456789 -> 923456789)
            if (normalized.startsWith("0")) {
                normalized = normalized.substring(1);
            }
            normalized = "244" + normalized;
        }
        
        if (properties.getDebug()) {
            log.debug("Número normalizado: {} -> {}", phoneNumber, normalized);
        }
        
        return normalized;
    }
}

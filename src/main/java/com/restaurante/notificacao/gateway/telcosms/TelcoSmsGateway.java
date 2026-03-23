package com.restaurante.notificacao.gateway.telcosms;

import com.restaurante.notificacao.gateway.SmsGateway;
import com.restaurante.notificacao.gateway.SmsResponse;
import com.restaurante.notificacao.gateway.telcosms.dto.TelcoSmsRequest;
import com.restaurante.notificacao.gateway.telcosms.dto.TelcoSmsResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

/**
 * Implementação do gateway SMS usando TelcoSMS
 * API: https://www.telcosms.co.ao
 * 
 * SOLID: Open/Closed Principle
 * - Aberto para extensão (implementa SmsGateway)
 * - Fechado para modificação (não precisa alterar NotificacaoService)
 */
@Component
public class TelcoSmsGateway implements SmsGateway {
    
    private static final Logger log = LoggerFactory.getLogger(TelcoSmsGateway.class);
    
    private final TelcoSmsProperties properties;
    private final RestTemplate restTemplate;
    
    public TelcoSmsGateway(TelcoSmsProperties properties, 
                           @org.springframework.beans.factory.annotation.Qualifier("smsRestTemplate") RestTemplate restTemplate) {
        this.properties = properties;
        this.restTemplate = restTemplate;
    }
    
    @Override
    public SmsResponse sendSms(String phoneNumber, String message) {
        // Normaliza o número de telefone
        String normalizedPhone = normalizePhoneNumber(phoneNumber);
        
        if (properties.getMock()) {
            return enviarSmsMock(normalizedPhone, message);
        }
        
        return enviarSmsReal(normalizedPhone, message);
    }
    
    @Override
    public boolean isMockMode() {
        return properties.getMock();
    }
    
    @Override
    public String getProviderName() {
        return "TelcoSMS";
    }
    
    /**
     * Envia SMS real através da API TelcoSMS
     */
    private SmsResponse enviarSmsReal(String phoneNumber, String message) {
        String url = properties.getBaseUrl() + "/send_message";
        
        try {
            log.info("Iniciando envio de SMS via TelcoSMS para: {}", phoneNumber);
            
            // Cria request
            TelcoSmsRequest request = new TelcoSmsRequest(
                properties.getApiKey(),
                phoneNumber,
                message
            );
            
            // Log payload (sem expor chave completa)
            String maskedKey = properties.getApiKey() != null && properties.getApiKey().length() > 4 
                ? properties.getApiKey().substring(0, 4) + "***" 
                : "***";
            log.info("Payload TelcoSMS: URL={}, Fone={}, Msg='{}', KeyPrefix={}", url, phoneNumber, message, maskedKey);
            
            // Configura headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<TelcoSmsRequest> entity = new HttpEntity<>(request, headers);
            
            // Envia requisição
            ResponseEntity<TelcoSmsResponse> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                entity,
                TelcoSmsResponse.class
            );
            
            TelcoSmsResponse telcoResponse = response.getBody();
            log.info("Resposta TelcoSMS: Status={}, Body={}", response.getStatusCode(), telcoResponse);
            
            // Converte TelcoSmsResponse para SmsResponse genérico
            if (telcoResponse != null && telcoResponse.isSuccess()) {
                log.info("SMS enviado com sucesso via TelcoSMS para {} - ID: {}", 
                    phoneNumber, telcoResponse.getMessageId());
                return SmsResponse.success(telcoResponse.getMessage(), telcoResponse.getMessageId());
            } else {
                String errorMsg = telcoResponse != null ? telcoResponse.getMessage() : "Resposta nula";
                log.warn("Falha ao enviar SMS via TelcoSMS para {}: {}", phoneNumber, errorMsg);
                return SmsResponse.error(errorMsg, telcoResponse != null ? telcoResponse.getErrorCode() : null);
            }
            
        } catch (Exception e) {
            log.error("Erro ao enviar SMS via TelcoSMS para {}. URL={}: {}", phoneNumber, url, e.getMessage(), e);
            return SmsResponse.error(e.getMessage(), "SEND_FAILED");
        }
    }
    
    /**
     * Simula envio de SMS (modo mock para desenvolvimento)
     */
    private SmsResponse enviarSmsMock(String phoneNumber, String message) {
        log.info("📱 [MOCK] SMS simulado via TelcoSMS para {}: {}", phoneNumber, message);
        
        String mockId = "MOCK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return SmsResponse.success("SMS enviado com sucesso (modo mock)", mockId);
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

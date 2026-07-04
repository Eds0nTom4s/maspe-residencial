package com.restaurante.notificacao.gateway.telcosms;

import com.restaurante.notificacao.gateway.SmsGateway;
import com.restaurante.notificacao.gateway.SmsResponse;
import com.restaurante.notificacao.gateway.telcosms.dto.TelcoSmsRequest;
import com.restaurante.notificacao.gateway.telcosms.dto.TelcoSmsResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
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

        String provider = resolveProvider();
        if (provider.equals("disabled")) {
            log.info("SMS provider disabled; envio não executado para {}", maskPhone(normalizedPhone));
            return SmsResponse.error("SMS provider disabled", "SMS_DISABLED");
        }

        if (provider.equals("log") || provider.equals("sandbox") || provider.equals("mock")) {
            return enviarSmsSandbox(normalizedPhone, provider);
        }

        return enviarSmsReal(normalizedPhone, message);
    }
    
    @Override
    public boolean isMockMode() {
        String provider = resolveProvider();
        return provider.equals("disabled") || provider.equals("log") || provider.equals("sandbox") || provider.equals("mock");
    }
    
    @Override
    public String getProviderName() {
        String provider = resolveProvider();
        if (provider.equals("telcosms") || provider.equals("provider_real") || provider.equals("real")) {
            return "TelcoSMS";
        }
        return "SMS-" + provider;
    }
    
    /**
     * Envia SMS real através da API TelcoSMS
     */
    private SmsResponse enviarSmsReal(String phoneNumber, String message) {
        String url = properties.getBaseUrl() + "/send_message";
        
        try {
            log.info("Iniciando envio de SMS via TelcoSMS para: {}", maskPhone(phoneNumber));
            
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
            log.info("Payload TelcoSMS: URL={}, Fone={}, KeyPrefix={}", url, maskPhone(phoneNumber), maskedKey);
            
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
                    maskPhone(phoneNumber), telcoResponse.getMessageId());
                String successMessage = telcoResponse.getMessage() != null
                    ? telcoResponse.getMessage()
                    : telcoResponse.getStatus();
                return SmsResponse.success(successMessage, telcoResponse.getMessageId());
            } else {
                String errorMsg = telcoResponse != null ? telcoResponse.getMessage() : "Resposta nula";
                log.warn("Falha ao enviar SMS via TelcoSMS para {}: {}", maskPhone(phoneNumber), errorMsg);
                return SmsResponse.error(errorMsg, telcoResponse != null ? telcoResponse.getErrorCode() : null);
            }
            
        } catch (HttpClientErrorException e) {
            // O TelcoSMS retorna HTTP 404 quando o saldo é insuficiente.
            // Precisamos ler o corpo e distinguir este caso de um erro genérico.
            String responseBody = e.getResponseBodyAsString();
            log.error("Erro ao enviar SMS via TelcoSMS para {}. URL={}: {}", maskPhone(phoneNumber), url, responseBody);

            if (responseBody != null && responseBody.toLowerCase().contains("saldo insuficiente")) {
                log.warn("Saldo insuficiente na conta TelcoSMS! Approvisionamento necessário.");
                return SmsResponse.error(
                    "Saldo insuficiente na conta de SMS. Contacte o administrador.",
                    "SALDO_INSUFICIENTE_GATEWAY"
                );
            }

            return SmsResponse.error(responseBody != null ? responseBody : e.getMessage(), "HTTP_" + e.getStatusCode().value());

        } catch (Exception e) {
            log.error("Erro ao enviar SMS via TelcoSMS para {}. URL={}: {}", maskPhone(phoneNumber), url, e.getMessage(), e);
            return SmsResponse.error(e.getMessage(), "SEND_FAILED");
        }
    }
    
    /**
     * Simula envio de SMS (log/sandbox para desenvolvimento)
     */
    private SmsResponse enviarSmsSandbox(String phoneNumber, String provider) {
        log.info("SMS {} simulado para {}", provider, maskPhone(phoneNumber));

        String mockId = provider.toUpperCase() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String message = provider.equals("sandbox")
                ? "SMS enviado com sucesso (modo mock/sandbox)"
                : "SMS enviado com sucesso (modo " + provider + ")";
        return SmsResponse.success(message, mockId);
    }

    private String resolveProvider() {
        String configured = properties.getProvider();
        if (configured == null || configured.isBlank()) {
            Boolean legacyMock = properties.getMock();
            if (Boolean.TRUE.equals(legacyMock)) return "mock";
            return "telcosms";
        }
        String provider = configured.trim().toLowerCase();
        if (!Boolean.TRUE.equals(properties.getEnabled()) && (provider.equals("telcosms") || provider.equals("provider_real") || provider.equals("real"))) {
            return "disabled";
        }
        return provider;
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

    private String maskPhone(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            return "***";
        }
        String digits = phoneNumber.replaceAll("\\D", "");
        if (digits.length() <= 4) {
            return "***" + digits;
        }
        return "***" + digits.substring(digits.length() - 4);
    }
}

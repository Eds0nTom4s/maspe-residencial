package com.restaurante.notificacao.gateway.telcosms;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Propriedades de configuração para integração com TelcoSMS
 * API: https://www.telcosms.co.ao
 */
@Configuration
@ConfigurationProperties(prefix = "app.notification.telcosms")
public class TelcoSmsProperties {
    
    /**
     * URL base da API TelcoSMS
     */
    private String baseUrl;
    
    /**
     * Chave de API para autenticação
     */
    private String apiKey;
    
    /**
     * Timeout para requisições HTTP (em milissegundos)
     */
    private Integer timeoutMs = 15000; // 15 segundos
    
    /**
     * Habilitar logs de debug
     */
    private Boolean debug = false;
    
    /**
     * Modo mock (para desenvolvimento/testes)
     * Quando true, não envia SMS real
     */
    private Boolean mock = true;
    
    /**
     * Prefixo padrão para números de telefone de Angola
     */
    private String defaultCountryCode = "+244";
    
    /**
     * Número de tentativas em caso de falha
     */
    private Integer maxRetries = 3;
    
    @PostConstruct
    public void validate() {
        if (!mock) {
            if (baseUrl == null || baseUrl.isBlank()) {
                throw new IllegalStateException("app.notification.telcosms.baseUrl é obrigatório quando mock=false");
            }
            if (apiKey == null || apiKey.isBlank()) {
                throw new IllegalStateException("app.notification.telcosms.apiKey é obrigatório quando mock=false");
            }
        }
    }
    
    // Getters e Setters
    
    public String getBaseUrl() {
        return baseUrl;
    }
    
    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }
    
    public String getApiKey() {
        return apiKey;
    }
    
    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }
    
    public Integer getTimeoutMs() {
        return timeoutMs;
    }
    
    public void setTimeoutMs(Integer timeoutMs) {
        this.timeoutMs = timeoutMs;
    }
    
    public Boolean getDebug() {
        return debug;
    }
    
    public void setDebug(Boolean debug) {
        this.debug = debug;
    }
    
    public Boolean getMock() {
        return mock;
    }
    
    public void setMock(Boolean mock) {
        this.mock = mock;
    }
    
    public String getDefaultCountryCode() {
        return defaultCountryCode;
    }
    
    public void setDefaultCountryCode(String defaultCountryCode) {
        this.defaultCountryCode = defaultCountryCode;
    }
    
    public Integer getMaxRetries() {
        return maxRetries;
    }
    
    public void setMaxRetries(Integer maxRetries) {
        this.maxRetries = maxRetries;
    }
}

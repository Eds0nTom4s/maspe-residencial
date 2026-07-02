package com.restaurante.notificacao.gateway.telcosms;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Propriedades de configuração para envio de SMS.
 */
@Configuration
@ConfigurationProperties(prefix = "consuma.sms")
public class TelcoSmsProperties {

    /**
     * Provider ativo: disabled, log, sandbox, telcosms/provider_real.
     */
    private String provider = "disabled";

    /**
     * Habilita envio externo de SMS.
     */
    private Boolean enabled = false;

    /**
     * Identificação do remetente quando suportada pelo provider.
     */
    private String senderId = "CONSUMA";
    
    /**
     * URL base da API TelcoSMS
     */
    private String baseUrl = "";
    
    /**
     * Chave de API para autenticação
     */
    private String apiKey = "";
    
    /**
     * Timeout para requisições HTTP (em milissegundos)
     */
    private Integer timeoutMs = 10000;
    
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
        if (isRealProvider() && Boolean.TRUE.equals(enabled)) {
            if (baseUrl == null || baseUrl.isBlank()) {
                throw new IllegalStateException("consuma.sms.base-url é obrigatório quando provider real está habilitado");
            }
            if (apiKey == null || apiKey.isBlank()) {
                throw new IllegalStateException("consuma.sms.api-key é obrigatório quando provider real está habilitado");
            }
        }
    }

    public boolean isRealProvider() {
        String p = normalizedProvider();
        return p.equals("telcosms") || p.equals("provider_real") || p.equals("real");
    }

    public String normalizedProvider() {
        return provider == null ? "disabled" : provider.trim().toLowerCase();
    }
    
    // Getters e Setters
    
    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public String getSenderId() {
        return senderId;
    }

    public void setSenderId(String senderId) {
        this.senderId = senderId;
    }

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

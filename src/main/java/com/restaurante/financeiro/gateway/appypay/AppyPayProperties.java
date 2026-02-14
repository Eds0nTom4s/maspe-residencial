package com.restaurante.financeiro.gateway.appypay;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configurações da AppyPay
 * 
 * BASEADO EM ARENATICKET (VALIDADO EM PRODUÇÃO)
 * 
 * Carregado do application.properties / application-dev.properties
 * Separação de ambientes: dev, homolog, prod
 */
@Configuration
@ConfigurationProperties(prefix = "app.payment.appypay")
@Data
public class AppyPayProperties {
    
    /**
     * URL base da API AppyPay
     * Exemplo: https://gwy-api.appypay.co.ao/v2.0/
     */
    private String baseUrl;
    
    /**
     * URL para obter token OAuth2
     * Exemplo: https://login.microsoftonline.com/auth.appypay.co.ao/oauth2/token
     */
    private String tokenUrl;
    
    /**
     * Client ID (identificador do merchant)
     * Fornecido pela AppyPay
     */
    private String clientId;
    
    /**
     * Client Secret (chave secreta)
     * NUNCA LOGAR - Variável de ambiente obrigatória
     */
    private String clientSecret;
    
    /**
     * Resource (audience) para OAuth2
     * Exemplo: bee57785-7a19-4f1c-9c8d-aa03f2f0e333
     */
    private String resource;
    
    /**
     * URL de callback para webhooks
     * AppyPay notifica quando pagamento confirma
     */
    private String callbackUrl;
    
    /**
     * Timeout de requisição (ms)
     * Default: 20 segundos
     */
    private int timeoutMs = 20000;
    
    /**
     * Modo debug (logs detalhados)
     */
    private boolean debug = false;
    
    /**
     * Modo mock (não chama API real)
     * Útil para testes locais
     */
    private boolean mock = false;
    
    /**
     * Métodos de pagamento com IDs específicos
     */
    private Methods methods = new Methods();
    
    /**
     * Classe interna para métodos de pagamento
     */
    @Data
    public static class Methods {
        /**
         * ID do método GPO (pagamento instantâneo)
         * Formato: GPO_{uuid}
         */
        private String gpo;
        
        /**
         * ID do método REF (referência bancária)
         * Formato: REF_{uuid}
         */
        private String ref;
    }
    
    /**
     * Valida configurações obrigatórias
     */
    public void validate() {
        if (!mock) {
            if (baseUrl == null || baseUrl.isBlank()) {
                throw new IllegalStateException("app.payment.appypay.base-url é obrigatório");
            }
            if (tokenUrl == null || tokenUrl.isBlank()) {
                throw new IllegalStateException("app.payment.appypay.token-url é obrigatório");
            }
            if (clientId == null || clientId.isBlank()) {
                throw new IllegalStateException("app.payment.appypay.client-id é obrigatório");
            }
            if (clientSecret == null || clientSecret.isBlank()) {
                throw new IllegalStateException("app.payment.appypay.client-secret é obrigatório");
            }
            if (resource == null || resource.isBlank()) {
                throw new IllegalStateException("app.payment.appypay.resource é obrigatório");
            }
        }
    }
    
    /**
     * Obtém ID do método GPO
     */
    public String getGpoMethodId() {
        return methods.getGpo();
    }
    
    /**
     * Obtém ID do método REF
     */
    public String getRefMethodId() {
        return methods.getRef();
    }
    
    /**
     * Verifica se método está configurado
     */
    public boolean isMetodoConfigurado(String metodo) {
        if ("GPO".equalsIgnoreCase(metodo)) {
            return methods.getGpo() != null && !methods.getGpo().isBlank();
        }
        if ("REF".equalsIgnoreCase(metodo)) {
            return methods.getRef() != null && !methods.getRef().isBlank();
        }
        return false;
    }
}

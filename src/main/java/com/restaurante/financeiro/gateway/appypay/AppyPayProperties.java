package com.restaurante.financeiro.gateway.appypay;

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
     * URL de retorno do comprador após fluxo de pagamento.
     */
    private String returnUrl;
    
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
     * Segredo partilhado para validação HMAC-SHA256 dos callbacks AppyPay.
     *
     * <p>Fornecido pela AppyPay no painel de merchant.
     * Definir via variável de ambiente {@code APPYPAY_WEBHOOK_SECRET}.
     * Se vazio, a validação HMAC é ignorada (apenas em modo mock/desenvolvimento).
     */
    private String webhookSecret;

    /**
     * Exige assinatura HMAC no callback. Manter false quando o merchant AppyPay
     * não envia assinatura; se habilitado, callbacks sem assinatura são rejeitados.
     */
    private boolean webhookSignatureRequired = false;

    /**
     * Configuração da rotina de reconciliação de pagamentos pendentes.
     */
    private Reconciliation reconciliation = new Reconciliation();

    /**
     * Métodos de pagamento com IDs específicos
     */
    private Methods methods = new Methods();

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public String getTokenUrl() { return tokenUrl; }
    public void setTokenUrl(String tokenUrl) { this.tokenUrl = tokenUrl; }

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }

    public String getClientSecret() { return clientSecret; }
    public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }

    public String getResource() { return resource; }
    public void setResource(String resource) { this.resource = resource; }

    public String getCallbackUrl() { return callbackUrl; }
    public void setCallbackUrl(String callbackUrl) { this.callbackUrl = callbackUrl; }

    public String getReturnUrl() { return returnUrl; }
    public void setReturnUrl(String returnUrl) { this.returnUrl = returnUrl; }

    public int getTimeoutMs() { return timeoutMs; }
    public void setTimeoutMs(int timeoutMs) { this.timeoutMs = timeoutMs; }

    public boolean isDebug() { return debug; }
    public void setDebug(boolean debug) { this.debug = debug; }

    public boolean isMock() { return mock; }
    public void setMock(boolean mock) { this.mock = mock; }

    public String getWebhookSecret() { return webhookSecret; }
    public void setWebhookSecret(String webhookSecret) { this.webhookSecret = webhookSecret; }

    public boolean isWebhookSignatureRequired() { return webhookSignatureRequired; }
    public void setWebhookSignatureRequired(boolean webhookSignatureRequired) {
        this.webhookSignatureRequired = webhookSignatureRequired;
    }

    public Methods getMethods() { return methods; }
    public void setMethods(Methods methods) { this.methods = methods; }

    public Reconciliation getReconciliation() { return reconciliation; }
    public void setReconciliation(Reconciliation reconciliation) { this.reconciliation = reconciliation; }
    
    /**
     * Classe interna para métodos de pagamento
     */
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

        public String getGpo() { return gpo; }
        public void setGpo(String gpo) { this.gpo = gpo; }

        public String getRef() { return ref; }
        public void setRef(String ref) { this.ref = ref; }
    }

    public static class Reconciliation {
        private boolean enabled = true;
        private String cron = "0 */15 * * * *";
        private int minAgeMinutes = 5;
        private int batchSize = 100;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public String getCron() { return cron; }
        public void setCron(String cron) { this.cron = cron; }

        public int getMinAgeMinutes() { return minAgeMinutes; }
        public void setMinAgeMinutes(int minAgeMinutes) { this.minAgeMinutes = minAgeMinutes; }

        public int getBatchSize() { return batchSize; }
        public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
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

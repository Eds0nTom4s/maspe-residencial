package com.restaurante.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Prompt 41.4/41.5 — Properties do Owner Action Token curto pós-OTP.
 */
@Configuration
@ConfigurationProperties(prefix = "consuma.sessao.owner-action-token")
public class SessaoOwnerActionTokenProperties {

    /** TTL do token em minutos (default: 10 min) */
    private int ttlMinutes = 10;

    /** Máximo de usos por token (default: 20) */
    private int maxUses = 20;

    /**
     * Pepper usado no hash do token.
     * Em produção, definir via CONSUMA_OWNER_ACTION_TOKEN_PEPPER.
     */
    private String hashPepper = "";

    /** Configurações do job de limpeza de tokens antigos */
    private final Cleanup cleanup = new Cleanup();

    public int getTtlMinutes() { return ttlMinutes; }
    public void setTtlMinutes(int ttlMinutes) { this.ttlMinutes = ttlMinutes; }

    public int getMaxUses() { return maxUses; }
    public void setMaxUses(int maxUses) { this.maxUses = maxUses; }

    public String getHashPepper() { return hashPepper; }
    public void setHashPepper(String hashPepper) { this.hashPepper = hashPepper; }

    public Cleanup getCleanup() { return cleanup; }

    public static class Cleanup {
        /** Habilita o job de limpeza */
        private boolean enabled = true;
        /** Retenção em dias antes de deletar tokens finalizados */
        private int retentionDays = 30;
        /** Tamanho do batch por execução */
        private int batchSize = 500;
        /** Cron: 03:20 todo dia */
        private String cron = "0 20 3 * * *";

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public int getRetentionDays() { return retentionDays; }
        public void setRetentionDays(int retentionDays) { this.retentionDays = retentionDays; }

        public int getBatchSize() { return batchSize; }
        public void setBatchSize(int batchSize) { this.batchSize = batchSize; }

        public String getCron() { return cron; }
        public void setCron(String cron) { this.cron = cron; }
    }
}

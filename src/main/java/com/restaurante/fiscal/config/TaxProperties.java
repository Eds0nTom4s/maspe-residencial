package com.restaurante.fiscal.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.math.RoundingMode;

@Configuration
@ConfigurationProperties(prefix = "consuma.tax")
public class TaxProperties {

    private boolean enabled = true;
    private String defaultCountry = "AO";
    private int monetaryScale = 2;
    private int calculationScale = 6;
    private RoundingMode roundingMode = RoundingMode.HALF_UP;

    private final Document document = new Document();
    private final Evidence evidence = new Evidence();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getDefaultCountry() {
        return defaultCountry;
    }

    public void setDefaultCountry(String defaultCountry) {
        this.defaultCountry = defaultCountry;
    }

    public int getMonetaryScale() {
        return monetaryScale;
    }

    public void setMonetaryScale(int monetaryScale) {
        this.monetaryScale = monetaryScale;
    }

    public int getCalculationScale() {
        return calculationScale;
    }

    public void setCalculationScale(int calculationScale) {
        this.calculationScale = calculationScale;
    }

    public RoundingMode getRoundingMode() {
        return roundingMode;
    }

    public void setRoundingMode(RoundingMode roundingMode) {
        this.roundingMode = roundingMode;
    }

    public Document getDocument() {
        return document;
    }

    public Evidence getEvidence() {
        return evidence;
    }

    public static class Document {
        private boolean autoIssueOnPayment = false;
        private String sequencePrefix = "INT";
        private boolean requireProductClassification = false;
        private final AutoIssue autoIssue = new AutoIssue();

        public boolean isAutoIssueOnPayment() {
            return autoIssueOnPayment;
        }

        public void setAutoIssueOnPayment(boolean autoIssueOnPayment) {
            this.autoIssueOnPayment = autoIssueOnPayment;
        }

        public String getSequencePrefix() {
            return sequencePrefix;
        }

        public void setSequencePrefix(String sequencePrefix) {
            this.sequencePrefix = sequencePrefix;
        }

        public boolean isRequireProductClassification() {
            return requireProductClassification;
        }

        public void setRequireProductClassification(boolean requireProductClassification) {
            this.requireProductClassification = requireProductClassification;
        }

        public AutoIssue getAutoIssue() {
            return autoIssue;
        }
    }

    public static class AutoIssue {
        private boolean enabled = true;
        private int maxAttempts = 5;
        private int initialDelaySeconds = 10;
        private int retryBackoffSeconds = 60;
        private int maxBackoffSeconds = 3600;
        private int batchSize = 100;
        private int staleLockMinutes = 10;
        private String workerId = "local";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        public int getInitialDelaySeconds() {
            return initialDelaySeconds;
        }

        public void setInitialDelaySeconds(int initialDelaySeconds) {
            this.initialDelaySeconds = initialDelaySeconds;
        }

        public int getRetryBackoffSeconds() {
            return retryBackoffSeconds;
        }

        public void setRetryBackoffSeconds(int retryBackoffSeconds) {
            this.retryBackoffSeconds = retryBackoffSeconds;
        }

        public int getMaxBackoffSeconds() {
            return maxBackoffSeconds;
        }

        public void setMaxBackoffSeconds(int maxBackoffSeconds) {
            this.maxBackoffSeconds = maxBackoffSeconds;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }

        public int getStaleLockMinutes() {
            return staleLockMinutes;
        }

        public void setStaleLockMinutes(int staleLockMinutes) {
            this.staleLockMinutes = staleLockMinutes;
        }

        public String getWorkerId() {
            return workerId;
        }

        public void setWorkerId(String workerId) {
            this.workerId = workerId;
        }
    }

    public static class Evidence {
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}

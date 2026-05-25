package com.restaurante.billing.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "consuma.billing")
@Data
public class BillingProperties {
    private boolean enabled = true;

    private Invoice invoice = new Invoice();
    private Evidence evidence = new Evidence();

    @Data
    public static class Invoice {
        private String sequencePrefix = "CONS-BILL";
    }

    @Data
    public static class Evidence {
        private boolean enabled = true;
    }
}


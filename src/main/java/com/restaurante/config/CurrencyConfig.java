package com.restaurante.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "system.currency")
@Data
public class CurrencyConfig {
    /**
     * ISO 4217 Currency Code (e.g., AOA, BRL, EUR)
     */
    private String code = "AOA";
    
    /**
     * Currency symbol (e.g., Kz, R$, €)
     */
    private String symbol = "Kz";
    
    /**
     * Java Locale language tag (e.g., pt-AO, pt-BR, en-US)
     */
    private String locale = "pt-AO";
}

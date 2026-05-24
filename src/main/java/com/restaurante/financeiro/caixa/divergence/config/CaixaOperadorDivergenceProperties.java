package com.restaurante.financeiro.caixa.divergence.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

@Configuration
@ConfigurationProperties(prefix = "consuma.caixa-operador.divergence")
public class CaixaOperadorDivergenceProperties {

    private BigDecimal lowThreshold = new BigDecimal("1000");
    private BigDecimal mediumThreshold = new BigDecimal("10000");
    private BigDecimal criticalThreshold = new BigDecimal("100000");

    public BigDecimal getLowThreshold() {
        return lowThreshold;
    }

    public void setLowThreshold(BigDecimal lowThreshold) {
        this.lowThreshold = lowThreshold;
    }

    public BigDecimal getMediumThreshold() {
        return mediumThreshold;
    }

    public void setMediumThreshold(BigDecimal mediumThreshold) {
        this.mediumThreshold = mediumThreshold;
    }

    public BigDecimal getCriticalThreshold() {
        return criticalThreshold;
    }

    public void setCriticalThreshold(BigDecimal criticalThreshold) {
        this.criticalThreshold = criticalThreshold;
    }
}


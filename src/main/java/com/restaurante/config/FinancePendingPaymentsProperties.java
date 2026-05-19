package com.restaurante.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "consuma.financeiro.pending-payments")
public class FinancePendingPaymentsProperties {

    private int defaultLookbackHours = 24;
    private int maxPageSize = 100;
    private int warningAfterMinutes = 10;
    private int criticalAfterMinutes = 30;
    private boolean blockTurnoCloseOnCritical = false;

    public int getDefaultLookbackHours() { return defaultLookbackHours; }
    public void setDefaultLookbackHours(int defaultLookbackHours) { this.defaultLookbackHours = defaultLookbackHours; }
    public int getMaxPageSize() { return maxPageSize; }
    public void setMaxPageSize(int maxPageSize) { this.maxPageSize = maxPageSize; }
    public int getWarningAfterMinutes() { return warningAfterMinutes; }
    public void setWarningAfterMinutes(int warningAfterMinutes) { this.warningAfterMinutes = warningAfterMinutes; }
    public int getCriticalAfterMinutes() { return criticalAfterMinutes; }
    public void setCriticalAfterMinutes(int criticalAfterMinutes) { this.criticalAfterMinutes = criticalAfterMinutes; }
    public boolean isBlockTurnoCloseOnCritical() { return blockTurnoCloseOnCritical; }
    public void setBlockTurnoCloseOnCritical(boolean blockTurnoCloseOnCritical) { this.blockTurnoCloseOnCritical = blockTurnoCloseOnCritical; }
}


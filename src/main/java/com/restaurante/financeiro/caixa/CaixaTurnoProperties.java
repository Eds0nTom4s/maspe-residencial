package com.restaurante.financeiro.caixa;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "consuma.financeiro.caixa-turno")
public class CaixaTurnoProperties {

    private int eventosRecentesLimit = 20;
    private boolean incluirEventos = true;
    private boolean incluirPendentes = true;

    public int getEventosRecentesLimit() {
        return eventosRecentesLimit;
    }

    public void setEventosRecentesLimit(int eventosRecentesLimit) {
        this.eventosRecentesLimit = eventosRecentesLimit;
    }

    public boolean isIncluirEventos() {
        return incluirEventos;
    }

    public void setIncluirEventos(boolean incluirEventos) {
        this.incluirEventos = incluirEventos;
    }

    public boolean isIncluirPendentes() {
        return incluirPendentes;
    }

    public void setIncluirPendentes(boolean incluirPendentes) {
        this.incluirPendentes = incluirPendentes;
    }
}


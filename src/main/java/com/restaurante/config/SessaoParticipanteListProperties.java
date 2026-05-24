package com.restaurante.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Prompt 41.4 — Properties de paginação de listagens de participantes.
 */
@Configuration
@ConfigurationProperties(prefix = "consuma.sessao.participantes.list")
public class SessaoParticipanteListProperties {

    /** Tamanho de página padrão */
    private int defaultPageSize = 20;

    /** Tamanho máximo de página permitido */
    private int maxPageSize = 100;

    public int getDefaultPageSize() { return defaultPageSize; }
    public void setDefaultPageSize(int defaultPageSize) { this.defaultPageSize = defaultPageSize; }

    public int getMaxPageSize() { return maxPageSize; }
    public void setMaxPageSize(int maxPageSize) { this.maxPageSize = maxPageSize; }
}

package com.restaurante.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Observabilidade operacional (Prompt 35).
 *
 * Foco: thresholds e limites para endpoints internos /platform/observabilidade.
 */
@Configuration
@ConfigurationProperties(prefix = "consuma.observabilidade")
@Data
public class ObservabilidadeProperties {

    /**
     * Device considerado offline quando ultimoHeartbeatEm < now - threshold.
     */
    private int deviceOfflineThresholdMinutes = 5;

    /**
     * SubPedido considerado atrasado quando em preparação há mais que threshold.
     */
    private int producaoSubpedidoAtrasadoMinutes = 30;

    /**
     * Turno em fecho "longo" quando status EM_FECHO por mais que threshold.
     */
    private int turnoEmFechoLongoMinutes = 30;

    /**
     * Lookback default para eventos operacionais.
     */
    private int eventosDefaultLookbackHours = 24;

    /**
     * Page size máximo para endpoints internos.
     */
    private int maxPageSize = 100;
}


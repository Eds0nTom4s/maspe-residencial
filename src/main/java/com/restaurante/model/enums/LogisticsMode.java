package com.restaurante.model.enums;

/**
 * Modo logístico padrão do tenant (visão agregada usada por templates).
 *
 * Observação:
 * - Nesta fase, é usado principalmente para provisionamento/políticas padrão.
 * - O enforcement em runtime pode evoluir em fases futuras.
 */
public enum LogisticsMode {
    NONE,
    TENANT_MANUAL,
    CONSUMA_NETWORK,
    HYBRID
}


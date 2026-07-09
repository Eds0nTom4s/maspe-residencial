package com.restaurante.repository.projection;

import java.time.LocalDateTime;

public interface SubPedidoFilaAggProjection {
    Long getCount();
    LocalDateTime getMaxUpdatedAt();
    LocalDateTime getMaxCreatedAt();
    LocalDateTime getMaxIniciadoEm();
    LocalDateTime getMaxProntoEm();
    LocalDateTime getMaxEntregueEm();
    Long getNullUpdatedAtCount();
}


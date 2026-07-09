package com.restaurante.repository.projection;

import java.time.LocalDateTime;

public interface QrAggProjection {
    Long getCount();
    LocalDateTime getMaxUpdatedAt();
    LocalDateTime getMaxCreatedAt();
    LocalDateTime getMaxRevogadoEm();
    Long getNullUpdatedAtCount();
}


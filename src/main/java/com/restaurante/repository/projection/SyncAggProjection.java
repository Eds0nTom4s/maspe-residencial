package com.restaurante.repository.projection;

import java.time.LocalDateTime;

public interface SyncAggProjection {
    Long getCount();
    LocalDateTime getMaxUpdatedAt();
    LocalDateTime getMaxCreatedAt();
    Long getNullUpdatedAtCount();
}


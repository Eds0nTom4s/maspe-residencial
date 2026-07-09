package com.restaurante.repository.projection;

import java.time.LocalDateTime;

public interface SessionOpenAggProjection {
    Long getCount();
    LocalDateTime getMaxAbertaEm();
    LocalDateTime getMaxUltimaAtividadeEm();
    LocalDateTime getMaxUpdatedAt();
}


package com.restaurante.service.operacional.event;

import com.restaurante.model.enums.OperationalEntityType;
import com.restaurante.model.enums.OperationalEventType;
import com.restaurante.model.enums.OperationalOrigem;
import lombok.Value;

import java.time.LocalDateTime;

@Value
public class OperationalEventLoggedEvent {
    Long logId;
    Long tenantId;
    OperationalEventType eventType;
    OperationalEntityType entityType;
    Long entityId;
    LocalDateTime occurredAt;
    OperationalOrigem origem;
    String statusAnterior;
    String statusNovo;
    String motivo;
    String metadataJson;
}


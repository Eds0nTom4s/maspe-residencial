package com.restaurante.platform.observabilidade.dto;

import com.restaurante.model.enums.OperationalActorType;
import com.restaurante.model.enums.OperationalEntityType;
import com.restaurante.model.enums.OperationalEventType;
import com.restaurante.model.enums.OperationalOrigem;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class OperationalEventObservabilidadeResponse {
    private Long eventId;
    private Long tenantId;
    private OperationalEventType eventType;
    private OperationalEntityType entityType;
    private Long entityId;
    private OperationalActorType actorType;
    private Long actorUserId;
    private Long deviceId;
    private Long pedidoId;
    private Long pagamentoId;
    private Long turnoId;
    private OperationalOrigem origem;
    private LocalDateTime createdAt;
    private String metadataResumo;
}


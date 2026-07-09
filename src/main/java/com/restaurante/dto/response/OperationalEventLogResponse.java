package com.restaurante.dto.response;

import com.restaurante.model.enums.OperationalActorType;
import com.restaurante.model.enums.OperationalEntityType;
import com.restaurante.model.enums.OperationalEventType;
import com.restaurante.model.enums.OperationalOrigem;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OperationalEventLogResponse {
    private Long id;
    private OperationalEventType eventType;
    private OperationalEntityType entityType;
    private Long entityId;
    private Long pedidoId;
    private Long subPedidoId;
    private String statusAnterior;
    private String statusNovo;
    private OperationalActorType actorType;
    private Long actorUserId;
    private Long deviceId;
    private OperationalOrigem origem;
    private String motivo;
    private LocalDateTime createdAt;
}


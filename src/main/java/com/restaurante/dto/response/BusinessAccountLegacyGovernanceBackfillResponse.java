package com.restaurante.dto.response;

import com.restaurante.model.enums.BusinessAccountEstado;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BusinessAccountLegacyGovernanceBackfillResponse {
    private Long businessAccountId;
    private BusinessAccountEstado estadoBefore;
    private BusinessAccountEstado estadoAfter;
    private Long responsavelUserIdBefore;
    private Long responsavelUserIdAfter;
    private List<Long> ownerUserIds;
    private List<Long> businessAccountMemberIds;
    private boolean promotedToAtiva;
    private boolean responsavelUpdated;
    private boolean requiresBackfillBefore;
    private boolean requiresBackfillAfter;
    private String status;
    private BusinessAccountGovernanceDiagnosticResponse diagnosticBefore;
    private BusinessAccountGovernanceDiagnosticResponse diagnosticAfter;
}

package com.restaurante.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BusinessAccountLegacyGovernanceBackfillRequest {
    private Long ownerUserId;
    private String reason;
}

package com.restaurante.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantOwnershipBackfillResponse {
    private Long tenantId;
    private Long businessAccountId;
    private Long ownerUserId;
    private String ownerEmail;
    private Long tenantUserId;
    private Long businessAccountMemberId;
    private String temporaryPassword;
    private LocalDateTime temporaryPasswordExpiresAt;
    private boolean requiresBackfill;
    private String status;
}

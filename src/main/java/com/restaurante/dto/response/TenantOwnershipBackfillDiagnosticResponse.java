package com.restaurante.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantOwnershipBackfillDiagnosticResponse {
    private Long tenantId;
    private String tenantName;
    private String tenantCode;
    private String tenantStatus;
    private Long businessAccountId;
    private String businessAccountName;
    private boolean hasBusinessAccount;
    private boolean hasTenantOwner;
    private boolean hasBusinessAccountOwner;
    private boolean hasPlatformAdminAsTenantOwner;
    private boolean hasPlatformAdminAsBusinessAccountOwner;
    private Long ownerUserId;
    private String ownerName;
    private String ownerEmail;
    private String ownerPhone;
    private boolean requiresBackfill;
    private List<String> blockingReasons;
    private List<String> warnings;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

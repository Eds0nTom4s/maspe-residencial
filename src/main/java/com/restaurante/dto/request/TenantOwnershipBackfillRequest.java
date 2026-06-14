package com.restaurante.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantOwnershipBackfillRequest {
    private Long businessAccountId;
    private Long ownerUserId;
    private String ownerName;
    private String ownerEmail;
    private String ownerPhone;
    private String ownerUsername;
    private Boolean generateTemporaryPassword;
    private Boolean sendCredentials;
    private String reason;
}

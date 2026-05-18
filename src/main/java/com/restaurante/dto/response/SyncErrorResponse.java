package com.restaurante.dto.response;

import java.time.LocalDateTime;
import java.util.Map;

public record SyncErrorResponse(
        SyncErrorCode code,
        String message,
        String syncDomain,
        boolean fullSyncRequired,
        SyncEnvelope.FullSyncRequiredReason fullSyncReason,
        boolean recoverable,
        SyncRecoveryAction action,
        LocalDateTime serverTime,
        Map<String, Object> details
) {

    public enum SyncErrorCode {
        SYNC_CURSOR_INVALID,
        SYNC_CURSOR_INVALID_SIGNATURE,
        SYNC_CURSOR_EXPIRED,
        SYNC_CURSOR_CONTEXT_MISMATCH,
        SYNC_CURSOR_MALFORMED,
        SYNC_UPDATED_SINCE_INVALID,
        SYNC_DEVICE_UNAUTHORIZED,
        SYNC_DEVICE_FORBIDDEN,
        SYNC_CAPABILITY_FORBIDDEN,
        SYNC_DOMAIN_FORBIDDEN,
        SYNC_FULL_REQUIRED,
        SYNC_SCOPE_AMBIGUOUS,
        SYNC_INTERNAL_ERROR
    }

    public enum SyncRecoveryAction {
        RETRY,
        FULL_SYNC,
        REAUTH_DEVICE,
        CLEAR_CURSOR,
        CONTACT_SUPPORT,
        NONE
    }
}


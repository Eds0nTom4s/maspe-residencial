package com.restaurante.dto.response;

import java.time.LocalDateTime;
import java.util.List;

public record SyncEnvelope<T>(
        T data,
        LocalDateTime syncGeneratedAt,
        String syncVersion,
        String etag,
        boolean fullSyncRequired,
        boolean hasMore,
        String nextCursor,
        SyncMode mode,
        List<SyncWarning> warnings
) {
    public enum SyncMode {
        FULL,
        INCREMENTAL,
        NOT_MODIFIED
    }

    public record SyncWarning(
            SyncWarningCode code,
            String message
    ) {}

    public enum SyncWarningCode {
        UPDATED_SINCE_UNRELIABLE,
        FULL_SYNC_RECOMMENDED,
        CURSOR_EXPIRED,
        PARTIAL_RESPONSE,
        DEVICE_SCOPE_AMBIGUOUS
    }

    public static <T> SyncEnvelope<T> full(T data, LocalDateTime now, String version, String etag, boolean hasMore, String nextCursor, List<SyncWarning> warnings) {
        return new SyncEnvelope<>(data, now, version, etag, false, hasMore, nextCursor, SyncMode.FULL, warnings);
    }

    public static <T> SyncEnvelope<T> incremental(T data, LocalDateTime now, String version, String etag, boolean fullSyncRequired, boolean hasMore, String nextCursor, List<SyncWarning> warnings) {
        return new SyncEnvelope<>(data, now, version, etag, fullSyncRequired, hasMore, nextCursor, SyncMode.INCREMENTAL, warnings);
    }
}


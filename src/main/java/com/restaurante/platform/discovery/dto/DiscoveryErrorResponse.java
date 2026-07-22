package com.restaurante.platform.discovery.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "Erro público padronizado do Discovery")
public record DiscoveryErrorResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant timestamp,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String error,
        @Schema(
                        allowableValues = {
                            "INVALID_REQUEST",
                            "SORT_NOT_SUPPORTED",
                            "NOT_FOUND",
                            "SERVICE_UNAVAILABLE",
                            "UNKNOWN"
                        },
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String code,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String message,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String path) {}

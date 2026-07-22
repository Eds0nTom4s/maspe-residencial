package com.restaurante.platform.discovery.dto;

import com.restaurante.platform.discovery.dto.DiscoveryDtos.MerchantCategoryDto;
import com.restaurante.platform.discovery.dto.DiscoveryDtos.MerchantSummaryDto;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Página zero-based de comerciantes públicos")
public record MerchantSearchResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<MerchantCategoryDto> categories,
        @Schema(description = "Nunca null", requiredMode = Schema.RequiredMode.REQUIRED)
        List<MerchantSummaryDto> merchants,
        @Schema(description = "Número zero-based", example = "0", minimum = "0", requiredMode = Schema.RequiredMode.REQUIRED)
        int page,
        @Schema(example = "20", minimum = "1", maximum = "100", requiredMode = Schema.RequiredMode.REQUIRED)
        int pageSize,
        @Schema(minimum = "0", requiredMode = Schema.RequiredMode.REQUIRED)
        int totalCount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        boolean hasMore) {}

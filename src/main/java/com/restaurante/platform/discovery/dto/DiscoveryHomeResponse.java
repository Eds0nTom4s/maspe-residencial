package com.restaurante.platform.discovery.dto;

import com.restaurante.platform.discovery.dto.DiscoveryDtos.MerchantCategoryDto;
import com.restaurante.platform.discovery.dto.DiscoveryDtos.MerchantSectionDto;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Home Discovery v1; chaves de secção são identidades estáveis")
public record DiscoveryHomeResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<MerchantCategoryDto> categories,
        @Schema(
                        description = "Vazia em v1 enquanto geografia não for aplicada",
                        requiredMode = Schema.RequiredMode.REQUIRED)
        MerchantSectionDto nearby,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        MerchantSectionDto recommended,
        @Schema(
                        description = "Vazia enquanto FEATURED não tiver fonte persistente",
                        requiredMode = Schema.RequiredMode.REQUIRED)
        MerchantSectionDto featured) {}

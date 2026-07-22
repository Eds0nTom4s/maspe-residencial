package com.restaurante.platform.discovery.dto;

import com.restaurante.platform.discovery.dto.DiscoveryDtos.MerchantAddressDto;
import com.restaurante.platform.discovery.dto.DiscoveryDtos.MerchantAvailabilityDto;
import com.restaurante.platform.discovery.dto.DiscoveryDtos.MerchantCategoryDto;
import com.restaurante.platform.discovery.dto.DiscoveryDtos.MerchantContactDto;
import com.restaurante.platform.discovery.dto.DiscoveryDtos.MerchantPromotionDto;
import com.restaurante.platform.discovery.dto.DiscoveryDtos.MerchantRatingDto;
import com.restaurante.platform.discovery.dto.DiscoveryDtos.WeeklyScheduleDto;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Set;

@Schema(description = "Detalhe público de um comerciante publicável")
public record MerchantOverviewResponse(
        @Schema(description = "merchantId: slug público estável", requiredMode = Schema.RequiredMode.REQUIRED)
        String id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String name,
        String shortDescription,
        String fullDescription,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        MerchantCategoryDto category,
        String bannerUrl,
        String logoUrl,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        MerchantAvailabilityDto availability,
        @Schema(description = "Nunca null", requiredMode = Schema.RequiredMode.REQUIRED)
        Set<String> fulfillmentOptions,
        MerchantRatingDto rating,
        MerchantAddressDto address,
        MerchantContactDto contact,
        WeeklyScheduleDto schedule,
        MerchantPromotionDto promotion,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        boolean catalogAvailable) {}

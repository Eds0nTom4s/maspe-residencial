package com.restaurante.platform.discovery.mapper;

import com.restaurante.platform.discovery.domain.HomeDiscoveryContent;
import com.restaurante.platform.discovery.domain.MerchantAddress;
import com.restaurante.platform.discovery.domain.MerchantAvailability;
import com.restaurante.platform.discovery.domain.MerchantCategory;
import com.restaurante.platform.discovery.domain.MerchantContact;
import com.restaurante.platform.discovery.domain.MerchantLocation;
import com.restaurante.platform.discovery.domain.MerchantOverview;
import com.restaurante.platform.discovery.domain.MerchantPromotion;
import com.restaurante.platform.discovery.domain.MerchantRating;
import com.restaurante.platform.discovery.domain.MerchantSearchContent;
import com.restaurante.platform.discovery.domain.MerchantSection;
import com.restaurante.platform.discovery.domain.MerchantSummary;
import com.restaurante.platform.discovery.domain.MoneyAmount;
import com.restaurante.platform.discovery.domain.WeeklySchedule;
import com.restaurante.platform.discovery.dto.DiscoveryDtos.MerchantAddressDto;
import com.restaurante.platform.discovery.dto.DiscoveryDtos.MerchantAvailabilityDto;
import com.restaurante.platform.discovery.dto.DiscoveryDtos.MerchantCategoryDto;
import com.restaurante.platform.discovery.dto.DiscoveryDtos.MerchantContactDto;
import com.restaurante.platform.discovery.dto.DiscoveryDtos.MerchantLocationDto;
import com.restaurante.platform.discovery.dto.DiscoveryDtos.MerchantPromotionDto;
import com.restaurante.platform.discovery.dto.DiscoveryDtos.MerchantRatingDto;
import com.restaurante.platform.discovery.dto.DiscoveryDtos.MerchantSectionDto;
import com.restaurante.platform.discovery.dto.DiscoveryDtos.MerchantSummaryDto;
import com.restaurante.platform.discovery.dto.DiscoveryDtos.MoneyAmountDto;
import com.restaurante.platform.discovery.dto.DiscoveryDtos.WeeklyScheduleDto;
import com.restaurante.platform.discovery.dto.DiscoveryHomeResponse;
import com.restaurante.platform.discovery.dto.MerchantOverviewResponse;
import com.restaurante.platform.discovery.dto.MerchantSearchResponse;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class DiscoveryDtoMapper {

    public DiscoveryHomeResponse toResponse(HomeDiscoveryContent source) {
        return new DiscoveryHomeResponse(
                source.categories().stream().map(this::toDto).toList(),
                toDto(source.nearby()),
                toDto(source.recommended()),
                toDto(source.featured()));
    }

    public MerchantSearchResponse toResponse(MerchantSearchContent source) {
        return new MerchantSearchResponse(
                source.categories().stream().map(this::toDto).toList(),
                source.merchants().stream().map(this::toDto).toList(),
                source.page(),
                source.pageSize(),
                source.totalCount(),
                source.hasMore());
    }

    public MerchantOverviewResponse toResponse(MerchantOverview source) {
        return new MerchantOverviewResponse(
                source.id(),
                source.name(),
                source.shortDescription(),
                source.fullDescription(),
                toDto(source.category()),
                source.bannerUrl(),
                source.logoUrl(),
                toDto(source.availability()),
                source.fulfillmentOptions().stream()
                        .map(Enum::name)
                        .collect(Collectors.toUnmodifiableSet()),
                toDto(source.rating()),
                toDto(source.address()),
                toDto(source.contact()),
                toDto(source.schedule()),
                toDto(source.promotion()),
                source.catalogAvailable());
    }

    public MerchantSummaryDto toDto(MerchantSummary source) {
        return new MerchantSummaryDto(
                source.id(),
                source.name(),
                toDto(source.category()),
                source.shortDescription(),
                source.imageUrl(),
                toDto(source.availability()),
                source.fulfillmentOptions().stream()
                        .map(Enum::name)
                        .collect(Collectors.toUnmodifiableSet()),
                toDto(source.location()),
                source.estimatedPreparationMinutes(),
                toDto(source.rating()),
                source.popularity(),
                toDto(source.minimumOrderAmount()),
                toDto(source.promotion()),
                source.featured(),
                source.catalogAvailable());
    }

    private MerchantSectionDto toDto(MerchantSection source) {
        return new MerchantSectionDto(
                source.items().stream().map(this::toDto).toList(), source.hasMore());
    }

    private MerchantCategoryDto toDto(MerchantCategory source) {
        return new MerchantCategoryDto(source.id(), source.name());
    }

    private MerchantAvailabilityDto toDto(MerchantAvailability source) {
        return new MerchantAvailabilityDto(
                source.status().name(), source.minutesRemaining(), source.opensAt());
    }

    private MerchantAddressDto toDto(MerchantAddress source) {
        return source == null ? null : new MerchantAddressDto(source.displayName());
    }

    private MerchantLocationDto toDto(MerchantLocation source) {
        return source == null
                ? null
                : new MerchantLocationDto(
                        source.latitude(),
                        source.longitude(),
                        source.municipalityId(),
                        source.distanceMeters());
    }

    private MerchantPromotionDto toDto(MerchantPromotion source) {
        return source == null
                ? null
                : new MerchantPromotionDto(
                        source.id(), source.title(), source.description(), source.badge());
    }

    private MerchantContactDto toDto(MerchantContact source) {
        return source == null ? null : new MerchantContactDto(source.phone(), source.email());
    }

    private MerchantRatingDto toDto(MerchantRating source) {
        return source == null ? null : new MerchantRatingDto(source.value(), source.count());
    }

    private MoneyAmountDto toDto(MoneyAmount source) {
        return source == null ? null : new MoneyAmountDto(source.minorUnits(), source.currency());
    }

    private WeeklyScheduleDto toDto(WeeklySchedule source) {
        return source == null
                ? null
                : new WeeklyScheduleDto(
                        source.openDays(), source.opensAt(), source.closesAt());
    }
}

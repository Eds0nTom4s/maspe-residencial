package com.restaurante.platform.discovery.mapper;

import com.restaurante.platform.discovery.domain.FulfillmentOption;
import com.restaurante.platform.discovery.domain.MerchantAddress;
import com.restaurante.platform.discovery.domain.MerchantAvailability;
import com.restaurante.platform.discovery.domain.MerchantCategory;
import com.restaurante.platform.discovery.domain.MerchantContact;
import com.restaurante.platform.discovery.domain.MerchantLocation;
import com.restaurante.platform.discovery.domain.MerchantOverview;
import com.restaurante.platform.discovery.domain.MerchantPromotion;
import com.restaurante.platform.discovery.domain.MerchantRating;
import com.restaurante.platform.discovery.domain.MerchantSummary;
import com.restaurante.platform.discovery.domain.MoneyAmount;
import com.restaurante.platform.discovery.domain.WeeklySchedule;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class DiscoveryEntityMapper {

    public MerchantCategory toCategory(DiscoveryEntitySource.Category source) {
        return new MerchantCategory(source.id(), source.name());
    }

    public MerchantSummary toSummary(
            DiscoveryEntitySource.Merchant source,
            MerchantCategory category,
            boolean locationAvailable) {
        return new MerchantSummary(
                source.id(),
                source.name(),
                category,
                source.shortDescription(),
                source.imageUrl(),
                availability(source),
                source.fulfillmentOptions().stream()
                        .map(FulfillmentOption::valueOf)
                        .collect(Collectors.toUnmodifiableSet()),
                new MerchantLocation(
                        null,
                        null,
                        null,
                        locationAvailable ? source.distanceMeters() : null),
                source.estimatedPreparationMinutes(),
                rating(source),
                source.popularity(),
                source.minimumOrderMinorUnits() == null
                        ? null
                        : new MoneyAmount(source.minimumOrderMinorUnits(), "AOA"),
                promotion(source),
                source.featured(),
                !"servicos-viana".equals(source.id()));
    }

    public MerchantOverview toOverview(
            DiscoveryEntitySource.Merchant source, MerchantCategory category) {
        MerchantSummary summary = toSummary(source, category, false);
        boolean isService = "servicos-viana".equals(source.id());
        return new MerchantOverview(
                summary.id(),
                summary.name(),
                summary.shortDescription(),
                summary.shortDescription() == null
                        ? null
                        : summary.shortDescription()
                                + ". Informação demonstrativa para apoiar a escolha do comerciante.",
                category,
                null,
                summary.imageUrl(),
                summary.availability(),
                summary.fulfillmentOptions(),
                summary.rating(),
                new MerchantAddress(address(source.id(), category.name())),
                isService
                        ? null
                        : new MerchantContact(
                                "+244 900 000 000", "contacto@example.invalid"),
                isService
                        ? null
                        : new WeeklySchedule(
                                EnumSet.of(
                                        DayOfWeek.MONDAY,
                                        DayOfWeek.TUESDAY,
                                        DayOfWeek.WEDNESDAY,
                                        DayOfWeek.THURSDAY,
                                        DayOfWeek.FRIDAY,
                                        DayOfWeek.SATURDAY),
                                LocalTime.of(8, 0),
                                LocalTime.of(20, 0)),
                summary.promotion(),
                !isService);
    }

    private MerchantAvailability availability(DiscoveryEntitySource.Merchant source) {
        return switch (source.availability()) {
            case "OPEN" -> MerchantAvailability.open();
            case "CLOSING_SOON" ->
                    MerchantAvailability.closingSoon(source.minutesRemaining());
            case "OPENS_AT" -> MerchantAvailability.opensAt(source.opensAt());
            case "CLOSED" -> MerchantAvailability.closed();
            default -> MerchantAvailability.unknown();
        };
    }

    private MerchantRating rating(DiscoveryEntitySource.Merchant source) {
        return source.rating() == null
                ? null
                : new MerchantRating(source.rating(), source.ratingCount());
    }

    private MerchantPromotion promotion(DiscoveryEntitySource.Merchant source) {
        return source.promotionId() == null
                ? null
                : new MerchantPromotion(
                        source.promotionId(),
                        source.promotionTitle(),
                        source.promotionDescription(),
                        source.promotionBadge());
    }

    private String address(String merchantId, String categoryName) {
        return switch (merchantId) {
            case "sabor-maianga" -> "Rua demonstrativa — Maianga, Luanda";
            case "mercado-talatona" -> "Talatona, Luanda";
            default -> categoryName + " — Luanda";
        };
    }
}

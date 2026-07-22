package com.restaurante.platform.discovery.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.restaurante.platform.discovery.domain.HomeDiscoveryContent;
import com.restaurante.platform.discovery.domain.MerchantAvailability;
import com.restaurante.platform.discovery.domain.MerchantCategory;
import com.restaurante.platform.discovery.domain.MerchantSection;
import com.restaurante.platform.discovery.domain.MerchantSummary;
import com.restaurante.platform.discovery.dto.DiscoveryHomeResponse;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DiscoveryMapperTest {

    private final DiscoveryEntityMapper entityMapper = new DiscoveryEntityMapper();
    private final DiscoveryDtoMapper dtoMapper = new DiscoveryDtoMapper();

    @Test
    void mapsEntityToDomainBeforeMappingDomainToDto() {
        TestMerchantEntity entity = new TestMerchantEntity(
                "merchant-one",
                "Merchant One",
                "restaurant",
                "Descrição",
                null,
                "CLOSING_SOON",
                15,
                null,
                Set.of("DELIVERY"),
                900,
                20,
                4.5,
                10,
                100,
                250000L,
                "promo",
                "Promoção",
                null,
                "NOVO",
                true);
        MerchantCategory category = new MerchantCategory("restaurant", "Restaurantes");

        MerchantSummary domain = entityMapper.toSummary(entity, category, true);
        var dto = dtoMapper.toDto(domain);

        assertEquals(MerchantAvailability.Status.CLOSING_SOON, domain.availability().status());
        assertEquals(15, domain.availability().minutesRemaining());
        assertEquals(900, domain.location().distanceMeters());
        assertEquals("AOA", domain.minimumOrderAmount().currency());
        assertEquals("CLOSING_SOON", dto.availability().status());
        assertEquals(Set.of("DELIVERY"), dto.fulfillmentOptions());
        assertEquals("promo", dto.promotion().id());
        assertEquals(true, dto.catalogAvailable());
        assertNotSame(entity, domain);
        assertNotSame(domain, dto);
    }

    @Test
    void mapsOptionalValuesAndAllHomeSections() {
        MerchantSummary merchant = new MerchantSummary(
                "merchant",
                "Merchant",
                new MerchantCategory("services", "Serviços"),
                null,
                null,
                MerchantAvailability.opensAt(LocalTime.of(7, 30)),
                Set.of(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                false);
        MerchantSection section = new MerchantSection(List.of(merchant), false);
        HomeDiscoveryContent content = new HomeDiscoveryContent(
                List.of(merchant.category()), section, new MerchantSection(List.of(), false), section);

        DiscoveryHomeResponse response = dtoMapper.toResponse(content);

        assertEquals(1, response.categories().size());
        assertEquals(1, response.nearby().items().size());
        assertEquals("07:30", response.nearby().items().getFirst().availability().opensAt().toString());
        assertNull(response.nearby().items().getFirst().rating());
        assertEquals(false, response.nearby().items().getFirst().catalogAvailable());
        assertEquals(0, response.recommended().items().size());
    }

    private record TestMerchantEntity(
            String id,
            String name,
            String categoryId,
            String shortDescription,
            String imageUrl,
            String availability,
            Integer minutesRemaining,
            LocalTime opensAt,
            Set<String> fulfillmentOptions,
            int distanceMeters,
            Integer estimatedPreparationMinutes,
            Double rating,
            Integer ratingCount,
            int popularity,
            Long minimumOrderMinorUnits,
            String promotionId,
            String promotionTitle,
            String promotionDescription,
            String promotionBadge,
            boolean featured)
            implements DiscoveryEntitySource.Merchant {}
}

package com.restaurante.platform.discovery.mapper;

import com.restaurante.model.enums.TenantDeliveryPolicyStatus;
import com.restaurante.model.enums.TenantTipo;
import com.restaurante.model.enums.TipoUnidadeAtendimento;
import com.restaurante.platform.discovery.domain.FulfillmentOption;
import com.restaurante.platform.discovery.domain.MerchantAddress;
import com.restaurante.platform.discovery.domain.MerchantAvailability;
import com.restaurante.platform.discovery.domain.MerchantCategory;
import com.restaurante.platform.discovery.domain.MerchantLocation;
import com.restaurante.platform.discovery.domain.MerchantOverview;
import com.restaurante.platform.discovery.domain.MerchantSummary;
import com.restaurante.platform.discovery.repository.projection.MerchantDiscoveryProjection;
import com.restaurante.platform.discovery.repository.projection.MerchantLocationProjection;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Maps persisted read projections into the pure Discovery domain. */
@Component
public final class PersistentDiscoveryMapper {

    private static final MerchantCategory RESTAURANT =
            new MerchantCategory("restaurant", "Restaurantes");
    private static final MerchantCategory BAKERY =
            new MerchantCategory("bakery", "Pastelaria");
    private static final MerchantCategory DRINKS = new MerchantCategory("drinks", "Bebidas");
    private static final MerchantCategory MARKET = new MerchantCategory("market", "Mercado");
    private static final MerchantCategory SERVICES =
            new MerchantCategory("services", "Serviços");
    private static final List<MerchantCategory> CATEGORIES =
            List.of(RESTAURANT, BAKERY, DRINKS, MARKET, SERVICES);
    private static final Map<String, MerchantCategory> CATEGORIES_BY_ID = Map.of(
            RESTAURANT.id(), RESTAURANT,
            BAKERY.id(), BAKERY,
            DRINKS.id(), DRINKS,
            MARKET.id(), MARKET,
            SERVICES.id(), SERVICES);

    public List<MerchantCategory> categories() {
        return CATEGORIES;
    }

    public MerchantCategory category(String categoryId) {
        return CATEGORIES_BY_ID.get(categoryId);
    }

    public Set<TenantTipo> tenantTypes(MerchantCategory category) {
        if (category == null) {
            return EnumSet.allOf(TenantTipo.class);
        }
        return switch (category.id()) {
            case "restaurant" -> EnumSet.of(
                    TenantTipo.RESTAURANTE,
                    TenantTipo.FOOD_COURT,
                    TenantTipo.EVENTO,
                    TenantTipo.VENDEDOR_RUA);
            case "drinks" -> EnumSet.of(TenantTipo.BAR);
            case "market" -> EnumSet.of(TenantTipo.LOJA);
            case "services" -> EnumSet.of(TenantTipo.CLUBE, TenantTipo.INSTITUCIONAL);
            default -> EnumSet.noneOf(TenantTipo.class);
        };
    }

    public MerchantSummary toSummary(MerchantDiscoveryProjection source) {
        return new MerchantSummary(
                source.getMerchantId(),
                source.getName(),
                category(source.getTenantType()),
                null,
                blankToNull(source.getBannerUrl()),
                MerchantAvailability.unknown(),
                fulfillmentOptions(source),
                location(source),
                source.getPreparationTimeMinutes(),
                null,
                null,
                null,
                null,
                null,
                catalogAvailable(source));
    }

    public MerchantOverview toOverview(
            MerchantDiscoveryProjection source, MerchantLocationProjection location) {
        String description = location == null ? null : blankToNull(location.getDescription());
        return new MerchantOverview(
                source.getMerchantId(),
                source.getName(),
                description,
                description,
                category(source.getTenantType()),
                blankToNull(source.getBannerUrl()),
                location == null ? null : blankToNull(location.getLogoUrl()),
                MerchantAvailability.unknown(),
                fulfillmentOptions(source),
                null,
                address(source),
                null,
                null,
                null,
                catalogAvailable(source));
    }

    private MerchantCategory category(TenantTipo type) {
        if (type == null) {
            return SERVICES;
        }
        return switch (type) {
            case RESTAURANTE, FOOD_COURT, EVENTO, VENDEDOR_RUA -> RESTAURANT;
            case BAR -> DRINKS;
            case LOJA -> MARKET;
            case CLUBE, INSTITUCIONAL -> SERVICES;
        };
    }

    private Set<FulfillmentOption> fulfillmentOptions(MerchantDiscoveryProjection source) {
        EnumSet<FulfillmentOption> options = EnumSet.noneOf(FulfillmentOption.class);
        boolean deliveryPolicyActive =
                source.getDeliveryPolicyStatus() == TenantDeliveryPolicyStatus.ACTIVE;
        if (deliveryPolicyActive && Boolean.TRUE.equals(source.getDeliveryEnabled())) {
            options.add(FulfillmentOption.DELIVERY);
        }
        if ((deliveryPolicyActive && Boolean.TRUE.equals(source.getCustomerPickupAllowed()))
                || Boolean.TRUE.equals(source.getOperationPickupAllowed())) {
            options.add(FulfillmentOption.PICKUP);
        }
        if (supportsDineIn(source.getTenantType())) {
            options.add(FulfillmentOption.DINE_IN);
        }
        return Set.copyOf(options);
    }

    private boolean supportsDineIn(TenantTipo type) {
        return type == TenantTipo.RESTAURANTE
                || type == TenantTipo.BAR
                || type == TenantTipo.FOOD_COURT
                || type == TenantTipo.EVENTO
                || type == TenantTipo.CLUBE;
    }

    private MerchantAddress address(MerchantDiscoveryProjection source) {
        LinkedHashSet<String> parts = new LinkedHashSet<>();
        addIfPresent(parts, source.getMunicipality());
        addIfPresent(parts, source.getProvince());
        return parts.isEmpty() ? null : new MerchantAddress(String.join(" — ", parts));
    }

    private MerchantLocation location(MerchantDiscoveryProjection source) {
        String municipality = blankToNull(source.getMunicipality());
        return municipality == null ? null : new MerchantLocation(null, null, municipality, null);
    }

    private boolean catalogAvailable(MerchantDiscoveryProjection source) {
        return Boolean.TRUE.equals(source.getCatalogPublished())
                && source.getActiveCatalogItemCount() != null
                && source.getActiveCatalogItemCount() > 0;
    }

    private void addIfPresent(Set<String> parts, String value) {
        String normalized = blankToNull(value);
        if (normalized != null) {
            parts.add(normalized);
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

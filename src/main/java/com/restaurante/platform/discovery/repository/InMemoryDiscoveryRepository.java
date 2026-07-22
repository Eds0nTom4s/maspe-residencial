package com.restaurante.platform.discovery.repository;

import com.restaurante.platform.discovery.domain.DiscoveryError;
import com.restaurante.platform.discovery.domain.DiscoveryOrderBy;
import com.restaurante.platform.discovery.domain.DiscoveryResult;
import com.restaurante.platform.discovery.domain.HomeDiscoveryContent;
import com.restaurante.platform.discovery.domain.MerchantCategory;
import com.restaurante.platform.discovery.domain.MerchantOverview;
import com.restaurante.platform.discovery.domain.MerchantSearchContent;
import com.restaurante.platform.discovery.domain.MerchantSection;
import com.restaurante.platform.discovery.domain.MerchantSummary;
import com.restaurante.platform.discovery.dto.HomeDiscoveryRequest;
import com.restaurante.platform.discovery.dto.MerchantRequest;
import com.restaurante.platform.discovery.dto.SearchDiscoveryRequest;
import com.restaurante.platform.discovery.mapper.DiscoveryEntityMapper;
import java.text.Normalizer;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class InMemoryDiscoveryRepository implements DiscoveryRepository {

    private static final int HOME_SECTION_SIZE = 4;
    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");

    private static final List<InMemoryCategoryEntity> CATEGORY_ENTITIES =
            List.of(
                    new InMemoryCategoryEntity("restaurant", "Restaurantes"),
                    new InMemoryCategoryEntity("bakery", "Pastelaria"),
                    new InMemoryCategoryEntity("drinks", "Bebidas"),
                    new InMemoryCategoryEntity("market", "Mercado"),
                    new InMemoryCategoryEntity("services", "Serviços"));

    private static final List<InMemoryMerchantEntity> MERCHANT_ENTITIES =
            List.of(
                    merchant(
                            "sabor-maianga",
                            "Sabor da Maianga",
                            "restaurant",
                            "Sabores angolanos preparados no dia",
                            null,
                            "OPEN",
                            null,
                            null,
                            Set.of("DELIVERY", "PICKUP", "DINE_IN"),
                            450,
                            25,
                            4.7,
                            125,
                            940,
                            550000L,
                            "almoco-local",
                            "Menu do dia",
                            "Selecção especial ao almoço",
                            "DESTAQUE",
                            true),
                    merchant(
                            "doce-embondeiro",
                            "Doce Embondeiro",
                            "bakery",
                            "Bolos, pão e pastelaria artesanal",
                            "https://images.invalid/doce-embondeiro.jpg",
                            "CLOSING_SOON",
                            35,
                            null,
                            Set.of("PICKUP", "DELIVERY"),
                            900,
                            20,
                            4.5,
                            82,
                            760,
                            250000L,
                            null,
                            null,
                            null,
                            null,
                            true),
                    merchant(
                            "cafe-horizonte",
                            "Café Horizonte",
                            "drinks",
                            "Café, sumos naturais e encontros tranquilos",
                            null,
                            "OPENS_AT",
                            null,
                            LocalTime.of(7, 30),
                            Set.of("DINE_IN", "PICKUP"),
                            1250,
                            15,
                            4.3,
                            44,
                            530,
                            null,
                            null,
                            null,
                            null,
                            null,
                            false),
                    merchant(
                            "mercado-talatona",
                            "Mercado Talatona",
                            "market",
                            "Mercearia e essenciais para casa",
                            "https://images.invalid/mercado-talatona.jpg",
                            "OPEN",
                            null,
                            null,
                            Set.of("DELIVERY", "PICKUP"),
                            2400,
                            35,
                            4.6,
                            210,
                            1000,
                            1000000L,
                            "cabaz-semana",
                            "Cabaz da semana",
                            null,
                            "OPORTUNIDADE",
                            true),
                    merchant(
                            "servicos-viana",
                            "Serviços Viana",
                            "services",
                            "Reparações e assistência ao domicílio",
                            null,
                            "UNKNOWN",
                            null,
                            null,
                            Set.of("SERVICE"),
                            5100,
                            null,
                            null,
                            null,
                            310,
                            null,
                            null,
                            null,
                            null,
                            null,
                            false),
                    merchant(
                            "cantinho-kilamba",
                            "Cantinho do Kilamba",
                            "restaurant",
                            "Refeições familiares e grelhados",
                            null,
                            "CLOSED",
                            null,
                            null,
                            Set.of("DINE_IN", "PICKUP"),
                            7300,
                            40,
                            4.2,
                            61,
                            620,
                            400000L,
                            null,
                            null,
                            null,
                            null,
                            false),
                    merchant(
                            "fonte-fresca",
                            "Fonte Fresca",
                            "drinks",
                            "Água, sumos e bebidas para entrega",
                            null,
                            "OPEN",
                            null,
                            null,
                            Set.of("DELIVERY"),
                            1800,
                            18,
                            null,
                            null,
                            470,
                            null,
                            "entrega-bairro",
                            "Entrega no bairro",
                            "Condições apresentadas pelo comerciante",
                            null,
                            false),
                    merchant(
                            "paes-mutamba",
                            "Pães da Mutamba",
                            "bakery",
                            "Pão fresco e pequenos-almoços",
                            null,
                            "CLOSING_SOON",
                            20,
                            null,
                            Set.of("PICKUP"),
                            3200,
                            12,
                            4.8,
                            39,
                            680,
                            null,
                            null,
                            null,
                            null,
                            null,
                            false));

    private final DiscoveryEntityMapper entityMapper;
    private final DiscoveryScenarioProvider scenarioProvider;
    private final List<MerchantCategory> categories;
    private final Map<String, MerchantCategory> categoriesById;

    public InMemoryDiscoveryRepository(
            DiscoveryEntityMapper entityMapper, DiscoveryScenarioProvider scenarioProvider) {
        this.entityMapper = entityMapper;
        this.scenarioProvider = scenarioProvider;
        this.categories = CATEGORY_ENTITIES.stream().map(entityMapper::toCategory).toList();
        this.categoriesById = categories.stream()
                .collect(Collectors.toUnmodifiableMap(MerchantCategory::id, category -> category));
    }

    @Override
    public DiscoveryResult<HomeDiscoveryContent> home(HomeDiscoveryRequest request) {
        DiscoveryResult<HomeDiscoveryContent> scenario = scenarioFailure();
        if (scenario != null) {
            return scenario;
        }

        boolean hasLocation = hasLocation(request.latitude(), request.longitude(), request.municipalityId());
        List<MerchantSummary> items = summaries(hasLocation).stream()
                .filter(item -> matchesCategory(item, request.categoryId()))
                .sorted(comparator(request.sort()))
                .toList();
        List<MerchantSummary> featured = items.stream()
                .filter(item -> Boolean.TRUE.equals(item.featured()))
                .toList();
        List<MerchantSummary> candidates = items.stream()
                .filter(item -> !Boolean.TRUE.equals(item.featured()))
                .toList();
        List<MerchantSummary> nearby = hasLocation
                ? candidates.stream()
                        .sorted(comparator(DiscoveryOrderBy.NEAREST.name()))
                        .limit(HOME_SECTION_SIZE)
                        .toList()
                : List.of();
        List<MerchantSummary> recommended = hasLocation
                ? List.of()
                : candidates.stream()
                        .sorted(comparator(DiscoveryOrderBy.MOST_POPULAR.name()))
                        .limit(HOME_SECTION_SIZE)
                        .toList();
        HomeDiscoveryContent content = new HomeDiscoveryContent(
                categories,
                new MerchantSection(nearby, hasLocation && candidates.size() > nearby.size()),
                new MerchantSection(
                        recommended, !hasLocation && candidates.size() > recommended.size()),
                new MerchantSection(featured, false));
        if (scenarioProvider.currentScenario() == DiscoveryScenario.EMPTY || items.isEmpty()) {
            return new DiscoveryResult.Empty<>(content);
        }
        return new DiscoveryResult.Success<>(content);
    }

    @Override
    public DiscoveryResult<MerchantSearchContent> search(SearchDiscoveryRequest request) {
        DiscoveryResult<MerchantSearchContent> scenario = scenarioFailure();
        if (scenario != null) {
            return scenario;
        }

        boolean hasLocation = hasLocation(request.latitude(), request.longitude(), request.municipalityId());
        String query = normalize(request.query());
        List<MerchantSummary> filtered = summaries(hasLocation).stream()
                .filter(item -> matchesCategory(item, request.categoryId()))
                .filter(item -> matchesQuery(item, query))
                .sorted(comparator(request.sort()))
                .toList();
        int from = (int) Math.min(
                (long) request.page() * request.pageSize(), filtered.size());
        int to = Math.min(from + request.pageSize(), filtered.size());
        MerchantSearchContent content = new MerchantSearchContent(
                categories,
                filtered.subList(from, to),
                request.page(),
                request.pageSize(),
                filtered.size(),
                to < filtered.size());
        if (scenarioProvider.currentScenario() == DiscoveryScenario.EMPTY
                || content.merchants().isEmpty()) {
            return new DiscoveryResult.Empty<>(content);
        }
        return new DiscoveryResult.Success<>(content);
    }

    @Override
    public DiscoveryResult<MerchantOverview> merchant(MerchantRequest request) {
        DiscoveryResult<MerchantOverview> scenario = scenarioFailure();
        if (scenario != null) {
            return scenario;
        }
        if (scenarioProvider.currentScenario() == DiscoveryScenario.EMPTY) {
            return notFound(request.merchantId());
        }
        return MERCHANT_ENTITIES.stream()
                .filter(entity -> entity.id().equals(request.merchantId()))
                .findFirst()
                .<DiscoveryResult<MerchantOverview>>map(
                        entity -> new DiscoveryResult.Success<>(
                                entityMapper.toOverview(entity, categoriesById.get(entity.categoryId()))))
                .orElseGet(() -> notFound(request.merchantId()));
    }

    public List<String> merchantIds() {
        return MERCHANT_ENTITIES.stream().map(InMemoryMerchantEntity::id).toList();
    }

    private <T> DiscoveryResult<T> scenarioFailure() {
        return switch (scenarioProvider.currentScenario()) {
            case ERROR -> new DiscoveryResult.Error<>(
                    DiscoveryError.UNKNOWN, "Não foi possível concluir o Discovery.");
            case OFFLINE -> new DiscoveryResult.Error<>(
                    DiscoveryError.SERVICE_UNAVAILABLE,
                    "O serviço Discovery está temporariamente indisponível.");
            default -> null;
        };
    }

    private DiscoveryResult<MerchantOverview> notFound(String merchantId) {
        return new DiscoveryResult.Error<>(
                DiscoveryError.NOT_FOUND,
                "Comerciante não encontrado.");
    }

    private List<MerchantSummary> summaries(boolean locationAvailable) {
        if (scenarioProvider.currentScenario() == DiscoveryScenario.EMPTY) {
            return List.of();
        }
        return MERCHANT_ENTITIES.stream()
                .map(entity -> entityMapper.toSummary(
                        entity, categoriesById.get(entity.categoryId()), locationAvailable))
                .toList();
    }

    private boolean matchesCategory(MerchantSummary merchant, String categoryId) {
        return categoryId == null || merchant.category().id().equals(categoryId);
    }

    private boolean matchesQuery(MerchantSummary merchant, String query) {
        if (query.isBlank()) {
            return true;
        }
        return normalize(merchant.name()).contains(query)
                || normalize(merchant.shortDescription()).contains(query)
                || normalize(merchant.category().name()).contains(query);
    }

    private Comparator<MerchantSummary> comparator(String sort) {
        return switch (DiscoveryOrderBy.valueOf(sort)) {
            case NEAREST -> Comparator.comparing(
                            (MerchantSummary item) -> item.location().distanceMeters() == null)
                    .thenComparing(
                            item -> item.location().distanceMeters(),
                            Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(MerchantSummary::id);
            case TOP_RATED -> Comparator.comparing(
                            (MerchantSummary item) ->
                                    item.rating() == null ? null : item.rating().value(),
                            Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(
                            item -> item.rating() == null ? null : item.rating().count(),
                            Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(MerchantSummary::id);
            case MOST_POPULAR -> Comparator.comparingInt(MerchantSummary::popularity)
                    .reversed()
                    .thenComparing(MerchantSummary::id);
            case FEATURED -> Comparator.comparing(MerchantSummary::featured)
                    .reversed()
                    .thenComparing(Comparator.comparingInt(MerchantSummary::popularity).reversed())
                    .thenComparing(MerchantSummary::id);
            case NAME -> Comparator.comparing((MerchantSummary item) -> normalize(item.name()))
                    .thenComparing(MerchantSummary::id);
        };
    }

    private boolean hasLocation(Double latitude, Double longitude, String municipalityId) {
        return (latitude != null && longitude != null) || municipalityId != null;
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        String decomposed = Normalizer.normalize(value.trim(), Normalizer.Form.NFD);
        return DIACRITICS.matcher(decomposed).replaceAll("").toLowerCase(Locale.ROOT);
    }

    private static InMemoryMerchantEntity merchant(
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
            boolean featured) {
        return new InMemoryMerchantEntity(
                id,
                name,
                categoryId,
                shortDescription,
                imageUrl,
                availability,
                minutesRemaining,
                opensAt,
                fulfillmentOptions,
                distanceMeters,
                estimatedPreparationMinutes,
                rating,
                ratingCount,
                popularity,
                minimumOrderMinorUnits,
                promotionId,
                promotionTitle,
                promotionDescription,
                promotionBadge,
                featured);
    }
}

package com.restaurante.android.discovery.service;

import com.restaurante.android.api.error.AndroidPublicApiException;
import com.restaurante.android.api.error.AndroidPublicErrorCode;
import com.restaurante.android.discovery.dto.AndroidDiscoveryHomeResponse;
import com.restaurante.android.discovery.dto.AndroidDiscoverySearchResponse;
import com.restaurante.android.discovery.dto.AndroidMerchantAvailability;
import com.restaurante.android.discovery.dto.AndroidMerchantDetailResponse;
import com.restaurante.android.discovery.dto.AndroidMerchantSectionResponse;
import com.restaurante.android.discovery.dto.AndroidMerchantSummaryResponse;
import com.restaurante.android.discovery.repository.AndroidDiscoveryMerchantProjection;
import com.restaurante.android.discovery.repository.AndroidDiscoveryReadRepository;
import com.restaurante.android.discovery.validation.ValidatedDiscoveryQuery;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AndroidDiscoveryService {

    private static final AndroidMerchantSectionResponse EMPTY_SECTION =
            new AndroidMerchantSectionResponse(List.of(), false);

    private final AndroidDiscoveryReadRepository repository;
    private final MerchantDiscoveryPublicationPolicy publicationPolicy;

    public AndroidDiscoveryService(
            AndroidDiscoveryReadRepository repository,
            MerchantDiscoveryPublicationPolicy publicationPolicy) {
        this.repository = repository;
        this.publicationPolicy = publicationPolicy;
    }

    public AndroidDiscoveryHomeResponse home(ValidatedDiscoveryQuery query) {
        // No recommendation, geo, featured or merchant-category source is canonical yet.
        return new AndroidDiscoveryHomeResponse(
                List.of(), EMPTY_SECTION, EMPTY_SECTION, EMPTY_SECTION);
    }

    public AndroidDiscoverySearchResponse search(ValidatedDiscoveryQuery query) {
        try {
            Page<AndroidDiscoveryMerchantProjection> page = repository.findPublicMerchants(
                    publicationPolicy.requiredTenantState(),
                    publicationPolicy.requiredBusinessAccountState(),
                    publicationPolicy.requiredSubscriptionStateForCanonicalAccount(),
                    escapeLike(query.query()),
                    query.municipality() == null ? null : query.municipality().toLowerCase(Locale.ROOT),
                    PageRequest.of(query.page(), query.pageSize()));
            List<AndroidMerchantSummaryResponse> merchants = page.getContent().stream()
                    .map(this::summary)
                    .toList();
            return new AndroidDiscoverySearchResponse(
                    List.of(), merchants, query.page(), query.pageSize(),
                    page.getTotalElements(), page.hasNext());
        } catch (DataAccessException exception) {
            throw unavailable();
        }
    }

    public AndroidMerchantDetailResponse detail(UUID merchantPublicId) {
        try {
            AndroidDiscoveryMerchantProjection source = repository.findPublicMerchant(
                            merchantPublicId,
                            publicationPolicy.requiredTenantState(),
                            publicationPolicy.requiredBusinessAccountState(),
                            publicationPolicy.requiredSubscriptionStateForCanonicalAccount())
                    .orElseThrow(this::notFound);
            AndroidMerchantSummaryResponse summary = summary(source);
            return new AndroidMerchantDetailResponse(
                    summary.merchantId(), summary.name(), summary.availability(),
                    summary.fulfillmentOptions(), summary.distanceMeters(), summary.rating(),
                    summary.popularityScore(), summary.featured(), summary.catalogAvailable(),
                    null, null, null);
        } catch (DataAccessException exception) {
            throw unavailable();
        }
    }

    private AndroidMerchantSummaryResponse summary(AndroidDiscoveryMerchantProjection source) {
        boolean catalogAvailable = Boolean.TRUE.equals(source.getCatalogPublished())
                && source.getActiveCatalogItemCount() != null
                && source.getActiveCatalogItemCount() > 0;
        return new AndroidMerchantSummaryResponse(
                source.getMerchantId(), source.getName().trim(),
                AndroidMerchantAvailability.UNKNOWN, List.of(),
                null, null, null, false, catalogAvailable);
    }

    private String escapeLike(String query) {
        if (query == null || query.isEmpty()) {
            return "";
        }
        return query.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    private AndroidPublicApiException notFound() {
        return new AndroidPublicApiException(
                AndroidPublicErrorCode.MERCHANT_NOT_FOUND,
                HttpStatus.NOT_FOUND,
                "Merchant não encontrado.",
                false,
                List.of());
    }

    private AndroidPublicApiException unavailable() {
        return new AndroidPublicApiException(
                AndroidPublicErrorCode.SERVICE_UNAVAILABLE,
                HttpStatus.SERVICE_UNAVAILABLE,
                "Discovery temporariamente indisponível.",
                true,
                List.of());
    }
}

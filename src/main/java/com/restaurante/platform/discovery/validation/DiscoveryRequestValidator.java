package com.restaurante.platform.discovery.validation;

import com.restaurante.platform.discovery.domain.DiscoveryError;
import com.restaurante.platform.discovery.domain.DiscoveryOrderBy;
import com.restaurante.platform.discovery.dto.HomeDiscoveryRequest;
import com.restaurante.platform.discovery.dto.MerchantRequest;
import com.restaurante.platform.discovery.dto.SearchDiscoveryRequest;
import com.restaurante.platform.discovery.exception.DiscoveryValidationException;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class DiscoveryRequestValidator {

    public static final int DEFAULT_PAGE = 0;
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_QUERY_LENGTH = 100;
    private static final int MAX_MERCHANT_ID_LENGTH = 100;
    private static final int MAX_FILTER_ID_LENGTH = 120;
    private static final Pattern MERCHANT_ID =
            Pattern.compile("^[a-z0-9]+(?:-[a-z0-9]+)*$");
    private static final Pattern CATEGORY_ID = MERCHANT_ID;
    private static final Pattern MUNICIPALITY_ID = Pattern.compile(
            "^[\\p{L}\\p{N}]+(?:[ .'-][\\p{L}\\p{N}]+)*$");
    private static final Set<String> KNOWN_SORTS = Set.of(
            DiscoveryOrderBy.NEAREST.name(),
            DiscoveryOrderBy.TOP_RATED.name(),
            DiscoveryOrderBy.MOST_POPULAR.name(),
            DiscoveryOrderBy.FEATURED.name(),
            DiscoveryOrderBy.NAME.name());
    private static final Set<String> SUPPORTED_SORTS = Set.of(DiscoveryOrderBy.NAME.name());

    public HomeDiscoveryRequest validate(HomeDiscoveryRequest request) {
        validateCoordinates(request.latitude(), request.longitude());
        return new HomeDiscoveryRequest(
                request.latitude(),
                request.longitude(),
                municipalityId(request.municipalityId()),
                categoryId(request.categoryId()),
                page(request.page()),
                pageSize(request.pageSize()),
                sort(request.sort()));
    }

    public SearchDiscoveryRequest validate(SearchDiscoveryRequest request) {
        validateCoordinates(request.latitude(), request.longitude());
        String query = request.query() == null ? "" : request.query().trim();
        if (query.length() > MAX_QUERY_LENGTH) {
            throw invalid("query deve ter no máximo 100 caracteres.");
        }
        return new SearchDiscoveryRequest(
                query,
                categoryId(request.categoryId()),
                request.latitude(),
                request.longitude(),
                municipalityId(request.municipalityId()),
                page(request.page()),
                pageSize(request.pageSize()),
                sort(request.sort()));
    }

    public MerchantRequest validate(MerchantRequest request) {
        String merchantId = request.merchantId() == null ? "" : request.merchantId().trim();
        if (merchantId.isEmpty()
                || merchantId.length() > MAX_MERCHANT_ID_LENGTH
                || !MERCHANT_ID.matcher(merchantId).matches()) {
            throw invalid("merchantId inválido.");
        }
        return new MerchantRequest(merchantId);
    }

    private int page(Integer value) {
        int page = value == null ? DEFAULT_PAGE : value;
        if (page < 0) {
            throw invalid("page deve ser maior ou igual a 0.");
        }
        return page;
    }

    private int pageSize(Integer value) {
        int pageSize = value == null ? DEFAULT_PAGE_SIZE : value;
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw invalid("pageSize deve estar entre 1 e 100.");
        }
        return pageSize;
    }

    private String sort(String value) {
        String sort = value == null || value.isBlank()
                ? DiscoveryOrderBy.NAME.name()
                : value.trim().toUpperCase(Locale.ROOT);
        if (!KNOWN_SORTS.contains(sort)) {
            throw invalid("sort inválido. Valor suportado: NAME.");
        }
        if (!SUPPORTED_SORTS.contains(sort)) {
            throw new DiscoveryValidationException(
                    DiscoveryError.SORT_NOT_SUPPORTED,
                    "sort '" + sort + "' ainda não possui fonte persistente suportada.");
        }
        return sort;
    }

    private String categoryId(String value) {
        String categoryId = trimToNull(value);
        if (categoryId != null
                && (categoryId.length() > MAX_FILTER_ID_LENGTH
                        || !CATEGORY_ID.matcher(categoryId).matches())) {
            throw invalid("categoryId inválido.");
        }
        return categoryId;
    }

    private String municipalityId(String value) {
        String municipalityId = trimToNull(value);
        if (municipalityId != null
                && (municipalityId.length() > MAX_FILTER_ID_LENGTH
                        || !MUNICIPALITY_ID.matcher(municipalityId).matches())) {
            throw invalid("municipalityId inválido.");
        }
        return municipalityId;
    }

    private void validateCoordinates(Double latitude, Double longitude) {
        if ((latitude == null) != (longitude == null)) {
            throw invalid("latitude e longitude devem ser informadas em conjunto.");
        }
        if (latitude != null && (!Double.isFinite(latitude) || latitude < -90 || latitude > 90)) {
            throw invalid("latitude deve estar entre -90 e 90.");
        }
        if (longitude != null
                && (!Double.isFinite(longitude) || longitude < -180 || longitude > 180)) {
            throw invalid("longitude deve estar entre -180 e 180.");
        }
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private DiscoveryValidationException invalid(String message) {
        return new DiscoveryValidationException(message);
    }
}

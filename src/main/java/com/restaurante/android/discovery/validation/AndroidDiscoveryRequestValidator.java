package com.restaurante.android.discovery.validation;

import com.restaurante.android.api.error.AndroidPublicApiException;
import com.restaurante.android.api.error.AndroidPublicErrorCode;
import com.restaurante.android.api.error.AndroidPublicFieldError;
import com.restaurante.android.foundation.identity.PublicIdSupport;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public final class AndroidDiscoveryRequestValidator {

    private static final Set<String> HOME_PARAMETERS = Set.of(
            "latitude", "longitude", "municipality", "categoryId", "page", "pageSize", "sort");
    private static final Set<String> SEARCH_PARAMETERS = Set.of(
            "query", "categoryId", "onlyOpen", "fulfillmentOptions", "latitude", "longitude",
            "municipality", "sort", "page", "pageSize");
    private static final Set<String> KNOWN_SORTS = Set.of(
            "NAME", "FEATURED", "NEAREST", "TOP_RATED", "MOST_POPULAR");
    private static final Set<String> FULFILLMENT = Set.of("PICKUP", "DELIVERY");
    private static final Pattern MUNICIPALITY = Pattern.compile(
            "^[\\p{L}\\p{N}]+(?:[ .'-][\\p{L}\\p{N}]+)*$");

    public ValidatedDiscoveryQuery home(HttpServletRequest request) {
        validateParameterShape(request, HOME_PARAMETERS, Set.of());
        return common(request, "", false);
    }

    public ValidatedDiscoveryQuery search(HttpServletRequest request) {
        validateParameterShape(request, SEARCH_PARAMETERS, Set.of("fulfillmentOptions"));
        String query = trim(request.getParameter("query"));
        if (query != null && query.length() > 100) {
            throw invalid("/query", "MAX_LENGTH", "query deve ter no máximo 100 caracteres.");
        }
        validateOnlyOpen(request.getParameter("onlyOpen"));
        validateFulfillment(request.getParameterValues("fulfillmentOptions"));
        return common(request, query == null ? "" : query, true);
    }

    public void noQueryParameters(HttpServletRequest request) {
        validateParameterShape(request, Set.of(), Set.of());
    }

    private ValidatedDiscoveryQuery common(
            HttpServletRequest request, String query, boolean search) {
        Double latitude = decimal(request.getParameter("latitude"), "/latitude", -90, 90);
        Double longitude = decimal(request.getParameter("longitude"), "/longitude", -180, 180);
        if ((latitude == null) != (longitude == null)) {
            throw invalid(
                    latitude == null ? "/latitude" : "/longitude",
                    "COORDINATE_PAIR_REQUIRED",
                    "latitude e longitude devem ser informadas em conjunto.");
        }

        String municipality = trim(request.getParameter("municipality"));
        if (municipality != null
                && (municipality.length() > 120 || !MUNICIPALITY.matcher(municipality).matches())) {
            throw invalid("/municipality", "INVALID_VALUE", "municipality inválido.");
        }

        validateCategory(request.getParameter("categoryId"));
        int page = integer(request.getParameter("page"), "/page", 0, Integer.MAX_VALUE, 0);
        int pageSize = integer(request.getParameter("pageSize"), "/pageSize", 1, 100, 20);
        String sort = sort(request.getParameter("sort"));
        return new ValidatedDiscoveryQuery(
                query, municipality, latitude, longitude, page, pageSize, sort);
    }

    private void validateParameterShape(
            HttpServletRequest request, Set<String> allowed, Set<String> repeatable) {
        TreeSet<String> unknown = new TreeSet<>(request.getParameterMap().keySet());
        unknown.removeAll(allowed);
        if (!unknown.isEmpty()) {
            String field = unknown.first();
            throw invalid("/" + field, "UNKNOWN_PARAMETER", "Parâmetro desconhecido.");
        }
        for (String name : allowed) {
            String[] values = request.getParameterValues(name);
            if (values != null && values.length > 1 && !repeatable.contains(name)) {
                throw invalid("/" + name, "REPEATED_PARAMETER", "Parâmetro repetido.");
            }
        }
    }

    private void validateCategory(String raw) {
        String value = trim(raw);
        if (value == null) {
            return;
        }
        try {
            PublicIdSupport.parseCanonical(value);
        } catch (IllegalArgumentException exception) {
            throw invalid("/categoryId", "INVALID_UUID", "categoryId deve ser UUID v4 canónico.");
        }
        throw invalid(
                "/categoryId",
                "CAPABILITY_NOT_SUPPORTED",
                "Filtro de categoria de merchant ainda não é suportado.");
    }

    private void validateOnlyOpen(String raw) {
        String value = trim(raw);
        if (value == null || "false".equalsIgnoreCase(value)) {
            return;
        }
        if (!"true".equalsIgnoreCase(value)) {
            throw invalid("/onlyOpen", "INVALID_VALUE", "onlyOpen deve ser true ou false.");
        }
        throw invalid(
                "/onlyOpen", "CAPABILITY_NOT_SUPPORTED", "onlyOpen requer horário canónico.");
    }

    private void validateFulfillment(String[] rawValues) {
        if (rawValues == null) {
            return;
        }
        List<String> values = Arrays.stream(rawValues)
                .flatMap(value -> Arrays.stream(value.split(",")))
                .map(this::trim)
                .filter(value -> value != null)
                .map(value -> value.toUpperCase(Locale.ROOT))
                .toList();
        if (values.isEmpty()) {
            return;
        }
        if (values.stream().anyMatch(value -> !FULFILLMENT.contains(value))) {
            throw invalid("/fulfillmentOptions", "INVALID_VALUE", "Fulfillment inválido.");
        }
        throw invalid(
                "/fulfillmentOptions",
                "CAPABILITY_NOT_SUPPORTED",
                "Filtro de fulfillment ainda não é suportado.");
    }

    private String sort(String raw) {
        String value = trim(raw);
        String normalized = value == null ? "NAME" : value.toUpperCase(Locale.ROOT);
        if (!KNOWN_SORTS.contains(normalized)) {
            throw invalid("/sort", "INVALID_VALUE", "sort inválido.");
        }
        if (!"NAME".equals(normalized)) {
            throw new AndroidPublicApiException(
                    AndroidPublicErrorCode.SORT_NOT_SUPPORTED,
                    HttpStatus.BAD_REQUEST,
                    "O sort solicitado não possui fonte persistente suportada.",
                    false,
                    List.of(new AndroidPublicFieldError(
                            "/sort", "SORT_NOT_SUPPORTED", "Somente NAME é suportado.")));
        }
        return normalized;
    }

    private Double decimal(String raw, String field, double min, double max) {
        String value = trim(raw);
        if (value == null) {
            return null;
        }
        try {
            double parsed = Double.parseDouble(value);
            if (!Double.isFinite(parsed) || parsed < min || parsed > max) {
                throw new NumberFormatException();
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw invalid(field, "OUT_OF_RANGE", "Coordenada inválida.");
        }
    }

    private int integer(String raw, String field, int min, int max, int defaultValue) {
        String value = trim(raw);
        if (value == null) {
            return defaultValue;
        }
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < min || parsed > max) {
                throw new NumberFormatException();
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw invalid(field, "OUT_OF_RANGE", "Valor inteiro fora do intervalo permitido.");
        }
    }

    private String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private AndroidPublicApiException invalid(String field, String code, String message) {
        return new AndroidPublicApiException(
                AndroidPublicErrorCode.INVALID_REQUEST,
                HttpStatus.BAD_REQUEST,
                "O pedido contém dados inválidos.",
                false,
                List.of(new AndroidPublicFieldError(field, code, message)));
    }
}

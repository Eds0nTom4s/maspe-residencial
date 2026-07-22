package com.restaurante.platform.discovery.validation;

import com.restaurante.platform.discovery.domain.DiscoveryError;
import com.restaurante.platform.discovery.exception.DiscoveryApiException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.stereotype.Component;

/** Enforces the exact public query-parameter allowlist instead of silently ignoring filters. */
@Component
public final class DiscoveryHttpParameterValidator {

    private static final Set<String> HOME_PARAMETERS = Set.of(
            "latitude", "longitude", "municipalityId", "categoryId", "page", "pageSize", "sort");
    private static final Set<String> SEARCH_PARAMETERS = Set.of(
            "query",
            "categoryId",
            "latitude",
            "longitude",
            "municipalityId",
            "page",
            "pageSize",
            "sort");

    public void validateHome(HttpServletRequest request) {
        validate(request, HOME_PARAMETERS);
    }

    public void validateSearch(HttpServletRequest request) {
        validate(request, SEARCH_PARAMETERS);
    }

    public void validateMerchant(HttpServletRequest request) {
        validate(request, Set.of());
    }

    private void validate(HttpServletRequest request, Set<String> allowed) {
        TreeSet<String> unsupported = new TreeSet<>(request.getParameterMap().keySet());
        unsupported.removeAll(allowed);
        if (!unsupported.isEmpty()) {
            throw new DiscoveryApiException(
                    DiscoveryError.INVALID_REQUEST,
                    "Parâmetro não suportado no contrato v1: " + unsupported.first() + ".");
        }
        for (String parameter : allowed) {
            String[] values = request.getParameterValues(parameter);
            if (values != null && values.length > 1) {
                throw new DiscoveryApiException(
                        DiscoveryError.INVALID_REQUEST,
                        "Parâmetro repetido no contrato v1: " + parameter + ".");
            }
        }
    }
}

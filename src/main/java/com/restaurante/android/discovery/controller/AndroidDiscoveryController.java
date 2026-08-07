package com.restaurante.android.discovery.controller;

import com.restaurante.android.api.AndroidPublicApiController;
import com.restaurante.android.api.error.AndroidPublicApiException;
import com.restaurante.android.api.error.AndroidPublicErrorCode;
import com.restaurante.android.api.error.AndroidPublicFieldError;
import com.restaurante.android.discovery.dto.AndroidDiscoveryHomeResponse;
import com.restaurante.android.discovery.dto.AndroidDiscoverySearchResponse;
import com.restaurante.android.discovery.dto.AndroidMerchantDetailResponse;
import com.restaurante.android.discovery.http.AndroidDiscoveryHttpResponseFactory;
import com.restaurante.android.discovery.http.AndroidDiscoveryRequestGuard;
import com.restaurante.android.discovery.http.DiscoveryRateLimitDecision;
import com.restaurante.android.discovery.service.AndroidDiscoveryService;
import com.restaurante.android.discovery.validation.AndroidDiscoveryRequestValidator;
import com.restaurante.android.discovery.validation.ValidatedDiscoveryQuery;
import com.restaurante.android.foundation.identity.PublicIdSupport;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

@AndroidPublicApiController
@RequestMapping("/v1/discovery")
public final class AndroidDiscoveryController {

    private final AndroidDiscoveryService service;
    private final AndroidDiscoveryRequestValidator validator;
    private final AndroidDiscoveryRequestGuard requestGuard;
    private final AndroidDiscoveryHttpResponseFactory responses;

    public AndroidDiscoveryController(
            AndroidDiscoveryService service,
            AndroidDiscoveryRequestValidator validator,
            AndroidDiscoveryRequestGuard requestGuard,
            AndroidDiscoveryHttpResponseFactory responses) {
        this.service = service;
        this.validator = validator;
        this.requestGuard = requestGuard;
        this.responses = responses;
    }

    @GetMapping("/home")
    public ResponseEntity<AndroidDiscoveryHomeResponse> home(HttpServletRequest request) {
        DiscoveryRateLimitDecision rateLimit = requestGuard.check(request, "home");
        ValidatedDiscoveryQuery query = validator.home(request);
        return responses.cacheable(
                service.home(query), request.getHeader(HttpHeaders.IF_NONE_MATCH), rateLimit);
    }

    @GetMapping("/search")
    public ResponseEntity<AndroidDiscoverySearchResponse> search(HttpServletRequest request) {
        DiscoveryRateLimitDecision rateLimit = requestGuard.check(request, "search");
        ValidatedDiscoveryQuery query = validator.search(request);
        return responses.cacheable(
                service.search(query), request.getHeader(HttpHeaders.IF_NONE_MATCH), rateLimit);
    }

    @GetMapping("/merchants/{merchantId}")
    public ResponseEntity<AndroidMerchantDetailResponse> detail(
            @PathVariable String merchantId, HttpServletRequest request) {
        DiscoveryRateLimitDecision rateLimit = requestGuard.check(request, "detail");
        validator.noQueryParameters(request);
        UUID publicId;
        try {
            publicId = PublicIdSupport.parseCanonical(merchantId);
        } catch (IllegalArgumentException exception) {
            throw new AndroidPublicApiException(
                    AndroidPublicErrorCode.INVALID_REQUEST,
                    HttpStatus.BAD_REQUEST,
                    "O pedido contém dados inválidos.",
                    false,
                    List.of(new AndroidPublicFieldError(
                            "/merchantId", "INVALID_UUID", "merchantId deve ser UUID v4 canónico.")));
        }
        return responses.cacheable(
                service.detail(publicId), request.getHeader(HttpHeaders.IF_NONE_MATCH), rateLimit);
    }
}

package com.restaurante.platform.discovery.controller;

import io.swagger.v3.oas.models.Operation;
import java.util.List;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;

/** Prevents public Discovery operations from inheriting the API-wide bearerAuth requirement. */
@Component
final class DiscoveryOpenApiCustomizer implements OperationCustomizer {

    @Override
    public Operation customize(Operation operation, HandlerMethod handlerMethod) {
        if (DiscoveryController.class.isAssignableFrom(handlerMethod.getBeanType())) {
            operation.setSecurity(List.of());
        }
        return operation;
    }
}

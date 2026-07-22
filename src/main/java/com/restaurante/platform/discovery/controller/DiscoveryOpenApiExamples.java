package com.restaurante.platform.discovery.controller;

/** Public, privacy-safe JSON examples used by the generated Discovery OpenAPI contract. */
final class DiscoveryOpenApiExamples {

    static final String HOME_WITH_DATA = """
            {
              "categories": [{"id": "restaurant", "name": "Restaurantes"}],
              "nearby": {"items": [], "hasMore": false},
              "recommended": {
                "items": [{
                  "id": "sabor-maianga",
                  "name": "Sabor da Maianga",
                  "category": {"id": "restaurant", "name": "Restaurantes"},
                  "availability": {"status": "UNKNOWN"},
                  "fulfillmentOptions": ["DINE_IN", "PICKUP"],
                  "location": {"municipalityId": "Maianga"},
                  "catalogAvailable": true
                }],
                "hasMore": false
              },
              "featured": {"items": [], "hasMore": false}
            }
            """;

    static final String HOME_EMPTY = """
            {
              "categories": [],
              "nearby": {"items": [], "hasMore": false},
              "recommended": {"items": [], "hasMore": false},
              "featured": {"items": [], "hasMore": false}
            }
            """;

    static final String SEARCH_WITH_DATA = """
            {
              "categories": [{"id": "restaurant", "name": "Restaurantes"}],
              "merchants": [{
                "id": "sabor-maianga",
                "name": "Sabor da Maianga",
                "category": {"id": "restaurant", "name": "Restaurantes"},
                "availability": {"status": "UNKNOWN"},
                "fulfillmentOptions": ["DINE_IN", "PICKUP"],
                "location": {"municipalityId": "Maianga"},
                "catalogAvailable": true
              }],
              "page": 0,
              "pageSize": 20,
              "totalCount": 1,
              "hasMore": false
            }
            """;

    static final String SEARCH_EMPTY = """
            {
              "categories": [],
              "merchants": [],
              "page": 0,
              "pageSize": 20,
              "totalCount": 0,
              "hasMore": false
            }
            """;

    static final String MERCHANT_DETAIL = """
            {
              "id": "sabor-maianga",
              "name": "Sabor da Maianga",
              "fullDescription": "Cozinha angolana",
              "category": {"id": "restaurant", "name": "Restaurantes"},
              "availability": {"status": "UNKNOWN"},
              "fulfillmentOptions": ["DINE_IN", "PICKUP"],
              "address": {"displayName": "Maianga — Luanda"},
              "catalogAvailable": true
            }
            """;

    static final String INVALID_REQUEST = """
            {
              "timestamp": "2026-07-21T08:00:00Z",
              "status": 400,
              "error": "Bad Request",
              "code": "INVALID_REQUEST",
              "message": "page deve ser maior ou igual a 0.",
              "path": "/api/v1/discovery/search"
            }
            """;

    static final String UNSUPPORTED_SORT = """
            {
              "timestamp": "2026-07-21T08:00:00Z",
              "status": 400,
              "error": "Bad Request",
              "code": "SORT_NOT_SUPPORTED",
              "message": "sort 'FEATURED' ainda não possui fonte persistente suportada.",
              "path": "/api/v1/discovery/search"
            }
            """;

    static final String NOT_FOUND = """
            {
              "timestamp": "2026-07-21T08:00:00Z",
              "status": 404,
              "error": "Not Found",
              "code": "NOT_FOUND",
              "message": "Comerciante não encontrado.",
              "path": "/api/v1/discovery/merchant/inexistente"
            }
            """;

    static final String SERVICE_UNAVAILABLE = """
            {
              "timestamp": "2026-07-21T08:00:00Z",
              "status": 503,
              "error": "Service Unavailable",
              "code": "SERVICE_UNAVAILABLE",
              "message": "O serviço Discovery está temporariamente indisponível.",
              "path": "/api/v1/discovery/home"
            }
            """;

    static final String UNKNOWN = """
            {
              "timestamp": "2026-07-21T08:00:00Z",
              "status": 500,
              "error": "Internal Server Error",
              "code": "UNKNOWN",
              "message": "Não foi possível concluir o Discovery.",
              "path": "/api/v1/discovery/home"
            }
            """;

    private DiscoveryOpenApiExamples() {}
}

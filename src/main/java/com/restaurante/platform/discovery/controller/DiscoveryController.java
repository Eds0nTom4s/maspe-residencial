package com.restaurante.platform.discovery.controller;

import com.restaurante.platform.discovery.domain.DiscoveryResult;
import com.restaurante.platform.discovery.dto.DiscoveryErrorResponse;
import com.restaurante.platform.discovery.dto.DiscoveryHomeResponse;
import com.restaurante.platform.discovery.dto.HomeDiscoveryRequest;
import com.restaurante.platform.discovery.dto.MerchantOverviewResponse;
import com.restaurante.platform.discovery.dto.MerchantRequest;
import com.restaurante.platform.discovery.dto.MerchantSearchResponse;
import com.restaurante.platform.discovery.dto.SearchDiscoveryRequest;
import com.restaurante.platform.discovery.exception.DiscoveryApiException;
import com.restaurante.platform.discovery.service.DiscoveryService;
import com.restaurante.platform.discovery.validation.DiscoveryHttpParameterValidator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/discovery")
@Tag(
        name = "Public Discovery",
        description =
                """
                Discovery v1 público. Capabilities fixas: NAME e filtros query/category/municipality;
                sem distance, rating, popularity, featured, open-now ou fulfillment filter.
                Parâmetros desconhecidos retornam 400 e as respostas usam Cache-Control: no-store.
                """)
@Slf4j
public class DiscoveryController {

    private final DiscoveryService service;
    private final DiscoveryHttpParameterValidator httpParameterValidator;

    public DiscoveryController(
            DiscoveryService service, DiscoveryHttpParameterValidator httpParameterValidator) {
        this.service = service;
        this.httpParameterValidator = httpParameterValidator;
    }

    @GetMapping(value = "/home", produces = "application/json;charset=UTF-8")
    @Operation(
            summary = "Obtém as secções públicas do Discovery",
            description =
                    """
                    Contrato v1 público, zero-based e sem geografia real. Coordenadas são
                    validadas para compatibilidade futura, mas nearby permanece vazio, nenhuma
                    distância é calculada e recommended recebe os resultados persistentes.
                    Campos opcionais nulos são omitidos e listas vazias permanecem arrays.
                    """,
            security = {})
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Home pública, incluindo secções vazias",
                headers = @Header(
                        name = "Cache-Control",
                        description = "Política temporária sem cache",
                        schema = @Schema(example = "no-store")),
                content = @Content(
                        schema = @Schema(implementation = DiscoveryHomeResponse.class),
                        examples = {
                            @ExampleObject(name = "com dados", value = DiscoveryOpenApiExamples.HOME_WITH_DATA),
                            @ExampleObject(name = "vazio", value = DiscoveryOpenApiExamples.HOME_EMPTY)
                        })),
        @ApiResponse(
                responseCode = "400",
                description = "Parâmetros inválidos ou sort conhecido sem suporte persistente",
                content = @Content(
                        schema = @Schema(implementation = DiscoveryErrorResponse.class),
                        examples = {
                            @ExampleObject(name = "request inválido", value = DiscoveryOpenApiExamples.INVALID_REQUEST),
                            @ExampleObject(name = "sort sem suporte", value = DiscoveryOpenApiExamples.UNSUPPORTED_SORT)
                        })),
        @ApiResponse(
                responseCode = "500",
                description = "Falha interna genérica",
                content = @Content(
                        schema = @Schema(implementation = DiscoveryErrorResponse.class),
                        examples = @ExampleObject(value = DiscoveryOpenApiExamples.UNKNOWN))),
        @ApiResponse(
                responseCode = "503",
                description = "Serviço/persistência temporariamente indisponível; retryable",
                content = @Content(
                        schema = @Schema(implementation = DiscoveryErrorResponse.class),
                        examples = @ExampleObject(value = DiscoveryOpenApiExamples.SERVICE_UNAVAILABLE)))
    })
    public ResponseEntity<DiscoveryHomeResponse> home(
            @Parameter(hidden = true) HttpServletRequest httpRequest,
            @Parameter(
                            description = "Latitude opcional; exige longitude e não é aplicada em v1",
                            example = "-8.8383",
                            schema = @Schema(type = "number", format = "double", minimum = "-90", maximum = "90"))
                    @RequestParam(required = false)
                    Double latitude,
            @Parameter(
                            description = "Longitude opcional; exige latitude e não é aplicada em v1",
                            example = "13.2344",
                            schema = @Schema(type = "number", format = "double", minimum = "-180", maximum = "180"))
                    @RequestParam(required = false)
                    Double longitude,
            @Parameter(
                            description = "Município textual, case-insensitive, máximo 120 caracteres",
                            schema = @Schema(maxLength = 120))
                    @RequestParam(required = false)
                    String municipalityId,
            @Parameter(
                            description = "ID público lowercase da categoria, máximo 120 caracteres",
                            schema = @Schema(
                                    maxLength = 120,
                                    pattern = "^[a-z0-9]+(?:-[a-z0-9]+)*$"))
                    @RequestParam(required = false)
                    String categoryId,
            @Parameter(
                            description = "Página zero-based",
                            schema = @Schema(type = "integer", format = "int32", defaultValue = "0", minimum = "0"))
                    @RequestParam(required = false)
                    Integer page,
            @Parameter(
                            description = "Itens da secção recommended",
                            schema = @Schema(
                                    type = "integer",
                                    format = "int32",
                                    defaultValue = "20",
                                    minimum = "1",
                                    maximum = "100"))
                    @RequestParam(required = false)
                    Integer pageSize,
            @Parameter(
                            description = "Somente NAME é suportado; outros valores conhecidos retornam SORT_NOT_SUPPORTED",
                            schema = @Schema(
                                    defaultValue = "NAME",
                                    allowableValues = {
                                        "NAME", "FEATURED", "NEAREST", "TOP_RATED", "MOST_POPULAR"
                                    }))
                    @RequestParam(required = false)
                    String sort) {
        httpParameterValidator.validateHome(httpRequest);
        long startedAt = System.nanoTime();
        DiscoveryResult<DiscoveryHomeResponse> result = service.home(new HomeDiscoveryRequest(
                latitude, longitude, municipalityId, categoryId, page, pageSize, sort));
        DiscoveryHomeResponse response = resolve(result);
        log.debug(
                "Discovery endpoint=home result={} page={} pageSize={} sort={} items={} elapsedMs={}",
                resultType(result),
                page,
                pageSize,
                sort,
                response.nearby().items().size()
                        + response.recommended().items().size()
                        + response.featured().items().size(),
                elapsedMillis(startedAt));
        return noStore(response);
    }

    @GetMapping(value = "/search", produces = "application/json;charset=UTF-8")
    @Operation(
            summary = "Pesquisa comerciantes públicos",
            description =
                    """
                    Filtros combinam com AND. Query vazia é permitida. A paginação é
                    zero-based e somente NAME possui fonte persistente. Empty retorna 200,
                    nunca 204 ou 404.
                    """,
            security = {})
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Página de resultados; vazio também retorna 200",
                headers = @Header(
                        name = "Cache-Control",
                        schema = @Schema(example = "no-store")),
                content = @Content(
                        schema = @Schema(implementation = MerchantSearchResponse.class),
                        examples = {
                            @ExampleObject(name = "com dados", value = DiscoveryOpenApiExamples.SEARCH_WITH_DATA),
                            @ExampleObject(name = "vazio", value = DiscoveryOpenApiExamples.SEARCH_EMPTY)
                        })),
        @ApiResponse(
                responseCode = "400",
                description = "INVALID_REQUEST ou SORT_NOT_SUPPORTED",
                content = @Content(
                        schema = @Schema(implementation = DiscoveryErrorResponse.class),
                        examples = {
                            @ExampleObject(name = "request inválido", value = DiscoveryOpenApiExamples.INVALID_REQUEST),
                            @ExampleObject(name = "sort sem suporte", value = DiscoveryOpenApiExamples.UNSUPPORTED_SORT)
                        })),
        @ApiResponse(
                responseCode = "500",
                description = "Falha interna genérica",
                content = @Content(
                        schema = @Schema(implementation = DiscoveryErrorResponse.class),
                        examples = @ExampleObject(value = DiscoveryOpenApiExamples.UNKNOWN))),
        @ApiResponse(
                responseCode = "503",
                description = "Serviço/persistência temporariamente indisponível; retryable",
                content = @Content(
                        schema = @Schema(implementation = DiscoveryErrorResponse.class),
                        examples = @ExampleObject(value = DiscoveryOpenApiExamples.SERVICE_UNAVAILABLE)))
    })
    public ResponseEntity<MerchantSearchResponse> search(
            @Parameter(hidden = true) HttpServletRequest httpRequest,
            @Parameter(description = "Texto literal trimado; 0 a 100 caracteres", schema = @Schema(maxLength = 100))
                    @RequestParam(required = false)
                    String query,
            @Parameter(
                            description = "ID público lowercase da categoria, máximo 120 caracteres",
                            schema = @Schema(
                                    maxLength = 120,
                                    pattern = "^[a-z0-9]+(?:-[a-z0-9]+)*$"))
                    @RequestParam(required = false)
                    String categoryId,
            @Parameter(
                            description = "Latitude opcional; exige longitude",
                            schema = @Schema(
                                    type = "number",
                                    format = "double",
                                    minimum = "-90",
                                    maximum = "90"))
                    @RequestParam(required = false)
                    Double latitude,
            @Parameter(
                            description = "Longitude opcional; exige latitude",
                            schema = @Schema(
                                    type = "number",
                                    format = "double",
                                    minimum = "-180",
                                    maximum = "180"))
                    @RequestParam(required = false)
                    Double longitude,
            @Parameter(
                            description = "Município textual, case-insensitive, máximo 120 caracteres",
                            schema = @Schema(maxLength = 120))
                    @RequestParam(required = false)
                    String municipalityId,
            @Parameter(
                            description = "Página zero-based",
                            schema = @Schema(
                                    type = "integer",
                                    format = "int32",
                                    defaultValue = "0",
                                    minimum = "0"))
                    @RequestParam(required = false)
                    Integer page,
            @Parameter(
                            description = "Itens por página",
                            schema = @Schema(
                                    type = "integer",
                                    format = "int32",
                                    defaultValue = "20",
                                    minimum = "1",
                                    maximum = "100"))
                    @RequestParam(required = false)
                    Integer pageSize,
            @Parameter(
                            description = "Somente NAME é suportado; sem fallback silencioso",
                            schema = @Schema(
                                    defaultValue = "NAME",
                                    allowableValues = {
                                        "NAME", "FEATURED", "NEAREST", "TOP_RATED", "MOST_POPULAR"
                                    }))
                    @RequestParam(required = false)
                    String sort) {
        httpParameterValidator.validateSearch(httpRequest);
        long startedAt = System.nanoTime();
        DiscoveryResult<MerchantSearchResponse> result = service.search(new SearchDiscoveryRequest(
                query,
                categoryId,
                latitude,
                longitude,
                municipalityId,
                page,
                pageSize,
                sort));
        MerchantSearchResponse response = resolve(result);
        log.debug(
                "Discovery endpoint=search result={} page={} pageSize={} sort={} items={} elapsedMs={}",
                resultType(result),
                response.page(),
                response.pageSize(),
                sort,
                response.merchants().size(),
                elapsedMillis(startedAt));
        return noStore(response);
    }

    @GetMapping(value = "/merchant/{merchantId}", produces = "application/json;charset=UTF-8")
    @Operation(
            summary = "Obtém o detalhe de um comerciante público",
            description = "Inexistente, privado, inactivo ou não publicável recebe o mesmo 404 genérico.",
            security = {})
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Detalhe do comerciante publicável",
                headers = @Header(name = "Cache-Control", schema = @Schema(example = "no-store")),
                content = @Content(
                        schema = @Schema(implementation = MerchantOverviewResponse.class),
                        examples = @ExampleObject(value = DiscoveryOpenApiExamples.MERCHANT_DETAIL))),
        @ApiResponse(
                responseCode = "400",
                description = "Identificador público malformado",
                content = @Content(
                        schema = @Schema(implementation = DiscoveryErrorResponse.class),
                        examples = @ExampleObject(value = DiscoveryOpenApiExamples.INVALID_REQUEST))),
        @ApiResponse(
                responseCode = "404",
                description = "Comerciante inexistente ou não publicável",
                content = @Content(
                        schema = @Schema(implementation = DiscoveryErrorResponse.class),
                        examples = @ExampleObject(value = DiscoveryOpenApiExamples.NOT_FOUND))),
        @ApiResponse(
                responseCode = "500",
                description = "Falha interna genérica",
                content = @Content(
                        schema = @Schema(implementation = DiscoveryErrorResponse.class),
                        examples = @ExampleObject(value = DiscoveryOpenApiExamples.UNKNOWN))),
        @ApiResponse(
                responseCode = "503",
                description = "Serviço/persistência temporariamente indisponível; retryable",
                content = @Content(
                        schema = @Schema(implementation = DiscoveryErrorResponse.class),
                        examples = @ExampleObject(value = DiscoveryOpenApiExamples.SERVICE_UNAVAILABLE)))
    })
    public ResponseEntity<MerchantOverviewResponse> merchant(
            @Parameter(hidden = true) HttpServletRequest httpRequest,
            @Parameter(
                            description = "Slug público lowercase estável",
                            example = "sabor-maianga",
                            schema = @Schema(pattern = "^[a-z0-9]+(?:-[a-z0-9]+)*$", maxLength = 100))
                    @PathVariable
                    String merchantId) {
        httpParameterValidator.validateMerchant(httpRequest);
        long startedAt = System.nanoTime();
        DiscoveryResult<MerchantOverviewResponse> result =
                service.merchant(new MerchantRequest(merchantId));
        MerchantOverviewResponse response = resolve(result);
        log.debug(
                "Discovery endpoint=merchant result={} elapsedMs={}",
                resultType(result),
                elapsedMillis(startedAt));
        return noStore(response);
    }

    private <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }

    private String resultType(DiscoveryResult<?> result) {
        if (result instanceof DiscoveryResult.Empty<?>) {
            return "EMPTY";
        }
        if (result instanceof DiscoveryResult.Error<?>) {
            return "ERROR";
        }
        return "SUCCESS";
    }

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    private <T> T resolve(DiscoveryResult<T> result) {
        if (result instanceof DiscoveryResult.Success<T> success) {
            return success.data();
        }
        if (result instanceof DiscoveryResult.Empty<T> empty) {
            return empty.data();
        }
        DiscoveryResult.Error<T> error = (DiscoveryResult.Error<T>) result;
        throw new DiscoveryApiException(error.reason(), error.message());
    }
}

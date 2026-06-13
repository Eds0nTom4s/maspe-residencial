package com.restaurante.controller;

import com.restaurante.dto.request.KdsTransitionRequest;
import com.restaurante.dto.response.ApiResponse;
import com.restaurante.dto.response.kds.KdsSubPedidoListResponse;
import com.restaurante.dto.response.kds.KdsSubPedidoResponse;
import com.restaurante.dto.response.kds.KdsUnidadeProducaoResponse;
import com.restaurante.model.enums.StatusSubPedido;
import com.restaurante.service.kds.KdsOperationsService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/tenant/kds")
@RequiredArgsConstructor
@Tag(name = "Tenant - KDS", description = "Contrato operacional tenant-scoped para KDS")
public class TenantKdsController {

    private final KdsOperationsService service;

    @GetMapping("/unidades-producao")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<KdsUnidadeProducaoResponse>>> listarUnidadesProducao() {
        return ResponseEntity.ok(ApiResponse.success("Unidades de produção KDS", service.listarUnidadesProducao()));
    }

    @GetMapping("/subpedidos")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<KdsSubPedidoListResponse>> listarSubPedidos(
            @RequestParam(required = false) Long unidadeProducaoId,
            @RequestParam(required = false) StatusSubPedido status,
            @RequestParam(required = false) Long pedidoId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdTo
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Subpedidos KDS",
                service.listarSubPedidos(unidadeProducaoId, status, pedidoId, createdFrom, createdTo)
        ));
    }

    @GetMapping("/subpedidos/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<KdsSubPedidoResponse>> buscarDetalhe(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Subpedido KDS", service.buscarDetalhe(id)));
    }

    @PatchMapping("/subpedidos/{id}/iniciar-preparo")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<KdsSubPedidoResponse>> iniciarPreparo(
            @PathVariable Long id,
            @RequestBody(required = false) KdsTransitionRequest request,
            HttpServletRequest http
    ) {
        return ResponseEntity.ok(ApiResponse.success("Preparo iniciado", service.iniciarPreparo(id, request, http)));
    }

    @PatchMapping("/subpedidos/{id}/pronto")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<KdsSubPedidoResponse>> marcarPronto(
            @PathVariable Long id,
            @RequestBody(required = false) KdsTransitionRequest request,
            HttpServletRequest http
    ) {
        return ResponseEntity.ok(ApiResponse.success("Subpedido pronto", service.marcarPronto(id, request, http)));
    }

    @PatchMapping("/subpedidos/{id}/entregar")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<KdsSubPedidoResponse>> entregar(
            @PathVariable Long id,
            @RequestBody(required = false) KdsTransitionRequest request,
            HttpServletRequest http
    ) {
        return ResponseEntity.ok(ApiResponse.success("Subpedido entregue", service.entregar(id, request, http)));
    }
}

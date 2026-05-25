package com.restaurante.controller;

import com.restaurante.dto.request.CreateInventoryReturnRequest;
import com.restaurante.dto.response.ApiResponse;
import com.restaurante.dto.response.DeviceErrorResponse;
import com.restaurante.dto.response.InventoryReturnRecordResponse;
import com.restaurante.exception.DeviceApiException;
import com.restaurante.inventory.service.InventoryReturnService;
import com.restaurante.model.entity.InventoryReturnRecord;
import com.restaurante.model.enums.DeviceCapability;
import com.restaurante.security.device.DevicePrincipal;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/device/inventory")
@RequiredArgsConstructor
@Tag(name = "Device - Inventory Returns", description = "Solicitação de devoluções (device/POS) e consulta")
public class DeviceInventoryReturnController {

    private final InventoryReturnService returnService;

    @PostMapping("/returns")
    public ResponseEntity<ApiResponse<InventoryReturnRecordResponse>> requestReturn(@Valid @RequestBody CreateInventoryReturnRequest request) {
        DevicePrincipal device = requireDevicePrincipal();
        requireCapability(device, DeviceCapability.REQUEST_INVENTORY_RETURN);

        List<InventoryReturnService.RequestedReturnLine> lines = request.getLines().stream()
                .map(l -> new InventoryReturnService.RequestedReturnLine(l.getPedidoItemId(), l.getQuantityReturned(), l.getRestockPolicy()))
                .toList();

        InventoryReturnRecord record = returnService.createReturn(
                device.tenantId(),
                request.getPedidoId(),
                request.getReturnType(),
                request.getReasonCategory(),
                request.getReasonDescription(),
                lines,
                com.restaurante.model.enums.InventoryReturnSource.POS,
                null
        );
        return ResponseEntity.ok(ApiResponse.success("Return solicitado", map(record)));
    }

    @GetMapping("/returns/{returnId}")
    public ResponseEntity<ApiResponse<InventoryReturnRecordResponse>> getReturn(@PathVariable Long returnId) {
        DevicePrincipal device = requireDevicePrincipal();
        requireCapability(device, DeviceCapability.VIEW_INVENTORY_RETURN);
        InventoryReturnRecord record = returnService.getByTenant(device.tenantId(), returnId);
        return ResponseEntity.ok(ApiResponse.success("Return", map(record)));
    }

    private void requireCapability(DevicePrincipal device, DeviceCapability cap) {
        if (device.capabilities() == null || !device.capabilities().contains(cap)) {
            throw new DeviceApiException(
                    HttpStatus.FORBIDDEN,
                    DeviceErrorResponse.DeviceErrorCode.DEVICE_FORBIDDEN,
                    "Capability requerida: " + cap.name(),
                    false,
                    DeviceErrorResponse.DeviceRecoveryAction.NONE,
                    java.util.Map.of("requiredCapability", cap.name())
            );
        }
    }

    private DevicePrincipal requireDevicePrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Object principal = auth != null ? auth.getPrincipal() : null;
        if (principal instanceof DevicePrincipal dp) return dp;
        throw new IllegalStateException("DevicePrincipal obrigatório.");
    }

    private InventoryReturnRecordResponse map(InventoryReturnRecord record) {
        InventoryReturnRecordResponse r = new InventoryReturnRecordResponse();
        r.setId(record.getId());
        r.setTenantId(record.getTenant() != null ? record.getTenant().getId() : null);
        r.setUnidadeAtendimentoId(record.getUnidadeAtendimento() != null ? record.getUnidadeAtendimento().getId() : null);
        r.setPedidoId(record.getPedido() != null ? record.getPedido().getId() : null);
        r.setPagamentoId(record.getPagamento() != null ? record.getPagamento().getId() : null);
        r.setFiscalDocumentId(record.getFiscalDocument() != null ? record.getFiscalDocument().getId() : null);
        r.setFiscalCorrectionDocumentId(record.getFiscalCorrectionDocument() != null ? record.getFiscalCorrectionDocument().getId() : null);
        r.setInventoryConsumptionRecordId(record.getConsumptionRecord() != null ? record.getConsumptionRecord().getId() : null);
        r.setReturnType(record.getReturnType());
        r.setStatus(record.getStatus());
        r.setSource(record.getSource());
        r.setReasonCategory(record.getReasonCategory());
        r.setReasonDescription(record.getReasonDescription());
        r.setRequestedByUserId(record.getRequestedBy() != null ? record.getRequestedBy().getId() : null);
        r.setApprovedByUserId(record.getApprovedBy() != null ? record.getApprovedBy().getId() : null);
        r.setRequestedAt(record.getRequestedAt());
        r.setApprovedAt(record.getApprovedAt());
        r.setProcessedAt(record.getProcessedAt());
        r.setTotalReturnCost(record.getTotalReturnCost());
        r.setTotalRevenueReversed(record.getTotalRevenueReversed());
        r.setTotalTaxReversed(record.getTotalTaxReversed());
        r.setTotalMarginReversed(record.getTotalMarginReversed());
        r.setWarningCount(record.getWarningCount());
        r.setCreatedAt(record.getCreatedAt());
        return r;
    }
}


package com.restaurante.controller;

import com.restaurante.dto.response.ApiResponse;
import com.restaurante.dto.response.DeviceFiscalIssueStatusResponse;
import com.restaurante.dto.response.DeviceErrorResponse;
import com.restaurante.exception.DeviceApiException;
import com.restaurante.fiscal.autoissue.repository.FiscalAutoIssueJobRepository;
import com.restaurante.fiscal.repository.FiscalDocumentRepository;
import com.restaurante.financeiro.repository.PagamentoGatewayRepository;
import com.restaurante.model.entity.FiscalAutoIssueJob;
import com.restaurante.model.entity.FiscalDocument;
import com.restaurante.model.entity.Pagamento;
import com.restaurante.model.enums.DeviceCapability;
import com.restaurante.security.device.DevicePrincipal;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/device/fiscal/issue-status")
@RequiredArgsConstructor
@Tag(name = "Device - Fiscal Issue Status", description = "Consulta status de emissão fiscal interna por pagamento (somente leitura)")
public class DeviceFiscalIssueStatusController {

    private final PagamentoGatewayRepository pagamentoRepository;
    private final FiscalDocumentRepository fiscalDocumentRepository;
    private final FiscalAutoIssueJobRepository jobRepository;

    @GetMapping("/pagamento/{pagamentoId}")
    public ResponseEntity<ApiResponse<DeviceFiscalIssueStatusResponse>> status(@PathVariable Long pagamentoId) {
        DevicePrincipal device = requireDevicePrincipal();
        if (device.capabilities() == null || !device.capabilities().contains(DeviceCapability.VIEW_FISCAL_DOCUMENT)) {
            throw new DeviceApiException(
                    HttpStatus.FORBIDDEN,
                    DeviceErrorResponse.DeviceErrorCode.DEVICE_FORBIDDEN,
                    "Capability requerida: VIEW_FISCAL_DOCUMENT",
                    false,
                    DeviceErrorResponse.DeviceRecoveryAction.NONE,
                    java.util.Map.of("requiredCapability", DeviceCapability.VIEW_FISCAL_DOCUMENT.name())
            );
        }

        Pagamento pg = pagamentoRepository.findByIdAndTenantId(pagamentoId, device.tenantId()).orElse(null);
        if (pg == null) return ResponseEntity.ok(ApiResponse.success("Status", null));

        FiscalDocument doc = fiscalDocumentRepository.findByTenantIdAndPagamentoId(device.tenantId(), pagamentoId).orElse(null);
        FiscalAutoIssueJob job = jobRepository.findFirstByTenantIdAndPagamentoIdOrderByCreatedAtDesc(device.tenantId(), pagamentoId).orElse(null);

        DeviceFiscalIssueStatusResponse r = new DeviceFiscalIssueStatusResponse();
        r.setPagamentoId(pg.getId());
        r.setPedidoId(pg.getPedido() != null ? pg.getPedido().getId() : null);
        r.setFiscalDocumentId(doc != null ? doc.getId() : null);
        r.setJobStatus(job != null ? job.getStatus() : null);
        r.setLastErrorCode(job != null ? job.getErrorCode() : null);

        return ResponseEntity.ok(ApiResponse.success("Status", r));
    }

    private DevicePrincipal requireDevicePrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Object principal = auth != null ? auth.getPrincipal() : null;
        if (principal instanceof DevicePrincipal dp) return dp;
        throw new IllegalStateException("DevicePrincipal obrigatório.");
    }
}

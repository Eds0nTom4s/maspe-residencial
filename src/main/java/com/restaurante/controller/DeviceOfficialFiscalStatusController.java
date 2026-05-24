package com.restaurante.controller;

import com.restaurante.dto.response.ApiResponse;
import com.restaurante.fiscal.official.repository.OfficialFiscalSubmissionRepository;
import com.restaurante.model.entity.OfficialFiscalSubmission;
import com.restaurante.security.device.DevicePrincipal;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/device/fiscal/official-status")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@Tag(name = "Device - Fiscal Official Status (Prep)", description = "Consulta somente leitura do estado oficial (Prompt 45 prep)")
public class DeviceOfficialFiscalStatusController {

    private final OfficialFiscalSubmissionRepository submissionRepository;

    @GetMapping("/document/{documentId}")
    public ResponseEntity<ApiResponse<DeviceOfficialFiscalStatusResponse>> get(DevicePrincipal device, @PathVariable Long documentId) {
        if (device == null) return ResponseEntity.ok(ApiResponse.success("Status", null));
        // capability check (mesmo contrato do device fiscal doc)
        if (device.capabilities() == null || !device.capabilities().contains(com.restaurante.model.enums.DeviceCapability.VIEW_FISCAL_DOCUMENT)) {
            return ResponseEntity.ok(ApiResponse.success("Status", null));
        }

        OfficialFiscalSubmission s = submissionRepository.findByTenantIdAndFiscalDocumentId(device.tenantId(), documentId).orElse(null);
        if (s == null) return ResponseEntity.ok(ApiResponse.success("Status", null));

        DeviceOfficialFiscalStatusResponse r = new DeviceOfficialFiscalStatusResponse();
        r.setSubmissionId(s.getId());
        r.setStatus(s.getStatus() != null ? s.getStatus().name() : null);
        r.setRequestId(s.getRequestId());
        r.setOfficialStatusCode(s.getOfficialStatusCode());
        r.setOfficialStatusMessage(s.getOfficialStatusMessage());
        r.setAcceptedAt(s.getAcceptedAt());
        r.setRejectedAt(s.getRejectedAt());
        return ResponseEntity.ok(ApiResponse.success("Status", r));
    }

    @Data
    public static class DeviceOfficialFiscalStatusResponse {
        private Long submissionId;
        private String status;
        private String requestId;
        private String officialStatusCode;
        private String officialStatusMessage;
        private java.time.LocalDateTime acceptedAt;
        private java.time.LocalDateTime rejectedAt;
    }
}


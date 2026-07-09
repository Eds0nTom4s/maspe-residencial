package com.restaurante.controller;

import com.restaurante.dto.response.ApiResponse;
import com.restaurante.dto.response.FiscalAutoIssueJobResponse;
import com.restaurante.fiscal.autoissue.service.FiscalAutoIssueAdminService;
import com.restaurante.model.enums.FiscalAutoIssueJobStatus;
import com.restaurante.model.enums.FiscalAutoIssueSource;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/tenant/fiscal/auto-issue")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@Tag(name = "Tenant Admin - Fiscal Auto Issue", description = "Automatização fiscal controlada pós-pagamento (jobs, retries, cancelamentos)")
public class TenantFiscalAutoIssueController {

    private final FiscalAutoIssueAdminService adminService;

    @GetMapping("/jobs")
    public ResponseEntity<ApiResponse<Page<FiscalAutoIssueJobResponse>>> list(
            @RequestParam(name = "status", required = false) FiscalAutoIssueJobStatus status,
            @RequestParam(name = "pedidoId", required = false) Long pedidoId,
            @RequestParam(name = "pagamentoId", required = false) Long pagamentoId,
            @RequestParam(name = "fiscalDocumentId", required = false) Long fiscalDocumentId,
            Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.success("Jobs", adminService.listJobs(status, pedidoId, pagamentoId, fiscalDocumentId, pageable)));
    }

    @GetMapping("/jobs/{jobId}")
    public ResponseEntity<ApiResponse<FiscalAutoIssueJobResponse>> get(@PathVariable Long jobId) {
        return ResponseEntity.ok(ApiResponse.success("Job", adminService.getJob(jobId)));
    }

    @PostMapping("/jobs/{jobId}/retry")
    public ResponseEntity<ApiResponse<FiscalAutoIssueJobResponse>> retry(@PathVariable Long jobId) {
        return ResponseEntity.ok(ApiResponse.success("Retry agendado", adminService.retryJob(jobId)));
    }

    @PostMapping("/jobs/{jobId}/cancel")
    public ResponseEntity<ApiResponse<FiscalAutoIssueJobResponse>> cancel(@PathVariable Long jobId) {
        return ResponseEntity.ok(ApiResponse.success("Job cancelado", adminService.cancelJob(jobId)));
    }

    @PostMapping("/issue-for-payment/{pagamentoId}")
    public ResponseEntity<ApiResponse<Void>> issueForPayment(@PathVariable Long pagamentoId,
                                                             @RequestParam(name = "source", required = false) FiscalAutoIssueSource source) {
        adminService.triggerForPagamento(pagamentoId, source != null ? source : FiscalAutoIssueSource.ADMIN_MANUAL_TRIGGER);
        return ResponseEntity.ok(ApiResponse.success("Trigger criado", null));
    }
}


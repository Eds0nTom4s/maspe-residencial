package com.restaurante.fiscal.autoissue.service;

import com.restaurante.dto.response.FiscalAutoIssueJobResponse;
import com.restaurante.exception.BusinessException;
import com.restaurante.fiscal.autoissue.event.PaymentConfirmedForFiscalIssueEvent;
import com.restaurante.fiscal.autoissue.repository.FiscalAutoIssueJobRepository;
import com.restaurante.fiscal.repository.FiscalDocumentRepository;
import com.restaurante.financeiro.repository.PagamentoGatewayRepository;
import com.restaurante.model.entity.FiscalAutoIssueJob;
import com.restaurante.model.entity.Pagamento;
import com.restaurante.model.enums.*;
import com.restaurante.security.tenant.TenantContext;
import com.restaurante.security.tenant.TenantGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class FiscalAutoIssueAdminService {

    private final TenantGuard tenantGuard;
    private final FiscalAutoIssueJobRepository jobRepository;
    private final PagamentoGatewayRepository pagamentoRepository;
    private final FiscalDocumentRepository fiscalDocumentRepository;
    private final FiscalAutoIssueIdempotencyKeyService keyService;
    private final ApplicationEventPublisher eventPublisher;
    private final com.restaurante.service.operacional.OperationalEventLogService operationalEventLogService;

    @Transactional(readOnly = true)
    public Page<FiscalAutoIssueJobResponse> listJobs(FiscalAutoIssueJobStatus status,
                                                     Long pedidoId,
                                                     Long pagamentoId,
                                                     Long fiscalDocumentId,
                                                     Pageable pageable) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE);
        TenantContext ctx = tenantGuard.requireContext();
        return jobRepository.listByTenant(ctx.tenantId(), status, pedidoId, pagamentoId, fiscalDocumentId, pageable)
                .map(this::map);
    }

    @Transactional(readOnly = true)
    public FiscalAutoIssueJobResponse getJob(Long jobId) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE);
        TenantContext ctx = tenantGuard.requireContext();
        FiscalAutoIssueJob j = jobRepository.findById(jobId).orElse(null);
        if (j == null || j.getTenant() == null || !j.getTenant().getId().equals(ctx.tenantId())) return null;
        return map(j);
    }

    @Transactional
    public FiscalAutoIssueJobResponse retryJob(Long jobId) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE);
        TenantContext ctx = tenantGuard.requireContext();
        FiscalAutoIssueJob j = jobRepository.findById(jobId).orElseThrow(() -> new BusinessException("Job não encontrado."));
        tenantGuard.assertResourceBelongsToTenant(j.getTenant().getId());

        if (j.getStatus() != FiscalAutoIssueJobStatus.FAILED_RETRYABLE && j.getStatus() != FiscalAutoIssueJobStatus.FAILED_PERMANENT) {
            throw new BusinessException("Retry permitido apenas para FAILED_*.");
        }
        j.setStatus(FiscalAutoIssueJobStatus.PENDING);
        j.setNextAttemptAt(LocalDateTime.now());
        j.setErrorCode(null);
        j.setErrorMessage(null);
        jobRepository.save(j);

        operationalEventLogService.logGeneric(
                OperationalEventType.FISCAL_AUTO_ISSUE_JOB_RETRIED_MANUALLY,
                OperationalEntityType.FISCAL_AUTO_ISSUE_JOB,
                j.getId(),
                OperationalOrigem.TENANT_FINANCE,
                "Retry manual de job fiscal",
                Map.of("jobId", j.getId()),
                null,
                null
        );

        return map(j);
    }

    @Transactional
    public FiscalAutoIssueJobResponse cancelJob(Long jobId) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE);
        TenantContext ctx = tenantGuard.requireContext();
        FiscalAutoIssueJob j = jobRepository.findById(jobId).orElseThrow(() -> new BusinessException("Job não encontrado."));
        tenantGuard.assertResourceBelongsToTenant(j.getTenant().getId());

        if (j.getStatus() != FiscalAutoIssueJobStatus.PENDING && j.getStatus() != FiscalAutoIssueJobStatus.FAILED_RETRYABLE) {
            throw new BusinessException("Cancelamento permitido apenas para PENDING/FAILED_RETRYABLE.");
        }
        j.setStatus(FiscalAutoIssueJobStatus.CANCELLED);
        j.setNextAttemptAt(null);
        jobRepository.save(j);

        operationalEventLogService.logGeneric(
                OperationalEventType.FISCAL_AUTO_ISSUE_JOB_CANCELLED,
                OperationalEntityType.FISCAL_AUTO_ISSUE_JOB,
                j.getId(),
                OperationalOrigem.TENANT_FINANCE,
                "Job fiscal cancelado",
                Map.of("jobId", j.getId()),
                null,
                null
        );
        return map(j);
    }

    @Transactional
    public void triggerForPagamento(Long pagamentoId, FiscalAutoIssueSource source) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE);
        TenantContext ctx = tenantGuard.requireContext();
        if (pagamentoId == null) throw new BusinessException("pagamentoId é obrigatório.");

        Pagamento pg = pagamentoRepository.findByIdAndTenantId(pagamentoId, ctx.tenantId())
                .orElseThrow(() -> new BusinessException("Pagamento não encontrado."));
        if (pg.getPedido() == null) throw new BusinessException("Pagamento sem pedido vinculado.");

        if (fiscalDocumentRepository.findByTenantIdAndPagamentoId(ctx.tenantId(), pagamentoId).isPresent()) {
            return; // idempotente
        }

        String key = keyService.buildKey(ctx.tenantId(), pg.getPedido().getId(), pg.getId());
        eventPublisher.publishEvent(new PaymentConfirmedForFiscalIssueEvent(
                ctx.tenantId(),
                pg.getPedido().getSessaoConsumo() != null && pg.getPedido().getSessaoConsumo().getUnidadeAtendimento() != null
                        ? pg.getPedido().getSessaoConsumo().getUnidadeAtendimento().getId()
                        : null,
                pg.getPedido().getId(),
                pg.getId(),
                pg.getPedido().getSessaoConsumo() != null ? pg.getPedido().getSessaoConsumo().getId() : null,
                pg.getOrdemPagamento() != null && pg.getOrdemPagamento().getCaixaOperadorSession() != null ? pg.getOrdemPagamento().getCaixaOperadorSession().getId() : null,
                source != null ? source : FiscalAutoIssueSource.ADMIN_MANUAL_TRIGGER
        ));

        operationalEventLogService.logGeneric(
                OperationalEventType.FISCAL_AUTO_ISSUE_MANUAL_TRIGGERED,
                OperationalEntityType.PAGAMENTO,
                pg.getId(),
                OperationalOrigem.TENANT_FINANCE,
                "Trigger manual de auto-emissão fiscal",
                Map.of(
                        "pagamentoId", pg.getId(),
                        "pedidoId", pg.getPedido().getId(),
                        "idempotencyKey", key
                ),
                null,
                null
        );
    }

    private FiscalAutoIssueJobResponse map(FiscalAutoIssueJob j) {
        FiscalAutoIssueJobResponse r = new FiscalAutoIssueJobResponse();
        r.setId(j.getId());
        r.setTenantId(j.getTenant() != null ? j.getTenant().getId() : null);
        r.setUnidadeAtendimentoId(j.getUnidadeAtendimento() != null ? j.getUnidadeAtendimento().getId() : null);
        r.setPedidoId(j.getPedido() != null ? j.getPedido().getId() : null);
        r.setPagamentoId(j.getPagamento() != null ? j.getPagamento().getId() : null);
        r.setSessaoConsumoId(j.getSessaoConsumo() != null ? j.getSessaoConsumo().getId() : null);
        r.setCaixaOperadorSessionId(j.getCaixaOperadorSession() != null ? j.getCaixaOperadorSession().getId() : null);
        r.setSource(j.getSource());
        r.setStatus(j.getStatus());
        r.setTriggerType(j.getTriggerType());
        r.setAttemptCount(j.getAttemptCount());
        r.setMaxAttempts(j.getMaxAttempts());
        r.setNextAttemptAt(j.getNextAttemptAt());
        r.setLastAttemptAt(j.getLastAttemptAt());
        r.setLockedBy(j.getLockedBy());
        r.setLockedAt(j.getLockedAt());
        r.setErrorCode(j.getErrorCode());
        r.setErrorMessage(j.getErrorMessage());
        r.setFiscalDocumentId(j.getFiscalDocument() != null ? j.getFiscalDocument().getId() : null);
        r.setProcessedAt(j.getProcessedAt());
        r.setCreatedAt(j.getCreatedAt());
        return r;
    }
}

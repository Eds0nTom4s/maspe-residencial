package com.restaurante.fiscal.autoissue.service;

import com.restaurante.fiscal.autoissue.repository.FiscalAutoIssueJobRepository;
import com.restaurante.fiscal.config.TaxProperties;
import com.restaurante.fiscal.repository.FiscalDocumentRepository;
import com.restaurante.fiscal.repository.TenantFiscalProfileRepository;
import com.restaurante.model.entity.*;
import com.restaurante.model.enums.FiscalAutoIssueJobStatus;
import com.restaurante.model.enums.FiscalAutoIssueSource;
import com.restaurante.model.enums.FiscalAutoIssueTriggerType;
import com.restaurante.model.enums.FiscalRegime;
import com.restaurante.model.enums.TenantFiscalProfileStatus;
import com.restaurante.model.enums.OperationalEntityType;
import com.restaurante.model.enums.OperationalEventType;
import com.restaurante.model.enums.OperationalOrigem;
import com.restaurante.service.operacional.OperationalEventLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class FiscalAutoIssueJobService {

    private final TaxProperties taxProperties;
    private final TenantFiscalProfileRepository tenantFiscalProfileRepository;
    private final FiscalDocumentRepository fiscalDocumentRepository;
    private final FiscalAutoIssueJobRepository jobRepository;
    private final FiscalAutoIssueIdempotencyKeyService idempotencyKeyService;
    private final OperationalEventLogService operationalEventLogService;

    @Transactional
    public FiscalAutoIssueJob createAutoOnPaymentConfirmedJobIfEligible(
            Tenant tenant,
            UnidadeAtendimento unidade,
            Pedido pedido,
            Pagamento pagamento,
            SessaoConsumo sessaoConsumoOrNull,
            CaixaOperadorSession caixaOrNull,
            FiscalAutoIssueSource source
    ) {
        if (!taxProperties.isEnabled()) return null;
        if (!taxProperties.getDocument().isAutoIssueOnPayment()) return null;
        if (!taxProperties.getDocument().getAutoIssue().isEnabled()) return null;

        TenantFiscalProfile profile = tenantFiscalProfileRepository.findByTenantId(tenant.getId()).orElse(null);
        if (profile == null) return null;
        if (profile.getStatus() != TenantFiscalProfileStatus.ACTIVE) return null;
        if (profile.getFiscalRegime() == null || profile.getFiscalRegime() == FiscalRegime.NOT_CONFIGURED) return null;
        if (!profile.isFiscalDocumentEnabled()) return null;

        // Idempotência por documento existente (por pagamento)
        if (fiscalDocumentRepository.findByTenantIdAndPagamentoId(tenant.getId(), pagamento.getId()).isPresent()) {
            return null;
        }

        String idempotencyKey = idempotencyKeyService.buildKey(tenant.getId(), pedido.getId(), pagamento.getId());
        FiscalAutoIssueJob existing = jobRepository.findByTenantIdAndIdempotencyKey(tenant.getId(), idempotencyKey).orElse(null);
        if (existing != null) return existing;

        FiscalAutoIssueJob job = new FiscalAutoIssueJob();
        job.setTenant(tenant);
        job.setUnidadeAtendimento(unidade);
        job.setPedido(pedido);
        job.setPagamento(pagamento);
        job.setSessaoConsumo(sessaoConsumoOrNull);
        job.setCaixaOperadorSession(caixaOrNull);
        job.setSource(source);
        job.setTriggerType(FiscalAutoIssueTriggerType.AUTO_ON_PAYMENT_CONFIRMED);
        job.setStatus(FiscalAutoIssueJobStatus.PENDING);
        job.setAttemptCount(0);
        job.setMaxAttempts(taxProperties.getDocument().getAutoIssue().getMaxAttempts());
        job.setIdempotencyKey(idempotencyKey);
        job.setNextAttemptAt(LocalDateTime.now().plusSeconds(taxProperties.getDocument().getAutoIssue().getInitialDelaySeconds()));

        try {
            job = jobRepository.save(job);
        } catch (DataIntegrityViolationException e) {
            // corrida: outro thread criou primeiro
            return jobRepository.findByTenantIdAndIdempotencyKey(tenant.getId(), idempotencyKey).orElse(null);
        }

        operationalEventLogService.logPublicEvent(
                tenant,
                pedido.getSessaoConsumo() != null ? pedido.getSessaoConsumo().getInstituicao() : null,
                unidade,
                null,
                pedido.getTurnoOperacional(),
                OperationalEventType.FISCAL_AUTO_ISSUE_JOB_CREATED,
                OperationalEntityType.FISCAL_AUTO_ISSUE_JOB,
                job.getId(),
                origemFromSource(source),
                "Job fiscal criado (auto-emissão pós-pagamento confirmado)",
                Map.of(
                        "jobId", job.getId(),
                        "pedidoId", pedido.getId(),
                        "pagamentoId", pagamento.getId(),
                        "source", source.name(),
                        "triggerType", job.getTriggerType().name()
                ),
                null,
                null
        );

        return job;
    }

    private static OperationalOrigem origemFromSource(FiscalAutoIssueSource source) {
        if (source == null) return OperationalOrigem.SYSTEM;
        return switch (source) {
            case CASH_MANUAL_PAYMENT, TPA_MANUAL_PAYMENT -> OperationalOrigem.DEVICE_POS;
            case APPYPAY_CALLBACK, APPYPAY_POLLING -> OperationalOrigem.GATEWAY;
            case QR_PUBLIC_PAYMENT -> OperationalOrigem.QR_PUBLICO;
            default -> OperationalOrigem.SYSTEM;
        };
    }
}

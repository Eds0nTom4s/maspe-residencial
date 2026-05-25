package com.restaurante.billing.service;

import com.restaurante.billing.hash.UsageEventHashService;
import com.restaurante.billing.repository.UsageEventRepository;
import com.restaurante.exception.BusinessException;
import com.restaurante.financeiro.enums.StatusPagamentoGateway;
import com.restaurante.financeiro.repository.PagamentoGatewayRepository;
import com.restaurante.model.entity.Pagamento;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.UnidadeAtendimento;
import com.restaurante.model.entity.UsageEvent;
import com.restaurante.model.enums.OperationalEntityType;
import com.restaurante.model.enums.OperationalEventType;
import com.restaurante.model.enums.OperationalOrigem;
import com.restaurante.model.enums.UsageMetricCode;
import com.restaurante.service.operacional.OperationalEventLogService;
import com.restaurante.repository.UnidadeAtendimentoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UsageMeteringService {

    private final UsageEventRepository usageEventRepository;
    private final PagamentoGatewayRepository pagamentoGatewayRepository;
    private final UnidadeAtendimentoRepository unidadeAtendimentoRepository;
    private final UsageEventHashService hashService;
    private final OperationalEventLogService operationalEventLogService;

    @Transactional
    public UsageEvent recordPaymentConfirmed(Long tenantId, Long unidadeId, Long pagamentoId, LocalDateTime occurredAt) {
        if (tenantId == null || pagamentoId == null) throw new BusinessException("USAGE_EVENT_ALREADY_EXISTS");

        Pagamento pg = pagamentoGatewayRepository.findById(pagamentoId)
                .orElseThrow(() -> new BusinessException("USAGE_EVENT_NOT_FOUND"));
        if (pg.getTenant() == null || !pg.getTenant().getId().equals(tenantId)) {
            throw new BusinessException("BILLING_FORBIDDEN");
        }
        if (pg.getStatus() != StatusPagamentoGateway.CONFIRMADO) {
            throw new BusinessException("USAGE_EVENT_NOT_FOUND");
        }

        String idem = idempotencyKeyForPaymentConfirmed(tenantId, pagamentoId);
        UnidadeAtendimento ua = null;
        if (unidadeId != null) {
            ua = unidadeAtendimentoRepository.findById(unidadeId).orElse(null);
        }
        return recordUsageEvent(
                pg.getTenant(),
                ua,
                UsageMetricCode.PAYMENT_CONFIRMED,
                "PAYMENT_CONFIRMED",
                OperationalEntityType.PAGAMENTO.name(),
                pagamentoId,
                idem,
                occurredAt != null ? occurredAt : pg.getConfirmedAt() != null ? pg.getConfirmedAt() : LocalDateTime.now(),
                BigDecimal.ONE,
                pg.getAmount(),
                "AOA",
                null
        );
    }

    @Transactional
    public UsageEvent recordUsageEvent(Tenant tenant,
                                      UnidadeAtendimento unidade,
                                      UsageMetricCode metricCode,
                                      String sourceEventType,
                                      String sourceEntityType,
                                      Long sourceEntityId,
                                      String idempotencyKey,
                                      LocalDateTime occurredAt,
                                      BigDecimal quantity,
                                      BigDecimal amount,
                                      String currency,
                                      String metadataJson) {
        if (tenant == null || tenant.getId() == null) throw new BusinessException("BILLING_FORBIDDEN");
        if (metricCode == null) throw new BusinessException("USAGE_EVENT_NOT_FOUND");
        if (idempotencyKey == null || idempotencyKey.isBlank()) throw new BusinessException("USAGE_EVENT_ALREADY_EXISTS");

        UsageEvent existing = usageEventRepository.findByTenantIdAndIdempotencyKey(tenant.getId(), idempotencyKey).orElse(null);
        if (existing != null) {
            operationalEventLogService.logGenericForTenant(
                    tenant.getId(),
                    OperationalEventType.USAGE_EVENT_DUPLICATE_IGNORED,
                    OperationalEntityType.USAGE_EVENT,
                    existing.getId(),
                    OperationalOrigem.SYSTEM,
                    "UsageEvent duplicado ignorado",
                    Map.of("tenantId", tenant.getId(), "metricCode", metricCode.name(), "idempotencyKey", idempotencyKey),
                    null,
                    null
            );
            return existing;
        }

        UsageEvent e = new UsageEvent();
        e.setTenant(tenant);
        e.setMetricCode(metricCode);
        e.setSourceEventType(sourceEventType);
        e.setSourceEntityType(sourceEntityType);
        e.setSourceEntityId(sourceEntityId);
        e.setIdempotencyKey(idempotencyKey);
        e.setOccurredAt(occurredAt != null ? occurredAt : LocalDateTime.now());
        e.setQuantity(quantity != null ? quantity : BigDecimal.ONE);
        e.setAmount(amount);
        e.setCurrency(currency);
        e.setUnidadeAtendimento(unidade);
        e.setMetadataJson(metadataJson);
        e = usageEventRepository.save(e);

        String eventHash = hashService.hash(e);

        operationalEventLogService.logGenericForTenant(
                tenant.getId(),
                OperationalEventType.USAGE_EVENT_RECORDED,
                OperationalEntityType.USAGE_EVENT,
                e.getId(),
                OperationalOrigem.SYSTEM,
                "UsageEvent registrado",
                Map.of(
                        "tenantId", tenant.getId(),
                        "metricCode", metricCode.name(),
                        "sourceEntityType", sourceEntityType,
                        "sourceEntityId", sourceEntityId,
                        "occurredAt", e.getOccurredAt(),
                        "amount", amount,
                        "quantity", e.getQuantity(),
                        "hash", eventHash
                ),
                null,
                null
        );

        return e;
    }

    public static String idempotencyKeyForPaymentConfirmed(Long tenantId, Long pagamentoId) {
        return "tenant:" + tenantId + ":payment:" + pagamentoId + ":usage:payment-confirmed:v1";
    }
}

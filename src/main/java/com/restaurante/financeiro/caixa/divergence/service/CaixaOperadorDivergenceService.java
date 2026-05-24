package com.restaurante.financeiro.caixa.divergence.service;

import com.restaurante.exception.BusinessException;
import com.restaurante.exception.DeviceApiException;
import com.restaurante.dto.response.DeviceErrorResponse;
import com.restaurante.financeiro.caixa.divergence.config.CaixaOperadorDivergenceProperties;
import com.restaurante.financeiro.caixa.divergence.repository.CaixaOperadorAdjustmentRepository;
import com.restaurante.financeiro.caixa.divergence.repository.CaixaOperadorDivergenceRepository;
import com.restaurante.financeiro.caixa.repository.CaixaOperadorSessionRepository;
import com.restaurante.model.entity.CaixaOperadorAdjustment;
import com.restaurante.model.entity.CaixaOperadorDivergence;
import com.restaurante.model.entity.CaixaOperadorSession;
import com.restaurante.model.entity.User;
import com.restaurante.model.enums.CaixaOperadorAdjustmentDirection;
import com.restaurante.model.enums.CaixaOperadorAdjustmentStatus;
import com.restaurante.model.enums.CaixaOperadorAdjustmentType;
import com.restaurante.model.enums.CaixaOperadorDivergencePaymentMethod;
import com.restaurante.model.enums.CaixaOperadorDivergenceReasonCategory;
import com.restaurante.model.enums.CaixaOperadorDivergenceSeverity;
import com.restaurante.model.enums.CaixaOperadorDivergenceStatus;
import com.restaurante.model.enums.CaixaOperadorDivergenceType;
import com.restaurante.model.enums.CaixaOperadorSessionStatus;
import com.restaurante.model.enums.DeviceCapability;
import com.restaurante.model.enums.OperationalEntityType;
import com.restaurante.model.enums.OperationalEventType;
import com.restaurante.model.enums.OperationalOrigem;
import com.restaurante.model.enums.TenantUserRole;
import com.restaurante.repository.UserRepository;
import com.restaurante.security.device.DevicePrincipal;
import com.restaurante.security.tenant.TenantContext;
import com.restaurante.security.tenant.TenantGuard;
import com.restaurante.service.operacional.OperationalEventLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CaixaOperadorDivergenceService {

    private static final int MAX_DESCRIPTION_LEN = 2000;

    private final CaixaOperadorDivergenceProperties props;
    private final CaixaOperadorDivergenceRepository divergenceRepository;
    private final CaixaOperadorAdjustmentRepository adjustmentRepository;
    private final CaixaOperadorSessionRepository caixaRepository;
    private final UserRepository userRepository;
    private final TenantGuard tenantGuard;
    private final OperationalEventLogService operationalEventLogService;

    @Transactional
    public List<CaixaOperadorDivergence> autoCreateDraftsIfNeeded(CaixaOperadorSession caixa, String ip, String userAgent) {
        if (caixa == null || caixa.getTenant() == null || caixa.getTenant().getId() == null) {
            throw new BusinessException("Caixa inválido para criar divergência.");
        }
        if (caixa.getStatus() != CaixaOperadorSessionStatus.CLOSED && caixa.getStatus() != CaixaOperadorSessionStatus.DISPUTED) {
            return List.of();
        }

        CaixasDiff diff = computeDiff(caixa);
        if (!diff.hasAnyDifference()) return List.of();

        CaixaOperadorDivergence cash = null;
        CaixaOperadorDivergence tpa = null;
        CaixaOperadorDivergence mixed = null;

        if (diff.cashDiff.compareTo(BigDecimal.ZERO) != 0) {
            cash = createOrGetOpen(caixa, CaixaOperadorDivergencePaymentMethod.CASH,
                    diff.cashDiff.signum() < 0 ? CaixaOperadorDivergenceType.CASH_SHORTAGE : CaixaOperadorDivergenceType.CASH_SURPLUS,
                    diff.expectedCash, diff.declaredCash, diff.cashDiff, diff.abs(diff.cashDiff),
                    severityFor(diff.abs(diff.cashDiff)),
                    ip,
                    userAgent);
        }
        if (diff.tpaDiff.compareTo(BigDecimal.ZERO) != 0) {
            tpa = createOrGetOpen(caixa, CaixaOperadorDivergencePaymentMethod.TPA,
                    diff.tpaDiff.signum() < 0 ? CaixaOperadorDivergenceType.TPA_SHORTAGE : CaixaOperadorDivergenceType.TPA_SURPLUS,
                    diff.expectedTpa, diff.declaredTpa, diff.tpaDiff, diff.abs(diff.tpaDiff),
                    severityFor(diff.abs(diff.tpaDiff)),
                    ip,
                    userAgent);
        }
        if (diff.cashDiff.compareTo(BigDecimal.ZERO) != 0 && diff.tpaDiff.compareTo(BigDecimal.ZERO) != 0) {
            BigDecimal expectedManual = diff.expectedCash.add(diff.expectedTpa);
            BigDecimal declaredManual = diff.declaredCash.add(diff.declaredTpa);
            BigDecimal manualDiff = declaredManual.subtract(expectedManual);
            if (manualDiff.compareTo(BigDecimal.ZERO) != 0) {
                mixed = createOrGetOpen(caixa, CaixaOperadorDivergencePaymentMethod.MANUAL_TOTAL,
                        CaixaOperadorDivergenceType.MIXED_DIFFERENCE,
                        expectedManual, declaredManual, manualDiff, diff.abs(manualDiff),
                        severityFor(diff.abs(manualDiff)),
                        ip,
                        userAgent);
            }
        }

        if (caixa.getStatus() == CaixaOperadorSessionStatus.CLOSED) {
            caixa.setStatus(CaixaOperadorSessionStatus.DISPUTED);
            caixaRepository.save(caixa);
        }

        return java.util.stream.Stream.of(cash, tpa, mixed).filter(java.util.Objects::nonNull).toList();
    }

    @Transactional
    public CaixaOperadorDivergence justifyByDevice(DevicePrincipal device,
                                                   Long divergenceId,
                                                   CaixaOperadorDivergenceReasonCategory reasonCategory,
                                                   String description,
                                                   String ip,
                                                   String userAgent) {
        requireCapability(device, DeviceCapability.JUSTIFY_OPERATOR_CASH_DIVERGENCE);
        CaixaOperadorDivergence d = divergenceRepository.findByIdForUpdate(divergenceId)
                .orElseThrow(() -> notFoundDevice("Divergência não encontrada."));
        if (!d.getTenant().getId().equals(device.tenantId())) throw notFoundDevice("Divergência não encontrada.");
        if (!d.getDispositivoOperacional().getId().equals(device.dispositivoId())) throw forbiddenDevice("Device não autorizado a justificar esta divergência.");
        if (d.getStatus() != CaixaOperadorDivergenceStatus.DRAFT) throw invalidStateDevice("Apenas DRAFT pode ser justificado.");
        if (reasonCategory == null) throw invalidRequestDevice("reasonCategory é obrigatório.");
        String desc = sanitizeDescription(description);
        if (desc == null) throw invalidRequestDevice("description é obrigatório.");

        d.setReasonCategory(reasonCategory);
        d.setDescription(desc);
        d = divergenceRepository.save(d);

        operationalEventLogService.logGeneric(
                OperationalEventType.CAIXA_OPERADOR_DIVERGENCE_JUSTIFIED,
                OperationalEntityType.CAIXA_OPERADOR_SESSION,
                d.getCaixaOperadorSession().getId(),
                OperationalOrigem.DEVICE_POS,
                "Divergência justificada (device)",
                Map.of(
                        "divergenceId", d.getId(),
                        "caixaId", d.getCaixaOperadorSession().getId(),
                        "type", d.getType().name(),
                        "severity", d.getSeverity().name(),
                        "paymentMethod", d.getPaymentMethod().name()
                ),
                ip,
                userAgent
        );

        return d;
    }

    @Transactional
    public CaixaOperadorDivergence submitByDevice(DevicePrincipal device,
                                                  Long divergenceId,
                                                  String ip,
                                                  String userAgent) {
        requireCapability(device, DeviceCapability.SUBMIT_OPERATOR_CASH_DIVERGENCE);
        CaixaOperadorDivergence d = divergenceRepository.findByIdForUpdate(divergenceId)
                .orElseThrow(() -> notFoundDevice("Divergência não encontrada."));
        if (!d.getTenant().getId().equals(device.tenantId())) throw notFoundDevice("Divergência não encontrada.");
        if (!d.getDispositivoOperacional().getId().equals(device.dispositivoId())) throw forbiddenDevice("Device não autorizado a submeter esta divergência.");
        if (d.getStatus() != CaixaOperadorDivergenceStatus.DRAFT) throw invalidStateDevice("Apenas DRAFT pode ser submetido.");
        if (d.getReasonCategory() == null || d.getDescription() == null || d.getDescription().isBlank()) {
            throw invalidRequestDevice("reasonCategory e description são obrigatórios para submissão.");
        }

        d.setStatus(CaixaOperadorDivergenceStatus.SUBMITTED);
        d.setSubmittedBy(d.getOperador());
        d.setSubmittedAt(LocalDateTime.now());
        d = divergenceRepository.save(d);

        operationalEventLogService.logGeneric(
                OperationalEventType.CAIXA_OPERADOR_DIVERGENCE_SUBMITTED,
                OperationalEntityType.CAIXA_OPERADOR_SESSION,
                d.getCaixaOperadorSession().getId(),
                OperationalOrigem.DEVICE_POS,
                "Divergência submetida (device)",
                Map.of(
                        "divergenceId", d.getId(),
                        "caixaId", d.getCaixaOperadorSession().getId(),
                        "type", d.getType().name(),
                        "severity", d.getSeverity().name(),
                        "paymentMethod", d.getPaymentMethod().name()
                ),
                ip,
                userAgent
        );

        return d;
    }

    @Transactional
    public CaixaOperadorDivergence approveByTenant(Long divergenceId,
                                                   String reviewNotes,
                                                   CaixaOperadorAdjustmentType adjustmentType,
                                                   CaixaOperadorAdjustmentDirection direction,
                                                   String evidenceReference) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE);
        TenantContext ctx = tenantGuard.requireContext();

        CaixaOperadorDivergence d = divergenceRepository.findByIdForUpdate(divergenceId)
                .orElseThrow(() -> new BusinessException("Divergência não encontrada."));
        tenantGuard.assertResourceBelongsToTenant(d.getTenant().getId());
        if (d.getStatus() != CaixaOperadorDivergenceStatus.SUBMITTED) {
            throw new BusinessException("Apenas SUBMITTED pode ser aprovado.");
        }

        User reviewer = userRepository.findById(ctx.userId()).orElseThrow(() -> new BusinessException("Usuário não encontrado."));
        d.setStatus(CaixaOperadorDivergenceStatus.APPROVED);
        d.setReviewedBy(reviewer);
        d.setReviewedAt(LocalDateTime.now());
        d.setReviewNotes(trimToNull(reviewNotes));
        d = divergenceRepository.save(d);

        if (adjustmentType != null) {
            if (direction == null) throw new BusinessException("direction é obrigatório quando adjustmentType é informado.");
            CaixaOperadorAdjustment adj = new CaixaOperadorAdjustment();
            adj.setTenant(d.getTenant());
            adj.setDivergence(d);
            adj.setCaixaOperadorSession(d.getCaixaOperadorSession());
            adj.setAdjustmentType(adjustmentType);
            adj.setPaymentMethod(d.getPaymentMethod());
            adj.setAmount(d.getAbsoluteDifferenceAmount());
            adj.setDirection(direction);
            adj.setStatus(CaixaOperadorAdjustmentStatus.APPROVED);
            adj.setApprovedBy(reviewer);
            adj.setApprovedAt(LocalDateTime.now());
            adj.setReason("AUTO_FROM_DIVERGENCE_APPROVAL");
            adj.setEvidenceReference(trimToNull(evidenceReference));
            adjustmentRepository.save(adj);

            operationalEventLogService.logGeneric(
                    OperationalEventType.CAIXA_OPERADOR_ADJUSTMENT_CREATED,
                    OperationalEntityType.CAIXA_OPERADOR_SESSION,
                    d.getCaixaOperadorSession().getId(),
                    OperationalOrigem.SYSTEM,
                    "Ajuste criado (aprovação divergência)",
                    Map.of(
                            "divergenceId", d.getId(),
                            "adjustmentType", adjustmentType.name(),
                            "amount", adj.getAmount(),
                            "direction", direction.name()
                    ),
                    null,
                    null
            );
        }

        operationalEventLogService.logGeneric(
                OperationalEventType.CAIXA_OPERADOR_DIVERGENCE_APPROVED,
                OperationalEntityType.CAIXA_OPERADOR_SESSION,
                d.getCaixaOperadorSession().getId(),
                OperationalOrigem.SYSTEM,
                "Divergência aprovada",
                Map.of(
                        "divergenceId", d.getId(),
                        "type", d.getType().name(),
                        "severity", d.getSeverity().name(),
                        "paymentMethod", d.getPaymentMethod().name(),
                        "reviewedByUserId", ctx.userId()
                ),
                null,
                null
        );

        return d;
    }

    @Transactional
    public CaixaOperadorDivergence rejectByTenant(Long divergenceId, String reviewNotes) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE);
        TenantContext ctx = tenantGuard.requireContext();

        CaixaOperadorDivergence d = divergenceRepository.findByIdForUpdate(divergenceId)
                .orElseThrow(() -> new BusinessException("Divergência não encontrada."));
        tenantGuard.assertResourceBelongsToTenant(d.getTenant().getId());
        if (d.getStatus() != CaixaOperadorDivergenceStatus.SUBMITTED) {
            throw new BusinessException("Apenas SUBMITTED pode ser rejeitado.");
        }

        User reviewer = userRepository.findById(ctx.userId()).orElseThrow(() -> new BusinessException("Usuário não encontrado."));
        d.setStatus(CaixaOperadorDivergenceStatus.REJECTED);
        d.setReviewedBy(reviewer);
        d.setReviewedAt(LocalDateTime.now());
        d.setReviewNotes(trimToNull(reviewNotes));
        d = divergenceRepository.save(d);

        operationalEventLogService.logGeneric(
                OperationalEventType.CAIXA_OPERADOR_DIVERGENCE_REJECTED,
                OperationalEntityType.CAIXA_OPERADOR_SESSION,
                d.getCaixaOperadorSession().getId(),
                OperationalOrigem.SYSTEM,
                "Divergência rejeitada",
                Map.of(
                        "divergenceId", d.getId(),
                        "type", d.getType().name(),
                        "severity", d.getSeverity().name(),
                        "paymentMethod", d.getPaymentMethod().name(),
                        "reviewedByUserId", ctx.userId()
                ),
                null,
                null
        );

        return d;
    }

    private CaixaOperadorDivergence createOrGetOpen(CaixaOperadorSession caixa,
                                                    CaixaOperadorDivergencePaymentMethod method,
                                                    CaixaOperadorDivergenceType type,
                                                    BigDecimal expected,
                                                    BigDecimal declared,
                                                    BigDecimal diff,
                                                    BigDecimal absDiff,
                                                    CaixaOperadorDivergenceSeverity severity,
                                                    String ip,
                                                    String userAgent) {
        return divergenceRepository.findOpenByTenantAndCaixaAndType(caixa.getTenant().getId(), caixa.getId(), type)
                .orElseGet(() -> {
                    CaixaOperadorDivergence d = new CaixaOperadorDivergence();
                    d.setTenant(caixa.getTenant());
                    d.setUnidadeAtendimento(caixa.getUnidadeAtendimento());
                    d.setTurnoOperacional(caixa.getTurnoOperacional());
                    d.setCaixaOperadorSession(caixa);
                    d.setDispositivoOperacional(caixa.getDispositivoOperacional());
                    d.setOperador(caixa.getOperador());
                    d.setStatus(CaixaOperadorDivergenceStatus.DRAFT);
                    d.setPaymentMethod(method);
                    d.setType(type);
                    d.setSeverity(severity);
                    d.setExpectedAmount(expected);
                    d.setDeclaredAmount(declared);
                    d.setDifferenceAmount(diff);
                    d.setAbsoluteDifferenceAmount(absDiff);
                    try {
                        d = divergenceRepository.save(d);
                    } catch (DataIntegrityViolationException ex) {
                        return divergenceRepository.findOpenByTenantAndCaixaAndType(caixa.getTenant().getId(), caixa.getId(), type).orElseThrow();
                    }

                    operationalEventLogService.logGeneric(
                            OperationalEventType.CAIXA_OPERADOR_DIVERGENCE_CREATED,
                            OperationalEntityType.CAIXA_OPERADOR_SESSION,
                            caixa.getId(),
                            OperationalOrigem.SYSTEM,
                            "Divergência criada automaticamente",
                            Map.of(
                                    "divergenceId", d.getId(),
                                    "type", type.name(),
                                    "severity", severity.name(),
                                    "paymentMethod", method.name(),
                                    "differenceAmount", diff
                            ),
                            ip,
                            userAgent
                    );
                    return d;
                });
    }

    private CaixaOperadorDivergenceSeverity severityFor(BigDecimal absDifference) {
        BigDecimal v = abs(absDifference);
        if (v.compareTo(BigDecimal.ZERO) == 0) return CaixaOperadorDivergenceSeverity.INFO;
        if (v.compareTo(nz(props.getLowThreshold())) <= 0) return CaixaOperadorDivergenceSeverity.LOW;
        if (v.compareTo(nz(props.getMediumThreshold())) <= 0) return CaixaOperadorDivergenceSeverity.MEDIUM;
        if (v.compareTo(nz(props.getCriticalThreshold())) >= 0) return CaixaOperadorDivergenceSeverity.CRITICAL;
        return CaixaOperadorDivergenceSeverity.HIGH;
    }

    private static BigDecimal abs(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v.abs();
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static String sanitizeDescription(String description) {
        if (description == null) return null;
        String t = description.trim();
        if (t.isEmpty()) return null;
        if (t.length() > MAX_DESCRIPTION_LEN) {
            t = t.substring(0, MAX_DESCRIPTION_LEN);
        }
        return t;
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private void requireCapability(DevicePrincipal device, DeviceCapability cap) {
        if (device.capabilities() == null || !device.capabilities().contains(cap)) {
            throw forbiddenDevice("Capability requerida: " + cap.name());
        }
    }

    private static DeviceApiException notFoundDevice(String msg) {
        return new DeviceApiException(HttpStatus.NOT_FOUND, DeviceErrorResponse.DeviceErrorCode.DEVICE_REQUEST_INVALID, msg, false, DeviceErrorResponse.DeviceRecoveryAction.NONE, null);
    }

    private static DeviceApiException forbiddenDevice(String msg) {
        return new DeviceApiException(HttpStatus.FORBIDDEN, DeviceErrorResponse.DeviceErrorCode.DEVICE_FORBIDDEN, msg, false, DeviceErrorResponse.DeviceRecoveryAction.NONE, null);
    }

    private static DeviceApiException invalidStateDevice(String msg) {
        return new DeviceApiException(HttpStatus.CONFLICT, DeviceErrorResponse.DeviceErrorCode.DEVICE_REQUEST_INVALID, msg, false, DeviceErrorResponse.DeviceRecoveryAction.NONE, null);
    }

    private static DeviceApiException invalidRequestDevice(String msg) {
        return new DeviceApiException(HttpStatus.BAD_REQUEST, DeviceErrorResponse.DeviceErrorCode.DEVICE_REQUEST_INVALID, msg, true, DeviceErrorResponse.DeviceRecoveryAction.NONE, null);
    }

    private static CaixasDiff computeDiff(CaixaOperadorSession caixa) {
        CaixasDiff d = new CaixasDiff();
        d.expectedCash = nz(caixa.getExpectedCashAmount());
        d.declaredCash = nz(caixa.getDeclaredCashAmount());
        d.cashDiff = nz(caixa.getCashDifferenceAmount());
        d.expectedTpa = nz(caixa.getExpectedTpaAmount());
        d.declaredTpa = nz(caixa.getDeclaredTpaAmount());
        d.tpaDiff = nz(caixa.getTpaDifferenceAmount());
        return d;
    }

    private static final class CaixasDiff {
        BigDecimal expectedCash;
        BigDecimal declaredCash;
        BigDecimal cashDiff;
        BigDecimal expectedTpa;
        BigDecimal declaredTpa;
        BigDecimal tpaDiff;

        boolean hasAnyDifference() {
            return cashDiff != null && cashDiff.compareTo(BigDecimal.ZERO) != 0
                    || tpaDiff != null && tpaDiff.compareTo(BigDecimal.ZERO) != 0;
        }

        BigDecimal abs(BigDecimal v) {
            return v == null ? BigDecimal.ZERO : v.abs();
        }
    }
}

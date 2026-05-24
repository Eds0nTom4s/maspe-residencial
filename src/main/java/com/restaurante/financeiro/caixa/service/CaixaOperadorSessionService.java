package com.restaurante.financeiro.caixa.service;

import com.restaurante.exception.BusinessException;
import com.restaurante.exception.DeviceApiException;
import com.restaurante.dto.response.DeviceErrorResponse;
import com.restaurante.financeiro.caixa.repository.CaixaOperadorSessionItemRepository;
import com.restaurante.financeiro.caixa.repository.CaixaOperadorSessionRepository;
import com.restaurante.financeiro.repository.OrdemPagamentoRepository;
import com.restaurante.financeiro.repository.PagamentoGatewayRepository;
import com.restaurante.model.entity.CaixaOperadorSession;
import com.restaurante.model.entity.CaixaOperadorSessionItem;
import com.restaurante.model.entity.DispositivoOperacional;
import com.restaurante.model.entity.Instituicao;
import com.restaurante.model.entity.OrdemPagamento;
import com.restaurante.model.entity.Pagamento;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.TurnoOperacional;
import com.restaurante.model.entity.UnidadeAtendimento;
import com.restaurante.model.entity.User;
import com.restaurante.model.enums.CaixaOperadorSessionStatus;
import com.restaurante.model.enums.DeviceCapability;
import com.restaurante.model.enums.MetodoPagamentoManual;
import com.restaurante.model.enums.OperationalEntityType;
import com.restaurante.model.enums.OperationalEventType;
import com.restaurante.model.enums.OperationalOrigem;
import com.restaurante.model.enums.OrdemPagamentoStatus;
import com.restaurante.model.enums.PaymentMethodCode;
import com.restaurante.repository.DispositivoOperacionalRepository;
import com.restaurante.repository.InstituicaoRepository;
import com.restaurante.repository.TenantRepository;
import com.restaurante.repository.TurnoOperacionalRepository;
import com.restaurante.repository.UnidadeAtendimentoRepository;
import com.restaurante.repository.UserRepository;
import com.restaurante.repository.TenantUserRepository;
import com.restaurante.model.enums.TenantUserEstado;
import com.restaurante.security.device.DevicePrincipal;
import com.restaurante.security.tenant.TenantContext;
import com.restaurante.security.tenant.TenantGuard;
import com.restaurante.service.operacional.OperationalEventLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CaixaOperadorSessionService {

    private final TenantRepository tenantRepository;
    private final InstituicaoRepository instituicaoRepository;
    private final UnidadeAtendimentoRepository unidadeAtendimentoRepository;
    private final DispositivoOperacionalRepository dispositivoOperacionalRepository;
    private final TurnoOperacionalRepository turnoOperacionalRepository;
    private final UserRepository userRepository;
    private final TenantUserRepository tenantUserRepository;

    private final CaixaOperadorSessionRepository caixaRepository;
    private final CaixaOperadorSessionItemRepository itemRepository;
    private final OrdemPagamentoRepository ordemPagamentoRepository;
    private final PagamentoGatewayRepository pagamentoGatewayRepository;

    private final OperationalEventLogService operationalEventLogService;
    private final TenantGuard tenantGuard;

    @Transactional
    public CaixaOperadorSession abrir(DevicePrincipal device, Long operadorUserId, Long turnoId, String notes, String ip, String userAgent) {
        requireCapability(device, DeviceCapability.OPEN_OPERATOR_CASH_SESSION);

        if (operadorUserId == null) {
            throw new DeviceApiException(HttpStatus.BAD_REQUEST,
                    DeviceErrorResponse.DeviceErrorCode.DEVICE_REQUEST_INVALID,
                    "operadorUserId é obrigatório.",
                    false,
                    DeviceErrorResponse.DeviceRecoveryAction.NONE,
                    null);
        }

        caixaRepository.findByTenantIdAndDispositivoOperacionalIdAndStatus(device.tenantId(), device.dispositivoId(), CaixaOperadorSessionStatus.OPEN)
                .ifPresent(existing -> {
                    throw new DeviceApiException(HttpStatus.CONFLICT,
                            DeviceErrorResponse.DeviceErrorCode.DEVICE_REQUEST_INVALID,
                            "Já existe um caixa OPEN para este device.",
                            false,
                            DeviceErrorResponse.DeviceRecoveryAction.NONE,
                            null);
                });

        Tenant tenant = tenantRepository.findById(device.tenantId())
                .orElseThrow(() -> new DeviceApiException(HttpStatus.NOT_FOUND,
                        DeviceErrorResponse.DeviceErrorCode.DEVICE_REQUEST_INVALID,
                        "Tenant não encontrado.",
                        false,
                        DeviceErrorResponse.DeviceRecoveryAction.NONE,
                        null));
        Instituicao inst = instituicaoRepository.findById(device.instituicaoId())
                .orElseThrow(() -> new DeviceApiException(HttpStatus.NOT_FOUND,
                        DeviceErrorResponse.DeviceErrorCode.DEVICE_REQUEST_INVALID,
                        "Instituição não encontrada.",
                        false,
                        DeviceErrorResponse.DeviceRecoveryAction.NONE,
                        null));
        UnidadeAtendimento ua = unidadeAtendimentoRepository.findById(device.unidadeAtendimentoId())
                .orElseThrow(() -> new DeviceApiException(HttpStatus.NOT_FOUND,
                        DeviceErrorResponse.DeviceErrorCode.DEVICE_REQUEST_INVALID,
                        "Unidade não encontrada.",
                        false,
                        DeviceErrorResponse.DeviceRecoveryAction.NONE,
                        null));
        DispositivoOperacional dispositivo = dispositivoOperacionalRepository.findById(device.dispositivoId())
                .orElseThrow(() -> new DeviceApiException(HttpStatus.NOT_FOUND,
                        DeviceErrorResponse.DeviceErrorCode.DEVICE_REQUEST_INVALID,
                        "Device não encontrado.",
                        false,
                        DeviceErrorResponse.DeviceRecoveryAction.NONE,
                        null));

        if (!tenantUserRepository.existsByTenantIdAndUserIdAndEstado(device.tenantId(), operadorUserId, TenantUserEstado.ATIVO)) {
            throw new DeviceApiException(HttpStatus.CONFLICT,
                    DeviceErrorResponse.DeviceErrorCode.DEVICE_REQUEST_INVALID,
                    "Operador não pertence ao tenant.",
                    false,
                    DeviceErrorResponse.DeviceRecoveryAction.NONE,
                    null);
        }
        User operador = userRepository.findById(operadorUserId)
                .orElseThrow(() -> new DeviceApiException(HttpStatus.NOT_FOUND,
                        DeviceErrorResponse.DeviceErrorCode.DEVICE_REQUEST_INVALID,
                        "Operador não encontrado.",
                        false,
                        DeviceErrorResponse.DeviceRecoveryAction.NONE,
                        null));

        TurnoOperacional turno = null;
        if (device.unidadeAtendimentoId() != null) {
            turno = turnoOperacionalRepository.findOpenByTenantAndInstituicaoAndUnidade(device.tenantId(), device.instituicaoId(), device.unidadeAtendimentoId())
                    .orElse(null);
        }
        if (turnoId != null) {
            TurnoOperacional requested = turnoOperacionalRepository.findById(turnoId)
                    .orElseThrow(() -> new DeviceApiException(HttpStatus.NOT_FOUND,
                            DeviceErrorResponse.DeviceErrorCode.DEVICE_REQUEST_INVALID,
                            "Turno não encontrado.",
                            false,
                            DeviceErrorResponse.DeviceRecoveryAction.NONE,
                            null));
            if (!requested.getTenant().getId().equals(device.tenantId())) {
                throw new DeviceApiException(HttpStatus.NOT_FOUND,
                        DeviceErrorResponse.DeviceErrorCode.DEVICE_REQUEST_INVALID,
                        "Turno não encontrado.",
                        false,
                        DeviceErrorResponse.DeviceRecoveryAction.NONE,
                        null);
            }
            if (turno != null && !requested.getId().equals(turno.getId())) {
                throw new DeviceApiException(HttpStatus.CONFLICT,
                        DeviceErrorResponse.DeviceErrorCode.DEVICE_REQUEST_INVALID,
                        "Turno informado não corresponde ao turno aberto.",
                        false,
                        DeviceErrorResponse.DeviceRecoveryAction.NONE,
                        null);
            }
            turno = requested;
        }

        CaixaOperadorSession caixa = new CaixaOperadorSession();
        caixa.setTenant(tenant);
        caixa.setInstituicao(inst);
        caixa.setUnidadeAtendimento(ua);
        caixa.setTurnoOperacional(turno);
        caixa.setDispositivoOperacional(dispositivo);
        caixa.setOperador(operador);
        caixa.setOpenedBy(operador);
        caixa.setStatus(CaixaOperadorSessionStatus.OPEN);
        caixa.setOpenedAt(LocalDateTime.now());
        caixa.setNotes(trimToNull(notes));

        caixa = caixaRepository.save(caixa);

        operationalEventLogService.logGeneric(
                OperationalEventType.CAIXA_OPERADOR_SESSION_OPENED,
                OperationalEntityType.CAIXA_OPERADOR_SESSION,
                caixa.getId(),
                OperationalOrigem.DEVICE_POS,
                "Caixa aberto (operador/device)",
                Map.of(
                        "caixaId", caixa.getId(),
                        "instituicaoId", device.instituicaoId(),
                        "unidadeAtendimentoId", device.unidadeAtendimentoId(),
                        "deviceId", device.dispositivoId(),
                        "operadorUserId", operadorUserId,
                        "turnoId", turno != null ? turno.getId() : null
                ),
                ip,
                userAgent
        );

        return caixa;
    }

    @Transactional(readOnly = true)
    public CaixaOperadorSession buscarOpenDoDevice(DevicePrincipal device) {
        requireCapability(device, DeviceCapability.VIEW_OPERATOR_CASH_SESSION);
        return caixaRepository.findByTenantIdAndDispositivoOperacionalIdAndStatus(device.tenantId(), device.dispositivoId(), CaixaOperadorSessionStatus.OPEN)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public CaixaOperadorSession buscarPorIdDoDevice(DevicePrincipal device, Long caixaId) {
        requireCapability(device, DeviceCapability.VIEW_OPERATOR_CASH_SESSION);
        if (caixaId == null) return null;
        CaixaOperadorSession caixa = caixaRepository.findById(caixaId).orElse(null);
        if (caixa == null) return null;
        if (!caixa.getTenant().getId().equals(device.tenantId())) return null;
        if (!caixa.getDispositivoOperacional().getId().equals(device.dispositivoId())) return null;
        return caixa;
    }

    @Transactional
    public CaixaOperadorSession fechar(DevicePrincipal device,
                                      Long caixaId,
                                      Long closedByUserId,
                                      BigDecimal declaredCashAmount,
                                      BigDecimal declaredTpaAmount,
                                      String closeReason,
                                      String notes,
                                      String ip,
                                      String userAgent) {
        requireCapability(device, DeviceCapability.CLOSE_OPERATOR_CASH_SESSION);

        if (caixaId == null) {
            throw new DeviceApiException(HttpStatus.BAD_REQUEST,
                    DeviceErrorResponse.DeviceErrorCode.DEVICE_REQUEST_INVALID,
                    "caixaId é obrigatório.",
                    false,
                    DeviceErrorResponse.DeviceRecoveryAction.NONE,
                    null);
        }
        if (declaredCashAmount != null && declaredCashAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw invalidDeclared("declaredCashAmount inválido.");
        }
        if (declaredTpaAmount != null && declaredTpaAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw invalidDeclared("declaredTpaAmount inválido.");
        }

        CaixaOperadorSession caixa = caixaRepository.findByIdForUpdate(caixaId)
                .orElseThrow(() -> new DeviceApiException(HttpStatus.NOT_FOUND,
                        DeviceErrorResponse.DeviceErrorCode.DEVICE_REQUEST_INVALID,
                        "Caixa não encontrado.",
                        false,
                        DeviceErrorResponse.DeviceRecoveryAction.NONE,
                        null));

        if (!caixa.getTenant().getId().equals(device.tenantId())) {
            throw new DeviceApiException(HttpStatus.NOT_FOUND,
                    DeviceErrorResponse.DeviceErrorCode.DEVICE_REQUEST_INVALID,
                    "Caixa não encontrado.",
                    false,
                    DeviceErrorResponse.DeviceRecoveryAction.NONE,
                    null);
        }
        if (!caixa.getDispositivoOperacional().getId().equals(device.dispositivoId())) {
            throw new DeviceApiException(HttpStatus.FORBIDDEN,
                    DeviceErrorResponse.DeviceErrorCode.DEVICE_REQUEST_INVALID,
                    "Device não autorizado a fechar este caixa.",
                    false,
                    DeviceErrorResponse.DeviceRecoveryAction.NONE,
                    null);
        }
        if (caixa.getStatus() != CaixaOperadorSessionStatus.OPEN) {
            throw new DeviceApiException(HttpStatus.CONFLICT,
                    DeviceErrorResponse.DeviceErrorCode.DEVICE_REQUEST_INVALID,
                    "Caixa não está OPEN.",
                    false,
                    DeviceErrorResponse.DeviceRecoveryAction.NONE,
                    null);
        }

        List<OrdemPagamento> ordens = ordemPagamentoRepository.findAllByTenantIdAndCaixaOperadorSessionIdAndStatus(
                device.tenantId(), caixa.getId(), OrdemPagamentoStatus.CONFIRMADA
        );

        BigDecimal expectedCash = sumOrdens(ordens, MetodoPagamentoManual.CASH);
        BigDecimal expectedTpa = sumOrdens(ordens, MetodoPagamentoManual.TPA);
        BigDecimal expectedManual = expectedCash.add(expectedTpa);

        BigDecimal declaredCash = declaredCashAmount != null ? declaredCashAmount : BigDecimal.ZERO;
        BigDecimal declaredTpa = declaredTpaAmount != null ? declaredTpaAmount : BigDecimal.ZERO;
        BigDecimal declaredManual = declaredCash.add(declaredTpa);

        caixa.setExpectedCashAmount(expectedCash);
        caixa.setExpectedTpaAmount(expectedTpa);
        caixa.setExpectedManualTotalAmount(expectedManual);
        caixa.setDeclaredCashAmount(declaredCash);
        caixa.setDeclaredTpaAmount(declaredTpa);
        caixa.setDeclaredManualTotalAmount(declaredManual);
        caixa.setCashDifferenceAmount(declaredCash.subtract(expectedCash));
        caixa.setTpaDifferenceAmount(declaredTpa.subtract(expectedTpa));
        caixa.setManualDifferenceAmount(declaredManual.subtract(expectedManual));
        caixa.setCloseReason(trimToNull(closeReason));
        caixa.setNotes(trimToNull(notes));
        caixa.setClosedAt(LocalDateTime.now());

        User closedBy = caixa.getOperador();
        if (closedByUserId != null) {
            if (!tenantUserRepository.existsByTenantIdAndUserIdAndEstado(device.tenantId(), closedByUserId, TenantUserEstado.ATIVO)) {
                throw new DeviceApiException(HttpStatus.CONFLICT,
                        DeviceErrorResponse.DeviceErrorCode.DEVICE_REQUEST_INVALID,
                        "closedByUserId não pertence ao tenant.",
                        false,
                        DeviceErrorResponse.DeviceRecoveryAction.NONE,
                        null);
            }
            closedBy = userRepository.findById(closedByUserId)
                    .orElseThrow(() -> new DeviceApiException(HttpStatus.NOT_FOUND,
                            DeviceErrorResponse.DeviceErrorCode.DEVICE_REQUEST_INVALID,
                            "closedByUserId não encontrado.",
                            false,
                            DeviceErrorResponse.DeviceRecoveryAction.NONE,
                            null));
        }
        caixa.setClosedBy(closedBy);
        caixa.setStatus(CaixaOperadorSessionStatus.CLOSED);

        caixa = caixaRepository.save(caixa);

        // Freeze items (idempotente: uk por ordem_pagamento_id)
        for (OrdemPagamento ordem : ordens) {
            CaixaOperadorSessionItem item = new CaixaOperadorSessionItem();
            item.setTenant(caixa.getTenant());
            item.setCaixaOperadorSession(caixa);
            item.setOrdemPagamento(ordem);
            item.setPedido(ordem.getPedido());
            item.setSessaoConsumo(ordem.getSessaoConsumo());
            item.setPaymentMethod(ordem.getMetodoSolicitado() == MetodoPagamentoManual.CASH ? PaymentMethodCode.CASH : PaymentMethodCode.TPA);
            item.setAmount(ordem.getValor());
            item.setConfirmedAt(ordem.getConfirmadoEm());
            item.setSource(ordem.getCriadoPorOrigem() != null ? ordem.getCriadoPorOrigem() : OperationalOrigem.DEVICE_POS);

            Pagamento pg = pagamentoGatewayRepository.findByTenantIdAndOrdemPagamentoId(device.tenantId(), ordem.getId()).orElse(null);
            item.setPagamento(pg);

            try {
                itemRepository.save(item);
            } catch (RuntimeException ignore) {
                // uk_caixa_item_ordem garante idempotência em re-close/retries
            }
        }

        operationalEventLogService.logGeneric(
                OperationalEventType.CAIXA_OPERADOR_SESSION_CLOSED,
                OperationalEntityType.CAIXA_OPERADOR_SESSION,
                caixa.getId(),
                OperationalOrigem.DEVICE_POS,
                "Caixa fechado (operador/device)",
                Map.ofEntries(
                        Map.entry("caixaId", (Object) caixa.getId()),
                        Map.entry("instituicaoId", device.instituicaoId()),
                        Map.entry("unidadeAtendimentoId", device.unidadeAtendimentoId()),
                        Map.entry("deviceId", device.dispositivoId()),
                        Map.entry("operadorUserId", caixa.getOperador().getId()),
                        Map.entry("expectedCashAmount", expectedCash),
                        Map.entry("declaredCashAmount", declaredCash),
                        Map.entry("cashDifferenceAmount", caixa.getCashDifferenceAmount()),
                        Map.entry("expectedTpaAmount", expectedTpa),
                        Map.entry("declaredTpaAmount", declaredTpa),
                        Map.entry("tpaDifferenceAmount", caixa.getTpaDifferenceAmount()),
                        Map.entry("manualDifferenceAmount", caixa.getManualDifferenceAmount())
                ),
                ip,
                userAgent
        );

        return caixa;
    }

    @Transactional
    public CaixaOperadorSession revisar(Long caixaId, CaixaOperadorSessionStatus status, String reviewNotes) {
        if (caixaId == null) throw new BusinessException("caixaId é obrigatório.");
        if (status == null || (status != CaixaOperadorSessionStatus.REVIEWED && status != CaixaOperadorSessionStatus.DISPUTED)) {
            throw new BusinessException("status inválido para revisão.");
        }

        tenantGuard.assertAnyTenantRole(
                com.restaurante.model.enums.TenantUserRole.TENANT_OWNER,
                com.restaurante.model.enums.TenantUserRole.TENANT_ADMIN,
                com.restaurante.model.enums.TenantUserRole.TENANT_FINANCE
        );
        TenantContext ctx = tenantGuard.requireContext();

        CaixaOperadorSession caixa = caixaRepository.findByIdForUpdate(caixaId)
                .orElseThrow(() -> new BusinessException("Caixa não encontrado."));
        tenantGuard.assertResourceBelongsToTenant(caixa.getTenant().getId());

        if (caixa.getStatus() != CaixaOperadorSessionStatus.CLOSED) {
            operationalEventLogService.logGeneric(
                    OperationalEventType.CAIXA_OPERADOR_SESSION_REVIEW_FAILED,
                    OperationalEntityType.CAIXA_OPERADOR_SESSION,
                    caixa.getId(),
                    OperationalOrigem.SYSTEM,
                    "Tentativa de revisão de caixa não-CLOSED",
                    Map.of("caixaId", caixa.getId(), "currentStatus", caixa.getStatus().name()),
                    null,
                    null
            );
            throw new BusinessException("Caixa deve estar CLOSED para revisão.");
        }

        User reviewer = userRepository.findById(ctx.userId())
                .orElseThrow(() -> new BusinessException("Usuário não encontrado."));

        caixa.setReviewedBy(reviewer);
        caixa.setReviewedAt(LocalDateTime.now());
        caixa.setReviewNotes(trimToNull(reviewNotes));
        caixa.setStatus(status);
        caixa = caixaRepository.save(caixa);

        operationalEventLogService.logGeneric(
                status == CaixaOperadorSessionStatus.REVIEWED
                        ? OperationalEventType.CAIXA_OPERADOR_SESSION_REVIEWED
                        : OperationalEventType.CAIXA_OPERADOR_SESSION_DISPUTED,
                OperationalEntityType.CAIXA_OPERADOR_SESSION,
                caixa.getId(),
                OperationalOrigem.SYSTEM,
                "Caixa revisado",
                Map.of(
                        "caixaId", caixa.getId(),
                        "status", status.name(),
                        "reviewedByUserId", ctx.userId()
                ),
                null,
                null
        );

        return caixa;
    }

    @Transactional(readOnly = true)
    public CaixaOperadorSession requireOpenForDevice(DevicePrincipal device) {
        CaixaOperadorSession caixa = caixaRepository.findByTenantIdAndDispositivoOperacionalIdAndStatus(device.tenantId(), device.dispositivoId(), CaixaOperadorSessionStatus.OPEN)
                .orElse(null);
        if (caixa == null) {
            operationalEventLogService.logGeneric(
                    OperationalEventType.CAIXA_OPERADOR_SESSION_REQUIRED_BUT_MISSING,
                    OperationalEntityType.CAIXA_OPERADOR_SESSION,
                    0L,
                    OperationalOrigem.DEVICE_POS,
                    "Caixa OPEN obrigatório para confirmação manual",
                    Map.of("deviceId", device.dispositivoId()),
                    null,
                    null
            );
            throw new DeviceApiException(HttpStatus.CONFLICT,
                    DeviceErrorResponse.DeviceErrorCode.DEVICE_REQUEST_INVALID,
                    "Caixa OPEN é obrigatório para confirmação manual (CASH/TPA).",
                    true,
                    DeviceErrorResponse.DeviceRecoveryAction.CONTACT_SUPPORT,
                    null);
        }
        return caixa;
    }

    private void requireCapability(DevicePrincipal device, DeviceCapability capability) {
        if (device.capabilities() == null || !device.capabilities().contains(capability)) {
            throw new DeviceApiException(HttpStatus.FORBIDDEN,
                    DeviceErrorResponse.DeviceErrorCode.DEVICE_FORBIDDEN,
                    "Capability requerida: " + capability.name(),
                    false,
                    DeviceErrorResponse.DeviceRecoveryAction.NONE,
                    null);
        }
    }

    private static DeviceApiException invalidDeclared(String message) {
        return new DeviceApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                DeviceErrorResponse.DeviceErrorCode.DEVICE_REQUEST_INVALID,
                message,
                false,
                DeviceErrorResponse.DeviceRecoveryAction.NONE,
                null);
    }

    private static BigDecimal sumOrdens(List<OrdemPagamento> ordens, MetodoPagamentoManual metodo) {
        if (ordens == null || ordens.isEmpty()) return BigDecimal.ZERO;
        BigDecimal total = BigDecimal.ZERO;
        for (OrdemPagamento o : ordens) {
            if (o.getMetodoSolicitado() == metodo && o.getValor() != null) {
                total = total.add(o.getValor());
            }
        }
        return total;
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}

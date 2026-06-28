package com.restaurante.financeiro.service;

import com.restaurante.config.FinancePendingPaymentsProperties;
import com.restaurante.financeiro.enums.MetodoPagamentoAppyPay;
import com.restaurante.financeiro.enums.PagamentoPollingStatus;
import com.restaurante.financeiro.monitoramento.dto.PagamentoPendenciaActionRecommended;
import com.restaurante.financeiro.monitoramento.dto.PagamentoPendenciaAlertLevel;
import com.restaurante.financeiro.monitoramento.dto.PagamentoPendenteResponse;
import com.restaurante.financeiro.monitoramento.dto.PagamentoPendenteResumoResponse;
import com.restaurante.financeiro.monitoramento.dto.PagamentoPollingDetalheResponse;
import com.restaurante.financeiro.monitoramento.dto.PagamentoPollingEventoRecenteResponse;
import com.restaurante.financeiro.monitoramento.dto.TurnoPagamentoAlertaCode;
import com.restaurante.financeiro.monitoramento.dto.TurnoPagamentoAlertaItemResponse;
import com.restaurante.financeiro.monitoramento.dto.TurnoPagamentoAlertaLevel;
import com.restaurante.financeiro.monitoramento.dto.TurnoPagamentoAlertasResponse;
import com.restaurante.financeiro.repository.PagamentoGatewayRepository;
import com.restaurante.model.entity.OperationalEventLog;
import com.restaurante.model.entity.Pagamento;
import com.restaurante.model.entity.Pedido;
import com.restaurante.model.entity.TurnoOperacional;
import com.restaurante.model.enums.OperationalEntityType;
import com.restaurante.model.enums.TenantUserRole;
import com.restaurante.model.enums.TurnoOperacionalStatus;
import com.restaurante.repository.OperationalEventLogRepository;
import com.restaurante.repository.TurnoOperacionalRepository;
import com.restaurante.security.tenant.TenantContext;
import com.restaurante.security.tenant.TenantGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PagamentoPendenteQueryService {

    private final TenantGuard tenantGuard;
    private final FinancePendingPaymentsProperties props;
    private final PagamentoGatewayRepository pagamentoGatewayRepository;
    private final OperationalEventLogRepository operationalEventLogRepository;
    private final TurnoOperacionalRepository turnoOperacionalRepository;

    public Page<PagamentoPendenteResponse> listarPendentes(
            Long turnoId,
            Long pedidoId,
            Long unidadeAtendimentoId,
            MetodoPagamentoAppyPay metodo,
            PagamentoPollingStatus pollingStatus,
            Boolean hasError,
            Integer olderThanMinutes,
            LocalDateTime de,
            LocalDateTime ate,
            Pageable pageable
    ) {
        tenantGuard.assertAnyTenantRole(
                TenantUserRole.TENANT_OWNER,
                TenantUserRole.TENANT_ADMIN,
                TenantUserRole.TENANT_FINANCE,
                TenantUserRole.TENANT_CASHIER,
                TenantUserRole.TENANT_OPERATOR
        );
        TenantContext ctx = tenantGuard.requireContext();

        Pageable safePageable = capPageable(pageable);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime from = de != null ? de : now.minusHours(props.getDefaultLookbackHours());
        LocalDateTime to = ate != null ? ate : now;
        LocalDateTime olderThan = olderThanMinutes != null ? now.minusMinutes(Math.max(0, olderThanMinutes)) : null;

        Page<Pagamento> page = pagamentoGatewayRepository.searchPendentesTenant(
                ctx.tenantId(),
                turnoId,
                pedidoId,
                unidadeAtendimentoId,
                metodo,
                pollingStatus,
                hasError == null ? null : hasError.toString(),
                olderThan,
                from,
                to,
                safePageable
        );

        List<PagamentoPendenteResponse> items = page.getContent().stream()
                .map(p -> toResponse(p, now))
                .toList();

        return new PageImpl<>(items, safePageable, page.getTotalElements());
    }

    public PagamentoPendenteResumoResponse resumoPendentes(Long turnoId, Long unidadeAtendimentoId, LocalDateTime de, LocalDateTime ate) {
        tenantGuard.assertAnyTenantRole(
                TenantUserRole.TENANT_OWNER,
                TenantUserRole.TENANT_ADMIN,
                TenantUserRole.TENANT_FINANCE,
                TenantUserRole.TENANT_CASHIER,
                TenantUserRole.TENANT_OPERATOR
        );
        TenantContext ctx = tenantGuard.requireContext();

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime from = de != null ? de : now.minusHours(props.getDefaultLookbackHours());
        LocalDateTime to = ate != null ? ate : now;

        // MVP: usar query paginada grande controlada pelo lookback (sem agregações complexas).
        Page<PagamentoPendenteResponse> page = listarPendentes(
                turnoId,
                null,
                unidadeAtendimentoId,
                null,
                null,
                null,
                null,
                from,
                to,
                Pageable.ofSize(Math.min(props.getMaxPageSize(), 200)).withPage(0)
        );

        List<PagamentoPendenteResponse> all = page.getContent();

        PagamentoPendenteResumoResponse r = new PagamentoPendenteResumoResponse();
        r.setTotalPendentes(all.size());
        r.setValorTotalPendente(all.stream().map(PagamentoPendenteResponse::getValor).reduce(BigDecimal.ZERO, BigDecimal::add));

        r.setTotalPollingEligible(all.stream().filter(p -> p.getPollingStatus() == PagamentoPollingStatus.ELIGIBLE).count());
        r.setTotalPollingInProgress(all.stream().filter(p -> p.getPollingStatus() == PagamentoPollingStatus.IN_PROGRESS).count());
        r.setTotalMaxAttemptsReached(all.stream().filter(p -> p.getPollingStatus() == PagamentoPollingStatus.MAX_ATTEMPTS_REACHED).count());
        r.setTotalComErroGateway(all.stream().filter(p -> p.getPollingLastErrorCode() != null).count());
        r.setTotalCriticos(all.stream().filter(p -> p.getAlertLevel() == PagamentoPendenciaAlertLevel.CRITICAL).count());
        r.setTotalWarnings(all.stream().filter(p -> p.getAlertLevel() == PagamentoPendenciaAlertLevel.WARNING).count());

        r.setMaisAntigoPendenteEm(all.stream().map(PagamentoPendenteResponse::getCriadoEm).min(LocalDateTime::compareTo).orElse(null));

        r.setPorPollingStatus(all.stream().collect(Collectors.groupingBy(p -> String.valueOf(p.getPollingStatus()), Collectors.counting())));
        r.setPorMetodoPagamento(all.stream().collect(Collectors.groupingBy(p -> String.valueOf(p.getMetodoPagamento()), Collectors.counting())));
        r.setPorUnidadeAtendimento(all.stream().collect(Collectors.groupingBy(PagamentoPendenteResponse::getUnidadeAtendimentoId, Collectors.counting())));
        r.setPorTurno(all.stream().collect(Collectors.groupingBy(PagamentoPendenteResponse::getTurnoOperacionalId, Collectors.counting())));

        return r;
    }

    public PagamentoPollingDetalheResponse detalhePolling(Long pagamentoId) {
        tenantGuard.assertAnyTenantRole(
                TenantUserRole.TENANT_OWNER,
                TenantUserRole.TENANT_ADMIN,
                TenantUserRole.TENANT_FINANCE,
                TenantUserRole.TENANT_CASHIER,
                TenantUserRole.TENANT_OPERATOR
        );
        TenantContext ctx = tenantGuard.requireContext();

        Pagamento pagamento = pagamentoGatewayRepository.findByIdAndTenantId(pagamentoId, ctx.tenantId())
                .orElseThrow(() -> new com.restaurante.exception.ResourceNotFoundException("Recurso não encontrado."));

        Pedido pedido = pagamento.getPedido();
        PagamentoPollingDetalheResponse r = new PagamentoPollingDetalheResponse();
        r.setPagamentoId(pagamento.getId());
        r.setPedidoId(pedido != null ? pedido.getId() : null);
        r.setNumeroPedido(pedido != null ? pedido.getNumero() : null);
        r.setStatusPagamento(pagamento.getStatus());
        r.setValor(pagamento.getAmount());
        r.setMoeda("AOA");
        r.setMetodoPagamento(pagamento.getMetodo());
        r.setExternalReference(pagamento.getExternalReference());
        r.setGatewayTransactionId(pagamento.getGatewayChargeId());
        r.setPollingEnabled(pagamento.isPollingEnabled());
        r.setPollingStatus(pagamento.getPollingStatus());
        r.setPollingAttempts(pagamento.getPollingAttempts());
        r.setLastPollingAttemptAt(pagamento.getLastPollingAttemptAt());
        r.setNextPollingAttemptAt(pagamento.getNextPollingAttemptAt());
        r.setGatewayStatusLastCheckedAt(pagamento.getGatewayStatusLastCheckedAt());
        r.setPollingLastErrorCode(pagamento.getPollingLastErrorCode());
        r.setPollingLastErrorMessage(pagamento.getPollingLastErrorMessage());
        r.setExpiresAt(pagamento.getExpiresAt());

        r.setCanManualPoll(pagamento.getStatus() != null && pagamento.getStatus().name().equals("PENDENTE"));
        r.setManualPollBlockedReason(r.isCanManualPoll() ? null : "Pagamento não está pendente.");

        List<OperationalEventLog> events = operationalEventLogRepository
                .findTop20ByTenantIdAndEntityTypeAndEntityIdOrderByCreatedAtDesc(ctx.tenantId(), OperationalEntityType.PAGAMENTO, pagamentoId);
        r.setEventosRecentes(events.stream().map(this::toEvento).toList());

        return r;
    }

    public TurnoPagamentoAlertasResponse alertasPorTurno(Long turnoId) {
        tenantGuard.assertAnyTenantRole(
                TenantUserRole.TENANT_OWNER,
                TenantUserRole.TENANT_ADMIN,
                TenantUserRole.TENANT_FINANCE,
                TenantUserRole.TENANT_CASHIER,
                TenantUserRole.TENANT_OPERATOR
        );
        TenantContext ctx = tenantGuard.requireContext();

        TurnoOperacional turno = turnoOperacionalRepository.findByIdAndTenantId(turnoId, ctx.tenantId())
                .orElseThrow(() -> new com.restaurante.exception.ResourceNotFoundException("Recurso não encontrado."));

        long pendentes = pagamentoGatewayRepository.countByTenantIdAndPedidoTurnoOperacionalIdAndStatus(ctx.tenantId(), turnoId, com.restaurante.financeiro.enums.StatusPagamentoGateway.PENDENTE);
        BigDecimal valorPend = pagamentoGatewayRepository.sumByTenantIdAndTurnoOperacionalIdAndStatus(ctx.tenantId(), turnoId, com.restaurante.financeiro.enums.StatusPagamentoGateway.PENDENTE);

        TurnoPagamentoAlertasResponse resp = new TurnoPagamentoAlertasResponse();
        resp.setTurnoId(turnoId);
        resp.setStatusTurno(turno.getStatus());
        resp.setTotalPagamentosPendentes(pendentes);
        resp.setValorPendente(valorPend);

        if (pendentes == 0) {
            resp.setAvisos(List.of());
            resp.setPagamentosCriticos(List.of());
            resp.setPagamentosPendentesResumo(new PagamentoPendenteResumoResponse());
            resp.setBloqueiaFecho(false);
            return resp;
        }

        List<PagamentoPendenteResponse> pendList = pagamentoGatewayRepository
                .findAllByTenantIdAndPedidoTurnoOperacionalIdAndStatus(ctx.tenantId(), turnoId, com.restaurante.financeiro.enums.StatusPagamentoGateway.PENDENTE)
                .stream()
                .map(p -> toResponse(p, LocalDateTime.now()))
                .toList();

        List<PagamentoPendenteResponse> criticos = pendList.stream()
                .filter(p -> p.getAlertLevel() == PagamentoPendenciaAlertLevel.CRITICAL)
                .sorted(Comparator.comparingLong(PagamentoPendenteResponse::getIdadeMinutos).reversed())
                .toList();

        long warn = pendList.stream().filter(p -> p.getAlertLevel() == PagamentoPendenciaAlertLevel.WARNING).count();
        long crit = criticos.size();

        resp.setTotalWarnings(warn);
        resp.setTotalCriticos(crit);
        resp.setPagamentosCriticos(criticos);
        resp.setPagamentosPendentesResumo(resumoPendentesFromList(pendList));

        resp.setBloqueiaFecho(props.isBlockTurnoCloseOnCritical() && crit > 0);

        TurnoPagamentoAlertaItemResponse item = new TurnoPagamentoAlertaItemResponse();
        item.setCode(TurnoPagamentoAlertaCode.PAYMENT_PENDING_CALLBACK);
        item.setLevel(crit > 0 ? TurnoPagamentoAlertaLevel.CRITICAL : TurnoPagamentoAlertaLevel.WARNING);
        item.setMessage("Há pagamentos pendentes no turno.");
        item.setActionRecommended(crit > 0 ? PagamentoPendenciaActionRecommended.MANUAL_POLL : PagamentoPendenciaActionRecommended.WAIT_NEXT_POLL);

        resp.setAvisos(List.of(item));
        return resp;
    }

    private PagamentoPendenteResumoResponse resumoPendentesFromList(List<PagamentoPendenteResponse> pendentes) {
        PagamentoPendenteResumoResponse r = new PagamentoPendenteResumoResponse();
        r.setTotalPendentes(pendentes.size());
        r.setValorTotalPendente(pendentes.stream().map(PagamentoPendenteResponse::getValor).reduce(BigDecimal.ZERO, BigDecimal::add));
        r.setTotalPollingEligible(pendentes.stream().filter(p -> p.getPollingStatus() == PagamentoPollingStatus.ELIGIBLE).count());
        r.setTotalPollingInProgress(pendentes.stream().filter(p -> p.getPollingStatus() == PagamentoPollingStatus.IN_PROGRESS).count());
        r.setTotalMaxAttemptsReached(pendentes.stream().filter(p -> p.getPollingStatus() == PagamentoPollingStatus.MAX_ATTEMPTS_REACHED).count());
        r.setTotalComErroGateway(pendentes.stream().filter(p -> p.getPollingLastErrorCode() != null).count());
        r.setTotalCriticos(pendentes.stream().filter(p -> p.getAlertLevel() == PagamentoPendenciaAlertLevel.CRITICAL).count());
        r.setTotalWarnings(pendentes.stream().filter(p -> p.getAlertLevel() == PagamentoPendenciaAlertLevel.WARNING).count());
        r.setMaisAntigoPendenteEm(pendentes.stream().map(PagamentoPendenteResponse::getCriadoEm).min(LocalDateTime::compareTo).orElse(null));
        r.setPorPollingStatus(pendentes.stream().collect(Collectors.groupingBy(p -> String.valueOf(p.getPollingStatus()), Collectors.counting())));
        r.setPorMetodoPagamento(pendentes.stream().collect(Collectors.groupingBy(p -> String.valueOf(p.getMetodoPagamento()), Collectors.counting())));
        r.setPorUnidadeAtendimento(pendentes.stream()
                .filter(p -> p.getUnidadeAtendimentoId() != null)
                .collect(Collectors.groupingBy(PagamentoPendenteResponse::getUnidadeAtendimentoId, Collectors.counting())));
        r.setPorTurno(pendentes.stream()
                .filter(p -> p.getTurnoOperacionalId() != null)
                .collect(Collectors.groupingBy(PagamentoPendenteResponse::getTurnoOperacionalId, Collectors.counting())));
        return r;
    }

    private Pageable capPageable(Pageable pageable) {
        if (pageable == null) return Pageable.ofSize(Math.min(props.getMaxPageSize(), 50));
        if (pageable.getPageSize() <= props.getMaxPageSize()) return pageable;
        return PageRequest.of(pageable.getPageNumber(), props.getMaxPageSize(), pageable.getSort());
    }

    private PagamentoPendenteResponse toResponse(Pagamento p, LocalDateTime now) {
        PagamentoPendenteResponse r = new PagamentoPendenteResponse();
        r.setPagamentoId(p.getId());
        Pedido ped = p.getPedido();
        r.setPedidoId(ped != null ? ped.getId() : null);
        r.setNumeroPedido(ped != null ? ped.getNumero() : null);
        r.setTurnoOperacionalId(ped != null && ped.getTurnoOperacional() != null ? ped.getTurnoOperacional().getId() : null);
        r.setTenantId(p.getTenant() != null ? p.getTenant().getId() : null);
        r.setInstituicaoId(ped != null && ped.getSessaoConsumo() != null && ped.getSessaoConsumo().getInstituicao() != null ? ped.getSessaoConsumo().getInstituicao().getId() : null);
        r.setUnidadeAtendimentoId(ped != null && ped.getSessaoConsumo() != null && ped.getSessaoConsumo().getUnidadeAtendimento() != null ? ped.getSessaoConsumo().getUnidadeAtendimento().getId() : null);
        r.setValor(p.getAmount());
        r.setMoeda("AOA");
        r.setMetodoPagamento(p.getMetodo());
        r.setOrigem(inferOrigem(p.getExternalReference()));
        r.setStatusPagamento(p.getStatus());
        r.setExternalReference(p.getExternalReference());
        r.setGatewayTransactionId(p.getGatewayChargeId());
        r.setPollingEnabled(p.isPollingEnabled());
        r.setPollingStatus(p.getPollingStatus());
        r.setPollingAttempts(p.getPollingAttempts());
        r.setLastPollingAttemptAt(p.getLastPollingAttemptAt());
        r.setNextPollingAttemptAt(p.getNextPollingAttemptAt());
        r.setGatewayStatusLastCheckedAt(p.getGatewayStatusLastCheckedAt());
        r.setPollingLastErrorCode(p.getPollingLastErrorCode());
        r.setPollingLastErrorMessage(p.getPollingLastErrorMessage());
        r.setCriadoEm(p.getCreatedAt());

        long ageMin = p.getCreatedAt() != null ? Math.max(0, Duration.between(p.getCreatedAt(), now).toMinutes()) : 0;
        r.setIdadeMinutos(ageMin);

        PagamentoPendenciaAlertLevel level;
        if (p.getPollingStatus() == PagamentoPollingStatus.MAX_ATTEMPTS_REACHED) level = PagamentoPendenciaAlertLevel.CRITICAL;
        else if (ageMin >= props.getCriticalAfterMinutes()) level = PagamentoPendenciaAlertLevel.CRITICAL;
        else if (ageMin >= props.getWarningAfterMinutes()) level = PagamentoPendenciaAlertLevel.WARNING;
        else level = PagamentoPendenciaAlertLevel.INFO;
        r.setAlertLevel(level);

        r.setActionRecommended(recommendAction(p, ageMin, level));
        return r;
    }

    private PagamentoPendenciaActionRecommended recommendAction(Pagamento p, long ageMin, PagamentoPendenciaAlertLevel level) {
        if (p.getStatus() != null && p.getStatus().isTerminal()) return PagamentoPendenciaActionRecommended.NONE;
        if (p.getPollingStatus() == PagamentoPollingStatus.IN_PROGRESS) return PagamentoPendenciaActionRecommended.WAIT_NEXT_POLL;
        if (p.getPollingStatus() == PagamentoPollingStatus.MAX_ATTEMPTS_REACHED) return PagamentoPendenciaActionRecommended.MANUAL_POLL;
        if (p.getPollingLastErrorCode() != null) return level == PagamentoPendenciaAlertLevel.CRITICAL ? PagamentoPendenciaActionRecommended.MANUAL_POLL : PagamentoPendenciaActionRecommended.WAIT_NEXT_POLL;
        if (ageMin < props.getWarningAfterMinutes()) return PagamentoPendenciaActionRecommended.WAIT_CALLBACK;
        if (level == PagamentoPendenciaAlertLevel.CRITICAL) return PagamentoPendenciaActionRecommended.MANUAL_POLL;
        return PagamentoPendenciaActionRecommended.WAIT_NEXT_POLL;
    }

    private String inferOrigem(String externalReference) {
        if (externalReference == null || externalReference.length() < 5) return "UNKNOWN";
        char type = externalReference.charAt(4);
        if (type == 'D') return "DEVICE_POS";
        if (type == 'Q') return "QR_PUBLICO";
        return "UNKNOWN";
    }

    private PagamentoPollingEventoRecenteResponse toEvento(OperationalEventLog e) {
        PagamentoPollingEventoRecenteResponse r = new PagamentoPollingEventoRecenteResponse();
        r.setEventId(e.getId());
        r.setEventType(e.getEventType());
        r.setOrigem(e.getOrigem());
        r.setMotivo(e.getMotivo());
        r.setCreatedAt(e.getCreatedAt());
        return r;
    }
}

package com.restaurante.financeiro.service;

import com.restaurante.financeiro.enums.StatusPagamentoGateway;
import com.restaurante.financeiro.monitoramento.dto.CallbackLogDetalheDTO;
import com.restaurante.financeiro.monitoramento.dto.CallbackLogResumoDTO;
import com.restaurante.financeiro.monitoramento.dto.FinanceiroPlatformResumoDTO;
import com.restaurante.financeiro.monitoramento.dto.FinanceiroTenantResumoDTO;
import com.restaurante.financeiro.monitoramento.dto.PagamentoMonitoramentoFiltro;
import com.restaurante.financeiro.monitoramento.dto.PagamentoResumoDTO;
import com.restaurante.financeiro.repository.PagamentoCallbackLogRepository;
import com.restaurante.financeiro.repository.PagamentoGatewayRepository;
import com.restaurante.model.entity.Pagamento;
import com.restaurante.model.entity.PagamentoCallbackLog;
import com.restaurante.model.entity.Pedido;
import com.restaurante.model.enums.CallbackProcessingStatus;
import com.restaurante.model.enums.StatusFinanceiroPedido;
import com.restaurante.security.tenant.TenantGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Service
@RequiredArgsConstructor
public class PagamentoMonitoramentoService {

    private final PagamentoGatewayRepository pagamentoRepository;
    private final PagamentoCallbackLogRepository callbackLogRepository;
    private final TenantGuard tenantGuard;

    @Value("${consuma.pagamentos.monitoramento.pendente-timeout-minutos:30}")
    private int pendenteTimeoutMinutos;

    @Transactional(readOnly = true)
    public Page<PagamentoResumoDTO> listarPagamentosDoTenant(Long tenantId, PagamentoMonitoramentoFiltro filtro, Pageable pageable) {
        tenantGuard.assertTenantActive(tenantId);
        tenantGuard.assertCurrentUserBelongsToTenant(tenantId);

        String pedidoNumero = filtro != null ? filtro.getPedidoNumero() : null;
        StatusPagamentoGateway status = filtro != null ? filtro.getStatusPagamento() : null;
        StatusFinanceiroPedido statusFin = filtro != null ? filtro.getStatusFinanceiroPedido() : null;
        var manualMethod = filtro != null ? filtro.getMetodoManual() : null;
        boolean appyPayOnly = filtro != null && Boolean.TRUE.equals(filtro.getAppyPayOnly());
        String externalRef = filtro != null ? filtro.getExternalReference() : null;
        LocalDateTime de = filtro != null ? filtro.getDe() : null;
        LocalDateTime ate = filtro != null ? filtro.getAte() : null;

        Page<Pagamento> page = pagamentoRepository.searchTenantPagamentos(
                tenantId, status, statusFin, manualMethod, appyPayOnly, externalRef, de, ate, pedidoNumero, pageable
        );

        int timeout = resolveTimeoutMinutos(filtro);
        Page<PagamentoResumoDTO> mapped = page.map(p -> toResumo(p, false, timeout));
        return applyPostFilters(mapped, filtro, pageable);
    }

    @Transactional(readOnly = true)
    public PagamentoResumoDTO buscarPagamentoDoTenant(Long tenantId, Long pagamentoId) {
        tenantGuard.assertTenantActive(tenantId);
        tenantGuard.assertCurrentUserBelongsToTenant(tenantId);

        Pagamento p = pagamentoRepository.findByIdAndTenantId(pagamentoId, tenantId)
                .orElseThrow(() -> new com.restaurante.exception.BusinessException("Pagamento não encontrado."));
        return toResumo(p, false, pendenteTimeoutMinutos);
    }

    @Transactional(readOnly = true)
    public Page<PagamentoResumoDTO> listarPagamentosPlatform(PagamentoMonitoramentoFiltro filtro, Pageable pageable) {
        tenantGuard.assertPlatformAdmin();

        Long tenantId = filtro != null ? filtro.getTenantId() : null;
        StatusPagamentoGateway status = filtro != null ? filtro.getStatusPagamento() : null;
        StatusFinanceiroPedido statusFin = filtro != null ? filtro.getStatusFinanceiroPedido() : null;
        var manualMethod = filtro != null ? filtro.getMetodoManual() : null;
        boolean appyPayOnly = filtro != null && Boolean.TRUE.equals(filtro.getAppyPayOnly());
        String externalRef = filtro != null ? filtro.getExternalReference() : null;
        String pedidoNumero = filtro != null ? filtro.getPedidoNumero() : null;
        LocalDateTime de = filtro != null ? filtro.getDe() : null;
        LocalDateTime ate = filtro != null ? filtro.getAte() : null;

        Page<Pagamento> page = (tenantId != null)
                ? pagamentoRepository.searchTenantPagamentos(tenantId, status, statusFin, manualMethod, appyPayOnly, externalRef, de, ate, pedidoNumero, pageable)
                : pagamentoRepository.searchPlatformPagamentos(status, statusFin, manualMethod, appyPayOnly, externalRef, de, ate, pedidoNumero, pageable);

        int timeout = resolveTimeoutMinutos(filtro);
        Page<PagamentoResumoDTO> mapped = page.map(p -> toResumo(p, true, timeout));
        return applyPostFilters(mapped, filtro, pageable);
    }

    @Transactional(readOnly = true)
    public Page<CallbackLogResumoDTO> listarCallbacksDoTenant(Long tenantId, CallbackProcessingStatus status, Pageable pageable) {
        tenantGuard.assertTenantActive(tenantId);
        tenantGuard.assertCurrentUserBelongsToTenant(tenantId);

        Page<PagamentoCallbackLog> page = (status == null)
                ? callbackLogRepository.findByTenantId(tenantId, pageable)
                : callbackLogRepository.findByTenantIdAndProcessingStatus(tenantId, status, pageable);

        return page.map(l -> toCallbackResumo(l, false));
    }

    @Transactional(readOnly = true)
    public Page<CallbackLogResumoDTO> listarCallbacksPlatform(Long tenantId, CallbackProcessingStatus status, Pageable pageable) {
        tenantGuard.assertPlatformAdmin();

        Page<PagamentoCallbackLog> page;
        if (tenantId != null) {
            page = (status == null)
                    ? callbackLogRepository.findByTenantId(tenantId, pageable)
                    : callbackLogRepository.findByTenantIdAndProcessingStatus(tenantId, status, pageable);
        } else if (status != null) {
            page = callbackLogRepository.findByProcessingStatus(status, pageable);
        } else {
            page = callbackLogRepository.findAll(pageable);
        }
        return page.map(l -> toCallbackResumo(l, true));
    }

    @Transactional(readOnly = true)
    public Page<CallbackLogResumoDTO> listarCallbacksSemTenant(Pageable pageable) {
        tenantGuard.assertPlatformAdmin();
        return callbackLogRepository.findByTenantIdIsNull(pageable).map(l -> toCallbackResumo(l, true));
    }

    @Transactional(readOnly = true)
    public CallbackLogDetalheDTO buscarCallbackLogDetalhePlatform(Long logId) {
        tenantGuard.assertPlatformAdmin();
        PagamentoCallbackLog l = callbackLogRepository.findById(logId)
                .orElseThrow(() -> new com.restaurante.exception.BusinessException("Callback log não encontrado."));
        return toCallbackDetalhe(l);
    }

    @Transactional(readOnly = true)
    public FinanceiroTenantResumoDTO resumoTenant(Long tenantId) {
        tenantGuard.assertTenantActive(tenantId);
        tenantGuard.assertCurrentUserBelongsToTenant(tenantId);

        LocalDateTime start = startOfToday();
        LocalDateTime end = endOfToday();

        FinanceiroTenantResumoDTO dto = new FinanceiroTenantResumoDTO();
        dto.setTotalPagamentosHoje(pagamentoRepository.countByTenantIdAndCreatedAtBetween(tenantId, start, end));
        dto.setTotalConfirmadoHoje(pagamentoRepository.countByTenantIdAndStatusAndCreatedAtBetween(tenantId, StatusPagamentoGateway.CONFIRMADO, start, end));
        dto.setTotalPendente(pagamentoRepository.countByTenantIdAndStatus(tenantId, StatusPagamentoGateway.PENDENTE));
        dto.setTotalPendenteValor(pagamentoRepository.sumPendentesByTenant(tenantId));

        LocalDateTime threshold = LocalDateTime.now().minusMinutes(pendenteTimeoutMinutos);
        dto.setPagamentosPendentesAntigos(pagamentoRepository.countPendentesAntigosByTenant(tenantId, threshold));

        dto.setCallbacksInvalidosHoje(callbackLogRepository.countByTenantIdAndProcessingStatusAndReceivedAtBetween(
                tenantId, CallbackProcessingStatus.INVALID_SIGNATURE, start, end));
        dto.setCallbacksPaymentNotFoundHoje(callbackLogRepository.countByTenantIdAndProcessingStatusAndReceivedAtBetween(
                tenantId, CallbackProcessingStatus.PAYMENT_NOT_FOUND, start, end));

        // divergência de valor: heurística (processing_error contém "Valor divergente")
        // Sem persistir flag divergente nesta fase.
        dto.setDivergenciasValorHoje(0);

        callbackLogRepository.findFirstByTenantIdOrderByReceivedAtDesc(tenantId)
                .ifPresent(last -> dto.setUltimoCallbackRecebidoEm(last.getReceivedAt()));

        return dto;
    }

    @Transactional(readOnly = true)
    public FinanceiroPlatformResumoDTO resumoPlatform() {
        tenantGuard.assertPlatformAdmin();

        LocalDateTime start = startOfToday();
        LocalDateTime end = endOfToday();
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(pendenteTimeoutMinutos);

        FinanceiroPlatformResumoDTO dto = new FinanceiroPlatformResumoDTO();
        dto.setTotalTenantsComPagamentos(pagamentoRepository.countDistinctTenants());
        dto.setTotalPagamentosHoje(pagamentoRepository.countHoje(start, end));
        dto.setTotalConfirmadoHoje(pagamentoRepository.countConfirmadoHoje(start, end));
        dto.setTotalPendente(pagamentoRepository.countPendentes());
        dto.setTotalPendentesAntigos(pagamentoRepository.countPendentesAntigos(threshold));
        dto.setTotalCallbacksInvalidos(callbackLogRepository.countByProcessingStatusAndReceivedAtBetween(
                CallbackProcessingStatus.INVALID_SIGNATURE, start, end));
        dto.setTotalPaymentNotFound(callbackLogRepository.countByProcessingStatusAndReceivedAtBetween(
                CallbackProcessingStatus.PAYMENT_NOT_FOUND, start, end));
        dto.setTotalDivergencias(0);
        dto.setValorConfirmadoHoje(pagamentoRepository.sumConfirmadoHoje(start, end));
        return dto;
    }

    private PagamentoResumoDTO toResumo(Pagamento p, boolean includeTenant, int timeoutMinutos) {
        PagamentoResumoDTO dto = new PagamentoResumoDTO();
        dto.setPagamentoId(p.getId());
        if (includeTenant && p.getTenant() != null) {
            dto.setTenantId(p.getTenant().getId());
            dto.setTenantNome(p.getTenant().getNome());
        }

        Pedido pedido = p.getPedido();
        if (pedido != null) {
            dto.setPedidoId(pedido.getId());
            dto.setPedidoNumero(pedido.getNumero());
            dto.setStatusFinanceiroPedido(pedido.getStatusFinanceiro());
        }

        dto.setExternalReference(p.getExternalReference());
        dto.setGatewayChargeId(p.getGatewayChargeId());
        dto.setMetodoPagamento(p.getMetodo());
        dto.setStatusPagamento(p.getStatus());
        dto.setValor(p.getAmount());
        dto.setMoeda("AOA");
        dto.setCriadoEm(p.getCreatedAt());
        dto.setAtualizadoEm(p.getUpdatedAt());
        dto.setConfirmadoEm(p.getConfirmedAt());

        Long idadeMin = computeAgeMinutes(p.getCreatedAt());
        dto.setIdadeMinutos(idadeMin);

        boolean pendenteAntigo = p.getStatus() == StatusPagamentoGateway.PENDENTE
                && idadeMin != null
                && idadeMin >= timeoutMinutos;
        dto.setPendenteHaMuitoTempo(pendenteAntigo);

        PagamentoCallbackLog lastLog = callbackLogRepository.findFirstByPagamentoIdOrderByReceivedAtDesc(p.getId()).orElse(null);

        dto.setPossuiCallback(lastLog != null);
        dto.setUltimaCallbackStatus(lastLog != null ? lastLog.getStatusRecebido() : null);
        dto.setDivergente(isDivergente(p, lastLog, pendenteAntigo));
        return dto;
    }

    private boolean isDivergente(Pagamento p, PagamentoCallbackLog lastLog, boolean pendenteAntigo) {
        if (pendenteAntigo) return true;
        if (lastLog == null) return false;
        if (lastLog.getProcessingStatus() == CallbackProcessingStatus.INVALID_SIGNATURE) return true;
        if (lastLog.getProcessingStatus() == CallbackProcessingStatus.PAYMENT_NOT_FOUND) return true;
        if (lastLog.getProcessingStatus() == CallbackProcessingStatus.FAILED) return true;
        if (lastLog.getProcessingError() != null && lastLog.getProcessingError().contains("Valor divergente")) return true;
        // Confirmado mas pedido não está PAGO é divergência operacional
        if (p.getStatus() == StatusPagamentoGateway.CONFIRMADO && p.getPedido() != null
                && p.getPedido().getStatusFinanceiro() != StatusFinanceiroPedido.PAGO) {
            return true;
        }
        return false;
    }

    private CallbackLogResumoDTO toCallbackResumo(PagamentoCallbackLog l, boolean includeTenant) {
        CallbackLogResumoDTO dto = new CallbackLogResumoDTO();
        dto.setId(l.getId());
        if (includeTenant && l.getTenant() != null) dto.setTenantId(l.getTenant().getId());
        dto.setPagamentoId(l.getPagamento() != null ? l.getPagamento().getId() : null);
        dto.setExternalReference(l.getExternalReference());
        dto.setProvider(l.getProvider());
        dto.setSignatureValid(l.getSignatureValid());
        dto.setProcessingStatus(l.getProcessingStatus());
        dto.setProcessingError(l.getProcessingError());
        dto.setReceivedAt(l.getReceivedAt());
        dto.setProcessedAt(l.getProcessedAt());
        dto.setStatusRecebido(l.getStatusRecebido());
        dto.setGatewayChargeId(l.getGatewayChargeId());
        return dto;
    }

    private CallbackLogDetalheDTO toCallbackDetalhe(PagamentoCallbackLog l) {
        CallbackLogDetalheDTO dto = new CallbackLogDetalheDTO();
        dto.setId(l.getId());
        dto.setTenantId(l.getTenant() != null ? l.getTenant().getId() : null);
        dto.setPagamentoId(l.getPagamento() != null ? l.getPagamento().getId() : null);
        dto.setExternalReference(l.getExternalReference());
        dto.setProvider(l.getProvider());
        dto.setSignatureValid(l.getSignatureValid());
        dto.setProcessingStatus(l.getProcessingStatus());
        dto.setProcessingError(l.getProcessingError());
        dto.setReceivedAt(l.getReceivedAt());
        dto.setProcessedAt(l.getProcessedAt());
        dto.setStatusRecebido(l.getStatusRecebido());
        dto.setGatewayChargeId(l.getGatewayChargeId());
        dto.setHeadersJson(l.getHeadersJson());
        dto.setPayloadJson(l.getPayloadJson());
        dto.setRawBody(l.getRawBody());
        return dto;
    }

    private static LocalDateTime startOfToday() {
        return LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
    }

    private static LocalDateTime endOfToday() {
        return LocalDateTime.of(LocalDate.now(), LocalTime.MAX);
    }

    private static Long computeAgeMinutes(LocalDateTime createdAt) {
        if (createdAt == null) return null;
        return Duration.between(createdAt, LocalDateTime.now()).toMinutes();
    }

    private int resolveTimeoutMinutos(PagamentoMonitoramentoFiltro filtro) {
        if (filtro == null || filtro.getPendenteHaMaisDeMinutos() == null) {
            return pendenteTimeoutMinutos;
        }
        return Math.max(1, filtro.getPendenteHaMaisDeMinutos());
    }

    private static Page<PagamentoResumoDTO> applyPostFilters(Page<PagamentoResumoDTO> page, PagamentoMonitoramentoFiltro filtro, Pageable pageable) {
        if (filtro == null) return page;
        boolean onlyDiv = Boolean.TRUE.equals(filtro.getSomenteDivergentes());
        if (!onlyDiv) return page;
        var filtered = page.getContent().stream()
                .filter(p -> Boolean.TRUE.equals(p.getDivergente()))
                .toList();
        return new PageImpl<>(filtered, pageable, page.getTotalElements());
    }
}

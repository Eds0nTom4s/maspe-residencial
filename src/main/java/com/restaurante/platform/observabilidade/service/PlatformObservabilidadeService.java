package com.restaurante.platform.observabilidade.service;

import com.restaurante.config.FinancePendingPaymentsProperties;
import com.restaurante.config.ObservabilidadeProperties;
import com.restaurante.financeiro.enums.PagamentoPollingStatus;
import com.restaurante.financeiro.enums.StatusPagamentoGateway;
import com.restaurante.financeiro.repository.PagamentoGatewayRepository;
import com.restaurante.model.entity.DispositivoOperacional;
import com.restaurante.model.entity.OperationalEventLog;
import com.restaurante.model.entity.Pagamento;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.TurnoOperacional;
import com.restaurante.model.enums.DeviceCapability;
import com.restaurante.model.enums.DispositivoStatus;
import com.restaurante.model.enums.DispositivoTipo;
import com.restaurante.model.enums.OperationalActorType;
import com.restaurante.model.enums.OperationalEntityType;
import com.restaurante.model.enums.OperationalEventType;
import com.restaurante.model.enums.TenantEstado;
import com.restaurante.model.enums.TurnoOperacionalStatus;
import com.restaurante.model.enums.StatusPedido;
import com.restaurante.model.enums.StatusSubPedido;
import com.restaurante.platform.observabilidade.dto.*;
import com.restaurante.repository.DispositivoOperacionalRepository;
import com.restaurante.repository.InstituicaoRepository;
import com.restaurante.repository.OperationalEventLogRepository;
import com.restaurante.repository.PedidoRepository;
import com.restaurante.repository.SubPedidoRepository;
import com.restaurante.repository.TenantRepository;
import com.restaurante.repository.TurnoOperacionalRepository;
import com.restaurante.repository.UnidadeAtendimentoRepository;
import com.restaurante.repository.UnidadeProducaoRepository;
import com.restaurante.service.device.DeviceCapabilities;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PlatformObservabilidadeService {

    private final ObservabilidadeProperties obsProps;
    private final FinancePendingPaymentsProperties financeProps;

    private final TenantRepository tenantRepository;
    private final InstituicaoRepository instituicaoRepository;
    private final UnidadeAtendimentoRepository unidadeAtendimentoRepository;
    private final TurnoOperacionalRepository turnoOperacionalRepository;
    private final DispositivoOperacionalRepository dispositivoOperacionalRepository;
    private final PagamentoGatewayRepository pagamentoGatewayRepository;
    private final PedidoRepository pedidoRepository;
    private final SubPedidoRepository subPedidoRepository;
    private final UnidadeProducaoRepository unidadeProducaoRepository;
    private final OperationalEventLogRepository operationalEventLogRepository;

    public PlatformSaudeOperacionalResponse saudeGlobal() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime offlineCutoff = now.minusMinutes(obsProps.getDeviceOfflineThresholdMinutes());
        LocalDateTime pagamentoCriticoCutoff = now.minusMinutes(financeProps.getCriticalAfterMinutes());

        PlatformSaudeOperacionalResponse r = new PlatformSaudeOperacionalResponse();
        r.setTotalTenants(tenantRepository.count());
        r.setTenantsAtivos(tenantRepository.countByEstado(TenantEstado.ATIVO));
        r.setTurnosAbertos(turnoOperacionalRepository.countByStatus(TurnoOperacionalStatus.ABERTO));
        r.setTurnosEmFecho(turnoOperacionalRepository.countByStatus(TurnoOperacionalStatus.EM_FECHO));
        r.setDevicesAtivos(dispositivoOperacionalRepository.countByStatus(DispositivoStatus.ATIVO));
        r.setDevicesOffline(dispositivoOperacionalRepository.countOfflineGlobal(offlineCutoff));
        r.setPagamentosPendentes(pagamentoGatewayRepository.countPendentes());
        r.setPagamentosMaxAttempts(pagamentoGatewayRepository.countMaxAttemptsReached());

        // "crítico" = pendente muito antigo OU max attempts
        long criticos = pagamentoGatewayRepository.countCriticosByTenant(pagamentoCriticoCutoff).stream()
                .mapToLong(o -> ((Number) o[1]).longValue())
                .sum();
        r.setPagamentosCriticos(criticos);

        r.setPedidosCriadosHoje(pedidoRepository.countPedidosHoje());
        r.setPedidosPendentes(pedidoRepository.countPedidosHojePorStatuses(List.of(StatusPedido.CRIADO, StatusPedido.EM_ANDAMENTO)));
        r.setSubPedidosEmPreparacao(subPedidoRepository.countByTenantForStatuses(List.of(StatusSubPedido.EM_PREPARACAO)).stream()
                .mapToLong(o -> ((Number) o[1]).longValue()).sum());
        r.setSubPedidosProntos(subPedidoRepository.countByTenantForStatuses(List.of(StatusSubPedido.PRONTO)).stream()
                .mapToLong(o -> ((Number) o[1]).longValue()).sum());

        List<PlatformAlertaOperacionalResponse> alerts = alertasAtivos(null, null, PageRequest.of(0, Math.min(500, obsProps.getMaxPageSize()))).getContent();
        r.setAlertasCriticos(alerts.stream().filter(a -> a.getLevel() == PlatformAlertLevel.CRITICAL).count());
        r.setAlertasWarnings(alerts.stream().filter(a -> a.getLevel() == PlatformAlertLevel.WARNING).count());
        r.setUltimaAtualizacao(now);
        return r;
    }

    public Page<TenantObservabilidadeResumoResponse> listarTenants(
            TenantEstado estado,
            Boolean comTurnoAberto,
            Boolean comAlertasCriticos,
            Boolean comDevicesOffline,
            Boolean comPagamentosCriticos,
            String search,
            Pageable pageable
    ) {
        Pageable safePageable = capPageable(pageable);

        boolean requiresPostFilter = Boolean.TRUE.equals(comTurnoAberto)
                || Boolean.TRUE.equals(comAlertasCriticos)
                || Boolean.TRUE.equals(comDevicesOffline)
                || Boolean.TRUE.equals(comPagamentosCriticos);

        if (!requiresPostFilter) {
            Page<Tenant> page = tenantRepository.searchPlatform(estado, search, safePageable);
            List<TenantObservabilidadeResumoResponse> content = mapTenantsToResumo(page.getContent());
            return new PageImpl<>(content, safePageable, page.getTotalElements());
        }

        // MVP: busca limitada e filtra em memória (piloto controlado).
        Page<Tenant> base = tenantRepository.searchPlatform(estado, search, PageRequest.of(0, 2000));
        List<TenantObservabilidadeResumoResponse> all = mapTenantsToResumo(base.getContent());

        List<TenantObservabilidadeResumoResponse> filtered = all.stream()
                .filter(t -> comTurnoAberto == null || !comTurnoAberto || t.getTurnosAbertos() > 0)
                .filter(t -> comDevicesOffline == null || !comDevicesOffline || t.getDevicesOffline() > 0)
                .filter(t -> comPagamentosCriticos == null || !comPagamentosCriticos || t.getPagamentosCriticos() > 0)
                .filter(t -> comAlertasCriticos == null || !comAlertasCriticos || t.getAlertLevel() == PlatformAlertLevel.CRITICAL)
                .toList();

        int start = (int) safePageable.getOffset();
        int end = Math.min(start + safePageable.getPageSize(), filtered.size());
        List<TenantObservabilidadeResumoResponse> slice = start >= filtered.size() ? List.of() : filtered.subList(start, end);
        return new PageImpl<>(slice, safePageable, filtered.size());
    }

    public TenantObservabilidadeDetalheResponse detalheTenant(Long tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new com.restaurante.exception.ResourceNotFoundException("Recurso não encontrado."));

        TenantObservabilidadeResumoResponse resumo = mapTenantsToResumo(List.of(tenant)).get(0);

        TenantObservabilidadeDetalheResponse r = new TenantObservabilidadeDetalheResponse();
        r.setResumo(resumo);

        // Turnos abertos recentes (top 10)
        Page<TurnoOperacional> turnos = turnoOperacionalRepository.searchPlatformByTenant(
                tenantId, null, null, null, null, null, PageRequest.of(0, 10));
        r.setTurnosAbertos(turnos.getContent().stream()
                .filter(t -> t.getStatus() == TurnoOperacionalStatus.ABERTO || t.getStatus() == TurnoOperacionalStatus.EM_FECHO)
                .map(this::toTurnoObsMinimal)
                .toList());

        // Devices "problema" (offline ou falha auth recente)
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cutoff = now.minusMinutes(obsProps.getDeviceOfflineThresholdMinutes());
        Page<DispositivoOperacional> devices = dispositivoOperacionalRepository.searchByTenantAndFilters(
                tenantId, null, null, null, null, PageRequest.of(0, 50));
        r.setDevicesProblema(devices.getContent().stream()
                .map(d -> toDeviceObs(d, now, cutoff))
                .filter(d -> d.isOffline() || d.getLastAuthFailureAt() != null)
                .sorted(Comparator.comparing(DeviceObservabilidadeResponse::isOffline).reversed()
                        .thenComparing(DeviceObservabilidadeResponse::getOfflineMinutes).reversed())
                .limit(20)
                .toList());

        // Pagamentos críticos (top 20)
        LocalDateTime critCutoff = now.minusMinutes(financeProps.getCriticalAfterMinutes());
        Page<Pagamento> pendentes = pagamentoGatewayRepository.searchPendentesTenant(
                tenantId, null, null, null, null, null, null, null,
                now.minusHours(financeProps.getDefaultLookbackHours()), now,
                PageRequest.of(0, 200));
        List<PlatformPagamentoObservabilidadeResponse> criticos = pendentes.getContent().stream()
                .map(p -> toPagamentoObs(p, now, critCutoff))
                .filter(p -> p.getAlertLevel() == PlatformAlertLevel.CRITICAL)
                .sorted(Comparator.comparingLong(PlatformPagamentoObservabilidadeResponse::getIdadeMinutos).reversed())
                .limit(20)
                .toList();
        r.setPagamentosCriticos(criticos);

        r.setProducao(producaoTenant(tenantId, null, null, null, null));

        // Eventos recentes (lookback)
        LocalDateTime from = now.minusHours(obsProps.getEventosDefaultLookbackHours());
        Page<OperationalEventLog> eventos = operationalEventLogRepository.searchTenantEventsExtended(
                tenantId, null, null, null, from, now, PageRequest.of(0, 50));
        r.setEventosRecentes(eventos.getContent().stream().map(this::toEventObs).toList());

        r.setAlertas(alertasAtivos(null, tenantId, PageRequest.of(0, 50)).getContent());
        return r;
    }

    public Page<TurnoObservabilidadeResponse> turnosTenant(Long tenantId,
                                                          TurnoOperacionalStatus status,
                                                          Long instituicaoId,
                                                          Long unidadeAtendimentoId,
                                                          LocalDateTime de,
                                                          LocalDateTime ate,
                                                          Pageable pageable) {
        Pageable safePageable = capPageable(pageable);
        Page<TurnoOperacional> page = turnoOperacionalRepository.searchPlatformByTenant(
                tenantId, status, instituicaoId, unidadeAtendimentoId, de, ate, safePageable);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime critCutoff = now.minusMinutes(financeProps.getCriticalAfterMinutes());
        LocalDateTime offlineCutoff = now.minusMinutes(obsProps.getDeviceOfflineThresholdMinutes());

        List<TurnoObservabilidadeResponse> content = page.getContent().stream()
                .map(t -> toTurnoObs(t, now, critCutoff, offlineCutoff))
                .toList();
        return new PageImpl<>(content, safePageable, page.getTotalElements());
    }

    public Page<DeviceObservabilidadeResponse> devicesTenant(Long tenantId,
                                                            DispositivoStatus status,
                                                            DispositivoTipo tipo,
                                                            Boolean offline,
                                                            Long unidadeAtendimentoId,
                                                            Long unidadeProducaoId,
                                                            Pageable pageable) {
        Pageable safePageable = capPageable(pageable);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cutoff = now.minusMinutes(obsProps.getDeviceOfflineThresholdMinutes());

        Page<DispositivoOperacional> page = dispositivoOperacionalRepository.searchByTenantAndFilters(
                tenantId, status, tipo, unidadeAtendimentoId, unidadeProducaoId, safePageable);

        List<DeviceObservabilidadeResponse> mapped = page.getContent().stream()
                .map(d -> toDeviceObs(d, now, cutoff))
                .filter(d -> offline == null || d.isOffline() == offline)
                .toList();
        return new PageImpl<>(mapped, safePageable, page.getTotalElements());
    }

    public Page<PlatformPagamentoObservabilidadeResponse> pagamentosTenant(Long tenantId,
                                                                          StatusPagamentoGateway status,
                                                                          PagamentoPollingStatus pollingStatus,
                                                                          Long turnoId,
                                                                          Long unidadeAtendimentoId,
                                                                          Boolean criticalOnly,
                                                                          Integer olderThanMinutes,
                                                                          Pageable pageable) {
        Pageable safePageable = capPageable(pageable);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime critCutoff = now.minusMinutes(financeProps.getCriticalAfterMinutes());
        LocalDateTime from = now.minusHours(financeProps.getDefaultLookbackHours());
        LocalDateTime to = now;
        LocalDateTime olderThan = olderThanMinutes != null ? now.minusMinutes(Math.max(0, olderThanMinutes)) : null;

        // MVP: foco em pendentes (observabilidade de piloto).
        Page<Pagamento> page;
        if (status == null || status == StatusPagamentoGateway.PENDENTE) {
            page = pagamentoGatewayRepository.searchPendentesTenant(
                    tenantId,
                    turnoId,
                    null,
                    unidadeAtendimentoId,
                    null,
                    pollingStatus,
                    null,
                    olderThan,
                    from,
                    to,
                    safePageable
            );
        } else {
            page = pagamentoGatewayRepository.findByTenantIdAndStatus(tenantId, status, safePageable);
        }

        List<PlatformPagamentoObservabilidadeResponse> items = page.getContent().stream()
                .map(p -> toPagamentoObs(p, now, critCutoff))
                .filter(p -> criticalOnly == null || !criticalOnly || p.getAlertLevel() == PlatformAlertLevel.CRITICAL)
                .toList();

        return new PageImpl<>(items, safePageable, page.getTotalElements());
    }

    public TenantProducaoObservabilidadeResponse producaoTenant(Long tenantId,
                                                               Long unidadeProducaoId,
                                                               StatusSubPedido status,
                                                               LocalDateTime de,
                                                               LocalDateTime ate) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime atrasadoCutoff = now.minusMinutes(obsProps.getProducaoSubpedidoAtrasadoMinutes());

        List<com.restaurante.model.entity.UnidadeProducao> ups;
        if (unidadeProducaoId != null) {
            ups = unidadeProducaoRepository.findByIdAndTenantId(unidadeProducaoId, tenantId)
                    .map(List::of).orElse(List.of());
        } else {
            ups = unidadeProducaoRepository.findByTenantIdAndAtivoTrueOrderByOrdemAsc(tenantId);
        }

        List<UnidadeProducaoObservabilidadeResponse> per = new ArrayList<>();
        long total = 0;
        long emPrep = 0;
        long prontos = 0;
        long atrasados = 0;

        Collection<StatusSubPedido> filaStatuses = List.of(StatusSubPedido.CRIADO, StatusSubPedido.PENDENTE, StatusSubPedido.EM_PREPARACAO, StatusSubPedido.PRONTO);

        for (com.restaurante.model.entity.UnidadeProducao up : ups) {
            long fila = subPedidoRepository.countByTenantAndUnidadeProducaoAndStatuses(tenantId, up.getId(), filaStatuses);
            long emP = subPedidoRepository.countByTenantAndUnidadeProducaoAndStatuses(tenantId, up.getId(), List.of(StatusSubPedido.EM_PREPARACAO));
            long pronto = subPedidoRepository.countByTenantAndUnidadeProducaoAndStatuses(tenantId, up.getId(), List.of(StatusSubPedido.PRONTO));
            LocalDateTime oldestPrep = subPedidoRepository.minEmPreparacaoAtByTenantAndUnidadeProducao(tenantId, up.getId());
            long atras = subPedidoRepository.countAtrasadosEmPreparacaoByTenantAndUnidadeProducao(tenantId, up.getId(), atrasadoCutoff);

            UnidadeProducaoObservabilidadeResponse u = new UnidadeProducaoObservabilidadeResponse();
            u.setUnidadeProducaoId(up.getId());
            u.setNome(up.getNome());
            u.setTotalFila(fila);
            u.setEmPreparacao(emP);
            u.setProntos(pronto);
            u.setMaisAntigoEmPreparacao(oldestPrep);
            u.setAlertLevel(atras > 0 ? PlatformAlertLevel.CRITICAL : (fila > 20 ? PlatformAlertLevel.WARNING : PlatformAlertLevel.INFO));
            per.add(u);

            total += fila;
            emPrep += emP;
            prontos += pronto;
            atrasados += atras;
        }

        TenantProducaoObservabilidadeResponse r = new TenantProducaoObservabilidadeResponse();
        r.setTotalSubPedidos(total);
        r.setEmPreparacao(emPrep);
        r.setProntos(prontos);
        r.setAtrasados(atrasados);
        r.setPorUnidadeProducao(per);
        r.setAlertas(alertasAtivos(PlatformAlertLevel.WARNING, tenantId, PageRequest.of(0, 100)).getContent().stream()
                .filter(a -> a.getTipo() == PlatformAlertaTipo.TENANT_SUBPEDIDO_ATRASADO)
                .toList());
        return r;
    }

    public Page<OperationalEventObservabilidadeResponse> eventosTenant(Long tenantId,
                                                                      OperationalEventType eventType,
                                                                      OperationalEntityType entityType,
                                                                      OperationalActorType actorType,
                                                                      LocalDateTime de,
                                                                      LocalDateTime ate,
                                                                      Pageable pageable) {
        Pageable safePageable = capPageable(pageable);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime from = de != null ? de : now.minusHours(obsProps.getEventosDefaultLookbackHours());
        LocalDateTime to = ate != null ? ate : now;
        Page<OperationalEventLog> page = operationalEventLogRepository.searchTenantEventsExtended(tenantId, eventType, entityType, actorType, from, to, safePageable);
        List<OperationalEventObservabilidadeResponse> mapped = page.getContent().stream().map(this::toEventObs).toList();
        return new PageImpl<>(mapped, safePageable, page.getTotalElements());
    }

    public Page<PlatformAlertaOperacionalResponse> alertasAtivos(PlatformAlertLevel level, Long tenantId, Pageable pageable) {
        Pageable safePageable = capPageable(pageable);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime offlineCutoff = now.minusMinutes(obsProps.getDeviceOfflineThresholdMinutes());
        LocalDateTime pagamentoCriticoCutoff = now.minusMinutes(financeProps.getCriticalAfterMinutes());
        LocalDateTime turnoFechoLongoCutoff = now.minusMinutes(obsProps.getTurnoEmFechoLongoMinutes());

        List<Tenant> tenants;
        if (tenantId != null) {
            tenants = tenantRepository.findById(tenantId).map(List::of).orElse(List.of());
        } else {
            tenants = tenantRepository.findAll();
        }

        // Pré-computações por tenant (group by)
        Map<Long, Long> offlineByTenant = dispositivoOperacionalRepository.countOfflineByTenant(offlineCutoff).stream()
                .collect(Collectors.toMap(o -> ((Number) o[0]).longValue(), o -> ((Number) o[1]).longValue()));
        Map<Long, Long> critPayByTenant = pagamentoGatewayRepository.countCriticosByTenant(pagamentoCriticoCutoff).stream()
                .collect(Collectors.toMap(o -> ((Number) o[0]).longValue(), o -> ((Number) o[1]).longValue()));
        Map<Long, Long> pendPayByTenant = pagamentoGatewayRepository.countPendentesByTenant().stream()
                .collect(Collectors.toMap(o -> ((Number) o[0]).longValue(), o -> ((Number) o[1]).longValue()));
        Map<Long, Long> openTurnosByTenant = turnoOperacionalRepository.countByTenantForStatuses(List.of(TurnoOperacionalStatus.ABERTO, TurnoOperacionalStatus.EM_FECHO)).stream()
                .collect(Collectors.toMap(o -> ((Number) o[0]).longValue(), o -> ((Number) o[1]).longValue()));

        // Turnos em fecho longos (on-the-fly por tenant)
        // MVP: calcula via consulta simples por tenant quando necessário (piloto).
        List<PlatformAlertaOperacionalResponse> alerts = new ArrayList<>();
        for (Tenant t : tenants) {
            Long tid = t.getId();
            long offline = offlineByTenant.getOrDefault(tid, 0L);
            long critPay = critPayByTenant.getOrDefault(tid, 0L);
            long pendPay = pendPayByTenant.getOrDefault(tid, 0L);
            long openTurnos = openTurnosByTenant.getOrDefault(tid, 0L);

            if (offline > 0) {
                alerts.add(buildAlert("DEV-OFF-" + tid, t, PlatformAlertLevel.WARNING, PlatformAlertaTipo.TENANT_DEVICE_OFFLINE,
                        "Tenant tem devices offline (" + offline + ").", "TENANT", tid, now, PlatformActionRecommended.CHECK_DEVICES));
            }
            if (critPay > 0) {
                alerts.add(buildAlert("PAY-CRIT-" + tid, t, PlatformAlertLevel.CRITICAL, PlatformAlertaTipo.TENANT_PAGAMENTO_CRITICO,
                        "Tenant tem pagamentos críticos pendentes (" + critPay + "/" + pendPay + ").", "TENANT", tid, now, PlatformActionRecommended.CHECK_GATEWAY));
            }
            if (openTurnos == 0) {
                alerts.add(buildAlert("NO-TURNO-" + tid, t, PlatformAlertLevel.INFO, PlatformAlertaTipo.TENANT_SEM_TURNO_ABERTO,
                        "Tenant sem turno aberto no momento.", "TENANT", tid, now, PlatformActionRecommended.CHECK_TURNOS));
            }

            // Turno em fecho longo
            List<TurnoOperacional> emFecho = turnoOperacionalRepository.searchPlatformByTenant(tid, TurnoOperacionalStatus.EM_FECHO, null, null, null, null, PageRequest.of(0, 20)).getContent();
            boolean fechoLongo = emFecho.stream().anyMatch(tr -> tr.getAbertoEm() != null && tr.getAbertoEm().isBefore(turnoFechoLongoCutoff));
            if (fechoLongo) {
                alerts.add(buildAlert("TURNO-FECHO-" + tid, t, PlatformAlertLevel.WARNING, PlatformAlertaTipo.TENANT_TURNO_EM_FECHO_LONGO,
                        "Tenant tem turno em fecho há muito tempo.", "TENANT", tid, now, PlatformActionRecommended.CHECK_TURNOS));
            }
        }

        List<PlatformAlertaOperacionalResponse> filtered = alerts.stream()
                .filter(a -> level == null || a.getLevel() == level)
                .sorted(Comparator.comparing(PlatformAlertaOperacionalResponse::getLevel).reversed()
                        .thenComparing(PlatformAlertaOperacionalResponse::getCreatedAt).reversed())
                .toList();

        int start = (int) safePageable.getOffset();
        int end = Math.min(start + safePageable.getPageSize(), filtered.size());
        List<PlatformAlertaOperacionalResponse> slice = start >= filtered.size() ? List.of() : filtered.subList(start, end);
        return new PageImpl<>(slice, safePageable, filtered.size());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Mapping helpers
    // ─────────────────────────────────────────────────────────────────────────

    private List<TenantObservabilidadeResumoResponse> mapTenantsToResumo(List<Tenant> tenants) {
        if (tenants.isEmpty()) return List.of();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime offlineCutoff = now.minusMinutes(obsProps.getDeviceOfflineThresholdMinutes());
        LocalDateTime paymentCritCutoff = now.minusMinutes(financeProps.getCriticalAfterMinutes());
        LocalDateTime activityFrom = now.minusHours(obsProps.getEventosDefaultLookbackHours());

        List<Long> tenantIds = tenants.stream().map(Tenant::getId).toList();

        Map<Long, Long> instCounts = tenantIds.stream()
                .collect(Collectors.toMap(Function.identity(), instituicaoRepository::countByTenantId));
        Map<Long, Long> unitCounts = tenantIds.stream()
                .collect(Collectors.toMap(Function.identity(), unidadeAtendimentoRepository::countByTenantId));

        Map<Long, Long> openTurnos = turnoOperacionalRepository.countByTenantForStatuses(List.of(TurnoOperacionalStatus.ABERTO, TurnoOperacionalStatus.EM_FECHO)).stream()
                .collect(Collectors.toMap(o -> ((Number) ((Object[]) o)[0]).longValue(), o -> ((Number) ((Object[]) o)[1]).longValue(), Long::sum));

        Map<Long, Long> devicesAtivos = dispositivoOperacionalRepository.countAtivosByTenant().stream()
                .collect(Collectors.toMap(o -> ((Number) ((Object[]) o)[0]).longValue(), o -> ((Number) ((Object[]) o)[1]).longValue(), Long::sum));
        Map<Long, Long> devicesOffline = dispositivoOperacionalRepository.countOfflineByTenant(offlineCutoff).stream()
                .collect(Collectors.toMap(o -> ((Number) ((Object[]) o)[0]).longValue(), o -> ((Number) ((Object[]) o)[1]).longValue(), Long::sum));

        Map<Long, Long> payPendentes = pagamentoGatewayRepository.countPendentesByTenant().stream()
                .collect(Collectors.toMap(o -> ((Number) ((Object[]) o)[0]).longValue(), o -> ((Number) ((Object[]) o)[1]).longValue(), Long::sum));
        Map<Long, Long> payCriticos = pagamentoGatewayRepository.countCriticosByTenant(paymentCritCutoff).stream()
                .collect(Collectors.toMap(o -> ((Number) ((Object[]) o)[0]).longValue(), o -> ((Number) ((Object[]) o)[1]).longValue(), Long::sum));

        Map<Long, LocalDateTime> lastActivity = operationalEventLogRepository.maxActivityByTenantSince(activityFrom).stream()
                .collect(Collectors.toMap(o -> ((Number) ((Object[]) o)[0]).longValue(), o -> (LocalDateTime) ((Object[]) o)[1], (a, b) -> a));

        Map<Long, Long> subPedidosEmAberto = subPedidoRepository.countByTenantForStatuses(List.of(StatusSubPedido.CRIADO, StatusSubPedido.PENDENTE, StatusSubPedido.EM_PREPARACAO)).stream()
                .collect(Collectors.toMap(o -> ((Number) ((Object[]) o)[0]).longValue(), o -> ((Number) ((Object[]) o)[1]).longValue(), Long::sum));

        // Pedidos hoje por tenant: MVP (piloto) — consulta agregada não existe; usa events como proxy se necessário.
        Map<Long, Long> pedidosHoje = tenantIds.stream()
                .collect(Collectors.toMap(Function.identity(), tid -> 0L));

        return tenants.stream().map(t -> {
            TenantObservabilidadeResumoResponse r = new TenantObservabilidadeResumoResponse();
            r.setTenantId(t.getId());
            r.setTenantNome(t.getNome());
            r.setTenantCode(t.getTenantCode());
            r.setEstado(t.getEstado());
            r.setTotalInstituicoes(instCounts.getOrDefault(t.getId(), 0L));
            r.setTotalUnidades(unitCounts.getOrDefault(t.getId(), 0L));
            r.setTurnosAbertos(openTurnos.getOrDefault(t.getId(), 0L));
            r.setDevicesAtivos(devicesAtivos.getOrDefault(t.getId(), 0L));
            r.setDevicesOffline(devicesOffline.getOrDefault(t.getId(), 0L));
            r.setPagamentosPendentes(payPendentes.getOrDefault(t.getId(), 0L));
            r.setPagamentosCriticos(payCriticos.getOrDefault(t.getId(), 0L));
            r.setPedidosHoje(pedidosHoje.getOrDefault(t.getId(), 0L));
            r.setSubPedidosEmAberto(subPedidosEmAberto.getOrDefault(t.getId(), 0L));
            r.setUltimaAtividadeEm(lastActivity.get(t.getId()));

            classifyTenantHealth(r);
            return r;
        }).toList();
    }

    private void classifyTenantHealth(TenantObservabilidadeResumoResponse r) {
        if (r.getPagamentosCriticos() > 0) {
            r.setAlertLevel(PlatformAlertLevel.CRITICAL);
            r.setActionRecommended(PlatformActionRecommended.CHECK_GATEWAY);
            return;
        }
        if (r.getDevicesOffline() > 0) {
            r.setAlertLevel(PlatformAlertLevel.WARNING);
            r.setActionRecommended(PlatformActionRecommended.CHECK_DEVICES);
            return;
        }
        if (r.getTurnosAbertos() == 0) {
            r.setAlertLevel(PlatformAlertLevel.INFO);
            r.setActionRecommended(PlatformActionRecommended.CHECK_TURNOS);
            return;
        }
        r.setAlertLevel(PlatformAlertLevel.INFO);
        r.setActionRecommended(PlatformActionRecommended.MONITOR);
    }

    private TurnoObservabilidadeResponse toTurnoObsMinimal(TurnoOperacional t) {
        TurnoObservabilidadeResponse r = new TurnoObservabilidadeResponse();
        r.setTurnoId(t.getId());
        r.setStatus(t.getStatus());
        r.setTipo(t.getTipo());
        r.setInstituicaoId(t.getInstituicao() != null ? t.getInstituicao().getId() : null);
        r.setUnidadeAtendimentoId(t.getUnidadeAtendimento() != null ? t.getUnidadeAtendimento().getId() : null);
        r.setAbertoEm(t.getAbertoEm());
        r.setFechadoEm(t.getFechadoEm());
        return r;
    }

    private TurnoObservabilidadeResponse toTurnoObs(TurnoOperacional t, LocalDateTime now, LocalDateTime critCutoff, LocalDateTime offlineCutoff) {
        TurnoObservabilidadeResponse r = toTurnoObsMinimal(t);
        Long tenantId = t.getTenant() != null ? t.getTenant().getId() : null;
        Long turnoId = t.getId();

        if (tenantId != null && turnoId != null) {
            long pedidosNonTerminal = pedidoRepository.countNonTerminalByTenantIdAndTurnoOperacionalId(tenantId, turnoId);
            long subPedidosNonTerminal = subPedidoRepository.countNonTerminalByTenantIdAndPedidoTurnoOperacionalId(tenantId, turnoId);
            long pendPag = pagamentoGatewayRepository.countByTenantIdAndPedidoTurnoOperacionalIdAndStatus(tenantId, turnoId, StatusPagamentoGateway.PENDENTE);

            // "críticos" no turno: MVP (usa critério por idade via consulta)
            Page<Pagamento> pendentes = pagamentoGatewayRepository.searchPendentesTenant(tenantId, turnoId, null, null, null, null, null, null,
                    now.minusHours(financeProps.getDefaultLookbackHours()), now, PageRequest.of(0, 200));
            long crit = pendentes.getContent().stream().filter(p -> isPagamentoCritico(p, now, critCutoff)).count();

            long devicesOffline = dispositivoOperacionalRepository.countOfflineByTenantAndUnidadeAtendimento(tenantId, r.getUnidadeAtendimentoId(), offlineCutoff);

            r.setPedidosTotal(pedidosNonTerminal);
            r.setSubPedidosEmAberto(subPedidosNonTerminal);
            r.setPagamentosPendentes(pendPag);
            r.setPagamentosCriticos(crit);
            r.setDevicesOffline(devicesOffline);

            boolean podeFechar = pedidosNonTerminal == 0 && subPedidosNonTerminal == 0;
            r.setPodeFechar(podeFechar);

            if (crit > 0) r.setAlertLevel(PlatformAlertLevel.CRITICAL);
            else if (devicesOffline > 0) r.setAlertLevel(PlatformAlertLevel.WARNING);
            else r.setAlertLevel(PlatformAlertLevel.INFO);
        }
        return r;
    }

    private DeviceObservabilidadeResponse toDeviceObs(DispositivoOperacional d, LocalDateTime now, LocalDateTime cutoff) {
        DeviceObservabilidadeResponse r = new DeviceObservabilidadeResponse();
        r.setDeviceId(d.getId());
        r.setNome(d.getNome());
        r.setTipo(d.getTipo());
        r.setStatus(d.getStatus());
        r.setInstituicaoId(d.getInstituicao() != null ? d.getInstituicao().getId() : null);
        r.setUnidadeAtendimentoId(d.getUnidadeAtendimento() != null ? d.getUnidadeAtendimento().getId() : null);
        r.setUnidadeProducaoId(d.getUnidadeProducao() != null ? d.getUnidadeProducao().getId() : null);
        r.setUltimoHeartbeatEm(d.getUltimoHeartbeatEm());
        r.setLastAuthAt(d.getLastAuthAt());
        r.setLastAuthFailureAt(d.getLastAuthFailureAt());
        r.setTokenVersion(d.getTokenVersion());

        boolean offline = d.getStatus() == DispositivoStatus.ATIVO && (d.getUltimoHeartbeatEm() == null || d.getUltimoHeartbeatEm().isBefore(cutoff));
        r.setOffline(offline);
        long offlineMinutes = offline && d.getUltimoHeartbeatEm() != null ? Duration.between(d.getUltimoHeartbeatEm(), now).toMinutes() : (offline ? obsProps.getDeviceOfflineThresholdMinutes() : 0);
        r.setOfflineMinutes(Math.max(0, offlineMinutes));

        List<DeviceCapability> caps = DeviceCapabilities.forTipo(d.getTipo());
        r.setCapabilities(caps);

        if (offline) {
            r.setAlertLevel(PlatformAlertLevel.WARNING);
            r.setActionRecommended(PlatformActionRecommended.CHECK_DEVICES);
        } else if (d.getLastAuthFailureAt() != null && d.getLastAuthAt() != null && d.getLastAuthFailureAt().isAfter(d.getLastAuthAt())) {
            r.setAlertLevel(PlatformAlertLevel.WARNING);
            r.setActionRecommended(PlatformActionRecommended.SUPPORT_REQUIRED);
        } else {
            r.setAlertLevel(PlatformAlertLevel.INFO);
            r.setActionRecommended(PlatformActionRecommended.MONITOR);
        }
        return r;
    }

    private PlatformPagamentoObservabilidadeResponse toPagamentoObs(Pagamento p, LocalDateTime now, LocalDateTime critCutoff) {
        PlatformPagamentoObservabilidadeResponse r = new PlatformPagamentoObservabilidadeResponse();
        r.setPagamentoId(p.getId());
        r.setPedidoId(p.getPedido() != null ? p.getPedido().getId() : null);
        r.setNumeroPedido(p.getPedido() != null ? p.getPedido().getNumero() : null);
        r.setTurnoId(p.getPedido() != null && p.getPedido().getTurnoOperacional() != null ? p.getPedido().getTurnoOperacional().getId() : null);
        r.setUnidadeAtendimentoId(p.getPedido() != null && p.getPedido().getSessaoConsumo() != null && p.getPedido().getSessaoConsumo().getUnidadeAtendimento() != null ? p.getPedido().getSessaoConsumo().getUnidadeAtendimento().getId() : null);
        r.setValor(p.getAmount());
        r.setMoeda("AOA");
        r.setMetodoPagamento(p.getMetodo());
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

        long idade = p.getCreatedAt() != null ? Duration.between(p.getCreatedAt(), now).toMinutes() : 0;
        r.setIdadeMinutos(Math.max(0, idade));

        if (isPagamentoCritico(p, now, critCutoff)) {
            r.setAlertLevel(PlatformAlertLevel.CRITICAL);
            r.setActionRecommended(PlatformActionRecommended.CHECK_GATEWAY);
        } else if (p.getPollingLastErrorCode() != null) {
            r.setAlertLevel(PlatformAlertLevel.WARNING);
            r.setActionRecommended(PlatformActionRecommended.CHECK_GATEWAY);
        } else {
            r.setAlertLevel(PlatformAlertLevel.INFO);
            r.setActionRecommended(PlatformActionRecommended.MONITOR);
        }
        return r;
    }

    private boolean isPagamentoCritico(Pagamento p, LocalDateTime now, LocalDateTime critCutoff) {
        if (p.getStatus() != StatusPagamentoGateway.PENDENTE) return false;
        if (p.getPollingStatus() == PagamentoPollingStatus.MAX_ATTEMPTS_REACHED) return true;
        if (p.getCreatedAt() != null && p.getCreatedAt().isBefore(critCutoff)) return true;
        return false;
    }

    private OperationalEventObservabilidadeResponse toEventObs(OperationalEventLog e) {
        OperationalEventObservabilidadeResponse r = new OperationalEventObservabilidadeResponse();
        r.setEventId(e.getId());
        r.setTenantId(e.getTenant() != null ? e.getTenant().getId() : null);
        r.setEventType(e.getEventType());
        r.setEntityType(e.getEntityType());
        r.setEntityId(e.getEntityId());
        r.setActorType(e.getActorType());
        r.setActorUserId(e.getActorUser() != null ? e.getActorUser().getId() : null);
        r.setDeviceId(e.getDispositivo() != null ? e.getDispositivo().getId() : null);
        r.setPedidoId(e.getPedido() != null ? e.getPedido().getId() : null);
        r.setPagamentoId(e.getEntityType() == OperationalEntityType.PAGAMENTO ? e.getEntityId() : null);
        r.setTurnoId(e.getTurno() != null ? e.getTurno().getId() : null);
        r.setOrigem(e.getOrigem());
        r.setCreatedAt(e.getCreatedAt());
        r.setMetadataResumo(sanitizeMetadataResumo(e.getMetadataJson()));
        return r;
    }

    private String sanitizeMetadataResumo(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) return null;
        int len = metadataJson.length();
        // Nunca devolve payload completo; apenas um resumo de tamanho.
        return "{\"size\":" + len + "}";
    }

    private PlatformAlertaOperacionalResponse buildAlert(String id, Tenant t, PlatformAlertLevel lvl, PlatformAlertaTipo tipo,
                                                        String message, String entityType, Long entityId, LocalDateTime createdAt,
                                                        PlatformActionRecommended action) {
        PlatformAlertaOperacionalResponse a = new PlatformAlertaOperacionalResponse();
        a.setAlertId(id);
        a.setTenantId(t.getId());
        a.setTenantNome(t.getNome());
        a.setLevel(lvl);
        a.setTipo(tipo);
        a.setMessage(message);
        a.setEntityType(entityType);
        a.setEntityId(entityId);
        a.setCreatedAt(createdAt);
        a.setActionRecommended(action);
        return a;
    }

    private Pageable capPageable(Pageable pageable) {
        if (pageable == null) {
            return PageRequest.of(0, Math.min(50, obsProps.getMaxPageSize()));
        }
        int size = Math.min(pageable.getPageSize(), obsProps.getMaxPageSize());
        return PageRequest.of(pageable.getPageNumber(), size, pageable.getSort());
    }
}

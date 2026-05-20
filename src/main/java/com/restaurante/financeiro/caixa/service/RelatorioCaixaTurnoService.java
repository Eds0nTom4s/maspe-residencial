package com.restaurante.financeiro.caixa.service;

import com.restaurante.exception.ResourceNotFoundException;
import com.restaurante.financeiro.caixa.CaixaTurnoProperties;
import com.restaurante.financeiro.caixa.dto.EventoFinanceiroTurnoResponse;
import com.restaurante.financeiro.caixa.dto.OrdemPagamentoResumoCaixaResponse;
import com.restaurante.financeiro.caixa.dto.PagamentoResumoCaixaResponse;
import com.restaurante.financeiro.caixa.dto.RelatorioCaixaTurnoResponse;
import com.restaurante.financeiro.caixa.dto.ResumoCaixaTurnoMiniResponse;
import com.restaurante.financeiro.caixa.dto.TotalPorDeviceResponse;
import com.restaurante.financeiro.caixa.dto.TotalPorMetodoPagamentoResponse;
import com.restaurante.financeiro.caixa.dto.TotalPorOrigemPagamentoResponse;
import com.restaurante.financeiro.enums.StatusPagamentoGateway;
import com.restaurante.financeiro.enums.TipoPagamentoFinanceiro;
import com.restaurante.financeiro.repository.OrdemPagamentoRepository;
import com.restaurante.financeiro.repository.PagamentoGatewayRepository;
import com.restaurante.model.entity.OperationalEventLog;
import com.restaurante.model.entity.OrdemPagamento;
import com.restaurante.model.entity.Pagamento;
import com.restaurante.model.entity.TurnoOperacional;
import com.restaurante.model.enums.MetodoPagamentoManual;
import com.restaurante.model.enums.OperationalEventType;
import com.restaurante.model.enums.OrdemPagamentoStatus;
import com.restaurante.model.enums.OrdemPagamentoTipo;
import com.restaurante.repository.OperationalEventLogRepository;
import com.restaurante.repository.TurnoOperacionalRepository;
import com.restaurante.financeiro.service.PagamentoPendenteQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RelatorioCaixaTurnoService {

    private final CaixaTurnoProperties properties;
    private final TurnoOperacionalRepository turnoOperacionalRepository;
    private final PagamentoGatewayRepository pagamentoGatewayRepository;
    private final OrdemPagamentoRepository ordemPagamentoRepository;
    private final OperationalEventLogRepository operationalEventLogRepository;
    private final PagamentoPendenteQueryService pagamentoPendenteQueryService;

    @Transactional(readOnly = true)
    public RelatorioCaixaTurnoResponse gerarRelatorio(Long tenantId, Long turnoId) {
        TurnoOperacional turno = turnoOperacionalRepository.findByIdAndTenantId(turnoId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Recurso não encontrado."));

        RelatorioCaixaTurnoResponse resp = new RelatorioCaixaTurnoResponse();
        resp.setTurnoId(turno.getId());
        resp.setTenantId(turno.getTenant().getId());
        resp.setInstituicaoId(turno.getInstituicao().getId());
        resp.setUnidadeAtendimentoId(turno.getUnidadeAtendimento().getId());
        resp.setStatusTurno(turno.getStatus().name());
        resp.setAbertoEm(turno.getAbertoEm());
        resp.setFechadoEm(turno.getFechadoEm());
        resp.setGeradoEm(LocalDateTime.now());

        // Manual (ordens confirmadas por device)
        List<OrdemPagamento> ordens = ordemPagamentoRepository.findAllByTenantIdAndTurnoOperacionalId(tenantId, turnoId);
        List<OrdemPagamento> ordensConfirmadas = ordens.stream().filter(o -> o.getStatus() == OrdemPagamentoStatus.CONFIRMADA).toList();
        List<OrdemPagamento> ordensPendentes = properties.isIncluirPendentes()
                ? ordens.stream().filter(o -> o.getStatus() == OrdemPagamentoStatus.AGUARDANDO_CONFIRMACAO).toList()
                : List.of();

        BigDecimal manualCash = sumOrdensByMetodo(ordensConfirmadas, MetodoPagamentoManual.CASH);
        BigDecimal manualTpa = sumOrdensByMetodo(ordensConfirmadas, MetodoPagamentoManual.TPA);
        BigDecimal manualTotal = manualCash.add(manualTpa);
        resp.setTotalManualConfirmado(manualTotal);

        BigDecimal manualPedidos = sumOrdensByTipo(ordensConfirmadas, OrdemPagamentoTipo.PEDIDO);
        BigDecimal manualFundos = sumOrdensByTipo(ordensConfirmadas, OrdemPagamentoTipo.FUNDO_CONSUMO);

        // Gateway (AppyPay) por turno (pedido.turnoOperacional) + recargas por janela do turno
        List<Pagamento> pagamentosTurno = pagamentoGatewayRepository.findAllByTenantIdAndPedidoTurnoOperacionalId(tenantId, turnoId);
        BigDecimal gatewayPedidosConfirmados = pagamentosTurno.stream()
                .filter(p -> p.getExternalReference() != null)
                .filter(p -> p.getStatus() == StatusPagamentoGateway.CONFIRMADO)
                .map(Pagamento::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal gatewayPedidosPendentes = pagamentosTurno.stream()
                .filter(p -> p.getExternalReference() != null)
                .filter(p -> p.getStatus() == StatusPagamentoGateway.PENDENTE)
                .map(Pagamento::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal gatewayPedidosFalhados = pagamentosTurno.stream()
                .filter(p -> p.getExternalReference() != null)
                .filter(p -> p.getStatus() == StatusPagamentoGateway.FALHOU)
                .map(Pagamento::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        LocalDateTime from = turno.getAbertoEm();
        LocalDateTime to = turno.getFechadoEm() != null ? turno.getFechadoEm() : LocalDateTime.now();
        List<Pagamento> gatewayRecargasConfirmadas = pagamentoGatewayRepository.findGatewayFundPaymentsByTenantAndWindow(
                tenantId,
                turno.getUnidadeAtendimento().getId(),
                from,
                to,
                TipoPagamentoFinanceiro.PRE_PAGO,
                StatusPagamentoGateway.CONFIRMADO
        );
        BigDecimal gatewayFundosConfirmados = gatewayRecargasConfirmadas.stream()
                .map(Pagamento::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Totais por destino (pedido vs fundo)
        resp.setTotalPagamentoPedidos(gatewayPedidosConfirmados.add(manualPedidos));
        resp.setTotalCarregamentoFundoConsumo(gatewayFundosConfirmados.add(manualFundos));

        // Gateway confirmado (somente AppyPay)
        resp.setTotalGatewayConfirmado(gatewayPedidosConfirmados.add(gatewayFundosConfirmados));

        // Pendentes/falhados (gateway + ordens pendentes)
        BigDecimal ordensPendentesTotal = ordensPendentes.stream().map(OrdemPagamento::getValor).reduce(BigDecimal.ZERO, BigDecimal::add);
        resp.setTotalPendente(gatewayPedidosPendentes.add(ordensPendentesTotal));
        resp.setTotalFalhado(gatewayPedidosFalhados); // manual falho não existe (ou vira EXPIRADA/CANCELADA na ordem)

        // Divergências: eventos + soma de pagamentos afetados quando possível
        BigDecimal divergente = computeDivergenciasValor(tenantId, turnoId);
        resp.setTotalDivergente(divergente);

        // Totais confirmados gerais
        resp.setTotalGeralConfirmado(resp.getTotalManualConfirmado().add(resp.getTotalGatewayConfirmado()));
        resp.setQuantidadeOrdensManuaisConfirmadas(ordensConfirmadas.size());
        resp.setQuantidadePagamentosConfirmados((int) (countGatewayConfirmados(pagamentosTurno) + gatewayRecargasConfirmadas.size() + ordensConfirmadas.size()));
        resp.setQuantidadePagamentosPendentes((int) (countGatewayPendentes(pagamentosTurno) + ordensPendentes.size()));

        resp.setTotaisPorMetodo(buildTotaisPorMetodo(manualCash, manualTpa, gatewayPedidosConfirmados.add(gatewayFundosConfirmados),
                gatewayPedidosPendentes, gatewayPedidosFalhados, ordensPendentes));
        resp.setTotaisPorOrigem(buildTotaisPorOrigem(ordensConfirmadas, pagamentosTurno, gatewayRecargasConfirmadas));
        resp.setTotaisPorDevice(buildTotaisPorDevice(ordensConfirmadas));

        if (properties.isIncluirPendentes()) {
            resp.setPagamentosPendentes(mapPagamentosPendentes(pagamentosTurno));
            resp.setOrdensPendentes(mapOrdensPendentes(ordensPendentes));
        }

        // Alertas financeiros já existentes
        resp.setAlertasFinanceiros(pagamentoPendenteQueryService.alertasPorTurno(turnoId));

        // Eventos financeiros recentes (sanitizados)
        if (properties.isIncluirEventos()) {
            resp.setEventosRecentes(mapEventosRecentes(tenantId, turnoId));
        }

        return resp;
    }

    @Transactional(readOnly = true)
    public ResumoCaixaTurnoMiniResponse gerarResumoMini(Long tenantId, Long turnoId) {
        RelatorioCaixaTurnoResponse full = gerarRelatorio(tenantId, turnoId);
        ResumoCaixaTurnoMiniResponse mini = new ResumoCaixaTurnoMiniResponse();
        mini.setTotalGeralConfirmado(full.getTotalGeralConfirmado());
        mini.setTotalPendente(full.getTotalPendente());
        mini.setTotalCarregamentoFundo(full.getTotalCarregamentoFundoConsumo());
        mini.setTotalPagamentoPedidos(full.getTotalPagamentoPedidos());

        BigDecimal cash = BigDecimal.ZERO;
        BigDecimal tpa = BigDecimal.ZERO;
        BigDecimal appy = BigDecimal.ZERO;
        for (TotalPorMetodoPagamentoResponse t : full.getTotaisPorMetodo()) {
            if ("CASH".equalsIgnoreCase(t.getMetodoPagamento())) cash = t.getTotalConfirmado();
            if ("TPA".equalsIgnoreCase(t.getMetodoPagamento())) tpa = t.getTotalConfirmado();
            if ("APPYPAY".equalsIgnoreCase(t.getMetodoPagamento())) appy = t.getTotalConfirmado();
        }
        mini.setTotalCash(cash);
        mini.setTotalTpa(tpa);
        mini.setTotalAppyPay(appy);
        return mini;
    }

    private static BigDecimal sumOrdensByMetodo(List<OrdemPagamento> ordens, MetodoPagamentoManual metodo) {
        return ordens.stream()
                .filter(o -> o.getMetodoSolicitado() == metodo)
                .map(OrdemPagamento::getValor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal sumOrdensByTipo(List<OrdemPagamento> ordens, OrdemPagamentoTipo tipo) {
        return ordens.stream()
                .filter(o -> o.getTipo() == tipo)
                .map(OrdemPagamento::getValor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static long countGatewayConfirmados(List<Pagamento> pagamentos) {
        return pagamentos.stream().filter(p -> p.getExternalReference() != null).filter(p -> p.getStatus() == StatusPagamentoGateway.CONFIRMADO).count();
    }

    private static long countGatewayPendentes(List<Pagamento> pagamentos) {
        return pagamentos.stream().filter(p -> p.getExternalReference() != null).filter(p -> p.getStatus() == StatusPagamentoGateway.PENDENTE).count();
    }

    private List<TotalPorMetodoPagamentoResponse> buildTotaisPorMetodo(BigDecimal manualCash,
                                                                      BigDecimal manualTpa,
                                                                      BigDecimal gatewayConfirmado,
                                                                      BigDecimal gatewayPendente,
                                                                      BigDecimal gatewayFalhou,
                                                                      List<OrdemPagamento> ordensPendentes) {
        List<TotalPorMetodoPagamentoResponse> out = new ArrayList<>();

        TotalPorMetodoPagamentoResponse cash = new TotalPorMetodoPagamentoResponse();
        cash.setMetodoPagamento("CASH");
        cash.setQuantidade(0);
        cash.setTotalConfirmado(manualCash);
        out.add(cash);

        TotalPorMetodoPagamentoResponse tpa = new TotalPorMetodoPagamentoResponse();
        tpa.setMetodoPagamento("TPA");
        tpa.setQuantidade(0);
        tpa.setTotalConfirmado(manualTpa);
        out.add(tpa);

        TotalPorMetodoPagamentoResponse appy = new TotalPorMetodoPagamentoResponse();
        appy.setMetodoPagamento("APPYPAY");
        appy.setQuantidade(0);
        appy.setTotalConfirmado(gatewayConfirmado);
        appy.setTotalPendente(gatewayPendente);
        appy.setTotalFalhado(gatewayFalhou);
        out.add(appy);

        // Pendentes manuais por método (informativo)
        BigDecimal pendCash = ordensPendentes.stream().filter(o -> o.getMetodoSolicitado() == MetodoPagamentoManual.CASH).map(OrdemPagamento::getValor).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal pendTpa = ordensPendentes.stream().filter(o -> o.getMetodoSolicitado() == MetodoPagamentoManual.TPA).map(OrdemPagamento::getValor).reduce(BigDecimal.ZERO, BigDecimal::add);
        cash.setTotalPendente(pendCash);
        tpa.setTotalPendente(pendTpa);

        return out;
    }

    private List<TotalPorOrigemPagamentoResponse> buildTotaisPorOrigem(List<OrdemPagamento> ordensConfirmadas,
                                                                      List<Pagamento> pagamentosTurno,
                                                                      List<Pagamento> gatewayRecargasConfirmadas) {
        Map<String, TotalPorOrigemPagamentoResponse> agg = new LinkedHashMap<>();

        // Manual device
        for (OrdemPagamento o : ordensConfirmadas) {
            String key = "MANUAL_DEVICE";
            TotalPorOrigemPagamentoResponse r = agg.computeIfAbsent(key, k -> {
                TotalPorOrigemPagamentoResponse x = new TotalPorOrigemPagamentoResponse();
                x.setOrigem(k);
                return x;
            });
            r.setQuantidade(r.getQuantidade() + 1);
            r.setTotal(r.getTotal().add(o.getValor()));
        }

        // Gateway callback/polling (tratamos como GATEWAY)
        for (Pagamento p : pagamentosTurno) {
            if (p.getExternalReference() == null) continue;
            if (p.getStatus() != StatusPagamentoGateway.CONFIRMADO) continue;
            TotalPorOrigemPagamentoResponse r = agg.computeIfAbsent("GATEWAY", k -> {
                TotalPorOrigemPagamentoResponse x = new TotalPorOrigemPagamentoResponse();
                x.setOrigem(k);
                return x;
            });
            r.setQuantidade(r.getQuantidade() + 1);
            r.setTotal(r.getTotal().add(p.getAmount()));
        }
        for (Pagamento p : gatewayRecargasConfirmadas) {
            TotalPorOrigemPagamentoResponse r = agg.computeIfAbsent("GATEWAY", k -> {
                TotalPorOrigemPagamentoResponse x = new TotalPorOrigemPagamentoResponse();
                x.setOrigem(k);
                return x;
            });
            r.setQuantidade(r.getQuantidade() + 1);
            r.setTotal(r.getTotal().add(p.getAmount()));
        }

        return new ArrayList<>(agg.values());
    }

    private List<TotalPorDeviceResponse> buildTotaisPorDevice(List<OrdemPagamento> ordensConfirmadas) {
        Map<Long, TotalPorDeviceResponse> agg = new HashMap<>();

        for (OrdemPagamento o : ordensConfirmadas) {
            if (o.getConfirmadoPorDispositivo() == null) continue;
            Long deviceId = o.getConfirmadoPorDispositivo().getId();
            TotalPorDeviceResponse r = agg.computeIfAbsent(deviceId, id -> {
                TotalPorDeviceResponse x = new TotalPorDeviceResponse();
                x.setDeviceId(deviceId);
                x.setDeviceNome(o.getConfirmadoPorDispositivo().getNome());
                x.setTipoDevice(o.getConfirmadoPorDispositivo().getTipo().name());
                return x;
            });
            r.setQuantidadeConfirmacoes(r.getQuantidadeConfirmacoes() + 1);
            if (o.getMetodoSolicitado() == MetodoPagamentoManual.CASH) {
                r.setTotalCash(r.getTotalCash().add(o.getValor()));
            }
            if (o.getMetodoSolicitado() == MetodoPagamentoManual.TPA) {
                r.setTotalTpa(r.getTotalTpa().add(o.getValor()));
            }
            r.setTotalManual(r.getTotalManual().add(o.getValor()));
            if (r.getUltimoPagamentoEm() == null || (o.getConfirmadoEm() != null && o.getConfirmadoEm().isAfter(r.getUltimoPagamentoEm()))) {
                r.setUltimoPagamentoEm(o.getConfirmadoEm());
            }
        }

        return new ArrayList<>(agg.values());
    }

    private List<PagamentoResumoCaixaResponse> mapPagamentosPendentes(List<Pagamento> pagamentosTurno) {
        List<PagamentoResumoCaixaResponse> out = new ArrayList<>();
        for (Pagamento p : pagamentosTurno) {
            if (p.getExternalReference() == null) continue;
            if (p.getStatus() != StatusPagamentoGateway.PENDENTE) continue;
            PagamentoResumoCaixaResponse r = new PagamentoResumoCaixaResponse();
            r.setPagamentoId(p.getId());
            r.setPedidoId(p.getPedido() != null ? p.getPedido().getId() : null);
            r.setMetodoPagamento(p.getMetodo() != null ? p.getMetodo().name() : "APPYPAY");
            r.setOrigem("GATEWAY");
            r.setStatus(p.getStatus().name());
            r.setValor(p.getAmount());
            r.setMoeda("AOA");
            r.setConfirmadoEm(p.getConfirmedAt());
            r.setExternalReference(p.getExternalReference());
            out.add(r);
        }
        return out;
    }

    private List<OrdemPagamentoResumoCaixaResponse> mapOrdensPendentes(List<OrdemPagamento> ordensPendentes) {
        List<OrdemPagamentoResumoCaixaResponse> out = new ArrayList<>();
        for (OrdemPagamento o : ordensPendentes) {
            OrdemPagamentoResumoCaixaResponse r = new OrdemPagamentoResumoCaixaResponse();
            r.setOrdemPagamentoId(o.getId());
            r.setTipo(o.getTipo().name());
            r.setMetodoSolicitado(o.getMetodoSolicitado().name());
            r.setStatus(o.getStatus().name());
            r.setValor(o.getValor());
            r.setMoeda(o.getMoeda());
            r.setPedidoId(o.getPedido() != null ? o.getPedido().getId() : null);
            r.setFundoConsumoId(o.getFundoConsumo() != null ? o.getFundoConsumo().getId() : null);
            r.setSessaoConsumoId(o.getSessaoConsumo() != null ? o.getSessaoConsumo().getId() : null);
            r.setCriadoEm(o.getCreatedAt());
            r.setConfirmadoEm(o.getConfirmadoEm());
            r.setConfirmadoPorDeviceId(o.getConfirmadoPorDispositivo() != null ? o.getConfirmadoPorDispositivo().getId() : null);
            out.add(r);
        }
        return out;
    }

    private BigDecimal computeDivergenciasValor(Long tenantId, Long turnoId) {
        List<OperationalEventLog> events = operationalEventLogRepository.findTopByTenantAndTurnoAndEventTypes(
                tenantId,
                turnoId,
                Set.of(OperationalEventType.PAGAMENTO_POLLING_DIVERGENCIA_VALOR),
                PageRequest.of(0, 200)
        );
        if (events.isEmpty()) return BigDecimal.ZERO;
        List<Long> pagamentoIds = events.stream().map(OperationalEventLog::getEntityId).toList();
        BigDecimal sum = BigDecimal.ZERO;
        for (Long id : pagamentoIds) {
            Pagamento p = pagamentoGatewayRepository.findByIdAndTenantId(id, tenantId).orElse(null);
            if (p != null) sum = sum.add(p.getAmount());
        }
        return sum;
    }

    private List<EventoFinanceiroTurnoResponse> mapEventosRecentes(Long tenantId, Long turnoId) {
        Set<OperationalEventType> types = Set.of(
                OperationalEventType.ORDEM_PAGAMENTO_CONFIRMADA_MANUAL,
                OperationalEventType.PAGAMENTO_CASH_CONFIRMADO_DEVICE,
                OperationalEventType.PAGAMENTO_TPA_CONFIRMADO_DEVICE,
                OperationalEventType.FUNDO_CONSUMO_CREDITADO_MANUAL,
                OperationalEventType.PAGAMENTO_CONFIRMADO_POR_POLLING,
                OperationalEventType.PAGAMENTO_INICIADO_DEVICE,
                OperationalEventType.PAGAMENTO_POLLING_MAX_TENTATIVAS,
                OperationalEventType.TURNO_FECHADO_COM_ALERTA_FINANCEIRO,
                OperationalEventType.PAGAMENTO_POLLING_DIVERGENCIA_VALOR
        );
        List<OperationalEventLog> logs = operationalEventLogRepository.findTopByTenantAndTurnoAndEventTypes(
                tenantId,
                turnoId,
                types,
                PageRequest.of(0, properties.getEventosRecentesLimit())
        );
        List<EventoFinanceiroTurnoResponse> out = new ArrayList<>();
        for (OperationalEventLog e : logs) {
            EventoFinanceiroTurnoResponse r = new EventoFinanceiroTurnoResponse();
            r.setEventId(e.getId());
            r.setEventType(e.getEventType().name());
            r.setEntityType(e.getEntityType().name());
            r.setEntityId(e.getEntityId());
            r.setActorType(e.getActorType().name());
            r.setActorUserId(e.getActorUser() != null ? e.getActorUser().getId() : null);
            r.setDeviceId(e.getDispositivo() != null ? e.getDispositivo().getId() : null);
            r.setOrigem(e.getOrigem().name());
            r.setCreatedAt(e.getCreatedAt());
            r.setResumo(e.getMotivo());
            out.add(r);
        }
        return out;
    }
}


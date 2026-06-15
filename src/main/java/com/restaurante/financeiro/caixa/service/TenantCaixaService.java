package com.restaurante.financeiro.caixa.service;

import com.restaurante.exception.BusinessException;
import com.restaurante.financeiro.caixa.dto.CaixaPedidoResponse;
import com.restaurante.financeiro.caixa.dto.CaixaResumoMetodoResponse;
import com.restaurante.financeiro.caixa.dto.CaixaResumoResponse;
import com.restaurante.financeiro.enums.StatusPagamentoGateway;
import com.restaurante.financeiro.monitoramento.dto.PagamentoMonitoramentoFiltro;
import com.restaurante.financeiro.monitoramento.dto.PagamentoResumoDTO;
import com.restaurante.financeiro.service.PagamentoMonitoramentoService;
import com.restaurante.model.entity.Mesa;
import com.restaurante.model.entity.Pedido;
import com.restaurante.model.entity.SessaoConsumo;
import com.restaurante.model.enums.MetodoPagamentoManual;
import com.restaurante.model.enums.OrdemPagamentoStatus;
import com.restaurante.model.enums.PaymentMethodCode;
import com.restaurante.model.enums.StatusFinanceiroPedido;
import com.restaurante.model.enums.StatusPedido;
import com.restaurante.repository.PedidoRepository;
import com.restaurante.security.tenant.TenantContext;
import com.restaurante.security.tenant.TenantGuard;
import com.restaurante.service.PedidoWorkflowMetadataService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TenantCaixaService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final TenantGuard tenantGuard;
    private final PedidoRepository pedidoRepository;
    private final PedidoWorkflowMetadataService pedidoWorkflowMetadataService;
    private final PagamentoMonitoramentoService pagamentoMonitoramentoService;

    @Transactional(readOnly = true)
    public Page<CaixaPedidoResponse> listarPedidos(String paymentStatus,
                                                   String statusFinanceiro,
                                                   String operationalStatus,
                                                   String statusOperacional,
                                                   String paymentMethod,
                                                   String metodoPagamento,
                                                   LocalDateTime dateFrom,
                                                   LocalDateTime dateTo,
                                                   String search,
                                                   Pageable pageable) {
        TenantContext ctx = requireTenantContext();
        Pageable safePageable = sanitizePageable(pageable);
        PaymentMethodCode method = parsePaymentMethod(firstNonBlank(paymentMethod, metodoPagamento));
        MetodoPagamentoManual manualMethod = toManualMethod(method);
        boolean appyPayOnly = method == PaymentMethodCode.APPYPAY;

        Page<Pedido> page = pedidoRepository.findTenantCaixaPedidosWithFilters(
                ctx.tenantId(),
                resolveOperationalStatuses(firstNonBlank(operationalStatus, statusOperacional)),
                resolveFinancialStatuses(firstNonBlank(paymentStatus, statusFinanceiro)),
                manualMethod,
                appyPayOnly,
                dateFrom,
                dateTo,
                normalizeSearch(search),
                safePageable
        );
        return page.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public CaixaResumoResponse resumo(LocalDateTime dateFrom, LocalDateTime dateTo) {
        TenantContext ctx = requireTenantContext();
        LocalDateTime from = dateFrom != null ? dateFrom : LocalDate.now().atStartOfDay();
        LocalDateTime to = dateTo != null ? dateTo : LocalDate.now().atTime(LocalTime.MAX);
        List<Pedido> pedidos = pedidoRepository.findTenantCaixaPedidosForResumo(ctx.tenantId(), from, to);

        BigDecimal totalPendente = BigDecimal.ZERO;
        BigDecimal totalPago = BigDecimal.ZERO;
        long quantidadePendente = 0;
        long quantidadePago = 0;
        Map<PaymentMethodCode, CaixaResumoAccumulator> porMetodo = new EnumMap<>(PaymentMethodCode.class);

        for (Pedido pedido : pedidos) {
            BigDecimal total = safeAmount(pedido.getTotal());
            var metadata = pedidoWorkflowMetadataService.resolve(pedido);
            PaymentMethodCode method = metadata.metodoPagamento();
            if (method != null) {
                porMetodo.computeIfAbsent(method, ignored -> new CaixaResumoAccumulator())
                        .add(total);
            }
            if (pedido.getStatusFinanceiro() == StatusFinanceiroPedido.PAGO) {
                quantidadePago++;
                totalPago = totalPago.add(total);
            } else if (pedido.getStatusFinanceiro() != StatusFinanceiroPedido.ESTORNADO) {
                quantidadePendente++;
                totalPendente = totalPendente.add(total);
            }
        }

        List<CaixaResumoMetodoResponse> porMetodoResponse = porMetodo.entrySet().stream()
                .map(entry -> CaixaResumoMetodoResponse.builder()
                        .metodoPagamento(entry.getKey())
                        .total(entry.getValue().total())
                        .quantidade(entry.getValue().quantidade())
                        .build())
                .toList();

        return CaixaResumoResponse.builder()
                .dateFrom(from)
                .dateTo(to)
                .totalPendente(totalPendente)
                .totalPago(totalPago)
                .quantidadePendente(quantidadePendente)
                .quantidadePago(quantidadePago)
                .porMetodo(porMetodoResponse)
                .build();
    }

    @Transactional(readOnly = true)
    public Page<PagamentoResumoDTO> historico(String paymentStatus,
                                              String statusFinanceiro,
                                              String paymentMethod,
                                              String metodoPagamento,
                                              String pedidoNumero,
                                              String externalReference,
                                              LocalDateTime dateFrom,
                                              LocalDateTime dateTo,
                                              Pageable pageable) {
        TenantContext ctx = requireTenantContext();
        PagamentoMonitoramentoFiltro filtro = new PagamentoMonitoramentoFiltro();
        filtro.setStatusPagamento(parseGatewayStatus(paymentStatus));
        filtro.setStatusFinanceiroPedido(parseFinancialStatus(statusFinanceiro));
        PaymentMethodCode method = parsePaymentMethod(firstNonBlank(paymentMethod, metodoPagamento));
        filtro.setMetodoManual(toManualMethod(method));
        filtro.setAppyPayOnly(method == PaymentMethodCode.APPYPAY);
        filtro.setPedidoNumero(blankToNull(pedidoNumero));
        filtro.setExternalReference(blankToNull(externalReference));
        filtro.setDe(dateFrom);
        filtro.setAte(dateTo);
        return pagamentoMonitoramentoService.listarPagamentosDoTenant(ctx.tenantId(), filtro, sanitizePageable(pageable));
    }

    public Pageable sanitizePageable(Pageable pageable) {
        int page = pageable != null ? Math.max(pageable.getPageNumber(), 0) : 0;
        int requestedSize = pageable != null ? pageable.getPageSize() : DEFAULT_PAGE_SIZE;
        int size = Math.max(1, Math.min(requestedSize <= 0 ? DEFAULT_PAGE_SIZE : requestedSize, MAX_PAGE_SIZE));
        Sort sort = pageable != null && pageable.getSort().isSorted()
                ? pageable.getSort()
                : Sort.by(Sort.Direction.DESC, "createdAt");
        return PageRequest.of(page, size, sort);
    }

    private CaixaPedidoResponse toResponse(Pedido pedido) {
        var metadata = pedidoWorkflowMetadataService.resolve(pedido);
        SessaoConsumo sessao = pedido.getSessaoConsumo();
        Mesa mesa = sessao != null ? sessao.getMesa() : null;
        Long instituicaoId = sessao != null && sessao.getInstituicao() != null ? sessao.getInstituicao().getId() : null;
        Long unidadeId = sessao != null && sessao.getUnidadeAtendimento() != null ? sessao.getUnidadeAtendimento().getId() : null;
        BigDecimal total = safeAmount(pedido.getTotal());
        BigDecimal valorPago = pedido.getStatusFinanceiro() == StatusFinanceiroPedido.PAGO ? total : BigDecimal.ZERO;
        BigDecimal saldoPendente = total.subtract(valorPago);
        PaymentMethodCode method = metadata.metodoPagamento();

        return CaixaPedidoResponse.builder()
                .pedidoId(pedido.getId())
                .pedidoNumero(pedido.getNumero())
                .tenantId(pedido.getTenant() != null ? pedido.getTenant().getId() : null)
                .origem(sessao != null ? "SESSAO_CONSUMO" : "PEDIDO_DIRETO")
                .contexto(resolveContexto(sessao, mesa))
                .instituicaoId(instituicaoId)
                .unidadeAtendimentoId(unidadeId)
                .mesaId(mesa != null ? mesa.getId() : null)
                .mesaReferencia(mesa != null ? mesa.getReferencia() : null)
                .clienteNome(metadata.clienteNome())
                .clienteTelefoneMascarado(metadata.clienteTelefoneMascarado())
                .operationalStatus(pedido.getStatus())
                .statusOperacional(pedido.getStatus())
                .paymentStatus(pedido.getStatusFinanceiro())
                .statusFinanceiro(pedido.getStatusFinanceiro())
                .paymentMethod(method)
                .metodoPagamento(method)
                .paymentMethodDetail(metadata.metodoPagamentoDetalhe())
                .ordemPagamentoStatus(metadata.ordemPagamentoStatus())
                .total(total)
                .valorPago(valorPago)
                .saldoPendente(saldoPendente.max(BigDecimal.ZERO))
                .createdAt(pedido.getCreatedAt())
                .updatedAt(pedido.getUpdatedAt())
                .paidAt(pedido.getPagoEm())
                .canConfirmPayment(canConfirmPayment(pedido, method, metadata.ordemPagamentoStatus()))
                .canReversePayment(false)
                .build();
    }

    private boolean canConfirmPayment(Pedido pedido, PaymentMethodCode method, String ordemStatus) {
        return (method == PaymentMethodCode.CASH || method == PaymentMethodCode.TPA)
                && pedido.getStatusFinanceiro() != StatusFinanceiroPedido.PAGO
                && pedido.getStatusFinanceiro() != StatusFinanceiroPedido.ESTORNADO
                && pedido.getStatus() != StatusPedido.CANCELADO
                && OrdemPagamentoStatus.AGUARDANDO_CONFIRMACAO.name().equals(ordemStatus);
    }

    private TenantContext requireTenantContext() {
        TenantContext ctx = tenantGuard.requireContext();
        if (ctx.tenantId() == null) {
            throw new com.restaurante.exception.ResourceNotFoundException("Recurso não encontrado.");
        }
        tenantGuard.assertCurrentUserBelongsToTenant(ctx.tenantId());
        tenantGuard.assertTenantActive(ctx.tenantId());
        return ctx;
    }

    private List<StatusFinanceiroPedido> resolveFinancialStatuses(String raw) {
        String normalized = normalize(raw);
        if (normalized == null || normalized.equals("TODOS") || normalized.equals("ALL")) {
            return List.of(StatusFinanceiroPedido.values());
        }
        return switch (normalized) {
            case "PENDENTES", "PENDENTE", "NAO_PAGO", "NÃO_PAGO", "PENDENTE_PAGAMENTO" ->
                    List.of(StatusFinanceiroPedido.NAO_PAGO, StatusFinanceiroPedido.PENDENTE_PAGAMENTO);
            case "PAGOS", "PAGO", "CONFIRMADO" -> List.of(StatusFinanceiroPedido.PAGO);
            case "PROBLEMAS", "PROBLEMA", "ESTORNADO", "FALHOU", "CANCELADO", "EXPIRADO" ->
                    List.of(StatusFinanceiroPedido.ESTORNADO);
            default -> List.of(parseFinancialStatus(normalized));
        };
    }

    private List<StatusPedido> resolveOperationalStatuses(String raw) {
        String normalized = normalize(raw);
        if (normalized == null || normalized.equals("TODOS") || normalized.equals("ALL")) {
            return List.of(StatusPedido.values());
        }
        return switch (normalized) {
            case "RECEBIDO", "CRIADO" -> List.of(StatusPedido.CRIADO);
            case "ACEITE", "ACEITO", "EM_ANDAMENTO", "EM_PREPARACAO", "EM_PREPARAÇÃO", "PRONTO" ->
                    List.of(StatusPedido.EM_ANDAMENTO);
            case "ENTREGUE", "FINALIZADO" -> List.of(StatusPedido.FINALIZADO);
            case "CANCELADO", "RECUSADO", "REJEITADO" -> List.of(StatusPedido.CANCELADO);
            default -> List.of(StatusPedido.valueOf(normalized));
        };
    }

    private StatusFinanceiroPedido parseFinancialStatus(String raw) {
        String normalized = normalize(raw);
        if (normalized == null) return null;
        return switch (normalized) {
            case "PENDENTE", "NAO_PAGO", "NÃO_PAGO" -> StatusFinanceiroPedido.NAO_PAGO;
            case "PENDENTE_PAGAMENTO" -> StatusFinanceiroPedido.PENDENTE_PAGAMENTO;
            case "PAGO", "CONFIRMADO" -> StatusFinanceiroPedido.PAGO;
            case "ESTORNADO", "FALHOU", "CANCELADO", "EXPIRADO" -> StatusFinanceiroPedido.ESTORNADO;
            default -> StatusFinanceiroPedido.valueOf(normalized);
        };
    }

    private StatusPagamentoGateway parseGatewayStatus(String raw) {
        String normalized = normalize(raw);
        if (normalized == null || normalized.equals("TODOS") || normalized.equals("ALL")) return null;
        return switch (normalized) {
            case "PAGO", "PAGOS", "CONFIRMADO" -> StatusPagamentoGateway.CONFIRMADO;
            case "PENDENTE", "PENDENTES", "NAO_PAGO", "NÃO_PAGO", "PENDENTE_PAGAMENTO" -> StatusPagamentoGateway.PENDENTE;
            case "FALHOU", "FALHADO", "PROBLEMA", "PROBLEMAS" -> StatusPagamentoGateway.FALHOU;
            case "CANCELADO", "EXPIRADO", "ESTORNADO" -> StatusPagamentoGateway.ESTORNADO;
            default -> StatusPagamentoGateway.valueOf(normalized);
        };
    }

    private PaymentMethodCode parsePaymentMethod(String raw) {
        String normalized = normalize(raw);
        if (normalized == null || normalized.equals("TODOS") || normalized.equals("ALL")) return null;
        return switch (normalized) {
            case "DINHEIRO", "CASH" -> PaymentMethodCode.CASH;
            case "TPA", "POS" -> PaymentMethodCode.TPA;
            case "APPYPAY", "APPY_PAY", "GPO", "REF", "REFERENCIA", "REFERÊNCIA" -> PaymentMethodCode.APPYPAY;
            case "TRANSFERENCIA", "TRANSFERÊNCIA" ->
                    throw new BusinessException("TRANSFERENCIA ainda não está mapeado como método de pagamento local neste domínio.");
            default -> PaymentMethodCode.valueOf(normalized);
        };
    }

    private MetodoPagamentoManual toManualMethod(PaymentMethodCode method) {
        if (method == PaymentMethodCode.CASH) return MetodoPagamentoManual.CASH;
        if (method == PaymentMethodCode.TPA) return MetodoPagamentoManual.TPA;
        return null;
    }

    private String resolveContexto(SessaoConsumo sessao, Mesa mesa) {
        if (mesa != null && mesa.getReferencia() != null) return "Mesa " + mesa.getReferencia();
        if (sessao != null && sessao.getUnidadeAtendimento() != null) return sessao.getUnidadeAtendimento().getNome();
        return "Atendimento";
    }

    private String firstNonBlank(String first, String second) {
        return !isBlank(first) ? first : (!isBlank(second) ? second : null);
    }

    private String normalizeSearch(String value) {
        return blankToNull(value);
    }

    private String blankToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String normalize(String value) {
        return isBlank(value) ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private BigDecimal safeAmount(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private static final class CaixaResumoAccumulator {
        private BigDecimal total = BigDecimal.ZERO;
        private long quantidade;

        void add(BigDecimal value) {
            total = total.add(value != null ? value : BigDecimal.ZERO);
            quantidade++;
        }

        BigDecimal total() {
            return total;
        }

        long quantidade() {
            return quantidade;
        }
    }
}

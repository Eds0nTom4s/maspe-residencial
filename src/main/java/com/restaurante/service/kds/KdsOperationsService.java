package com.restaurante.service.kds;

import com.restaurante.dto.request.KdsTransitionRequest;
import com.restaurante.dto.response.kds.KdsSubPedidoItemResponse;
import com.restaurante.dto.response.kds.KdsSubPedidoListResponse;
import com.restaurante.dto.response.kds.KdsSubPedidoResponse;
import com.restaurante.dto.response.kds.KdsSummaryResponse;
import com.restaurante.dto.response.kds.KdsUnidadeProducaoResponse;
import com.restaurante.exception.BusinessException;
import com.restaurante.exception.KdsSubPedidoConflictException;
import com.restaurante.exception.ResourceNotFoundException;
import com.restaurante.model.entity.ItemPedido;
import com.restaurante.model.entity.Pedido;
import com.restaurante.model.entity.SessaoConsumo;
import com.restaurante.model.entity.SubPedido;
import com.restaurante.model.enums.StatusSubPedido;
import com.restaurante.model.enums.TenantUserRole;
import com.restaurante.repository.SubPedidoRepository;
import com.restaurante.repository.UnidadeProducaoRepository;
import com.restaurante.security.tenant.TenantContext;
import com.restaurante.security.tenant.TenantGuard;
import com.restaurante.service.operacional.SubPedidoStatusTransitionService;
import com.restaurante.service.operacional.OperationalCapabilitiesPolicy;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class KdsOperationsService {

    private static final String CONFLICT_MESSAGE =
            "Este item já foi atualizado por outro operador. Atualize a lista para ver o estado mais recente.";

    private final TenantGuard tenantGuard;
    private final UnidadeProducaoRepository unidadeProducaoRepository;
    private final SubPedidoRepository subPedidoRepository;
    private final SubPedidoStatusTransitionService transitionService;
    private final OperationalCapabilitiesPolicy operationalCapabilitiesPolicy;

    @Transactional(readOnly = true)
    public List<KdsUnidadeProducaoResponse> listarUnidadesProducao() {
        Long tenantId = requireKdsTenant();
        return unidadeProducaoRepository.findByTenantIdAndAtivoTrueOrderByOrdemAsc(tenantId).stream()
                .map(up -> new KdsUnidadeProducaoResponse(
                        up.getId(),
                        up.getNome(),
                        up.getTipo(),
                        up.getAtivo(),
                        up.getOrdem()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public KdsSubPedidoListResponse listarSubPedidos(Long unidadeProducaoId,
                                                     StatusSubPedido status,
                                                     Long pedidoId,
                                                     LocalDateTime createdFrom,
                                                     LocalDateTime createdTo) {
        Long tenantId = requireKdsTenant();
        if (unidadeProducaoId != null) {
            unidadeProducaoRepository.findByIdAndTenantId(unidadeProducaoId, tenantId)
                    .orElseThrow(() -> new ResourceNotFoundException("UnidadeProducao", "id", unidadeProducaoId));
        }
        List<KdsSubPedidoResponse> items = subPedidoRepository.findKdsContractByTenantAndFilters(
                        tenantId,
                        unidadeProducaoId,
                        status,
                        pedidoId,
                        createdFrom,
                        createdTo
                ).stream()
                .map(this::toResponse)
                .toList();
        return new KdsSubPedidoListResponse(items, toSummary(items));
    }

    @Transactional(readOnly = true)
    public KdsSubPedidoResponse buscarDetalhe(Long id) {
        Long tenantId = requireKdsTenant();
        return toResponse(loadSubPedido(id, tenantId));
    }

    @Transactional
    public KdsSubPedidoResponse iniciarPreparo(Long id, KdsTransitionRequest request, HttpServletRequest http) {
        Long tenantId = requireKdsTenant();
        SubPedido current = loadSubPedido(id, tenantId);
        assertVersionMatches(current, request);

        if (current.getStatus() == StatusSubPedido.EM_PREPARACAO) {
            return toResponse(current);
        }
        if (current.getStatus() == StatusSubPedido.CRIADO) {
            transitionService.atualizarStatus(id, StatusSubPedido.PENDENTE, "Aceite operacional KDS", remoteIp(http), userAgent(http));
        } else if (current.getStatus() != StatusSubPedido.PENDENTE) {
            throw new BusinessException("SubPedido deve estar em CRIADO ou PENDENTE para iniciar preparo.");
        }

        transitionService.atualizarStatus(id, StatusSubPedido.EM_PREPARACAO, "Preparo iniciado no KDS", remoteIp(http), userAgent(http));
        return buscarDetalhe(id);
    }

    @Transactional
    public KdsSubPedidoResponse marcarPronto(Long id, KdsTransitionRequest request, HttpServletRequest http) {
        Long tenantId = requireKdsTenant();
        SubPedido current = loadSubPedido(id, tenantId);
        assertVersionMatches(current, request);

        if (current.getStatus() == StatusSubPedido.PRONTO) {
            return toResponse(current);
        }
        if (current.getStatus() != StatusSubPedido.EM_PREPARACAO) {
            throw new BusinessException("SubPedido deve estar em EM_PREPARACAO para ser marcado como PRONTO.");
        }

        transitionService.atualizarStatus(id, StatusSubPedido.PRONTO, "Preparo finalizado no KDS", remoteIp(http), userAgent(http));
        return buscarDetalhe(id);
    }

    @Transactional
    public KdsSubPedidoResponse entregar(Long id, KdsTransitionRequest request, HttpServletRequest http) {
        Long tenantId = requireKdsTenant();
        SubPedido current = loadSubPedido(id, tenantId);
        assertVersionMatches(current, request);

        if (current.getStatus() == StatusSubPedido.ENTREGUE) {
            return toResponse(current);
        }
        if (current.getStatus() != StatusSubPedido.PRONTO) {
            throw new BusinessException("SubPedido deve estar em PRONTO para ser entregue.");
        }

        transitionService.atualizarStatus(id, StatusSubPedido.ENTREGUE, "Entrega confirmada no KDS", remoteIp(http), userAgent(http));
        return buscarDetalhe(id);
    }

    private Long requireKdsTenant() {
        TenantContext ctx = tenantGuard.requireContext();
        if (ctx.tenantId() == null) {
            throw new org.springframework.security.access.AccessDeniedException("Token tenant-scoped obrigatório para operar KDS.");
        }
        tenantGuard.assertAnyTenantRole(
                TenantUserRole.TENANT_OWNER,
                TenantUserRole.TENANT_ADMIN,
                TenantUserRole.TENANT_OPERATOR,
                TenantUserRole.TENANT_KITCHEN
        );
        operationalCapabilitiesPolicy.assertKdsEnabled(ctx.tenantId());
        return ctx.tenantId();
    }

    private SubPedido loadSubPedido(Long id, Long tenantId) {
        SubPedido subPedido = subPedidoRepository.findKdsContractByIdAndTenant(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("SubPedido", "id", id));
        operationalCapabilitiesPolicy.assertPedidoCanUseKds(subPedido.getPedido());
        return subPedido;
    }

    private void assertVersionMatches(SubPedido subPedido, KdsTransitionRequest request) {
        Long expected = request != null ? request.getVersion() : null;
        if (expected == null) {
            return;
        }
        Long current = subPedido.getVersion();
        if (!expected.equals(current)) {
            throw new KdsSubPedidoConflictException(CONFLICT_MESSAGE, subPedido.getStatus(), current);
        }
    }

    private KdsSubPedidoResponse toResponse(SubPedido sp) {
        Pedido pedido = sp.getPedido();
        SessaoConsumo sessao = pedido != null ? pedido.getSessaoConsumo() : null;
        var mesa = sessao != null ? sessao.getMesa() : null;
        var cliente = pedido != null ? pedido.getClienteConsumo() : null;

        return new KdsSubPedidoResponse(
                sp.getId(),
                pedido != null ? pedido.getId() : null,
                pedido != null ? pedido.getNumero() : null,
                sp.getStatus(),
                sp.getUnidadeProducao() != null ? sp.getUnidadeProducao().getId() : null,
                sp.getUnidadeProducao() != null ? sp.getUnidadeProducao().getNome() : null,
                sp.getItens() != null ? sp.getItens().stream().map(this::toItemResponse).toList() : List.of(),
                cliente != null ? cliente.getNome() : null,
                cliente != null ? cliente.getTelefone() : null,
                mesa != null ? mesa.getId() : null,
                mesa != null ? resolveMesaNome(mesa) : null,
                sessao != null ? sessao.getId() : null,
                sp.getCreatedAt(),
                sp.getUpdatedAt(),
                sp.getVersion()
        );
    }

    private KdsSubPedidoItemResponse toItemResponse(ItemPedido item) {
        return new KdsSubPedidoItemResponse(
                item.getProduto() != null ? item.getProduto().getId() : null,
                item.getProduto() != null ? item.getProduto().getNome() : null,
                item.getQuantidade(),
                item.getObservacoes()
        );
    }

    private KdsSummaryResponse toSummary(List<KdsSubPedidoResponse> items) {
        long pendentes = items.stream()
                .filter(item -> item.status() == StatusSubPedido.CRIADO || item.status() == StatusSubPedido.PENDENTE)
                .count();
        long emPreparacao = items.stream().filter(item -> item.status() == StatusSubPedido.EM_PREPARACAO).count();
        long prontos = items.stream().filter(item -> item.status() == StatusSubPedido.PRONTO).count();
        long entregues = items.stream().filter(item -> item.status() == StatusSubPedido.ENTREGUE).count();
        return new KdsSummaryResponse(pendentes, emPreparacao, prontos, entregues, items.size());
    }

    private String resolveMesaNome(com.restaurante.model.entity.Mesa mesa) {
        if (mesa.getReferencia() != null && !mesa.getReferencia().isBlank()) {
            return mesa.getReferencia();
        }
        return mesa.getNumero() != null ? "Mesa " + mesa.getNumero() : null;
    }

    private String remoteIp(HttpServletRequest request) {
        return request != null ? request.getRemoteAddr() : null;
    }

    private String userAgent(HttpServletRequest request) {
        return request != null ? request.getHeader("User-Agent") : null;
    }
}

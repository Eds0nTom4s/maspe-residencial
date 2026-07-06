package com.restaurante.service.tenantadmin;

import com.restaurante.config.OperacaoProperties;
import com.restaurante.dto.response.TenantPedidoDetalheResponse;
import com.restaurante.dto.response.TenantPedidoResumoResponse;
import com.restaurante.exception.ResourceNotFoundException;
import com.restaurante.exception.TurnoObrigatorioException;
import com.restaurante.model.entity.ItemPedido;
import com.restaurante.model.entity.Mesa;
import com.restaurante.model.entity.Pedido;
import com.restaurante.model.entity.SessaoConsumo;
import com.restaurante.model.entity.SubPedido;
import com.restaurante.model.enums.StatusFinanceiroPedido;
import com.restaurante.model.enums.StatusPedido;
import com.restaurante.repository.PedidoRepository;
import com.restaurante.repository.TurnoOperacionalRepository;
import com.restaurante.security.tenant.TenantContext;
import com.restaurante.security.tenant.TenantGuard;
import com.restaurante.service.operacional.PedidoAllowedActionsService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TenantAdminPedidoService {

    private final TenantGuard tenantGuard;
    private final PedidoRepository pedidoRepository;
    private final TurnoOperacionalRepository turnoOperacionalRepository;
    private final OperacaoProperties operacaoProperties;
    private final PedidoAllowedActionsService pedidoAllowedActionsService;

    @Transactional(readOnly = true)
    public Page<TenantPedidoResumoResponse> listarPedidos(
            StatusPedido statusOperacional,
            StatusFinanceiroPedido statusFinanceiro,
            Long instituicaoId,
            Long unidadeAtendimentoId,
            Long mesaId,
            LocalDateTime de,
            LocalDateTime ate,
            Pageable pageable
    ) {
        TenantContext ctx = requireTenantContext();
        Page<Pedido> page;
        if (operacaoProperties.isTurnoObrigatorio()) {
            List<Long> turnoIds = turnoOperacionalRepository.findOpenIdsByTenantAndOptionalScope(
                    ctx.tenantId(),
                    instituicaoId,
                    unidadeAtendimentoId
            );
            if (turnoIds.isEmpty()) {
                throw new TurnoObrigatorioException("Abra um turno para consultar os pedidos operacionais.");
            }
            page = pedidoRepository.findTenantPedidosWithFiltersAndTurnos(
                    ctx.tenantId(),
                    turnoIds,
                    statusOperacional,
                    statusFinanceiro,
                    de,
                    ate,
                    instituicaoId,
                    unidadeAtendimentoId,
                    mesaId,
                    pageable
            );
        } else {
            page = pedidoRepository.findTenantPedidosWithFilters(
                    ctx.tenantId(),
                    statusOperacional,
                    statusFinanceiro,
                    de,
                    ate,
                    instituicaoId,
                    unidadeAtendimentoId,
                    mesaId,
                    pageable
            );
        }
        return page.map(p -> toResumo(p, ctx));
    }

    @Transactional(readOnly = true)
    public TenantPedidoDetalheResponse buscarDetalhe(Long pedidoId) {
        TenantContext ctx = requireTenantContext();
        Pedido p = pedidoRepository.findByIdAndTenantIdComItens(pedidoId, ctx.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Recurso não encontrado."));
        return toDetalhe(p, ctx);
    }

    private TenantContext requireTenantContext() {
        TenantContext ctx = tenantGuard.requireContext();
        if (ctx.tenantId() == null) {
            throw new ResourceNotFoundException("Recurso não encontrado.");
        }
        tenantGuard.assertCurrentUserBelongsToTenant(ctx.tenantId());
        tenantGuard.assertTenantActive(ctx.tenantId());
        return ctx;
    }

    private TenantPedidoResumoResponse toResumo(Pedido p, TenantContext tenantContext) {
        SessaoConsumo s = p.getSessaoConsumo();
        Long instId = s != null && s.getInstituicao() != null ? s.getInstituicao().getId() : null;
        Long uaId = s != null && s.getUnidadeAtendimento() != null ? s.getUnidadeAtendimento().getId() : null;
        Mesa mesa = s != null ? s.getMesa() : null;
        var capabilities = pedidoAllowedActionsService.evaluate(p, tenantContext);
        return TenantPedidoResumoResponse.builder()
                .id(p.getId())
                .numero(p.getNumero())
                .statusOperacional(p.getStatus())
                .statusFinanceiro(p.getStatusFinanceiro())
                .total(p.getTotal())
                .pedidoOrigem(p.getPedidoOrigem())
                .instituicaoId(instId)
                .unidadeAtendimentoId(uaId)
                .mesaId(mesa != null ? mesa.getId() : null)
                .mesaReferencia(mesa != null ? mesa.getReferencia() : null)
                .criadoEm(p.getCreatedAt())
                .atualizadoEm(p.getUpdatedAt())
                .pagoEm(p.getPagoEm())
                .quantidadeItens(p.getItens() != null ? p.getItens().size() : 0)
                .allowedActions(capabilities.allowedActions())
                .actionReasons(capabilities.actionReasons())
                .build();
    }

    private TenantPedidoDetalheResponse toDetalhe(Pedido p, TenantContext tenantContext) {
        SessaoConsumo s = p.getSessaoConsumo();
        var ctx = TenantPedidoDetalheResponse.TenantPedidoContextResponse.builder();
        if (s != null) {
            if (s.getInstituicao() != null) {
                ctx.instituicaoId(s.getInstituicao().getId());
                ctx.instituicaoNome(s.getInstituicao().getNome());
            }
            if (s.getUnidadeAtendimento() != null) {
                ctx.unidadeAtendimentoId(s.getUnidadeAtendimento().getId());
                ctx.unidadeAtendimentoNome(s.getUnidadeAtendimento().getNome());
            }
            if (s.getMesa() != null) {
                ctx.mesaId(s.getMesa().getId());
                ctx.mesaReferencia(s.getMesa().getReferencia());
                ctx.mesaNumero(s.getMesa().getNumero());
            }
        }

        List<TenantPedidoDetalheResponse.ItemResponse> itens = (p.getItens() == null ? List.<ItemPedido>of() : p.getItens())
                .stream()
                .map(i -> TenantPedidoDetalheResponse.ItemResponse.builder()
                        .produtoId(i.getProduto() != null ? i.getProduto().getId() : null)
                        .produtoNome(i.getProduto() != null ? i.getProduto().getNome() : null)
                        .quantidade(i.getQuantidade())
                        .precoUnitario(i.getPrecoUnitario())
                        .subtotal(i.getSubtotal())
                        .observacao(i.getObservacoes())
                        .build())
                .toList();

        List<TenantPedidoDetalheResponse.SubPedidoResponse> subs = (p.getSubPedidos() == null ? List.<SubPedido>of() : p.getSubPedidos())
                .stream()
                .map(sp -> TenantPedidoDetalheResponse.SubPedidoResponse.builder()
                        .id(sp.getId())
                        .status(sp.getStatus() != null ? sp.getStatus().name() : null)
                        .itens((sp.getItens() == null ? List.<ItemPedido>of() : sp.getItens()).stream()
                                .map(i -> TenantPedidoDetalheResponse.ItemResponse.builder()
                                        .produtoId(i.getProduto() != null ? i.getProduto().getId() : null)
                                        .produtoNome(i.getProduto() != null ? i.getProduto().getNome() : null)
                                        .quantidade(i.getQuantidade())
                                        .precoUnitario(i.getPrecoUnitario())
                                        .subtotal(i.getSubtotal())
                                        .observacao(i.getObservacoes())
                                        .build())
                                .toList())
                        .build())
                .toList();

        var capabilities = pedidoAllowedActionsService.evaluate(p, tenantContext);
        return TenantPedidoDetalheResponse.builder()
                .id(p.getId())
                .numero(p.getNumero())
                .statusOperacional(p.getStatus())
                .statusFinanceiro(p.getStatusFinanceiro())
                .total(p.getTotal())
                .pedidoOrigem(p.getPedidoOrigem())
                .observacoes(p.getObservacoes())
                .criadoEm(p.getCreatedAt())
                .atualizadoEm(p.getUpdatedAt())
                .pagoEm(p.getPagoEm())
                .contexto(ctx.build())
                .itens(itens)
                .subPedidos(subs)
                .allowedActions(capabilities.allowedActions())
                .actionReasons(capabilities.actionReasons())
                .build();
    }
}

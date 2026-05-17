package com.restaurante.service.producao;

import com.restaurante.dto.response.SubPedidoProducaoResponse;
import com.restaurante.exception.BusinessException;
import com.restaurante.exception.ResourceNotFoundException;
import com.restaurante.model.entity.ItemPedido;
import com.restaurante.model.entity.SubPedido;
import com.restaurante.model.enums.StatusSubPedido;
import com.restaurante.model.enums.TenantUserRole;
import com.restaurante.repository.SubPedidoRepository;
import com.restaurante.security.tenant.TenantContext;
import com.restaurante.security.tenant.TenantGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProducaoSubPedidoService {

    private final TenantGuard tenantGuard;
    private final SubPedidoRepository subPedidoRepository;
    private final UnidadeProducaoService unidadeProducaoService;

    @Transactional(readOnly = true)
    public List<SubPedidoProducaoResponse> listarSubPedidosDaUnidade(Long unidadeProducaoId, StatusSubPedido status) {
        TenantContext ctx = tenantGuard.requireContext();
        tenantGuard.assertAnyTenantRole(
                TenantUserRole.TENANT_OWNER,
                TenantUserRole.TENANT_ADMIN,
                TenantUserRole.TENANT_OPERATOR,
                TenantUserRole.TENANT_KITCHEN
        );

        if (ctx.tenantId() == null) {
            throw new BusinessException("TenantContext obrigatório.");
        }
        // valida unidade pertence ao tenant
        unidadeProducaoService.buscarPorIdETenant(unidadeProducaoId, ctx.tenantId());

        List<SubPedido> subs = subPedidoRepository.findByTenantIdAndUnidadeProducaoIdAndStatusOrderByCreatedAtDesc(
                ctx.tenantId(), unidadeProducaoId, status
        );
        return subs.stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public SubPedidoProducaoResponse buscarDetalhe(Long subPedidoId) {
        TenantContext ctx = tenantGuard.requireContext();
        tenantGuard.assertAnyTenantRole(
                TenantUserRole.TENANT_OWNER,
                TenantUserRole.TENANT_ADMIN,
                TenantUserRole.TENANT_OPERATOR,
                TenantUserRole.TENANT_KITCHEN
        );
        SubPedido sp = subPedidoRepository.findByIdAndTenantId(subPedidoId, ctx.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("SubPedido", "id", subPedidoId));
        return toDto(sp);
    }

    @Transactional
    public SubPedidoProducaoResponse atualizarStatus(Long subPedidoId, StatusSubPedido novoStatus) {
        TenantContext ctx = tenantGuard.requireContext();

        boolean isKitchen = tenantGuard.hasAnyTenantRole(TenantUserRole.TENANT_KITCHEN);
        if (isKitchen) {
            // Apenas transições de produção
            if (!(novoStatus == StatusSubPedido.EM_PREPARACAO || novoStatus == StatusSubPedido.PRONTO)) {
                throw new org.springframework.security.access.AccessDeniedException("Usuário não possui permissão para executar esta ação.");
            }
        } else {
            tenantGuard.assertAnyTenantRole(
                    TenantUserRole.TENANT_OWNER,
                    TenantUserRole.TENANT_ADMIN,
                    TenantUserRole.TENANT_OPERATOR
            );
        }

        SubPedido sp = subPedidoRepository.findByIdAndTenantId(subPedidoId, ctx.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("SubPedido", "id", subPedidoId));

        if (novoStatus == null) {
            throw new BusinessException("Status é obrigatório.");
        }
        if (!sp.podeTransicionarPara(novoStatus)) {
            throw new BusinessException("Transição de status inválida.");
        }

        sp.setStatus(novoStatus);
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        if (novoStatus == StatusSubPedido.EM_PREPARACAO) {
            sp.setIniciadoEm(now);
        } else if (novoStatus == StatusSubPedido.PRONTO) {
            sp.setProntoEm(now);
        }

        SubPedido saved = subPedidoRepository.save(sp);
        return toDto(saved);
    }

    private SubPedidoProducaoResponse toDto(SubPedido sp) {
        var pedido = sp.getPedido();
        var sessao = pedido != null ? pedido.getSessaoConsumo() : null;
        var mesa = sessao != null ? sessao.getMesa() : null;

        return SubPedidoProducaoResponse.builder()
                .id(sp.getId())
                .numero(sp.getNumero())
                .status(sp.getStatus())
                .pedidoId(pedido != null ? pedido.getId() : null)
                .pedidoNumero(pedido != null ? pedido.getNumero() : null)
                .unidadeProducaoId(sp.getUnidadeProducao() != null ? sp.getUnidadeProducao().getId() : null)
                .unidadeProducaoNome(sp.getUnidadeProducao() != null ? sp.getUnidadeProducao().getNome() : null)
                .unidadeProducaoCodigo(sp.getUnidadeProducao() != null ? sp.getUnidadeProducao().getCodigo() : null)
                .mesaId(mesa != null ? mesa.getId() : null)
                .mesaReferencia(mesa != null ? mesa.getReferencia() : null)
                .mesaNumero(mesa != null ? mesa.getNumero() : null)
                .total(sp.getTotal())
                .criadoEm(sp.getCreatedAt())
                .atualizadoEm(sp.getUpdatedAt())
                .itens(sp.getItens() != null ? sp.getItens().stream().map(this::toItem).toList() : List.of())
                .build();
    }

    private SubPedidoProducaoResponse.Item toItem(ItemPedido i) {
        return SubPedidoProducaoResponse.Item.builder()
                .produtoId(i.getProduto() != null ? i.getProduto().getId() : null)
                .produtoNome(i.getProduto() != null ? i.getProduto().getNome() : null)
                .quantidade(i.getQuantidade())
                .precoUnitario(i.getPrecoUnitario())
                .subtotal(i.getSubtotal())
                .observacoes(i.getObservacoes())
                .build();
    }
}


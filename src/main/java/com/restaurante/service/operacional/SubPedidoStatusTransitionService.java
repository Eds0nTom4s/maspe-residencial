package com.restaurante.service.operacional;

import com.restaurante.dto.response.SubPedidoProducaoResponse;
import com.restaurante.exception.BusinessException;
import com.restaurante.exception.ConflictException;
import com.restaurante.exception.ResourceNotFoundException;
import com.restaurante.model.entity.ItemPedido;
import com.restaurante.model.entity.Pedido;
import com.restaurante.model.entity.SessaoConsumo;
import com.restaurante.model.entity.SubPedido;
import com.restaurante.model.enums.OperationalOrigem;
import com.restaurante.model.enums.StatusSubPedido;
import com.restaurante.model.enums.TenantUserRole;
import com.restaurante.repository.PedidoRepository;
import com.restaurante.repository.SubPedidoRepository;
import com.restaurante.security.device.DevicePrincipal;
import com.restaurante.security.tenant.TenantContext;
import com.restaurante.security.tenant.TenantGuard;
import com.restaurante.service.PedidoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SubPedidoStatusTransitionService {

    private final TenantGuard tenantGuard;
    private final SubPedidoRepository subPedidoRepository;
    private final PedidoService pedidoService;
    private final PedidoRepository pedidoRepository;
    private final OperationalEventLogService operationalEventLogService;

    @Transactional
    public SubPedidoProducaoResponse atualizarStatus(Long subPedidoId, StatusSubPedido novoStatus, String motivo, String ip, String userAgent) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        DevicePrincipal devicePrincipal = auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof DevicePrincipal dp ? dp : null;

        Long tenantId;
        boolean isDevice = devicePrincipal != null;
        if (isDevice) {
            tenantId = devicePrincipal.tenantId();
        } else {
            TenantContext ctx = tenantGuard.requireContext();
            tenantId = ctx.tenantId();
        }

        if (tenantId == null) throw new ResourceNotFoundException("Recurso não encontrado.");

        if (novoStatus == null) {
            throw new BusinessException("Status é obrigatório.");
        }

        boolean isKitchen = !isDevice && tenantGuard.hasAnyTenantRole(TenantUserRole.TENANT_KITCHEN);
        if (isKitchen) {
            // Cozinha só pode transições de produção
            if (!(novoStatus == StatusSubPedido.EM_PREPARACAO || novoStatus == StatusSubPedido.PRONTO)) {
                throw new org.springframework.security.access.AccessDeniedException("Usuário não possui permissão para executar esta ação.");
            }
        } else if (isDevice) {
            // Device só pode transições de produção e precisa de capability
            boolean canUpdate = devicePrincipal.capabilities() != null
                    && devicePrincipal.capabilities().contains(com.restaurante.model.enums.DeviceCapability.UPDATE_PRODUCTION_STATUS);
            if (!canUpdate) {
                throw new org.springframework.security.access.AccessDeniedException("Usuário não possui permissão para executar esta ação.");
            }
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

        SubPedido sp = subPedidoRepository.findByIdAndTenantId(subPedidoId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Recurso não encontrado."));

        StatusSubPedido anterior = sp.getStatus();
        if (!sp.podeTransicionarPara(novoStatus)) {
            operationalEventLogService.logTransitionBlocked(
                    com.restaurante.model.enums.OperationalEntityType.SUBPEDIDO,
                    sp.getId(),
                    resolveOrigem(),
                    "Transição inválida",
                    Map.of("statusAnterior", anterior != null ? anterior.name() : null, "statusNovo", novoStatus.name()),
                    ip,
                    userAgent
            );
            throw new ConflictException("Transição de status inválida.");
        }

        if (novoStatus == StatusSubPedido.CANCELADO && (motivo == null || motivo.isBlank())) {
            throw new BusinessException("Motivo é obrigatório para cancelar.");
        }

        LocalDateTime now = LocalDateTime.now();
        sp.setStatus(novoStatus);
        if (novoStatus == StatusSubPedido.EM_PREPARACAO) {
            sp.setIniciadoEm(now);
        } else if (novoStatus == StatusSubPedido.PRONTO) {
            sp.setProntoEm(now);
        } else if (novoStatus == StatusSubPedido.ENTREGUE) {
            sp.setEntregueEm(now);
        }

        SubPedido saved = subPedidoRepository.save(sp);

        operationalEventLogService.logSubPedidoStatusChanged(
                saved,
                anterior != null ? anterior.name() : null,
                novoStatus.name(),
                resolveOrigem(),
                motivo,
                Map.of(),
                ip,
                userAgent
        );

        // Status do Pedido é derivado dos subpedidos; recalcula de forma centralizada
        Pedido pedido = saved.getPedido();
        if (pedido != null) {
            var beforePedidoStatus = pedido.getStatus();
            pedidoService.recalcularStatusPedido(pedido.getId());
            Pedido after = pedidoRepository.findByIdAndTenantId(pedido.getId(), tenantId).orElse(null);
            if (after != null && after.getStatus() != null && beforePedidoStatus != after.getStatus()) {
                operationalEventLogService.logPedidoStatusChanged(
                        after,
                        beforePedidoStatus != null ? beforePedidoStatus.name() : null,
                        after.getStatus().name(),
                        resolveOrigem(),
                        "Status recalculado a partir de SubPedido",
                        Map.of("derivedFromSubPedidoId", saved.getId()),
                        ip,
                        userAgent
                );
            }
        }

        return toDto(saved);
    }

    private OperationalOrigem resolveOrigem() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof DevicePrincipal dp) {
            return switch (dp.tipo()) {
                case KDS, COZINHA, BAR -> OperationalOrigem.DEVICE_KDS;
                default -> OperationalOrigem.DEVICE_POS;
            };
        }
        if (tenantGuard.hasAnyTenantRole(TenantUserRole.TENANT_KITCHEN)) return OperationalOrigem.TENANT_KITCHEN;
        if (tenantGuard.hasAnyTenantRole(TenantUserRole.TENANT_CASHIER)) return OperationalOrigem.TENANT_CASHIER;
        if (tenantGuard.hasAnyTenantRole(TenantUserRole.TENANT_OPERATOR)) return OperationalOrigem.TENANT_OPERATOR;
        if (tenantGuard.hasAnyTenantRole(TenantUserRole.TENANT_ADMIN)) return OperationalOrigem.TENANT_ADMIN;
        if (tenantGuard.hasAnyTenantRole(TenantUserRole.TENANT_OWNER)) return OperationalOrigem.TENANT_OWNER;
        return OperationalOrigem.SYSTEM;
    }

    private SubPedidoProducaoResponse toDto(SubPedido sp) {
        var pedido = sp.getPedido();
        SessaoConsumo sessao = pedido != null ? pedido.getSessaoConsumo() : null;
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

package com.restaurante.service;

import com.restaurante.dto.request.TenantPagamentoPolicyRequest;
import com.restaurante.dto.response.TenantPagamentoPolicyResponse;
import com.restaurante.exception.BusinessException;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.TenantOperacaoPolicy;
import com.restaurante.model.enums.ComportamentoPedidoNaoPago;
import com.restaurante.model.enums.OperationalEntityType;
import com.restaurante.model.enums.OperationalEventType;
import com.restaurante.model.enums.OperationalOrigem;
import com.restaurante.repository.TenantOperacaoPolicyRepository;
import com.restaurante.repository.TenantRepository;
import com.restaurante.security.tenant.TenantContextHolder;
import com.restaurante.security.tenant.TenantGuard;
import com.restaurante.service.operacional.OperationalEventLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class TenantPagamentoPolicyService {

    private final TenantGuard tenantGuard;
    private final TenantRepository tenantRepository;
    private final TenantOperacaoPolicyRepository repository;
    private final OperationalEventLogService operationalEventLogService;

    @Transactional
    public TenantPagamentoPolicyResponse obterDoTenantAtual() {
        Long tenantId = requireTenantId("leitura de política de pagamento");
        return toResponse(getOrCreatePolicy(tenantId));
    }

    @Transactional
    public TenantPagamentoPolicyResponse atualizarDoTenantAtual(TenantPagamentoPolicyRequest request) {
        Long tenantId = requireTenantId("atualização de política de pagamento");
        TenantOperacaoPolicy policy = getOrCreatePolicy(tenantId);
        apply(policy, request);
        TenantOperacaoPolicy saved = repository.save(policy);
        operationalEventLogService.logGenericForTenant(
                tenantId,
                OperationalEventType.PAYMENT_POLICY_UPDATED,
                OperationalEntityType.TENANT_PAYMENT_POLICY,
                saved.getId(),
                OperationalOrigem.TENANT_ADMIN,
                "Política de pagamento atualizada",
                Map.of(
                        "pagamentoObrigatorioAntesDoPedido", saved.isPagamentoObrigatorioAntesDoPedido(),
                        "permitirPedidoSemPagamento", saved.isPermitirPedidoSemPagamento(),
                        "permitirPosPago", saved.isPermitirPosPago(),
                        "comportamentoPedidoNaoPago", saved.getComportamentoPedidoNaoPago().name()
                ),
                null,
                null
        );
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public TenantOperacaoPolicy obterParaTenant(Long tenantId) {
        return repository.findByTenantId(tenantId).orElseGet(() -> defaultPolicyForRead(tenantId));
    }

    private TenantOperacaoPolicy getOrCreatePolicy(Long tenantId) {
        return repository.findByTenantId(tenantId).orElseGet(() -> {
            Tenant tenant = tenantRepository.findById(tenantId)
                    .orElseThrow(() -> new BusinessException("Tenant não encontrado: " + tenantId));
            TenantOperacaoPolicy p = defaultPolicyForRead(tenantId);
            p.setTenant(tenant);
            return repository.save(p);
        });
    }

    private TenantOperacaoPolicy defaultPolicyForRead(Long tenantId) {
        TenantOperacaoPolicy p = new TenantOperacaoPolicy();
        tenantRepository.findById(tenantId).ifPresent(p::setTenant);
        p.setPagamentoObrigatorioAntesDoPedido(true);
        p.setPermitirPedidoSemPagamento(false);
        p.setPermitirPosPago(false);
        p.setPermitirCash(false);
        p.setPermitirPagamentoNaEntrega(false);
        p.setTempoExpiracaoPedidoPendentePagamentoMinutos(15);
        p.setComportamentoPedidoNaoPago(ComportamentoPedidoNaoPago.CRIAR_PENDENTE);
        return p;
    }

    private void apply(TenantOperacaoPolicy policy, TenantPagamentoPolicyRequest request) {
        policy.setPagamentoObrigatorioAntesDoPedido(Boolean.TRUE.equals(request.getPagamentoObrigatorioAntesDoPedido()));
        policy.setPermitirPedidoSemPagamento(Boolean.TRUE.equals(request.getPermitirPedidoSemPagamento()));
        policy.setPermitirPosPago(Boolean.TRUE.equals(request.getPermitirPosPago()));
        policy.setPermitirCash(Boolean.TRUE.equals(request.getPermitirCash()));
        policy.setPermitirPagamentoNaEntrega(Boolean.TRUE.equals(request.getPermitirPagamentoNaEntrega()));
        policy.setTempoExpiracaoPedidoPendentePagamentoMinutos(request.getTempoExpiracaoPedidoPendentePagamentoMinutos());
        policy.setComportamentoPedidoNaoPago(request.getComportamentoPedidoNaoPago());
    }

    private TenantPagamentoPolicyResponse toResponse(TenantOperacaoPolicy p) {
        return TenantPagamentoPolicyResponse.builder()
                .tenantId(p.getTenant() != null ? p.getTenant().getId() : null)
                .pagamentoObrigatorioAntesDoPedido(p.isPagamentoObrigatorioAntesDoPedido())
                .permitirPedidoSemPagamento(p.isPermitirPedidoSemPagamento())
                .permitirPosPago(p.isPermitirPosPago())
                .permitirCash(p.isPermitirCash())
                .permitirPagamentoNaEntrega(p.isPermitirPagamentoNaEntrega())
                .tempoExpiracaoPedidoPendentePagamentoMinutos(p.getTempoExpiracaoPedidoPendentePagamentoMinutos())
                .comportamentoPedidoNaoPago(p.getComportamentoPedidoNaoPago())
                .configuradoEm(p.getUpdatedAt() != null ? p.getUpdatedAt() : p.getCreatedAt())
                .build();
    }

    private Long requireTenantId(String operacao) {
        Long tenantId = TenantContextHolder.require().tenantId();
        if (tenantId == null) {
            throw new BusinessException("TenantContext ausente para " + operacao + ".");
        }
        tenantGuard.assertTenantActive(tenantId);
        tenantGuard.assertCurrentUserBelongsToTenant(tenantId);
        return tenantId;
    }
}

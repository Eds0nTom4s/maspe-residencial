package com.restaurante.service.operacional;

import com.restaurante.exception.OperationalCapabilityDisabledException;
import com.restaurante.exception.ResourceNotFoundException;
import com.restaurante.model.entity.Pedido;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.TenantOperacaoPolicy;
import com.restaurante.model.entity.TenantOperationalModulesConfig;
import com.restaurante.model.enums.TenantTipo;
import com.restaurante.repository.TenantOperacaoPolicyRepository;
import com.restaurante.repository.TenantOperationalModulesConfigRepository;
import com.restaurante.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OperationalCapabilitiesPolicy {

    private final TenantRepository tenantRepository;
    private final TenantOperacaoPolicyRepository operacaoPolicyRepository;
    private final TenantOperationalModulesConfigRepository modulesRepository;
    private final OperationalTemplatePolicy templatePolicy;

    @Transactional(readOnly = true)
    public Capabilities resolve(Long tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Recurso não encontrado."));
        return resolve(tenant);
    }

    @Transactional(readOnly = true)
    public Capabilities resolve(Tenant tenant) {
        if (tenant == null || tenant.getId() == null) {
            throw new ResourceNotFoundException("Recurso não encontrado.");
        }

        boolean ponto = isPontoOperation(tenant);
        TenantOperacaoPolicy operacao = operacaoPolicyRepository.findByTenantId(tenant.getId()).orElse(null);
        TenantOperationalModulesConfig modules = modulesRepository.findByTenantId(tenant.getId()).orElse(null);

        boolean productionEnabled = operacao != null ? operacao.isProductionEnabled() : !ponto;
        boolean operationKdsEnabled = operacao != null ? operacao.isKdsEnabled() : !ponto;
        boolean moduleKdsEnabled = modules != null ? modules.isKdsEnabled() : !ponto;
        boolean kdsEnabled = productionEnabled && operationKdsEnabled && moduleKdsEnabled;

        return new Capabilities(productionEnabled, kdsEnabled, ponto);
    }

    public boolean isProductionEnabled(Long tenantId) {
        return resolve(tenantId).productionEnabled();
    }

    public boolean isKdsEnabled(Long tenantId) {
        return resolve(tenantId).kdsEnabled();
    }

    public boolean canUseProduction(Pedido pedido) {
        return pedido != null
                && pedido.getTenant() != null
                && resolve(pedido.getTenant()).productionEnabled();
    }

    public boolean canEnterKds(Pedido pedido) {
        return pedido != null
                && pedido.getTenant() != null
                && resolve(pedido.getTenant()).kdsEnabled();
    }

    public boolean canDeliverWithoutReady(Pedido pedido) {
        if (pedido == null || pedido.getTenant() == null) {
            return false;
        }
        Capabilities capabilities = resolve(pedido.getTenant());
        return capabilities.pontoOperation() && !capabilities.productionEnabled();
    }

    public void assertKdsEnabled(Long tenantId) {
        if (!isKdsEnabled(tenantId)) {
            throw OperationalCapabilityDisabledException.kds();
        }
    }

    public void assertProductionEnabled(Long tenantId) {
        if (!isProductionEnabled(tenantId)) {
            throw OperationalCapabilityDisabledException.production();
        }
    }

    public void assertPedidoCanUseKds(Pedido pedido) {
        if (!canEnterKds(pedido)) {
            throw OperationalCapabilityDisabledException.kds();
        }
    }

    public void assertPedidoCanUseProduction(Pedido pedido) {
        if (!canUseProduction(pedido)) {
            throw OperationalCapabilityDisabledException.production();
        }
    }

    private boolean isPontoOperation(Tenant tenant) {
        String templateCode = templatePolicy.normalizeTemplateCode(
                tenant.getTemplateCode(),
                tenant.getTemplateVersion()
        );
        return templatePolicy.isPontoTemplate(templateCode)
                || tenant.getTipo() == TenantTipo.VENDEDOR_RUA
                || tenant.getTipo() == TenantTipo.LOJA;
    }

    public record Capabilities(boolean productionEnabled, boolean kdsEnabled, boolean pontoOperation) {
    }
}

package com.restaurante.fiscal.service;

import com.restaurante.exception.BusinessException;
import com.restaurante.dto.request.UpsertTenantTaxPolicyRequest;
import com.restaurante.fiscal.repository.TaxRateRepository;
import com.restaurante.fiscal.repository.TenantTaxPolicyRepository;
import com.restaurante.model.entity.TaxRate;
import com.restaurante.model.entity.TenantTaxPolicy;
import com.restaurante.model.enums.OperationalEntityType;
import com.restaurante.model.enums.OperationalEventType;
import com.restaurante.model.enums.OperationalOrigem;
import com.restaurante.model.enums.TenantUserRole;
import com.restaurante.repository.TenantRepository;
import com.restaurante.security.tenant.TenantContext;
import com.restaurante.security.tenant.TenantGuard;
import com.restaurante.service.operacional.OperationalEventLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class TenantTaxPolicyService {

    private final TenantGuard tenantGuard;
    private final TenantRepository tenantRepository;
    private final TenantTaxPolicyRepository policyRepository;
    private final TaxRateRepository taxRateRepository;
    private final OperationalEventLogService operationalEventLogService;

    @Transactional(readOnly = true)
    public Page<TenantTaxPolicy> list(Pageable pageable) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE);
        TenantContext ctx = tenantGuard.requireContext();
        return policyRepository.listByTenant(ctx.tenantId(), null, pageable);
    }

    @Transactional
    public TenantTaxPolicy create(UpsertTenantTaxPolicyRequest request, String ip, String userAgent) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE);
        TenantContext ctx = tenantGuard.requireContext();
        if (request == null) throw new BusinessException("request é obrigatório.");

        TenantTaxPolicy p = new TenantTaxPolicy();
        p.setTenant(tenantRepository.findById(ctx.tenantId()).orElseThrow(() -> new BusinessException("Tenant não encontrado.")));
        apply(p, request);
        p = policyRepository.save(p);

        operationalEventLogService.logGeneric(
                OperationalEventType.TENANT_TAX_POLICY_CREATED,
                OperationalEntityType.TENANT_TAX_POLICY,
                p.getId(),
                OperationalOrigem.SYSTEM,
                "Política fiscal criada",
                Map.of("policyId", p.getId(), "tenantId", ctx.tenantId(), "status", p.getStatus().name(), "fiscalRegime", p.getFiscalRegime().name()),
                ip,
                userAgent
        );

        return p;
    }

    @Transactional
    public TenantTaxPolicy update(Long policyId, UpsertTenantTaxPolicyRequest request, String ip, String userAgent) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE);
        TenantContext ctx = tenantGuard.requireContext();
        if (policyId == null) throw new BusinessException("policyId é obrigatório.");
        if (request == null) throw new BusinessException("request é obrigatório.");

        TenantTaxPolicy p = policyRepository.findById(policyId).orElseThrow(() -> new BusinessException("Política fiscal não encontrada."));
        tenantGuard.assertResourceBelongsToTenant(p.getTenant().getId());
        apply(p, request);
        p = policyRepository.save(p);

        operationalEventLogService.logGeneric(
                OperationalEventType.TENANT_TAX_POLICY_UPDATED,
                OperationalEntityType.TENANT_TAX_POLICY,
                p.getId(),
                OperationalOrigem.SYSTEM,
                "Política fiscal atualizada",
                Map.of("policyId", p.getId(), "tenantId", ctx.tenantId(), "status", p.getStatus().name()),
                ip,
                userAgent
        );

        return p;
    }

    private void apply(TenantTaxPolicy p, UpsertTenantTaxPolicyRequest request) {
        p.setName(request.getName().trim());
        p.setFiscalRegime(request.getFiscalRegime());
        p.setPricesIncludeTax(request.isPricesIncludeTax());
        p.setAllowTaxExemptItems(request.isAllowTaxExemptItems());
        p.setRequireTaxDocumentOnPayment(request.isRequireTaxDocumentOnPayment());
        p.setStatus(request.getStatus());
        p.setEffectiveFrom(request.getEffectiveFrom());
        p.setEffectiveTo(request.getEffectiveTo());

        if (request.getDefaultTaxRateId() != null) {
            TaxRate r = taxRateRepository.findById(request.getDefaultTaxRateId()).orElseThrow(() -> new BusinessException("TaxRate não encontrado."));
            p.setDefaultTaxRate(r);
        } else {
            p.setDefaultTaxRate(null);
        }
    }
}


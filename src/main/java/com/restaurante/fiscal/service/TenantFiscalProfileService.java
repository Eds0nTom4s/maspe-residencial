package com.restaurante.fiscal.service;

import com.restaurante.exception.BusinessException;
import com.restaurante.dto.request.UpsertTenantFiscalProfileRequest;
import com.restaurante.fiscal.repository.TenantFiscalProfileRepository;
import com.restaurante.fiscal.repository.TenantTaxPolicyRepository;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.TenantFiscalProfile;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class TenantFiscalProfileService {

    private final TenantGuard tenantGuard;
    private final TenantRepository tenantRepository;
    private final TenantFiscalProfileRepository profileRepository;
    private final TenantTaxPolicyRepository taxPolicyRepository;
    private final OperationalEventLogService operationalEventLogService;

    @Transactional(readOnly = true)
    public TenantFiscalProfile getCurrent() {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE);
        TenantContext ctx = tenantGuard.requireContext();
        return profileRepository.findByTenantId(ctx.tenantId()).orElse(null);
    }

    @Transactional
    public TenantFiscalProfile upsert(UpsertTenantFiscalProfileRequest request, String ip, String userAgent) {
        if (request == null) throw new BusinessException("request é obrigatório.");
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE);
        TenantContext ctx = tenantGuard.requireContext();

        Tenant tenant = tenantRepository.findById(ctx.tenantId()).orElseThrow(() -> new BusinessException("Tenant não encontrado."));
        TenantFiscalProfile p = profileRepository.findByTenantId(ctx.tenantId()).orElse(null);
        boolean created = false;
        if (p == null) {
            p = new TenantFiscalProfile();
            p.setTenant(tenant);
            created = true;
        }
        p.setStatus(request.getStatus());
        p.setFiscalRegime(request.getFiscalRegime());
        p.setTaxpayerNumber(trimToNull(request.getTaxpayerNumber()));
        p.setLegalName(trimToNull(request.getLegalName()));
        p.setCommercialName(trimToNull(request.getCommercialName()));
        if (request.getCountryCode() != null && !request.getCountryCode().isBlank()) {
            p.setCountryCode(request.getCountryCode().trim());
        }
        p.setProvince(trimToNull(request.getProvince()));
        p.setMunicipality(trimToNull(request.getMunicipality()));
        p.setAddress(trimToNull(request.getAddress()));
        p.setInvoiceRequired(request.isInvoiceRequired());
        p.setFiscalDocumentEnabled(request.isFiscalDocumentEnabled());

        if (request.getDefaultTaxPolicyId() != null) {
            TenantTaxPolicy policy = taxPolicyRepository.findById(request.getDefaultTaxPolicyId())
                    .orElseThrow(() -> new BusinessException("defaultTaxPolicyId não encontrado."));
            tenantGuard.assertResourceBelongsToTenant(policy.getTenant().getId());
            p.setDefaultTaxPolicy(policy);
        } else {
            p.setDefaultTaxPolicy(null);
        }

        p = profileRepository.save(p);

        operationalEventLogService.logGeneric(
                created ? OperationalEventType.TENANT_FISCAL_PROFILE_CREATED : OperationalEventType.TENANT_FISCAL_PROFILE_UPDATED,
                OperationalEntityType.TENANT_FISCAL_PROFILE,
                p.getId(),
                OperationalOrigem.SYSTEM,
                created ? "Perfil fiscal criado" : "Perfil fiscal atualizado",
                Map.of(
                        "fiscalProfileId", p.getId(),
                        "tenantId", tenant.getId(),
                        "status", p.getStatus().name(),
                        "fiscalRegime", p.getFiscalRegime().name(),
                        "fiscalDocumentEnabled", p.isFiscalDocumentEnabled()
                ),
                ip,
                userAgent
        );

        return p;
    }

    private static String trimToNull(String v) {
        if (v == null) return null;
        String t = v.trim();
        return t.isEmpty() ? null : t;
    }
}

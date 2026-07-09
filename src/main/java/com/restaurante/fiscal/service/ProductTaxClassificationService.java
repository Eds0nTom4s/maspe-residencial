package com.restaurante.fiscal.service;

import com.restaurante.exception.BusinessException;
import com.restaurante.dto.request.UpsertProductTaxClassificationRequest;
import com.restaurante.fiscal.repository.ProductTaxClassificationRepository;
import com.restaurante.fiscal.repository.TaxRateRepository;
import com.restaurante.model.entity.ProductTaxClassification;
import com.restaurante.model.entity.Produto;
import com.restaurante.model.entity.TaxRate;
import com.restaurante.model.enums.OperationalEntityType;
import com.restaurante.model.enums.OperationalEventType;
import com.restaurante.model.enums.OperationalOrigem;
import com.restaurante.model.enums.TenantUserRole;
import com.restaurante.repository.ProdutoRepository;
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
public class ProductTaxClassificationService {

    private final TenantGuard tenantGuard;
    private final TenantRepository tenantRepository;
    private final ProductTaxClassificationRepository classificationRepository;
    private final ProdutoRepository produtoRepository;
    private final TaxRateRepository taxRateRepository;
    private final OperationalEventLogService operationalEventLogService;

    @Transactional(readOnly = true)
    public Page<ProductTaxClassification> list(Pageable pageable) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE);
        TenantContext ctx = tenantGuard.requireContext();
        return classificationRepository.listByTenant(ctx.tenantId(), null, pageable);
    }

    @Transactional
    public ProductTaxClassification createOrUpdate(UpsertProductTaxClassificationRequest request, String ip, String userAgent) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE);
        TenantContext ctx = tenantGuard.requireContext();
        if (request == null) throw new BusinessException("request é obrigatório.");
        if (request.getProductId() == null) throw new BusinessException("productId é obrigatório.");

        Produto prod = produtoRepository.findById(request.getProductId()).orElseThrow(() -> new BusinessException("Produto não encontrado."));
        tenantGuard.assertResourceBelongsToTenant(prod.getTenant().getId());

        ProductTaxClassification existing = classificationRepository.findActiveEffectiveByTenantAndProduct(ctx.tenantId(), prod.getId(), null).orElse(null);
        boolean created = false;
        ProductTaxClassification c = existing;
        if (c == null) {
            c = new ProductTaxClassification();
            c.setTenant(tenantRepository.findById(ctx.tenantId()).orElseThrow(() -> new BusinessException("Tenant não encontrado.")));
            c.setProduct(prod);
            created = true;
        }

        if (request.getTaxRateId() != null) {
            TaxRate r = taxRateRepository.findById(request.getTaxRateId()).orElseThrow(() -> new BusinessException("TaxRate não encontrado."));
            c.setTaxRate(r);
        } else {
            c.setTaxRate(null);
        }
        c.setTaxCategory(request.getTaxCategory());
        c.setExemptReason(trimToNull(request.getExemptReason()));
        c.setEffectiveFrom(request.getEffectiveFrom());
        c.setEffectiveTo(request.getEffectiveTo());
        c.setStatus(request.getStatus());
        c = classificationRepository.save(c);

        operationalEventLogService.logGeneric(
                created ? OperationalEventType.PRODUCT_TAX_CLASSIFICATION_CREATED : OperationalEventType.PRODUCT_TAX_CLASSIFICATION_UPDATED,
                OperationalEntityType.PRODUCT_TAX_CLASSIFICATION,
                c.getId(),
                OperationalOrigem.SYSTEM,
                created ? "Classificação fiscal criada" : "Classificação fiscal atualizada",
                Map.of(
                        "classificationId", c.getId(),
                        "tenantId", ctx.tenantId(),
                        "productId", prod.getId(),
                        "taxCategory", c.getTaxCategory().name(),
                        "status", c.getStatus().name()
                ),
                ip,
                userAgent
        );

        return c;
    }

    private static String trimToNull(String v) {
        if (v == null) return null;
        String t = v.trim();
        return t.isEmpty() ? null : t;
    }
}


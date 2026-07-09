package com.restaurante.delivery.service;

import com.restaurante.delivery.dto.request.UpdateProductDeliveryPolicyRequest;
import com.restaurante.delivery.dto.response.ProductDeliveryPolicyResponse;
import com.restaurante.delivery.repository.ProductDeliveryPolicyRepository;
import com.restaurante.exception.BusinessException;
import com.restaurante.model.entity.ProductDeliveryPolicy;
import com.restaurante.model.entity.Produto;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.enums.OperationalEntityType;
import com.restaurante.model.enums.OperationalEventType;
import com.restaurante.model.enums.OperationalOrigem;
import com.restaurante.model.enums.ProductDeliveryPolicyStatus;
import com.restaurante.repository.ProdutoRepository;
import com.restaurante.repository.TenantRepository;
import com.restaurante.service.operacional.OperationalEventLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ProductDeliveryPolicyService {

    private final TenantRepository tenantRepository;
    private final ProdutoRepository produtoRepository;
    private final ProductDeliveryPolicyRepository repository;
    private final OperationalEventLogService operationalEventLogService;

    @Transactional
    public ProductDeliveryPolicyResponse upsert(Long tenantId, Long productId, UpdateProductDeliveryPolicyRequest req) {
        if (tenantId == null || productId == null) throw new BusinessException("PRODUCT_NOT_DELIVERY_ELIGIBLE");
        Tenant tenant = tenantRepository.findById(tenantId).orElseThrow(() -> new BusinessException("TENANT_NOT_FOUND"));
        Produto produto = produtoRepository.findById(productId).orElseThrow(() -> new BusinessException("PRODUCT_NOT_FOUND"));
        if (produto.getTenant() == null || !tenantId.equals(produto.getTenant().getId())) throw new BusinessException("DELIVERY_FORBIDDEN");

        ProductDeliveryPolicy p = repository.findByTenantIdAndProduct_Id(tenantId, productId).orElse(null);
        if (p == null) {
            p = new ProductDeliveryPolicy();
            p.setTenant(tenant);
            p.setProduct(produto);
            p.setStatus(ProductDeliveryPolicyStatus.ACTIVE);
        }
        p.setDeliveryEligible(req != null && req.isDeliveryEligible());
        p.setFragile(req != null && req.isFragile());
        p.setRequiresCooling(req != null && req.isRequiresCooling());
        p.setMaxDeliveryDistanceKm(req != null ? req.getMaxDeliveryDistanceKm() : null);
        p.setEstimatedPackageWeight(req != null ? req.getEstimatedPackageWeight() : null);
        p.setPackageSize(req != null ? req.getPackageSize() : null);
        p.setAllowMotorbikeDelivery(req == null || req.isAllowMotorbikeDelivery());
        p.setAllowCarDelivery(req == null || req.isAllowCarDelivery());
        p.setNotes(req != null ? req.getNotes() : null);
        p = repository.save(p);

        operationalEventLogService.logGenericForTenant(
                tenantId,
                OperationalEventType.PRODUCT_DELIVERY_POLICY_UPDATED,
                OperationalEntityType.PRODUCT_DELIVERY_POLICY,
                p.getId(),
                OperationalOrigem.TENANT_ADMIN,
                "ProductDeliveryPolicy atualizado",
                Map.of(
                        "tenantId", tenantId,
                        "productId", productId,
                        "deliveryEligible", p.isDeliveryEligible()
                ),
                null,
                null
        );

        return map(p);
    }

    @Transactional(readOnly = true)
    public List<ProductDeliveryPolicyResponse> list(Long tenantId) {
        if (tenantId == null) return List.of();
        return repository.findByTenantIdOrderByIdDesc(tenantId).stream().limit(500).map(ProductDeliveryPolicyService::map).toList();
    }

    @Transactional(readOnly = true)
    public ProductDeliveryPolicy getOrNull(Long tenantId, Long productId) {
        if (tenantId == null || productId == null) return null;
        return repository.findByTenantIdAndProduct_Id(tenantId, productId).orElse(null);
    }

    private static ProductDeliveryPolicyResponse map(ProductDeliveryPolicy p) {
        return new ProductDeliveryPolicyResponse(
                p.getId(),
                p.getProduct() != null ? p.getProduct().getId() : null,
                p.isDeliveryEligible(),
                p.isFragile(),
                p.isRequiresCooling(),
                p.getMaxDeliveryDistanceKm(),
                p.getEstimatedPackageWeight(),
                p.getPackageSize(),
                p.isAllowMotorbikeDelivery(),
                p.isAllowCarDelivery(),
                p.getNotes(),
                p.getStatus()
        );
    }
}


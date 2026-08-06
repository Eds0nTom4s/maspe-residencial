package com.restaurante.android.foundation.identity;

import com.restaurante.model.entity.CategoriaProduto;
import com.restaurante.model.entity.Pedido;
import com.restaurante.model.entity.Produto;
import com.restaurante.model.entity.Tenant;
import com.restaurante.repository.CategoriaProdutoRepository;
import com.restaurante.repository.PedidoRepository;
import com.restaurante.repository.ProdutoRepository;
import com.restaurante.repository.TenantRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves Android public identifiers without accepting a tenant override.
 * Child lookups are always scoped by the tenant derived from the merchant UUID.
 */
@Service
@Transactional(readOnly = true)
public class AndroidPublicIdentityLookupService {

    private final TenantRepository tenantRepository;
    private final ProdutoRepository produtoRepository;
    private final CategoriaProdutoRepository categoriaRepository;
    private final PedidoRepository pedidoRepository;

    public AndroidPublicIdentityLookupService(
            TenantRepository tenantRepository,
            ProdutoRepository produtoRepository,
            CategoriaProdutoRepository categoriaRepository,
            PedidoRepository pedidoRepository) {
        this.tenantRepository = tenantRepository;
        this.produtoRepository = produtoRepository;
        this.categoriaRepository = categoriaRepository;
        this.pedidoRepository = pedidoRepository;
    }

    public Optional<MerchantIdentityContext> resolveMerchant(UUID merchantPublicId) {
        if (merchantPublicId == null) {
            return Optional.empty();
        }
        return tenantRepository.findByMerchantPublicId(merchantPublicId).map(this::toContext);
    }

    public Optional<Produto> resolveProduct(UUID merchantPublicId, UUID productPublicId) {
        if (productPublicId == null) {
            return Optional.empty();
        }
        return derivedTenantId(merchantPublicId)
                .flatMap(tenantId -> produtoRepository.findByTenantIdAndPublicId(tenantId, productPublicId));
    }

    public Optional<CategoriaProduto> resolveCategory(UUID merchantPublicId, UUID categoryPublicId) {
        if (categoryPublicId == null) {
            return Optional.empty();
        }
        return derivedTenantId(merchantPublicId)
                .flatMap(tenantId -> categoriaRepository.findByTenantIdAndPublicId(tenantId, categoryPublicId));
    }

    public Optional<Pedido> resolveOrder(UUID merchantPublicId, UUID orderPublicId) {
        if (orderPublicId == null) {
            return Optional.empty();
        }
        return derivedTenantId(merchantPublicId)
                .flatMap(tenantId -> pedidoRepository.findByTenantIdAndPublicId(tenantId, orderPublicId));
    }

    private Optional<Long> derivedTenantId(UUID merchantPublicId) {
        return resolveMerchant(merchantPublicId).map(MerchantIdentityContext::tenantId);
    }

    private MerchantIdentityContext toContext(Tenant tenant) {
        Long businessAccountId = tenant.getBusinessAccount() == null
                ? null
                : tenant.getBusinessAccount().getId();
        return new MerchantIdentityContext(
                tenant.getMerchantPublicId(),
                tenant.getId(),
                businessAccountId,
                tenant.getSlug(),
                tenant.getEstado());
    }
}

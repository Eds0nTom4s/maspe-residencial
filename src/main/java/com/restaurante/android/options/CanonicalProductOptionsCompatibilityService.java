package com.restaurante.android.options;

import com.restaurante.android.foundation.money.AndroidMoneyConverter;
import com.restaurante.android.options.dto.AndroidProductOptionGroupProjection;
import com.restaurante.android.options.dto.AndroidProductOptionProjection;
import com.restaurante.model.entity.ProductOption;
import com.restaurante.model.entity.ProductOptionGroup;
import com.restaurante.model.entity.Produto;
import com.restaurante.repository.ProductOptionGroupRepository;
import com.restaurante.repository.ProductOptionRepository;
import com.restaurante.repository.VariacaoProdutoRepository;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Separates genuine no-option products from legacy products that the frozen Android schema
 * cannot represent. No legacy row is converted or adapted here.
 */
@Service
public class CanonicalProductOptionsCompatibilityService {
    private final ProductOptionGroupRepository groups;
    private final ProductOptionRepository options;
    private final VariacaoProdutoRepository legacyVariations;

    public CanonicalProductOptionsCompatibilityService(ProductOptionGroupRepository groups,
                                                        ProductOptionRepository options,
                                                        VariacaoProdutoRepository legacyVariations) {
        this.groups = groups;
        this.options = options;
        this.legacyVariations = legacyVariations;
    }

    @Transactional(readOnly = true)
    public CanonicalProductOptionsCompatibility compatibilityOf(Produto product) {
        requirePersisted(product);
        List<ProductOptionGroup> canonicalGroups = groups.findByTenantIdAndProdutoIdOrderBySortOrderAscPublicIdAsc(
                product.getTenant().getId(), product.getId());
        if (!canonicalGroups.isEmpty()) {
            return validationErrors(product, canonicalGroups).isEmpty()
                    ? CanonicalProductOptionsCompatibility.CANONICAL_OPTIONS
                    : CanonicalProductOptionsCompatibility.CANONICAL_OPTIONS_INVALID;
        }
        return legacyVariations.findByProdutoId(product.getId()).isEmpty()
                ? CanonicalProductOptionsCompatibility.NO_OPTIONS
                : CanonicalProductOptionsCompatibility.LEGACY_OPTIONS_UNMIGRATED;
    }

    @Transactional(readOnly = true)
    public List<AndroidProductOptionGroupProjection> projectForAndroid(Produto product) {
        CanonicalProductOptionsCompatibility state = compatibilityOf(product);
        if (state == CanonicalProductOptionsCompatibility.NO_OPTIONS) return List.of();
        if (state != CanonicalProductOptionsCompatibility.CANONICAL_OPTIONS) {
            throw new ProductOptionsNotProjectableException(state,
                    "Product options are not representable by the canonical Android schema: " + state);
        }
        Long tenantId = product.getTenant().getId();
        List<ProductOptionGroup> activeGroups = groups.findByTenantIdAndProdutoIdAndActiveTrueOrderBySortOrderAscPublicIdAsc(
                tenantId, product.getId());
        if (activeGroups.isEmpty()) return List.of();
        Map<Long, List<ProductOption>> optionsByGroup = options
                .findByTenantIdAndOptionGroupIdInAndActiveTrueOrderBySortOrderAscPublicIdAsc(
                        tenantId, activeGroups.stream().map(ProductOptionGroup::getId).toList())
                .stream().collect(Collectors.groupingBy(option -> option.getOptionGroup().getId()));
        return activeGroups.stream().map(group -> new AndroidProductOptionGroupProjection(
                group.getPublicId(), group.getName(), group.getMinSelections(), group.getMaxSelections(),
                group.getMinSelections() > 0, group.getMaxSelections() == 1, group.getSortOrder(),
                optionsByGroup.getOrDefault(group.getId(), List.of()).stream()
                        .map(option -> new AndroidProductOptionProjection(option.getPublicId(), option.getName(),
                                AndroidMoneyConverter.toContract(option.getAdditionalPrice(), "AOA"),
                                Boolean.TRUE.equals(option.getAvailable()),
                                Boolean.TRUE.equals(option.getDefaultSelected()), option.getSortOrder()))
                        .toList())).toList();
    }

    @Transactional(readOnly = true)
    public List<String> validationErrors(Produto product) {
        requirePersisted(product);
        return validationErrors(product, groups.findByTenantIdAndProdutoIdOrderBySortOrderAscPublicIdAsc(
                product.getTenant().getId(), product.getId()));
    }

    private List<String> validationErrors(Produto product, List<ProductOptionGroup> canonicalGroups) {
        if (canonicalGroups.isEmpty()) return List.of();
        Long tenantId = product.getTenant().getId();
        Map<Long, List<ProductOption>> allOptionsByGroup = options.findByTenantIdAndOptionGroupIdInOrderBySortOrderAscPublicIdAsc(
                        tenantId, canonicalGroups.stream().map(ProductOptionGroup::getId).toList())
                .stream().collect(Collectors.groupingBy(option -> option.getOptionGroup().getId()));
        java.util.ArrayList<String> errors = new java.util.ArrayList<>();
        for (ProductOptionGroup group : canonicalGroups) {
            if (!sameTenant(group.getTenant().getId(), tenantId) || !group.getProduto().getId().equals(product.getId())) {
                errors.add("GROUP_OWNERSHIP");
                continue;
            }
            if (group.getMinSelections() < 0 || group.getMaxSelections() < 1
                    || group.getMinSelections() > group.getMaxSelections() || group.getSortOrder() < 0) {
                errors.add("GROUP_RULES");
            }
            List<ProductOption> groupOptions = allOptionsByGroup.getOrDefault(group.getId(), List.of());
            if (Boolean.TRUE.equals(group.getActive()) && groupOptions.isEmpty()) errors.add("ACTIVE_GROUP_WITHOUT_OPTIONS");
            long availableCount = groupOptions.stream()
                    .filter(option -> Boolean.TRUE.equals(option.getActive()) && Boolean.TRUE.equals(option.getAvailable()))
                    .count();
            long defaultCount = groupOptions.stream().filter(option -> Boolean.TRUE.equals(option.getDefaultSelected())).count();
            if (groupOptions.stream().anyMatch(option -> !sameTenant(option.getTenant().getId(), tenantId))) errors.add("OPTION_OWNERSHIP");
            if (groupOptions.stream().anyMatch(option -> option.getSortOrder() < 0
                    || option.getAdditionalPrice() == null || option.getAdditionalPrice().signum() < 0)) errors.add("OPTION_RULES");
            if (groupOptions.stream().anyMatch(option -> Boolean.TRUE.equals(option.getDefaultSelected())
                    && (!Boolean.TRUE.equals(option.getActive()) || !Boolean.TRUE.equals(option.getAvailable())))) {
                errors.add("INVALID_DEFAULT");
            }
            if (defaultCount > group.getMaxSelections()) errors.add("TOO_MANY_DEFAULTS");
            if (Boolean.TRUE.equals(group.getActive()) && group.getMinSelections() > availableCount) errors.add("MIN_EXCEEDS_AVAILABLE");
        }
        return List.copyOf(errors);
    }

    private boolean sameTenant(Long left, Long right) { return left != null && left.equals(right); }

    private void requirePersisted(Produto product) {
        if (product == null || product.getId() == null || product.getTenant() == null || product.getTenant().getId() == null) {
            throw new IllegalArgumentException("A persisted tenant-scoped product is required.");
        }
    }
}

package com.restaurante.android.options;

import com.restaurante.model.entity.ProductOption;
import com.restaurante.model.entity.ProductOptionGroup;
import com.restaurante.model.entity.Produto;
import com.restaurante.repository.ProductOptionGroupRepository;
import com.restaurante.repository.ProductOptionRepository;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Validates canonical selections without creating a quote, order, or payment side effect. */
@Service
public class ProductOptionSelectionValidator {
    private final CanonicalProductOptionsCompatibilityService compatibility;
    private final ProductOptionGroupRepository groups;
    private final ProductOptionRepository options;

    public ProductOptionSelectionValidator(CanonicalProductOptionsCompatibilityService compatibility,
                                           ProductOptionGroupRepository groups, ProductOptionRepository options) {
        this.compatibility = compatibility;
        this.groups = groups;
        this.options = options;
    }

    @Transactional(readOnly = true)
    public ProductOptionSelection validate(Produto product, Collection<UUID> selectedOptionPublicIds) {
        CanonicalProductOptionsCompatibility state = compatibility.compatibilityOf(product);
        if (state != CanonicalProductOptionsCompatibility.NO_OPTIONS && state != CanonicalProductOptionsCompatibility.CANONICAL_OPTIONS) {
            throw new ProductOptionSelectionException(ProductOptionSelectionException.Reason.OPTIONS_NOT_PROJECTABLE);
        }
        List<UUID> ids = selectedOptionPublicIds == null ? List.of() : List.copyOf(selectedOptionPublicIds);
        if (new HashSet<>(ids).size() != ids.size()) {
            throw new ProductOptionSelectionException(ProductOptionSelectionException.Reason.DUPLICATE_OPTION);
        }
        Long tenantId = product.getTenant().getId();
        List<ProductOptionGroup> activeGroups = groups.findByTenantIdAndProdutoIdAndActiveTrueOrderBySortOrderAscPublicIdAsc(tenantId, product.getId());
        Map<UUID, ProductOption> productOptions = options.findByTenantIdAndOptionGroupIdInOrderBySortOrderAscPublicIdAsc(
                        tenantId, activeGroups.stream().map(ProductOptionGroup::getId).toList()).stream()
                .collect(Collectors.toMap(ProductOption::getPublicId, option -> option));
        List<ProductOption> selected = ids.stream().map(id -> {
            ProductOption option = productOptions.get(id);
            if (option == null) {
                ProductOption globallyKnown = options.findByPublicId(id).orElse(null);
                if (globallyKnown == null) {
                    throw new ProductOptionSelectionException(ProductOptionSelectionException.Reason.UNKNOWN_OPTION);
                }
                if (!globallyKnown.getTenant().getId().equals(tenantId)) {
                    throw new ProductOptionSelectionException(ProductOptionSelectionException.Reason.OTHER_TENANT);
                }
                if (!globallyKnown.getOptionGroup().getProduto().getId().equals(product.getId())) {
                    throw new ProductOptionSelectionException(ProductOptionSelectionException.Reason.OTHER_PRODUCT);
                }
                throw new ProductOptionSelectionException(ProductOptionSelectionException.Reason.OTHER_GROUP);
            }
            if (!Boolean.TRUE.equals(option.getActive())) throw new ProductOptionSelectionException(ProductOptionSelectionException.Reason.INACTIVE_OPTION);
            if (!Boolean.TRUE.equals(option.getAvailable())) throw new ProductOptionSelectionException(ProductOptionSelectionException.Reason.UNAVAILABLE_OPTION);
            return option;
        }).toList();
        for (ProductOptionGroup group : activeGroups) {
            long count = selected.stream().filter(option -> option.getOptionGroup().getId().equals(group.getId())).count();
            if (count < group.getMinSelections()) throw new ProductOptionSelectionException(ProductOptionSelectionException.Reason.REQUIRED_GROUP_UNSATISFIED);
            if (count > group.getMaxSelections()) throw new ProductOptionSelectionException(ProductOptionSelectionException.Reason.TOO_MANY_SELECTIONS);
        }
        BigDecimal contribution = selected.stream().map(ProductOption::getAdditionalPrice).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new ProductOptionSelection(List.copyOf(selected), contribution);
    }
}

package com.restaurante.service;

import com.restaurante.model.entity.ProductOption;
import com.restaurante.model.entity.ProductOptionGroup;
import com.restaurante.model.entity.Produto;
import com.restaurante.repository.ProductOptionGroupRepository;
import com.restaurante.repository.ProductOptionRepository;
import com.restaurante.repository.ProdutoRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Canonical transactional write boundary for product options. Version bumps are intentionally deferred to Catalog R2. */
@Service
public class CanonicalProductOptionsService {
    private final ProdutoRepository products;
    private final ProductOptionGroupRepository groups;
    private final ProductOptionRepository options;

    public CanonicalProductOptionsService(ProdutoRepository products, ProductOptionGroupRepository groups,
                                          ProductOptionRepository options) {
        this.products = products;
        this.groups = groups;
        this.options = options;
    }

    @Transactional
    public ProductOptionGroup createGroup(Long tenantId, Long productId, GroupCommand command) {
        Produto product = product(tenantId, productId);
        validateGroup(command);
        ProductOptionGroup group = new ProductOptionGroup();
        group.setTenant(product.getTenant());
        group.setProduto(product);
        apply(group, command);
        return groups.save(group);
    }

    @Transactional
    public ProductOptionGroup updateGroup(Long tenantId, Long groupId, GroupCommand command) {
        ProductOptionGroup group = group(tenantId, groupId);
        validateGroup(command);
        apply(group, command);
        return groups.save(group);
    }

    @Transactional
    public ProductOptionGroup setGroupActive(Long tenantId, Long groupId, boolean active) {
        ProductOptionGroup group = group(tenantId, groupId);
        group.setActive(active);
        return groups.save(group);
    }

    @Transactional
    public ProductOption createOption(Long tenantId, Long groupId, OptionCommand command) {
        ProductOptionGroup group = group(tenantId, groupId);
        validateOption(command);
        ProductOption option = new ProductOption();
        option.setTenant(group.getTenant());
        option.setOptionGroup(group);
        apply(option, command);
        return options.save(option);
    }

    @Transactional
    public ProductOption updateOption(Long tenantId, Long optionId, OptionCommand command) {
        ProductOption option = options.findByTenantIdAndId(tenantId, optionId)
                .orElseThrow(() -> new IllegalArgumentException("Canonical option not found for tenant."));
        validateOption(command);
        apply(option, command);
        return options.save(option);
    }

    @Transactional
    public ProductOption setOptionActive(Long tenantId, Long optionId, boolean active) {
        ProductOption option = options.findByTenantIdAndId(tenantId, optionId)
                .orElseThrow(() -> new IllegalArgumentException("Canonical option not found for tenant."));
        if (!active && Boolean.TRUE.equals(option.getDefaultSelected())) {
            throw new IllegalArgumentException("A default option must be deselected before deactivation.");
        }
        option.setActive(active);
        return options.save(option);
    }

    private Produto product(Long tenantId, Long productId) {
        return products.findByIdAndTenantId(productId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found for tenant."));
    }

    private ProductOptionGroup group(Long tenantId, Long groupId) {
        return groups.findByTenantIdAndId(tenantId, groupId)
                .orElseThrow(() -> new IllegalArgumentException("Canonical option group not found for tenant."));
    }

    private void apply(ProductOptionGroup group, GroupCommand command) {
        group.setName(command.name());
        group.setMinSelections(command.minSelections());
        group.setMaxSelections(command.maxSelections());
        group.setSortOrder(command.sortOrder());
        group.setActive(command.active());
    }

    private void apply(ProductOption option, OptionCommand command) {
        option.setName(command.name());
        option.setAdditionalPrice(command.additionalPrice().setScale(2, RoundingMode.UNNECESSARY));
        option.setAvailable(command.available());
        option.setDefaultSelected(command.defaultSelected());
        option.setSortOrder(command.sortOrder());
        option.setActive(command.active());
    }

    private void validateGroup(GroupCommand command) {
        Objects.requireNonNull(command, "Group command is required.");
        if (command.name() == null || command.name().isBlank() || command.minSelections() < 0
                || command.maxSelections() < 1 || command.minSelections() > command.maxSelections()
                || command.sortOrder() < 0) throw new IllegalArgumentException("Invalid canonical option group rules.");
    }

    private void validateOption(OptionCommand command) {
        Objects.requireNonNull(command, "Option command is required.");
        if (command.name() == null || command.name().isBlank() || command.additionalPrice() == null
                || command.additionalPrice().signum() < 0 || command.sortOrder() < 0) {
            throw new IllegalArgumentException("Invalid canonical option rules.");
        }
        try { command.additionalPrice().setScale(2, RoundingMode.UNNECESSARY); }
        catch (ArithmeticException exception) { throw new IllegalArgumentException("Additional price must use two decimal places.", exception); }
        if (command.defaultSelected() && (!command.active() || !command.available())) {
            throw new IllegalArgumentException("A default option must be active and available.");
        }
    }

    public record GroupCommand(String name, int minSelections, int maxSelections, int sortOrder, boolean active) { }
    public record OptionCommand(String name, BigDecimal additionalPrice, boolean available,
                                boolean defaultSelected, int sortOrder, boolean active) { }
}

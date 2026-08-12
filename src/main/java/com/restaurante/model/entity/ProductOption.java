package com.restaurante.model.entity;

import com.restaurante.android.foundation.identity.PublicIdSupport;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

/** Canonical selectable option belonging to one canonical product option group. */
@Entity
@Table(name = "product_options", uniqueConstraints = {
        @UniqueConstraint(name = "uq_product_options_public_id", columnNames = "public_id"),
        @UniqueConstraint(name = "uq_product_options_tenant_id_id", columnNames = {"tenant_id", "id"})
}, indexes = {
        @Index(name = "idx_product_options_tenant_group_active_order",
                columnList = "tenant_id, option_group_id, active, sort_order, public_id")
})
public class ProductOption extends BaseEntity {

    @Column(name = "public_id", nullable = false, updatable = false, columnDefinition = "uuid")
    private UUID publicId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "option_group_id", nullable = false)
    private ProductOptionGroup optionGroup;

    @NotBlank
    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @NotNull @DecimalMin("0.00")
    @Column(name = "additional_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal additionalPrice = BigDecimal.ZERO;

    @NotNull
    @Column(name = "available", nullable = false)
    private Boolean available = true;

    @NotNull
    @Column(name = "default_selected", nullable = false)
    private Boolean defaultSelected = false;

    @NotNull @Min(0)
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @NotNull
    @Column(name = "active", nullable = false)
    private Boolean active = true;

    @PrePersist
    private void ensurePublicId() {
        if (publicId == null) publicId = PublicIdSupport.generate();
    }

    public UUID getPublicId() { return publicId; }
    public Tenant getTenant() { return tenant; }
    public void setTenant(Tenant tenant) { this.tenant = tenant; }
    public ProductOptionGroup getOptionGroup() { return optionGroup; }
    public void setOptionGroup(ProductOptionGroup optionGroup) { this.optionGroup = optionGroup; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public BigDecimal getAdditionalPrice() { return additionalPrice; }
    public void setAdditionalPrice(BigDecimal additionalPrice) { this.additionalPrice = additionalPrice; }
    public Boolean getAvailable() { return available; }
    public void setAvailable(Boolean available) { this.available = available; }
    public Boolean getDefaultSelected() { return defaultSelected; }
    public void setDefaultSelected(Boolean defaultSelected) { this.defaultSelected = defaultSelected; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
}

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
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Canonical, tenant-scoped selection group for a product. */
@Entity
@Table(name = "product_option_groups", uniqueConstraints = {
        @UniqueConstraint(name = "uq_product_option_groups_public_id", columnNames = "public_id"),
        @UniqueConstraint(name = "uq_product_option_groups_tenant_id_id", columnNames = {"tenant_id", "id"})
}, indexes = {
        @Index(name = "idx_product_option_groups_tenant_product_active_order",
                columnList = "tenant_id, produto_id, active, sort_order, public_id")
})
public class ProductOptionGroup extends BaseEntity {

    @Column(name = "public_id", nullable = false, updatable = false, columnDefinition = "uuid")
    private UUID publicId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "produto_id", nullable = false)
    private Produto produto;

    @NotBlank
    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @NotNull @Min(0)
    @Column(name = "min_selections", nullable = false)
    private Integer minSelections = 0;

    @NotNull @Min(1)
    @Column(name = "max_selections", nullable = false)
    private Integer maxSelections = 1;

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
    public Produto getProduto() { return produto; }
    public void setProduto(Produto produto) { this.produto = produto; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Integer getMinSelections() { return minSelections; }
    public void setMinSelections(Integer minSelections) { this.minSelections = minSelections; }
    public Integer getMaxSelections() { return maxSelections; }
    public void setMaxSelections(Integer maxSelections) { this.maxSelections = maxSelections; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
}

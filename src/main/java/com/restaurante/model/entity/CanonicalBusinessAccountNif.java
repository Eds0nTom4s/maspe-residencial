package com.restaurante.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "canonical_business_account_nifs")
public class CanonicalBusinessAccountNif {
    @Id
    @Column(name = "normalized_nif", length = 30)
    private String normalizedNif;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "business_account_id", nullable = false, unique = true)
    private BusinessAccount businessAccount;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public String getNormalizedNif() { return normalizedNif; }
    public void setNormalizedNif(String value) { normalizedNif = value; }
    public BusinessAccount getBusinessAccount() { return businessAccount; }
    public void setBusinessAccount(BusinessAccount value) { businessAccount = value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime value) { createdAt = value; }
}

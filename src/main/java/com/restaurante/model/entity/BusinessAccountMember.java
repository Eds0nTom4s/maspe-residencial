package com.restaurante.model.entity;

import com.restaurante.model.enums.BusinessAccountMemberEstado;
import com.restaurante.model.enums.BusinessAccountRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "business_account_members", indexes = {
        @Index(name = "idx_business_account_member_account", columnList = "business_account_id"),
        @Index(name = "idx_business_account_member_user", columnList = "user_id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_business_account_member_unique", columnNames = {"business_account_id", "user_id"})
})
public class BusinessAccountMember extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "business_account_id", nullable = false)
    private BusinessAccount businessAccount;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 30)
    private BusinessAccountRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private BusinessAccountMemberEstado estado;

    public BusinessAccountMember() {
    }

    public BusinessAccount getBusinessAccount() {
        return businessAccount;
    }

    public void setBusinessAccount(BusinessAccount businessAccount) {
        this.businessAccount = businessAccount;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public BusinessAccountRole getRole() {
        return role;
    }

    public void setRole(BusinessAccountRole role) {
        this.role = role;
    }

    public BusinessAccountMemberEstado getEstado() {
        return estado;
    }

    public void setEstado(BusinessAccountMemberEstado estado) {
        this.estado = estado;
    }
}

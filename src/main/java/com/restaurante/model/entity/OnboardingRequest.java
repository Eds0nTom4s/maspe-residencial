package com.restaurante.model.entity;

import com.restaurante.model.enums.OnboardingPaymentStatus;
import com.restaurante.model.enums.OnboardingRequestStatus;
import com.restaurante.model.enums.TenantTipo;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "onboarding_requests", indexes = {
        @Index(name = "idx_onboarding_status", columnList = "status"),
        @Index(name = "idx_onboarding_business_account", columnList = "business_account_id"),
        @Index(name = "idx_onboarding_created_at", columnList = "created_at")
})
public class OnboardingRequest extends BaseEntity {

    @Column(name = "nome_solicitante", length = 160)
    private String nomeSolicitante;

    @Column(name = "telefone", length = 30)
    private String telefone;

    @Column(name = "email", length = 120)
    private String email;

    @Column(name = "nome_negocio", nullable = false, length = 160)
    private String nomeNegocio;

    @Column(name = "nif", length = 30)
    private String nif;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_negocio", length = 40)
    private TenantTipo tipoNegocio;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plano_id")
    private Plano plano;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "business_account_id")
    private BusinessAccount businessAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id")
    private Tenant tenant;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private OnboardingRequestStatus status = OnboardingRequestStatus.PENDENTE;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_pagamento", nullable = false, length = 30)
    private OnboardingPaymentStatus statusPagamento = OnboardingPaymentStatus.NAO_APLICAVEL;

    @Column(name = "valor", precision = 19, scale = 4)
    private BigDecimal valor;

    @Column(name = "moeda", length = 3)
    private String moeda = "AOA";

    @Column(name = "observacao", length = 500)
    private String observacao;

    @Column(name = "motivo_rejeicao", length = 500)
    private String motivoRejeicao;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "rejected_at")
    private LocalDateTime rejectedAt;

    @Column(name = "activated_at")
    private LocalDateTime activatedAt;

    @Column(name = "notification_status", length = 40)
    private String notificationStatus;

    @Column(name = "notification_message", length = 500)
    private String notificationMessage;

    public String getNomeSolicitante() {
        return nomeSolicitante;
    }

    public void setNomeSolicitante(String nomeSolicitante) {
        this.nomeSolicitante = nomeSolicitante;
    }

    public String getTelefone() {
        return telefone;
    }

    public void setTelefone(String telefone) {
        this.telefone = telefone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getNomeNegocio() {
        return nomeNegocio;
    }

    public void setNomeNegocio(String nomeNegocio) {
        this.nomeNegocio = nomeNegocio;
    }

    public String getNif() {
        return nif;
    }

    public void setNif(String nif) {
        this.nif = nif;
    }

    public TenantTipo getTipoNegocio() {
        return tipoNegocio;
    }

    public void setTipoNegocio(TenantTipo tipoNegocio) {
        this.tipoNegocio = tipoNegocio;
    }

    public Plano getPlano() {
        return plano;
    }

    public void setPlano(Plano plano) {
        this.plano = plano;
    }

    public BusinessAccount getBusinessAccount() {
        return businessAccount;
    }

    public void setBusinessAccount(BusinessAccount businessAccount) {
        this.businessAccount = businessAccount;
    }

    public Tenant getTenant() {
        return tenant;
    }

    public void setTenant(Tenant tenant) {
        this.tenant = tenant;
    }

    public OnboardingRequestStatus getStatus() {
        return status;
    }

    public void setStatus(OnboardingRequestStatus status) {
        this.status = status;
    }

    public OnboardingPaymentStatus getStatusPagamento() {
        return statusPagamento;
    }

    public void setStatusPagamento(OnboardingPaymentStatus statusPagamento) {
        this.statusPagamento = statusPagamento;
    }

    public BigDecimal getValor() {
        return valor;
    }

    public void setValor(BigDecimal valor) {
        this.valor = valor;
    }

    public String getMoeda() {
        return moeda;
    }

    public void setMoeda(String moeda) {
        this.moeda = moeda;
    }

    public String getObservacao() {
        return observacao;
    }

    public void setObservacao(String observacao) {
        this.observacao = observacao;
    }

    public String getMotivoRejeicao() {
        return motivoRejeicao;
    }

    public void setMotivoRejeicao(String motivoRejeicao) {
        this.motivoRejeicao = motivoRejeicao;
    }

    public LocalDateTime getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(LocalDateTime approvedAt) {
        this.approvedAt = approvedAt;
    }

    public LocalDateTime getRejectedAt() {
        return rejectedAt;
    }

    public void setRejectedAt(LocalDateTime rejectedAt) {
        this.rejectedAt = rejectedAt;
    }

    public LocalDateTime getActivatedAt() {
        return activatedAt;
    }

    public void setActivatedAt(LocalDateTime activatedAt) {
        this.activatedAt = activatedAt;
    }

    public String getNotificationStatus() {
        return notificationStatus;
    }

    public void setNotificationStatus(String notificationStatus) {
        this.notificationStatus = notificationStatus;
    }

    public String getNotificationMessage() {
        return notificationMessage;
    }

    public void setNotificationMessage(String notificationMessage) {
        this.notificationMessage = notificationMessage;
    }
}

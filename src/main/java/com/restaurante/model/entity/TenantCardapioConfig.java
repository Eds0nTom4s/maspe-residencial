package com.restaurante.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "tenant_cardapio_configs", indexes = {
        @Index(name = "idx_tenant_cardapio_config_tenant", columnList = "tenant_id", unique = true),
        @Index(name = "idx_tenant_cardapio_config_publicado", columnList = "cardapio_publicado")
})
public class TenantCardapioConfig extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false, unique = true)
    private Tenant tenant;

    @Column(name = "cardapio_publicado", nullable = false)
    private Boolean cardapioPublicado = false;

    @Column(name = "cardapio_publicado_em")
    private LocalDateTime cardapioPublicadoEm;

    @Column(name = "cardapio_publicado_por_user_id")
    private Long cardapioPublicadoPorUserId;

    @Column(name = "cardapio_despublicado_em")
    private LocalDateTime cardapioDespublicadoEm;

    @Column(name = "cardapio_despublicado_por_user_id")
    private Long cardapioDespublicadoPorUserId;

    @Column(name = "cardapio_motivo_despublicacao", length = 500)
    private String cardapioMotivoDespublicacao;

    @Column(name = "cardapio_atualizado_em")
    private LocalDateTime cardapioAtualizadoEm;

    public Tenant getTenant() {
        return tenant;
    }

    public void setTenant(Tenant tenant) {
        this.tenant = tenant;
    }

    public Boolean getCardapioPublicado() {
        return cardapioPublicado;
    }

    public void setCardapioPublicado(Boolean cardapioPublicado) {
        this.cardapioPublicado = cardapioPublicado;
    }

    public LocalDateTime getCardapioPublicadoEm() {
        return cardapioPublicadoEm;
    }

    public void setCardapioPublicadoEm(LocalDateTime cardapioPublicadoEm) {
        this.cardapioPublicadoEm = cardapioPublicadoEm;
    }

    public Long getCardapioPublicadoPorUserId() {
        return cardapioPublicadoPorUserId;
    }

    public void setCardapioPublicadoPorUserId(Long cardapioPublicadoPorUserId) {
        this.cardapioPublicadoPorUserId = cardapioPublicadoPorUserId;
    }

    public LocalDateTime getCardapioDespublicadoEm() {
        return cardapioDespublicadoEm;
    }

    public void setCardapioDespublicadoEm(LocalDateTime cardapioDespublicadoEm) {
        this.cardapioDespublicadoEm = cardapioDespublicadoEm;
    }

    public Long getCardapioDespublicadoPorUserId() {
        return cardapioDespublicadoPorUserId;
    }

    public void setCardapioDespublicadoPorUserId(Long cardapioDespublicadoPorUserId) {
        this.cardapioDespublicadoPorUserId = cardapioDespublicadoPorUserId;
    }

    public String getCardapioMotivoDespublicacao() {
        return cardapioMotivoDespublicacao;
    }

    public void setCardapioMotivoDespublicacao(String cardapioMotivoDespublicacao) {
        this.cardapioMotivoDespublicacao = cardapioMotivoDespublicacao;
    }

    public LocalDateTime getCardapioAtualizadoEm() {
        return cardapioAtualizadoEm;
    }

    public void setCardapioAtualizadoEm(LocalDateTime cardapioAtualizadoEm) {
        this.cardapioAtualizadoEm = cardapioAtualizadoEm;
    }
}

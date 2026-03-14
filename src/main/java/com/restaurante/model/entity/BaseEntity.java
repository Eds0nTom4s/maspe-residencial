package com.restaurante.model.entity;

import jakarta.persistence.*;

import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Classe base para auditoria de entidades
 * Fornece campos comuns de rastreamento e controle de concorrência
 * 
 * Auditoria temporal: createdAt, updatedAt
 * Auditoria de usuário: createdBy, modifiedBy
 * Controle de concorrência: version (Optimistic Locking)
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Controle de versão para Optimistic Locking
     * Incrementado automaticamente a cada update
     * Previne conflitos de concorrência (race conditions)
     */
    @Version
    @Column(name = "version")
    private Long version;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * Identificador do usuário que criou o registro
     * Capturado automaticamente via AuditorAware
     */
    @CreatedBy
    @Column(name = "created_by", updatable = false, length = 100)
    private String createdBy;

    /**
     * Identificador do usuário que modificou o registro pela última vez
     * Atualizado automaticamente via AuditorAware
     */
    @LastModifiedBy
    @Column(name = "modified_by", length = 100)
    private String modifiedBy;

    // Construtores

    public BaseEntity() {
    }

    public BaseEntity(Long id, Long version, LocalDateTime createdAt, LocalDateTime updatedAt, String createdBy, String modifiedBy) {
        this.id = id;
        this.version = version;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.createdBy = createdBy;
        this.modifiedBy = modifiedBy;
    }

    // Getters and Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getModifiedBy() {
        return modifiedBy;
    }

    public void setModifiedBy(String modifiedBy) {
        this.modifiedBy = modifiedBy;
    }
}

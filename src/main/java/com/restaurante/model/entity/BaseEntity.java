package com.restaurante.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
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
@Data
@NoArgsConstructor
@AllArgsConstructor
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
}

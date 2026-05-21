package com.restaurante.consumo.identificacao.entity;

import com.restaurante.model.entity.Tenant;
import com.restaurante.model.enums.ClienteConsumoStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(
        name = "cliente_consumo",
        uniqueConstraints = @UniqueConstraint(name = "uk_cliente_consumo_tenant_phone", columnNames = {"tenant_id", "telefone_normalizado"})
)
@Getter
@Setter
public class ClienteConsumo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false, updatable = false)
    private Tenant tenant;

    @Column(name = "telefone", nullable = false, length = 30)
    private String telefone;

    @Column(name = "telefone_normalizado", nullable = false, length = 30)
    private String telefoneNormalizado;

    @Column(name = "nome", length = 120)
    private String nome;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ClienteConsumoStatus status = ClienteConsumoStatus.ACTIVE;

    @Column(name = "telefone_verificado", nullable = false)
    private boolean telefoneVerificado = false;

    @Column(name = "primeiro_verificado_em")
    private Instant primeiroVerificadoEm;

    @Column(name = "ultimo_verificado_em")
    private Instant ultimoVerificadoEm;

    @Column(name = "ultimo_acesso_em")
    private Instant ultimoAcessoEm;

    @Column(name = "metadata_json", columnDefinition = "jsonb")
    private String metadataJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}


package com.restaurante.consumo.participante.entity;

import com.restaurante.consumo.identificacao.entity.ClienteConsumo;
import com.restaurante.model.entity.SessaoConsumo;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.enums.SessaoOwnerActionTokenPurpose;
import com.restaurante.model.enums.SessaoOwnerActionTokenStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Prompt 41.4 — Owner Action Token curto emitido após OTP do OWNER.
 * Permite múltiplas ações de gestão da sessão sem repetir OTP a cada acção.
 * <p>
 * SEGURANÇA:
 * - Apenas o hash (SHA-256 + pepper) é persistido, nunca o token em texto claro.
 * - Token expira por TTL configurável.
 * - Token é limitado a uma sessão e a um OWNER específico.
 * - Token não é JWT; não fornece autenticação fora da sessão.
 */
@Entity
@Table(
        name = "sessao_owner_action_tokens",
        indexes = {
                @Index(name = "idx_owner_action_tokens_tenant_sessao",
                        columnList = "tenant_id, sessao_consumo_id, status"),
                @Index(name = "idx_owner_action_tokens_owner_status",
                        columnList = "tenant_id, owner_participante_id, status, expires_at")
        }
)
@Getter
@Setter
public class SessaoOwnerActionToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false, updatable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sessao_consumo_id", nullable = false, updatable = false)
    private SessaoConsumo sessaoConsumo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_participante_id", nullable = false, updatable = false)
    private SessaoConsumoParticipante ownerParticipante;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_consumo_id", nullable = false, updatable = false)
    private ClienteConsumo clienteConsumo;

    /** Hash (SHA-256 + pepper) do token. Nunca o token em claro. */
    @Column(name = "token_hash", nullable = false, unique = true, length = 255)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 80)
    private SessaoOwnerActionTokenPurpose purpose;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private SessaoOwnerActionTokenStatus status = SessaoOwnerActionTokenStatus.ACTIVE;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "use_count", nullable = false)
    private int useCount = 0;

    /** null = sem limite de usos */
    @Column(name = "max_uses")
    private Integer maxUses;

    @Column(name = "client_ip", length = 100)
    private String clientIp;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
        if (status == null) status = SessaoOwnerActionTokenStatus.ACTIVE;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public boolean isActive() {
        return status == SessaoOwnerActionTokenStatus.ACTIVE;
    }

    public boolean isExpired(Instant now) {
        return expiresAt != null && expiresAt.isBefore(now);
    }

    public boolean hasExceededMaxUses() {
        return maxUses != null && useCount >= maxUses;
    }
}

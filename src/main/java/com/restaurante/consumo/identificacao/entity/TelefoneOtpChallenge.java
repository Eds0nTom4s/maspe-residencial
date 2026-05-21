package com.restaurante.consumo.identificacao.entity;

import com.restaurante.model.entity.SessaoConsumo;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.enums.OtpPurpose;
import com.restaurante.model.enums.OtpStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "telefone_otp_challenge", indexes = {
        @Index(name = "idx_otp_challenge_tenant_phone_status", columnList = "tenant_id, telefone_normalizado, status"),
        @Index(name = "idx_otp_challenge_expires_at", columnList = "expires_at")
})
@Getter
@Setter
public class TelefoneOtpChallenge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false, updatable = false)
    private Tenant tenant;

    @Column(name = "telefone_normalizado", nullable = false, length = 30)
    private String telefoneNormalizado;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 50)
    private OtpPurpose purpose;

    @Column(name = "otp_hash", nullable = false, length = 255)
    private String otpHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private OtpStatus status = OtpStatus.PENDING;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "attempts", nullable = false)
    private int attempts = 0;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts = 5;

    @Column(name = "resend_count", nullable = false)
    private int resendCount = 0;

    @Column(name = "last_sent_at", nullable = false)
    private Instant lastSentAt;

    @Column(name = "client_ip", length = 100)
    private String clientIp;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sessao_consumo_id")
    private SessaoConsumo sessaoConsumo;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (lastSentAt == null) lastSentAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}


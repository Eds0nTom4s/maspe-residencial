package com.restaurante.model.entity;

import com.restaurante.model.enums.DispositivoStatus;
import com.restaurante.model.enums.DispositivoTipo;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "dispositivos_operacionais", indexes = {
        @Index(name = "idx_dispositivo_tenant", columnList = "tenant_id"),
        @Index(name = "idx_dispositivo_tenant_status", columnList = "tenant_id, status"),
        @Index(name = "idx_dispositivo_tenant_instituicao", columnList = "tenant_id, instituicao_id"),
        @Index(name = "idx_dispositivo_tenant_unidade", columnList = "tenant_id, unidade_atendimento_id"),
        @Index(name = "idx_dispositivo_device_token_hash", columnList = "device_token_hash"),
        @Index(name = "idx_dispositivo_activation_code_hash", columnList = "activation_code_hash"),
        @Index(name = "idx_dispositivo_ultimo_heartbeat", columnList = "ultimo_heartbeat_em")
})
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class DispositivoOperacional extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "instituicao_id", nullable = false)
    private Instituicao instituicao;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unidade_atendimento_id")
    private UnidadeAtendimento unidadeAtendimento;

    @NotBlank
    @Column(name = "nome", nullable = false, length = 120)
    private String nome;

    @NotBlank
    @Column(name = "codigo", nullable = false, length = 60)
    private String codigo;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 30)
    private DispositivoTipo tipo = DispositivoTipo.OUTRO;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private DispositivoStatus status = DispositivoStatus.PENDENTE_ATIVACAO;

    @Column(name = "activation_code_hash", length = 64)
    private String activationCodeHash;

    @Column(name = "activation_code_expires_at")
    private LocalDateTime activationCodeExpiresAt;

    @Column(name = "device_token_hash", length = 64)
    private String deviceTokenHash;

    @Column(name = "device_token_issued_at")
    private LocalDateTime deviceTokenIssuedAt;

    @Column(name = "device_token_revoked_at")
    private LocalDateTime deviceTokenRevokedAt;

    @Column(name = "ultimo_heartbeat_em")
    private LocalDateTime ultimoHeartbeatEm;

    @Column(name = "ultimo_ip", length = 64)
    private String ultimoIp;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @Column(name = "app_version", length = 40)
    private String appVersion;

    @Column(name = "platform", length = 30)
    private String platform;

    @Column(name = "modelo_dispositivo", length = 80)
    private String modeloDispositivo;

    @Column(name = "fabricante", length = 80)
    private String fabricante;

    @Column(name = "serial_hash", length = 64)
    private String serialHash;

    @Column(name = "ativado_em")
    private LocalDateTime ativadoEm;

    @Column(name = "revogado_em")
    private LocalDateTime revogadoEm;
}


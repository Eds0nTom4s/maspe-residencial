package com.restaurante.model.entity;

import com.restaurante.model.enums.StatusQrCode;
import com.restaurante.model.enums.TipoQrCode;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entity QrCodeToken - Representa um token QR Code com expiração e controle de uso
 */
@Entity
@Table(name = "qr_code_tokens", indexes = {
    @Index(name = "idx_qrcode_token", columnList = "token", unique = true),
    @Index(name = "idx_qrcode_unidade_consumo", columnList = "unidade_consumo_id"),
    @Index(name = "idx_qrcode_status", columnList = "status"),
    @Index(name = "idx_qrcode_expiracao", columnList = "expiraEm")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class QrCodeToken extends BaseEntity {

    /**
     * Token único (UUID) do QR Code
     */
    @Column(name = "token", nullable = false, unique = true, length = 36)
    private String token;

    /**
     * Tipo de QR Code (MESA, ENTREGA, PAGAMENTO)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 20)
    private TipoQrCode tipo;

    /**
     * Status do QR Code (ATIVO, USADO, EXPIRADO, CANCELADO)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StatusQrCode status;

    /**
     * Data/hora de expiração
     */
    @Column(name = "expira_em", nullable = false)
    private LocalDateTime expiraEm;

    /**
     * Unidade de Consumo associada (mesa, quarto, etc)
     * Obrigatório para tipo MESA
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unidade_consumo_id")
    private UnidadeDeConsumo unidadeDeConsumo;

    /**
     * Pedido associado (para tipo ENTREGA ou PAGAMENTO)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pedido_id")
    private Pedido pedido;

    /**
     * Data/hora de uso (quando foi utilizado)
     */
    @Column(name = "usado_em")
    private LocalDateTime usadoEm;

    /**
     * Usuário que utilizou o QR Code
     */
    @Column(name = "usado_por", length = 100)
    private String usadoPor;

    /**
     * Metadados adicionais (JSON)
     */
    @Column(name = "metadados", columnDefinition = "TEXT")
    private String metadados;

    /**
     * Gera novo token UUID
     */
    @PrePersist
    public void gerarToken() {
        if (this.token == null) {
            this.token = UUID.randomUUID().toString();
        }
        if (this.status == null) {
            this.status = StatusQrCode.ATIVO;
        }
    }

    /**
     * Verifica se o QR Code está válido
     */
    public boolean isValido() {
        return status == StatusQrCode.ATIVO && !isExpirado();
    }

    /**
     * Verifica se expirou
     */
    public boolean isExpirado() {
        return LocalDateTime.now().isAfter(expiraEm);
    }

    /**
     * Marca como usado
     */
    public void marcarComoUsado(String usuario) {
        this.status = StatusQrCode.USADO;
        this.usadoEm = LocalDateTime.now();
        this.usadoPor = usuario;
    }

    /**
     * Marca como expirado
     */
    public void marcarComoExpirado() {
        this.status = StatusQrCode.EXPIRADO;
    }

    /**
     * Cancela o QR Code
     */
    public void cancelar() {
        this.status = StatusQrCode.CANCELADO;
    }

    /**
     * Renova a validade do QR Code
     */
    public void renovar() {
        if (tipo.isUsoMultiplo()) {
            this.expiraEm = LocalDateTime.now().plusMinutes(tipo.getValidadeMinutos());
            this.status = StatusQrCode.ATIVO;
            this.usadoEm = null;
            this.usadoPor = null;
        }
    }

    /**
     * Obtém URL completa do QR Code
     */
    public String getUrl(String baseUrl) {
        return String.format("%s/qrcode/validar/%s", baseUrl, token);
    }
}

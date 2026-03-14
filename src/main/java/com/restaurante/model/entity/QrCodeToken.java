package com.restaurante.model.entity;

import com.restaurante.model.enums.StatusQrCode;
import com.restaurante.model.enums.TipoQrCode;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Entity QrCodeToken - Representa um token QR Code com expiração e controle de uso
 */
@Entity
@Table(name = "qr_code_tokens", indexes = {
    @Index(name = "idx_qrcode_token", columnList = "token", unique = true),
    @Index(name = "idx_qrcode_mesa", columnList = "mesa_id"),
    @Index(name = "idx_qrcode_status", columnList = "status"),
    @Index(name = "idx_qrcode_expiracao", columnList = "expiraEm")
})
public class QrCodeToken extends BaseEntity {

    public QrCodeToken() {
    }

    public QrCodeToken(String token, TipoQrCode tipo, StatusQrCode status, LocalDateTime expiraEm, Mesa mesa, Pedido pedido, LocalDateTime usadoEm, String usadoPor, String metadados) {
        this.token = token;
        this.tipo = tipo;
        this.status = status;
        this.expiraEm = expiraEm;
        this.mesa = mesa;
        this.pedido = pedido;
        this.usadoEm = usadoEm;
        this.usadoPor = usadoPor;
        this.metadados = metadados;
    }

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
     * Mesa associada (recurso físico permanente).
     * Obrigatório para tipo MESA.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mesa_id")
    private Mesa mesa;

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

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public TipoQrCode getTipo() {
        return tipo;
    }

    public void setTipo(TipoQrCode tipo) {
        this.tipo = tipo;
    }

    public StatusQrCode getStatus() {
        return status;
    }

    public void setStatus(StatusQrCode status) {
        this.status = status;
    }

    public LocalDateTime getExpiraEm() {
        return expiraEm;
    }

    public void setExpiraEm(LocalDateTime expiraEm) {
        this.expiraEm = expiraEm;
    }

    public Mesa getMesa() {
        return mesa;
    }

    public void setMesa(Mesa mesa) {
        this.mesa = mesa;
    }

    public Pedido getPedido() {
        return pedido;
    }

    public void setPedido(Pedido pedido) {
        this.pedido = pedido;
    }

    public LocalDateTime getUsadoEm() {
        return usadoEm;
    }

    public void setUsadoEm(LocalDateTime usadoEm) {
        this.usadoEm = usadoEm;
    }

    public String getUsadoPor() {
        return usadoPor;
    }

    public void setUsadoPor(String usadoPor) {
        this.usadoPor = usadoPor;
    }

    public String getMetadados() {
        return metadados;
    }

    public void setMetadados(String metadados) {
        this.metadados = metadados;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        QrCodeToken that = (QrCodeToken) o;
        return Objects.equals(token, that.token);
    }

    @Override
    public int hashCode() {
        return Objects.hash(token);
    }

    public static QrCodeTokenBuilder builder() {
        return new QrCodeTokenBuilder();
    }

    public static class QrCodeTokenBuilder {
        private String token;
        private TipoQrCode tipo;
        private StatusQrCode status;
        private LocalDateTime expiraEm;
        private Mesa mesa;
        private Pedido pedido;
        private LocalDateTime usadoEm;
        private String usadoPor;
        private String metadados;

        public QrCodeTokenBuilder token(String token) {
            this.token = token;
            return this;
        }

        public QrCodeTokenBuilder tipo(TipoQrCode tipo) {
            this.tipo = tipo;
            return this;
        }

        public QrCodeTokenBuilder status(StatusQrCode status) {
            this.status = status;
            return this;
        }

        public QrCodeTokenBuilder expiraEm(LocalDateTime expiraEm) {
            this.expiraEm = expiraEm;
            return this;
        }

        public QrCodeTokenBuilder mesa(Mesa mesa) {
            this.mesa = mesa;
            return this;
        }

        public QrCodeTokenBuilder pedido(Pedido pedido) {
            this.pedido = pedido;
            return this;
        }

        public QrCodeTokenBuilder usadoEm(LocalDateTime usadoEm) {
            this.usadoEm = usadoEm;
            return this;
        }

        public QrCodeTokenBuilder usadoPor(String usadoPor) {
            this.usadoPor = usadoPor;
            return this;
        }

        public QrCodeTokenBuilder metadados(String metadados) {
            this.metadados = metadados;
            return this;
        }

        public QrCodeToken build() {
            return new QrCodeToken(token, tipo, status, expiraEm, mesa, pedido, usadoEm, usadoPor, metadados);
        }
    }
}

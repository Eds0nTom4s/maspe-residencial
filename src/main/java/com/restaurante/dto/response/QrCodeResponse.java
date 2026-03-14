package com.restaurante.dto.response;

import com.restaurante.model.enums.StatusQrCode;
import com.restaurante.model.enums.TipoQrCode;
import java.time.LocalDateTime;

public class QrCodeResponse {

    private Long id;
    private String token;
    private TipoQrCode tipo;
    private StatusQrCode status;
    private LocalDateTime expiraEm;
    private Long mesaId;
    private String referenciaMesa;
    private Long pedidoId;
    private LocalDateTime usadoEm;
    private String usadoPor;
    private String metadados;
    private String url;
    private Boolean valido;
    private Boolean expirado;
    private LocalDateTime criadoEm;
    private String criadoPor;

    public QrCodeResponse() {
    }

    public QrCodeResponse(Long id, String token, TipoQrCode tipo, StatusQrCode status, LocalDateTime expiraEm, Long mesaId, String referenciaMesa, Long pedidoId, LocalDateTime usadoEm, String usadoPor, String metadados, String url, Boolean valido, Boolean expirado, LocalDateTime criadoEm, String criadoPor) {
        this.id = id;
        this.token = token;
        this.tipo = tipo;
        this.status = status;
        this.expiraEm = expiraEm;
        this.mesaId = mesaId;
        this.referenciaMesa = referenciaMesa;
        this.pedidoId = pedidoId;
        this.usadoEm = usadoEm;
        this.usadoPor = usadoPor;
        this.metadados = metadados;
        this.url = url;
        this.valido = valido;
        this.expirado = expirado;
        this.criadoEm = criadoEm;
        this.criadoPor = criadoPor;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public TipoQrCode getTipo() { return tipo; }
    public void setTipo(TipoQrCode tipo) { this.tipo = tipo; }
    public StatusQrCode getStatus() { return status; }
    public void setStatus(StatusQrCode status) { this.status = status; }
    public LocalDateTime getExpiraEm() { return expiraEm; }
    public void setExpiraEm(LocalDateTime expiraEm) { this.expiraEm = expiraEm; }
    public Long getMesaId() { return mesaId; }
    public void setMesaId(Long mesaId) { this.mesaId = mesaId; }
    public String getReferenciaMesa() { return referenciaMesa; }
    public void setReferenciaMesa(String referenciaMesa) { this.referenciaMesa = referenciaMesa; }
    public Long getPedidoId() { return pedidoId; }
    public void setPedidoId(Long pedidoId) { this.pedidoId = pedidoId; }
    public LocalDateTime getUsadoEm() { return usadoEm; }
    public void setUsadoEm(LocalDateTime usadoEm) { this.usadoEm = usadoEm; }
    public String getUsadoPor() { return usadoPor; }
    public void setUsadoPor(String usadoPor) { this.usadoPor = usadoPor; }
    public String getMetadados() { return metadados; }
    public void setMetadados(String metadados) { this.metadados = metadados; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public Boolean getValido() { return valido; }
    public void setValido(Boolean valido) { this.valido = valido; }
    public Boolean getExpirado() { return expirado; }
    public void setExpirado(Boolean expirado) { this.expirado = expirado; }
    public LocalDateTime getCriadoEm() { return criadoEm; }
    public void setCriadoEm(LocalDateTime criadoEm) { this.criadoEm = criadoEm; }
    public String getCriadoPor() { return criadoPor; }
    public void setCriadoPor(String criadoPor) { this.criadoPor = criadoPor; }

    public static QrCodeResponseBuilder builder() {
        return new QrCodeResponseBuilder();
    }

    public static class QrCodeResponseBuilder {
        private Long id;
        private String token;
        private TipoQrCode tipo;
        private StatusQrCode status;
        private LocalDateTime expiraEm;
        private Long mesaId;
        private String referenciaMesa;
        private Long pedidoId;
        private LocalDateTime usadoEm;
        private String usadoPor;
        private String metadados;
        private String url;
        private Boolean valido;
        private Boolean expirado;
        private LocalDateTime criadoEm;
        private String criadoPor;

        public QrCodeResponseBuilder id(Long id) { this.id = id; return this; }
        public QrCodeResponseBuilder token(String token) { this.token = token; return this; }
        public QrCodeResponseBuilder tipo(TipoQrCode tipo) { this.tipo = tipo; return this; }
        public QrCodeResponseBuilder status(StatusQrCode status) { this.status = status; return this; }
        public QrCodeResponseBuilder expiraEm(LocalDateTime expiraEm) { this.expiraEm = expiraEm; return this; }
        public QrCodeResponseBuilder mesaId(Long mesaId) { this.mesaId = mesaId; return this; }
        public QrCodeResponseBuilder referenciaMesa(String referenciaMesa) { this.referenciaMesa = referenciaMesa; return this; }
        public QrCodeResponseBuilder pedidoId(Long pedidoId) { this.pedidoId = pedidoId; return this; }
        public QrCodeResponseBuilder usadoEm(LocalDateTime usadoEm) { this.usadoEm = usadoEm; return this; }
        public QrCodeResponseBuilder usadoPor(String usadoPor) { this.usadoPor = usadoPor; return this; }
        public QrCodeResponseBuilder metadados(String metadados) { this.metadados = metadados; return this; }
        public QrCodeResponseBuilder url(String url) { this.url = url; return this; }
        public QrCodeResponseBuilder valido(Boolean valido) { this.valido = valido; return this; }
        public QrCodeResponseBuilder expirado(Boolean expirado) { this.expirado = expirado; return this; }
        public QrCodeResponseBuilder criadoEm(LocalDateTime criadoEm) { this.criadoEm = criadoEm; return this; }
        public QrCodeResponseBuilder criadoPor(String criadoPor) { this.criadoPor = criadoPor; return this; }

        public QrCodeResponse build() {
            return new QrCodeResponse(id, token, tipo, status, expiraEm, mesaId, referenciaMesa, pedidoId, usadoEm, usadoPor, metadados, url, valido, expirado, criadoEm, criadoPor);
        }
    }
}

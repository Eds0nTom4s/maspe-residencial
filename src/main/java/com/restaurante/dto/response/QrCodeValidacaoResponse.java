package com.restaurante.dto.response;

public class QrCodeValidacaoResponse {

    private Boolean valido;
    private String mensagem;
    private QrCodeResponse qrCode;
    private String motivoInvalido; // EXPIRADO, USADO, CANCELADO, NAO_ENCONTRADO

    public QrCodeValidacaoResponse() {
    }

    public QrCodeValidacaoResponse(Boolean valido, String mensagem, QrCodeResponse qrCode, String motivoInvalido) {
        this.valido = valido;
        this.mensagem = mensagem;
        this.qrCode = qrCode;
        this.motivoInvalido = motivoInvalido;
    }

    public Boolean getValido() {
        return valido;
    }

    public void setValido(Boolean valido) {
        this.valido = valido;
    }

    public String getMensagem() {
        return mensagem;
    }

    public void setMensagem(String mensagem) {
        this.mensagem = mensagem;
    }

    public QrCodeResponse getQrCode() {
        return qrCode;
    }

    public void setQrCode(QrCodeResponse qrCode) {
        this.qrCode = qrCode;
    }

    public String getMotivoInvalido() {
        return motivoInvalido;
    }

    public void setMotivoInvalido(String motivoInvalido) {
        this.motivoInvalido = motivoInvalido;
    }

    public static QrCodeValidacaoResponseBuilder builder() {
        return new QrCodeValidacaoResponseBuilder();
    }

    public static class QrCodeValidacaoResponseBuilder {
        private Boolean valido;
        private String mensagem;
        private QrCodeResponse qrCode;
        private String motivoInvalido;

        public QrCodeValidacaoResponseBuilder valido(Boolean valido) {
            this.valido = valido;
            return this;
        }

        public QrCodeValidacaoResponseBuilder mensagem(String mensagem) {
            this.mensagem = mensagem;
            return this;
        }

        public QrCodeValidacaoResponseBuilder qrCode(QrCodeResponse qrCode) {
            this.qrCode = qrCode;
            return this;
        }

        public QrCodeValidacaoResponseBuilder motivoInvalido(String motivoInvalido) {
            this.motivoInvalido = motivoInvalido;
            return this;
        }

        public QrCodeValidacaoResponse build() {
            return new QrCodeValidacaoResponse(valido, mensagem, qrCode, motivoInvalido);
        }
    }
}

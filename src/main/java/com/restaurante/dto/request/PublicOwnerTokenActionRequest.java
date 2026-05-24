package com.restaurante.dto.request;

/**
 * Prompt 41.4 — Request com ownerActionToken para endpoints que suportam token pós-OTP.
 * O ownerActionToken é aceite no body (compatível com JSON clients).
 */
public class PublicOwnerTokenActionRequest {

    /** Token curto emitido após OTP verificado. */
    private String ownerActionToken;

    /** reason opcional para ações que o suportam */
    private String reason;

    public String getOwnerActionToken() { return ownerActionToken; }
    public void setOwnerActionToken(String ownerActionToken) { this.ownerActionToken = ownerActionToken; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}

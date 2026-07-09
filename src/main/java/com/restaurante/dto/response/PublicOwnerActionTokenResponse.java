package com.restaurante.dto.response;

import java.time.Instant;

/**
 * Prompt 41.4 — Response ao verificar OTP do OWNER, retorna ownerActionToken.
 */
public class PublicOwnerActionTokenResponse {

    /** Token curto para ações subsequentes sem novo OTP. Retornado UMA SÓ VEZ. */
    private String ownerActionToken;

    private Long tokenId;
    private Instant expiresAt;
    private Integer maxUses;
    private Long ownerParticipanteId;
    private Long sessaoConsumoId;

    public String getOwnerActionToken() { return ownerActionToken; }
    public void setOwnerActionToken(String ownerActionToken) { this.ownerActionToken = ownerActionToken; }

    public Long getTokenId() { return tokenId; }
    public void setTokenId(Long tokenId) { this.tokenId = tokenId; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

    public Integer getMaxUses() { return maxUses; }
    public void setMaxUses(Integer maxUses) { this.maxUses = maxUses; }

    public Long getOwnerParticipanteId() { return ownerParticipanteId; }
    public void setOwnerParticipanteId(Long ownerParticipanteId) { this.ownerParticipanteId = ownerParticipanteId; }

    public Long getSessaoConsumoId() { return sessaoConsumoId; }
    public void setSessaoConsumoId(Long sessaoConsumoId) { this.sessaoConsumoId = sessaoConsumoId; }
}

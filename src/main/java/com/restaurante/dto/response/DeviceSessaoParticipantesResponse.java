package com.restaurante.dto.response;

import com.restaurante.model.enums.SessaoParticipanteRole;
import com.restaurante.model.enums.SessaoParticipanteStatus;

import java.time.Instant;
import java.util.List;

public class DeviceSessaoParticipantesResponse {

    private Long sessaoConsumoId;
    private List<Item> participantes;

    public Long getSessaoConsumoId() {
        return sessaoConsumoId;
    }

    public void setSessaoConsumoId(Long sessaoConsumoId) {
        this.sessaoConsumoId = sessaoConsumoId;
    }

    public List<Item> getParticipantes() {
        return participantes;
    }

    public void setParticipantes(List<Item> participantes) {
        this.participantes = participantes;
    }

    public static class Item {
        private Long participanteId;
        private String nomeExibicao;
        private SessaoParticipanteRole role;
        private SessaoParticipanteStatus status;
        private Instant joinedAt;
        private Instant lastActivityAt;
        private Instant expiresAt;
        private Integer resendCount;
        private Instant lastResendAt;
        private Boolean canResend;

        public Long getParticipanteId() {
            return participanteId;
        }

        public void setParticipanteId(Long participanteId) {
            this.participanteId = participanteId;
        }

        public String getNomeExibicao() {
            return nomeExibicao;
        }

        public void setNomeExibicao(String nomeExibicao) {
            this.nomeExibicao = nomeExibicao;
        }

        public SessaoParticipanteRole getRole() {
            return role;
        }

        public void setRole(SessaoParticipanteRole role) {
            this.role = role;
        }

        public SessaoParticipanteStatus getStatus() {
            return status;
        }

        public void setStatus(SessaoParticipanteStatus status) {
            this.status = status;
        }

        public Instant getJoinedAt() {
            return joinedAt;
        }

        public void setJoinedAt(Instant joinedAt) {
            this.joinedAt = joinedAt;
        }

        public Instant getLastActivityAt() {
            return lastActivityAt;
        }

        public void setLastActivityAt(Instant lastActivityAt) {
            this.lastActivityAt = lastActivityAt;
        }

        public Instant getExpiresAt() {
            return expiresAt;
        }

        public void setExpiresAt(Instant expiresAt) {
            this.expiresAt = expiresAt;
        }

        public Integer getResendCount() {
            return resendCount;
        }

        public void setResendCount(Integer resendCount) {
            this.resendCount = resendCount;
        }

        public Instant getLastResendAt() {
            return lastResendAt;
        }

        public void setLastResendAt(Instant lastResendAt) {
            this.lastResendAt = lastResendAt;
        }

        public Boolean getCanResend() {
            return canResend;
        }

        public void setCanResend(Boolean canResend) {
            this.canResend = canResend;
        }
    }
}

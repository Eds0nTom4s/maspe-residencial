package com.restaurante.dto.response;

import com.restaurante.model.enums.SessaoParticipanteStatus;

import java.time.Instant;
import java.util.List;

public class PublicSessaoParticipantesPendingResponse {

    private List<Item> participantes;

    public List<Item> getParticipantes() {
        return participantes;
    }

    public void setParticipantes(List<Item> participantes) {
        this.participantes = participantes;
    }

    public static class Item {
        private Long participanteId;
        private String nomeExibicao;
        private SessaoParticipanteStatus status;
        private Instant joinedAt;
        private String telefoneMascarado;
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

        public String getTelefoneMascarado() {
            return telefoneMascarado;
        }

        public void setTelefoneMascarado(String telefoneMascarado) {
            this.telefoneMascarado = telefoneMascarado;
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

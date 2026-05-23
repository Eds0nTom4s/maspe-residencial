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
    }
}


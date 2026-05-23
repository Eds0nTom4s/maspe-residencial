package com.restaurante.dto.response;

import com.restaurante.model.enums.SessaoParticipanteRole;
import com.restaurante.model.enums.SessaoParticipanteStatus;

import java.time.Instant;
import java.util.List;

public class PublicSessaoParticipantesResponse {

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
        private SessaoParticipanteRole role;
        private SessaoParticipanteStatus status;
        private Instant joinedAt;

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
    }
}


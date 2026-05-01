package com.restaurante.store.dto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class StoreOrderTrackingDTO {
    private Long ordemId;
    private String numero;
    private String status;
    private String statusPagamento;
    private String etapaAtual;
    private LocalDateTime criadaEm;
    private LocalDateTime atualizadaEm;
    private List<TrackingStepDTO> etapas = new ArrayList<>();

    public Long getOrdemId() { return ordemId; }
    public void setOrdemId(Long ordemId) { this.ordemId = ordemId; }
    public String getNumero() { return numero; }
    public void setNumero(String numero) { this.numero = numero; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getStatusPagamento() { return statusPagamento; }
    public void setStatusPagamento(String statusPagamento) { this.statusPagamento = statusPagamento; }
    public String getEtapaAtual() { return etapaAtual; }
    public void setEtapaAtual(String etapaAtual) { this.etapaAtual = etapaAtual; }
    public LocalDateTime getCriadaEm() { return criadaEm; }
    public void setCriadaEm(LocalDateTime criadaEm) { this.criadaEm = criadaEm; }
    public LocalDateTime getAtualizadaEm() { return atualizadaEm; }
    public void setAtualizadaEm(LocalDateTime atualizadaEm) { this.atualizadaEm = atualizadaEm; }
    public List<TrackingStepDTO> getEtapas() { return etapas; }
    public void setEtapas(List<TrackingStepDTO> etapas) { this.etapas = etapas; }

    public static class TrackingStepDTO {
        private String codigo;
        private String descricao;
        private boolean concluida;
        private LocalDateTime concluidaEm;

        public TrackingStepDTO() {}

        public TrackingStepDTO(String codigo, String descricao, boolean concluida, LocalDateTime concluidaEm) {
            this.codigo = codigo;
            this.descricao = descricao;
            this.concluida = concluida;
            this.concluidaEm = concluidaEm;
        }

        public String getCodigo() { return codigo; }
        public void setCodigo(String codigo) { this.codigo = codigo; }
        public String getDescricao() { return descricao; }
        public void setDescricao(String descricao) { this.descricao = descricao; }
        public boolean isConcluida() { return concluida; }
        public void setConcluida(boolean concluida) { this.concluida = concluida; }
        public LocalDateTime getConcluidaEm() { return concluidaEm; }
        public void setConcluidaEm(LocalDateTime concluidaEm) { this.concluidaEm = concluidaEm; }
    }
}

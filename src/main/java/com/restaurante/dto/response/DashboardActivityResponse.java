package com.restaurante.dto.response;

import java.time.LocalDateTime;

/**
 * DTO para resposta de atividades do dashboard
 */
public class DashboardActivityResponse {
    private String tipo;
    private String descricao;
    private LocalDateTime timestamp;
    private String detalhes;

    public DashboardActivityResponse() {}

    public DashboardActivityResponse(String tipo, String descricao, LocalDateTime timestamp, String detalhes) {
        this.tipo = tipo;
        this.descricao = descricao;
        this.timestamp = timestamp;
        this.detalhes = detalhes;
    }

    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }
    public String getDescricao() { return descricao; }
    public void setDescricao(String descricao) { this.descricao = descricao; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    public String getDetalhes() { return detalhes; }
    public void setDetalhes(String detalhes) { this.detalhes = detalhes; }
}

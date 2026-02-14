package com.restaurante.model.entity;

import com.restaurante.financeiro.enums.TipoEventoFinanceiro;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Auditoria Financeira - Event Log
 * 
 * IMUTÁVEL: Registro permanente de todas as ações financeiras
 * 
 * RESPONSABILIDADES:
 * - Rastrear quem fez o quê e quando
 * - Registrar motivos para ações críticas
 * - Compliance e auditoria externa
 * 
 * EVENTOS OBRIGATÓRIOS:
 * - Autorização/negação de pós-pago
 * - Confirmação de pagamento
 * - Estornos (manual ou automático)
 * - Alteração de políticas globais
 */
@Entity
@Table(name = "pagamento_event_log", indexes = {
    @Index(name = "idx_pag_event_pagamento", columnList = "pagamento_id"),
    @Index(name = "idx_pag_event_pedido", columnList = "pedido_id"),
    @Index(name = "idx_pag_event_timestamp", columnList = "timestamp"),
    @Index(name = "idx_pag_event_usuario", columnList = "usuario"),
    @Index(name = "idx_pag_event_tipo", columnList = "tipo_evento")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PagamentoEventLog {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * Tipo do evento financeiro
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_evento", nullable = false, length = 50)
    private TipoEventoFinanceiro tipoEvento;
    
    /**
     * Pagamento relacionado (nullable para eventos globais)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pagamento_id")
    private Pagamento pagamento;
    
    /**
     * Pedido relacionado (facilitador de consultas)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pedido_id")
    private Pedido pedido;
    
    /**
     * Usuário que executou a ação
     * Formato: "username" ou "SYSTEM"
     */
    @Column(name = "usuario", length = 100, nullable = false)
    private String usuario;
    
    /**
     * Role do usuário
     * ATENDENTE, GERENTE, ADMIN, SYSTEM
     */
    @Column(name = "role", length = 50, nullable = false)
    private String role;
    
    /**
     * IP da requisição (auditoria)
     */
    @Column(name = "ip", length = 50)
    private String ip;
    
    /**
     * Motivo da ação
     * OBRIGATÓRIO para: estorno, negação pós-pago, cancelamentos
     */
    @Column(name = "motivo", length = 500)
    private String motivo;
    
    /**
     * Dados adicionais (JSON)
     * Opcional: valores, status anterior/novo, etc
     */
    @Column(name = "dados_adicionais", columnDefinition = "TEXT")
    private String dadosAdicionais;
    
    /**
     * Timestamp do evento
     */
    @Column(name = "timestamp", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();
    
    /**
     * Valida se motivo é obrigatório
     */
    @PrePersist
    @PreUpdate
    private void validarMotivo() {
        if (tipoEvento != null && tipoEvento.requerMotivo()) {
            if (motivo == null || motivo.isBlank()) {
                throw new IllegalStateException(
                    "Motivo é obrigatório para evento: " + tipoEvento
                );
            }
        }
    }
}

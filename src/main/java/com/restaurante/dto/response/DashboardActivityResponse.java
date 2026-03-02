package com.restaurante.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO para resposta de atividades do dashboard
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardActivityResponse {
    private String tipo; // PEDIDO_CRIADO, PEDIDO_CONFIRMADO, PAGAMENTO_RECEBIDO, etc.
    private String descricao;
    private LocalDateTime timestamp;
    private String detalhes; // Informações adicionais (número pedido, valor, etc.)
}

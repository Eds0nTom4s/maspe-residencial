package com.restaurante.notificacao.dto;

import com.restaurante.model.enums.StatusSubPedido;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO para notificações WebSocket de mudança de status de SubPedido
 * Contém informações mínimas necessárias para atualização em tempo real
 * 
 * USADO EM:
 * - Notificações para cozinha (/topic/cozinha/{cozinhaId})
 * - Notificações para atendentes (/topic/atendente/{unidadeId})
 * - Notificações específicas de SubPedido (/topic/subpedido/{id})
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificacaoSubPedidoDTO {

    /**
     * ID do SubPedido
     */
    private Long id;

    /**
     * Número do SubPedido (ex: PED-001-1)
     */
    private String numero;

    /**
     * ID do Pedido pai
     */
    private Long pedidoId;

    /**
     * Número do Pedido pai (ex: PED-001)
     */
    private String numeroPedido;

    /**
     * Status anterior do SubPedido
     */
    private StatusSubPedido statusAnterior;

    /**
     * Status novo do SubPedido
     */
    private StatusSubPedido statusNovo;

    /**
     * ID da Cozinha responsável
     */
    private Long cozinhaId;

    /**
     * Nome da Cozinha responsável
     */
    private String nomeCozinha;

    /**
     * ID da Unidade de Atendimento de origem
     */
    private Long unidadeAtendimentoId;

    /**
     * Nome da Unidade de Atendimento de origem
     */
    private String nomeUnidadeAtendimento;

    /**
     * Usuário responsável pela mudança
     */
    private String usuario;

    /**
     * Timestamp da mudança
     */
    private LocalDateTime timestamp;

    /**
     * Observações sobre a mudança
     */
    private String observacoes;

    /**
     * Tipo de ação que gerou a notificação
     */
    private TipoAcaoSubPedido tipoAcao;

    /**
     * Enum para classificar o tipo de ação
     */
    public enum TipoAcaoSubPedido {
        CRIACAO("SubPedido criado"),
        MUDANCA_STATUS("Status alterado"),
        CANCELAMENTO("SubPedido cancelado"),
        OBSERVACAO_ADICIONADA("Observação adicionada");

        private final String descricao;

        TipoAcaoSubPedido(String descricao) {
            this.descricao = descricao;
        }

        public String getDescricao() {
            return descricao;
        }
    }
}

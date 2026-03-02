package com.restaurante.notificacao.service;

import com.restaurante.model.entity.SubPedido;
import com.restaurante.model.enums.StatusSubPedido;
import com.restaurante.notificacao.dto.NotificacaoSubPedidoDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Serviço para envio de notificações WebSocket em tempo real
 * 
 * RESPONSABILIDADES:
 * - Enviar notificações de mudança de status de SubPedido
 * - Broadcast para múltiplos canais (cozinha, atendente, específico)
 * - Garantir formato consistente de mensagens
 * 
 * TÓPICOS WEBSOCKET:
 * - /topic/cozinha/{cozinhaId} → Notificações para cozinha específica
 * - /topic/atendente/unidade/{unidadeId} → Notificações para atendentes de unidade
 * - /topic/subpedido/{id} → Notificações para SubPedido específico
 * - /topic/pedido/{id} → Notificações para Pedido específico
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WebSocketNotificacaoService {

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Notifica mudança de status de SubPedido para TODOS os canais relevantes
     * 
     * @param subPedido SubPedido que teve status alterado
     * @param statusAnterior Status anterior
     * @param usuario Usuário responsável pela mudança
     * @param observacoes Observações sobre a mudança
     */
    public void notificarMudancaStatusSubPedido(
            SubPedido subPedido, 
            StatusSubPedido statusAnterior, 
            String usuario,
            String observacoes) {
        
        log.debug("Notificando mudança de status: SubPedido {} - {} → {}", 
            subPedido.getNumero(), statusAnterior, subPedido.getStatus());

        // Criar DTO com dados da notificação
        NotificacaoSubPedidoDTO notificacao = construirNotificacao(
            subPedido, statusAnterior, usuario, observacoes, 
            NotificacaoSubPedidoDTO.TipoAcaoSubPedido.MUDANCA_STATUS
        );

        // Broadcast para múltiplos canais
        notificarCozinha(notificacao);
        notificarAtendentes(notificacao);
        notificarSubPedidoEspecifico(notificacao);
        notificarPedido(notificacao);
    }

    /**
     * Notifica criação de novo SubPedido
     * Usado quando novo pedido entra na fila da cozinha
     */
    public void notificarNovoSubPedido(SubPedido subPedido, String usuario) {
        log.info("Notificando novo SubPedido: {} para cozinha {}", 
            subPedido.getNumero(), subPedido.getCozinha().getNome());

        NotificacaoSubPedidoDTO notificacao = construirNotificacao(
            subPedido, null, usuario, "Novo SubPedido criado", 
            NotificacaoSubPedidoDTO.TipoAcaoSubPedido.CRIACAO
        );

        // Notifica apenas a cozinha responsável (novo item na fila)
        notificarCozinha(notificacao);
    }

    /**
     * Notifica quando SubPedido fica PRONTO
     * Prioridade ALTA - atendente deve buscar
     */
    public void notificarSubPedidoPronto(SubPedido subPedido, String usuario) {
        log.info("🔔 SubPedido PRONTO: {} - Notificando atendentes", 
            subPedido.getNumero());

        NotificacaoSubPedidoDTO notificacao = construirNotificacao(
            subPedido, StatusSubPedido.EM_PREPARACAO, usuario, 
            "SubPedido pronto para entrega", 
            NotificacaoSubPedidoDTO.TipoAcaoSubPedido.MUDANCA_STATUS
        );

        // Notifica atendentes (prioridade)
        notificarAtendentes(notificacao);
        notificarPedido(notificacao);
    }

    /**
     * Notifica quando SubPedido é CANCELADO
     */
    public void notificarCancelamentoSubPedido(
            SubPedido subPedido, 
            StatusSubPedido statusAnterior, 
            String usuario, 
            String motivo) {
        
        log.warn("⚠️ SubPedido CANCELADO: {} - Motivo: {}", 
            subPedido.getNumero(), motivo);

        NotificacaoSubPedidoDTO notificacao = construirNotificacao(
            subPedido, statusAnterior, usuario, motivo, 
            NotificacaoSubPedidoDTO.TipoAcaoSubPedido.CANCELAMENTO
        );

        // Notifica todos os canais (importante)
        notificarCozinha(notificacao);
        notificarAtendentes(notificacao);
        notificarSubPedidoEspecifico(notificacao);
        notificarPedido(notificacao);
    }

    /**
     * Notifica quando Pedido é LIBERADO AUTOMATICAMENTE
     * (dentro do limite de risco)
     * 
     * BROADCAST CRÍTICO: Cozinha, Bar, Painel Gerente, Balcão
     */
    public void notificarPedidoLiberadoAutomaticamente(com.restaurante.model.entity.Pedido pedido) {
        log.info("🟢 PEDIDO_LIBERADO_AUTOMATICAMENTE: {} - Enviando para produção", 
            pedido.getNumero());

        // Notifica cada cozinha responsável por SubPedidos
        for (SubPedido subPedido : pedido.getSubPedidos()) {
            String topico = String.format("/topic/cozinha/%d", subPedido.getCozinha().getId());
            
            java.util.Map<String, Object> evento = java.util.Map.of(
                "tipo", "PEDIDO_LIBERADO_AUTOMATICAMENTE",
                "pedidoNumero", pedido.getNumero(),
                "pedidoId", pedido.getId(),
                "subPedidoNumero", subPedido.getNumero(),
                "subPedidoId", subPedido.getId(),
                "status", subPedido.getStatus().toString(),
                "totalItens", subPedido.getItens().size(),
                "timestamp", java.time.LocalDateTime.now()
            );
            
            try {
                messagingTemplate.convertAndSend(topico, evento);
                log.debug("✓ Notificação PEDIDO_LIBERADO enviada para cozinha: {}", topico);
            } catch (Exception e) {
                log.error("Erro ao notificar cozinha {}: {}", topico, e.getMessage(), e);
            }
        }

        // Notifica painel gerente (overview global)
        String topicoGerente = "/topic/gerente/pedidos";
        java.util.Map<String, Object> eventoGerente = java.util.Map.of(
            "tipo", "PEDIDO_LIBERADO_AUTOMATICAMENTE",
            "pedidoNumero", pedido.getNumero(),
            "pedidoId", pedido.getId(),
            "total", pedido.getTotal(),
            "tipoPagamento", pedido.getTipoPagamento().toString(),
            "totalSubPedidos", pedido.getSubPedidos().size(),
            "timestamp", java.time.LocalDateTime.now()
        );
        
        try {
            messagingTemplate.convertAndSend(topicoGerente, eventoGerente);
            log.debug("✓ Notificação PEDIDO_LIBERADO enviada para gerente");
        } catch (Exception e) {
            log.error("Erro ao notificar gerente: {}", e.getMessage(), e);
        }
    }

    /**
     * Notifica quando Pedido é BLOQUEADO POR LIMITE
     * (limite de pós-pago atingido)
     * 
     * ALERTA CRÍTICO: Apenas Gerente/Admin podem ver e desbloquear
     */
    public void notificarPedidoBloqueadoPorLimite(com.restaurante.model.entity.Pedido pedido) {
        log.warn("🔴 PEDIDO_BLOQUEADO_POR_LIMITE: {} - Requer intervenção gerencial", 
            pedido.getNumero());

        // Notifica apenas painel gerente (não vai para cozinha)
        String topico = "/topic/gerente/alertas";
        
        java.util.Map<String, Object> alerta = java.util.Map.of(
            "tipo", "PEDIDO_BLOQUEADO_POR_LIMITE",
            "severidade", "ALTA",
            "pedidoNumero", pedido.getNumero(),
            "pedidoId", pedido.getId(),
            "total", pedido.getTotal(),
            "tipoPagamento", pedido.getTipoPagamento().toString(),
            "unidadeConsumoReferencia", pedido.getSessaoConsumo().getMesa().getReferencia(),
            "mensagem", "Limite de pós-pago atingido. Pedido aguarda confirmação de pagamento.",
            "timestamp", java.time.LocalDateTime.now()
        );
        
        try {
            messagingTemplate.convertAndSend(topico, alerta);
            log.info("✓ Alerta PEDIDO_BLOQUEADO enviado para gerente");
        } catch (Exception e) {
            log.error("Erro ao enviar alerta para gerente: {}", e.getMessage(), e);
        }

        // Notifica cliente via tópico específico do pedido
        String topicoCliente = String.format("/topic/pedido/%d", pedido.getId());
        java.util.Map<String, Object> avisoCliente = java.util.Map.of(
            "tipo", "PEDIDO_AGUARDANDO_CONFIRMACAO",
            "pedidoNumero", pedido.getNumero(),
            "mensagem", "Seu pedido foi registrado e aguarda confirmação de pagamento.",
            "timestamp", java.time.LocalDateTime.now()
        );
        
        try {
            messagingTemplate.convertAndSend(topicoCliente, avisoCliente);
        } catch (Exception e) {
            log.error("Erro ao notificar cliente: {}", e.getMessage(), e);
        }
    }

    // ========== MÉTODOS PRIVADOS DE ENVIO ==========

    /**
     * Envia notificação para cozinha específica
     * Tópico: /topic/cozinha/{cozinhaId}
     */
    private void notificarCozinha(NotificacaoSubPedidoDTO notificacao) {
        String topico = String.format("/topic/cozinha/%d", notificacao.getCozinhaId());
        
        try {
            messagingTemplate.convertAndSend(topico, notificacao);
            log.debug("✓ Notificação enviada para cozinha: {}", topico);
        } catch (Exception e) {
            log.error("Erro ao enviar notificação para cozinha {}: {}", 
                topico, e.getMessage(), e);
        }
    }

    /**
     * Envia notificação para atendentes da unidade
     * Tópico: /topic/atendente/unidade/{unidadeId}
     */
    private void notificarAtendentes(NotificacaoSubPedidoDTO notificacao) {
        String topico = String.format("/topic/atendente/unidade/%d", 
            notificacao.getUnidadeAtendimentoId());
        
        try {
            messagingTemplate.convertAndSend(topico, notificacao);
            log.debug("✓ Notificação enviada para atendentes: {}", topico);
        } catch (Exception e) {
            log.error("Erro ao enviar notificação para atendentes {}: {}", 
                topico, e.getMessage(), e);
        }
    }

    /**
     * Envia notificação para SubPedido específico
     * Tópico: /topic/subpedido/{id}
     */
    private void notificarSubPedidoEspecifico(NotificacaoSubPedidoDTO notificacao) {
        String topico = String.format("/topic/subpedido/%d", notificacao.getId());
        
        try {
            messagingTemplate.convertAndSend(topico, notificacao);
            log.debug("✓ Notificação enviada para SubPedido: {}", topico);
        } catch (Exception e) {
            log.error("Erro ao enviar notificação para SubPedido {}: {}", 
                topico, e.getMessage(), e);
        }
    }

    /**
     * Envia notificação para Pedido pai
     * Tópico: /topic/pedido/{pedidoId}
     * Cliente pode acompanhar todos os SubPedidos do seu pedido
     */
    private void notificarPedido(NotificacaoSubPedidoDTO notificacao) {
        String topico = String.format("/topic/pedido/%d", notificacao.getPedidoId());
        
        try {
            messagingTemplate.convertAndSend(topico, notificacao);
            log.debug("✓ Notificação enviada para Pedido: {}", topico);
        } catch (Exception e) {
            log.error("Erro ao enviar notificação para Pedido {}: {}", 
                topico, e.getMessage(), e);
        }
    }

    // ========== CONSTRUTOR DE DTO ==========

    /**
     * Constrói DTO de notificação a partir de SubPedido
     */
    private NotificacaoSubPedidoDTO construirNotificacao(
            SubPedido subPedido,
            StatusSubPedido statusAnterior,
            String usuario,
            String observacoes,
            NotificacaoSubPedidoDTO.TipoAcaoSubPedido tipoAcao) {
        
        return NotificacaoSubPedidoDTO.builder()
            .id(subPedido.getId())
            .numero(subPedido.getNumero())
            .pedidoId(subPedido.getPedido().getId())
            .numeroPedido(subPedido.getPedido().getNumero())
            .statusAnterior(statusAnterior)
            .statusNovo(subPedido.getStatus())
            .cozinhaId(subPedido.getCozinha().getId())
            .nomeCozinha(subPedido.getCozinha().getNome())
            .unidadeAtendimentoId(subPedido.getUnidadeAtendimento().getId())
            .nomeUnidadeAtendimento(subPedido.getUnidadeAtendimento().getNome())
            .usuario(usuario != null ? usuario : "system")
            .timestamp(LocalDateTime.now())
            .observacoes(observacoes)
            .tipoAcao(tipoAcao)
            .build();
    }
}

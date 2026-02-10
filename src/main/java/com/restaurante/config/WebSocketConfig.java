package com.restaurante.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Configuração de WebSocket para notificações em tempo real
 * Preparado para implementação futura
 * 
 * Exemplo de uso:
 * - Notificar atendentes quando novo pedido é criado
 * - Atualizar status de pedidos em tempo real
 * - Notificar clientes sobre status do pedido
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Prefixo para mensagens enviadas do servidor para o cliente
        config.enableSimpleBroker("/topic", "/queue");
        
        // Prefixo para mensagens enviadas do cliente para o servidor
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Endpoint WebSocket que os clientes usarão para conectar
        registry.addEndpoint("/ws")
                .setAllowedOrigins("http://localhost:3000", "http://localhost:4200")
                .withSockJS();
    }

    /*
     * Tópicos sugeridos:
     * /topic/pedidos/novos - Notificações de novos pedidos
     * /topic/pedidos/{id} - Atualizações de pedido específico
     * /topic/mesas/{id} - Atualizações de mesa específica
     * /queue/user/{userId} - Mensagens privadas para usuário específico
     */
}

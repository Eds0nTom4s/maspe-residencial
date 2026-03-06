package com.restaurante.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import com.restaurante.security.JwtChannelInterceptor;

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
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtChannelInterceptor jwtChannelInterceptor;

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
                .setAllowedOrigins(
                    "http://localhost:3000",
                    "http://localhost:3001",
                    "http://localhost:4200",
                    "http://localhost:5173",
                    "http://localhost:5174",
                    "http://localhost:8081",
                    "http://127.0.0.1:3000",
                    "http://127.0.0.1:3001",
                    "http://127.0.0.1:4200",
                    "http://127.0.0.1:5173",
                    "http://127.0.0.1:5174"
                )
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // Registrar interceptor JWT para validar tokens em mensagens STOMP
        registration.interceptors(jwtChannelInterceptor);
    }

    /*
     * Tópicos sugeridos:
     * /topic/pedidos/novos - Notificações de novos pedidos
     * /topic/pedidos/{id} - Atualizações de pedido específico
     * /topic/mesas/{id} - Atualizações de mesa específica
     * /queue/user/{userId} - Mensagens privadas para usuário específico
     */
}

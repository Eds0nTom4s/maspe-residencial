package com.restaurante.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Interceptor para validar JWT em conexões WebSocket STOMP.
 * 
 * Valida o token JWT enviado no header "Authorization" durante o handshake STOMP CONNECT
 * e configura o principal de segurança para mensagens subsequentes.
 * 
 * Formato esperado do header: "Authorization: Bearer <token>"
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtChannelInterceptor implements ChannelInterceptor {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        
        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            log.debug("WebSocket CONNECT recebido. Validando JWT...");
            
            // Extrair token do header Authorization
            String authHeader = accessor.getFirstNativeHeader("Authorization");
            
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                
                try {
                    // Validar token
                    if (jwtTokenProvider.validateToken(token)) {
                        String username = jwtTokenProvider.getUsernameFromToken(token);
                        String roles = jwtTokenProvider.getRolesFromToken(token);
                        
                        // Converter roles string para lista de authorities
                        List<SimpleGrantedAuthority> authorities = Arrays.stream(roles.split(","))
                                .map(String::trim)
                                .map(SimpleGrantedAuthority::new)
                                .collect(Collectors.toList());
                        
                        // Criar authentication e configurar no accessor
                        UsernamePasswordAuthenticationToken authentication = 
                                new UsernamePasswordAuthenticationToken(username, null, authorities);
                        
                        accessor.setUser(authentication);
                        
                        log.info("WebSocket autenticado: usuário={}, roles={}", username, roles);
                    } else {
                        log.warn("WebSocket CONNECT com token inválido");
                        throw new IllegalArgumentException("Token JWT inválido");
                    }
                } catch (Exception e) {
                    log.error("Erro ao validar JWT no WebSocket: {}", e.getMessage());
                    throw new IllegalArgumentException("Erro ao validar token JWT", e);
                }
            } else {
                log.warn("WebSocket CONNECT sem Authorization header ou formato incorreto");
                // Permitir conexão sem autenticação (opcional - pode lançar exceção se quiser forçar autenticação)
                // throw new IllegalArgumentException("Token JWT não fornecido");
            }
        }
        
        return message;
    }
}

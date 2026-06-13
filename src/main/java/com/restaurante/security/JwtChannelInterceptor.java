package com.restaurante.security;

import io.jsonwebtoken.Claims;
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
import java.util.Collections;
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
                        Claims claims = jwtTokenProvider.getClaims(token);
                        String username = claims.getSubject();
                        String roles = claims.get("roles", String.class);
                        
                        // Converter roles string para lista de authorities
                        List<SimpleGrantedAuthority> authorities = Arrays.stream((roles != null ? roles : "").split(","))
                                .map(String::trim)
                                .filter(role -> !role.isBlank())
                                .map(SimpleGrantedAuthority::new)
                                .collect(Collectors.toList());
                        
                        // Criar authentication e configurar no accessor
                        UsernamePasswordAuthenticationToken authentication = 
                                new UsernamePasswordAuthenticationToken(username, null, authorities);
                        authentication.setDetails(toTenantStompSession(claims));
                        
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
                throw new IllegalArgumentException("Token JWT não fornecido");
            }
        } else if (accessor != null && StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            validateTenantSubscription(accessor);
        }
        
        return message;
    }

    private TenantStompSession toTenantStompSession(Claims claims) {
        Object rawTenantRoles = claims.get("tenantRoles");
        List<String> tenantRoles = Collections.emptyList();
        if (rawTenantRoles instanceof List<?> list) {
            tenantRoles = list.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .toList();
        }
        return new TenantStompSession(
                asLong(claims.get("userId")),
                asLong(claims.get("tenantId")),
                claims.get("tenantCode", String.class),
                claims.get("tokenType", String.class),
                Boolean.TRUE.equals(claims.get("platformAdmin", Boolean.class)),
                tenantRoles
        );
    }

    private void validateTenantSubscription(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        Long destinationTenantId = extractTenantId(destination);
        if (destinationTenantId == null) {
            return;
        }

        if (!(accessor.getUser() instanceof UsernamePasswordAuthenticationToken authentication)
                || !(authentication.getDetails() instanceof TenantStompSession session)) {
            log.warn("WebSocket SUBSCRIBE tenant-scoped sem sessão autenticada: destination={}", destination);
            throw new IllegalArgumentException("Sessão WebSocket não autenticada");
        }

        if (session.tenantId() == null || !"TENANT".equals(session.tokenType())) {
            log.warn("WebSocket SUBSCRIBE KDS bloqueado: token sem tenant selecionado, destination={}", destination);
            throw new IllegalArgumentException("Token tenant-scoped obrigatório para tópico KDS");
        }

        if (!destinationTenantId.equals(session.tenantId())) {
            log.warn("WebSocket SUBSCRIBE cross-tenant bloqueado: tokenTenantId={}, destination={}",
                    session.tenantId(), destination);
            throw new IllegalArgumentException("Inscrição cross-tenant bloqueada");
        }
    }

    private Long extractTenantId(String destination) {
        if (destination == null) {
            return null;
        }
        String prefix = "/topic/tenant/";
        if (!destination.startsWith(prefix)) {
            return null;
        }
        String remainder = destination.substring(prefix.length());
        int slash = remainder.indexOf('/');
        if (slash <= 0) {
            return null;
        }
        try {
            return Long.parseLong(remainder.substring(0, slash));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Tópico tenant inválido");
        }
    }

    private Long asLong(Object value) {
        if (value instanceof Long l) {
            return l;
        }
        if (value instanceof Integer i) {
            return i.longValue();
        }
        if (value instanceof Number n) {
            return n.longValue();
        }
        if (value instanceof String s && !s.isBlank()) {
            return Long.parseLong(s);
        }
        return null;
    }
}

package com.restaurante.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.dto.response.ApiResponse;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Handlers customizados para exceções de segurança
 * 
 * - JwtAuthenticationEntryPoint: Quando token ausente ou inválido (401)
 * - JwtAccessDeniedHandler: Quando usuário autenticado mas sem permissão (403)
 * 
 * ⚠️ SEGURANÇA: Mensagens genéricas para não vazar informações do sistema
 */
@Slf4j
public class JwtSecurityExceptionHandlers {

    /**
     * Handler para falhas de autenticação (401 Unauthorized)
     * Disparado quando:
     * - Token JWT ausente
     * - Token JWT inválido
     * - Token JWT expirado
     */
    @Component
    @RequiredArgsConstructor
    public static class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

        private final ObjectMapper objectMapper;

        @Override
        public void commence(
                HttpServletRequest request,
                HttpServletResponse response,
                AuthenticationException authException) throws IOException {

            log.warn("Tentativa de acesso não autorizado: {} {} - IP: {}", 
                     request.getMethod(), 
                     request.getRequestURI(),
                     request.getRemoteAddr());

            // Mensagem genérica para não vazar informações
            ApiResponse<Void> errorResponse = ApiResponse.error(
                "Autenticação necessária. Token ausente, inválido ou expirado."
            );

            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            
            objectMapper.writeValue(response.getWriter(), errorResponse);
        }
    }

    /**
     * Handler para falhas de autorização (403 Forbidden)
     * Disparado quando:
     * - Usuário autenticado mas sem permissão (role inadequada)
     * - Tentativa de acesso a recurso protegido
     */
    @Component
    @RequiredArgsConstructor
    public static class JwtAccessDeniedHandler implements AccessDeniedHandler {

        private final ObjectMapper objectMapper;

        @Override
        public void handle(
                HttpServletRequest request,
                HttpServletResponse response,
                AccessDeniedException accessDeniedException) throws IOException {

            String username = request.getUserPrincipal() != null 
                ? request.getUserPrincipal().getName() 
                : "anonymous";

            log.warn("🔐 ACESSO NEGADO: Usuário '{}' tentou acessar {} {} - IP: {}", 
                     username,
                     request.getMethod(),
                     request.getRequestURI(),
                     request.getRemoteAddr());

            // Mensagem genérica para não vazar estrutura de permissões
            ApiResponse<Void> errorResponse = ApiResponse.error(
                "Acesso negado. Você não tem permissão para acessar este recurso."
            );

            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            
            objectMapper.writeValue(response.getWriter(), errorResponse);
        }
    }
}

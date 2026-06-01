package com.restaurante.security.tenant;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Popula e limpa TenantContext por request.
 *
 * Observação (Prompt 4):
 * - Não aplica enforcement global. Apenas resolve e coloca no holder quando possível.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnBean(TenantResolver.class)
public class TenantContextFilter extends OncePerRequestFilter {

    private final TenantResolver tenantResolver;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        TenantContext previous = TenantContextHolder.get().orElse(null);
        boolean setByFilter = false;
        try {
            var resolved = tenantResolver.resolve(request);
            resolved.ifPresent(ctx -> {
                TenantContextHolder.set(ctx);
                log.debug("TenantContext resolvido: tenantId={}, source={}", ctx.tenantId(), ctx.source());
                // Sempre que resolvermos, devemos restaurar o contexto anterior ao final do request
                // (evita "pisar" em contexto pré-existente e mantém isolamento entre requests).
                // Em produção normalmente não há contexto prévio; em testes, pode haver.
                // A restauração acontece no finally.
                // Marcamos aqui para saber se houve set durante este filtro.
            });
            setByFilter = resolved.isPresent();
            filterChain.doFilter(request, response);
        } catch (com.restaurante.exception.TenantTokenStaleException e) {
            log.warn("Tenant token stale no filtro: {}", e.getMessage());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            String json = String.format(
                "{\"timestamp\":\"%s\",\"status\":401,\"error\":\"Sessão desatualizada\",\"code\":\"TENANT_TOKEN_STALE\",\"message\":\"%s\",\"path\":\"%s\"}",
                java.time.LocalDateTime.now(),
                e.getMessage().replace("\"", "\\\""),
                request.getRequestURI().replace("\"", "\\\"")
            );
            response.getWriter().write(json);
        } catch (com.restaurante.exception.TenantAccessDeniedException e) {
            log.warn("Acesso negado no filtro de tenant: {}", e.getMessage());
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            String json = String.format(
                "{\"timestamp\":\"%s\",\"status\":403,\"error\":\"Acesso negado\",\"code\":\"MEMBERSHIP_NOT_ACTIVE\",\"message\":\"%s\",\"path\":\"%s\"}",
                java.time.LocalDateTime.now(),
                e.getMessage().replace("\"", "\\\""),
                request.getRequestURI().replace("\"", "\\\"")
            );
            response.getWriter().write(json);
        } finally {
            if (setByFilter) {
                if (previous != null) {
                    TenantContextHolder.set(previous);
                } else {
                    TenantContextHolder.clear();
                }
            }
        }
    }
}

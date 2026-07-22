package com.restaurante.security.tenant;

import com.restaurante.platform.discovery.controller.DiscoveryPublicEndpoints;
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
 *
 * Endpoints pré-tenant (SANDBOX-LOCAL-004-B):
 * - /auth/tenants e /api/auth/tenants são endpoints de descoberta de tenant.
 * - Os três GETs de /v1/discovery são a fronteira pública de comerciantes.
 * - Não exigem JWT e NÃO devem ter TenantContext resolvido.
 * - Os endpoints /auth/tenants são usados antes da seleção; Discovery é
 *   global e não aceita seleção tenant por JWT ou header.
 * - O bypass é cirúrgico. Nenhum outro endpoint de /auth/** é excluído.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnBean(TenantResolver.class)
public class TenantContextFilter extends OncePerRequestFilter {

    private final TenantResolver tenantResolver;

    /** Endpoints pré-tenant autenticados e os três GETs públicos de Discovery. */
    private boolean isPreTenantAuthEndpoint(HttpServletRequest request) {
        String path = request.getRequestURI();
        return "/auth/tenants".equals(path)
                || "/api/auth/tenants".equals(path);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return isPreTenantAuthEndpoint(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (DiscoveryPublicEndpoints.matches(request)) {
            TenantContextHolder.clear();
            try {
                filterChain.doFilter(request, response);
            } finally {
                TenantContextHolder.clear();
            }
            return;
        }
        try {
            var resolved = tenantResolver.resolve(request);
            resolved.ifPresent(ctx -> {
                TenantContextHolder.set(ctx);
                log.debug("TenantContext resolvido: tenantId={}, source={}", ctx.tenantId(), ctx.source());
            });
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
            TenantContextHolder.clear();
        }
    }
}

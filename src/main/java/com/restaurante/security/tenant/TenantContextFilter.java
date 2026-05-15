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
        try {
            tenantResolver.resolve(request).ifPresent(ctx -> {
                TenantContextHolder.set(ctx);
                log.debug("TenantContext resolvido: tenantId={}, source={}", ctx.tenantId(), ctx.source());
            });
            filterChain.doFilter(request, response);
        } finally {
            TenantContextHolder.clear();
        }
    }
}

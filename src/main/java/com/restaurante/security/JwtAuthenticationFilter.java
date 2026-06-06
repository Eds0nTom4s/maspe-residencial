package com.restaurante.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filter JWT para interceptar requests e validar token no header Authorization
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final CustomUserDetailsService customUserDetailsService;
    private final JwtAuthenticationFactory jwtAuthenticationFactory;
    private final JwtUserStatusValidator jwtUserStatusValidator;

    @org.springframework.beans.factory.annotation.Value("${consuma.security.jwt.strict-user-validation:false}")
    private boolean strictUserValidation;

    @org.springframework.beans.factory.annotation.Value("${consuma.security.jwt.validate-user-active:false}")
    private boolean validateUserActive;

    @org.springframework.beans.factory.annotation.Value("${consuma.security.jwt.allow-legacy-userdetails-fallback:true}")
    private boolean allowLegacyFallback;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // Não aplicar filtro JWT para endpoints públicos
        return path.startsWith("/h2-console") || 
               path.startsWith("/api/h2-console") ||
               (path.startsWith("/api/auth/") && !path.startsWith("/api/auth/tenant/select") && !path.startsWith("/api/auth/tenants")) ||
               (path.startsWith("/auth/") && !path.startsWith("/auth/tenant/select") && !path.startsWith("/auth/tenants")) ||
               path.startsWith("/swagger-ui") ||
               path.startsWith("/v3/api-docs") ||
               path.startsWith("/actuator");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        try {
            String jwt = extractJwtFromRequest(request);

            if (StringUtils.hasText(jwt) && jwtTokenProvider.validateToken(jwt)) {
                boolean modern = jwtTokenProvider.hasModernClaims(jwt);
                if (modern) {
                    Authentication authentication = jwtAuthenticationFactory.buildAuthentication(jwt);

                    if (validateUserActive || strictUserValidation) {
                        Object principal = authentication.getPrincipal();
                        if (principal instanceof JwtPrincipal jp) {
                            jwtUserStatusValidator.validateUserStillActive(jp.getUserId());
                        } else if (strictUserValidation) {
                            // fallback conservador
                            String username = jwtTokenProvider.getUsernameFromToken(jwt);
                            customUserDetailsService.loadUserByUsername(username);
                        }
                    }

                    if (authentication instanceof UsernamePasswordAuthenticationToken upat) {
                        upat.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    }
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    log.debug("Autenticação JWT otimizada (claims) configurada para sub={}", authentication.getName());
                } else if (allowLegacyFallback) {
                    String username = jwtTokenProvider.getUsernameFromToken(jwt);

                    UserDetails userDetails = customUserDetailsService.loadUserByUsername(username);
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails,
                                    null,
                                    userDetails.getAuthorities()
                            );

                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);

                    log.debug("Autenticação JWT legacy (UserDetails) configurada para usuário: {}", username);
                } else {
                    log.debug("Token sem claims modernas e fallback legacy desabilitado; request seguirá sem autenticação.");
                }
            }
        } catch (Exception ex) {
            log.error("Não foi possível configurar autenticação de usuário no security context", ex);
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Extrai JWT do header Authorization
     */
    private String extractJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");

        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }

        return null;
    }
}

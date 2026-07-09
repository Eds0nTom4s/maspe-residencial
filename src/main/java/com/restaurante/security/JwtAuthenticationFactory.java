package com.restaurante.security;

import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Constrói Authentication a partir de claims confiáveis do JWT (token já validado).
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFactory {

    private final JwtTokenProvider jwtTokenProvider;

    public Authentication buildAuthentication(String token) {
        JwtPrincipal principal = buildPrincipal(token);
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    public JwtPrincipal buildPrincipal(String token) {
        Claims claims = jwtTokenProvider.getClaims(token);

        String username = claims.getSubject();
        Long userId = claims.get("userId", Long.class);
        String tokenType = claims.get("tokenType", String.class);
        Boolean platformAdmin = claims.get("platformAdmin", Boolean.class);

        Long tenantId = claims.get("tenantId", Long.class);
        String tenantCode = claims.get("tenantCode", String.class);

        Set<String> tenantRoles = new HashSet<>();
        Object tenantRolesObj = claims.get("tenantRoles");
        if (tenantRolesObj instanceof List<?> list) {
            for (Object o : list) {
                if (o != null) tenantRoles.add(String.valueOf(o));
            }
        }

        Collection<GrantedAuthority> authorities = buildAuthorities(claims, tenantRoles);

        Instant iat = claims.getIssuedAt() != null ? claims.getIssuedAt().toInstant() : null;
        Instant exp = claims.getExpiration() != null ? claims.getExpiration().toInstant() : null;

        String tenantUserStatus = claims.get("tenantUserStatus", String.class);

        return JwtPrincipal.builder()
                .userId(userId)
                .username(username)
                .email(claims.get("email", String.class))
                .tokenType(tokenType)
                .platformAdmin(platformAdmin != null && platformAdmin)
                .tenantId(tenantId)
                .tenantCode(tenantCode)
                .tenantRoles(tenantRoles)
                .tenantUserStatus(tenantUserStatus)
                .issuedAt(iat)
                .expiresAt(exp)
                .authorities(authorities)
                .build();
    }

    /**
     * Authorities globais (ROLE_*) vêm do claim "roles" (csv).
     * Roles de tenant (TENANT_*) são adicionadas como authorities para compatibilidade com extração por Authentication.
     */
    private Collection<GrantedAuthority> buildAuthorities(Claims claims, Set<String> tenantRoles) {
        Set<String> names = new HashSet<>();

        String rolesCsv = claims.get("roles", String.class);
        if (rolesCsv != null && !rolesCsv.isBlank()) {
            for (String r : rolesCsv.split(",")) {
                String trimmed = r != null ? r.trim() : "";
                if (!trimmed.isBlank()) names.add(trimmed);
            }
        }

        names.addAll(tenantRoles);

        List<GrantedAuthority> authorities = new ArrayList<>(names.size());
        for (String n : names) {
            authorities.add(new SimpleGrantedAuthority(n));
        }
        return authorities;
    }
}


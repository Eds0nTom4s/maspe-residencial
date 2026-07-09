package com.restaurante.security;

import lombok.Builder;
import lombok.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.util.Collection;
import java.util.Set;

/**
 * Principal leve derivado do JWT.
 *
 * Importante:
 * - Não carrega password.
 * - Não consulta banco.
 * - Serve para reduzir loadUserByUsername por request quando o token possui claims modernas.
 */
@Value
@Builder
public class JwtPrincipal implements UserDetails {

    Long userId;
    String username;
    String email;
    String tokenType; // GLOBAL | TENANT
    boolean platformAdmin;

    Long tenantId;
    String tenantCode;
    Set<String> tenantRoles;
    String tenantUserStatus; // ATIVO | SUSPENSO | INATIVO — lido do claim 'tenantUserStatus'

    Instant issuedAt;
    Instant expiresAt;

    Collection<? extends GrantedAuthority> authorities;

    public boolean isTenantScoped() {
        return "TENANT".equalsIgnoreCase(tokenType) && tenantId != null;
    }

    public boolean isGlobalToken() {
        return "GLOBAL".equalsIgnoreCase(tokenType);
    }

    @Override
    public String getPassword() {
        return null;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}


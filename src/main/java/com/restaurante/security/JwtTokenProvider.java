package com.restaurante.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Date;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Provider para geração e validação de JWT tokens
 * 
 * ⚠️ SEGURANÇA CRÍTICA:
 * - Secret deve ter mínimo 256 bits (32 caracteres) para HS256
 * - EM PRODUÇÃO: Usar variável de ambiente JWT_SECRET
 * - Expiração padrão: 1 hora (access token), 7 dias (refresh token)
 * - Algoritmo: HS256 (HMAC com SHA-256)
 */
@Component
@Slf4j
public class JwtTokenProvider {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration:3600000}") // 1 hora em ms
    private long jwtExpiration;

    @Value("${jwt.refresh-expiration:604800000}") // 7 dias em ms
    private long jwtRefreshExpiration;

    public long getExpirationMs() {
        return jwtExpiration;
    }

    /**
     * Gera token JWT a partir da autenticação
     */
    public String generateToken(Authentication authentication) {
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        String roles = userDetails.getAuthorities()
                .stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(","));
        Long userId = null;
        if (authentication.getPrincipal() instanceof com.restaurante.model.entity.User u) {
            userId = u.getId();
        }
        return generateToken(userDetails.getUsername(), roles, null, userId, "GLOBAL");
    }

    /**
     * Gera token JWT a partir de username
     */
    public String generateToken(String username) {
        return generateToken(username, null);
    }

    /**
     * Gera token JWT com roles
     */
    public String generateToken(String username, String roles) {
        return generateToken(username, roles, null);
    }

    /**
     * Gera token JWT com roles e nome da instituição
     */
    public String generateToken(String username, String roles, String instituicaoNome) {
        return generateToken(username, roles, instituicaoNome, null, null);
    }

    /**
     * Gera token JWT com claims adicionais.
     */
    public String generateToken(String username, String roles, String instituicaoNome, Long userId, String tokenType) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpiration);

        JwtBuilder builder = Jwts.builder()
                .setSubject(username)
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(getSigningKey(), SignatureAlgorithm.HS256);

        if (roles != null) {
            builder.claim("roles", roles);
        }
        if (instituicaoNome != null) {
            builder.claim("instituicao", instituicaoNome);
        }
        if (userId != null) {
            builder.claim("userId", userId);
        }
        if (tokenType != null) {
            builder.claim("tokenType", tokenType);
        }

        return builder.compact();
    }

    /**
     * Gera token tenant-scoped para uso em /api/tenant/**.
     */
    public String generateTenantScopedToken(
            com.restaurante.model.entity.User user,
            com.restaurante.model.entity.Tenant tenant,
            com.restaurante.model.enums.TenantUserRole tenantRole,
            com.restaurante.model.enums.TenantUserEstado tenantUserEstado,
            Integer tenantAccessVersion,
            String tenantPermissionsUpdatedAt
    ) {
        return generateTenantScopedToken(
                user,
                tenant,
                tenantRole,
                tenantUserEstado,
                tenantAccessVersion,
                tenantPermissionsUpdatedAt,
                false
        );
    }

    public String generateTenantScopedToken(
            com.restaurante.model.entity.User user,
            com.restaurante.model.entity.Tenant tenant,
            com.restaurante.model.enums.TenantUserRole tenantRole,
            com.restaurante.model.enums.TenantUserEstado tenantUserEstado,
            Integer tenantAccessVersion,
            String tenantPermissionsUpdatedAt,
            boolean platformAdmin
    ) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpiration);
        String roles = user.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.joining(","));

        List<String> tenantRoles = tenantRole != null ? List.of(tenantRole.name()) : List.of();

        var builder = Jwts.builder()
                .setSubject(user.getUsername())
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .claim("roles", roles)
                .claim("userId", user.getId())
                .claim("tokenType", "TENANT")
                .claim("tenantId", tenant.getId())
                .claim("tenantCode", tenant.getTenantCode())
                .claim("tenantRoles", tenantRoles)
                .claim("tenantUserStatus", tenantUserEstado != null ? tenantUserEstado.name() : null)
                .claim("platformAdmin", platformAdmin)
                .signWith(getSigningKey(), SignatureAlgorithm.HS256);

        if (tenantAccessVersion != null) {
            builder.claim("tenantAccessVersion", tenantAccessVersion);
        }
        if (tenantPermissionsUpdatedAt != null) {
            builder.claim("tenantPermissionsUpdatedAt", tenantPermissionsUpdatedAt);
        }

        return builder.compact();
    }

    /**
     * Gera refresh token
     */
    public String generateRefreshToken(String username) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtRefreshExpiration);

        return Jwts.builder()
                .setSubject(username)
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .claim("type", "refresh")
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Extrai username do token
     */
    public String getUsernameFromToken(String token) {
        return getClaims(token).getSubject();
    }

    /**
     * Extrai roles do token
     */
    public String getRolesFromToken(String token) {
        return getClaims(token).get("roles", String.class);
    }

    public Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean hasModernClaims(String token) {
        try {
            Claims c = getClaims(token);
            return c.get("userId", Long.class) != null && c.get("roles", String.class) != null;
        } catch (Exception e) {
            return false;
        }
    }

    public String getTokenType(String token) {
        return getClaims(token).get("tokenType", String.class);
    }

    /**
     * Valida token JWT
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (SecurityException ex) {
            log.error("Invalid JWT signature");
        } catch (MalformedJwtException ex) {
            log.error("Invalid JWT token");
        } catch (ExpiredJwtException ex) {
            log.error("Expired JWT token");
        } catch (UnsupportedJwtException ex) {
            log.error("Unsupported JWT token");
        } catch (IllegalArgumentException ex) {
            log.error("JWT claims string is empty");
        }
        return false;
    }

    /**
     * Verifica se é refresh token
     */
    public boolean isRefreshToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            return "refresh".equals(claims.get("type", String.class));
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Obtém chave de assinatura
     */
    private SecretKey getSigningKey() {
        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}

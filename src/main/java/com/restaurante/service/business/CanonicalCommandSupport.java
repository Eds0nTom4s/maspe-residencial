package com.restaurante.service.business;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.restaurante.exception.BusinessException;
import com.restaurante.security.JwtPrincipal;
import com.restaurante.security.tenant.TenantContext;
import com.restaurante.security.tenant.TenantContextHolder;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.TreeSet;
import java.util.UUID;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class CanonicalCommandSupport {
    private final ObjectMapper objectMapper;
    private final EntityManager entityManager;

    public String json(Object value) {
        try {
            return canonicalMapper().writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new BusinessException("Não foi possível normalizar o comando canónico.");
        }
    }

    public <T> T read(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException ex) {
            throw new BusinessException("Payload persistido de provisionamento inválido.");
        }
    }

    public String fingerprint(Object value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(json(value).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public void requireKey(String key) {
        if (key == null || key.isBlank() || key.length() > 100) {
            throw new BusinessException("Idempotency-Key obrigatória e limitada a 100 caracteres.");
        }
    }

    public void lock(String scope) {
        entityManager.createNativeQuery("select pg_advisory_xact_lock(hashtextextended(cast(:scope as text), 0))")
                .setParameter("scope", scope)
                .getSingleResult();
    }

    public Actor actor(HttpServletRequest request) {
        TenantContext context = TenantContextHolder.get().orElse(null);
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Long userId = context != null ? context.userId() : null;
        if (userId == null && auth != null && auth.getPrincipal() instanceof JwtPrincipal principal) {
            userId = principal.getUserId();
        }
        TreeSet<String> roles = new TreeSet<>();
        if (context != null && context.roles() != null) roles.addAll(context.roles());
        if (auth != null && auth.getAuthorities() != null) {
            auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).forEach(roles::add);
        }
        String correlationId = Optional.ofNullable(request.getHeader("X-Correlation-Id"))
                .map(String::trim).filter(v -> !v.isBlank()).orElse(UUID.randomUUID().toString());
        if (correlationId.length() > 120) {
            throw new BusinessException("X-Correlation-Id não pode exceder 120 caracteres.");
        }
        String ip = Optional.ofNullable(request.getHeader("X-Forwarded-For"))
                .map(v -> v.split(",")[0].trim()).orElse(request.getRemoteAddr());
        if (ip != null && ip.length() > 64) {
            throw new BusinessException("X-Forwarded-For/IP inválido: máximo de 64 caracteres.");
        }
        return new Actor(userId, String.join(",", roles), correlationId, ip,
                truncate(request.getHeader("User-Agent"), 500));
    }

    /** NIF ausente é aceite; quando presente, pontuação comum é removida e o valor fica em maiúsculas. */
    public String normalizeNif(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim().toUpperCase(Locale.ROOT).replaceAll("[\\s./-]", "");
        if (!normalized.matches("[A-Z0-9]{5,30}")) {
            throw new BusinessException("NIF inválido; use entre 5 e 30 caracteres alfanuméricos.");
        }
        return normalized;
    }

    public String sanitize(Throwable error) {
        String message = error == null ? "Falha de provisionamento." : error.getMessage();
        return truncate(message == null ? "Falha de provisionamento." : message, 500);
    }

    private ObjectMapper canonicalMapper() {
        return objectMapper.copy()
                .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    }

    private static String truncate(String value, int max) {
        if (value == null || value.length() <= max) return value;
        return value.substring(0, max);
    }

    public record Actor(Long userId, String roles, String correlationId, String ip, String userAgent) {}
}

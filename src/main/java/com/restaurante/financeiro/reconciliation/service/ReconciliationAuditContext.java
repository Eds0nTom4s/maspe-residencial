package com.restaurante.financeiro.reconciliation.service;
import com.restaurante.security.tenant.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import java.util.*;
@Component
public class ReconciliationAuditContext {
    public Actor current(HttpServletRequest request) {
        TenantContext c=TenantContextHolder.require();
        String corr=Optional.ofNullable(request.getHeader("X-Correlation-Id")).filter(s->!s.isBlank()).orElse(UUID.randomUUID().toString());
        String ip=Optional.ofNullable(request.getHeader("X-Forwarded-For")).map(s->s.split(",")[0].trim()).orElse(request.getRemoteAddr());
        return new Actor(c.userId(), String.join(",", new TreeSet<>(c.roles()==null?Set.of():c.roles())), c.platformAdmin()?"PLATFORM_ADMIN":"TENANT", ip, request.getHeader("User-Agent"), corr);
    }
    public record Actor(Long userId,String roles,String origin,String ip,String userAgent,String correlationId) {}
}

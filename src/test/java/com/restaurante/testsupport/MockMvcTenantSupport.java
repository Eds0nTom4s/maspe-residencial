package com.restaurante.testsupport;

import com.restaurante.model.entity.User;
import com.restaurante.security.tenant.TenantResolver;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

public final class MockMvcTenantSupport {

    private MockMvcTenantSupport() {
    }

    public static RequestPostProcessor tenantHeaders(Long tenantId, String tenantCode) {
        return request -> {
            if (tenantId != null) {
                request.addHeader(TenantResolver.HEADER_TENANT_ID, String.valueOf(tenantId));
            }
            if (tenantCode != null && !tenantCode.isBlank()) {
                request.addHeader(TenantResolver.HEADER_TENANT_CODE, tenantCode);
            }
            return request;
        };
    }

    public static RequestPostProcessor authUser(User user, String... authorities) {
        Objects.requireNonNull(user, "user");
        List<GrantedAuthority> granted = Arrays.stream(authorities == null ? new String[0] : authorities)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .distinct()
                .map(s -> (GrantedAuthority) new SimpleGrantedAuthority(s))
                .toList();

        Authentication auth = new UsernamePasswordAuthenticationToken(user, "N/A", granted);
        return authentication(auth);
    }
}

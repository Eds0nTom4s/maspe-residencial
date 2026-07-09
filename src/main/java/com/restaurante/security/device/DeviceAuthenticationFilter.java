package com.restaurante.security.device;

import com.restaurante.model.enums.DeviceCapability;
import com.restaurante.service.device.DeviceAuthService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Autentica requests com header "Authorization: Device <token>".
 *
 * Observação:
 * - Não substitui JWT humano (Bearer).
 * - Se já existir Authentication no contexto, não sobrescreve.
 */
public class DeviceAuthenticationFilter extends OncePerRequestFilter {

    private final DeviceAuthService deviceAuthService;

    public DeviceAuthenticationFilter(DeviceAuthService deviceAuthService) {
        this.deviceAuthService = deviceAuthService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        boolean deviceEligiblePath =
                (path != null && (path.startsWith("/device/") || path.startsWith("/api/device/") ||
                        path.startsWith("/tenant/producao/") || path.startsWith("/api/tenant/producao/")));

        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            String auth = request.getHeader("Authorization");
            if (deviceEligiblePath && auth != null && auth.regionMatches(true, 0, "Device ", 0, "Device ".length())) {
                String ua = request.getHeader("User-Agent");
                String ip = request.getRemoteAddr();

                DevicePrincipal principal = deviceAuthService.authenticateDeviceHeader(auth, ua, ip);
                List<GrantedAuthority> authorities = buildAuthorities(principal);
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(principal, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }

        filterChain.doFilter(request, response);
    }

    private List<GrantedAuthority> buildAuthorities(DevicePrincipal principal) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_DEVICE"));
        if (principal != null && principal.tipo() != null) {
            authorities.add(new SimpleGrantedAuthority("DEVICE_" + principal.tipo().name()));
        }
        if (principal != null && principal.capabilities() != null) {
            for (DeviceCapability cap : principal.capabilities()) {
                authorities.add(new SimpleGrantedAuthority("CAP_" + cap.name()));
            }
        }
        return authorities;
    }
}

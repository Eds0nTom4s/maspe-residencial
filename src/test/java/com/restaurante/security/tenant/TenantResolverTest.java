package com.restaurante.security.tenant;

import com.restaurante.exception.BusinessException;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.TenantUser;
import com.restaurante.model.entity.User;
import com.restaurante.model.enums.Role;
import com.restaurante.model.enums.TenantEstado;
import com.restaurante.model.enums.TenantUserEstado;
import com.restaurante.repository.TenantRepository;
import com.restaurante.repository.TenantUserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantResolverTest {

    @Mock
    private TenantRepository tenantRepository;
    @Mock
    private TenantUserRepository tenantUserRepository;

    private TenantResolver tenantResolver;

    @BeforeEach
    void cleanup() {
        SecurityContextHolder.clearContext();
        tenantResolver = new TenantResolver(
                new org.springframework.beans.factory.ObjectProvider<>() {
                    @Override public TenantRepository getObject(Object... args) { return tenantRepository; }
                    @Override public TenantRepository getObject() { return tenantRepository; }
                    @Override public TenantRepository getIfAvailable() { return tenantRepository; }
                    @Override public TenantRepository getIfUnique() { return tenantRepository; }
                },
                new org.springframework.beans.factory.ObjectProvider<>() {
                    @Override public TenantUserRepository getObject(Object... args) { return tenantUserRepository; }
                    @Override public TenantUserRepository getObject() { return tenantUserRepository; }
                    @Override public TenantUserRepository getIfAvailable() { return tenantUserRepository; }
                    @Override public TenantUserRepository getIfUnique() { return tenantUserRepository; }
                }
        );
    }

    @Test
    void shouldAutoResolveWhenUserHasSingleActiveMembership() {
        User principal = User.builder()
                .username("u1")
                .password("x")
                .telefone("+244900000100")
                .roles(Set.of(Role.ROLE_ATENDENTE))
                .ativo(true)
                .build();
        principal.setId(10L);

        Tenant tenant = new Tenant();
        tenant.setId(1L);
        tenant.setTenantCode("T1");
        tenant.setEstado(TenantEstado.ATIVO);

        TenantUser membership = new TenantUser();
        membership.setTenant(tenant);
        membership.setUser(principal);
        membership.setEstado(TenantUserEstado.ATIVO);

        when(tenantUserRepository.findByUserIdAndEstado(10L, TenantUserEstado.ATIVO)).thenReturn(List.of(membership));
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );

        HttpServletRequest req = new MockHttpServletRequest();
        TenantContext ctx = tenantResolver.resolve(req).orElseThrow();
        assertEquals(1L, ctx.tenantId());
        assertEquals("T1", ctx.tenantCode());
        assertEquals(TenantResolutionSource.JWT, ctx.source());
    }

    @Test
    void shouldRequireHeaderWhenUserHasMultipleMemberships() {
        User principal = User.builder()
                .username("u1")
                .password("x")
                .telefone("+244900000101")
                .roles(Set.of(Role.ROLE_ATENDENTE))
                .ativo(true)
                .build();
        principal.setId(10L);

        when(tenantUserRepository.findByUserIdAndEstado(10L, TenantUserEstado.ATIVO)).thenReturn(List.of(new TenantUser(), new TenantUser()));

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );

        HttpServletRequest req = new MockHttpServletRequest();
        assertTrue(tenantResolver.resolve(req).isEmpty());
    }

    @Test
    void shouldResolveByHeaderAndValidateMembership() {
        User principal = User.builder()
                .username("u1")
                .password("x")
                .telefone("+244900000102")
                .roles(Set.of(Role.ROLE_ATENDENTE))
                .ativo(true)
                .build();
        principal.setId(10L);

        Tenant tenant = new Tenant();
        tenant.setId(7L);
        tenant.setTenantCode("T7");
        tenant.setEstado(TenantEstado.ATIVO);

        when(tenantRepository.findById(7L)).thenReturn(Optional.of(tenant));
        when(tenantUserRepository.existsByTenantIdAndUserIdAndEstado(7L, 10L, TenantUserEstado.ATIVO)).thenReturn(true);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader(TenantResolver.HEADER_TENANT_ID, "7");
        TenantContext ctx = tenantResolver.resolve(req).orElseThrow();
        assertEquals(7L, ctx.tenantId());
    }

    @Test
    void shouldAllowPlatformAdminToSelectTenantWithoutMembership() {
        User principal = User.builder()
                .username("admin")
                .password("x")
                .telefone("+244900000200")
                .roles(Set.of(Role.ROLE_ADMIN))
                .ativo(true)
                .build();
        principal.setId(99L);

        Tenant tenant = new Tenant();
        tenant.setId(5L);
        tenant.setTenantCode("T5");
        tenant.setEstado(TenantEstado.ATIVO);

        when(tenantRepository.findById(5L)).thenReturn(Optional.of(tenant));

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader(TenantResolver.HEADER_TENANT_ID, "5");
        TenantContext ctx = tenantResolver.resolve(req).orElseThrow();
        assertTrue(ctx.platformAdmin());
        assertTrue(ctx.selectedByPlatformAdmin());
        assertEquals(TenantResolutionSource.PLATFORM_ADMIN_SELECTION, ctx.source());
    }

    @Test
    void shouldFailOnInvalidTenantHeader() {
        User principal = User.builder()
                .username("u1")
                .password("x")
                .telefone("+244900000103")
                .roles(Set.of(Role.ROLE_ATENDENTE))
                .ativo(true)
                .build();
        principal.setId(10L);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader(TenantResolver.HEADER_TENANT_ID, "abc");
        assertThrows(BusinessException.class, () -> tenantResolver.resolve(req));
    }
}

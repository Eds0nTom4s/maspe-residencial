package com.restaurante.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.dto.request.ProvisionarTenantRequest;
import com.restaurante.dto.request.SelectTenantRequest;
import com.restaurante.dto.response.ProvisionarTenantResponse;
import com.restaurante.model.entity.User;
import com.restaurante.model.enums.TenantTipo;
import com.restaurante.security.tenant.TenantContext;
import com.restaurante.security.tenant.TenantContextHolder;
import com.restaurante.security.tenant.TenantResolutionSource;
import com.restaurante.service.TenantProvisioningService;
import com.restaurante.testsupport.PostgresTestcontainersConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.main.web-application-type=servlet",
                "jwt.secret=0123456789ABCDEF0123456789ABCDEF",
                "consuma.security.jwt.allow-legacy-userdetails-fallback=true",
                "consuma.security.jwt.strict-user-validation=false",
                "consuma.security.jwt.validate-user-active=false"
        }
)
@AutoConfigureMockMvc(addFilters = true)
@ActiveProfiles("it-postgres")
class JwtAuthenticationFilterNoUserLookupIT extends PostgresTestcontainersConfig {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired TenantProvisioningService provisioningService;
    @Autowired com.restaurante.repository.UserRepository userRepository;
    @Autowired JwtTokenProvider jwtTokenProvider;

    @MockBean CustomUserDetailsService customUserDetailsService;

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    @Test
    void modernGlobalToken_authenticatesWithoutLoadUserByUsername() throws Exception {
        ProvisionarTenantResponse provisioned = provisionTenant("banca-authopt", "BAO", "owner-authopt@a.com");

        User owner = userRepository.findById(provisioned.getOwnerUserId()).orElseThrow();

        // token "moderno": inclui userId + tokenType
        String token = jwtTokenProvider.generateToken(owner.getUsername(), "ROLE_GERENTE", null, owner.getId(), "GLOBAL");

        String resp = mockMvc.perform(post("/auth/tenant/select")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(SelectTenantRequest.builder().tenantId(provisioned.getTenantId()).build())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(resp);
        org.assertj.core.api.Assertions.assertThat(json.at("/success").asBoolean()).isTrue();

        verify(customUserDetailsService, never()).loadUserByUsername(anyString());
    }

    @Test
    void legacyToken_usesLoadUserByUsernameFallbackWhenEnabled() throws Exception {
        ProvisionarTenantResponse provisioned = provisionTenant("banca-authopt-legacy", "BAL", "owner-authopt-legacy@a.com");

        User owner = userRepository.findById(provisioned.getOwnerUserId()).orElseThrow();

        // token legacy: sem userId/tokenType
        String token = jwtTokenProvider.generateToken(owner.getUsername(), "ROLE_GERENTE");

        when(customUserDetailsService.loadUserByUsername(owner.getUsername())).thenReturn(owner);

        mockMvc.perform(post("/auth/tenant/select")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(SelectTenantRequest.builder().tenantId(provisioned.getTenantId()).build())))
                .andExpect(status().isOk());

        verify(customUserDetailsService, times(1)).loadUserByUsername(owner.getUsername());
    }

    private ProvisionarTenantResponse provisionTenant(String slug, String tenantCode, String ownerEmail) {
        TenantContextHolder.set(new TenantContext(
                null, null, 1L, Set.of("ROLE_ADMIN"),
                TenantResolutionSource.JWT, true, false
        ));

        String phone = "+244900" + Math.abs(slug.hashCode() % 1_000_000);

        return provisioningService.provisionar(
                ProvisionarTenantRequest.builder()
                        .tenant(ProvisionarTenantRequest.TenantInfo.builder()
                                .nome("Tenant " + slug)
                                .slug(slug)
                                .tenantCode(tenantCode)
                                .tipo(TenantTipo.VENDEDOR_RUA)
                                .build())
                        .planoCodigo("PILOTO")
                        .templateCodigo("VENDEDOR_RUA")
                        .instituicao(ProvisionarTenantRequest.InstituicaoInfo.builder()
                                .nome("Inst " + slug)
                                .sigla(tenantCode)
                                .build())
                        .responsavel(ProvisionarTenantRequest.ResponsavelInfo.builder()
                                .email(ownerEmail)
                                .telefone(phone)
                                .criarUsuario(true)
                                .build())
                        .build()
        );
    }
}

package com.restaurante.tenantadmin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.dto.request.ProvisionarTenantRequest;
import com.restaurante.dto.response.ProvisionarTenantResponse;
import com.restaurante.model.entity.TenantUser;
import com.restaurante.model.entity.User;
import com.restaurante.model.enums.Role;
import com.restaurante.model.enums.TenantTipo;
import com.restaurante.model.enums.TenantUserEstado;
import com.restaurante.model.enums.TenantUserRole;
import com.restaurante.repository.TenantRepository;
import com.restaurante.repository.TenantUserRepository;
import com.restaurante.repository.UserRepository;
import com.restaurante.security.JwtTokenProvider;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "spring.main.web-application-type=servlet"
)
@AutoConfigureMockMvc
@ActiveProfiles("it-postgres")
class TenantQrPrincipalControllerIT extends PostgresTestcontainersConfig {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @Autowired TenantProvisioningService provisioningService;
    @Autowired UserRepository userRepository;
    @Autowired TenantUserRepository tenantUserRepository;
    @Autowired TenantRepository tenantRepository;
    @Autowired JwtTokenProvider jwtTokenProvider;

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    @Test
    void unauthenticated_getPrincipalQr_returns401() throws Exception {
        mockMvc.perform(get("/tenant/qr/principal").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tenant_with_principal_qr_returns200_and_no_sensitive_fields() throws Exception {
        ProvisionarTenantResponse prov = provisionarTenant("t-prq");

        User u = createUser("owner@t.com", "+244900000010");
        linkTenantUser(prov.getTenantId(), u.getId(), TenantUserRole.TENANT_OWNER, TenantUserEstado.ATIVO);
        String token = tenantToken(prov.getTenantId(), u.getId(), TenantUserRole.TENANT_OWNER, TenantUserEstado.ATIVO);

        String resp = mockMvc.perform(get("/tenant/qr/principal")
                        .header("Authorization", "Bearer " + token)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode root = objectMapper.readTree(resp);
        assertThat(root.path("success").asBoolean()).isTrue();
        JsonNode data = root.path("data");
        assertThat(data.path("qrCodeId").isNumber()).isTrue();
        assertThat(data.path("qrUrlPublica").isTextual()).isTrue();
        // sensitive fields must not be present
        assertThat(data.has("token")).isFalse();
        assertThat(data.has("qrToken")).isFalse();
        assertThat(data.has("secret")).isFalse();
    }

    private String tenantToken(Long tenantId, Long userId, TenantUserRole role, TenantUserEstado estado) {
        var tenant = tenantRepository.findById(tenantId).orElseThrow();
        var user = userRepository.findById(userId).orElseThrow();
        return jwtTokenProvider.generateTenantScopedToken(
                user,
                tenant,
                role,
                estado,
                1,
                null
        );
    }

    private ProvisionarTenantResponse provisionarTenant(String slugBase) {
        String suffix = String.valueOf(Math.abs(System.nanoTime() % 100_000L));
        String slug = slugBase + "-" + suffix;
        TenantContextHolder.set(new TenantContext(
                null, null, 1L, Set.of(Role.ROLE_ADMIN.name()),
                TenantResolutionSource.JWT, true, false
        ));

        String code = slug.replace("-", "").toUpperCase();
        if (code.length() > 10) code = code.substring(0, 10);

        return provisioningService.provisionar(
                ProvisionarTenantRequest.builder()
                        .tenant(ProvisionarTenantRequest.TenantInfo.builder()
                                .nome("Tenant " + slug)
                                .slug(slug)
                                .tenantCode(code)
                                .tipo(TenantTipo.RESTAURANTE)
                                .build())
                        .planoCodigo("PILOTO")
                        .templateCodigo("RESTAURANTE_SIMPLES")
                        .instituicao(ProvisionarTenantRequest.InstituicaoInfo.builder()
                                .nome("Inst " + slug)
                                .sigla(slug.substring(0, Math.min(4, slug.length())).toUpperCase())
                                .build())
                        .responsavel(ProvisionarTenantRequest.ResponsavelInfo.builder()
                                .email(slug + "@owner.com")
                                .telefone("+24491" + String.format("%07d", Math.abs(System.nanoTime() % 10_000_000L)))
                                .criarUsuario(true)
                                .build())
                        .opcoes(ProvisionarTenantRequest.OpcoesProvisionamento.builder()
                                .criarMesas(true)
                                .quantidadeMesas(1)
                                .criarQrPorMesa(true)
                                .criarQrPrincipal(true)
                                .build())
                        .build()
        );
    }

    private User createUser(String email, String telefone) {
        String suffix = String.valueOf(Math.abs(System.nanoTime() % 100_000L));
        String uniqueEmail = email.replace("@", "-" + suffix + "@");
        String uniqueTelefone = "+24492" + String.format("%07d", Math.abs(System.nanoTime() % 10_000_000L));
        User u = new User();
        u.setUsername(uniqueEmail);
        u.setPassword("x");
        u.setEmail(uniqueEmail);
        u.setTelefone(uniqueTelefone);
        u.setRoles(Set.of(Role.ROLE_GERENTE));
        u.setAtivo(true);
        return userRepository.saveAndFlush(u);
    }

    private void linkTenantUser(Long tenantId, Long userId, TenantUserRole role, TenantUserEstado estado) {
        var tenant = tenantRepository.findById(tenantId).orElseThrow();
        var user = userRepository.findById(userId).orElseThrow();
        TenantUser tu = new TenantUser();
        tu.setTenant(tenant);
        tu.setUser(user);
        tu.setRole(role);
        tu.setEstado(estado);
        tenantUserRepository.saveAndFlush(tu);
    }
}

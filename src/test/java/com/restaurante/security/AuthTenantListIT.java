package com.restaurante.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.dto.request.ProvisionarTenantRequest;
import com.restaurante.dto.request.SelectTenantRequest;
import com.restaurante.dto.response.ProvisionarTenantResponse;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.TenantUser;
import com.restaurante.model.entity.User;
import com.restaurante.model.enums.Role;
import com.restaurante.model.enums.TenantTipo;
import com.restaurante.model.enums.TenantUserEstado;
import com.restaurante.model.enums.TenantUserRole;
import com.restaurante.repository.TenantRepository;
import com.restaurante.repository.TenantUserRepository;
import com.restaurante.repository.UserRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testes de integração para GET /auth/tenants — listagem de tenants acessíveis
 * ao usuário autenticado.
 *
 * Cenários:
 * 1. Sem token → 401
 * 2. Usuário com tenant vinculado → retorna apenas tenants acessíveis (não os
 * de outros)
 * 3. Usuário sem vínculos → 200 com lista vazia
 * 4. Tenant inativo não aparece (estado != ATIVO)
 * 5. Fluxo completo: login → listar → selecionar tenant
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
                "spring.main.web-application-type=servlet",
                "jwt.secret=0123456789ABCDEF0123456789ABCDEF",
                "jwt.expiration=3600000"
})
@AutoConfigureMockMvc(addFilters = true)
@ActiveProfiles("it-postgres")
class AuthTenantListIT extends PostgresTestcontainersConfig {

        @Autowired
        MockMvc mockMvc;
        @Autowired
        ObjectMapper objectMapper;
        @Autowired
        TenantProvisioningService provisioningService;
        @Autowired
        UserRepository userRepository;
        @Autowired
        TenantUserRepository tenantUserRepository;
        @Autowired
        TenantRepository tenantRepository;
        @Autowired
        JwtTokenProvider jwtTokenProvider;

        @AfterEach
        void clear() {
                TenantContextHolder.clear();
        }

        // =============================================================
        // Cenário 1: Sem token → 401
        // =============================================================
        @Test
        void listarTenants_semToken_retorna401() throws Exception {
                mockMvc.perform(get("/auth/tenants")
                                .accept(MediaType.APPLICATION_JSON))
                                .andExpect(status().isUnauthorized());
        }

        // =============================================================
        // Cenário 2: Usuário com tenant vinculado → retorna apenas seus tenants
        // =============================================================
        @Test
        void listarTenants_usuarioComTenant_retornaApenasTenantsAcessiveis() throws Exception {
                String suffix = String.valueOf(Math.abs(System.nanoTime() % 1_000_000L));

                // Provisionar dois tenants distintos
                ProvisionarTenantResponse tenantA = provisionTenant("atl-a-" + suffix, "ATLA" + suffix.substring(0, 4),
                                "owner-a-" + suffix + "@a.com");
                ProvisionarTenantResponse tenantB = provisionTenant("atl-b-" + suffix, "ATLB" + suffix.substring(0, 4),
                                "owner-b-" + suffix + "@b.com");

                // Criar usuário avulso e vincular apenas ao tenantA
                User user = createUser("user-atl-" + suffix + "@test.com");
                linkTenantUser(tenantA.getTenantId(), user.getId(), TenantUserRole.TENANT_ADMIN,
                                TenantUserEstado.ATIVO);
                // NÃO vincular ao tenantB

                String globalToken = jwtTokenProvider.generateToken(user.getUsername(), "ROLE_GERENTE", null,
                                user.getId(), "GLOBAL");

                String resp = mockMvc.perform(get("/auth/tenants")
                                .header("Authorization", "Bearer " + globalToken)
                                .accept(MediaType.APPLICATION_JSON))
                                .andExpect(status().isOk())
                                .andReturn().getResponse().getContentAsString();

                JsonNode json = objectMapper.readTree(resp);
                assertThat(json.at("/success").asBoolean()).isTrue();

                JsonNode data = json.at("/data");
                assertThat(data.isArray()).isTrue();

                // Deve conter tenantA
                boolean foundA = false;
                boolean foundB = false;
                for (JsonNode item : data) {
                        long tid = item.at("/tenantId").asLong();
                        if (tid == tenantA.getTenantId())
                                foundA = true;
                        if (tid == tenantB.getTenantId())
                                foundB = true;
                }
                assertThat(foundA).as("Deve conter tenantA (vinculado)").isTrue();
                assertThat(foundB).as("Não deve conter tenantB (não vinculado)").isFalse();

                // Verificar estrutura do item retornado
                JsonNode firstItem = data.get(0);
                assertThat(firstItem.has("tenantId")).isTrue();
                assertThat(firstItem.has("tenantCode")).isTrue();
                assertThat(firstItem.has("slug")).isTrue();
                assertThat(firstItem.has("nome")).isTrue();
                assertThat(firstItem.has("estado")).isTrue();
                assertThat(firstItem.has("ativo")).isTrue();
                assertThat(firstItem.has("roles")).isTrue();
                assertThat(firstItem.has("principal")).isTrue();

                // Verificar que 'principal' é true no primeiro item
                assertThat(firstItem.at("/principal").asBoolean()).isTrue();

                // Verificar que nenhum campo sensível está exposto
                assertThat(firstItem.has("password")).isFalse();
                assertThat(firstItem.has("jwtSecret")).isFalse();
                assertThat(firstItem.has("nif")).isFalse();
        }

        // =============================================================
        // Cenário 3: Usuário sem vínculos → 200 com lista vazia
        // =============================================================
        @Test
        void listarTenants_usuarioSemTenant_retornaListaVazia() throws Exception {
                String suffix = String.valueOf(Math.abs(System.nanoTime() % 1_000_000L));
                User user = createUser("notenants-" + suffix + "@test.com");
                String globalToken = jwtTokenProvider.generateToken(user.getUsername(), "ROLE_GERENTE", null,
                                user.getId(), "GLOBAL");

                String resp = mockMvc.perform(get("/auth/tenants")
                                .header("Authorization", "Bearer " + globalToken)
                                .accept(MediaType.APPLICATION_JSON))
                                .andExpect(status().isOk())
                                .andReturn().getResponse().getContentAsString();

                JsonNode json = objectMapper.readTree(resp);
                assertThat(json.at("/success").asBoolean()).isTrue();
                assertThat(json.at("/data").isArray()).isTrue();
                assertThat(json.at("/data").size()).isEqualTo(0);
        }

        // =============================================================
        // Cenário 4: Tenant inativo não aparece
        // =============================================================
        @Test
        void listarTenants_naoRetornaTenantInativo() throws Exception {
                String suffix = String.valueOf(Math.abs(System.nanoTime() % 1_000_000L));

                // Provisionar tenant
                ProvisionarTenantResponse prov = provisionTenant("inativo-" + suffix, "INAT" + suffix.substring(0, 4),
                                "owner-in-" + suffix + "@a.com");

                User user = createUser("usr-in-" + suffix + "@test.com");
                linkTenantUser(prov.getTenantId(), user.getId(), TenantUserRole.TENANT_ADMIN, TenantUserEstado.ATIVO);

                // Suspender tenant diretamente no repositório
                Tenant tenant = tenantRepository.findById(prov.getTenantId()).orElseThrow();
                tenant.setEstado(com.restaurante.model.enums.TenantEstado.SUSPENSO);
                tenantRepository.saveAndFlush(tenant);

                // Limpar TenantContext para garantir que o filtro não resolve tenant suspenso
                // do contexto
                TenantContextHolder.clear();

                // Usar token moderno com userId para evitar fallback legacy
                String globalToken = jwtTokenProvider.generateToken(user.getUsername(), "ROLE_GERENTE", null,
                                user.getId(), "GLOBAL");

                String resp = mockMvc.perform(get("/auth/tenants")
                                .header("Authorization", "Bearer " + globalToken)
                                .accept(MediaType.APPLICATION_JSON))
                                .andExpect(status().isOk())
                                .andReturn().getResponse().getContentAsString();

                JsonNode json = objectMapper.readTree(resp);
                assertThat(json.at("/success").asBoolean()).isTrue();
                // Tenant suspenso não deve aparecer
                assertThat(json.at("/data").size()).isEqualTo(0);
        }

        // =============================================================
        // Cenário 5: Fluxo completo — login → listar tenants → tenant select
        // =============================================================
        @Test
        void listarTenants_fluxoCompleto_loginListarSelecionarTenant() throws Exception {
                String suffix = String.valueOf(Math.abs(System.nanoTime() % 1_000_000L));
                ProvisionarTenantResponse prov = provisionTenant("fluxo-" + suffix, "FLUX" + suffix.substring(0, 4),
                                "owner-fl-" + suffix + "@a.com");

                // Owner do tenant tem vínculo TENANT_OWNER criado pelo provisioning
                User owner = userRepository.findById(prov.getOwnerUserId()).orElseThrow();
                String globalToken = jwtTokenProvider.generateToken(
                                owner.getUsername(), "ROLE_GERENTE", null, owner.getId(), "GLOBAL");

                // 1. Listar tenants
                String listResp = mockMvc.perform(get("/auth/tenants")
                                .header("Authorization", "Bearer " + globalToken)
                                .accept(MediaType.APPLICATION_JSON))
                                .andExpect(status().isOk())
                                .andReturn().getResponse().getContentAsString();

                JsonNode listJson = objectMapper.readTree(listResp);
                assertThat(listJson.at("/success").asBoolean()).isTrue();
                assertThat(listJson.at("/data").size()).isGreaterThan(0);

                // Pegar tenantId do resultado
                long tenantId = listJson.at("/data/0/tenantId").asLong();
                assertThat(tenantId).isEqualTo(prov.getTenantId());

                // 2. Selecionar tenant com o tenantId obtido da listagem
                String selectResp = mockMvc.perform(post("/auth/tenant/select")
                                .header("Authorization", "Bearer " + globalToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(new SelectTenantRequest(tenantId))))
                                .andExpect(status().isOk())
                                .andReturn().getResponse().getContentAsString();

                JsonNode selectJson = objectMapper.readTree(selectResp);
                assertThat(selectJson.at("/success").asBoolean()).isTrue();
                assertThat(selectJson.at("/data/tenantId").asLong()).isEqualTo(tenantId);
                assertThat(selectJson.at("/data/accessToken").asText()).isNotBlank();

                // Verificar que o token tenant é válido e contém o tenantId correto
                String tenantToken = selectJson.at("/data/accessToken").asText();
                var claims = jwtTokenProvider.getClaims(tenantToken);
                assertThat(claims.get("tokenType", String.class)).isEqualTo("TENANT");
                assertThat(claims.get("tenantId", Long.class)).isEqualTo(tenantId);
        }

        @Test
        void listarTenants_multiRoleAgregaUmaOpcaoComRolesOrdenadas() throws Exception {
                String suffix = String.valueOf(Math.abs(System.nanoTime() % 1_000_000L));
                ProvisionarTenantResponse provisioned = provisionTenant(
                                "multi-role-" + suffix,
                                "MR" + suffix,
                                "owner-mr-" + suffix + "@a.com");
                User user = createUser("multi-role-user-" + suffix + "@test.com");
                linkTenantUser(provisioned.getTenantId(), user.getId(), TenantUserRole.TENANT_OPERATOR,
                                TenantUserEstado.ATIVO);
                linkTenantUser(provisioned.getTenantId(), user.getId(), TenantUserRole.TENANT_FINANCE,
                                TenantUserEstado.ATIVO);
                linkTenantUser(provisioned.getTenantId(), user.getId(), TenantUserRole.TENANT_CASHIER,
                                TenantUserEstado.ATIVO);
                linkTenantUser(provisioned.getTenantId(), user.getId(), TenantUserRole.TENANT_ADMIN,
                                TenantUserEstado.SUSPENSO);

                String globalToken = jwtTokenProvider.generateToken(
                                user.getUsername(), "ROLE_GERENTE", null, user.getId(), "GLOBAL");
                JsonNode data = objectMapper.readTree(mockMvc.perform(get("/auth/tenants")
                                .header("Authorization", "Bearer " + globalToken))
                                .andExpect(status().isOk())
                                .andReturn().getResponse().getContentAsString()).path("data");

                assertThat(data.size()).isEqualTo(1);
                assertThat(data.get(0).path("tenantId").asLong()).isEqualTo(provisioned.getTenantId());
                assertThat(objectMapper.convertValue(data.get(0).path("roles"), java.util.List.class))
                                .containsExactly("TENANT_CASHIER", "TENANT_FINANCE", "TENANT_OPERATOR");
        }

        @Test
        void listarTenants_rejeitaJwtTenantEJwtLegacySemScope() throws Exception {
                String suffix = String.valueOf(Math.abs(System.nanoTime() % 1_000_000L));
                ProvisionarTenantResponse provisioned = provisionTenant(
                                "scope-" + suffix,
                                "SC" + suffix,
                                "owner-scope-" + suffix + "@a.com");
                User owner = userRepository.findById(provisioned.getOwnerUserId()).orElseThrow();
                String globalToken = jwtTokenProvider.generateToken(
                                owner.getUsername(), "ROLE_GERENTE", null, owner.getId(), "GLOBAL");
                String select = mockMvc.perform(post("/auth/tenant/select")
                                .header("Authorization", "Bearer " + globalToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(new SelectTenantRequest(provisioned.getTenantId()))))
                                .andExpect(status().isOk())
                                .andReturn().getResponse().getContentAsString();
                String tenantToken = objectMapper.readTree(select).at("/data/accessToken").asText();

                String wrongScope = mockMvc.perform(get("/auth/tenants")
                                .header("Authorization", "Bearer " + tenantToken))
                                .andExpect(status().isForbidden())
                                .andReturn().getResponse().getContentAsString();
                assertThat(objectMapper.readTree(wrongScope).path("code").asText())
                                .isEqualTo("TOKEN_SCOPE_INVALID");

                String legacyToken = jwtTokenProvider.generateToken(owner.getUsername(), "ROLE_GERENTE");
                mockMvc.perform(get("/auth/tenants")
                                .header("Authorization", "Bearer " + legacyToken))
                                .andExpect(status().isUnauthorized());
        }

        // =============================================================
        // Helpers
        // =============================================================

        private ProvisionarTenantResponse provisionTenant(String slug, String tenantCode, String ownerEmail) {
                TenantContextHolder.set(new TenantContext(
                                null, null, 1L, Set.of("ROLE_ADMIN"),
                                TenantResolutionSource.JWT, true, false));

                // Garantir código único com no máximo 10 chars
                String code = tenantCode.replaceAll("[^A-Z0-9]", "");
                if (code.length() > 10)
                        code = code.substring(0, 10);

                String phone = "+24491" + String.format("%07d", Math.abs(slug.hashCode() % 10_000_000L));

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
                                                                .sigla(code.substring(0, Math.min(4, code.length())))
                                                                .build())
                                                .responsavel(ProvisionarTenantRequest.ResponsavelInfo.builder()
                                                                .email(ownerEmail)
                                                                .telefone(phone)
                                                                .criarUsuario(true)
                                                                .build())
                                                .opcoes(ProvisionarTenantRequest.OpcoesProvisionamento.builder()
                                                                .criarMesas(false)
                                                                .criarQrPorMesa(false)
                                                                .build())
                                                .build());
        }

        private User createUser(String email) {
                String suffix = String.valueOf(Math.abs(System.nanoTime() % 100_000L));
                String phone = "+24492" + String.format("%07d", Math.abs(System.nanoTime() % 10_000_000L));
                User u = new User();
                u.setUsername(email);
                u.setPassword("x");
                u.setEmail(email);
                u.setTelefone(phone);
                u.setRoles(Set.of(Role.ROLE_GERENTE));
                u.setAtivo(true);
                return userRepository.saveAndFlush(u);
        }

        private void linkTenantUser(Long tenantId, Long userId, TenantUserRole role, TenantUserEstado estado) {
                Tenant tenant = tenantRepository.findById(tenantId).orElseThrow();
                User user = userRepository.findById(userId).orElseThrow();
                TenantUser tu = new TenantUser();
                tu.setTenant(tenant);
                tu.setUser(user);
                tu.setRole(role);
                tu.setEstado(estado);
                tenantUserRepository.saveAndFlush(tu);
        }
}

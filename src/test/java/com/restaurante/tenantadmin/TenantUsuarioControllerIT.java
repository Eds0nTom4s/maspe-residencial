package com.restaurante.tenantadmin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.dto.request.CriarTenantUsuarioRequest;
import com.restaurante.dto.request.ProvisionarTenantRequest;
import com.restaurante.dto.request.AlterarTenantUsuarioRolesRequest;
import com.restaurante.dto.request.SuspenderTenantUsuarioRequest;
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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "spring.main.web-application-type=servlet"
)
@AutoConfigureMockMvc
@ActiveProfiles("it-postgres")
class TenantUsuarioControllerIT extends PostgresTestcontainersConfig {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired TenantProvisioningService provisioningService;
    @Autowired UserRepository userRepository;
    @Autowired TenantRepository tenantRepository;
    @Autowired TenantUserRepository tenantUserRepository;

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    @Test
    @WithMockUser(username = "owner")
    void owner_canListAndCreateOperator_andAuditIsRecorded() throws Exception {
        ProvisionarTenantResponse prov = provisionTenant("tu-a", "TUA");
        TenantContextHolder.set(new TenantContext(
                prov.getTenantId(), prov.getTenantCode(), prov.getOwnerUserId(),
                Set.of(Role.ROLE_GERENTE.name(), TenantUserRole.TENANT_OWNER.name()),
                TenantResolutionSource.JWT, false, false
        ));

        String listResp = mockMvc.perform(get("/tenant/usuarios").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode listJson = objectMapper.readTree(listResp);
        assertThat(listJson.at("/success").asBoolean()).isTrue();

        CriarTenantUsuarioRequest req = new CriarTenantUsuarioRequest();
        req.setNome("Operador 1");
        req.setEmail("op1@t.com");
        req.setTelefone("+244900111111");
        req.setRoles(Set.of(TenantUserRole.TENANT_OPERATOR));
        req.setEstadoInicial(TenantUserEstado.ATIVO);

        String createResp = mockMvc.perform(post("/tenant/usuarios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        JsonNode createJson = objectMapper.readTree(createResp);
        assertThat(createJson.at("/data/userId").asLong()).isPositive();
        assertThat(createJson.at("/data/roles").toString()).contains("TENANT_OPERATOR");

        String auditResp = mockMvc.perform(get("/tenant/auditoria").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode auditJson = objectMapper.readTree(auditResp);
        assertThat(auditJson.at("/success").asBoolean()).isTrue();
        assertThat(auditJson.at("/data/content").toString()).contains("TENANT_USER_CREATED");
    }

    @Test
    @WithMockUser(username = "owner")
    void audit_doesNotContainTemporaryPassword() throws Exception {
        ProvisionarTenantResponse prov = provisionTenant("tu-pass", "TUP");
        TenantContextHolder.set(new TenantContext(
                prov.getTenantId(), prov.getTenantCode(), prov.getOwnerUserId(),
                Set.of(Role.ROLE_GERENTE.name(), TenantUserRole.TENANT_OWNER.name()),
                TenantResolutionSource.JWT, false, false
        ));

        CriarTenantUsuarioRequest req = new CriarTenantUsuarioRequest();
        req.setNome("Operador 2");
        req.setEmail("op-pass@t.com");
        req.setTelefone("+244904111111");
        req.setRoles(Set.of(TenantUserRole.TENANT_OPERATOR));
        req.setSenhaTemporaria("Alterar@123");

        mockMvc.perform(post("/tenant/usuarios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        String auditResp = mockMvc.perform(get("/tenant/auditoria").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(auditResp).doesNotContain("Alterar@123");
    }

    @Test
    @WithMockUser(username = "admin")
    void admin_canCreateOperator_butCannotCreateOwnerOrAdmin() throws Exception {
        ProvisionarTenantResponse prov = provisionTenant("tu-b", "TUB");

        User admin = createUser("admin@t.com", "+244900222222");
        linkTenantUser(prov.getTenantId(), admin.getId(), TenantUserRole.TENANT_ADMIN, TenantUserEstado.ATIVO);

        TenantContextHolder.set(new TenantContext(
                prov.getTenantId(), prov.getTenantCode(), admin.getId(),
                Set.of(Role.ROLE_GERENTE.name(), TenantUserRole.TENANT_ADMIN.name()),
                TenantResolutionSource.JWT, false, false
        ));

        CriarTenantUsuarioRequest ok = new CriarTenantUsuarioRequest();
        ok.setNome("Operador");
        ok.setEmail("op2@t.com");
        ok.setTelefone("+244900333333");
        ok.setRoles(Set.of(TenantUserRole.TENANT_OPERATOR));
        mockMvc.perform(post("/tenant/usuarios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ok)))
                .andExpect(status().isCreated());

        CriarTenantUsuarioRequest badOwner = new CriarTenantUsuarioRequest();
        badOwner.setNome("Owner");
        badOwner.setEmail("bad-owner@t.com");
        badOwner.setTelefone("+244900444444");
        badOwner.setRoles(Set.of(TenantUserRole.TENANT_OWNER));
        mockMvc.perform(post("/tenant/usuarios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badOwner)))
                .andExpect(status().isForbidden());

        CriarTenantUsuarioRequest badAdmin = new CriarTenantUsuarioRequest();
        badAdmin.setNome("Admin2");
        badAdmin.setEmail("bad-admin@t.com");
        badAdmin.setTelefone("+244900555555");
        badAdmin.setRoles(Set.of(TenantUserRole.TENANT_ADMIN));
        mockMvc.perform(post("/tenant/usuarios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badAdmin)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "operator")
    void operator_cannotManageUsers() throws Exception {
        ProvisionarTenantResponse prov = provisionTenant("tu-c", "TUC");

        User op = createUser("op@t.com", "+244900666666");
        linkTenantUser(prov.getTenantId(), op.getId(), TenantUserRole.TENANT_OPERATOR, TenantUserEstado.ATIVO);

        TenantContextHolder.set(new TenantContext(
                prov.getTenantId(), prov.getTenantCode(), op.getId(),
                Set.of(Role.ROLE_GERENTE.name(), TenantUserRole.TENANT_OPERATOR.name()),
                TenantResolutionSource.JWT, false, false
        ));

        mockMvc.perform(get("/tenant/usuarios").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "owner")
    void maxUsuarios_isEnforced_distinctUsers() throws Exception {
        ProvisionarTenantResponse prov = provisionTenant("tu-lim", "TUL");
        TenantContextHolder.set(new TenantContext(
                prov.getTenantId(), prov.getTenantCode(), prov.getOwnerUserId(),
                Set.of(Role.ROLE_GERENTE.name(), TenantUserRole.TENANT_OWNER.name()),
                TenantResolutionSource.JWT, false, false
        ));

        // Plano PILOTO maxUsuarios=5. Owner já conta 1. Criar +4 deve passar, +5 deve bloquear (409).
        for (int i = 1; i <= 4; i++) {
            CriarTenantUsuarioRequest req = new CriarTenantUsuarioRequest();
            req.setNome("U" + i);
            req.setEmail("u" + i + "@t.com");
            req.setTelefone("+24490100000" + i);
            req.setRoles(Set.of(TenantUserRole.TENANT_OPERATOR));
            mockMvc.perform(post("/tenant/usuarios")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isCreated());
        }

        CriarTenantUsuarioRequest req5 = new CriarTenantUsuarioRequest();
        req5.setNome("U5");
        req5.setEmail("u5@t.com");
        req5.setTelefone("+244901000005");
        req5.setRoles(Set.of(TenantUserRole.TENANT_OPERATOR));
        mockMvc.perform(post("/tenant/usuarios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req5)))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(username = "owner")
    void cannotRemoveLastOwner() throws Exception {
        ProvisionarTenantResponse prov = provisionTenant("tu-last", "TUX");
        TenantContextHolder.set(new TenantContext(
                prov.getTenantId(), prov.getTenantCode(), prov.getOwnerUserId(),
                Set.of(Role.ROLE_GERENTE.name(), TenantUserRole.TENANT_OWNER.name()),
                TenantResolutionSource.JWT, false, false
        ));

        mockMvc.perform(delete("/tenant/usuarios/" + prov.getOwnerUserId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SuspenderTenantUsuarioRequest())))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "owner")
    void crossTenant_detailReturns404() throws Exception {
        ProvisionarTenantResponse provA = provisionTenant("tu-ct-a", "CTA");
        ProvisionarTenantResponse provB = provisionTenant("tu-ct-b", "CTB");

        // cria um operador no tenant A
        TenantContextHolder.set(new TenantContext(
                provA.getTenantId(), provA.getTenantCode(), provA.getOwnerUserId(),
                Set.of(Role.ROLE_GERENTE.name(), TenantUserRole.TENANT_OWNER.name()),
                TenantResolutionSource.JWT, false, false
        ));
        CriarTenantUsuarioRequest req = new CriarTenantUsuarioRequest();
        req.setNome("OpA");
        req.setEmail("opa@t.com");
        req.setTelefone("+244902000001");
        req.setRoles(Set.of(TenantUserRole.TENANT_OPERATOR));
        String created = mockMvc.perform(post("/tenant/usuarios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long userIdA = objectMapper.readTree(created).at("/data/userId").asLong();

        // tenta buscar do tenant B
        TenantContextHolder.set(new TenantContext(
                provB.getTenantId(), provB.getTenantCode(), provB.getOwnerUserId(),
                Set.of(Role.ROLE_GERENTE.name(), TenantUserRole.TENANT_OWNER.name()),
                TenantResolutionSource.JWT, false, false
        ));
        mockMvc.perform(get("/tenant/usuarios/" + userIdA).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "owner")
    void admin_cannotModifyOwner() throws Exception {
        ProvisionarTenantResponse prov = provisionTenant("tu-mod", "TUM");

        User admin = createUser("admin2@t.com", "+244903000001");
        linkTenantUser(prov.getTenantId(), admin.getId(), TenantUserRole.TENANT_ADMIN, TenantUserEstado.ATIVO);

        TenantContextHolder.set(new TenantContext(
                prov.getTenantId(), prov.getTenantCode(), admin.getId(),
                Set.of(Role.ROLE_GERENTE.name(), TenantUserRole.TENANT_ADMIN.name()),
                TenantResolutionSource.JWT, false, false
        ));

        AlterarTenantUsuarioRolesRequest change = new AlterarTenantUsuarioRolesRequest();
        change.setRoles(Set.of(TenantUserRole.TENANT_FINANCE));
        mockMvc.perform(put("/tenant/usuarios/" + prov.getOwnerUserId() + "/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(change)))
                .andExpect(status().isForbidden());
    }

    private ProvisionarTenantResponse provisionTenant(String slug, String tenantCode) {
        TenantContextHolder.set(new TenantContext(
                null, null, 1L, Set.of(Role.ROLE_ADMIN.name()),
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
                                .email(slug + "@owner.com")
                                .telefone(phone)
                                .criarUsuario(true)
                                .build())
                        .build()
        );
    }

    private User createUser(String email, String telefone) {
        User u = new User();
        u.setUsername(email);
        u.setPassword("x");
        u.setEmail(email);
        u.setTelefone(telefone);
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

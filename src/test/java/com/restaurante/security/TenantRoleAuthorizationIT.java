package com.restaurante.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.dto.request.ProvisionarTenantRequest;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "spring.main.web-application-type=servlet"
)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("it-postgres")
class TenantRoleAuthorizationIT extends PostgresTestcontainersConfig {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @Autowired TenantProvisioningService provisioningService;
    @Autowired UserRepository userRepository;
    @Autowired TenantUserRepository tenantUserRepository;
    @Autowired TenantRepository tenantRepository;

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    @Test
    @WithMockUser(username = "tenant-user")
    void operator_cannotRevokeQr_butCanListPedidos() throws Exception {
        ProvisionarTenantResponse prov = provisionarTenant("t-op");

        User operator = createUser("op@t.com", "+244900000001");
        linkTenantUser(prov.getTenantId(), operator.getId(), TenantUserRole.TENANT_OPERATOR, TenantUserEstado.ATIVO);

        TenantContextHolder.set(new TenantContext(
                prov.getTenantId(), prov.getTenantCode(), operator.getId(),
                Set.of(Role.ROLE_GERENTE.name()), TenantResolutionSource.JWT, false, false
        ));

        // pode listar pedidos
        mockMvc.perform(get("/tenant/pedidos").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        // não pode revogar QR
        mockMvc.perform(post("/tenant/qrcodes/" + prov.getQrCodeId() + "/revogar")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "tenant-user")
    void finance_canAccessFinance_butCannotAccessQr() throws Exception {
        ProvisionarTenantResponse prov = provisionarTenant("t-fin");

        User finance = createUser("fin@t.com", "+244900000002");
        linkTenantUser(prov.getTenantId(), finance.getId(), TenantUserRole.TENANT_FINANCE, TenantUserEstado.ATIVO);

        TenantContextHolder.set(new TenantContext(
                prov.getTenantId(), prov.getTenantCode(), finance.getId(),
                Set.of(Role.ROLE_GERENTE.name()), TenantResolutionSource.JWT, false, false
        ));

        mockMvc.perform(get("/tenant/financeiro/resumo").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        mockMvc.perform(get("/tenant/qrcodes").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "tenant-user")
    void cashier_canListQr_butCannotGenerateMesaQr_orAccessFinance() throws Exception {
        ProvisionarTenantResponse prov = provisionarTenant("t-cash");

        User cashier = createUser("cash@t.com", "+244900000003");
        linkTenantUser(prov.getTenantId(), cashier.getId(), TenantUserRole.TENANT_CASHIER, TenantUserEstado.ATIVO);

        TenantContextHolder.set(new TenantContext(
                prov.getTenantId(), prov.getTenantCode(), cashier.getId(),
                Set.of(Role.ROLE_ATENDENTE.name()), TenantResolutionSource.JWT, false, false
        ));

        mockMvc.perform(get("/tenant/qrcodes").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        // gerar QR por mesa exige OWNER/ADMIN
        Long mesaId = prov.getMesas() != null && !prov.getMesas().isEmpty() ? prov.getMesas().getFirst().getMesaId() : null;
        if (mesaId != null) {
            mockMvc.perform(post("/tenant/mesas/" + mesaId + "/qrcode").accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isForbidden());
        }

        mockMvc.perform(get("/tenant/financeiro/pagamentos").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "tenant-user")
    void kitchen_onlyMeAllowed_structureBlocked() throws Exception {
        ProvisionarTenantResponse prov = provisionarTenant("t-kit");

        User kitchen = createUser("k@t.com", "+244900000004");
        linkTenantUser(prov.getTenantId(), kitchen.getId(), TenantUserRole.TENANT_KITCHEN, TenantUserEstado.ATIVO);

        TenantContextHolder.set(new TenantContext(
                prov.getTenantId(), prov.getTenantCode(), kitchen.getId(),
                Set.of(Role.ROLE_COZINHA.name()), TenantResolutionSource.JWT, false, false
        ));

        mockMvc.perform(get("/tenant/me").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        mockMvc.perform(get("/tenant/mesas").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "tenant-user")
    void suspendedMembership_isBlocked() throws Exception {
        ProvisionarTenantResponse prov = provisionarTenant("t-susp");

        User op = createUser("susp@t.com", "+244900000005");
        linkTenantUser(prov.getTenantId(), op.getId(), TenantUserRole.TENANT_OPERATOR, TenantUserEstado.SUSPENSO);

        TenantContextHolder.set(new TenantContext(
                prov.getTenantId(), prov.getTenantCode(), op.getId(),
                Set.of(Role.ROLE_GERENTE.name()), TenantResolutionSource.JWT, false, false
        ));

        mockMvc.perform(get("/tenant/pedidos").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    private ProvisionarTenantResponse provisionarTenant(String slug) {
        TenantContextHolder.set(new TenantContext(
                null, null, 1L, Set.of(Role.ROLE_ADMIN.name()),
                TenantResolutionSource.JWT, true, false
        ));

        return provisioningService.provisionar(
                ProvisionarTenantRequest.builder()
                        .tenant(ProvisionarTenantRequest.TenantInfo.builder()
                                .nome("Tenant " + slug)
                                .slug(slug)
                                .tenantCode(slug.replace("-", "").toUpperCase())
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
                                .telefone("+244911111111")
                                .criarUsuario(true)
                                .build())
                        .opcoes(ProvisionarTenantRequest.OpcoesProvisionamento.builder()
                                .criarMesas(true)
                                .quantidadeMesas(1)
                                .criarQrPorMesa(true)
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


package com.restaurante.device;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.dto.request.ProvisionarTenantRequest;
import com.restaurante.dto.request.RegistrarDispositivoRequest;
import com.restaurante.dto.response.ProvisionarTenantResponse;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.TenantUser;
import com.restaurante.model.entity.User;
import com.restaurante.model.enums.DispositivoTipo;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "spring.main.web-application-type=servlet"
)
@AutoConfigureMockMvc
@ActiveProfiles("it-postgres")
class DispositivoOperacionalTenantAdminIT extends PostgresTestcontainersConfig {

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
    @WithMockUser(username = "tenant-owner")
    void owner_canRegisterDevice_andCodeIsHashed() throws Exception {
        ProvisionarTenantResponse prov = provisionTenant("t-dev-1", "TD1");
        TenantContextHolder.set(new TenantContext(
                prov.getTenantId(), prov.getTenantCode(), prov.getOwnerUserId(),
                Set.of(Role.ROLE_GERENTE.name()), TenantResolutionSource.JWT, false, false
        ));

        RegistrarDispositivoRequest req = new RegistrarDispositivoRequest();
        req.setNome("POS Balcão Principal");
        req.setCodigo("POS-BALCAO-01");
        req.setTipo(DispositivoTipo.POS);
        req.setInstituicaoId(prov.getInstituicaoId());
        req.setUnidadeAtendimentoId(prov.getUnidadeAtendimentoId());

        String resp = mockMvc.perform(post("/tenant/dispositivos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(resp);
        assertThat(json.at("/success").asBoolean()).isTrue();
        assertThat(json.at("/data/activationCode").asText()).isNotBlank();
        assertThat(json.at("/data/activationCodeExpiresAt").asText()).isNotBlank();
    }

    @Test
    @WithMockUser(username = "tenant-admin")
    void duplicateCodigo_sameTenant_isBlocked() throws Exception {
        ProvisionarTenantResponse prov = provisionTenant("t-dev-dup", "TDD");
        TenantContextHolder.set(new TenantContext(
                prov.getTenantId(), prov.getTenantCode(), prov.getOwnerUserId(),
                Set.of(Role.ROLE_GERENTE.name()), TenantResolutionSource.JWT, false, false
        ));

        RegistrarDispositivoRequest req = new RegistrarDispositivoRequest();
        req.setNome("POS 1");
        req.setCodigo("POS-01");
        req.setTipo(DispositivoTipo.POS);
        req.setInstituicaoId(prov.getInstituicaoId());
        req.setUnidadeAtendimentoId(prov.getUnidadeAtendimentoId());

        mockMvc.perform(post("/tenant/dispositivos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/tenant/dispositivos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "tenant-owner")
    void maxDispositivos_isEnforced() throws Exception {
        ProvisionarTenantResponse prov = provisionTenant("t-dev-limit", "TDL");
        TenantContextHolder.set(new TenantContext(
                prov.getTenantId(), prov.getTenantCode(), prov.getOwnerUserId(),
                Set.of(Role.ROLE_GERENTE.name()), TenantResolutionSource.JWT, false, false
        ));

        for (int i = 1; i <= 3; i++) {
            RegistrarDispositivoRequest req = new RegistrarDispositivoRequest();
            req.setNome("POS " + i);
            req.setCodigo("POS-" + i);
            req.setTipo(DispositivoTipo.POS);
            req.setInstituicaoId(prov.getInstituicaoId());
            req.setUnidadeAtendimentoId(prov.getUnidadeAtendimentoId());
            mockMvc.perform(post("/tenant/dispositivos")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isCreated());
        }

        RegistrarDispositivoRequest req4 = new RegistrarDispositivoRequest();
        req4.setNome("POS 4");
        req4.setCodigo("POS-4");
        req4.setTipo(DispositivoTipo.POS);
        req4.setInstituicaoId(prov.getInstituicaoId());
        req4.setUnidadeAtendimentoId(prov.getUnidadeAtendimentoId());

        mockMvc.perform(post("/tenant/dispositivos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req4)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "tenant-operator")
    void operator_cannotRegisterDevice() throws Exception {
        ProvisionarTenantResponse prov = provisionTenant("t-dev-op", "TDO");

        User operator = new User();
        operator.setUsername("op@t.com");
        operator.setPassword("x");
        operator.setEmail("op@t.com");
        operator.setTelefone("+244922000111");
        operator.setRoles(Set.of(Role.ROLE_GERENTE));
        operator.setAtivo(true);
        operator = userRepository.saveAndFlush(operator);

        Tenant tenant = tenantRepository.findById(prov.getTenantId()).orElseThrow();
        TenantUser tu = new TenantUser();
        tu.setTenant(tenant);
        tu.setUser(operator);
        tu.setRole(TenantUserRole.TENANT_OPERATOR);
        tu.setEstado(TenantUserEstado.ATIVO);
        tenantUserRepository.saveAndFlush(tu);

        TenantContextHolder.set(new TenantContext(
                prov.getTenantId(), prov.getTenantCode(), operator.getId(),
                Set.of(Role.ROLE_GERENTE.name()), TenantResolutionSource.JWT, false, false
        ));

        RegistrarDispositivoRequest req = new RegistrarDispositivoRequest();
        req.setNome("POS");
        req.setCodigo("POS-OP");
        req.setTipo(DispositivoTipo.POS);
        req.setInstituicaoId(prov.getInstituicaoId());
        req.setUnidadeAtendimentoId(prov.getUnidadeAtendimentoId());

        mockMvc.perform(post("/tenant/dispositivos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "platform-admin")
    void sameCodigo_differentTenants_isAllowed() throws Exception {
        ProvisionarTenantResponse provA = provisionTenant("t-dev-a", "TDA");
        ProvisionarTenantResponse provB = provisionTenant("t-dev-b", "TDB");

        TenantContextHolder.set(new TenantContext(
                provA.getTenantId(), provA.getTenantCode(), provA.getOwnerUserId(),
                Set.of(Role.ROLE_GERENTE.name()), TenantResolutionSource.JWT, false, false
        ));
        RegistrarDispositivoRequest reqA = new RegistrarDispositivoRequest();
        reqA.setNome("POS");
        reqA.setCodigo("POS-01");
        reqA.setTipo(DispositivoTipo.POS);
        reqA.setInstituicaoId(provA.getInstituicaoId());
        reqA.setUnidadeAtendimentoId(provA.getUnidadeAtendimentoId());
        mockMvc.perform(post("/tenant/dispositivos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqA)))
                .andExpect(status().isCreated());

        TenantContextHolder.set(new TenantContext(
                provB.getTenantId(), provB.getTenantCode(), provB.getOwnerUserId(),
                Set.of(Role.ROLE_GERENTE.name()), TenantResolutionSource.JWT, false, false
        ));
        RegistrarDispositivoRequest reqB = new RegistrarDispositivoRequest();
        reqB.setNome("POS");
        reqB.setCodigo("POS-01");
        reqB.setTipo(DispositivoTipo.POS);
        reqB.setInstituicaoId(provB.getInstituicaoId());
        reqB.setUnidadeAtendimentoId(provB.getUnidadeAtendimentoId());
        mockMvc.perform(post("/tenant/dispositivos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqB)))
                .andExpect(status().isCreated());
    }

    private ProvisionarTenantResponse provisionTenant(String slug, String tenantCode) {
        TenantContextHolder.set(new TenantContext(
                null, null, 1L, Set.of(Role.ROLE_ADMIN.name()),
                TenantResolutionSource.JWT, true, false
        ));
        long suffix = Math.abs(System.nanoTime() % 1_000_000L);
        String phone = "+24492" + String.format("%07d", Math.abs(new java.util.Random().nextInt(10000000)));
        return provisioningService.provisionar(
                ProvisionarTenantRequest.builder()
                        .tenant(ProvisionarTenantRequest.TenantInfo.builder()
                                .nome("Tenant " + slug)
                                .slug(slug + "-" + suffix)
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
                                .email(slug + suffix + "@owner.com")
                                .telefone(phone)
                                .criarUsuario(true)
                                .build())
                        .build()
        );
    }
}

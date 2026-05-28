package com.restaurante.device;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.dto.request.ProvisionarTenantRequest;
import com.restaurante.dto.request.UpdateDeviceCapabilityRequest;
import com.restaurante.dto.response.ProvisionarTenantResponse;
import com.restaurante.model.entity.DispositivoOperacional;
import com.restaurante.model.entity.Instituicao;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.UnidadeAtendimento;
import com.restaurante.model.enums.*;
import com.restaurante.repository.DispositivoOperacionalRepository;
import com.restaurante.repository.InstituicaoRepository;
import com.restaurante.repository.TenantRepository;
import com.restaurante.repository.UnidadeAtendimentoRepository;
import com.restaurante.security.tenant.TenantContext;
import com.restaurante.security.tenant.TenantContextHolder;
import com.restaurante.security.tenant.TenantResolutionSource;
import com.restaurante.service.TenantProvisioningService;
import com.restaurante.testsupport.PostgresTestcontainersConfig;
import com.restaurante.security.JwtTokenProvider;
import org.springframework.transaction.annotation.Transactional;
import com.restaurante.repository.TenantUserRepository;
import com.restaurante.model.entity.User;
import com.restaurante.model.entity.TenantUser;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "spring.main.web-application-type=servlet"
)
@AutoConfigureMockMvc(addFilters = true)
@ActiveProfiles("it-postgres")
@Transactional
class TenantDeviceCapabilityControllerIT extends PostgresTestcontainersConfig {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired TenantProvisioningService provisioningService;
    @Autowired TenantRepository tenantRepository;
    @Autowired InstituicaoRepository instituicaoRepository;
    @Autowired UnidadeAtendimentoRepository unidadeAtendimentoRepository;
    @Autowired DispositivoOperacionalRepository dispositivoOperacionalRepository;
    @Autowired TenantUserRepository tenantUserRepository;
    @Autowired JwtTokenProvider jwtTokenProvider;
    @Autowired com.restaurante.repository.UserRepository userRepository;

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    @Test
    void owner_can_list_and_update_device_capabilities() throws Exception {
        ProvisionarTenantResponse prov = provisionTenant("cap-ctrl-a", "DCA");
        com.restaurante.model.entity.User owner = userRepository.findById(prov.getOwnerUserId()).orElseThrow();
        Tenant tenant = tenantRepository.findById(prov.getTenantId()).orElseThrow();
        String token = jwtTokenProvider.generateTenantScopedToken(owner, tenant, TenantUserRole.TENANT_OWNER, TenantUserEstado.ATIVO, 1, null);

        DispositivoOperacional device = criarDevice(prov, OperationalDeviceType.POS_CAIXA);

        String list = mockMvc.perform(get("/tenant/devices/{deviceId}/capabilities", device.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode arr = objectMapper.readTree(list).at("/data");
        assertThat(arr.isArray()).isTrue();
        assertThat(arr.toString()).contains("LOOKUP_CONSUMPTION_BY_PHONE");
        assertThat(arr.toString()).doesNotContain("CROSS_UNIT_ASSISTED_IDENTIFICATION");

        UpdateDeviceCapabilityRequest req = new UpdateDeviceCapabilityRequest();
        req.setEnabled(false);
        mockMvc.perform(put("/tenant/devices/{deviceId}/capabilities/{cap}", device.getId(), DeviceCapability.LOOKUP_CONSUMPTION_BY_PHONE)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    @Test
    void cashier_cannot_update_device_capabilities() throws Exception {
        ProvisionarTenantResponse prov = provisionTenant("cap-ctrl-b", "DCB");
        User cashier = createUser("cash@ctrlb.com", "+244900111222");
        linkTenantUser(prov.getTenantId(), cashier.getId(), TenantUserRole.TENANT_CASHIER, TenantUserEstado.ATIVO);

        Tenant tenant = tenantRepository.findById(prov.getTenantId()).orElseThrow();
        String token = jwtTokenProvider.generateTenantScopedToken(cashier, tenant, TenantUserRole.TENANT_CASHIER, TenantUserEstado.ATIVO, 1, null);

        DispositivoOperacional device = criarDevice(prov, OperationalDeviceType.POS_CAIXA);
        UpdateDeviceCapabilityRequest req = new UpdateDeviceCapabilityRequest();
        req.setEnabled(false);

        mockMvc.perform(put("/tenant/devices/{deviceId}/capabilities/{cap}", device.getId(), DeviceCapability.LOOKUP_CONSUMPTION_BY_PHONE)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
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
        u.setRoles(Set.of(Role.ROLE_ATENDENTE));
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

    private DispositivoOperacional criarDevice(ProvisionarTenantResponse prov, OperationalDeviceType opType) {
        Tenant tenant = tenantRepository.findById(prov.getTenantId()).orElseThrow();
        Instituicao inst = instituicaoRepository.findById(prov.getInstituicaoId()).orElseThrow();
        UnidadeAtendimento ua = unidadeAtendimentoRepository.findById(prov.getUnidadeAtendimentoId()).orElseThrow();
        DispositivoOperacional d = new DispositivoOperacional();
        d.setTenant(tenant);
        d.setInstituicao(inst);
        d.setUnidadeAtendimento(ua);
        d.setCodigo("DEV-CAP-" + System.nanoTime());
        d.setNome("Device Cap");
        d.setTipo(DispositivoTipo.POS);
        d.setOperationalDeviceType(opType);
        d.setStatus(DispositivoStatus.ATIVO);
        d.setTokenVersion(1);
        return dispositivoOperacionalRepository.saveAndFlush(d);
    }

    private ProvisionarTenantResponse provisionTenant(String nome, String code) {
        TenantContextHolder.set(new TenantContext(
                null, null, 1L, Set.of(Role.ROLE_ADMIN.name()),
                TenantResolutionSource.JWT, true, false
        ));
        String phone = "+244900" + Math.abs(nome.hashCode() % 1_000_000);
        return provisioningService.provisionar(
                ProvisionarTenantRequest.builder()
                        .tenant(ProvisionarTenantRequest.TenantInfo.builder()
                                .nome("Tenant " + nome)
                                .slug(nome)
                                .tenantCode(code)
                                .tipo(TenantTipo.VENDEDOR_RUA)
                                .build())
                        .planoCodigo("PILOTO")
                        .templateCodigo("VENDEDOR_RUA")
                        .instituicao(ProvisionarTenantRequest.InstituicaoInfo.builder()
                                .nome("Inst " + nome)
                                .sigla("I" + code)
                                .telefone(phone)
                                .build())
                        .responsavel(ProvisionarTenantRequest.ResponsavelInfo.builder()
                                .email(nome + "@owner.com")
                                .telefone(phone)
                                .criarUsuario(true)
                                .build())
                        .build()
        );
    }
}

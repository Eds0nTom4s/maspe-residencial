package com.restaurante.device;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.dto.request.DeviceActivationRequest;
import com.restaurante.dto.request.DeviceHeartbeatRequest;
import com.restaurante.dto.request.ProvisionarTenantRequest;
import com.restaurante.dto.request.RegistrarDispositivoRequest;
import com.restaurante.dto.response.ProvisionarTenantResponse;
import com.restaurante.model.entity.DispositivoOperacional;
import com.restaurante.model.enums.DispositivoTipo;
import com.restaurante.model.enums.Role;
import com.restaurante.model.enums.TenantTipo;
import com.restaurante.repository.DispositivoOperacionalRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "spring.main.web-application-type=servlet"
)
@AutoConfigureMockMvc
@ActiveProfiles("it-postgres")
class DeviceHeartbeatConfigIT extends PostgresTestcontainersConfig {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired TenantProvisioningService provisioningService;
    @Autowired DispositivoOperacionalRepository dispositivoOperacionalRepository;

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    @Test
    @WithMockUser(username = "tenant-owner")
    void heartbeat_updatesTimestamp_and_configReturnsLinks() throws Exception {
        ProvisionarTenantResponse prov = provisionTenant("t-hb-1", "TH1");
        TenantContextHolder.set(new TenantContext(
                prov.getTenantId(), prov.getTenantCode(), prov.getOwnerUserId(),
                Set.of(Role.ROLE_GERENTE.name()), TenantResolutionSource.JWT, false, false
        ));

        RegistrarDispositivoRequest req = new RegistrarDispositivoRequest();
        req.setNome("POS");
        req.setCodigo("POS-HB");
        req.setTipo(DispositivoTipo.POS);
        req.setInstituicaoId(prov.getInstituicaoId());
        req.setUnidadeAtendimentoId(prov.getUnidadeAtendimentoId());

        String regResp = mockMvc.perform(post("/tenant/dispositivos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        JsonNode regJson = objectMapper.readTree(regResp);
        String activationCode = regJson.at("/data/activationCode").asText();
        Long dispositivoId = regJson.at("/data/dispositivoId").asLong();

        DeviceActivationRequest actReq = new DeviceActivationRequest();
        actReq.setActivationCode(activationCode);
        actReq.setCodigo("POS-HB");
        String actResp = mockMvc.perform(post("/device/activate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(actReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        JsonNode actJson = objectMapper.readTree(actResp);
        String deviceToken = actJson.at("/data/deviceToken").asText();
        assertThat(deviceToken).isNotBlank();

        DeviceHeartbeatRequest hb = new DeviceHeartbeatRequest();
        hb.setAppVersion("1.0.1");
        String hbResp = mockMvc.perform(post("/device/heartbeat")
                        .header("Authorization", "Device " + deviceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(hb)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode hbJson = objectMapper.readTree(hbResp);
        assertThat(hbJson.at("/data/status").asText()).isEqualTo("OK");

        DispositivoOperacional d = dispositivoOperacionalRepository.findById(dispositivoId).orElseThrow();
        assertThat(d.getUltimoHeartbeatEm()).isNotNull();
        assertThat(d.getAppVersion()).isEqualTo("1.0.1");

        String cfgResp = mockMvc.perform(get("/device/config")
                        .header("Authorization", "Device " + deviceToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode cfgJson = objectMapper.readTree(cfgResp);
        assertThat(cfgJson.at("/data/tenant/id").asLong()).isEqualTo(prov.getTenantId());
        assertThat(cfgJson.at("/data/instituicao/id").asLong()).isEqualTo(prov.getInstituicaoId());
        assertThat(cfgJson.at("/data/unidadeAtendimento/id").asLong()).isEqualTo(prov.getUnidadeAtendimentoId());
    }

    @Test
    void deviceEndpoints_doNotAcceptBearerJwtAsDeviceToken() throws Exception {
        mockMvc.perform(get("/device/config")
                        .header("Authorization", "Bearer some.jwt.token")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
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
}

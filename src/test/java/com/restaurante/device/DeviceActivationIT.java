package com.restaurante.device;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.dto.request.DeviceActivationRequest;
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
import com.restaurante.testsupport.UniqueTestData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
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
class DeviceActivationIT extends PostgresTestcontainersConfig {

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
    void activationCode_valid_activatesAndReturnsDeviceTokenOnce() throws Exception {
        ProvisionarTenantResponse prov = provisionTenant("t-act-1", "TA1");
        TenantContextHolder.set(new TenantContext(
                prov.getTenantId(), prov.getTenantCode(), prov.getOwnerUserId(),
                Set.of(Role.ROLE_GERENTE.name()), TenantResolutionSource.JWT, false, false
        ));

        RegistrarDispositivoRequest req = new RegistrarDispositivoRequest();
        req.setNome("POS");
        req.setCodigo("POS-01");
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
        assertThat(activationCode).isNotBlank();

        DispositivoOperacional before = dispositivoOperacionalRepository.findById(dispositivoId).orElseThrow();
        assertThat(before.getActivationCodeHash()).isNotBlank();
        assertThat(before.getActivationCodeHash()).doesNotContain(activationCode);

        DeviceActivationRequest actReq = new DeviceActivationRequest();
        actReq.setActivationCode(activationCode);
        actReq.setCodigo("POS-01");
        actReq.setAppVersion("1.0.0");
        actReq.setPlatform("ANDROID");

        MvcResult actResult = mockMvc.perform(post("/device/activate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(actReq)))
                .andReturn();
        int actStatus = actResult.getResponse().getStatus();
        String actBody = actResult.getResponse().getContentAsString();
        assertThat(actStatus)
                .as("Status /device/activate != 201. Body: %s", actBody)
                .isEqualTo(201);

        JsonNode actJson = objectMapper.readTree(actBody);
        String deviceToken = actJson.at("/data/deviceToken").asText();
        assertThat(deviceToken).isNotBlank();

        DispositivoOperacional after = dispositivoOperacionalRepository.findById(dispositivoId).orElseThrow();
        assertThat(after.getDeviceTokenHash()).isNotBlank();
        assertThat(after.getDeviceTokenHash()).doesNotContain(deviceToken);
        assertThat(after.getActivationCodeHash()).isNull();

        // activation code não pode ser reutilizado
        mockMvc.perform(post("/device/activate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(actReq)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "tenant-owner")
    void activationCode_expired_isRejected() throws Exception {
        ProvisionarTenantResponse prov = provisionTenant("t-act-exp", "TAX");
        TenantContextHolder.set(new TenantContext(
                prov.getTenantId(), prov.getTenantCode(), prov.getOwnerUserId(),
                Set.of(Role.ROLE_GERENTE.name()), TenantResolutionSource.JWT, false, false
        ));

        RegistrarDispositivoRequest req = new RegistrarDispositivoRequest();
        req.setNome("POS");
        req.setCodigo("POS-EXP");
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

        DispositivoOperacional d = dispositivoOperacionalRepository.findById(dispositivoId).orElseThrow();
        d.setActivationCodeExpiresAt(LocalDateTime.now().minusMinutes(1));
        dispositivoOperacionalRepository.saveAndFlush(d);

        DeviceActivationRequest actReq = new DeviceActivationRequest();
        actReq.setActivationCode(activationCode);
        actReq.setCodigo("POS-EXP");

        mockMvc.perform(post("/device/activate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(actReq)))
                .andExpect(status().isUnauthorized());
    }

    private ProvisionarTenantResponse provisionTenant(String slug, String tenantCode) {
        TenantContextHolder.set(new TenantContext(
                null, null, 1L, Set.of(Role.ROLE_ADMIN.name()),
                TenantResolutionSource.JWT, true, false
        ));
        String uniqueSlug = UniqueTestData.uniqueSlug(slug);
        String uniqueTenantCode = UniqueTestData.uniqueTenantCode(tenantCode);
        String uniqueSigla = UniqueTestData.uniqueInstituicaoSigla(tenantCode);
        return provisioningService.provisionar(
                ProvisionarTenantRequest.builder()
                        .tenant(ProvisionarTenantRequest.TenantInfo.builder()
                                .nome("Tenant " + uniqueSlug)
                                .slug(uniqueSlug)
                                .tenantCode(uniqueTenantCode)
                                .tipo(TenantTipo.VENDEDOR_RUA)
                                .build())
                        .planoCodigo("PILOTO")
                        .templateCodigo("VENDEDOR_RUA")
                        .instituicao(ProvisionarTenantRequest.InstituicaoInfo.builder()
                                .nome("Inst " + uniqueSlug)
                                .sigla(uniqueSigla)
                                .build())
                        .responsavel(ProvisionarTenantRequest.ResponsavelInfo.builder()
                                .email(UniqueTestData.uniqueEmail(slug + "-owner"))
                                .telefone(UniqueTestData.uniqueTelefone())
                                .criarUsuario(true)
                                .build())
                        .build()
        );
    }
}

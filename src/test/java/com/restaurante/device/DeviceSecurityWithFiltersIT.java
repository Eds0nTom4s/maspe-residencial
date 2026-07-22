package com.restaurante.device;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.dto.request.ProvisionarTenantRequest;
import com.restaurante.dto.request.SelectTenantRequest;
import com.restaurante.dto.request.RegistrarDispositivoRequest;
import com.restaurante.dto.response.ProvisionarTenantResponse;
import com.restaurante.model.entity.User;
import com.restaurante.model.enums.DispositivoTipo;
import com.restaurante.model.enums.TenantTipo;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.main.web-application-type=servlet",
                "jwt.secret=0123456789ABCDEF0123456789ABCDEF",
                "jwt.expiration=3600000"
        }
)
@AutoConfigureMockMvc(addFilters = true)
@ActiveProfiles("it-postgres")
class DeviceSecurityWithFiltersIT extends PostgresTestcontainersConfig {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired TenantProvisioningService provisioningService;
    @Autowired com.restaurante.repository.UserRepository userRepository;
    @Autowired JwtTokenProvider jwtTokenProvider;

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    @Test
    void tenantEndpoints_doNotAcceptDeviceAuthorizationHeader() throws Exception {
        ProvisionarTenantResponse prov = provisionTenant("t-dev-sec", "TDS", "owner-sec@a.com");
        User owner = userRepository.findById(prov.getOwnerUserId()).orElseThrow();
        String globalToken = jwtTokenProvider.generateToken(
                owner.getUsername(), "ROLE_GERENTE", null, owner.getId(), "GLOBAL");

        String selectResp = mockMvc.perform(post("/auth/tenant/select")
                        .header("Authorization", "Bearer " + globalToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SelectTenantRequest(prov.getTenantId()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode selectJson = objectMapper.readTree(selectResp);
        String tenantToken = selectJson.at("/data/accessToken").asText();

        // sem Bearer JWT, apenas Device token -> deve falhar (não autentica)
        mockMvc.perform(post("/tenant/dispositivos")
                        .header("Authorization", "Device some-device-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRegisterReq(prov))))
                .andExpect(status().isUnauthorized());

        // com JWT tenant-scoped -> ok
        mockMvc.perform(post("/tenant/dispositivos")
                        .header("Authorization", "Bearer " + tenantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRegisterReq(prov))))
                .andExpect(status().isCreated());

        // endpoints device não exigem JWT (permitAll), mas também não aceitam Bearer como Device token
        mockMvc.perform(get("/device/config")
                        .header("Authorization", "Bearer " + tenantToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    private RegistrarDispositivoRequest buildRegisterReq(ProvisionarTenantResponse prov) {
        RegistrarDispositivoRequest req = new RegistrarDispositivoRequest();
        req.setNome("POS");
        req.setCodigo("POS-SEC-01");
        req.setTipo(DispositivoTipo.POS);
        req.setInstituicaoId(prov.getInstituicaoId());
        req.setUnidadeAtendimentoId(prov.getUnidadeAtendimentoId());
        return req;
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

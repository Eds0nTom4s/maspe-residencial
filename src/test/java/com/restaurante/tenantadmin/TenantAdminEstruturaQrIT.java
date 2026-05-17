package com.restaurante.tenantadmin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.dto.request.ProvisionarTenantRequest;
import com.restaurante.dto.response.ProvisionarTenantResponse;
import com.restaurante.model.enums.TenantTipo;
import com.restaurante.repository.MesaRepository;
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
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("it-postgres")
class TenantAdminEstruturaQrIT extends PostgresTestcontainersConfig {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired TenantProvisioningService provisioningService;
    @Autowired MesaRepository mesaRepository;

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    @Test
    @WithMockUser(username = "tenant-owner")
    void tenant_canListMesas_andGenerateMesaQr() throws Exception {
        // provisionar com 1 mesa, sem QR por mesa
        TenantContextHolder.set(new TenantContext(
                null, null, 1L, Set.of("ROLE_ADMIN"),
                TenantResolutionSource.JWT, true, false
        ));

        ProvisionarTenantResponse provisioned = provisioningService.provisionar(
                ProvisionarTenantRequest.builder()
                        .tenant(ProvisionarTenantRequest.TenantInfo.builder()
                                .nome("Rest A")
                                .slug("rest-a-tenant")
                                .tenantCode("RA")
                                .tipo(TenantTipo.RESTAURANTE)
                                .build())
                        .planoCodigo("PILOTO")
                        .templateCodigo("RESTAURANTE_SIMPLES")
                        .instituicao(ProvisionarTenantRequest.InstituicaoInfo.builder()
                                .nome("Rest A")
                                .sigla("RA")
                                .build())
                        .responsavel(ProvisionarTenantRequest.ResponsavelInfo.builder()
                                .email("owner@ra.com")
                                .criarUsuario(true)
                                .build())
                        .opcoes(ProvisionarTenantRequest.OpcoesProvisionamento.builder()
                                .criarMesas(true)
                                .quantidadeMesas(1)
                                .criarQrPorMesa(false)
                                .build())
                        .build()
        );

        Long mesaId = provisioned.getMesas().getFirst().getMesaId();

        TenantContextHolder.set(new TenantContext(
                provisioned.getTenantId(), provisioned.getTenantCode(), provisioned.getOwnerUserId(),
                Set.of("ROLE_GERENTE"), TenantResolutionSource.JWT, false, false
        ));

        String respList = mockMvc.perform(get("/tenant/mesas").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode listJson = objectMapper.readTree(respList);
        assertThat(listJson.at("/data").isArray()).isTrue();
        assertThat(listJson.at("/data").size()).isGreaterThanOrEqualTo(1);

        String respQr = mockMvc.perform(post("/tenant/mesas/" + mesaId + "/qrcode")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode qrJson = objectMapper.readTree(respQr);
        assertThat(qrJson.at("/data/token").asText()).startsWith("q_");

        // cross-tenant: outro tenant não pode acessar mesa
        TenantContextHolder.set(new TenantContext(
                null, null, 1L, Set.of("ROLE_ADMIN"),
                TenantResolutionSource.JWT, true, false
        ));
        ProvisionarTenantResponse other = provisioningService.provisionar(
                ProvisionarTenantRequest.builder()
                        .tenant(ProvisionarTenantRequest.TenantInfo.builder()
                                .nome("Rest B")
                                .slug("rest-b-tenant")
                                .tenantCode("RB")
                                .tipo(TenantTipo.RESTAURANTE)
                                .build())
                        .planoCodigo("PILOTO")
                        .templateCodigo("RESTAURANTE_SIMPLES")
                        .instituicao(ProvisionarTenantRequest.InstituicaoInfo.builder().nome("Rest B").sigla("RB").build())
                        .responsavel(ProvisionarTenantRequest.ResponsavelInfo.builder().email("owner@rb.com").criarUsuario(true).build())
                        .opcoes(ProvisionarTenantRequest.OpcoesProvisionamento.builder().criarMesas(false).criarQrPorMesa(false).build())
                        .build()
        );

        TenantContextHolder.set(new TenantContext(
                other.getTenantId(), other.getTenantCode(), other.getOwnerUserId(),
                Set.of("ROLE_GERENTE"), TenantResolutionSource.JWT, false, false
        ));

        mockMvc.perform(get("/tenant/mesas/" + mesaId))
                .andExpect(status().isNotFound());

        // sanity: mesa ainda existe
        assertThat(mesaRepository.findById(mesaId)).isPresent();
    }
}


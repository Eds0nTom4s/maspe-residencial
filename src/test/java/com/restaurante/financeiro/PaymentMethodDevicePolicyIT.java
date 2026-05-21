package com.restaurante.financeiro;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.dto.request.ProvisionarTenantRequest;
import com.restaurante.dto.request.UpdateDevicePaymentMethodPolicyRequest;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "spring.main.web-application-type=servlet"
)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("it-postgres")
class PaymentMethodDevicePolicyIT extends PostgresTestcontainersConfig {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired TenantProvisioningService provisioningService;
    @Autowired DispositivoOperacionalRepository dispositivoOperacionalRepository;
    @Autowired TenantRepository tenantRepository;
    @Autowired InstituicaoRepository instituicaoRepository;
    @Autowired UnidadeAtendimentoRepository unidadeAtendimentoRepository;

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    @Test
    void owner_can_put_and_delete_device_policy_and_invalid_min_max_is_rejected() throws Exception {
        ProvisionarTenantResponse prov = provisionTenant("pm-dev-pol-a", "DPA");
        DispositivoOperacional device = criarDevicePos(prov);

        TenantContextHolder.set(new TenantContext(
                prov.getTenantId(), prov.getTenantCode(), prov.getOwnerUserId(),
                Set.of(Role.ROLE_GERENTE.name(), TenantUserRole.TENANT_OWNER.name()),
                TenantResolutionSource.JWT, false, false
        ));

        UpdateDevicePaymentMethodPolicyRequest req = new UpdateDevicePaymentMethodPolicyRequest();
        req.setInheritFromUnidade(false);
        req.setStatus(PaymentMethodPolicyStatus.ALLOW);
        req.setCanStartGateway(false);
        String putResp = mockMvc.perform(put("/tenant/devices/{deviceId}/payment-method-policies/{code}", device.getId(), PaymentMethodCode.APPYPAY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(putResp).at("/data/code").asText()).isEqualTo("APPYPAY");

        String list = mockMvc.perform(get("/tenant/devices/{deviceId}/payment-method-policies", device.getId()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode data = objectMapper.readTree(list).at("/data");
        assertThat(data.isArray()).isTrue();

        UpdateDevicePaymentMethodPolicyRequest invalid = new UpdateDevicePaymentMethodPolicyRequest();
        invalid.setInheritFromUnidade(false);
        invalid.setStatus(PaymentMethodPolicyStatus.ALLOW);
        invalid.setMinAmount(new BigDecimal("10.00"));
        invalid.setMaxAmount(new BigDecimal("5.00"));
        mockMvc.perform(put("/tenant/devices/{deviceId}/payment-method-policies/{code}", device.getId(), PaymentMethodCode.CASH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(delete("/tenant/devices/{deviceId}/payment-method-policies/{code}", device.getId(), PaymentMethodCode.APPYPAY))
                .andExpect(status().isOk());
    }

    @Test
    void cross_tenant_device_policy_returns_404() throws Exception {
        ProvisionarTenantResponse a = provisionTenant("pm-dev-pol-b1", "DPB1");
        ProvisionarTenantResponse b = provisionTenant("pm-dev-pol-b2", "DPB2");
        DispositivoOperacional deviceB = criarDevicePos(b);

        TenantContextHolder.set(new TenantContext(
                a.getTenantId(), a.getTenantCode(), a.getOwnerUserId(),
                Set.of(Role.ROLE_GERENTE.name(), TenantUserRole.TENANT_OWNER.name()),
                TenantResolutionSource.JWT, false, false
        ));

        mockMvc.perform(get("/tenant/devices/{deviceId}/payment-method-policies", deviceB.getId()))
                .andExpect(status().isNotFound());

        UpdateDevicePaymentMethodPolicyRequest req = new UpdateDevicePaymentMethodPolicyRequest();
        req.setInheritFromUnidade(false);
        req.setStatus(PaymentMethodPolicyStatus.BLOCK);
        mockMvc.perform(put("/tenant/devices/{deviceId}/payment-method-policies/{code}", deviceB.getId(), PaymentMethodCode.CASH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());
    }

    private DispositivoOperacional criarDevicePos(ProvisionarTenantResponse prov) {
        Tenant tenant = tenantRepository.findById(prov.getTenantId()).orElseThrow();
        Instituicao inst = instituicaoRepository.findById(prov.getInstituicaoId()).orElseThrow();
        UnidadeAtendimento ua = unidadeAtendimentoRepository.findById(prov.getUnidadeAtendimentoId()).orElseThrow();

        DispositivoOperacional d = new DispositivoOperacional();
        d.setTenant(tenant);
        d.setInstituicao(inst);
        d.setUnidadeAtendimento(ua);
        d.setCodigo("POS-POL-" + System.nanoTime());
        d.setNome("POS POL");
        d.setTipo(DispositivoTipo.POS);
        d.setStatus(DispositivoStatus.ATIVO);
        d.setTokenVersion(1);
        return dispositivoOperacionalRepository.save(d);
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
                                .sigla(code)
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

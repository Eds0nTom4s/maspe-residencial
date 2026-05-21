package com.restaurante.financeiro;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.dto.request.ProvisionarTenantRequest;
import com.restaurante.dto.request.UpdateUnidadePaymentMethodPolicyRequest;
import com.restaurante.dto.response.ProvisionarTenantResponse;
import com.restaurante.model.enums.PaymentMethodCode;
import com.restaurante.model.enums.PaymentMethodPolicyStatus;
import com.restaurante.model.enums.Role;
import com.restaurante.model.enums.TenantTipo;
import com.restaurante.model.enums.TenantUserRole;
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
class PaymentMethodUnitPolicyIT extends PostgresTestcontainersConfig {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired TenantProvisioningService provisioningService;

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    @Test
    void owner_can_put_and_delete_unidade_policy_and_invalid_min_max_is_rejected() throws Exception {
        ProvisionarTenantResponse prov = provisionTenant("pm-unit-pol-a", "UPA");

        TenantContextHolder.set(new TenantContext(
                prov.getTenantId(), prov.getTenantCode(), prov.getOwnerUserId(),
                Set.of(Role.ROLE_GERENTE.name(), TenantUserRole.TENANT_OWNER.name()),
                TenantResolutionSource.JWT, false, false
        ));

        UpdateUnidadePaymentMethodPolicyRequest req = new UpdateUnidadePaymentMethodPolicyRequest();
        req.setInheritFromTenant(false);
        req.setStatus(PaymentMethodPolicyStatus.BLOCK);
        String putResp = mockMvc.perform(put("/tenant/unidades/{unidadeId}/payment-method-policies/{code}", prov.getUnidadeAtendimentoId(), PaymentMethodCode.CASH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readTree(putResp).at("/data/code").asText()).isEqualTo("CASH");

        // GET list
        String list = mockMvc.perform(get("/tenant/unidades/{unidadeId}/payment-method-policies", prov.getUnidadeAtendimentoId()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode data = objectMapper.readTree(list).at("/data");
        assertThat(data.isArray()).isTrue();

        // invalid min/max
        UpdateUnidadePaymentMethodPolicyRequest invalid = new UpdateUnidadePaymentMethodPolicyRequest();
        invalid.setInheritFromTenant(false);
        invalid.setStatus(PaymentMethodPolicyStatus.ALLOW);
        invalid.setMinAmount(new BigDecimal("10.00"));
        invalid.setMaxAmount(new BigDecimal("5.00"));
        mockMvc.perform(put("/tenant/unidades/{unidadeId}/payment-method-policies/{code}", prov.getUnidadeAtendimentoId(), PaymentMethodCode.TPA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());

        // delete policy
        mockMvc.perform(delete("/tenant/unidades/{unidadeId}/payment-method-policies/{code}", prov.getUnidadeAtendimentoId(), PaymentMethodCode.CASH))
                .andExpect(status().isOk());
    }

    @Test
    void cross_tenant_unidade_policy_returns_404() throws Exception {
        ProvisionarTenantResponse a = provisionTenant("pm-unit-pol-b1", "UPB1");
        ProvisionarTenantResponse b = provisionTenant("pm-unit-pol-b2", "UPB2");

        TenantContextHolder.set(new TenantContext(
                a.getTenantId(), a.getTenantCode(), a.getOwnerUserId(),
                Set.of(Role.ROLE_GERENTE.name(), TenantUserRole.TENANT_OWNER.name()),
                TenantResolutionSource.JWT, false, false
        ));

        mockMvc.perform(get("/tenant/unidades/{unidadeId}/payment-method-policies", b.getUnidadeAtendimentoId()))
                .andExpect(status().isNotFound());

        UpdateUnidadePaymentMethodPolicyRequest req = new UpdateUnidadePaymentMethodPolicyRequest();
        req.setInheritFromTenant(false);
        req.setStatus(PaymentMethodPolicyStatus.BLOCK);
        mockMvc.perform(put("/tenant/unidades/{unidadeId}/payment-method-policies/{code}", b.getUnidadeAtendimentoId(), PaymentMethodCode.CASH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());
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

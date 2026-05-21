package com.restaurante.financeiro;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.dto.request.PaymentPolicyRolloutRequest;
import com.restaurante.dto.request.ProvisionarTenantRequest;
import com.restaurante.dto.response.ProvisionarTenantResponse;
import com.restaurante.model.enums.*;
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
        properties = "spring.main.web-application-type=servlet"
)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("it-postgres")
class PaymentPolicyRolloutRerunHardeningTest extends PostgresTestcontainersConfig {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired TenantProvisioningService provisioningService;

    @AfterEach
    void clear() { TenantContextHolder.clear(); }

    @Test
    void rerun_is_blocked_for_pending_rollout() throws Exception {
        ProvisionarTenantResponse prov = provisionTenant("pm-rerun-hard-a", "RH1");
        TenantContextHolder.set(new TenantContext(
                prov.getTenantId(), prov.getTenantCode(), prov.getOwnerUserId(),
                Set.of(Role.ROLE_GERENTE.name(), TenantUserRole.TENANT_OWNER.name()),
                TenantResolutionSource.JWT, false, false
        ));

        long tpl = templateIdByCode("KDS_SEM_PAGAMENTO");
        PaymentPolicyRolloutRequest req = new PaymentPolicyRolloutRequest();
        req.setUnidadeId(prov.getUnidadeAtendimentoId());
        req.setRolloutMode(PaymentMethodPolicyRolloutMode.UNIT_ALL_DEVICES);
        req.setOverwriteMode(PaymentMethodPolicyOverwriteMode.SKIP_EXISTING);

        String submit = mockMvc.perform(post("/tenant/payment-policy-templates/{templateId}/rollout/submit", tpl)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long rolloutId = objectMapper.readTree(submit).at("/data/rolloutId").asLong();

        mockMvc.perform(post("/tenant/payment-policy-rollouts/{rolloutId}/rerun", rolloutId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    private Long templateIdByCode(String code) throws Exception {
        String list = mockMvc.perform(get("/tenant/payment-policy-templates"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var arr = objectMapper.readTree(list).at("/data");
        for (var n : arr) if (code.equals(n.at("/code").asText())) return n.at("/templateId").asLong();
        throw new IllegalStateException("Template não encontrado no test: " + code);
    }

    private ProvisionarTenantResponse provisionTenant(String nome, String code) {
        TenantContextHolder.set(new TenantContext(null, null, 1L, Set.of(Role.ROLE_ADMIN.name()), TenantResolutionSource.JWT, true, false));
        String phone = "+244900" + Math.abs(nome.hashCode() % 1_000_000);
        return provisioningService.provisionar(
                ProvisionarTenantRequest.builder()
                        .tenant(ProvisionarTenantRequest.TenantInfo.builder().nome("Tenant " + nome).slug(nome).tenantCode(code).tipo(TenantTipo.VENDEDOR_RUA).build())
                        .planoCodigo("PILOTO")
                        .templateCodigo("VENDEDOR_RUA")
                        .instituicao(ProvisionarTenantRequest.InstituicaoInfo.builder().nome("Inst " + nome).sigla(code).build())
                        .responsavel(ProvisionarTenantRequest.ResponsavelInfo.builder().email(nome + "@owner.com").telefone(phone).criarUsuario(true).build())
                        .build()
        );
    }
}


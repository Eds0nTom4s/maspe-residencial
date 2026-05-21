package com.restaurante.financeiro;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.dto.request.PaymentPolicyRolloutRequest;
import com.restaurante.dto.request.ProvisionarTenantRequest;
import com.restaurante.dto.response.ProvisionarTenantResponse;
import com.restaurante.financeiro.paymentmethod.service.PaymentMethodPolicyRolloutWorkerService;
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
class PaymentPolicyRolloutProgressAuditTest extends PostgresTestcontainersConfig {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired TenantProvisioningService provisioningService;
    @Autowired PaymentMethodPolicyRolloutWorkerService workerService;

    @AfterEach
    void clear() { TenantContextHolder.clear(); }

    @Test
    void status_exposes_progress_percent_and_worker_updates_it() throws Exception {
        ProvisionarTenantResponse prov = provisionTenant("pm-progress-a", "PP1");
        TenantContextHolder.set(new TenantContext(
                prov.getTenantId(), prov.getTenantCode(), prov.getOwnerUserId(),
                Set.of(Role.ROLE_GERENTE.name(), TenantUserRole.TENANT_OWNER.name()),
                TenantResolutionSource.JWT, false, false
        ));

        long tpl = templateIdByCode("KDS_SEM_PAGAMENTO");
        PaymentPolicyRolloutRequest req = new PaymentPolicyRolloutRequest();
        req.setUnidadeId(prov.getUnidadeAtendimentoId());
        req.setRolloutMode(PaymentMethodPolicyRolloutMode.UNIT_ALL_DEVICES);
        req.setOverwriteMode(PaymentMethodPolicyOverwriteMode.OVERWRITE_EXISTING);

        String submit = mockMvc.perform(post("/tenant/payment-policy-templates/{templateId}/rollout/submit", tpl)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long rolloutId = objectMapper.readTree(submit).at("/data/rolloutId").asLong();

        workerService.processOneEligibleRollout();

        String statusResp = mockMvc.perform(get("/tenant/payment-policy-rollouts/{rolloutId}", rolloutId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode statusData = objectMapper.readTree(statusResp).at("/data");
        assertThat(statusData.at("/progressPercent").asInt()).isBetween(0, 100);
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


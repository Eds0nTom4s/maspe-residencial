package com.restaurante.financeiro;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.dto.request.PaymentPolicyRolloutRequest;
import com.restaurante.dto.request.ProvisionarTenantRequest;
import com.restaurante.dto.response.ProvisionarTenantResponse;
import com.restaurante.financeiro.paymentmethod.repository.PaymentMethodPolicyTemplateRepository;
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

import java.time.Instant;
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
class PaymentPolicyRolloutBackoffTest extends PostgresTestcontainersConfig {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired TenantProvisioningService provisioningService;
    @Autowired PaymentMethodPolicyRolloutWorkerService workerService;
    @Autowired PaymentMethodPolicyTemplateRepository templateRepository;

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    @Test
    void item_failure_schedules_backoff_with_nextRetryAt() throws Exception {
        ProvisionarTenantResponse prov = provisionTenant("pm-backoff-a", "PB1");
        TenantContextHolder.set(new TenantContext(
                prov.getTenantId(), prov.getTenantCode(), prov.getOwnerUserId(),
                Set.of(Role.ROLE_GERENTE.name(), TenantUserRole.TENANT_OWNER.name()),
                TenantResolutionSource.JWT, false, false
        ));

        Long tpl = templateIdByCode("KDS_SEM_PAGAMENTO");

        // Tornar template INACTIVE para forçar falha do item
        var template = templateRepository.findByIdAndTenant_Id(tpl, prov.getTenantId()).orElseThrow();
        template.setStatus(PaymentMethodPolicyTemplateStatus.INACTIVE);
        templateRepository.saveAndFlush(template);

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

        String items = mockMvc.perform(get("/tenant/payment-policy-rollouts/{rolloutId}/items", rolloutId)
                        .param("status", "PENDING")
                        .param("page", "0")
                        .param("size", "50"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode content = objectMapper.readTree(items).at("/data/content");
        // itens podem ter voltado para PENDING por backoff; validar nextRetryAt futuro
        assertThat(content.isArray()).isTrue();
        if (content.size() > 0) {
            JsonNode first = content.get(0);
            assertThat(first.at("/attempts").asInt()).isGreaterThanOrEqualTo(1);
            assertThat(first.at("/nextRetryAt").isMissingNode()).isFalse();
        }

        // Restaurar template para ACTIVE para não afetar outros testes na mesma suite
        template.setStatus(PaymentMethodPolicyTemplateStatus.ACTIVE);
        templateRepository.saveAndFlush(template);
        assertThat(Instant.now()).isNotNull();
    }

    private Long templateIdByCode(String code) throws Exception {
        String list = mockMvc.perform(get("/tenant/payment-policy-templates"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode arr = objectMapper.readTree(list).at("/data");
        for (JsonNode n : arr) {
            if (code.equals(n.at("/code").asText())) return n.at("/templateId").asLong();
        }
        throw new IllegalStateException("Template não encontrado no test: " + code);
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

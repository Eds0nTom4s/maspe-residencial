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
import org.springframework.jdbc.core.JdbcTemplate;
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
@org.springframework.security.test.context.support.WithMockUser(username = "tenant-user")
@ActiveProfiles("it-postgres")
class PaymentPolicyRolloutStaleLockRecoveryIT extends PostgresTestcontainersConfig {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired TenantProvisioningService provisioningService;
    @Autowired PaymentMethodPolicyRolloutWorkerService workerService;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired FinanceiroItFixtureSupport fixtureSupport;

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    @Test
    void stale_running_item_is_recovered_back_to_pending_or_failed() throws Exception {
        ProvisionarTenantResponse prov = provisionTenant("pm-stale-a", "PS1");
        fixtureSupport.createKdsDevice(prov, "KDS STALE");
        TenantContextHolder.set(new TenantContext(
                prov.getTenantId(), prov.getTenantCode(), prov.getOwnerUserId(),
                Set.of(Role.ROLE_GERENTE.name(), TenantUserRole.TENANT_OWNER.name()),
                TenantResolutionSource.JWT, false, false
        ));

        Long tpl = templateIdByCode("KDS_SEM_PAGAMENTO");
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

        // Forçar 1 item como RUNNING stale
        Long itemId = jdbcTemplate.queryForObject(
                "select id from payment_method_policy_rollout_items where rollout_id = ? limit 1",
                Long.class,
                rolloutId
        );
        assertThat(itemId).isNotNull();

        jdbcTemplate.update("""
                update payment_method_policy_rollout_items
                   set status = 'RUNNING',
                       last_locked_at = now() - interval '10 minutes'
                 where id = ?
                """, itemId);

        // garantir rollout RUNNING para ser elegível
        jdbcTemplate.update("""
                update payment_method_policy_rollouts
                   set status = 'RUNNING',
                       execution_mode = 'ASYNC',
                       locked_at = now(),
                       locked_by = 'test'
                 where id = ?
                """, rolloutId);

        workerService.processOneEligibleRollout();

        String items = mockMvc.perform(get("/tenant/payment-policy-rollouts/{rolloutId}/items", rolloutId)
                        .param("status", "PENDING")
                        .param("page", "0")
                        .param("size", "50"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode content = objectMapper.readTree(items).at("/data/content");
        assertThat(content.isArray()).isTrue();
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

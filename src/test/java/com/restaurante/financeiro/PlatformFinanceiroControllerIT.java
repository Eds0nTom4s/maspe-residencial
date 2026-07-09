package com.restaurante.financeiro;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.financeiro.enums.StatusPagamentoGateway;
import com.restaurante.financeiro.repository.PagamentoCallbackLogRepository;
import com.restaurante.financeiro.repository.PagamentoGatewayRepository;
import com.restaurante.model.entity.Pagamento;
import com.restaurante.model.entity.PagamentoCallbackLog;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.enums.CallbackProcessingStatus;
import com.restaurante.model.enums.TenantEstado;
import com.restaurante.model.enums.TenantTipo;
import com.restaurante.security.tenant.TenantContext;
import com.restaurante.security.tenant.TenantContextHolder;
import com.restaurante.security.tenant.TenantResolutionSource;
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

import java.math.BigDecimal;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "spring.main.web-application-type=servlet"
)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("it-postgres")
class PlatformFinanceiroControllerIT extends PostgresTestcontainersConfig {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @Autowired com.restaurante.repository.TenantRepository tenantRepository;
    @Autowired PagamentoGatewayRepository pagamentoGatewayRepository;
    @Autowired PagamentoCallbackLogRepository callbackLogRepository;

    @AfterEach
    void cleanupContext() {
        TenantContextHolder.clear();
    }

    @Test
    @WithMockUser(username = "platform-admin")
    void platformAdmin_canListGlobalPayments_andSeeCallbacksWithoutTenant() throws Exception {
        Tenant tenantA = criarTenant("Tenant A", "ta2", "TA2");
        Tenant tenantB = criarTenant("Tenant B", "tb2", "TB2");
        String uniqueSuffix = UniqueTestData.uniqueSuffix().toUpperCase();
        String externalReference = "UNK-" + uniqueSuffix.substring(0, Math.min(11, uniqueSuffix.length()));

        Pagamento pA = criarPagamento(tenantA, "REF_PA", new BigDecimal("10.00"), StatusPagamentoGateway.PENDENTE);
        criarPagamento(tenantB, "REF_PB", new BigDecimal("20.00"), StatusPagamentoGateway.PENDENTE);

        // log sem tenant (ex.: PAYMENT_NOT_FOUND)
        PagamentoCallbackLog semTenant = new PagamentoCallbackLog();
        semTenant.setProvider("APPYPAY");
        semTenant.setExternalReference(externalReference);
        semTenant.setProcessed(true);
        semTenant.setProcessingStatus(CallbackProcessingStatus.PAYMENT_NOT_FOUND);
        callbackLogRepository.saveAndFlush(semTenant);

        TenantContextHolder.set(new TenantContext(
                null, null, 999L, Set.of("ROLE_ADMIN"),
                TenantResolutionSource.JWT, true, false
        ));

        String listPayments = mockMvc.perform(get("/platform/financeiro/pagamentos")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode root = objectMapper.readTree(listPayments);
        assertThat(root.at("/data/content").size()).isGreaterThanOrEqualTo(2);

        String listCallbacksNoTenant = mockMvc.perform(get("/platform/financeiro/callbacks/sem-tenant")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode cbRoot = objectMapper.readTree(listCallbacksNoTenant);
        JsonNode callbackContent = cbRoot.at("/data/content");
        assertThat(callbackContent.isArray()).isTrue();
        assertThat(java.util.stream.StreamSupport.stream(callbackContent.spliterator(), false)
                .anyMatch(node -> externalReference.equals(node.at("/externalReference").asText()))).isTrue();

        // detalhe do callback (platform)
        Long logId = callbackLogRepository.findAll().stream()
                .filter(l -> externalReference.equals(l.getExternalReference()))
                .findFirst().orElseThrow().getId();
        String detail = mockMvc.perform(get("/platform/financeiro/callbacks/{id}", logId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode dRoot = objectMapper.readTree(detail);
        assertThat(dRoot.at("/data/externalReference").asText()).isEqualTo(externalReference);
    }

    private Tenant criarTenant(String nome, String slug, String tenantCode) {
        Tenant t = new Tenant();
        t.setNome(nome);
        t.setSlug(UniqueTestData.uniqueSlug(slug));
        t.setTenantCode(UniqueTestData.uniqueTenantCode(tenantCode));
        t.setTipo(TenantTipo.RESTAURANTE);
        t.setEstado(TenantEstado.ATIVO);
        return tenantRepository.saveAndFlush(t);
    }

    private Pagamento criarPagamento(Tenant tenant, String extRef, BigDecimal valor, StatusPagamentoGateway status) {
        Pagamento p = Pagamento.builder()
                .tenant(tenant)
                .pedido(null)
                .fundoConsumo(null)
                .cliente(null)
                .tipoPagamento(com.restaurante.financeiro.enums.TipoPagamentoFinanceiro.POS_PAGO)
                .metodo(null)
                .amount(valor)
                .status(status)
                .externalReference(extRef)
                .observacoes("test")
                .build();
        return pagamentoGatewayRepository.saveAndFlush(p);
    }
}

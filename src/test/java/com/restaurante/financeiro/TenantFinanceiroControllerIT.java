package com.restaurante.financeiro;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.financeiro.enums.StatusPagamentoGateway;
import com.restaurante.financeiro.repository.PagamentoCallbackLogRepository;
import com.restaurante.financeiro.repository.PagamentoGatewayRepository;
import com.restaurante.model.entity.Pagamento;
import com.restaurante.model.entity.PagamentoCallbackLog;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.TenantUser;
import com.restaurante.model.entity.User;
import com.restaurante.model.enums.CallbackProcessingStatus;
import com.restaurante.model.enums.TenantEstado;
import com.restaurante.model.enums.TenantTipo;
import com.restaurante.model.enums.TenantUserEstado;
import com.restaurante.model.enums.TenantUserRole;
import com.restaurante.repository.TenantRepository;
import com.restaurante.repository.TenantUserRepository;
import com.restaurante.repository.UserRepository;
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
class TenantFinanceiroControllerIT extends PostgresTestcontainersConfig {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @Autowired TenantRepository tenantRepository;
    @Autowired UserRepository userRepository;
    @Autowired TenantUserRepository tenantUserRepository;
    @Autowired PagamentoGatewayRepository pagamentoGatewayRepository;
    @Autowired PagamentoCallbackLogRepository callbackLogRepository;

    @AfterEach
    void cleanupContext() {
        TenantContextHolder.clear();
    }

    @Test
    @WithMockUser(username = "tenant-user")
    void tenantSeesOnlyItsPayments_andCannotFetchOtherTenantPayment() throws Exception {
        Tenant tenantA = criarTenant("Banca A", "banca-a", "TAA");
        Tenant tenantB = criarTenant("Bar B", "bar-b", "TBB");

        User userA = criarUser("userA", "+244900000001");
        vincularTenantUserAtivo(tenantA, userA);

        Pagamento pA = criarPagamento(tenantA, "REF_A", new BigDecimal("10.00"), StatusPagamentoGateway.PENDENTE);
        Pagamento pB = criarPagamento(tenantB, "REF_B", new BigDecimal("20.00"), StatusPagamentoGateway.PENDENTE);

        criarCallbackLog(tenantA, pA, CallbackProcessingStatus.PROCESSED);
        criarCallbackLog(tenantB, pB, CallbackProcessingStatus.PROCESSED);

        TenantContextHolder.set(new TenantContext(
                tenantA.getId(), tenantA.getTenantCode(), userA.getId(), Set.of("ROLE_USER"),
                TenantResolutionSource.JWT, false, false
        ));

        String listJson = mockMvc.perform(get("/tenant/financeiro/pagamentos")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode root = objectMapper.readTree(listJson);
        assertThat(root.at("/success").asBoolean()).isTrue();
        JsonNode content = root.at("/data/content");
        assertThat(content.isArray()).isTrue();
        assertThat(content).hasSize(1);
        assertThat(content.get(0).at("/externalReference").asText()).isEqualTo("REF_A");

        mockMvc.perform(get("/tenant/financeiro/pagamentos/{id}", pB.getId())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest()); // BusinessException -> 400 no handler padrão
    }

    @Test
    @WithMockUser(username = "tenant-user")
    void tenantDoesNotSeeCallbacksFromOtherTenants() throws Exception {
        Tenant tenantA = criarTenant("Tenant A", "ta", "TA");
        Tenant tenantB = criarTenant("Tenant B", "tb", "TB");

        User userA = criarUser("userA2", "+244900000002");
        vincularTenantUserAtivo(tenantA, userA);

        Pagamento pA = criarPagamento(tenantA, "REF_A2", new BigDecimal("10.00"), StatusPagamentoGateway.PENDENTE);
        Pagamento pB = criarPagamento(tenantB, "REF_B2", new BigDecimal("20.00"), StatusPagamentoGateway.PENDENTE);

        criarCallbackLog(tenantA, pA, CallbackProcessingStatus.INVALID_SIGNATURE);
        criarCallbackLog(tenantB, pB, CallbackProcessingStatus.PROCESSED);

        TenantContextHolder.set(new TenantContext(
                tenantA.getId(), tenantA.getTenantCode(), userA.getId(), Set.of("ROLE_USER"),
                TenantResolutionSource.JWT, false, false
        ));

        String listJson = mockMvc.perform(get("/tenant/financeiro/callbacks")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode root = objectMapper.readTree(listJson);
        JsonNode content = root.at("/data/content");
        assertThat(content).hasSize(1);
        assertThat(content.get(0).at("/processingStatus").asText()).isEqualTo("INVALID_SIGNATURE");
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

    private User criarUser(String username, String telefone) {
        User u = new User();
        u.setUsername(UniqueTestData.uniqueUsername(username));
        u.setPassword("x");
        u.setTelefone(UniqueTestData.uniqueTelefone());
        u.setAtivo(true);
        return userRepository.saveAndFlush(u);
    }

    private void vincularTenantUserAtivo(Tenant tenant, User user) {
        TenantUser tu = new TenantUser();
        tu.setTenant(tenant);
        tu.setUser(user);
        tu.setRole(TenantUserRole.TENANT_ADMIN);
        tu.setEstado(TenantUserEstado.ATIVO);
        tenantUserRepository.saveAndFlush(tu);
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

    private void criarCallbackLog(Tenant tenant, Pagamento pagamento, CallbackProcessingStatus status) {
        PagamentoCallbackLog l = new PagamentoCallbackLog();
        l.setTenant(tenant);
        l.setPagamento(pagamento);
        l.setProvider("APPYPAY");
        l.setExternalReference(pagamento.getExternalReference());
        l.setProcessed(true);
        l.setProcessingStatus(status);
        callbackLogRepository.saveAndFlush(l);
    }
}

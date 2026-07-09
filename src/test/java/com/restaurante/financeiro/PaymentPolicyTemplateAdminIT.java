package com.restaurante.financeiro;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.dto.request.CreatePaymentPolicyTemplateRequest;
import com.restaurante.dto.request.PaymentPolicyTemplateItemRequest;
import com.restaurante.dto.request.ProvisionarTenantRequest;
import com.restaurante.dto.request.UpdatePaymentPolicyTemplateRequest;
import com.restaurante.dto.response.ProvisionarTenantResponse;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.TenantUser;
import com.restaurante.model.entity.User;
import com.restaurante.model.enums.*;
import com.restaurante.repository.TenantRepository;
import com.restaurante.repository.TenantUserRepository;
import com.restaurante.repository.UserRepository;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "spring.main.web-application-type=servlet"
)
@AutoConfigureMockMvc
@ActiveProfiles("it-postgres")
class PaymentPolicyTemplateAdminIT extends PostgresTestcontainersConfig {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired TenantProvisioningService provisioningService;
    @Autowired TenantRepository tenantRepository;
    @Autowired UserRepository userRepository;
    @Autowired TenantUserRepository tenantUserRepository;

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    @Test
    @WithMockUser(username = "owner")
    void owner_can_create_and_update_template_and_invalid_rules_are_rejected() throws Exception {
        ProvisionarTenantResponse prov = provisionTenant("pmtpl-admin-a", "TA1");
        TenantContextHolder.set(new TenantContext(
                prov.getTenantId(), prov.getTenantCode(), prov.getOwnerUserId(),
                Set.of(Role.ROLE_GERENTE.name(), TenantUserRole.TENANT_OWNER.name()),
                TenantResolutionSource.JWT, false, false
        ));

        CreatePaymentPolicyTemplateRequest create = new CreatePaymentPolicyTemplateRequest();
        create.setCode("CUSTOM_POS");
        create.setName("Custom POS");
        create.setStatus(PaymentMethodPolicyTemplateStatus.ACTIVE);
        PaymentPolicyTemplateItemRequest cash = new PaymentPolicyTemplateItemRequest();
        cash.setPaymentMethodCode(PaymentMethodCode.CASH);
        cash.setPolicyStatus(PaymentMethodPolicyStatus.ALLOW);
        cash.setCanConfirmManual(true);
        cash.setCanStartGateway(false);
        create.setItems(List.of(cash));

        mockMvc.perform(post("/tenant/payment-policy-templates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(create)))
                .andExpect(status().isOk());

        // minAmount > maxAmount => 400
        CreatePaymentPolicyTemplateRequest invalidMinMax = new CreatePaymentPolicyTemplateRequest();
        invalidMinMax.setCode("BAD_MINMAX");
        invalidMinMax.setName("Bad");
        invalidMinMax.setStatus(PaymentMethodPolicyTemplateStatus.ACTIVE);
        PaymentPolicyTemplateItemRequest bad = new PaymentPolicyTemplateItemRequest();
        bad.setPaymentMethodCode(PaymentMethodCode.TPA);
        bad.setPolicyStatus(PaymentMethodPolicyStatus.ALLOW);
        bad.setMinAmount(new BigDecimal("10.00"));
        bad.setMaxAmount(new BigDecimal("5.00"));
        invalidMinMax.setItems(List.of(bad));
        mockMvc.perform(post("/tenant/payment-policy-templates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidMinMax)))
                .andExpect(status().isBadRequest());

        // CASH com canStartGateway=true => 400
        CreatePaymentPolicyTemplateRequest invalidCashGateway = new CreatePaymentPolicyTemplateRequest();
        invalidCashGateway.setCode("BAD_CASH_GATEWAY");
        invalidCashGateway.setName("Bad");
        invalidCashGateway.setStatus(PaymentMethodPolicyTemplateStatus.ACTIVE);
        PaymentPolicyTemplateItemRequest badCash = new PaymentPolicyTemplateItemRequest();
        badCash.setPaymentMethodCode(PaymentMethodCode.CASH);
        badCash.setPolicyStatus(PaymentMethodPolicyStatus.ALLOW);
        badCash.setCanStartGateway(true);
        invalidCashGateway.setItems(List.of(badCash));
        mockMvc.perform(post("/tenant/payment-policy-templates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidCashGateway)))
                .andExpect(status().isBadRequest());

        // APPYPAY com canConfirmManual=true => 400
        CreatePaymentPolicyTemplateRequest invalidAppyManual = new CreatePaymentPolicyTemplateRequest();
        invalidAppyManual.setCode("BAD_APPY_MANUAL");
        invalidAppyManual.setName("Bad");
        invalidAppyManual.setStatus(PaymentMethodPolicyTemplateStatus.ACTIVE);
        PaymentPolicyTemplateItemRequest badAppy = new PaymentPolicyTemplateItemRequest();
        badAppy.setPaymentMethodCode(PaymentMethodCode.APPYPAY);
        badAppy.setPolicyStatus(PaymentMethodPolicyStatus.ALLOW);
        badAppy.setCanConfirmManual(true);
        invalidAppyManual.setItems(List.of(badAppy));
        mockMvc.perform(post("/tenant/payment-policy-templates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidAppyManual)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "cashier")
    void cashier_cannot_create_or_update_templates() throws Exception {
        ProvisionarTenantResponse prov = provisionTenant("pmtpl-admin-b", "TA2");
        User cashier = createTenantUser(prov, TenantUserRole.TENANT_CASHIER);
        TenantContextHolder.set(new TenantContext(
                prov.getTenantId(), prov.getTenantCode(), cashier.getId(),
                Set.of(Role.ROLE_GERENTE.name(), TenantUserRole.TENANT_CASHIER.name()),
                TenantResolutionSource.JWT, false, false
        ));

        CreatePaymentPolicyTemplateRequest create = new CreatePaymentPolicyTemplateRequest();
        create.setCode("CUSTOM_POS");
        create.setName("Custom POS");
        create.setStatus(PaymentMethodPolicyTemplateStatus.ACTIVE);
        PaymentPolicyTemplateItemRequest cash = new PaymentPolicyTemplateItemRequest();
        cash.setPaymentMethodCode(PaymentMethodCode.CASH);
        cash.setPolicyStatus(PaymentMethodPolicyStatus.BLOCK);
        create.setItems(List.of(cash));

        mockMvc.perform(post("/tenant/payment-policy-templates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(create)))
                .andExpect(status().isForbidden());

        UpdatePaymentPolicyTemplateRequest update = new UpdatePaymentPolicyTemplateRequest();
        update.setName("x");
        update.setStatus(PaymentMethodPolicyTemplateStatus.ACTIVE);
        update.setItems(List.of(cash));
        mockMvc.perform(put("/tenant/payment-policy-templates/{templateId}", 999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isForbidden());
    }

    private ProvisionarTenantResponse provisionTenant(String nome, String code) {
        String suffix = String.valueOf(Math.abs(System.nanoTime() % 1_000_000L));
        String effectiveCode = (code == null || code.isBlank()) ? ("T" + suffix) : (code + suffix);

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
                                .tenantCode(effectiveCode)
                                .tipo(TenantTipo.VENDEDOR_RUA)
                                .build())
                        .planoCodigo("PILOTO")
                        .templateCodigo("VENDEDOR_RUA")
                        .instituicao(ProvisionarTenantRequest.InstituicaoInfo.builder()
                                .nome("Inst " + nome)
                                .sigla(effectiveCode)
                                .build())
                        .responsavel(ProvisionarTenantRequest.ResponsavelInfo.builder()
                                .email(nome + "+" + suffix + "@owner.com")
                                .telefone(phone)
                                .criarUsuario(true)
                                .build())
                .build()
        );
    }

    private User createTenantUser(ProvisionarTenantResponse prov, TenantUserRole role) {
        Tenant tenant = tenantRepository.findById(prov.getTenantId()).orElseThrow();

        String suffix = String.valueOf(Math.abs(System.nanoTime() % 1_000_000L));
        User user = new User();
        user.setUsername(role.name().toLowerCase() + "-" + suffix);
        user.setPassword("{noop}x");
        user.setEmail(role.name().toLowerCase() + "-" + suffix + "@test.local");
        user.setTelefone("+24491" + String.format("%07d", Math.abs(System.nanoTime() % 10_000_000L)));
        user.adicionarRole(Role.ROLE_GERENTE);
        user = userRepository.saveAndFlush(user);

        TenantUser tenantUser = new TenantUser();
        tenantUser.setTenant(tenant);
        tenantUser.setUser(user);
        tenantUser.setRole(role);
        tenantUser.setEstado(TenantUserEstado.ATIVO);
        tenantUserRepository.saveAndFlush(tenantUser);
        return user;
    }
}

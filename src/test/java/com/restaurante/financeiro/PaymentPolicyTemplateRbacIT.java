package com.restaurante.financeiro;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.dto.request.PaymentPolicyRolloutRequest;
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

import java.util.List;
import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "spring.main.web-application-type=servlet"
)
@AutoConfigureMockMvc
@ActiveProfiles("it-postgres")
class PaymentPolicyTemplateRbacIT extends PostgresTestcontainersConfig {

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
    @WithMockUser(authorities = { "ROLE_GERENTE" })
    void operator_and_kitchen_blocked_and_cashier_cannot_apply_rollout() throws Exception {
        ProvisionarTenantResponse prov = provisionTenant("pmtpl-rbac-a", "TR1");
        User operator = createTenantUser(prov, TenantUserRole.TENANT_OPERATOR);
        User kitchen = createTenantUser(prov, TenantUserRole.TENANT_KITCHEN);
        User cashier = createTenantUser(prov, TenantUserRole.TENANT_CASHIER);

        // OPERATOR bloqueado
        TenantContextHolder.set(new TenantContext(
                prov.getTenantId(), prov.getTenantCode(), operator.getId(),
                Set.of(Role.ROLE_GERENTE.name(), TenantUserRole.TENANT_OPERATOR.name()),
                TenantResolutionSource.JWT, false, false
        ));
        mockMvc.perform(get("/tenant/payment-policy-templates"))
                .andExpect(status().isForbidden());

        // KITCHEN bloqueado
        TenantContextHolder.set(new TenantContext(
                prov.getTenantId(), prov.getTenantCode(), kitchen.getId(),
                Set.of(Role.ROLE_GERENTE.name(), TenantUserRole.TENANT_KITCHEN.name()),
                TenantResolutionSource.JWT, false, false
        ));
        mockMvc.perform(get("/tenant/payment-policy-templates"))
                .andExpect(status().isForbidden());

        // CASHIER pode listar, mas não pode aplicar rollout nem atualizar
        TenantContextHolder.set(new TenantContext(
                prov.getTenantId(), prov.getTenantCode(), cashier.getId(),
                Set.of(Role.ROLE_GERENTE.name(), TenantUserRole.TENANT_CASHIER.name()),
                TenantResolutionSource.JWT, false, false
        ));
        mockMvc.perform(get("/tenant/payment-policy-templates"))
                .andExpect(status().isOk());

        UpdatePaymentPolicyTemplateRequest update = new UpdatePaymentPolicyTemplateRequest();
        update.setName("x");
        update.setStatus(PaymentMethodPolicyTemplateStatus.ACTIVE);
        update.setItems(List.of());
        mockMvc.perform(put("/tenant/payment-policy-templates/{templateId}", 999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isForbidden());

        PaymentPolicyRolloutRequest apply = new PaymentPolicyRolloutRequest();
        apply.setUnidadeId(prov.getUnidadeAtendimentoId());
        apply.setRolloutMode(PaymentMethodPolicyRolloutMode.UNIT_ALL_DEVICES);
        apply.setOverwriteMode(PaymentMethodPolicyOverwriteMode.SKIP_EXISTING);
        mockMvc.perform(post("/tenant/payment-policy-templates/{templateId}/rollout/apply", 999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(apply)))
                .andExpect(status().isForbidden());
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

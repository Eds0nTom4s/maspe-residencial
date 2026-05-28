package com.restaurante.financeiro;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.dto.request.ProvisionarTenantRequest;
import com.restaurante.dto.request.UpdateTenantPaymentMethodRequest;
import com.restaurante.dto.response.ProvisionarTenantResponse;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.TenantUser;
import com.restaurante.model.entity.User;
import com.restaurante.model.enums.Role;
import com.restaurante.model.enums.TenantUserEstado;
import com.restaurante.model.enums.TenantTipo;
import com.restaurante.model.enums.TenantUserRole;
import com.restaurante.security.tenant.TenantContext;
import com.restaurante.security.tenant.TenantContextHolder;
import com.restaurante.security.tenant.TenantResolutionSource;
import com.restaurante.security.JwtTokenProvider;
import com.restaurante.repository.TenantRepository;
import com.restaurante.repository.TenantUserRepository;
import com.restaurante.repository.UserRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "spring.main.web-application-type=servlet"
)
@AutoConfigureMockMvc
@ActiveProfiles("it-postgres")
class TenantPaymentMethodRbacIT extends PostgresTestcontainersConfig {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired TenantProvisioningService provisioningService;
    @Autowired TenantRepository tenantRepository;
    @Autowired UserRepository userRepository;
    @Autowired TenantUserRepository tenantUserRepository;
    @Autowired JwtTokenProvider jwtTokenProvider;

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    @Test
    void operator_and_kitchen_are_blocked_from_tenant_payment_method_admin_endpoints() throws Exception {
        ProvisionarTenantResponse prov = provisionTenant("pm-rbac-a", "PRB");

        Tenant tenant = tenantRepository.findById(prov.getTenantId()).orElseThrow();

        User operator = createTenantUser(tenant, TenantUserRole.TENANT_OPERATOR);
        User kitchen = createTenantUser(tenant, TenantUserRole.TENANT_KITCHEN);

        String operatorToken = jwtTokenProvider.generateTenantScopedToken(
                operator, tenant, TenantUserRole.TENANT_OPERATOR, TenantUserEstado.ATIVO, 1, null
        );
        String kitchenToken = jwtTokenProvider.generateTenantScopedToken(
                kitchen, tenant, TenantUserRole.TENANT_KITCHEN, TenantUserEstado.ATIVO, 1, null
        );

        // OPERATOR cannot list
        mockMvc.perform(get("/tenant/payment-methods")
                        .header("Authorization", "Bearer " + operatorToken))
                .andExpect(status().isForbidden());

        // KITCHEN cannot list
        mockMvc.perform(get("/tenant/payment-methods")
                        .header("Authorization", "Bearer " + kitchenToken))
                .andExpect(status().isForbidden());

        // OPERATOR cannot patch
        UpdateTenantPaymentMethodRequest req = new UpdateTenantPaymentMethodRequest();
        req.setDisplayName("X");
        mockMvc.perform(patch("/tenant/payment-methods/CASH")
                        .header("Authorization", "Bearer " + operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());

        // KITCHEN cannot activate/deactivate
        mockMvc.perform(post("/tenant/payment-methods/TPA/activate")
                        .header("Authorization", "Bearer " + kitchenToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/tenant/payment-methods/TPA/deactivate")
                        .header("Authorization", "Bearer " + kitchenToken))
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

    private User createTenantUser(Tenant tenant, TenantUserRole role) {
        User u = new User();
        long n = System.nanoTime();
        u.setUsername(role.name().toLowerCase() + "-" + n);
        u.setPassword("{noop}x");
        u.setEmail(role.name().toLowerCase() + "-" + n + "@test.local");
        u.setTelefone("+244911" + String.format("%06d", (int) (n % 1_000_000)));
        u.adicionarRole(Role.ROLE_GERENTE);
        u = userRepository.saveAndFlush(u);

        TenantUser tu = new TenantUser();
        tu.setTenant(tenant);
        tu.setUser(u);
        tu.setRole(role);
        tu.setEstado(TenantUserEstado.ATIVO);
        tenantUserRepository.saveAndFlush(tu);
        return u;
    }
}

package com.restaurante.financeiro;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.dto.request.ProvisionarTenantRequest;
import com.restaurante.dto.response.ProvisionarTenantResponse;
import com.restaurante.financeiro.paymentmethod.repository.TenantPaymentMethodRepository;
import com.restaurante.financeiro.paymentmethod.service.TenantPaymentMethodBootstrapService;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.User;
import com.restaurante.model.enums.TenantUserEstado;
import com.restaurante.model.enums.PaymentMethodCode;
import com.restaurante.model.enums.PaymentMethodStatus;
import com.restaurante.model.enums.Role;
import com.restaurante.model.enums.TenantTipo;
import com.restaurante.model.enums.TenantUserRole;
import com.restaurante.security.tenant.TenantContext;
import com.restaurante.security.tenant.TenantContextHolder;
import com.restaurante.security.tenant.TenantResolutionSource;
import com.restaurante.security.JwtTokenProvider;
import com.restaurante.repository.TenantRepository;
import com.restaurante.repository.UserRepository;
import com.restaurante.service.TenantProvisioningService;
import com.restaurante.testsupport.PostgresTestcontainersConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "spring.main.web-application-type=servlet"
)
@AutoConfigureMockMvc
@ActiveProfiles("it-postgres")
class TenantPaymentMethodLastActiveIT extends PostgresTestcontainersConfig {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired TenantProvisioningService provisioningService;
    @Autowired TenantPaymentMethodBootstrapService bootstrapService;
    @Autowired TenantPaymentMethodRepository tenantPaymentMethodRepository;
    @Autowired TenantRepository tenantRepository;
    @Autowired UserRepository userRepository;
    @Autowired JwtTokenProvider jwtTokenProvider;

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    @Test
    void cannot_deactivate_last_active_method_for_pedido_when_allow_no_active_false() throws Exception {
        ProvisionarTenantResponse prov = provisionTenant("pm-last-a", "PLA");

        bootstrapService.ensureDefaults(prov.getTenantId());

        // deixar apenas CASH ativo para PEDIDO
        var tpa = tenantPaymentMethodRepository.findByTenantIdAndCode(prov.getTenantId(), PaymentMethodCode.TPA).orElseThrow();
        tpa.setStatus(PaymentMethodStatus.INACTIVE);
        tenantPaymentMethodRepository.saveAndFlush(tpa);

        var appy = tenantPaymentMethodRepository.findByTenantIdAndCode(prov.getTenantId(), PaymentMethodCode.APPYPAY).orElseThrow();
        appy.setStatus(PaymentMethodStatus.INACTIVE);
        tenantPaymentMethodRepository.saveAndFlush(appy);

        User owner = userRepository.findById(prov.getOwnerUserId()).orElseThrow();
        Tenant tenant = tenantRepository.findById(prov.getTenantId()).orElseThrow();
        String token = jwtTokenProvider.generateTenantScopedToken(
                owner,
                tenant,
                TenantUserRole.TENANT_OWNER,
                TenantUserEstado.ATIVO,
                1,
                null
        );

        mockMvc.perform(post("/tenant/payment-methods/CASH/deactivate")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
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

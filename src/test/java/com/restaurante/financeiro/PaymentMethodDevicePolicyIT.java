package com.restaurante.financeiro;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.dto.request.ProvisionarTenantRequest;
import com.restaurante.dto.request.UpdateDevicePaymentMethodPolicyRequest;
import com.restaurante.dto.response.ProvisionarTenantResponse;
import com.restaurante.model.entity.DispositivoOperacional;
import com.restaurante.model.entity.Instituicao;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.UnidadeAtendimento;
import com.restaurante.repository.UserRepository;
import com.restaurante.repository.TenantUserRepository;
import com.restaurante.repository.UnidadeAtendimentoRepository;
import com.restaurante.repository.TenantRepository;
import com.restaurante.repository.InstituicaoRepository;
import com.restaurante.repository.DispositivoOperacionalRepository;
import com.restaurante.security.JwtTokenProvider;
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

import java.math.BigDecimal;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.restaurante.security.tenant.TenantContextHolder;
import com.restaurante.security.tenant.TenantContext;
import com.restaurante.security.tenant.TenantResolutionSource;
import com.restaurante.model.enums.Role;
import com.restaurante.model.enums.TenantUserRole;
import com.restaurante.model.enums.PaymentMethodPolicyStatus;
import com.restaurante.model.enums.PaymentMethodCode;
import com.restaurante.model.enums.OperationalDeviceType;
import com.restaurante.model.enums.TenantTipo;
import com.restaurante.model.enums.DispositivoTipo;
import com.restaurante.model.enums.DispositivoStatus;
import com.restaurante.model.enums.TenantUserEstado;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "spring.main.web-application-type=servlet"
)
@AutoConfigureMockMvc
@ActiveProfiles("it-postgres")
@Transactional
class PaymentMethodDevicePolicyIT extends PostgresTestcontainersConfig {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired TenantProvisioningService provisioningService;
    @Autowired DispositivoOperacionalRepository dispositivoOperacionalRepository;
    @Autowired TenantRepository tenantRepository;
    @Autowired InstituicaoRepository instituicaoRepository;
    @Autowired UnidadeAtendimentoRepository unidadeAtendimentoRepository;
    @Autowired UserRepository userRepository;
    @Autowired TenantUserRepository tenantUserRepository;
    @Autowired JwtTokenProvider jwtTokenProvider;

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    private String tenantToken(ProvisionarTenantResponse prov) {
        var tenant = tenantRepository.findById(prov.getTenantId()).orElseThrow();
        var user = userRepository.findById(prov.getOwnerUserId()).orElseThrow();
        return jwtTokenProvider.generateTenantScopedToken(
                user,
                tenant,
                TenantUserRole.TENANT_OWNER,
                TenantUserEstado.ATIVO,
                1,
                null
        );
    }

    private org.springframework.security.authentication.UsernamePasswordAuthenticationToken tenantAuth(ProvisionarTenantResponse prov) {
        com.restaurante.security.JwtPrincipal principal = com.restaurante.security.JwtPrincipal.builder()
                .userId(prov.getOwnerUserId())
                .username(prov.getOwnerEmail())
                .email(prov.getOwnerEmail())
                .tokenType("TENANT")
                .tenantId(prov.getTenantId())
                .tenantCode(prov.getTenantCode())
                .tenantRoles(Set.of(Role.ROLE_GERENTE.name(), TenantUserRole.TENANT_OWNER.name()))
                .authorities(java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_GERENTE")))
                .build();
        return new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(principal, "N/A", principal.getAuthorities());
    }

    @Test
    void owner_can_put_and_delete_device_policy_and_invalid_min_max_is_rejected() throws Exception {
        ProvisionarTenantResponse prov = provisionTenant("pm-dev-pol-a", "DPA");
        DispositivoOperacional device = criarDevicePos(prov);

        TenantContextHolder.set(new TenantContext(
                prov.getTenantId(), prov.getTenantCode(), prov.getOwnerUserId(),
                Set.of(Role.ROLE_GERENTE.name(), TenantUserRole.TENANT_OWNER.name()),
                TenantResolutionSource.JWT, false, false
        ));

        String token = tenantToken(prov);

        UpdateDevicePaymentMethodPolicyRequest req = new UpdateDevicePaymentMethodPolicyRequest();
        req.setInheritFromUnidade(false);
        req.setStatus(PaymentMethodPolicyStatus.ALLOW);
        req.setCanStartGateway(false);
        String putResp = mockMvc.perform(put("/tenant/devices/{deviceId}/payment-method-policies/{code}", device.getId(), PaymentMethodCode.APPYPAY)
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication(tenantAuth(prov)))
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(putResp).at("/data/code").asText()).isEqualTo("APPYPAY");

        String list = mockMvc.perform(get("/tenant/devices/{deviceId}/payment-method-policies", device.getId())
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication(tenantAuth(prov)))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode data = objectMapper.readTree(list).at("/data");
        assertThat(data.isArray()).isTrue();

        UpdateDevicePaymentMethodPolicyRequest invalid = new UpdateDevicePaymentMethodPolicyRequest();
        invalid.setInheritFromUnidade(false);
        invalid.setStatus(PaymentMethodPolicyStatus.ALLOW);
        invalid.setMinAmount(new BigDecimal("10.00"));
        invalid.setMaxAmount(new BigDecimal("5.00"));
        mockMvc.perform(put("/tenant/devices/{deviceId}/payment-method-policies/{code}", device.getId(), PaymentMethodCode.CASH)
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication(tenantAuth(prov)))
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(delete("/tenant/devices/{deviceId}/payment-method-policies/{code}", device.getId(), PaymentMethodCode.APPYPAY)
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication(tenantAuth(prov)))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void cross_tenant_device_policy_returns_404() throws Exception {
        ProvisionarTenantResponse a = provisionTenant("pm-dev-pol-b1", "DPB1");
        ProvisionarTenantResponse b = provisionTenant("pm-dev-pol-b2", "DPB2");
        DispositivoOperacional deviceB = criarDevicePos(b);

        TenantContextHolder.set(new TenantContext(
                a.getTenantId(), a.getTenantCode(), a.getOwnerUserId(),
                Set.of(Role.ROLE_GERENTE.name(), TenantUserRole.TENANT_OWNER.name()),
                TenantResolutionSource.JWT, false, false
        ));

        String tokenA = tenantToken(a);

        mockMvc.perform(get("/tenant/devices/{deviceId}/payment-method-policies", deviceB.getId())
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication(tenantAuth(a)))
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNotFound());

        UpdateDevicePaymentMethodPolicyRequest req = new UpdateDevicePaymentMethodPolicyRequest();
        req.setInheritFromUnidade(false);
        req.setStatus(PaymentMethodPolicyStatus.BLOCK);
        mockMvc.perform(put("/tenant/devices/{deviceId}/payment-method-policies/{code}", deviceB.getId(), PaymentMethodCode.CASH)
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication(tenantAuth(a)))
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());
    }

    private DispositivoOperacional criarDevicePos(ProvisionarTenantResponse prov) {
        Tenant tenant = tenantRepository.findById(prov.getTenantId()).orElseThrow();
        Instituicao inst = instituicaoRepository.findById(prov.getInstituicaoId()).orElseThrow();
        UnidadeAtendimento ua = unidadeAtendimentoRepository.findById(prov.getUnidadeAtendimentoId()).orElseThrow();

        DispositivoOperacional d = new DispositivoOperacional();
        d.setTenant(tenant);
        d.setInstituicao(inst);
        d.setUnidadeAtendimento(ua);
        d.setCodigo("POS-POL-" + System.nanoTime());
        d.setNome("POS POL");
        d.setTipo(DispositivoTipo.POS);
        d.setStatus(DispositivoStatus.ATIVO);
        d.setTokenVersion(1);
        return dispositivoOperacionalRepository.save(d);
    }

    private ProvisionarTenantResponse provisionTenant(String nome, String code) {
        TenantContextHolder.set(new TenantContext(
                null, null, 1L, Set.of(Role.ROLE_ADMIN.name()),
                TenantResolutionSource.JWT, true, false
        ));
        String suffix = String.valueOf(Math.abs(System.nanoTime() % 100_000L));
        String uniqueCode = code + suffix;
        if (uniqueCode.length() > 10) uniqueCode = uniqueCode.substring(0, 10);
        String phone = "+244900" + Math.abs(nome.hashCode() % 1_000_000);
        return provisioningService.provisionar(
                ProvisionarTenantRequest.builder()
                        .tenant(ProvisionarTenantRequest.TenantInfo.builder()
                                .nome("Tenant " + nome)
                                .slug(nome + "-" + suffix)
                                .tenantCode(uniqueCode)
                                .tipo(TenantTipo.VENDEDOR_RUA)
                                .build())
                        .planoCodigo("PILOTO")
                        .templateCodigo("VENDEDOR_RUA")
                        .instituicao(ProvisionarTenantRequest.InstituicaoInfo.builder()
                                .nome("Inst " + nome)
                                .sigla(uniqueCode.substring(0, Math.min(4, uniqueCode.length())))
                                .build())
                        .responsavel(ProvisionarTenantRequest.ResponsavelInfo.builder()
                                .email(nome + suffix + "@owner.com")
                                .telefone(phone)
                                .criarUsuario(true)
                                .build())
                        .build()
        );
    }
}

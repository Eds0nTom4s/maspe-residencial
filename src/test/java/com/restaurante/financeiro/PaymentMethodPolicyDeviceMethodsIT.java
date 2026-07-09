package com.restaurante.financeiro;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.dto.request.ProvisionarTenantRequest;
import com.restaurante.dto.request.UpdateDevicePaymentMethodPolicyRequest;
import com.restaurante.dto.request.UpdateUnidadePaymentMethodPolicyRequest;
import com.restaurante.dto.response.ProvisionarTenantResponse;
import com.restaurante.financeiro.paymentmethod.service.TenantPaymentMethodBootstrapService;
import com.restaurante.model.entity.DispositivoOperacional;
import com.restaurante.model.entity.Instituicao;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.UnidadeAtendimento;
import com.restaurante.model.enums.DeviceCapability;
import com.restaurante.model.enums.DispositivoStatus;
import com.restaurante.model.enums.DispositivoTipo;
import com.restaurante.model.enums.PaymentDestination;
import com.restaurante.model.enums.PaymentMethodCode;
import com.restaurante.model.enums.PaymentMethodPolicyStatus;
import com.restaurante.model.enums.Role;
import com.restaurante.model.enums.TenantTipo;
import com.restaurante.model.enums.TenantUserRole;
import com.restaurante.repository.DispositivoOperacionalRepository;
import com.restaurante.repository.InstituicaoRepository;
import com.restaurante.repository.TenantRepository;
import com.restaurante.repository.UnidadeAtendimentoRepository;
import com.restaurante.security.device.DevicePrincipal;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "spring.main.web-application-type=servlet"
)
@AutoConfigureMockMvc
@ActiveProfiles("it-postgres")
class PaymentMethodPolicyDeviceMethodsIT extends PostgresTestcontainersConfig {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired TenantProvisioningService provisioningService;
    @Autowired TenantPaymentMethodBootstrapService bootstrapService;

    @Autowired DispositivoOperacionalRepository dispositivoOperacionalRepository;
    @Autowired TenantRepository tenantRepository;
    @Autowired InstituicaoRepository instituicaoRepository;
    @Autowired UnidadeAtendimentoRepository unidadeAtendimentoRepository;
    @Autowired com.restaurante.repository.UserRepository userRepository;
    @Autowired com.restaurante.repository.TenantUserRepository tenantUserRepository;
    @Autowired com.restaurante.security.JwtTokenProvider jwtTokenProvider;

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
                com.restaurante.model.enums.TenantUserEstado.ATIVO,
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
    void device_payment_methods_respects_unidade_block_and_device_flags() throws Exception {
        ProvisionarTenantResponse prov = provisionTenant("pm-pol-dev-a", "PPDA");
        bootstrapService.ensureDefaults(prov.getTenantId());

        // criar device POS e auth principal
        DispositivoOperacional disp = criarDevicePos(prov);
        DevicePrincipal device = new DevicePrincipal(
                disp.getId(),
                disp.getCodigo(),
                prov.getTenantId(),
                prov.getTenantCode(),
                prov.getInstituicaoId(),
                prov.getUnidadeAtendimentoId(),
                null,
                DispositivoTipo.POS,
                DispositivoStatus.ATIVO,
                List.of(DeviceCapability.VIEW_PAYMENTS, DeviceCapability.INITIATE_PAYMENT, DeviceCapability.CONFIRM_CASH_PAYMENT, DeviceCapability.CONFIRM_TPA_PAYMENT),
                1
        );
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                device, "N/A", List.of(new SimpleGrantedAuthority("ROLE_DEVICE"))
        );

        // baseline: espera CASH e TPA (mínimo)
        JsonNode list0 = listDeviceMethods(auth);
        assertThat(codes(list0)).contains("CASH", "TPA");

        // aplica policy BLOCK TPA na unidade
        TenantContextHolder.set(new TenantContext(
                prov.getTenantId(), prov.getTenantCode(), prov.getOwnerUserId(),
                Set.of(Role.ROLE_GERENTE.name(), TenantUserRole.TENANT_OWNER.name()),
                TenantResolutionSource.JWT, false, false
        ));
        UpdateUnidadePaymentMethodPolicyRequest blockTpa = new UpdateUnidadePaymentMethodPolicyRequest();
        blockTpa.setInheritFromTenant(false);
        blockTpa.setStatus(PaymentMethodPolicyStatus.BLOCK);
        mockMvc.perform(put("/tenant/unidades/{unidadeId}/payment-method-policies/{code}", prov.getUnidadeAtendimentoId(), PaymentMethodCode.TPA)
                        .with(authentication(tenantAuth(prov)))
                        .header("Authorization", "Bearer " + tenantToken(prov))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(blockTpa)))
                .andExpect(status().isOk());

        JsonNode list1 = listDeviceMethods(auth);
        assertThat(codes(list1)).contains("CASH");
        assertThat(codes(list1)).doesNotContain("TPA");

        // aplica policy device: CASH canConfirmManual=false => CASH não deve aparecer no endpoint de disponibilidade
        UpdateDevicePaymentMethodPolicyRequest cashNoConfirm = new UpdateDevicePaymentMethodPolicyRequest();
        cashNoConfirm.setInheritFromUnidade(false);
        cashNoConfirm.setStatus(PaymentMethodPolicyStatus.ALLOW);
        cashNoConfirm.setCanConfirmManual(false);
        mockMvc.perform(put("/tenant/devices/{deviceId}/payment-method-policies/{code}", disp.getId(), PaymentMethodCode.CASH)
                        .with(authentication(tenantAuth(prov)))
                        .header("Authorization", "Bearer " + tenantToken(prov))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cashNoConfirm)))
                .andExpect(status().isOk());

        JsonNode list2 = listDeviceMethods(auth);
        assertThat(codes(list2)).doesNotContain("CASH");
    }

    private JsonNode listDeviceMethods(UsernamePasswordAuthenticationToken auth) throws Exception {
        String resp = mockMvc.perform(get("/device/payment-methods")
                        .with(authentication(auth))
                        .param("destination", PaymentDestination.PEDIDO.name())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resp).at("/data");
    }

    private java.util.Set<String> codes(JsonNode list) {
        java.util.Set<String> out = new java.util.HashSet<>();
        if (list != null && list.isArray()) list.forEach(n -> out.add(n.at("/code").asText()));
        return out;
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
        d.setNome("POS Policy");
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


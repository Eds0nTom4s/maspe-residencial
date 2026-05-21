package com.restaurante.financeiro;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.dto.request.PaymentPolicyRolloutRequest;
import com.restaurante.dto.request.ProvisionarTenantRequest;
import com.restaurante.dto.request.UpdateDevicePaymentMethodPolicyRequest;
import com.restaurante.dto.response.ProvisionarTenantResponse;
import com.restaurante.financeiro.paymentmethod.entity.DevicePaymentMethodPolicy;
import com.restaurante.financeiro.paymentmethod.repository.DevicePaymentMethodPolicyRepository;
import com.restaurante.model.entity.DispositivoOperacional;
import com.restaurante.model.entity.Instituicao;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.UnidadeAtendimento;
import com.restaurante.model.enums.*;
import com.restaurante.repository.DispositivoOperacionalRepository;
import com.restaurante.repository.InstituicaoRepository;
import com.restaurante.repository.TenantRepository;
import com.restaurante.repository.UnidadeAtendimentoRepository;
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

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "spring.main.web-application-type=servlet"
)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("it-postgres")
class PaymentPolicyRolloutOverwriteModeIT extends PostgresTestcontainersConfig {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired TenantProvisioningService provisioningService;
    @Autowired DispositivoOperacionalRepository dispositivoOperacionalRepository;
    @Autowired DevicePaymentMethodPolicyRepository devicePolicyRepository;
    @Autowired TenantRepository tenantRepository;
    @Autowired InstituicaoRepository instituicaoRepository;
    @Autowired UnidadeAtendimentoRepository unidadeAtendimentoRepository;

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    @Test
    void overwrite_only_template_managed_preserves_manual_override() throws Exception {
        ProvisionarTenantResponse prov = provisionTenant("pm-roll-ovr-a", "RO1");
        DispositivoOperacional posCaixa = criarDevice(prov, "POS CAIXA", DispositivoTipo.CHECKOUT, OperationalDeviceType.POS_CAIXA);

        TenantContextHolder.set(new TenantContext(
                prov.getTenantId(), prov.getTenantCode(), prov.getOwnerUserId(),
                Set.of(Role.ROLE_GERENTE.name(), TenantUserRole.TENANT_OWNER.name()),
                TenantResolutionSource.JWT, false, false
        ));

        Long tpl = templateIdByCode("POS_CAIXA_COMPLETO");

        // 1) apply inicial cria 3 policies template-managed
        PaymentPolicyRolloutRequest first = new PaymentPolicyRolloutRequest();
        first.setUnidadeId(prov.getUnidadeAtendimentoId());
        first.setRolloutMode(PaymentMethodPolicyRolloutMode.UNIT_ALL_DEVICES);
        first.setOverwriteMode(PaymentMethodPolicyOverwriteMode.OVERWRITE_EXISTING);
        mockMvc.perform(post("/tenant/payment-policy-templates/{templateId}/rollout/apply", tpl)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(first)))
                .andExpect(status().isOk());

        DevicePaymentMethodPolicy cash = devicePolicyRepository.findByTenant_IdAndDispositivoOperacional_IdAndPaymentMethodCode(
                prov.getTenantId(), posCaixa.getId(), PaymentMethodCode.CASH
        ).orElseThrow();
        assertThat(cash.isTemplateManaged()).isTrue();
        assertThat(cash.isManualOverride()).isFalse();

        // 2) ajuste manual muda CASH para BLOCK e marca manualOverride=true/templateManaged=false
        UpdateDevicePaymentMethodPolicyRequest manual = new UpdateDevicePaymentMethodPolicyRequest();
        manual.setInheritFromUnidade(false);
        manual.setStatus(PaymentMethodPolicyStatus.BLOCK);
        mockMvc.perform(put("/tenant/devices/{deviceId}/payment-method-policies/{code}", posCaixa.getId(), PaymentMethodCode.CASH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(manual)))
                .andExpect(status().isOk());

        DevicePaymentMethodPolicy cashAfter = devicePolicyRepository.findByTenant_IdAndDispositivoOperacional_IdAndPaymentMethodCode(
                prov.getTenantId(), posCaixa.getId(), PaymentMethodCode.CASH
        ).orElseThrow();
        assertThat(cashAfter.getStatus()).isEqualTo(PaymentMethodPolicyStatus.BLOCK);
        assertThat(cashAfter.isManualOverride()).isTrue();
        assertThat(cashAfter.isTemplateManaged()).isFalse();

        // 3) rollout overwrite_only_template_managed não pisa no CASH manual (template desejaria ALLOW)
        PaymentPolicyRolloutRequest second = new PaymentPolicyRolloutRequest();
        second.setUnidadeId(prov.getUnidadeAtendimentoId());
        second.setRolloutMode(PaymentMethodPolicyRolloutMode.UNIT_ALL_DEVICES);
        second.setOverwriteMode(PaymentMethodPolicyOverwriteMode.OVERWRITE_ONLY_TEMPLATE_MANAGED);
        mockMvc.perform(post("/tenant/payment-policy-templates/{templateId}/rollout/apply", tpl)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(second)))
                .andExpect(status().isOk());

        DevicePaymentMethodPolicy cashFinal = devicePolicyRepository.findByTenant_IdAndDispositivoOperacional_IdAndPaymentMethodCode(
                prov.getTenantId(), posCaixa.getId(), PaymentMethodCode.CASH
        ).orElseThrow();
        assertThat(cashFinal.getStatus()).isEqualTo(PaymentMethodPolicyStatus.BLOCK);
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

    private DispositivoOperacional criarDevice(ProvisionarTenantResponse prov, String nome, DispositivoTipo tipo, OperationalDeviceType operationalType) {
        Tenant tenant = tenantRepository.findById(prov.getTenantId()).orElseThrow();
        Instituicao inst = instituicaoRepository.findById(prov.getInstituicaoId()).orElseThrow();
        UnidadeAtendimento ua = unidadeAtendimentoRepository.findById(prov.getUnidadeAtendimentoId()).orElseThrow();

        DispositivoOperacional d = new DispositivoOperacional();
        d.setTenant(tenant);
        d.setInstituicao(inst);
        d.setUnidadeAtendimento(ua);
        d.setCodigo("ROLL-" + System.nanoTime());
        d.setNome(nome);
        d.setTipo(tipo);
        d.setOperationalDeviceType(operationalType);
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


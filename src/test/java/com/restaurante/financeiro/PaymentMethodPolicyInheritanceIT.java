package com.restaurante.financeiro;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.dto.request.ProvisionarTenantRequest;
import com.restaurante.dto.request.UpdateUnidadePaymentMethodPolicyRequest;
import com.restaurante.dto.response.ProvisionarTenantResponse;
import com.restaurante.financeiro.paymentmethod.service.TenantPaymentMethodBootstrapService;
import com.restaurante.model.entity.Instituicao;
import com.restaurante.model.entity.QrCodeOperacional;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.UnidadeAtendimento;
import com.restaurante.model.enums.PaymentDestination;
import com.restaurante.model.enums.PaymentMethodCode;
import com.restaurante.model.enums.PaymentMethodPolicyStatus;
import com.restaurante.model.enums.Role;
import com.restaurante.model.enums.TenantTipo;
import com.restaurante.model.enums.TenantUserRole;
import com.restaurante.model.enums.TipoUnidadeAtendimento;
import com.restaurante.model.enums.QrCodeOperacionalTipo;
import com.restaurante.repository.InstituicaoRepository;
import com.restaurante.repository.TenantRepository;
import com.restaurante.repository.UnidadeAtendimentoRepository;
import com.restaurante.security.tenant.TenantContext;
import com.restaurante.security.tenant.TenantContextHolder;
import com.restaurante.security.tenant.TenantResolutionSource;
import com.restaurante.service.QrCodeOperacionalService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "spring.main.web-application-type=servlet"
)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("it-postgres")
class PaymentMethodPolicyInheritanceIT extends PostgresTestcontainersConfig {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired TenantProvisioningService provisioningService;
    @Autowired TenantPaymentMethodBootstrapService bootstrapService;

    @Autowired TenantRepository tenantRepository;
    @Autowired InstituicaoRepository instituicaoRepository;
    @Autowired UnidadeAtendimentoRepository unidadeAtendimentoRepository;
    @Autowired QrCodeOperacionalService qrCodeOperacionalService;

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    @Test
    void unidade_policy_block_affects_qr_and_delete_restores_inheritance() throws Exception {
        ProvisionarTenantResponse prov = provisionTenant("pm-pol-inherit-a", "PPIA");
        bootstrapService.ensureDefaults(prov.getTenantId());

        Tenant tenant = tenantRepository.findById(prov.getTenantId()).orElseThrow();
        Instituicao inst = instituicaoRepository.findById(prov.getInstituicaoId()).orElseThrow();
        UnidadeAtendimento ua = unidadeAtendimentoRepository.findById(prov.getUnidadeAtendimentoId()).orElseThrow();

        QrCodeOperacional qr = qrCodeOperacionalService.criarQr(
                tenant.getId(), inst.getId(), ua.getId(), null, QrCodeOperacionalTipo.UNIDADE_ATENDIMENTO, "QR"
        );

        // sem policy explícita: herda tenant => CASH aparece
        JsonNode list0 = listQrMethods(qr.getToken());
        assertThat(codes(list0)).contains("CASH");

        // aplica policy BLOCK CASH na unidade
        TenantContextHolder.set(new TenantContext(
                prov.getTenantId(), prov.getTenantCode(), prov.getOwnerUserId(),
                Set.of(Role.ROLE_GERENTE.name(), TenantUserRole.TENANT_OWNER.name()),
                TenantResolutionSource.JWT, false, false
        ));
        UpdateUnidadePaymentMethodPolicyRequest block = new UpdateUnidadePaymentMethodPolicyRequest();
        block.setInheritFromTenant(false);
        block.setStatus(PaymentMethodPolicyStatus.BLOCK);

        mockMvc.perform(put("/tenant/unidades/{unidadeId}/payment-method-policies/{code}", ua.getId(), PaymentMethodCode.CASH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(block)))
                .andExpect(status().isOk());

        JsonNode list1 = listQrMethods(qr.getToken());
        assertThat(codes(list1)).doesNotContain("CASH");

        // remove policy => volta a herdar tenant
        mockMvc.perform(delete("/tenant/unidades/{unidadeId}/payment-method-policies/{code}", ua.getId(), PaymentMethodCode.CASH))
                .andExpect(status().isOk());

        JsonNode list2 = listQrMethods(qr.getToken());
        assertThat(codes(list2)).contains("CASH");
    }

    private JsonNode listQrMethods(String token) throws Exception {
        String resp = mockMvc.perform(get("/public/q/{token}/payment-methods", token)
                        .param("destination", PaymentDestination.PEDIDO.name()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resp).at("/data");
    }

    private java.util.Set<String> codes(JsonNode list) {
        java.util.Set<String> out = new java.util.HashSet<>();
        if (list != null && list.isArray()) {
            list.forEach(n -> out.add(n.at("/code").asText()));
        }
        return out;
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

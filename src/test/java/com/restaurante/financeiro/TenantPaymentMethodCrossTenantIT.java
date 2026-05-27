package com.restaurante.financeiro;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.dto.request.AbrirTurnoRequest;
import com.restaurante.dto.request.ChecklistItemRespostaRequest;
import com.restaurante.dto.request.CriarCarregamentoFundoRequest;
import com.restaurante.dto.request.ConfirmarOrdemManualRequest;
import com.restaurante.dto.request.ProvisionarTenantRequest;
import com.restaurante.dto.response.ProvisionarTenantResponse;
import com.restaurante.financeiro.paymentmethod.repository.TenantPaymentMethodRepository;
import com.restaurante.financeiro.paymentmethod.service.TenantPaymentMethodBootstrapService;
import com.restaurante.model.entity.DispositivoOperacional;
import com.restaurante.model.entity.Instituicao;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.UnidadeAtendimento;
import com.restaurante.model.enums.*;
import com.restaurante.security.device.DevicePrincipal;
import com.restaurante.security.tenant.TenantContext;
import com.restaurante.security.tenant.TenantContextHolder;
import com.restaurante.security.tenant.TenantResolutionSource;
import com.restaurante.service.TenantProvisioningService;
import com.restaurante.testsupport.PostgresTestcontainersConfig;
import com.restaurante.repository.DispositivoOperacionalRepository;
import com.restaurante.repository.InstituicaoRepository;
import com.restaurante.repository.TenantRepository;
import com.restaurante.repository.UnidadeAtendimentoRepository;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "spring.main.web-application-type=servlet"
)
@AutoConfigureMockMvc
@ActiveProfiles("it-postgres")
class TenantPaymentMethodCrossTenantIT extends PostgresTestcontainersConfig {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired TenantProvisioningService provisioningService;
    @Autowired TenantPaymentMethodRepository tenantPaymentMethodRepository;
    @Autowired TenantPaymentMethodBootstrapService bootstrapService;
    @Autowired DispositivoOperacionalRepository dispositivoOperacionalRepository;
    @Autowired TenantRepository tenantRepository;
    @Autowired InstituicaoRepository instituicaoRepository;
    @Autowired UnidadeAtendimentoRepository unidadeAtendimentoRepository;

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    @Test
    void public_qr_payment_methods_are_resolved_from_qr_token_and_do_not_leak_cross_tenant() throws Exception {
        ProvisionarTenantResponse a = provisionTenant("pm-ct-a", "CTA");
        ProvisionarTenantResponse b = provisionTenant("pm-ct-b", "CTB");

        bootstrapService.ensureDefaults(a.getTenantId());
        bootstrapService.ensureDefaults(b.getTenantId());

        // Tenant B: desativar TPA e esconder CASH do QR para criar uma "assinatura" distinta
        var tpaB = tenantPaymentMethodRepository.findByTenantIdAndCode(b.getTenantId(), PaymentMethodCode.TPA).orElseThrow();
        tpaB.setStatus(PaymentMethodStatus.INACTIVE);
        tenantPaymentMethodRepository.saveAndFlush(tpaB);
        var cashB = tenantPaymentMethodRepository.findByTenantIdAndCode(b.getTenantId(), PaymentMethodCode.CASH).orElseThrow();
        cashB.setEnabledForQr(false);
        tenantPaymentMethodRepository.saveAndFlush(cashB);

        String respA = mockMvc.perform(get("/public/q/{token}/payment-methods", a.getMesas().get(0).getQrToken())
                        .param("destination", "PEDIDO")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode codesA = objectMapper.readTree(respA).at("/data");
        assertThat(codesA.toString()).contains("CASH");

        String respB = mockMvc.perform(get("/public/q/{token}/payment-methods", b.getMesas().get(0).getQrToken())
                        .param("destination", "PEDIDO")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode dataB = objectMapper.readTree(respB).at("/data");
        assertThat(dataB.toString()).doesNotContain("TPA");
        assertThat(dataB.toString()).doesNotContain("CASH");
    }

    @Test
    void device_cannot_confirm_manual_order_from_another_tenant() throws Exception {
        ProvisionarTenantResponse a = provisionTenant("pm-ct-a2", "CTA2");
        ProvisionarTenantResponse b = provisionTenant("pm-ct-b2", "CTB2");

        bootstrapService.ensureDefaults(a.getTenantId());
        bootstrapService.ensureDefaults(b.getTenantId());

        String mesaQrTokenA = a.getMesas().get(0).getQrToken();
        String consumoJson = mockMvc.perform(post("/public/q/" + mesaQrTokenA + "/consumos/anonimo"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String codigoConsumo = objectMapper.readTree(consumoJson).at("/data/qrCodeSessao").asText();

        CriarCarregamentoFundoRequest criar = new CriarCarregamentoFundoRequest();
        criar.setValor(new BigDecimal("1000.00"));
        criar.setMetodoPagamento(MetodoPagamentoManual.CASH);
        String ordemJson = mockMvc.perform(post("/public/q/" + mesaQrTokenA + "/consumos/" + codigoConsumo + "/carregamentos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(criar)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long ordemIdA = objectMapper.readTree(ordemJson).at("/data/ordemPagamentoId").asLong();

        // abrir turnos em ambos para evitar 409 por turno fechado no device B
        abrirTurnoOwner(a);
        abrirTurnoOwner(b);

        DispositivoOperacional dispB = criarDevicePos(b);
        DevicePrincipal deviceB = new DevicePrincipal(
                dispB.getId(),
                dispB.getCodigo(),
                b.getTenantId(),
                b.getTenantCode(),
                b.getInstituicaoId(),
                b.getUnidadeAtendimentoId(),
                null,
                DispositivoTipo.POS,
                DispositivoStatus.ATIVO,
                List.of(DeviceCapability.CONFIRM_CASH_PAYMENT, DeviceCapability.VIEW_PAYMENTS),
                1
        );
        UsernamePasswordAuthenticationToken authB = new UsernamePasswordAuthenticationToken(
                deviceB, "N/A", List.of(new SimpleGrantedAuthority("ROLE_DEVICE"))
        );

        ConfirmarOrdemManualRequest confirm = new ConfirmarOrdemManualRequest();
        confirm.setClientRequestId("ct-confirm-1");
        confirm.setMetodoConfirmado(MetodoPagamentoManual.CASH);
        confirm.setValorRecebido(new BigDecimal("1000.00"));
        confirm.setObservacao("ct");

        mockMvc.perform(post("/device/ordens-pagamento/{ordemId}/confirmar-manual", ordemIdA)
                        .with(authentication(authB))
                        .header("Idempotency-Key", "idem-ct-confirm-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(confirm)))
                .andExpect(status().isNotFound());
    }

    private void abrirTurnoOwner(ProvisionarTenantResponse prov) throws Exception {
        TenantContextHolder.set(new TenantContext(
                prov.getTenantId(), prov.getTenantCode(), prov.getOwnerUserId(),
                Set.of(Role.ROLE_GERENTE.name(), TenantUserRole.TENANT_OWNER.name()),
                TenantResolutionSource.JWT, false, false
        ));
        mockMvc.perform(post("/tenant/operacao/turnos/abrir")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(abrirTurnoReq(prov))))
                .andExpect(status().isCreated());
    }

    private AbrirTurnoRequest abrirTurnoReq(ProvisionarTenantResponse prov) {
        AbrirTurnoRequest req = new AbrirTurnoRequest();
        req.setInstituicaoId(prov.getInstituicaoId());
        req.setUnidadeAtendimentoId(prov.getUnidadeAtendimentoId());
        req.setTipo(TurnoOperacionalTipo.DIARIO);
        req.setNome("Turno");
        req.setObservacao("Abertura");
        req.setChecklist(List.of(
                boolItem("DEVICE_ONLINE", true),
                boolItem("QR_VISIVEL", true),
                boolItem("CATALOGO_ATUALIZADO", true),
                boolItem("UNIDADE_PRODUCAO_ATIVA", true),
                boolItem("OPERADOR_CONFIRMOU", true)
        ));
        return req;
    }

    private ChecklistItemRespostaRequest boolItem(String codigo, boolean v) {
        ChecklistItemRespostaRequest it = new ChecklistItemRespostaRequest();
        it.setCodigo(codigo);
        it.setValorBoolean(v);
        return it;
    }

    private DispositivoOperacional criarDevicePos(ProvisionarTenantResponse prov) {
        Tenant tenant = tenantRepository.findById(prov.getTenantId()).orElseThrow();
        Instituicao inst = instituicaoRepository.findById(prov.getInstituicaoId()).orElseThrow();
        UnidadeAtendimento ua = unidadeAtendimentoRepository.findById(prov.getUnidadeAtendimentoId()).orElseThrow();
        DispositivoOperacional d = new DispositivoOperacional();
        d.setTenant(tenant);
        d.setInstituicao(inst);
        d.setUnidadeAtendimento(ua);
        d.setCodigo("POS-CT-" + System.nanoTime());
        d.setNome("POS CT");
        d.setTipo(DispositivoTipo.POS);
        d.setStatus(DispositivoStatus.ATIVO);
        d.setTokenVersion(1);
        return dispositivoOperacionalRepository.save(d);
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
}

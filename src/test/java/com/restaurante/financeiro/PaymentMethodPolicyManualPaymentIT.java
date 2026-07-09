package com.restaurante.financeiro;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.dto.request.AbrirTurnoRequest;
import com.restaurante.dto.request.ChecklistItemRespostaRequest;
import com.restaurante.dto.request.ConfirmarOrdemManualRequest;
import com.restaurante.dto.request.CriarCarregamentoFundoRequest;
import com.restaurante.dto.request.ProvisionarTenantRequest;
import com.restaurante.dto.request.UpdateDevicePaymentMethodPolicyRequest;
import com.restaurante.dto.request.UpdateUnidadePaymentMethodPolicyRequest;
import com.restaurante.dto.response.ProvisionarTenantResponse;
import com.restaurante.model.entity.DispositivoOperacional;
import com.restaurante.model.enums.DeviceCapability;
import com.restaurante.model.enums.DispositivoStatus;
import com.restaurante.model.enums.DispositivoTipo;
import com.restaurante.model.enums.MetodoPagamentoManual;
import com.restaurante.model.enums.PaymentMethodCode;
import com.restaurante.model.enums.PaymentMethodPolicyStatus;
import com.restaurante.model.enums.Role;
import com.restaurante.model.enums.TenantTipo;
import com.restaurante.model.enums.TenantUserRole;
import com.restaurante.model.enums.TurnoOperacionalTipo;
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
import com.restaurante.testsupport.UniqueTestData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "spring.main.web-application-type=servlet"
)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("it-postgres")
class PaymentMethodPolicyManualPaymentIT extends PostgresTestcontainersConfig {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired TenantProvisioningService provisioningService;
    @Autowired DispositivoOperacionalRepository dispositivoOperacionalRepository;
    @Autowired TenantRepository tenantRepository;
    @Autowired InstituicaoRepository instituicaoRepository;
    @Autowired UnidadeAtendimentoRepository unidadeAtendimentoRepository;
    @Autowired FinanceiroItFixtureSupport fixtureSupport;

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    @WithMockUser(username = "owner")
    void confirm_manual_cash_is_blocked_if_unidade_policy_blocks_after_order_creation() throws Exception {
        ProvisionarTenantResponse prov = provisionTenant("pm-pol-manual-a", "PPMA");
        String mesaQrToken = fixtureSupport.ensureMesaQrToken(prov);

        setTenantOwnerContext(prov);
        mockMvc.perform(post("/tenant/operacao/turnos/abrir")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(abrirTurnoReq(prov))))
                .andExpect(status().isCreated());

        // cria consumo anónimo
        String consumoJson = mockMvc.perform(post("/public/q/{token}/consumos/anonimo", mesaQrToken))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String codigoConsumo = objectMapper.readTree(consumoJson).at("/data/qrCodeSessao").asText();

        // cria ordem de carregamento CASH (policy ainda herda do tenant)
        CriarCarregamentoFundoRequest criar = new CriarCarregamentoFundoRequest();
        criar.setValor(new BigDecimal("10000.00"));
        criar.setMetodoPagamento(MetodoPagamentoManual.CASH);
        String ordemJson = mockMvc.perform(post("/public/q/{token}/consumos/{codigo}/carregamentos", mesaQrToken, codigoConsumo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(criar)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long ordemId = objectMapper.readTree(ordemJson).at("/data/ordemPagamentoId").asLong();

        // depois do pedido: unidade bloqueia CASH
        UpdateUnidadePaymentMethodPolicyRequest blockCash = new UpdateUnidadePaymentMethodPolicyRequest();
        blockCash.setInheritFromTenant(false);
        blockCash.setStatus(PaymentMethodPolicyStatus.BLOCK);
        mockMvc.perform(put("/tenant/unidades/{unidadeId}/payment-method-policies/{code}", prov.getUnidadeAtendimentoId(), PaymentMethodCode.CASH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(blockCash)))
                .andExpect(status().isOk());

        // device confirma manual => deve falhar por policy
        UsernamePasswordAuthenticationToken auth = deviceAuth(prov, List.of(DeviceCapability.CONFIRM_CASH_PAYMENT, DeviceCapability.VIEW_PAYMENTS));
        SecurityContextHolder.getContext().setAuthentication(auth);
        ConfirmarOrdemManualRequest confirm = new ConfirmarOrdemManualRequest();
        confirm.setClientRequestId("confirm-pol-1");
        confirm.setMetodoConfirmado(MetodoPagamentoManual.CASH);
        confirm.setValorRecebido(new BigDecimal("10000.00"));
        confirm.setObservacao("ok");

        mockMvc.perform(post("/device/ordens-pagamento/{ordemId}/confirmar-manual", ordemId)
                        .with(authentication(auth))
                        .header("Idempotency-Key", "idem-confirm-pol-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(confirm)))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(username = "owner")
    void confirm_manual_cash_is_blocked_if_device_canConfirmManual_false() throws Exception {
        ProvisionarTenantResponse prov = provisionTenant("pm-pol-manual-b", "PPMB");
        String mesaQrToken = fixtureSupport.ensureMesaQrToken(prov);

        setTenantOwnerContext(prov);
        mockMvc.perform(post("/tenant/operacao/turnos/abrir")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(abrirTurnoReq(prov))))
                .andExpect(status().isCreated());

        String consumoJson = mockMvc.perform(post("/public/q/{token}/consumos/anonimo", mesaQrToken))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String codigoConsumo = objectMapper.readTree(consumoJson).at("/data/qrCodeSessao").asText();

        CriarCarregamentoFundoRequest criar = new CriarCarregamentoFundoRequest();
        criar.setValor(new BigDecimal("5000.00"));
        criar.setMetodoPagamento(MetodoPagamentoManual.CASH);
        String ordemJson = mockMvc.perform(post("/public/q/{token}/consumos/{codigo}/carregamentos", mesaQrToken, codigoConsumo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(criar)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long ordemId = objectMapper.readTree(ordemJson).at("/data/ordemPagamentoId").asLong();

        DispositivoOperacional disp = criarDevicePos(prov);

        // device policy: CASH canConfirmManual=false
        UpdateDevicePaymentMethodPolicyRequest devPol = new UpdateDevicePaymentMethodPolicyRequest();
        devPol.setInheritFromUnidade(false);
        devPol.setStatus(PaymentMethodPolicyStatus.ALLOW);
        devPol.setCanConfirmManual(false);
        mockMvc.perform(put("/tenant/devices/{deviceId}/payment-method-policies/{code}", disp.getId(), PaymentMethodCode.CASH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(devPol)))
                .andExpect(status().isOk());

        DevicePrincipal principal = new DevicePrincipal(
                disp.getId(),
                disp.getCodigo(),
                prov.getTenantId(),
                prov.getTenantCode(),
                prov.getInstituicaoId(),
                prov.getUnidadeAtendimentoId(),
                null,
                DispositivoTipo.POS,
                DispositivoStatus.ATIVO,
                List.of(DeviceCapability.CONFIRM_CASH_PAYMENT, DeviceCapability.VIEW_PAYMENTS),
                1
        );
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                principal, "N/A", List.of(new SimpleGrantedAuthority("ROLE_DEVICE"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);

        ConfirmarOrdemManualRequest confirm = new ConfirmarOrdemManualRequest();
        confirm.setClientRequestId("confirm-pol-2");
        confirm.setMetodoConfirmado(MetodoPagamentoManual.CASH);
        confirm.setValorRecebido(new BigDecimal("5000.00"));
        confirm.setObservacao("ok");

        mockMvc.perform(post("/device/ordens-pagamento/{ordemId}/confirmar-manual", ordemId)
                        .with(authentication(auth))
                        .header("Idempotency-Key", "idem-confirm-pol-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(confirm)))
                .andExpect(status().isConflict());
    }

    private UsernamePasswordAuthenticationToken deviceAuth(ProvisionarTenantResponse prov, List<DeviceCapability> capabilities) {
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
                capabilities,
                1
        );
        return new UsernamePasswordAuthenticationToken(
                device, "N/A", List.of(new SimpleGrantedAuthority("ROLE_DEVICE"))
        );
    }

    private DispositivoOperacional criarDevicePos(ProvisionarTenantResponse prov) {
        DispositivoOperacional d = new DispositivoOperacional();
        d.setTenant(tenantRepository.findById(prov.getTenantId()).orElseThrow());
        d.setInstituicao(instituicaoRepository.findById(prov.getInstituicaoId()).orElseThrow());
        d.setUnidadeAtendimento(unidadeAtendimentoRepository.findById(prov.getUnidadeAtendimentoId()).orElseThrow());
        d.setCodigo("POS-MANPOL-" + System.nanoTime());
        d.setNome("POS Manual Policy");
        d.setTipo(DispositivoTipo.POS);
        d.setStatus(DispositivoStatus.ATIVO);
        d.setTokenVersion(1);
        return dispositivoOperacionalRepository.save(d);
    }

    private void setTenantOwnerContext(ProvisionarTenantResponse prov) {
        TenantContextHolder.set(new TenantContext(
                prov.getTenantId(), prov.getTenantCode(), prov.getOwnerUserId(),
                Set.of(Role.ROLE_GERENTE.name(), TenantUserRole.TENANT_OWNER.name()),
                TenantResolutionSource.JWT, false, false
        ));
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

    private ProvisionarTenantResponse provisionTenant(String nome, String code) {
        TenantContextHolder.set(new TenantContext(
                null, null, 1L, Set.of(Role.ROLE_ADMIN.name()),
                TenantResolutionSource.JWT, true, false
        ));
        String tenantSlug = UniqueTestData.uniqueSlug(nome);
        String tenantCode = UniqueTestData.uniqueTenantCode(code);
        String instituicaoSigla = UniqueTestData.uniqueInstituicaoSigla(code);
        String ownerEmail = UniqueTestData.uniqueEmail(nome + "-owner");
        String phone = UniqueTestData.uniqueTelefone();
        return provisioningService.provisionar(
                ProvisionarTenantRequest.builder()
                        .tenant(ProvisionarTenantRequest.TenantInfo.builder()
                                .nome("Tenant " + nome)
                                .slug(tenantSlug)
                                .tenantCode(tenantCode)
                                .tipo(TenantTipo.VENDEDOR_RUA)
                                .build())
                        .planoCodigo("PILOTO")
                        .templateCodigo("VENDEDOR_RUA")
                        .instituicao(ProvisionarTenantRequest.InstituicaoInfo.builder()
                                .nome("Inst " + nome)
                                .sigla(instituicaoSigla)
                                .build())
                        .responsavel(ProvisionarTenantRequest.ResponsavelInfo.builder()
                                .email(ownerEmail)
                                .telefone(phone)
                                .criarUsuario(true)
                                .build())
                        .build()
        );
    }

}

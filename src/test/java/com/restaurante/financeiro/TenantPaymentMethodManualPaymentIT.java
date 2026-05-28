package com.restaurante.financeiro;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.dto.request.AbrirTurnoRequest;
import com.restaurante.dto.request.ChecklistItemRespostaRequest;
import com.restaurante.dto.request.ConfirmarOrdemManualRequest;
import com.restaurante.dto.request.CriarCarregamentoFundoRequest;
import com.restaurante.dto.request.ProvisionarTenantRequest;
import com.restaurante.dto.response.ProvisionarTenantResponse;
import com.restaurante.financeiro.paymentmethod.repository.TenantPaymentMethodRepository;
import com.restaurante.model.entity.DispositivoOperacional;
import com.restaurante.model.entity.Instituicao;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.UnidadeAtendimento;
import com.restaurante.model.enums.DeviceCapability;
import com.restaurante.model.enums.DispositivoStatus;
import com.restaurante.model.enums.DispositivoTipo;
import com.restaurante.model.enums.MetodoPagamentoManual;
import com.restaurante.model.enums.PaymentMethodCode;
import com.restaurante.model.enums.PaymentMethodStatus;
import com.restaurante.model.enums.Role;
import com.restaurante.model.enums.TenantTipo;
import com.restaurante.model.enums.TenantUserRole;
import com.restaurante.model.enums.TurnoOperacionalTipo;
import com.restaurante.repository.DispositivoOperacionalRepository;
import com.restaurante.repository.InstituicaoRepository;
import com.restaurante.repository.TenantRepository;
import com.restaurante.repository.UnidadeAtendimentoRepository;
import com.restaurante.security.device.DevicePrincipal;
import com.restaurante.security.tenant.TenantContextHolder;
import com.restaurante.security.JwtTokenProvider;
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

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "spring.main.web-application-type=servlet"
)
@AutoConfigureMockMvc
@ActiveProfiles("it-postgres")
class TenantPaymentMethodManualPaymentIT extends PostgresTestcontainersConfig {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired TenantProvisioningService provisioningService;
    @Autowired DispositivoOperacionalRepository dispositivoOperacionalRepository;
	@Autowired TenantPaymentMethodRepository tenantPaymentMethodRepository;
	@Autowired TenantRepository tenantRepository;
	@Autowired InstituicaoRepository instituicaoRepository;
	@Autowired UnidadeAtendimentoRepository unidadeAtendimentoRepository;
	@Autowired com.restaurante.repository.UserRepository userRepository;
	@Autowired JwtTokenProvider jwtTokenProvider;

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    @Test
	    void confirm_manual_cash_is_blocked_if_method_deactivated_after_order_creation() throws Exception {
	        ProvisionarTenantResponse prov = provisionTenant("pm-manual-a", "PMA1");
	        String mesaQrToken = prov.getMesas().get(0).getQrToken();

	        activateCashForQrAndFundo(prov.getTenantId());

	        // cria consumo anónimo
	        String consumoJson = mockMvc.perform(post("/public/q/" + mesaQrToken + "/consumos/anonimo"))
	                .andExpect(status().isCreated())
	                .andReturn().getResponse().getContentAsString();
	        String codigoConsumo = objectMapper.readTree(consumoJson).at("/data/qrCodeSessao").asText();

        // cria ordem de carregamento CASH
        CriarCarregamentoFundoRequest criar = new CriarCarregamentoFundoRequest();
        criar.setValor(new BigDecimal("10000.00"));
        criar.setMetodoPagamento(MetodoPagamentoManual.CASH);
        String ordemJson = mockMvc.perform(post("/public/q/" + mesaQrToken + "/consumos/" + codigoConsumo + "/carregamentos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(criar)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
	        Long ordemId = objectMapper.readTree(ordemJson).at("/data/ordemPagamentoId").asLong();

	        // abrir turno (tenant context)
	        String ownerToken = ownerToken(prov, TenantUserRole.TENANT_OWNER);
	        mockMvc.perform(post("/tenant/operacao/turnos/abrir")
	                        .header("Authorization", "Bearer " + ownerToken)
	                        .contentType(MediaType.APPLICATION_JSON)
	                        .content(objectMapper.writeValueAsString(abrirTurnoReq(prov))))
	                .andExpect(status().isCreated());

        // desativa CASH após criação
        var cash = tenantPaymentMethodRepository.findByTenantIdAndCode(prov.getTenantId(), PaymentMethodCode.CASH).orElseThrow();
        cash.setStatus(PaymentMethodStatus.INACTIVE);
        tenantPaymentMethodRepository.saveAndFlush(cash);

        // device pos com capabilities de confirmação
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
                List.of(DeviceCapability.CONFIRM_CASH_PAYMENT, DeviceCapability.VIEW_PAYMENTS),
                1
        );
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                device, "N/A", List.of(new SimpleGrantedAuthority("ROLE_DEVICE"))
        );

        ConfirmarOrdemManualRequest confirm = new ConfirmarOrdemManualRequest();
        confirm.setClientRequestId("confirm-1");
        confirm.setMetodoConfirmado(MetodoPagamentoManual.CASH);
        confirm.setValorRecebido(new BigDecimal("10000.00"));
        confirm.setObservacao("ok");

        mockMvc.perform(post("/device/ordens-pagamento/{ordemId}/confirmar-manual", ordemId)
                        .with(authentication(auth))
                        .header("Idempotency-Key", "idem-confirm-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(confirm)))
                .andExpect(status().isConflict());
    }

    @Test
	    void confirm_manual_cash_is_blocked_if_method_suspended_after_order_creation() throws Exception {
	        ProvisionarTenantResponse prov = provisionTenant("pm-manual-susp", "PMA2");
	        String mesaQrToken = prov.getMesas().get(0).getQrToken();

	        activateCashForQrAndFundo(prov.getTenantId());

	        String consumoJson = mockMvc.perform(post("/public/q/" + mesaQrToken + "/consumos/anonimo"))
	                .andExpect(status().isCreated())
	                .andReturn().getResponse().getContentAsString();
	        String codigoConsumo = objectMapper.readTree(consumoJson).at("/data/qrCodeSessao").asText();

        CriarCarregamentoFundoRequest criar = new CriarCarregamentoFundoRequest();
        criar.setValor(new BigDecimal("10000.00"));
        criar.setMetodoPagamento(MetodoPagamentoManual.CASH);
        String ordemJson = mockMvc.perform(post("/public/q/" + mesaQrToken + "/consumos/" + codigoConsumo + "/carregamentos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(criar)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
	        Long ordemId = objectMapper.readTree(ordemJson).at("/data/ordemPagamentoId").asLong();

	        // abrir turno
	        String ownerToken = ownerToken(prov, TenantUserRole.TENANT_OWNER);
	        mockMvc.perform(post("/tenant/operacao/turnos/abrir")
	                        .header("Authorization", "Bearer " + ownerToken)
	                        .contentType(MediaType.APPLICATION_JSON)
	                        .content(objectMapper.writeValueAsString(abrirTurnoReq(prov))))
	                .andExpect(status().isCreated());

        var cash = tenantPaymentMethodRepository.findByTenantIdAndCode(prov.getTenantId(), PaymentMethodCode.CASH).orElseThrow();
        cash.setStatus(PaymentMethodStatus.SUSPENDED);
        tenantPaymentMethodRepository.saveAndFlush(cash);

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
                List.of(DeviceCapability.CONFIRM_CASH_PAYMENT, DeviceCapability.VIEW_PAYMENTS),
                1
        );
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                device, "N/A", List.of(new SimpleGrantedAuthority("ROLE_DEVICE"))
        );

        ConfirmarOrdemManualRequest confirm = new ConfirmarOrdemManualRequest();
        confirm.setClientRequestId("confirm-susp-1");
        confirm.setMetodoConfirmado(MetodoPagamentoManual.CASH);
        confirm.setValorRecebido(new BigDecimal("10000.00"));
        confirm.setObservacao("ok");

        mockMvc.perform(post("/device/ordens-pagamento/{ordemId}/confirmar-manual", ordemId)
                        .with(authentication(auth))
                        .header("Idempotency-Key", "idem-confirm-susp-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(confirm)))
                .andExpect(status().isConflict());
    }

    @Test
	    void confirm_manual_cash_requires_open_turno_even_if_method_active() throws Exception {
	        ProvisionarTenantResponse prov = provisionTenant("pm-manual-no-turno", "PMA3");
	        String mesaQrToken = prov.getMesas().get(0).getQrToken();

	        activateCashForQrAndFundo(prov.getTenantId());

	        String consumoJson = mockMvc.perform(post("/public/q/" + mesaQrToken + "/consumos/anonimo"))
	                .andExpect(status().isCreated())
	                .andReturn().getResponse().getContentAsString();
	        String codigoConsumo = objectMapper.readTree(consumoJson).at("/data/qrCodeSessao").asText();

        CriarCarregamentoFundoRequest criar = new CriarCarregamentoFundoRequest();
        criar.setValor(new BigDecimal("5000.00"));
        criar.setMetodoPagamento(MetodoPagamentoManual.CASH);
        String ordemJson = mockMvc.perform(post("/public/q/" + mesaQrToken + "/consumos/" + codigoConsumo + "/carregamentos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(criar)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
	        Long ordemId = objectMapper.readTree(ordemJson).at("/data/ordemPagamentoId").asLong();

        // sem abrir turno
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
                List.of(DeviceCapability.CONFIRM_CASH_PAYMENT, DeviceCapability.VIEW_PAYMENTS),
                1
        );
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                device, "N/A", List.of(new SimpleGrantedAuthority("ROLE_DEVICE"))
        );

        ConfirmarOrdemManualRequest confirm = new ConfirmarOrdemManualRequest();
        confirm.setClientRequestId("confirm-no-turno-1");
        confirm.setMetodoConfirmado(MetodoPagamentoManual.CASH);
        confirm.setValorRecebido(new BigDecimal("5000.00"));
        confirm.setObservacao("ok");

	        mockMvc.perform(post("/device/ordens-pagamento/{ordemId}/confirmar-manual", ordemId)
	                        .with(authentication(auth))
	                        .header("Idempotency-Key", "idem-confirm-no-turno-1")
	                        .contentType(MediaType.APPLICATION_JSON)
	                        .content(objectMapper.writeValueAsString(confirm)))
	                .andExpect(status().isConflict());
	    }

	    private void activateCashForQrAndFundo(Long tenantId) {
	        var cash = tenantPaymentMethodRepository.findByTenantIdAndCode(tenantId, PaymentMethodCode.CASH).orElseThrow();
	        cash.setStatus(PaymentMethodStatus.ACTIVE);
	        cash.setEnabledForQr(true);
	        cash.setEnabledForFundoConsumo(true);
	        tenantPaymentMethodRepository.saveAndFlush(cash);
	    }

	    private String ownerToken(ProvisionarTenantResponse prov, TenantUserRole role) {
	        var owner = userRepository.findById(prov.getOwnerUserId()).orElseThrow();
	        var tenant = tenantRepository.findById(prov.getTenantId()).orElseThrow();
	        return jwtTokenProvider.generateTenantScopedToken(
	                owner,
	                tenant,
	                role,
	                com.restaurante.model.enums.TenantUserEstado.ATIVO,
	                1,
	                null
	        );
	    }

    private DispositivoOperacional criarDevicePos(ProvisionarTenantResponse prov) {
        Tenant tenant = tenantRepository.findById(prov.getTenantId()).orElseThrow();
        Instituicao inst = instituicaoRepository.findById(prov.getInstituicaoId()).orElseThrow();
        UnidadeAtendimento ua = unidadeAtendimentoRepository.findById(prov.getUnidadeAtendimentoId()).orElseThrow();
        DispositivoOperacional d = new DispositivoOperacional();
        d.setTenant(tenant);
        d.setInstituicao(inst);
        d.setUnidadeAtendimento(ua);
        d.setCodigo("POS-PM-" + System.nanoTime());
        d.setNome("POS PM");
        d.setTipo(DispositivoTipo.POS);
        d.setStatus(DispositivoStatus.ATIVO);
        d.setTokenVersion(1);
        return dispositivoOperacionalRepository.save(d);
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
                        .opcoes(ProvisionarTenantRequest.OpcoesProvisionamento.builder()
                                .criarMesas(true)
                                .quantidadeMesas(1)
                                .criarQrPorMesa(true)
                                .build())
                        .build()
        );
    }
}

package com.restaurante.consumo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.dto.request.AbrirTurnoRequest;
import com.restaurante.dto.request.AbrirCaixaOperadorRequest;
import com.restaurante.dto.request.ChecklistItemRespostaRequest;
import com.restaurante.dto.request.ConfirmarOrdemManualRequest;
import com.restaurante.dto.request.CriarCarregamentoFundoRequest;
import com.restaurante.dto.request.ProvisionarTenantRequest;
import com.restaurante.dto.response.ProvisionarTenantResponse;
import com.restaurante.financeiro.FinanceiroItFixtureSupport;
import com.restaurante.model.entity.DispositivoOperacional;
import com.restaurante.model.enums.DeviceCapability;
import com.restaurante.model.enums.DispositivoStatus;
import com.restaurante.model.enums.DispositivoTipo;
import com.restaurante.model.enums.MetodoPagamentoManual;
import com.restaurante.model.enums.Role;
import com.restaurante.model.enums.TenantUserRole;
import com.restaurante.model.enums.TurnoOperacionalTipo;
import com.restaurante.repository.DispositivoOperacionalRepository;
import com.restaurante.repository.TenantRepository;
import com.restaurante.repository.InstituicaoRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "spring.main.web-application-type=servlet"
)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("it-postgres")
class Prompt36ManualCashTpaIT extends PostgresTestcontainersConfig {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired TenantProvisioningService provisioningService;
    @Autowired TenantRepository tenantRepository;
    @Autowired InstituicaoRepository instituicaoRepository;
    @Autowired UnidadeAtendimentoRepository unidadeAtendimentoRepository;
    @Autowired DispositivoOperacionalRepository dispositivoOperacionalRepository;
    @Autowired FinanceiroItFixtureSupport fixtureSupport;

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    @WithMockUser(username = "owner")
    void public_canCreateConsumoAnonimo_andCreateOrdemCarregamento_cash_andDeviceConfirms_idempotent() throws Exception {
        ProvisionarTenantResponse prov = provisionTenant("p36-cash", "P36C");
        String mesaQrToken = fixtureSupport.ensureMesaQrToken(prov);

        // cria consumo anónimo
        String consumoJson = mockMvc.perform(post("/public/q/" + mesaQrToken + "/consumos/anonimo"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode consumoNode = objectMapper.readTree(consumoJson).get("data");
        String codigoConsumo = consumoNode.get("qrCodeSessao").asText();
        assertThat(codigoConsumo).isNotBlank();

        // abrir turno antes do carregamento manual
        TenantContextHolder.set(new TenantContext(
                prov.getTenantId(), prov.getTenantCode(), prov.getOwnerUserId(),
                Set.of(Role.ROLE_GERENTE.name(), TenantUserRole.TENANT_OWNER.name()),
                TenantResolutionSource.JWT, false, false
        ));
        mockMvc.perform(post("/tenant/operacao/turnos/abrir")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(abrirTurnoReq(prov))))
                .andExpect(status().isCreated());

        // cria ordem de carregamento cash
        CriarCarregamentoFundoRequest criar = new CriarCarregamentoFundoRequest();
        criar.setValor(new BigDecimal("10000.00"));
        criar.setMetodoPagamento(MetodoPagamentoManual.CASH);
        String ordemJson = mockMvc.perform(post("/public/q/" + mesaQrToken + "/consumos/" + codigoConsumo + "/carregamentos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(criar)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode ordemNode = objectMapper.readTree(ordemJson).get("data");
        Long ordemId = ordemNode.get("ordemPagamentoId").asLong();
        String ordemToken = ordemNode.get("ordemToken").asText();
        assertThat(ordemId).isPositive();
        assertThat(ordemToken).startsWith("OP-");

        // status público pendente
        String statusPendJson = mockMvc.perform(get("/public/ordens-pagamento/" + ordemToken + "/status"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(statusPendJson).get("data").get("status").asText())
                .isEqualTo("AGUARDANDO_CONFIRMACAO");

        // device pos com capabilities
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
                List.of(
                        DeviceCapability.VIEW_PAYMENT_ORDER,
                        DeviceCapability.CONFIRM_CASH_PAYMENT,
                        DeviceCapability.OPEN_OPERATOR_CASH_SESSION,
                        DeviceCapability.REPRINT_CONSUMPTION_DOCUMENTS
                ),
                1
        );
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                device, "N/A", List.of(new SimpleGrantedAuthority("ROLE_DEVICE"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);

        AbrirCaixaOperadorRequest abrirCaixa = new AbrirCaixaOperadorRequest();
        abrirCaixa.setOperadorUserId(prov.getOwnerUserId());
        mockMvc.perform(post("/device/caixa-operador/open")
                        .with(authentication(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(abrirCaixa)))
                .andExpect(status().isOk());

        // escanear
        String scanJson = mockMvc.perform(get("/device/ordens-pagamento/" + ordemToken).with(authentication(auth)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(scanJson).get("data").get("podeConfirmar").asBoolean()).isTrue();

        // confirmar cash
        ConfirmarOrdemManualRequest conf = new ConfirmarOrdemManualRequest();
        conf.setClientRequestId("pos-conf-1");
        conf.setMetodoConfirmado(MetodoPagamentoManual.CASH);
        conf.setValorRecebido(new BigDecimal("10000.00"));
        conf.setObservacao("Recebido em numerário");

        String confJson1 = mockMvc.perform(post("/device/ordens-pagamento/" + ordemId + "/confirmar-manual")
                        .with(authentication(auth))
                        .header("Idempotency-Key", "idem-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(conf)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode confNode1 = objectMapper.readTree(confJson1).get("data");
        assertThat(confNode1.get("status").asText()).isEqualTo("CONFIRMADA");
        assertThat(confNode1.get("saldoAtual").asText()).isNotBlank();

        // replay idempotente não duplica (mesma key + mesmo body)
        String confJson2 = mockMvc.perform(post("/device/ordens-pagamento/" + ordemId + "/confirmar-manual")
                        .with(authentication(auth))
                        .header("Idempotency-Key", "idem-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(conf)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode confNode2 = objectMapper.readTree(confJson2).get("data");
        assertThat(confNode2.get("idempotentReplay").asBoolean()).isTrue();

        // status público confirmado
        String statusConfJson = mockMvc.perform(get("/public/ordens-pagamento/" + ordemToken + "/status"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode statusNode = objectMapper.readTree(statusConfJson).get("data");
        assertThat(statusNode.get("status").asText()).isEqualTo("CONFIRMADA");
        assertThat(statusNode.get("saldoAtual").decimalValue()).isEqualByComparingTo(new BigDecimal("10000.00"));
    }

    @Test
    void public_createCarregamento_withoutOpenTurno_returns400() throws Exception {
        ProvisionarTenantResponse prov = provisionTenant("p36-no-turno", "P36N");
        String mesaQrToken = fixtureSupport.ensureMesaQrToken(prov);

        String consumoJson = mockMvc.perform(post("/public/q/" + mesaQrToken + "/consumos/anonimo"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String codigoConsumo = objectMapper.readTree(consumoJson).get("data").get("qrCodeSessao").asText();

        CriarCarregamentoFundoRequest criar = new CriarCarregamentoFundoRequest();
        criar.setValor(new BigDecimal("5000.00"));
        criar.setMetodoPagamento(MetodoPagamentoManual.CASH);
        mockMvc.perform(post("/public/q/" + mesaQrToken + "/consumos/" + codigoConsumo + "/carregamentos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(criar)))
                .andExpect(status().isBadRequest());
    }

    private ProvisionarTenantResponse provisionTenant(String slug, String code) {
        TenantContextHolder.set(new TenantContext(
                null, null, 1L, Set.of(Role.ROLE_ADMIN.name()),
                TenantResolutionSource.JWT, true, false
        ));
        String tenantSlug = UniqueTestData.uniqueSlug(slug);
        String tenantCode = UniqueTestData.uniqueTenantCode(code);
        String instituicaoSigla = UniqueTestData.uniqueInstituicaoSigla(code);
        String ownerEmail = UniqueTestData.uniqueEmail(slug + "-owner");
        String phone = UniqueTestData.uniqueTelefone();
        return provisioningService.provisionar(
                ProvisionarTenantRequest.builder()
                        .tenant(ProvisionarTenantRequest.TenantInfo.builder()
                                .nome("Tenant " + slug)
                                .slug(tenantSlug)
                                .tenantCode(tenantCode)
                                .tipo(com.restaurante.model.enums.TenantTipo.VENDEDOR_RUA)
                                .build())
                        .planoCodigo("PILOTO")
                        .templateCodigo("VENDEDOR_RUA")
                        .instituicao(ProvisionarTenantRequest.InstituicaoInfo.builder()
                                .nome("Inst " + slug)
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

    private AbrirTurnoRequest abrirTurnoReq(ProvisionarTenantResponse prov) {
        AbrirTurnoRequest req = new AbrirTurnoRequest();
        req.setInstituicaoId(prov.getInstituicaoId());
        req.setUnidadeAtendimentoId(prov.getUnidadeAtendimentoId());
        req.setTipo(TurnoOperacionalTipo.DIARIO);
        req.setNome("Turno");
        req.setChecklist(List.of(
                item("DEVICE_ONLINE", true),
                item("QR_VISIVEL", true),
                item("CATALOGO_ATUALIZADO", true),
                item("UNIDADE_PRODUCAO_ATIVA", true),
                item("INTERNET_OK", true),
                item("OPERADOR_CONFIRMOU", true)
        ));
        return req;
    }

    private ChecklistItemRespostaRequest item(String codigo, boolean v) {
        ChecklistItemRespostaRequest i = new ChecklistItemRespostaRequest();
        i.setCodigo(codigo);
        i.setValorBoolean(v);
        return i;
    }

    private DispositivoOperacional criarDevicePos(ProvisionarTenantResponse prov) {
        var tenant = tenantRepository.findById(prov.getTenantId()).orElseThrow();
        var inst = instituicaoRepository.findById(prov.getInstituicaoId()).orElseThrow();
        var ua = unidadeAtendimentoRepository.findById(prov.getUnidadeAtendimentoId()).orElseThrow();
        DispositivoOperacional d = new DispositivoOperacional();
        d.setTenant(tenant);
        d.setInstituicao(inst);
        d.setUnidadeAtendimento(ua);
        d.setCodigo("POS-" + (System.nanoTime() % 100000));
        d.setNome("POS");
        d.setTipo(DispositivoTipo.POS);
        d.setStatus(DispositivoStatus.ATIVO);
        d.setTokenVersion(1);
        return dispositivoOperacionalRepository.saveAndFlush(d);
    }

}

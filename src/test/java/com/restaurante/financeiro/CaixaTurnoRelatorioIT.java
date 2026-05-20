package com.restaurante.financeiro;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.dto.request.AbrirTurnoRequest;
import com.restaurante.dto.request.ChecklistItemRespostaRequest;
import com.restaurante.dto.request.ConfirmarOrdemManualRequest;
import com.restaurante.dto.request.CriarCarregamentoFundoRequest;
import com.restaurante.dto.request.ProvisionarTenantRequest;
import com.restaurante.dto.response.ProvisionarTenantResponse;
import com.restaurante.model.entity.DispositivoOperacional;
import com.restaurante.model.enums.DeviceCapability;
import com.restaurante.model.enums.DispositivoStatus;
import com.restaurante.model.enums.DispositivoTipo;
import com.restaurante.model.enums.MetodoPagamentoManual;
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
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("it-postgres")
class CaixaTurnoRelatorioIT extends PostgresTestcontainersConfig {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired TenantProvisioningService provisioningService;
    @Autowired TenantRepository tenantRepository;
    @Autowired InstituicaoRepository instituicaoRepository;
    @Autowired UnidadeAtendimentoRepository unidadeAtendimentoRepository;
    @Autowired DispositivoOperacionalRepository dispositivoOperacionalRepository;

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    @Test
    void finance_canReadRelatorioCaixa_andCashIsCounted() throws Exception {
        ProvisionarTenantResponse prov = provisionTenant("p37-caixa", "P37C");
        String mesaQrToken = prov.getMesas().get(0).getQrToken();

        // Abrir turno como OWNER
        TenantContextHolder.set(new TenantContext(
                prov.getTenantId(), prov.getTenantCode(), prov.getOwnerUserId(),
                Set.of(Role.ROLE_GERENTE.name(), TenantUserRole.TENANT_OWNER.name()),
                TenantResolutionSource.JWT, false, false
        ));
        String abrirJson = mockMvc.perform(post("/tenant/operacao/turnos/abrir")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(abrirTurnoReq(prov))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long turnoId = objectMapper.readTree(abrirJson).get("data").get("id").asLong();

        // cria consumo e ordem cash (público)
        String consumoJson = mockMvc.perform(post("/public/q/" + mesaQrToken + "/consumos/anonimo"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String codigoConsumo = objectMapper.readTree(consumoJson).get("data").get("qrCodeSessao").asText();

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

        // device confirma
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
                List.of(DeviceCapability.VIEW_PAYMENT_ORDER, DeviceCapability.CONFIRM_CASH_PAYMENT),
                1
        );
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                device, "N/A", List.of(new SimpleGrantedAuthority("ROLE_DEVICE"))
        );
        // scan ok
        mockMvc.perform(get("/device/ordens-pagamento/" + ordemToken).with(authentication(auth)))
                .andExpect(status().isOk());

        ConfirmarOrdemManualRequest conf = new ConfirmarOrdemManualRequest();
        conf.setClientRequestId("pos-conf-p37");
        conf.setMetodoConfirmado(MetodoPagamentoManual.CASH);
        conf.setValorRecebido(new BigDecimal("10000.00"));
        mockMvc.perform(post("/device/ordens-pagamento/" + ordemId + "/confirmar-manual")
                        .with(authentication(auth))
                        .header("Idempotency-Key", "idem-p37")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(conf)))
                .andExpect(status().isOk());

        // FINANCE lê relatório
        TenantContextHolder.set(new TenantContext(
                prov.getTenantId(), prov.getTenantCode(), prov.getOwnerUserId(),
                Set.of(Role.ROLE_GERENTE.name(), TenantUserRole.TENANT_FINANCE.name()),
                TenantResolutionSource.JWT, false, false
        ));
        String relJson = mockMvc.perform(get("/tenant/financeiro/turnos/" + turnoId + "/relatorio-caixa"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode rel = objectMapper.readTree(relJson).get("data");
        assertThat(rel.get("totalManualConfirmado").decimalValue()).isEqualByComparingTo(new BigDecimal("10000.00"));
    }

    @Test
    void kitchen_cannotReadRelatorioCaixa() throws Exception {
        ProvisionarTenantResponse prov = provisionTenant("p37-kitchen", "P37K");

        TenantContextHolder.set(new TenantContext(
                prov.getTenantId(), prov.getTenantCode(), prov.getOwnerUserId(),
                Set.of(Role.ROLE_GERENTE.name(), TenantUserRole.TENANT_KITCHEN.name()),
                TenantResolutionSource.JWT, false, false
        ));

        mockMvc.perform(get("/tenant/financeiro/turnos/999/relatorio-caixa"))
                .andExpect(status().is4xxClientError());
    }

    private ProvisionarTenantResponse provisionTenant(String slug, String tenantCode) {
        TenantContextHolder.set(new TenantContext(
                null, null, 1L, Set.of(Role.ROLE_ADMIN.name()),
                TenantResolutionSource.JWT, true, false
        ));
        String phone = "+244900" + Math.abs(slug.hashCode() % 1_000_000);
        return provisioningService.provisionar(
                ProvisionarTenantRequest.builder()
                        .tenant(ProvisionarTenantRequest.TenantInfo.builder()
                                .nome("Tenant " + slug)
                                .slug(slug)
                                .tenantCode(tenantCode)
                                .tipo(TenantTipo.VENDEDOR_RUA)
                                .build())
                        .planoCodigo("PILOTO")
                        .templateCodigo("VENDEDOR_RUA")
                        .instituicao(ProvisionarTenantRequest.InstituicaoInfo.builder()
                                .nome("Inst " + slug)
                                .sigla(tenantCode)
                                .build())
                        .responsavel(ProvisionarTenantRequest.ResponsavelInfo.builder()
                                .email(slug + "@owner.com")
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
        d.setNome("POS 1");
        d.setCodigo("POS-" + (System.nanoTime() % 100000));
        d.setTipo(DispositivoTipo.POS);
        d.setStatus(DispositivoStatus.ATIVO);
        return dispositivoOperacionalRepository.saveAndFlush(d);
    }
}


package com.restaurante.financeiro;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.dto.request.AbrirTurnoRequest;
import com.restaurante.dto.request.ChecklistItemRespostaRequest;
import com.restaurante.dto.request.FecharTurnoRequest;
import com.restaurante.dto.request.ProvisionarTenantRequest;
import com.restaurante.dto.response.ProvisionarTenantResponse;
import com.restaurante.financeiro.snapshot.evidence.repository.TurnoEvidenceBundleAccessLogRepository;
import com.restaurante.financeiro.snapshot.evidence.repository.TurnoEvidenceBundleRepository;
import com.restaurante.model.entity.Plano;
import com.restaurante.model.entity.ProvisioningTemplate;
import com.restaurante.model.enums.Role;
import com.restaurante.model.enums.TenantTipo;
import com.restaurante.model.enums.TenantUserRole;
import com.restaurante.model.enums.TurnoOperacionalTipo;
import com.restaurante.repository.PlanoRepository;
import com.restaurante.repository.ProvisioningTemplateRepository;
import com.restaurante.security.tenant.TenantContext;
import com.restaurante.security.tenant.TenantContextHolder;
import com.restaurante.security.tenant.TenantResolutionSource;
import com.restaurante.service.TenantProvisioningService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Set;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = "spring.main.web-application-type=servlet")
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class EvidenceBundlePersistenceTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired TenantProvisioningService provisioningService;
    @Autowired TurnoEvidenceBundleRepository bundleRepository;
    @Autowired TurnoEvidenceBundleAccessLogRepository accessLogRepository;
    @Autowired PlanoRepository planoRepository;
    @Autowired ProvisioningTemplateRepository provisioningTemplateRepository;

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    @Test
    @WithMockUser(username = "finance")
    void finance_persiste_bundle_sequence_1_e_registra_access_log() throws Exception {
        ProvisionarTenantResponse prov = provisionTenant("ev-bundle-a", "EBA");
        TenantContextHolder.set(new TenantContext(
                prov.getTenantId(), prov.getTenantCode(), prov.getOwnerUserId(),
                Set.of(Role.ROLE_GERENTE.name(), TenantUserRole.TENANT_ADMIN.name()),
                TenantResolutionSource.JWT, false, false
        ));

        long turnoId = abrirTurno(prov);
        fecharTurno(turnoId);

        String resp = mockMvc.perform(post("/tenant/operacao/turnos/{turnoId}/snapshot/evidence-bundles", turnoId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(resp);
        assertThat(json.at("/data/sequenceNumber").asInt()).isEqualTo(1);
        assertThat(json.at("/data/retencao/wormLocked").asBoolean()).isTrue();
        assertThat(json.at("/data/integridade/bundleHash").asText()).isNotBlank();
        assertThat(json.at("/data/cadeiaCustodia/chainHash").asText()).isNotBlank();

        assertThat(bundleRepository.findByTenantIdAndTurnoIdOrderBySequenceNumberDesc(prov.getTenantId(), turnoId, org.springframework.data.domain.PageRequest.of(0, 10)))
                .hasSize(1);
        assertThat(accessLogRepository.count()).isGreaterThan(0);
    }

    @Test
    @WithMockUser(username = "finance")
    void dois_bundles_do_mesmo_turno_criam_cadeia_com_sequence_incremental() throws Exception {
        ProvisionarTenantResponse prov = provisionTenant("ev-bundle-b", "EBB");
        TenantContextHolder.set(new TenantContext(
                prov.getTenantId(), prov.getTenantCode(), prov.getOwnerUserId(),
                Set.of(Role.ROLE_GERENTE.name(), TenantUserRole.TENANT_ADMIN.name()),
                TenantResolutionSource.JWT, false, false
        ));

        long turnoId = abrirTurno(prov);
        fecharTurno(turnoId);

        String r1 = mockMvc.perform(post("/tenant/operacao/turnos/{turnoId}/snapshot/evidence-bundles", turnoId))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long bundle1Id = objectMapper.readTree(r1).at("/data/bundleId").asLong();

        String r2 = mockMvc.perform(post("/tenant/operacao/turnos/{turnoId}/snapshot/evidence-bundles", turnoId))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode j2 = objectMapper.readTree(r2);
        assertThat(j2.at("/data/sequenceNumber").asInt()).isEqualTo(2);

        // valida link (via banco)
        var page = bundleRepository.findByTenantIdAndTurnoIdOrderBySequenceNumberDesc(prov.getTenantId(), turnoId, org.springframework.data.domain.PageRequest.of(0, 10));
        assertThat(page).hasSize(2);
        var b2 = page.getContent().get(0);
        assertThat(b2.getSequenceNumber()).isEqualTo(2);
        assertThat(b2.getPreviousBundle()).isNotNull();
        assertThat(b2.getPreviousBundle().getId()).isEqualTo(bundle1Id);
        assertThat(b2.getPreviousBundleHash()).isNotBlank();
        assertThat(b2.getChainHash()).isNotBlank();
        assertThat(b2.getChainSignature()).isNotBlank();
    }

    private long abrirTurno(ProvisionarTenantResponse prov) throws Exception {
        AbrirTurnoRequest req = new AbrirTurnoRequest();
        req.setInstituicaoId(prov.getInstituicaoId());
        req.setUnidadeAtendimentoId(prov.getUnidadeAtendimentoId());
        req.setTipo(TurnoOperacionalTipo.DIARIO);
        req.setNome("Turno bundle");
        req.setObservacao("Abertura");
        req.setChecklist(List.of(
                boolItem("DEVICE_ONLINE", true),
                boolItem("QR_VISIVEL", true),
                boolItem("CATALOGO_ATUALIZADO", true),
                boolItem("UNIDADE_PRODUCAO_ATIVA", true),
                boolItem("OPERADOR_CONFIRMOU", true)
        ));
        String openResp = mockMvc.perform(post("/tenant/operacao/turnos/abrir")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(openResp).at("/data/id").asLong();
    }

    private void fecharTurno(long turnoId) throws Exception {
        FecharTurnoRequest req = new FecharTurnoRequest();
        req.setForcarFecho(false);
        req.setChecklist(List.of(
                boolItem("PEDIDOS_PENDENTES_VERIFICADOS", true),
                boolItem("PAGAMENTOS_PENDENTES_VERIFICADOS", true),
                boolItem("SUBPEDIDOS_EM_ABERTO_VERIFICADOS", true)
        ));
        mockMvc.perform(post("/tenant/operacao/turnos/" + turnoId + "/fechar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    private ChecklistItemRespostaRequest boolItem(String codigo, boolean v) {
        ChecklistItemRespostaRequest it = new ChecklistItemRespostaRequest();
        it.setCodigo(codigo);
        it.setValorBoolean(v);
        return it;
    }

    private ProvisionarTenantResponse provisionTenant(String slug, String tenantCode) {
        ensurePlanoAndTemplate();
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

    private void ensurePlanoAndTemplate() {
        if (planoRepository.findByCodigo("PILOTO").isEmpty()) {
            Plano p = new Plano();
            p.setCodigo("PILOTO");
            p.setNome("Piloto");
            p.setDescricao("Plano de piloto para testes");
            p.setPrecoMensal(BigDecimal.ZERO);
            p.setMaxInstituicoes(10);
            p.setMaxUnidadesAtendimento(50);
            p.setMaxProdutos(5000);
            p.setMaxUsuarios(200);
            p.setMaxQrCodes(1000);
            p.setMaxDispositivos(200);
            p.setPermiteMultiInstituicao(true);
            p.setPermitePedidosQr(true);
            p.setPermitePos(true);
            p.setPermiteOffline(false);
            p.setAtivo(true);
            planoRepository.save(p);
        }

        if (provisioningTemplateRepository.findByCodigoAndAtivoTrue("VENDEDOR_RUA").isEmpty()) {
            ProvisioningTemplate t = new ProvisioningTemplate();
            t.setCodigo("VENDEDOR_RUA");
            t.setNome("Vendedor Rua");
            t.setDescricao("Template mínimo para testes");
            t.setTipoTenant(TenantTipo.VENDEDOR_RUA);
            t.setAtivo(true);
            t.setConfiguracaoJson("{\"templates\":{}}");
            provisioningTemplateRepository.save(t);
        }
    }
}

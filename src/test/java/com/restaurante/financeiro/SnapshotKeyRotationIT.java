package com.restaurante.financeiro;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.restaurante.dto.request.AbrirTurnoRequest;
import com.restaurante.dto.request.ChecklistItemRespostaRequest;
import com.restaurante.dto.request.FecharTurnoRequest;
import com.restaurante.dto.request.ProvisionarTenantRequest;
import com.restaurante.dto.response.ProvisionarTenantResponse;
import com.restaurante.model.entity.OperationalEventLog;
import com.restaurante.model.entity.TurnoOperacional;
import com.restaurante.model.enums.OperationalEventType;
import com.restaurante.model.enums.Role;
import com.restaurante.model.enums.TenantTipo;
import com.restaurante.model.enums.TenantUserRole;
import com.restaurante.model.enums.TurnoOperacionalTipo;
import com.restaurante.repository.OperationalEventLogRepository;
import com.restaurante.repository.TurnoOperacionalRepository;
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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.main.web-application-type=servlet",
                "consuma.financeiro.snapshot-integridade.active-key-id=platform-snapshot-key-v2",
                "consuma.financeiro.snapshot-integridade.keys.platform-snapshot-key-v1.status=DEPRECATED",
                "consuma.financeiro.snapshot-integridade.keys.platform-snapshot-key-v1.secret=TEST_SECRET_SNAPSHOT_HMAC_V1_32CHARS_MIN_123456",
                "consuma.financeiro.snapshot-integridade.keys.platform-snapshot-key-v2.status=ACTIVE",
                "consuma.financeiro.snapshot-integridade.keys.platform-snapshot-key-v2.secret=TEST_SECRET_SNAPSHOT_HMAC_V2_32CHARS_MIN_abcdef"
        }
)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("it-postgres")
class SnapshotKeyRotationIT extends PostgresTestcontainersConfig {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired TenantProvisioningService provisioningService;
    @Autowired TurnoOperacionalRepository turnoOperacionalRepository;
    @Autowired OperationalEventLogRepository operationalEventLogRepository;

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    @Test
    @WithMockUser(username = "finance")
    void fecho_assina_com_activeKeyId_v2() throws Exception {
        ProvisionarTenantResponse prov = provisionTenant("rot-key-a", "RKA");
        TenantContextHolder.set(new TenantContext(
                prov.getTenantId(), prov.getTenantCode(), prov.getOwnerUserId(),
                Set.of(Role.ROLE_GERENTE.name(), TenantUserRole.TENANT_FINANCE.name()),
                TenantResolutionSource.JWT, false, false
        ));

        long turnoId = abrirTurno(prov);
        fecharTurno(turnoId);

        TurnoOperacional turno = turnoOperacionalRepository.findByIdAndTenantId(turnoId, prov.getTenantId()).orElseThrow();
        ObjectNode root = (ObjectNode) objectMapper.readTree(turno.getResumoJson());
        assertThat(root.at("/financeiro/integridade/signatureKeyId").asText()).isEqualTo("platform-snapshot-key-v2");
    }

    @Test
    @WithMockUser(username = "finance")
    void keyId_desconhecido_retorna_failureReason_e_registra_evento() throws Exception {
        ProvisionarTenantResponse prov = provisionTenant("rot-key-b", "RKB");
        TenantContextHolder.set(new TenantContext(
                prov.getTenantId(), prov.getTenantCode(), prov.getOwnerUserId(),
                Set.of(Role.ROLE_GERENTE.name(), TenantUserRole.TENANT_FINANCE.name()),
                TenantResolutionSource.JWT, false, false
        ));

        long turnoId = abrirTurno(prov);
        fecharTurno(turnoId);

        // adulterar keyId para desconhecido, mantendo hash/signature para forçar falha por KEY_NOT_FOUND
        TurnoOperacional turno = turnoOperacionalRepository.findByIdAndTenantId(turnoId, prov.getTenantId()).orElseThrow();
        ObjectNode root = (ObjectNode) objectMapper.readTree(turno.getResumoJson());
        ObjectNode integ = (ObjectNode) root.at("/financeiro/integridade");
        integ.put("signatureKeyId", "unknown-key");
        ((ObjectNode) root.get("financeiro")).set("integridade", integ);
        turno.setResumoJson(objectMapper.writeValueAsString(root));
        turnoOperacionalRepository.saveAndFlush(turno);

        String resp = mockMvc.perform(get("/tenant/financeiro/turnos/{id}/snapshot/export", turnoId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(resp);
        assertThat(json.at("/data/verificacao/assinaturaValida").asBoolean()).isFalse();
        assertThat(json.at("/data/verificacao/assinaturaFailureReason").asText()).isEqualTo("KEY_NOT_FOUND");

        List<OperationalEventLog> events = operationalEventLogRepository.findByTenantIdAndEventType(
                prov.getTenantId(),
                OperationalEventType.SNAPSHOT_FINANCEIRO_KEY_ID_DESCONHECIDO
        );
        assertThat(events).isNotEmpty();
    }

    @Test
    @WithMockUser(username = "finance")
    void snapshot_assinado_com_v2_nao_valida_se_keyId_alterado_para_v1_deprecated() throws Exception {
        // Aqui o objetivo é apenas demonstrar que o export usa o keyId persistido; como a assinatura foi gerada com v2,
        // trocar keyId para v1 (DEPRECATED) deve resultar em assinatura inválida.
        ProvisionarTenantResponse prov = provisionTenant("rot-key-c", "RKC");
        TenantContextHolder.set(new TenantContext(
                prov.getTenantId(), prov.getTenantCode(), prov.getOwnerUserId(),
                Set.of(Role.ROLE_GERENTE.name(), TenantUserRole.TENANT_FINANCE.name()),
                TenantResolutionSource.JWT, false, false
        ));

        long turnoId = abrirTurno(prov);
        fecharTurno(turnoId);

        TurnoOperacional turno = turnoOperacionalRepository.findByIdAndTenantId(turnoId, prov.getTenantId()).orElseThrow();
        ObjectNode root = (ObjectNode) objectMapper.readTree(turno.getResumoJson());
        ObjectNode integ = (ObjectNode) root.at("/financeiro/integridade");
        integ.put("signatureKeyId", "platform-snapshot-key-v1"); // v1 é DEPRECATED (verifica, mas não corresponde à assinatura v2)
        ((ObjectNode) root.get("financeiro")).set("integridade", integ);
        turno.setResumoJson(objectMapper.writeValueAsString(root));
        turnoOperacionalRepository.saveAndFlush(turno);

        String resp = mockMvc.perform(get("/tenant/financeiro/turnos/{id}/snapshot/export", turnoId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(resp);
        assertThat(json.at("/data/verificacao/assinaturaFailureReason").asText()).isEqualTo("SIGNATURE_MISMATCH");
    }

    private long abrirTurno(ProvisionarTenantResponse prov) throws Exception {
        AbrirTurnoRequest req = new AbrirTurnoRequest();
        req.setInstituicaoId(prov.getInstituicaoId());
        req.setUnidadeAtendimentoId(prov.getUnidadeAtendimentoId());
        req.setTipo(TurnoOperacionalTipo.DIARIO);
        req.setNome("Turno rot");
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
}

package com.restaurante.financeiro;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.dto.request.AbrirTurnoRequest;
import com.restaurante.dto.request.ChecklistItemRespostaRequest;
import com.restaurante.dto.request.FecharTurnoRequest;
import com.restaurante.dto.request.ProvisionarTenantRequest;
import com.restaurante.dto.response.ProvisionarTenantResponse;
import com.restaurante.model.enums.Role;
import com.restaurante.model.enums.TenantTipo;
import com.restaurante.model.enums.TenantUserRole;
import com.restaurante.model.enums.TurnoOperacionalTipo;
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
                "consuma.financeiro.snapshot-integridade.signature-enabled=false"
        }
)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("it-postgres")
class SnapshotFinanceiroExportSignatureDisabledIT extends PostgresTestcontainersConfig {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired TenantProvisioningService provisioningService;

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    @Test
    @WithMockUser(username = "finance")
    void export_funciona_sem_assinatura_quando_signature_disabled() throws Exception {
        ProvisionarTenantResponse prov = provisionTenant("snap-exp-sigoff", "SNO");
        TenantContextHolder.set(ownerContext(prov));
        long turnoId = abrirTurno(prov);
        fecharTurno(turnoId);
        TenantContextHolder.set(financeContext(prov));

        String resp = mockMvc.perform(get("/tenant/financeiro/turnos/{id}/snapshot/export", turnoId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(resp);
        assertThat(json.at("/data/verificacao/hashValido").asBoolean()).isTrue();
        assertThat(json.at("/data/verificacao/valido").asBoolean()).isTrue();
        // assinatura pode vir null/false, mas não deve bloquear export
        assertThat(json.at("/data/verificacao/assinaturaValida").isMissingNode()
                || json.at("/data/verificacao/assinaturaValida").isNull()
                || json.at("/data/verificacao/assinaturaValida").asBoolean(false) == false).isTrue();
    }

    private long abrirTurno(ProvisionarTenantResponse prov) throws Exception {
        AbrirTurnoRequest req = new AbrirTurnoRequest();
        req.setInstituicaoId(prov.getInstituicaoId());
        req.setUnidadeAtendimentoId(prov.getUnidadeAtendimentoId());
        req.setTipo(TurnoOperacionalTipo.DIARIO);
        req.setNome("Turno sigoff");
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

    private TenantContext ownerContext(ProvisionarTenantResponse prov) {
        return new TenantContext(
                prov.getTenantId(), prov.getTenantCode(), prov.getOwnerUserId(),
                Set.of(Role.ROLE_GERENTE.name(), TenantUserRole.TENANT_OWNER.name()),
                TenantResolutionSource.JWT, false, false
        );
    }

    private TenantContext financeContext(ProvisionarTenantResponse prov) {
        return new TenantContext(
                prov.getTenantId(), prov.getTenantCode(), prov.getOwnerUserId(),
                Set.of(Role.ROLE_GERENTE.name(), TenantUserRole.TENANT_FINANCE.name()),
                TenantResolutionSource.JWT, false, false
        );
    }
}

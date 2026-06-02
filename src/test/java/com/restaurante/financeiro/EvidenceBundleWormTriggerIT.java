package com.restaurante.financeiro;

import com.restaurante.dto.request.AbrirTurnoRequest;
import com.restaurante.dto.request.ChecklistItemRespostaRequest;
import com.restaurante.dto.request.ProvisionarTenantRequest;
import com.restaurante.dto.response.ProvisionarTenantResponse;
import com.restaurante.financeiro.snapshot.evidence.entity.TurnoEvidenceBundle;
import com.restaurante.financeiro.snapshot.evidence.repository.TurnoEvidenceBundleRepository;
import com.restaurante.model.entity.Instituicao;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.TurnoOperacional;
import com.restaurante.model.entity.UnidadeAtendimento;
import com.restaurante.model.enums.EvidenceBundleStatus;
import com.restaurante.model.enums.EvidenceBundleType;
import com.restaurante.model.enums.Role;
import com.restaurante.model.enums.TenantTipo;
import com.restaurante.model.enums.TenantUserRole;
import com.restaurante.model.enums.TurnoOperacionalTipo;
import com.restaurante.repository.InstituicaoRepository;
import com.restaurante.repository.TenantRepository;
import com.restaurante.repository.TurnoOperacionalRepository;
import com.restaurante.repository.UnidadeAtendimentoRepository;
import com.restaurante.security.tenant.TenantContext;
import com.restaurante.security.tenant.TenantContextHolder;
import com.restaurante.security.tenant.TenantResolutionSource;
import com.restaurante.service.TenantProvisioningService;
import com.restaurante.testsupport.PostgresTestcontainersConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "spring.main.web-application-type=servlet"
)
@AutoConfigureMockMvc(addFilters = false)
@org.springframework.security.test.context.support.WithMockUser(username = "tenant-user")
@ActiveProfiles("it-postgres")
class EvidenceBundleWormTriggerIT extends PostgresTestcontainersConfig {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired ObjectMapper objectMapper;
    @Autowired TenantProvisioningService provisioningService;
    @Autowired TenantRepository tenantRepository;
    @Autowired InstituicaoRepository instituicaoRepository;
    @Autowired UnidadeAtendimentoRepository unidadeAtendimentoRepository;
    @Autowired TurnoOperacionalRepository turnoOperacionalRepository;
    @Autowired TurnoEvidenceBundleRepository bundleRepository;

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    @Test
    void worm_trigger_blocks_update_delete_and_allows_status_transition_to_retention_expired() throws Exception {
        ProvisionarTenantResponse prov = provisionTenant("worm-trg-a", "WTA");
        TenantContextHolder.set(new TenantContext(
                prov.getTenantId(), prov.getTenantCode(), prov.getOwnerUserId(),
                Set.of(Role.ROLE_GERENTE.name(), TenantUserRole.TENANT_OWNER.name()),
                TenantResolutionSource.JWT, false, false
        ));

        long turnoId = abrirTurno(prov);
        Tenant tenant = tenantRepository.findById(prov.getTenantId()).orElseThrow();
        Instituicao inst = instituicaoRepository.findById(prov.getInstituicaoId()).orElseThrow();
        UnidadeAtendimento ua = unidadeAtendimentoRepository.findById(prov.getUnidadeAtendimentoId()).orElseThrow();
        TurnoOperacional turno = turnoOperacionalRepository.findByIdAndTenantId(turnoId, prov.getTenantId()).orElseThrow();

        TurnoEvidenceBundle b = new TurnoEvidenceBundle();
        b.setTenant(tenant);
        b.setTurno(turno);
        b.setInstituicao(inst);
        b.setUnidadeAtendimento(ua);
        b.setBundleVersion("v1");
        b.setBundleType(EvidenceBundleType.FINANCEIRO_TURNO_SNAPSHOT_EVIDENCE);
        b.setStatus(EvidenceBundleStatus.ACTIVE);
        b.setSequenceNumber(1);
        b.setGeneratedAt(LocalDateTime.now());
        b.setGeneratedByActorType("USER");
        b.setSourceEndpoint("/test");
        b.setCanonicalizationVersion("1.0");
        b.setHashAlgorithm("SHA-256");
        b.setBundleHash("deadbeef");
        b.setSignatureAlgorithm("HMAC-SHA256");
        b.setBundleSignature("sig");
        b.setSignatureKeyId("k1");
        b.setSignatureGeneratedAt(LocalDateTime.now());
        b.setChainHash("chain");
        b.setChainSignature("chainSig");
        b.setChainSignatureKeyId("k1");
        b.setChainSignatureGeneratedAt(LocalDateTime.now());
        b.setRetentionUntil(LocalDateTime.now().plusDays(1));
        b.setWormLocked(true);
        b.setBundleJson("{\"bundleVersion\":\"v1\"}");
        b.setMetadataJson("{\"m\":1}");
        bundleRepository.saveAndFlush(b);

        Long bundleId = b.getId();

        assertThatThrownBy(() -> jdbcTemplate.update(
                "update turno_evidence_bundles set bundle_json = cast(? as jsonb) where id = ?",
                "{\"tampered\":true}", bundleId
        )).hasMessageContaining("turno_evidence_bundles is WORM protected");

        assertThatThrownBy(() -> jdbcTemplate.update(
                "delete from turno_evidence_bundles where id = ?",
                bundleId
        )).hasMessageContaining("DELETE is not allowed");

        // Transição permitida: ACTIVE -> RETENTION_EXPIRED (somente status/updated_at/modified_by)
        jdbcTemplate.update(
                "update turno_evidence_bundles set status = 'RETENTION_EXPIRED', updated_at = now(), modified_by = 'job' where id = ?",
                bundleId
        );

        assertThatThrownBy(() -> jdbcTemplate.update(
                "update turno_evidence_bundles set status = 'ACTIVE', updated_at = now(), modified_by = 'x' where id = ?",
                bundleId
        )).hasMessageContaining("invalid status transition");
    }

    private long abrirTurno(ProvisionarTenantResponse prov) throws Exception {
        AbrirTurnoRequest req = new AbrirTurnoRequest();
        req.setInstituicaoId(prov.getInstituicaoId());
        req.setUnidadeAtendimentoId(prov.getUnidadeAtendimentoId());
        req.setTipo(TurnoOperacionalTipo.DIARIO);
        req.setNome("Turno worm");
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
                        .build()
        );
    }
}

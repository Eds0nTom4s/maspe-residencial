package com.restaurante.financeiro;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.restaurante.dto.request.AbrirTurnoRequest;
import com.restaurante.dto.request.ChecklistItemRespostaRequest;
import com.restaurante.dto.request.FecharTurnoRequest;
import com.restaurante.dto.request.ProvisionarTenantRequest;
import com.restaurante.dto.response.ProvisionarTenantResponse;
import com.restaurante.financeiro.repository.OrdemPagamentoRepository;
import com.restaurante.model.entity.Instituicao;
import com.restaurante.model.entity.OperationalEventLog;
import com.restaurante.model.entity.OrdemPagamento;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.TenantUser;
import com.restaurante.model.entity.User;
import com.restaurante.model.entity.TurnoOperacional;
import com.restaurante.model.entity.UnidadeAtendimento;
import com.restaurante.model.enums.MetodoPagamentoManual;
import com.restaurante.model.enums.OperationalEventType;
import com.restaurante.model.enums.OperationalOrigem;
import com.restaurante.model.enums.OrdemPagamentoStatus;
import com.restaurante.model.enums.OrdemPagamentoTipo;
import com.restaurante.model.enums.Role;
import com.restaurante.model.enums.TenantTipo;
import com.restaurante.model.enums.TenantUserEstado;
import com.restaurante.model.enums.TenantUserRole;
import com.restaurante.model.enums.TurnoOperacionalTipo;
import com.restaurante.repository.InstituicaoRepository;
import com.restaurante.repository.OperationalEventLogRepository;
import com.restaurante.repository.TenantRepository;
import com.restaurante.repository.TenantUserRepository;
import com.restaurante.repository.UserRepository;
import com.restaurante.repository.TurnoOperacionalRepository;
import com.restaurante.repository.UnidadeAtendimentoRepository;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "spring.main.web-application-type=servlet"
)
@AutoConfigureMockMvc
@ActiveProfiles("it-postgres")
class SnapshotFinanceiroExportIT extends PostgresTestcontainersConfig {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired TenantProvisioningService provisioningService;

    @Autowired TenantRepository tenantRepository;
    @Autowired InstituicaoRepository instituicaoRepository;
    @Autowired UnidadeAtendimentoRepository unidadeAtendimentoRepository;
    @Autowired TurnoOperacionalRepository turnoOperacionalRepository;
    @Autowired OrdemPagamentoRepository ordemPagamentoRepository;
    @Autowired OperationalEventLogRepository operationalEventLogRepository;
    @Autowired UserRepository userRepository;
    @Autowired TenantUserRepository tenantUserRepository;

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    @Test
    @WithMockUser(username = "finance")
    void export_retorna_verificacao_valida_quando_integro() throws Exception {
        ProvisionarTenantResponse prov = provisionTenant("snap-exp-a", "SEA");
        TenantContextHolder.set(new TenantContext(
                prov.getTenantId(), prov.getTenantCode(), prov.getOwnerUserId(),
                Set.of(Role.ROLE_GERENTE.name(), TenantUserRole.TENANT_OWNER.name(), TenantUserRole.TENANT_FINANCE.name()),
                TenantResolutionSource.JWT, false, false
        ));

        long turnoId = abrirTurno(prov);
        criarOrdensConfirmadas(prov, turnoId);
        fecharTurno(turnoId, false);

        String resp = mockMvc.perform(get("/tenant/financeiro/turnos/{id}/snapshot/export", turnoId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(resp);
        assertThat(json.at("/data/verificacao/valido").asBoolean()).isTrue();
        assertThat(json.at("/data/verificacao/hashValido").asBoolean()).isTrue();
        assertThat(json.at("/data/verificacao/assinaturaValida").asBoolean()).isTrue();
        assertThat(json.at("/data/integridade/snapshotHash").asText()).isNotBlank();
        assertThat(json.at("/data/integridade/snapshotSignature").asText()).isNotBlank();
    }

    @Test
    @WithMockUser(username = "finance")
    void export_detecta_tamper_quando_snapshot_financeiro_foi_alterado() throws Exception {
        ProvisionarTenantResponse prov = provisionTenant("snap-exp-b", "SEB");
        TenantContextHolder.set(new TenantContext(
                prov.getTenantId(), prov.getTenantCode(), prov.getOwnerUserId(),
                Set.of(Role.ROLE_GERENTE.name(), TenantUserRole.TENANT_OWNER.name(), TenantUserRole.TENANT_FINANCE.name()),
                TenantResolutionSource.JWT, false, false
        ));

        long turnoId = abrirTurno(prov);
        criarOrdensConfirmadas(prov, turnoId);
        fecharTurno(turnoId, false);

        TurnoOperacional turno = turnoOperacionalRepository.findByIdAndTenantId(turnoId, prov.getTenantId()).orElseThrow();
        ObjectNode root = (ObjectNode) objectMapper.readTree(turno.getResumoJson());
        ObjectNode fin = (ObjectNode) root.get("financeiro");
        fin.put("totalCash", "99999.99"); // adulteração simulada
        root.set("financeiro", fin);
        turno.setResumoJson(objectMapper.writeValueAsString(root));
        turnoOperacionalRepository.saveAndFlush(turno);

        String resp = mockMvc.perform(get("/tenant/financeiro/turnos/{id}/snapshot/export", turnoId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(resp);
        assertThat(json.at("/data/verificacao/valido").asBoolean()).isFalse();
        assertThat(json.at("/data/verificacao/hashValido").asBoolean()).isFalse();
        assertThat(json.at("/data/verificacao/hashPersistido").asText()).isNotBlank();
        assertThat(json.at("/data/verificacao/hashRecalculado").asText()).isNotBlank();

        List<OperationalEventLog> inval = operationalEventLogRepository.findByTenantIdAndEventType(
                prov.getTenantId(),
                OperationalEventType.SNAPSHOT_FINANCEIRO_INTEGRIDADE_INVALIDA
        );
        assertThat(inval).isNotEmpty();
    }

    @Test
    @WithMockUser(username = "finance")
    void snapshot_antigo_sem_integridade_recebe_hash_no_primeiro_export_sem_mudar_valores() throws Exception {
        ProvisionarTenantResponse prov = provisionTenant("snap-exp-c", "SEC");
        TenantContextHolder.set(new TenantContext(
                prov.getTenantId(), prov.getTenantCode(), prov.getOwnerUserId(),
                Set.of(Role.ROLE_GERENTE.name(), TenantUserRole.TENANT_OWNER.name(), TenantUserRole.TENANT_FINANCE.name()),
                TenantResolutionSource.JWT, false, false
        ));

        long turnoId = abrirTurno(prov);
        criarOrdensConfirmadas(prov, turnoId);
        fecharTurno(turnoId, false);

        // Simula snapshot 37.1 antigo: remove bloco integridade
        TurnoOperacional turno = turnoOperacionalRepository.findByIdAndTenantId(turnoId, prov.getTenantId()).orElseThrow();
        ObjectNode root = (ObjectNode) objectMapper.readTree(turno.getResumoJson());
        ObjectNode fin = (ObjectNode) root.get("financeiro");
        fin.remove("integridade");
        root.set("financeiro", fin);
        String before = objectMapper.writeValueAsString(fin);
        turno.setResumoJson(objectMapper.writeValueAsString(root));
        turnoOperacionalRepository.saveAndFlush(turno);

        mockMvc.perform(get("/tenant/financeiro/turnos/{id}/snapshot/export", turnoId))
                .andExpect(status().isOk());

        TurnoOperacional after = turnoOperacionalRepository.findByIdAndTenantId(turnoId, prov.getTenantId()).orElseThrow();
        ObjectNode rootAfter = (ObjectNode) objectMapper.readTree(after.getResumoJson());
        ObjectNode finAfter = (ObjectNode) rootAfter.get("financeiro");
        assertThat(finAfter.hasNonNull("integridade")).isTrue();
        assertThat(finAfter.at("/integridade/snapshotSignature").asText()).isNotBlank();

        // Valores financeiros não devem mudar (compara JSON sem integridade)
        ObjectNode finAfterNoInteg = finAfter.deepCopy();
        finAfterNoInteg.remove("integridade");
        assertThat(objectMapper.writeValueAsString(finAfterNoInteg)).isEqualTo(before);

        List<OperationalEventLog> events = operationalEventLogRepository.findByTenantIdAndEventType(
                prov.getTenantId(),
                OperationalEventType.SNAPSHOT_FINANCEIRO_HASH_GERADO
        );
        assertThat(events).isNotEmpty();
    }

    @Test
    @WithMockUser(username = "finance")
    void assinatura_invalida_nao_e_sobrescrita_automaticamente() throws Exception {
        ProvisionarTenantResponse prov = provisionTenant("snap-exp-e", "SEE");
        TenantContextHolder.set(new TenantContext(
                prov.getTenantId(), prov.getTenantCode(), prov.getOwnerUserId(),
                Set.of(Role.ROLE_GERENTE.name(), TenantUserRole.TENANT_OWNER.name(), TenantUserRole.TENANT_FINANCE.name()),
                TenantResolutionSource.JWT, false, false
        ));

        long turnoId = abrirTurno(prov);
        criarOrdensConfirmadas(prov, turnoId);
        fecharTurno(turnoId, false);

        TurnoOperacional turno = turnoOperacionalRepository.findByIdAndTenantId(turnoId, prov.getTenantId()).orElseThrow();
        ObjectNode root = (ObjectNode) objectMapper.readTree(turno.getResumoJson());
        ObjectNode fin = (ObjectNode) root.get("financeiro");
        ObjectNode integ = (ObjectNode) fin.get("integridade");
        String oldSig = integ.get("snapshotSignature").asText();
        integ.put("snapshotSignature", "deadbeef" + oldSig); // assinatura adulterada
        fin.set("integridade", integ);
        root.set("financeiro", fin);
        turno.setResumoJson(objectMapper.writeValueAsString(root));
        turnoOperacionalRepository.saveAndFlush(turno);

        String resp = mockMvc.perform(get("/tenant/financeiro/turnos/{id}/snapshot/export", turnoId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(resp);
        assertThat(json.at("/data/verificacao/hashValido").asBoolean()).isTrue();
        assertThat(json.at("/data/verificacao/assinaturaValida").asBoolean()).isFalse();
        assertThat(json.at("/data/verificacao/valido").asBoolean()).isFalse();

        // não sobrescreve assinatura persistida
        TurnoOperacional after = turnoOperacionalRepository.findByIdAndTenantId(turnoId, prov.getTenantId()).orElseThrow();
        ObjectNode afterRoot = (ObjectNode) objectMapper.readTree(after.getResumoJson());
        String persistedSig = afterRoot.at("/financeiro/integridade/snapshotSignature").asText();
        assertThat(persistedSig).startsWith("deadbeef");

        List<OperationalEventLog> inval = operationalEventLogRepository.findByTenantIdAndEventType(
                prov.getTenantId(),
                OperationalEventType.SNAPSHOT_FINANCEIRO_ASSINATURA_INVALIDA
        );
        assertThat(inval).isNotEmpty();
    }

    @Test
    @WithMockUser(username = "operator")
    void operator_nao_pode_exportar_snapshot() throws Exception {
        ProvisionarTenantResponse prov = provisionTenant("snap-exp-d", "SED");

        User operator = new User();
        String suffix = String.valueOf(Math.abs(System.nanoTime() % 1_000_000L));
        operator.setUsername("op+" + suffix + "@t.com");
        operator.setPassword("x");
        operator.setEmail(operator.getUsername());
        operator.setTelefone("+24490" + String.format("%06d", Integer.parseInt(suffix)));
        operator.setRoles(Set.of(Role.ROLE_GERENTE));
        operator.setAtivo(true);
        operator = userRepository.saveAndFlush(operator);

        Tenant tenant = tenantRepository.findById(prov.getTenantId()).orElseThrow();
        TenantUser tu = new TenantUser();
        tu.setTenant(tenant);
        tu.setUser(operator);
        tu.setRole(TenantUserRole.TENANT_OPERATOR);
        tu.setEstado(TenantUserEstado.ATIVO);
        tenantUserRepository.saveAndFlush(tu);

        TenantContextHolder.set(new TenantContext(
                prov.getTenantId(), prov.getTenantCode(), operator.getId(),
                Set.of(Role.ROLE_GERENTE.name(), TenantUserRole.TENANT_OPERATOR.name()),
                TenantResolutionSource.JWT, false, false
        ));

        long turnoId = abrirTurno(prov);
        fecharTurno(turnoId, false);

        mockMvc.perform(get("/tenant/financeiro/turnos/{id}/snapshot/export", turnoId))
                .andExpect(status().isForbidden());
    }

    private void criarOrdensConfirmadas(ProvisionarTenantResponse prov, long turnoId) {
        Tenant tenant = tenantRepository.findById(prov.getTenantId()).orElseThrow();
        Instituicao inst = instituicaoRepository.findById(prov.getInstituicaoId()).orElseThrow();
        UnidadeAtendimento ua = unidadeAtendimentoRepository.findById(prov.getUnidadeAtendimentoId()).orElseThrow();
        TurnoOperacional turno = turnoOperacionalRepository.findByIdAndTenantId(turnoId, prov.getTenantId()).orElseThrow();

        ordemPagamentoRepository.save(buildOrdem(tenant, inst, ua, turno, OrdemPagamentoTipo.PEDIDO, MetodoPagamentoManual.CASH, new BigDecimal("10000.00")));
        ordemPagamentoRepository.save(buildOrdem(tenant, inst, ua, turno, OrdemPagamentoTipo.FUNDO_CONSUMO, MetodoPagamentoManual.TPA, new BigDecimal("5000.00")));
        ordemPagamentoRepository.flush();
    }

    private OrdemPagamento buildOrdem(Tenant tenant,
                                     Instituicao inst,
                                     UnidadeAtendimento ua,
                                     TurnoOperacional turno,
                                     OrdemPagamentoTipo tipo,
                                     MetodoPagamentoManual metodo,
                                     BigDecimal valor) {
        OrdemPagamento o = new OrdemPagamento();
        o.setTenant(tenant);
        o.setInstituicao(inst);
        o.setUnidadeAtendimento(ua);
        o.setTurnoOperacional(turno);
        o.setTipo(tipo);
        o.setStatus(OrdemPagamentoStatus.CONFIRMADA);
        o.setMetodoSolicitado(metodo);
        o.setValor(valor);
        o.setMoeda("AOA");
        o.setTokenQr("OP-EXP-" + System.nanoTime());
        o.setCodigoCurto("OP-EXP");
        o.setCriadoPorOrigem(OperationalOrigem.DEVICE_POS);
        return o;
    }

    private long abrirTurno(ProvisionarTenantResponse prov) throws Exception {
        AbrirTurnoRequest req = new AbrirTurnoRequest();
        req.setInstituicaoId(prov.getInstituicaoId());
        req.setUnidadeAtendimentoId(prov.getUnidadeAtendimentoId());
        req.setTipo(TurnoOperacionalTipo.DIARIO);
        req.setNome("Turno export");
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

    private void fecharTurno(long turnoId, boolean forcar) throws Exception {
        FecharTurnoRequest req = new FecharTurnoRequest();
        req.setForcarFecho(forcar);
        if (forcar) req.setObservacao("forcado");
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

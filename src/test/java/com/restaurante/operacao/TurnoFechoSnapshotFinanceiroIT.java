package com.restaurante.operacao;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.dto.request.AbrirTurnoRequest;
import com.restaurante.dto.request.ChecklistItemRespostaRequest;
import com.restaurante.dto.request.FecharTurnoRequest;
import com.restaurante.dto.request.ProvisionarTenantRequest;
import com.restaurante.dto.response.ProvisionarTenantResponse;
import com.restaurante.financeiro.repository.OrdemPagamentoRepository;
import com.restaurante.model.entity.Instituicao;
import com.restaurante.model.entity.OrdemPagamento;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.TurnoOperacional;
import com.restaurante.model.entity.UnidadeAtendimento;
import com.restaurante.model.enums.MetodoPagamentoManual;
import com.restaurante.model.enums.OperationalOrigem;
import com.restaurante.model.enums.OrdemPagamentoStatus;
import com.restaurante.model.enums.OrdemPagamentoTipo;
import com.restaurante.model.enums.Role;
import com.restaurante.model.enums.TenantUserRole;
import com.restaurante.model.enums.TenantTipo;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "spring.main.web-application-type=servlet"
)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("it-postgres")
class TurnoFechoSnapshotFinanceiroIT extends PostgresTestcontainersConfig {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired TenantProvisioningService provisioningService;

    @Autowired TenantRepository tenantRepository;
    @Autowired InstituicaoRepository instituicaoRepository;
    @Autowired UnidadeAtendimentoRepository unidadeAtendimentoRepository;
    @Autowired TurnoOperacionalRepository turnoOperacionalRepository;
    @Autowired OrdemPagamentoRepository ordemPagamentoRepository;

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    @Test
    @WithMockUser(username = "owner")
    void fecho_normal_persiste_snapshot_financeiro_no_resumo_json() throws Exception {
        ProvisionarTenantResponse prov = provisionTenant("turno-snap-a", "TSA");
        TenantContextHolder.set(new TenantContext(
                prov.getTenantId(), prov.getTenantCode(), prov.getOwnerUserId(),
                Set.of(Role.ROLE_GERENTE.name(), TenantUserRole.TENANT_OWNER.name()),
                TenantResolutionSource.JWT, false, false
        ));

        long turnoId = abrirTurno(prov);

        // cria ordens manuais confirmadas no turno (CASH=10000, TPA=5000)
        Tenant tenant = tenantRepository.findById(prov.getTenantId()).orElseThrow();
        Instituicao inst = instituicaoRepository.findById(prov.getInstituicaoId()).orElseThrow();
        UnidadeAtendimento ua = unidadeAtendimentoRepository.findById(prov.getUnidadeAtendimentoId()).orElseThrow();
        TurnoOperacional turno = turnoOperacionalRepository.findByIdAndTenantId(turnoId, prov.getTenantId()).orElseThrow();

        ordemPagamentoRepository.save(buildOrdem(tenant, inst, ua, turno, OrdemPagamentoTipo.PEDIDO, MetodoPagamentoManual.CASH, "OP-SNAP-CASH", new BigDecimal("10000.00")));
        ordemPagamentoRepository.save(buildOrdem(tenant, inst, ua, turno, OrdemPagamentoTipo.FUNDO_CONSUMO, MetodoPagamentoManual.TPA, "OP-SNAP-TPA", new BigDecimal("5000.00")));
        ordemPagamentoRepository.flush();

        FecharTurnoRequest closeReq = fecharReq(false);
        String closeResp = mockMvc.perform(post("/tenant/operacao/turnos/" + turnoId + "/fechar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(closeReq)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String resumoJson = objectMapper.readTree(closeResp).at("/data/resumoJson").asText();
        JsonNode root = objectMapper.readTree(resumoJson);

        assertThat(root.hasNonNull("financeiro")).isTrue();
        JsonNode fin = root.get("financeiro");
        assertThat(fin.get("snapshotVersion").asText()).isEqualTo("37.1");

        assertThat(fin.get("totalCash").asText()).isEqualTo("10000.00");
        assertThat(fin.get("totalTpa").asText()).isEqualTo("5000.00");
        assertThat(fin.get("totalAppyPay").asText()).isEqualTo("0");

        assertThat(fin.get("totalPagamentoPedidos").asText()).isEqualTo("10000.00");
        assertThat(fin.get("totalCarregamentoFundo").asText()).isEqualTo("5000.00");

        assertThat(fin.hasNonNull("totaisPorMetodo")).isTrue();
        assertThat(fin.hasNonNull("totaisPorOrigem")).isTrue();
        assertThat(fin.hasNonNull("alertasFinanceiros")).isTrue();
        assertThat(fin.at("/alertasFinanceiros/totalPagamentosPendentes").asLong()).isEqualTo(0L);

        assertThat(fin.hasNonNull("integridade")).isTrue();
        assertThat(fin.at("/integridade/hashAlgorithm").asText()).isEqualTo("SHA-256");
        assertThat(fin.at("/integridade/snapshotHash").asText()).isNotBlank();
        assertThat(fin.at("/integridade/canonicalizationVersion").asText()).isEqualTo("1.0");

        // Sanitização: não persistir payload bruto do gateway nem tokens
        assertThat(resumoJson).doesNotContain("gatewayResponse");
        assertThat(resumoJson).doesNotContain("gateway_status_raw");
        assertThat(resumoJson).doesNotContain("deviceToken");
    }

    @Test
    @WithMockUser(username = "owner")
    void fecho_forcado_persiste_snapshot_financeiro_com_flag_forcarFecho() throws Exception {
        ProvisionarTenantResponse prov = provisionTenant("turno-snap-b", "TSB");
        TenantContextHolder.set(new TenantContext(
                prov.getTenantId(), prov.getTenantCode(), prov.getOwnerUserId(),
                Set.of(Role.ROLE_GERENTE.name(), TenantUserRole.TENANT_OWNER.name()),
                TenantResolutionSource.JWT, false, false
        ));

        long turnoId = abrirTurno(prov);

        FecharTurnoRequest closeReq = fecharReq(true);
        closeReq.setObservacao("forcado");
        String closeResp = mockMvc.perform(post("/tenant/operacao/turnos/" + turnoId + "/fechar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(closeReq)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String resumoJson = objectMapper.readTree(closeResp).at("/data/resumoJson").asText();
        JsonNode root = objectMapper.readTree(resumoJson);
        JsonNode fin = root.get("financeiro");
        assertThat(fin.get("snapshotVersion").asText()).isEqualTo("37.1");
        assertThat(fin.at("/observacoes/forcarFecho").asBoolean()).isTrue();
    }

    private long abrirTurno(ProvisionarTenantResponse prov) throws Exception {
        AbrirTurnoRequest req = new AbrirTurnoRequest();
        req.setInstituicaoId(prov.getInstituicaoId());
        req.setUnidadeAtendimentoId(prov.getUnidadeAtendimentoId());
        req.setTipo(TurnoOperacionalTipo.DIARIO);
        req.setNome("Turno snap");
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

    private OrdemPagamento buildOrdem(Tenant tenant,
                                     Instituicao inst,
                                     UnidadeAtendimento ua,
                                     TurnoOperacional turno,
                                     OrdemPagamentoTipo tipo,
                                     MetodoPagamentoManual metodo,
                                     String token,
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
        o.setTokenQr(token + "-" + System.nanoTime());
        o.setCodigoCurto(token);
        o.setCriadoPorOrigem(OperationalOrigem.DEVICE_POS);
        return o;
    }

    private FecharTurnoRequest fecharReq(boolean forcar) {
        FecharTurnoRequest req = new FecharTurnoRequest();
        req.setForcarFecho(forcar);
        req.setChecklist(List.of(
                boolItem("PEDIDOS_PENDENTES_VERIFICADOS", true),
                boolItem("PAGAMENTOS_PENDENTES_VERIFICADOS", true),
                boolItem("SUBPEDIDOS_EM_ABERTO_VERIFICADOS", true)
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
                        .build()
        );
    }
}

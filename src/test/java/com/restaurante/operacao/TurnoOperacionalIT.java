package com.restaurante.operacao;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.dto.request.AbrirTurnoRequest;
import com.restaurante.dto.request.CancelarTurnoRequest;
import com.restaurante.dto.request.ChecklistItemRespostaRequest;
import com.restaurante.dto.request.FecharTurnoRequest;
import com.restaurante.dto.request.ProvisionarTenantRequest;
import com.restaurante.dto.request.PublicQrPedidoItemRequest;
import com.restaurante.dto.request.PublicQrPedidoRequest;
import com.restaurante.dto.response.ProvisionarTenantResponse;
import com.restaurante.exception.ConflictException;
import com.restaurante.financeiro.enums.StatusPagamentoGateway;
import com.restaurante.model.entity.OperationalEventLog;
import com.restaurante.model.entity.Pedido;
import com.restaurante.model.entity.Produto;
import com.restaurante.model.entity.SubPedido;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.User;
import com.restaurante.model.enums.OperationalEventType;
import com.restaurante.model.enums.Role;
import com.restaurante.model.enums.StatusFinanceiroPedido;
import com.restaurante.model.enums.StatusPedido;
import com.restaurante.model.enums.StatusSubPedido;
import com.restaurante.model.enums.TenantTipo;
import com.restaurante.model.enums.TenantUserRole;
import com.restaurante.model.enums.TipoPagamentoPedido;
import com.restaurante.model.enums.TurnoOperacionalTipo;
import com.restaurante.repository.OperationalEventLogRepository;
import com.restaurante.repository.PedidoRepository;
import com.restaurante.repository.ProdutoRepository;
import com.restaurante.repository.SubPedidoRepository;
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
import org.springframework.data.domain.PageRequest;
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
        properties = "spring.main.web-application-type=servlet"
)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("it-postgres")
class TurnoOperacionalIT extends PostgresTestcontainersConfig {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired TenantProvisioningService provisioningService;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired PedidoRepository pedidoRepository;
    @Autowired SubPedidoRepository subPedidoRepository;
    @Autowired OperationalEventLogRepository operationalEventLogRepository;

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    @Test
    @WithMockUser(username = "owner")
    void owner_opens_turno_with_valid_checklist_and_event_is_recorded() throws Exception {
        ProvisionarTenantResponse prov = provisionTenant("op-turno-a", "OTA");
        TenantContextHolder.set(new TenantContext(
                prov.getTenantId(), prov.getTenantCode(), prov.getOwnerUserId(),
                Set.of(Role.ROLE_GERENTE.name(), TenantUserRole.TENANT_OWNER.name()),
                TenantResolutionSource.JWT, false, false
        ));

        AbrirTurnoRequest req = abrirReq(prov.getInstituicaoId(), prov.getUnidadeAtendimentoId());

        String resp = mockMvc.perform(post("/tenant/operacao/turnos/abrir")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(resp);
        long turnoId = json.at("/data/id").asLong();
        assertThat(turnoId).isPositive();

        List<OperationalEventLog> events = operationalEventLogRepository.findByTenantIdAndEventType(prov.getTenantId(), OperationalEventType.TURNO_ABERTO);
        assertThat(events).isNotEmpty();
    }

    @Test
    @WithMockUser(username = "owner")
    void cannot_open_two_turnos_in_same_unit() throws Exception {
        ProvisionarTenantResponse prov = provisionTenant("op-turno-b", "OTB");
        TenantContextHolder.set(new TenantContext(
                prov.getTenantId(), prov.getTenantCode(), prov.getOwnerUserId(),
                Set.of(Role.ROLE_GERENTE.name(), TenantUserRole.TENANT_OWNER.name()),
                TenantResolutionSource.JWT, false, false
        ));

        AbrirTurnoRequest req = abrirReq(prov.getInstituicaoId(), prov.getUnidadeAtendimentoId());
        mockMvc.perform(post("/tenant/operacao/turnos/abrir")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/tenant/operacao/turnos/abrir")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(username = "finance")
    void finance_cannot_open_turno() throws Exception {
        ProvisionarTenantResponse prov = provisionTenant("op-turno-fin", "OTF");
        TenantContextHolder.set(new TenantContext(
                prov.getTenantId(), prov.getTenantCode(), prov.getOwnerUserId(),
                Set.of(Role.ROLE_GERENTE.name(), TenantUserRole.TENANT_FINANCE.name()),
                TenantResolutionSource.JWT, false, false
        ));

        AbrirTurnoRequest req = abrirReq(prov.getInstituicaoId(), prov.getUnidadeAtendimentoId());
        mockMvc.perform(post("/tenant/operacao/turnos/abrir")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "owner")
    void missing_required_checklist_blocks_opening() throws Exception {
        ProvisionarTenantResponse prov = provisionTenant("op-turno-chk", "OTC");
        TenantContextHolder.set(new TenantContext(
                prov.getTenantId(), prov.getTenantCode(), prov.getOwnerUserId(),
                Set.of(Role.ROLE_GERENTE.name(), TenantUserRole.TENANT_OWNER.name()),
                TenantResolutionSource.JWT, false, false
        ));

        AbrirTurnoRequest req = new AbrirTurnoRequest();
        req.setInstituicaoId(prov.getInstituicaoId());
        req.setUnidadeAtendimentoId(prov.getUnidadeAtendimentoId());
        req.setTipo(TurnoOperacionalTipo.DIARIO);
        req.setNome("Turno");
        req.setChecklist(List.of()); // faltando obrigatórios

        mockMvc.perform(post("/tenant/operacao/turnos/abrir")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "owner")
    void pre_fecho_blocks_close_when_pending_subpedidos_exist_and_force_requires_owner_admin() throws Exception {
        ProvisionarTenantResponse prov = provisionTenant("op-turno-close", "OTD");
        TenantContextHolder.set(new TenantContext(
                prov.getTenantId(), prov.getTenantCode(), prov.getOwnerUserId(),
                Set.of(Role.ROLE_GERENTE.name(), TenantUserRole.TENANT_OWNER.name()),
                TenantResolutionSource.JWT, false, false
        ));

        // abre turno
        AbrirTurnoRequest openReq = abrirReq(prov.getInstituicaoId(), prov.getUnidadeAtendimentoId());
        String openResp = mockMvc.perform(post("/tenant/operacao/turnos/abrir")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(openReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long turnoId = objectMapper.readTree(openResp).at("/data/id").asLong();

        // cria pedido/subpedido não-terminal associado ao turno (via endpoint público QR)
        Produto prod = produtoRepository.findByTenantIdAndDisponivelTrueAndAtivoTrue(prov.getTenantId(), PageRequest.of(0, 1))
                .getContent().get(0);
        PublicQrPedidoRequest qrReq = new PublicQrPedidoRequest();
        PublicQrPedidoItemRequest it = new PublicQrPedidoItemRequest();
        it.setProdutoId(prod.getId());
        it.setQuantidade(1);
        qrReq.setItens(List.of(it));
        qrReq.setIdempotencyKey("idem-" + turnoId);
        String publicResp = mockMvc.perform(post("/public/q/" + prov.getQrToken() + "/pedidos")
                        .header("Idempotency-Key", "idem-" + turnoId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(qrReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long pedidoId = objectMapper.readTree(publicResp).at("/data/pedidoId").asLong();

        Pedido pedido = pedidoRepository.findById(pedidoId).orElseThrow();
        assertThat(pedido.getTurnoOperacional()).isNotNull();
        assertThat(pedido.getTurnoOperacional().getId()).isEqualTo(turnoId);

        SubPedido sp = subPedidoRepository.findByPedidoIdOrderByCreatedAtAsc(pedidoId).get(0);
        sp.setStatus(StatusSubPedido.EM_PREPARACAO);
        subPedidoRepository.saveAndFlush(sp);

        // tenta fechar sem forçar -> 409
        FecharTurnoRequest closeReq = fecharReq(false);
        mockMvc.perform(post("/tenant/operacao/turnos/" + turnoId + "/fechar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(closeReq)))
                .andExpect(status().isConflict());

        // força fecho com OWNER -> ok
        FecharTurnoRequest forceReq = fecharReq(true);
        forceReq.setObservacao("forcado");
        mockMvc.perform(post("/tenant/operacao/turnos/" + turnoId + "/fechar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(forceReq)))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "owner")
    void cross_tenant_detalhar_returns_404() throws Exception {
        ProvisionarTenantResponse a = provisionTenant("op-turno-xa", "OXA");
        ProvisionarTenantResponse b = provisionTenant("op-turno-xb", "OXB");

        // abre turno no tenant A
        TenantContextHolder.set(new TenantContext(
                a.getTenantId(), a.getTenantCode(), a.getOwnerUserId(),
                Set.of(Role.ROLE_GERENTE.name(), TenantUserRole.TENANT_OWNER.name()),
                TenantResolutionSource.JWT, false, false
        ));
        String openResp = mockMvc.perform(post("/tenant/operacao/turnos/abrir")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(abrirReq(a.getInstituicaoId(), a.getUnidadeAtendimentoId()))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long turnoId = objectMapper.readTree(openResp).at("/data/id").asLong();

        // tenta acessar com tenant B
        TenantContextHolder.set(new TenantContext(
                b.getTenantId(), b.getTenantCode(), b.getOwnerUserId(),
                Set.of(Role.ROLE_GERENTE.name(), TenantUserRole.TENANT_OWNER.name()),
                TenantResolutionSource.JWT, false, false
        ));
        mockMvc.perform(get("/tenant/operacao/turnos/" + turnoId))
                .andExpect(status().isNotFound());
    }

    private AbrirTurnoRequest abrirReq(Long instituicaoId, Long unidadeAtendimentoId) {
        AbrirTurnoRequest req = new AbrirTurnoRequest();
        req.setInstituicaoId(instituicaoId);
        req.setUnidadeAtendimentoId(unidadeAtendimentoId);
        req.setTipo(TurnoOperacionalTipo.DIARIO);
        req.setNome("Turno manhã");
        req.setObservacao("Abertura normal");
        req.setChecklist(List.of(
                boolItem("DEVICE_ONLINE", true),
                boolItem("QR_VISIVEL", true),
                boolItem("CATALOGO_ATUALIZADO", true),
                boolItem("UNIDADE_PRODUCAO_ATIVA", true),
                boolItem("OPERADOR_CONFIRMOU", true)
        ));
        return req;
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
        ChecklistItemRespostaRequest r = new ChecklistItemRespostaRequest();
        r.setCodigo(codigo);
        r.setValorBoolean(v);
        return r;
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

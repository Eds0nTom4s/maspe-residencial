package com.restaurante.consumo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.consumo.identificacao.entity.ClienteConsumo;
import com.restaurante.consumo.identificacao.repository.ClienteConsumoRepository;
import com.restaurante.consumo.participante.entity.SessaoConsumoParticipante;
import com.restaurante.consumo.participante.repository.SessaoConsumoParticipanteRepository;
import com.restaurante.consumo.participante.service.SessaoOwnerActionTokenService;
import com.restaurante.model.entity.*;
import com.restaurante.model.enums.*;
import com.restaurante.repository.*;
import com.restaurante.testsupport.PostgresTestcontainersConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.main.web-application-type=servlet",
                "consuma.sessao.owner-action-token.ttl-minutes=10",
                "consuma.sessao.owner-action-token.max-uses=5",
                "consuma.sessao.owner-action-token.hash-pepper=testpepper"
        }
)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("it-postgres")
@DisplayName("PublicOwnerTokenActionController — Integração de Ações via Token")
class PublicOwnerTokenActionControllerIT extends PostgresTestcontainersConfig {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @Autowired TenantRepository tenantRepository;
    @Autowired InstituicaoRepository instituicaoRepository;
    @Autowired UnidadeAtendimentoRepository unidadeAtendimentoRepository;
    @Autowired MesaRepository mesaRepository;
    @Autowired QrCodeOperacionalRepository qrCodeOperacionalRepository;
    @Autowired SessaoConsumoRepository sessaoConsumoRepository;
    @Autowired ClienteConsumoRepository clienteConsumoRepository;
    @Autowired SessaoConsumoParticipanteRepository participanteRepository;
    @Autowired SessaoOwnerActionTokenService ownerTokenService;

    private Tenant tenant;
    private Instituicao instituicao;
    private UnidadeAtendimento unidade;
    private Mesa mesa;
    private QrCodeOperacional qr;
    private SessaoConsumo sessao;

    private SessaoConsumoParticipante owner;
    private SessaoConsumoParticipante pendingMember;
    private String rawToken;

    @BeforeEach
    void setupData() {
        // Limpar dados anteriores para evitar poluição
        participanteRepository.deleteAllInBatch();
        sessaoConsumoRepository.deleteAllInBatch();
        qrCodeOperacionalRepository.deleteAllInBatch();
        mesaRepository.deleteAllInBatch();
        unidadeAtendimentoRepository.deleteAllInBatch();
        instituicaoRepository.deleteAllInBatch();
        tenantRepository.deleteAllInBatch();

        // 1. Setup Base
        tenant = new Tenant();
        tenant.setNome("Consuma Test Tenant");
        tenant.setSlug("consuma-test-tenant");
        tenant.setTenantCode("CTT-" + UUID.randomUUID().toString().substring(0, 5));
        tenant.setTipo(TenantTipo.RESTAURANTE);
        tenant.setEstado(TenantEstado.ATIVO);
        tenant = tenantRepository.saveAndFlush(tenant);

        instituicao = new Instituicao();
        instituicao.setTenant(tenant);
        instituicao.setNome("Inst Test");
        instituicao.setSigla("IT");
        instituicao.setNif("NIF-TEST-1");
        instituicao.setTelefoneAutorizacao("+244900000000");
        instituicao.setAtiva(true);
        instituicao = instituicaoRepository.saveAndFlush(instituicao);

        unidade = new UnidadeAtendimento();
        unidade.setInstituicao(instituicao);
        unidade.setNome("Unidade 1");
        unidade.setTipo(TipoUnidadeAtendimento.RESTAURANTE);
        unidade.setAtiva(true);
        unidade = unidadeAtendimentoRepository.saveAndFlush(unidade);

        mesa = new Mesa();
        mesa.setUnidadeAtendimento(unidade);
        mesa.setNumero(10);
        mesa.setAtiva(true);
        mesa = mesaRepository.saveAndFlush(mesa);

        qr = new QrCodeOperacional();
        qr.setTenant(tenant);
        qr.setInstituicao(instituicao);
        qr.setMesa(mesa);
        qr.setTipo(QrCodeOperacionalTipo.MESA);
        qr.setToken("qr-token-test-" + UUID.randomUUID());
        qr = qrCodeOperacionalRepository.saveAndFlush(qr);

        sessao = new SessaoConsumo();
        sessao.setTenant(tenant);
        sessao.setInstituicao(instituicao);
        sessao.setUnidadeAtendimento(unidade);
        sessao.setMesa(mesa);
        sessao.setModoAnonimo(true);
        sessao.setStatus(StatusSessaoConsumo.ABERTA);
        sessao.setTipoSessao(TipoSessao.POS_PAGO);
        sessao.setQrCodeSessao(qr.getToken());
        sessao = sessaoConsumoRepository.saveAndFlush(sessao);


        // 2. Setup Owner
        ClienteConsumo ownerCliente = new ClienteConsumo();
        ownerCliente.setTenant(tenant);
        ownerCliente.setNome("Dono da Sessao");
        ownerCliente.setTelefone("+244911111111");
        ownerCliente.setTelefoneNormalizado("+244911111111");
        ownerCliente.setStatus(ClienteConsumoStatus.ACTIVE);
        ownerCliente = clienteConsumoRepository.saveAndFlush(ownerCliente);

        owner = new SessaoConsumoParticipante();
        owner.setTenant(tenant);
        owner.setSessaoConsumo(sessao);
        owner.setClienteConsumo(ownerCliente);
        owner.setRole(SessaoParticipanteRole.OWNER);
        owner.setStatus(SessaoParticipanteStatus.ACTIVE);
        owner.setNomeExibicao("Dono");
        owner = participanteRepository.saveAndFlush(owner);

        // Vincular owner à sessão de consumo principal
        sessao.setClienteConsumo(ownerCliente);
        sessao = sessaoConsumoRepository.saveAndFlush(sessao);

        // 3. Emitir Token de Ação para o Owner
        var issueResult = ownerTokenService.issueAfterOwnerOtp(owner, "127.0.0.1", "Mozilla/5.0");
        rawToken = issueResult.rawToken();

        // 4. Setup Member Pendente de Aprovação
        ClienteConsumo memberCliente = new ClienteConsumo();
        memberCliente.setTenant(tenant);
        memberCliente.setNome("Membro Pendente");
        memberCliente.setTelefone("+244922222222");
        memberCliente.setTelefoneNormalizado("+244922222222");
        memberCliente.setStatus(ClienteConsumoStatus.ACTIVE);
        memberCliente = clienteConsumoRepository.saveAndFlush(memberCliente);

        pendingMember = new SessaoConsumoParticipante();
        pendingMember.setTenant(tenant);
        pendingMember.setSessaoConsumo(sessao);
        pendingMember.setClienteConsumo(memberCliente);
        pendingMember.setRole(SessaoParticipanteRole.MEMBER);
        pendingMember.setStatus(SessaoParticipanteStatus.PENDING_APPROVAL);
        pendingMember.setNomeExibicao("Pendente");
        pendingMember.setApprovalRequestedAt(Instant.now());
        pendingMember = participanteRepository.saveAndFlush(pendingMember);
    }

    // =========================================================================
    // 3.1 Extração: Header vs Body
    // =========================================================================

    @Test
    @DisplayName("Aprovar participante usando o token via Header")
    void approve_using_token_in_header() throws Exception {
        mockMvc.perform(post("/api/public/q/{token}/participantes/{participanteId}/approve-by-token",
                        qr.getToken(), pendingMember.getId())
                        .header("X-Owner-Action-Token", rawToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Aprovado no header\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.approved").value(true));

        var updated = participanteRepository.findById(pendingMember.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(SessaoParticipanteStatus.ACTIVE);
    }

    @Test
    @DisplayName("Aprovar participante usando o token via Body")
    void approve_using_token_in_body() throws Exception {
        mockMvc.perform(post("/api/public/q/{token}/participantes/{participanteId}/approve-by-token",
                        qr.getToken(), pendingMember.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ownerActionToken\":\"" + rawToken + "\", \"reason\":\"Aprovado no body\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("Prioriza Header quando ambos Header e Body são informados")
    void header_takes_precedence_over_body() throws Exception {
        mockMvc.perform(post("/api/public/q/{token}/participantes/{participanteId}/approve-by-token",
                        qr.getToken(), pendingMember.getId())
                        .header("X-Owner-Action-Token", rawToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ownerActionToken\":\"TOKEN-INVALIDO\", \"reason\":\"Prioridade\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("Erro se token não for enviado no header nem no body")
    void error_when_token_is_missing() throws Exception {
        mockMvc.perform(post("/api/public/q/{token}/participantes/{participanteId}/approve-by-token",
                        qr.getToken(), pendingMember.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Sem token\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("OWNER_ACTION_TOKEN_REQUIRED"));
    }

    // =========================================================================
    // 3.2 Query Parameter Rejeitado
    // =========================================================================

    @Test
    @DisplayName("Rejeita requisição se o token for enviado via Query Param")
    void error_when_token_is_sent_in_query_param() throws Exception {
        mockMvc.perform(post("/api/public/q/{token}/participantes/{participanteId}/approve-by-token?ownerActionToken=" + rawToken,
                        qr.getToken(), pendingMember.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("OWNER_ACTION_TOKEN_QUERY_PARAM_NOT_ALLOWED"));
    }

    // =========================================================================
    // 3.3 Fluxo Feliz
    // =========================================================================

    @Test
    @DisplayName("Rejeitar participante com sucesso")
    void reject_participant_success() throws Exception {
        mockMvc.perform(post("/api/public/q/{token}/participantes/{participanteId}/reject-by-token",
                        qr.getToken(), pendingMember.getId())
                        .header("X-Owner-Action-Token", rawToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Muito barulhento\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("REJECTED"))
                .andExpect(jsonPath("$.data.rejected").value(true));

        var updated = participanteRepository.findById(pendingMember.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(SessaoParticipanteStatus.REJECTED);
    }

    @Test
    @DisplayName("Cancelar participante com sucesso")
    void cancel_participant_success() throws Exception {
        mockMvc.perform(post("/api/public/q/{token}/participantes/{participanteId}/cancel-by-token",
                        qr.getToken(), pendingMember.getId())
                        .header("X-Owner-Action-Token", rawToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Desistiu\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.wasCancelled").value(true));

        var updated = participanteRepository.findById(pendingMember.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(SessaoParticipanteStatus.CANCELLED);
    }

    // =========================================================================
    // 3.4 Estados Inválidos
    // =========================================================================

    @Test
    @DisplayName("Aprovar participante que já está ativo falha com erro de domínio")
    void approve_active_participant_fails() throws Exception {
        pendingMember.setStatus(SessaoParticipanteStatus.ACTIVE);
        participanteRepository.saveAndFlush(pendingMember);

        mockMvc.perform(post("/api/public/q/{token}/participantes/{participanteId}/approve-by-token",
                        qr.getToken(), pendingMember.getId())
                        .header("X-Owner-Action-Token", rawToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("PARTICIPANTE_ESTADO_INVALIDO"));
    }

    // =========================================================================
    // 3.5 Segurança e Vazamento de Dados
    // =========================================================================

    @Test
    @DisplayName("Token inválido não resulta em 500")
    void invalid_token_returns_bad_request_not_500() throws Exception {
        mockMvc.perform(post("/api/public/q/{token}/participantes/{participanteId}/approve-by-token",
                        qr.getToken(), pendingMember.getId())
                        .header("X-Owner-Action-Token", "TOKEN-COMPLETAMENTE-INVALIDO-E-FALSO")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("OWNER_ACTION_TOKEN_INVALID"));
    }

    @Test
    @DisplayName("Resposta da API não expõe dados sensíveis (telefone completo ou tokenHash)")
    void response_does_not_leak_sensitive_data() throws Exception {
        MvcResult mvcResult = mockMvc.perform(post("/api/public/q/{token}/participantes/{participanteId}/approve-by-token",
                        qr.getToken(), pendingMember.getId())
                        .header("X-Owner-Action-Token", rawToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andReturn();

        String jsonResponse = mvcResult.getResponse().getContentAsString();
        assertThat(jsonResponse).doesNotContain("+244922222222"); // Membro completo
        assertThat(jsonResponse).doesNotContain("+244911111111"); // Owner completo
        assertThat(jsonResponse).doesNotContain("tokenHash");
        assertThat(jsonResponse).doesNotContain("rawToken");
        assertThat(jsonResponse).doesNotContain("testpepper");
    }
}

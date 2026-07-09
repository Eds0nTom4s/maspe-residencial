package com.restaurante.consumo;

import com.restaurante.consumo.identificacao.entity.ClienteConsumo;
import com.restaurante.consumo.identificacao.repository.ClienteConsumoRepository;
import com.restaurante.consumo.participante.entity.SessaoConsumoParticipante;
import com.restaurante.consumo.participante.repository.SessaoConsumoParticipanteRepository;
import com.restaurante.consumo.participante.service.SessaoOwnerActionTokenService;
import com.restaurante.model.entity.*;
import com.restaurante.model.enums.*;
import com.restaurante.repository.*;
import com.restaurante.testsupport.PostgresTestcontainersConfig;
import com.restaurante.testsupport.UniqueTestData;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        "spring.main.web-application-type=servlet",
        "consuma.sessao.owner-action-token.ttl-minutes=10",
        "consuma.sessao.owner-action-token.max-uses=5",
        "consuma.sessao.owner-action-token.hash-pepper=testpepper"
})
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("it-postgres")
@DisplayName("PublicOwnerTokenActionController — Integração de Ações via Token")
class PublicOwnerTokenActionControllerIT extends PostgresTestcontainersConfig {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    TenantRepository tenantRepository;
    @Autowired
    InstituicaoRepository instituicaoRepository;
    @Autowired
    UnidadeAtendimentoRepository unidadeAtendimentoRepository;
    @Autowired
    MesaRepository mesaRepository;
    @Autowired
    QrCodeOperacionalRepository qrCodeOperacionalRepository;
    @Autowired
    SessaoConsumoRepository sessaoConsumoRepository;
    @Autowired
    ClienteConsumoRepository clienteConsumoRepository;
    @Autowired
    SessaoConsumoParticipanteRepository participanteRepository;
    @Autowired
    SessaoOwnerActionTokenService ownerTokenService;

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
        String suffix = UniqueTestData.uniqueSuffix();
        String ownerPhone = UniqueTestData.uniqueTelefone();
        String memberPhone = UniqueTestData.uniqueTelefone();

        // 1. Setup Base
        tenant = new Tenant();
        tenant.setNome("Consuma Test Tenant " + suffix);
        tenant.setSlug(UniqueTestData.uniqueSlug("consuma-test-tenant"));
        tenant.setTenantCode(UniqueTestData.uniqueTenantCode("CTT"));
        tenant.setTipo(TenantTipo.RESTAURANTE);
        tenant.setEstado(TenantEstado.ATIVO);
        tenant = tenantRepository.saveAndFlush(tenant);

        instituicao = new Instituicao();
        instituicao.setTenant(tenant);
        instituicao.setNome("Inst Test " + suffix);
        instituicao.setSigla(UniqueTestData.uniqueInstituicaoSigla("IT"));
        instituicao.setNif(UniqueTestData.uniqueNif("NIF-TEST"));
        instituicao.setTelefoneAutorizacao(UniqueTestData.uniqueTelefone());
        instituicao.setAtiva(true);
        instituicao = instituicaoRepository.saveAndFlush(instituicao);

        unidade = new UnidadeAtendimento();
        unidade.setInstituicao(instituicao);
        unidade.setNome("Unidade 1");
        unidade.setTipo(TipoUnidadeAtendimento.RESTAURANTE);
        unidade.setAtiva(true);
        unidade = unidadeAtendimentoRepository.saveAndFlush(unidade);

        mesa = new Mesa();
        mesa.setTenant(tenant);
        mesa.setInstituicao(instituicao);
        mesa.setUnidadeAtendimento(unidade);
        mesa.setReferencia("Mesa " + suffix);
        mesa.setNumero(10);
        mesa.setQrCode(UniqueTestData.uniqueQrCode("owner-token"));
        mesa.setAtiva(true);
        mesa = mesaRepository.saveAndFlush(mesa);

        qr = new QrCodeOperacional();
        qr.setTenant(tenant);
        qr.setInstituicao(instituicao);
        qr.setUnidadeAtendimento(unidade);
        qr.setMesa(mesa);
        qr.setTipo(QrCodeOperacionalTipo.MESA);
        qr.setToken("qr-token-test-" + UniqueTestData.uniqueSuffix());
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
        ownerCliente.setTelefone(ownerPhone);
        ownerCliente.setTelefoneNormalizado(ownerPhone);
        ownerCliente.setStatus(ClienteConsumoStatus.ACTIVE);
        ownerCliente = clienteConsumoRepository.saveAndFlush(ownerCliente);

        owner = new SessaoConsumoParticipante();
        owner.setTenant(tenant);
        owner.setSessaoConsumo(sessao);
        owner.setClienteConsumo(ownerCliente);
        owner.setRole(SessaoParticipanteRole.OWNER);
        owner.setStatus(SessaoParticipanteStatus.ACTIVE);
        owner.setNomeExibicao("Dono");
        owner.setTelefoneNormalizado(ownerPhone);
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
        memberCliente.setTelefone(memberPhone);
        memberCliente.setTelefoneNormalizado(memberPhone);
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
        pendingMember.setTelefoneNormalizado(memberPhone);
        pendingMember = participanteRepository.saveAndFlush(pendingMember);
    }

    // =========================================================================
    // 3.1 Extração: Header vs Body
    // =========================================================================

    @Test
    @DisplayName("Aprovar participante usando o token via Header")
    void approve_using_token_in_header() throws Exception {
        mockMvc.perform(post("/public/q/{token}/participantes/{participanteId}/approve-by-token",
                qr.getToken(), pendingMember.getId())
                .header("X-Owner-Action-Token", rawToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Aprovado no header\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.approved").value(true));

        var updated = participanteRepository.findById(pendingMember.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(SessaoParticipanteStatus.ACTIVE);
    }

    @Test
    @DisplayName("Aprovar participante usando o token via Body")
    void approve_using_token_in_body() throws Exception {
        mockMvc.perform(post("/public/q/{token}/participantes/{participanteId}/approve-by-token",
                qr.getToken(), pendingMember.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ownerActionToken\":\"" + rawToken + "\", \"reason\":\"Aprovado no body\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.approved").value(true));
    }

    @Test
    @DisplayName("Prioriza Header quando ambos Header e Body são informados")
    void header_takes_precedence_over_body() throws Exception {
        mockMvc.perform(post("/public/q/{token}/participantes/{participanteId}/approve-by-token",
                qr.getToken(), pendingMember.getId())
                .header("X-Owner-Action-Token", rawToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ownerActionToken\":\"TOKEN-INVALIDO\", \"reason\":\"Prioridade\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.approved").value(true));
    }

    @Test
    @DisplayName("Erro se token não for enviado no header nem no body")
    void error_when_token_is_missing() throws Exception {
        mockMvc.perform(post("/public/q/{token}/participantes/{participanteId}/approve-by-token",
                qr.getToken(), pendingMember.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Sem token\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("OWNER_ACTION_TOKEN_REQUIRED"));
    }

    // =========================================================================
    // 3.2 Query Parameter Rejeitado
    // =========================================================================

    @Test
    @DisplayName("Rejeita requisição se o token for enviado via Query Param")
    void error_when_token_is_sent_in_query_param() throws Exception {
        mockMvc.perform(post(
                "/public/q/{token}/participantes/{participanteId}/approve-by-token?ownerActionToken=" + rawToken,
                qr.getToken(), pendingMember.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("OWNER_ACTION_TOKEN_QUERY_PARAM_NOT_ALLOWED"));
    }

    // =========================================================================
    // 3.3 Fluxo Feliz
    // =========================================================================

    @Test
    @DisplayName("Rejeitar participante com sucesso")
    void reject_participant_success() throws Exception {
        mockMvc.perform(post("/public/q/{token}/participantes/{participanteId}/reject-by-token",
                qr.getToken(), pendingMember.getId())
                .header("X-Owner-Action-Token", rawToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Muito barulhento\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejected").value(true));

        var updated = participanteRepository.findById(pendingMember.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(SessaoParticipanteStatus.REJECTED);
    }

    @Test
    @DisplayName("Cancelar participante com sucesso")
    void cancel_participant_success() throws Exception {
        mockMvc.perform(post("/public/q/{token}/participantes/{participanteId}/cancel-by-token",
                qr.getToken(), pendingMember.getId())
                .header("X-Owner-Action-Token", rawToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Desistiu\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.wasCancelled").value(true));

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

        mockMvc.perform(post("/public/q/{token}/participantes/{participanteId}/approve-by-token",
                qr.getToken(), pendingMember.getId())
                .header("X-Owner-Action-Token", rawToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("PARTICIPANT_ALREADY_ACTIVE"));
    }

    // =========================================================================
    // 3.5 Segurança e Vazamento de Dados
    // =========================================================================

    @Test
    @DisplayName("Token inválido não resulta em 500")
    void invalid_token_returns_bad_request_not_500() throws Exception {
        mockMvc.perform(post("/public/q/{token}/participantes/{participanteId}/approve-by-token",
                qr.getToken(), pendingMember.getId())
                .header("X-Owner-Action-Token", "TOKEN-COMPLETAMENTE-INVALIDO-E-FALSO")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("OWNER_ACTION_TOKEN_INVALID"));
    }

    @Test
    @DisplayName("Resposta da API não expõe dados sensíveis (telefone completo ou tokenHash)")
    void response_does_not_leak_sensitive_data() throws Exception {
        MvcResult mvcResult = mockMvc
                .perform(post("/public/q/{token}/participantes/{participanteId}/approve-by-token",
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

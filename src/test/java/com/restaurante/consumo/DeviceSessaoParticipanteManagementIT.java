package com.restaurante.consumo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.consumo.identificacao.entity.ClienteConsumo;
import com.restaurante.consumo.identificacao.repository.ClienteConsumoRepository;
import com.restaurante.consumo.participante.entity.SessaoConsumoParticipante;
import com.restaurante.consumo.participante.repository.SessaoConsumoParticipanteRepository;
import com.restaurante.consumo.identificacao.repository.TelefoneOtpChallengeRepository;
import com.restaurante.model.entity.DispositivoOperacional;
import com.restaurante.model.entity.Instituicao;
import com.restaurante.model.entity.SessaoConsumo;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.UnidadeAtendimento;
import com.restaurante.model.enums.ClienteConsumoStatus;
import com.restaurante.model.enums.DeviceCapability;
import com.restaurante.model.enums.DispositivoStatus;
import com.restaurante.model.enums.DispositivoTipo;
import com.restaurante.model.enums.SessaoParticipanteRole;
import com.restaurante.model.enums.SessaoParticipanteStatus;
import com.restaurante.model.enums.StatusSessaoConsumo;
import com.restaurante.model.enums.TipoSessao;
import com.restaurante.model.enums.TipoUnidadeAtendimento;
import com.restaurante.repository.DispositivoOperacionalRepository;
import com.restaurante.repository.InstituicaoRepository;
import com.restaurante.repository.SessaoConsumoRepository;
import com.restaurante.repository.TenantRepository;
import com.restaurante.repository.UnidadeAtendimentoRepository;
import com.restaurante.security.device.DevicePrincipal;
import com.restaurante.testsupport.PostgresTestcontainersConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.main.web-application-type=servlet",
                "consuma.otp.enabled=true",
                "consuma.otp.mock-enabled=true",
                "consuma.otp.hash-pepper=testpepper"
        }
)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("it-postgres")
class DeviceSessaoParticipanteManagementIT extends PostgresTestcontainersConfig {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired TenantRepository tenantRepository;
    @Autowired InstituicaoRepository instituicaoRepository;
    @Autowired UnidadeAtendimentoRepository unidadeAtendimentoRepository;
    @Autowired SessaoConsumoRepository sessaoConsumoRepository;
    @Autowired DispositivoOperacionalRepository dispositivoOperacionalRepository;
    @Autowired TelefoneOtpChallengeRepository challengeRepository;
    @Autowired ClienteConsumoRepository clienteConsumoRepository;
    @Autowired SessaoConsumoParticipanteRepository participanteRepository;

    @Test
    void device_requires_capability_to_list_participants() throws Exception {
        Tenant tenant = criarTenant("Tenant P", "tenant-p", "TP");
        Instituicao inst = criarInstituicao(tenant, "Inst P", "IP", "NIF-IP-001", "+244900002001");
        UnidadeAtendimento ua = criarUnidade(inst, "UA P", TipoUnidadeAtendimento.RESTAURANTE);

        SessaoConsumo sessao = criarSessaoAberta(tenant, inst, ua);
        DispositivoOperacional disp = criarDevicePos(tenant, inst, ua);

        DevicePrincipal deviceNoCaps = new DevicePrincipal(
                disp.getId(), disp.getCodigo(),
                tenant.getId(), tenant.getTenantCode(),
                inst.getId(), ua.getId(), null,
                DispositivoTipo.POS, DispositivoStatus.ATIVO,
                List.of(), 1
        );
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                deviceNoCaps, "N/A", List.of(new SimpleGrantedAuthority("ROLE_DEVICE"))
        );

        mockMvc.perform(get("/device/sessoes-consumo/{sessaoId}/participantes", sessao.getId())
                        .with(authentication(auth)))
                .andExpect(status().isForbidden());
    }

    @Test
    void pos_can_request_and_verify_otp_to_add_participant_as_member() throws Exception {
        Tenant tenant = criarTenant("Tenant P2", "tenant-p2", "TP2");
        Instituicao inst = criarInstituicao(tenant, "Inst P2", "IP2", "NIF-IP2-001", "+244900002002");
        UnidadeAtendimento ua = criarUnidade(inst, "UA P2", TipoUnidadeAtendimento.RESTAURANTE);
        SessaoConsumo sessao = criarSessaoAberta(tenant, inst, ua);

        DispositivoOperacional disp = criarDevicePos(tenant, inst, ua);
        DevicePrincipal device = new DevicePrincipal(
                disp.getId(), disp.getCodigo(),
                tenant.getId(), tenant.getTenantCode(),
                inst.getId(), ua.getId(), null,
                DispositivoTipo.POS, DispositivoStatus.ATIVO,
                List.of(DeviceCapability.ADD_SESSION_PARTICIPANT),
                1
        );
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                device, "N/A", List.of(new SimpleGrantedAuthority("ROLE_DEVICE"))
        );

        String reqBody = "{\"telefone\":\"923000000\",\"nomeExibicao\":\"Ana\"}";
        String reqResp = mockMvc.perform(post("/device/sessoes-consumo/{sessaoId}/participantes/otp/request", sessao.getId())
                        .with(authentication(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reqBody))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode reqData = objectMapper.readTree(reqResp).at("/data");
        long challengeId = reqData.at("/challengeId").asLong();
        String debugOtp = reqData.at("/debugOtp").asText();
        assertThat(challengeId).isPositive();
        assertThat(debugOtp).isNotBlank();

        String verifyBody = "{\"challengeId\":" + challengeId + ",\"telefone\":\"923000000\",\"otp\":\"" + debugOtp + "\",\"nomeExibicao\":\"Ana\"}";
        String verifyResp = mockMvc.perform(post("/device/sessoes-consumo/{sessaoId}/participantes/otp/verify", sessao.getId())
                        .with(authentication(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(verifyBody))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode verifyData = objectMapper.readTree(verifyResp).at("/data");
        assertThat(verifyData.at("/sessaoConsumoId").asLong()).isEqualTo(sessao.getId());
        assertThat(verifyData.at("/role").asText()).isEqualTo("MEMBER");

        long participanteId = verifyData.at("/participanteId").asLong();
        SessaoConsumoParticipante p = participanteRepository.findByTenant_IdAndId(tenant.getId(), participanteId).orElseThrow();
        assertThat(p.getStatus()).isEqualTo(SessaoParticipanteStatus.ACTIVE);
        assertThat(p.getRole()).isEqualTo(SessaoParticipanteRole.MEMBER);
    }

    @Test
    void pos_cannot_remove_or_demote_or_block_last_owner() throws Exception {
        Tenant tenant = criarTenant("Tenant P3", "tenant-p3", "TP3");
        Instituicao inst = criarInstituicao(tenant, "Inst P3", "IP3", "NIF-IP3-001", "+244900002003");
        UnidadeAtendimento ua = criarUnidade(inst, "UA P3", TipoUnidadeAtendimento.RESTAURANTE);
        SessaoConsumo sessao = criarSessaoAberta(tenant, inst, ua);

        ClienteConsumo owner = criarCliente(tenant, "923111111", "+244923111111");
        ClienteConsumo member = criarCliente(tenant, "923222222", "+244923222222");

        SessaoConsumoParticipante pOwner = criarParticipante(tenant, sessao, owner, SessaoParticipanteRole.OWNER);
        SessaoConsumoParticipante pMember = criarParticipante(tenant, sessao, member, SessaoParticipanteRole.MEMBER);

        DispositivoOperacional disp = criarDevicePos(tenant, inst, ua);
        DevicePrincipal device = new DevicePrincipal(
                disp.getId(), disp.getCodigo(),
                tenant.getId(), tenant.getTenantCode(),
                inst.getId(), ua.getId(), null,
                DispositivoTipo.POS, DispositivoStatus.ATIVO,
                List.of(
                        DeviceCapability.REMOVE_SESSION_PARTICIPANT,
                        DeviceCapability.PROMOTE_SESSION_PARTICIPANT,
                        DeviceCapability.DEMOTE_SESSION_PARTICIPANT,
                        DeviceCapability.BLOCK_SESSION_PARTICIPANT
                ),
                1
        );
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                device, "N/A", List.of(new SimpleGrantedAuthority("ROLE_DEVICE"))
        );

        // cannot remove last owner
        mockMvc.perform(post("/device/sessoes-consumo/{sessaoId}/participantes/{pid}/remove", sessao.getId(), pOwner.getId())
                        .with(authentication(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"test\"}"))
                .andExpect(status().isBadRequest());

        // promote member to owner -> now can remove old owner
        mockMvc.perform(post("/device/sessoes-consumo/{sessaoId}/participantes/{pid}/promote", sessao.getId(), pMember.getId())
                        .with(authentication(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetRole\":\"OWNER\",\"reason\":\"shift\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/device/sessoes-consumo/{sessaoId}/participantes/{pid}/remove", sessao.getId(), pOwner.getId())
                        .with(authentication(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"test\"}"))
                .andExpect(status().isOk());

        // now only one owner remains (the promoted member) -> cannot demote or block
        mockMvc.perform(post("/device/sessoes-consumo/{sessaoId}/participantes/{pid}/demote", sessao.getId(), pMember.getId())
                        .with(authentication(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetRole\":\"MEMBER\",\"reason\":\"shift\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/device/sessoes-consumo/{sessaoId}/participantes/{pid}/block", sessao.getId(), pMember.getId())
                        .with(authentication(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"abuse\"}"))
                .andExpect(status().isBadRequest());
    }

    private Tenant criarTenant(String nome, String code, String prefix) {
        Tenant t = new Tenant();
        t.setNome(nome);
        t.setSlug(code);
        t.setTenantCode(prefix);
        t.setTipo(com.restaurante.model.enums.TenantTipo.RESTAURANTE);
        t.setEstado(com.restaurante.model.enums.TenantEstado.ATIVO);
        return tenantRepository.saveAndFlush(t);
    }

    private Instituicao criarInstituicao(Tenant tenant, String nome, String sigla, String nif, String telefone) {
        Instituicao i = new Instituicao();
        i.setTenant(tenant);
        i.setNome(nome);
        i.setSigla(sigla);
        i.setNif(nif);
        i.setTelefoneAutorizacao(telefone);
        i.setAtiva(true);
        return instituicaoRepository.saveAndFlush(i);
    }

    private UnidadeAtendimento criarUnidade(Instituicao inst, String nome, TipoUnidadeAtendimento tipo) {
        UnidadeAtendimento ua = new UnidadeAtendimento();
        ua.setInstituicao(inst);
        ua.setNome(nome);
        ua.setTipo(tipo);
        ua.setAtiva(true);
        return unidadeAtendimentoRepository.saveAndFlush(ua);
    }

    private SessaoConsumo criarSessaoAberta(Tenant tenant, Instituicao inst, UnidadeAtendimento ua) {
        SessaoConsumo s = new SessaoConsumo();
        s.setTenant(tenant);
        s.setInstituicao(inst);
        s.setUnidadeAtendimento(ua);
        s.setModoAnonimo(true);
        s.setStatus(StatusSessaoConsumo.ABERTA);
        s.setTipoSessao(TipoSessao.PRE_PAGO);
        return sessaoConsumoRepository.saveAndFlush(s);
    }

    private DispositivoOperacional criarDevicePos(Tenant tenant, Instituicao inst, UnidadeAtendimento ua) {
        DispositivoOperacional d = new DispositivoOperacional();
        d.setTenant(tenant);
        d.setInstituicao(inst);
        d.setUnidadeAtendimento(ua);
        d.setCodigo("POS-TEST-" + System.nanoTime());
        d.setNome("POS Test");
        d.setTipo(DispositivoTipo.POS);
        d.setStatus(DispositivoStatus.ATIVO);
        d.setOperationalDeviceType(com.restaurante.model.enums.OperationalDeviceType.POS_CAIXA);
        d.setTokenVersion(1);
        return dispositivoOperacionalRepository.saveAndFlush(d);
    }

    private ClienteConsumo criarCliente(Tenant tenant, String telefone, String normalizado) {
        ClienteConsumo c = new ClienteConsumo();
        c.setTenant(tenant);
        c.setTelefone(telefone);
        c.setTelefoneNormalizado(normalizado);
        c.setStatus(ClienteConsumoStatus.ACTIVE);
        c.setTelefoneVerificado(true);
        return clienteConsumoRepository.saveAndFlush(c);
    }

    private SessaoConsumoParticipante criarParticipante(Tenant tenant, SessaoConsumo sessao, ClienteConsumo cliente, SessaoParticipanteRole role) {
        SessaoConsumoParticipante p = new SessaoConsumoParticipante();
        p.setTenant(tenant);
        p.setSessaoConsumo(sessao);
        p.setClienteConsumo(cliente);
        p.setTelefoneNormalizado(cliente.getTelefoneNormalizado());
        p.setRole(role);
        p.setStatus(SessaoParticipanteStatus.ACTIVE);
        p.setJoinedAt(java.time.Instant.now());
        return participanteRepository.saveAndFlush(p);
    }
}

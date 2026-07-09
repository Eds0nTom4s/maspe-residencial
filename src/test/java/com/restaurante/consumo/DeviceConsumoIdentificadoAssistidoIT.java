package com.restaurante.consumo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.consumo.identificacao.repository.TelefoneOtpChallengeRepository;
import com.restaurante.model.entity.DispositivoOperacional;
import com.restaurante.model.entity.Instituicao;
import com.restaurante.model.entity.SessaoConsumo;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.UnidadeAtendimento;
import com.restaurante.model.enums.*;
import com.restaurante.repository.DispositivoOperacionalRepository;
import com.restaurante.repository.InstituicaoRepository;
import com.restaurante.repository.SessaoConsumoRepository;
import com.restaurante.repository.TenantRepository;
import com.restaurante.repository.UnidadeAtendimentoRepository;
import com.restaurante.security.device.DevicePrincipal;
import com.restaurante.testsupport.PostgresTestcontainersConfig;
import com.restaurante.testsupport.UniqueTestData;
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
@AutoConfigureMockMvc
@ActiveProfiles("it-postgres")
class DeviceConsumoIdentificadoAssistidoIT extends PostgresTestcontainersConfig {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired TenantRepository tenantRepository;
    @Autowired InstituicaoRepository instituicaoRepository;
    @Autowired UnidadeAtendimentoRepository unidadeAtendimentoRepository;
    @Autowired SessaoConsumoRepository sessaoConsumoRepository;
    @Autowired DispositivoOperacionalRepository dispositivoOperacionalRepository;
    @Autowired TelefoneOtpChallengeRepository challengeRepository;

    @Test
    void device_can_request_and_verify_otp_to_link_specific_session() throws Exception {
        Tenant tenant = criarTenant("Tenant Assist", "tenant-assist", "TAS");
        Instituicao inst = criarInstituicao(tenant, "Inst Assist", "IA", "NIF-IA-001", "+244900002001");
        UnidadeAtendimento ua = criarUnidade(inst, "UA Assist", TipoUnidadeAtendimento.RESTAURANTE);

        SessaoConsumo sessao = new SessaoConsumo();
        sessao.setTenant(tenant);
        sessao.setInstituicao(inst);
        sessao.setUnidadeAtendimento(ua);
        sessao.setModoAnonimo(true);
        sessao.setStatus(StatusSessaoConsumo.ABERTA);
        sessao.setTipoSessao(TipoSessao.PRE_PAGO);
        sessao = sessaoConsumoRepository.saveAndFlush(sessao);

        DispositivoOperacional disp = criarDevicePos(tenant, inst, ua);
        DevicePrincipal device = new DevicePrincipal(
                disp.getId(),
                disp.getCodigo(),
                tenant.getId(),
                tenant.getTenantCode(),
                inst.getId(),
                ua.getId(),
                null,
                DispositivoTipo.POS,
                DispositivoStatus.ATIVO,
                List.of(
                        DeviceCapability.LOOKUP_CONSUMPTION_BY_PHONE,
                        DeviceCapability.REQUEST_ASSISTED_IDENTIFICATION_OTP,
                        DeviceCapability.VERIFY_ASSISTED_IDENTIFICATION_OTP,
                        DeviceCapability.LINK_CUSTOMER_TO_SESSION
                ),
                1
        );
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                device, "N/A", List.of(new SimpleGrantedAuthority("ROLE_DEVICE"))
        );

        String reqBody = "{\"telefone\":\"923000000\"}";
        String reqResp = mockMvc.perform(post("/device/sessoes-consumo/{sessaoId}/identificacao/otp/request", sessao.getId())
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

        // verify should link session and consume challenge
        String verifyBody = "{\"challengeId\":" + challengeId + ",\"telefone\":\"923000000\",\"otp\":\"" + debugOtp + "\"}";
        String verifyResp = mockMvc.perform(post("/device/sessoes-consumo/{sessaoId}/identificacao/otp/verify", sessao.getId())
                        .with(authentication(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(verifyBody))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode verifyData = objectMapper.readTree(verifyResp).at("/data");
        assertThat(verifyData.at("/sessaoConsumoId").asLong()).isEqualTo(sessao.getId());
        assertThat(verifyData.at("/clienteConsumoId").asLong()).isPositive();
        assertThat(verifyData.at("/statusMensagem").asText()).isEqualTo("IDENTIFICADA");

        SessaoConsumo updated = sessaoConsumoRepository.findByIdAndTenantId(sessao.getId(), tenant.getId()).orElseThrow();
        assertThat(updated.getClienteConsumo()).isNotNull();
        assertThat(updated.getTelefoneIdentificado()).isEqualTo("+244923000000");
        assertThat(updated.isIdentificadoPorOtp()).isTrue();
        assertThat(updated.getIdentificacaoStatus()).isEqualTo(SessaoIdentificacaoStatus.IDENTIFICADA);

        var ch = challengeRepository.findByIdAndTenant_Id(challengeId, tenant.getId()).orElseThrow();
        assertThat(ch.getStatus()).isEqualTo(OtpStatus.CONSUMED);
        assertThat(ch.getPurpose()).isEqualTo(OtpPurpose.POS_VINCULAR_SESSAO);
        assertThat(ch.getSessaoConsumo().getId()).isEqualTo(sessao.getId());
    }

    private Tenant criarTenant(String nome, String slug, String tenantCode) {
        Tenant t = new Tenant();
        t.setNome(nome);
        t.setSlug(UniqueTestData.uniqueSlug(slug));
        t.setTenantCode(UniqueTestData.uniqueTenantCode(tenantCode));
        t.setTipo(TenantTipo.RESTAURANTE);
        t.setEstado(TenantEstado.ATIVO);
        return tenantRepository.saveAndFlush(t);
    }

    private Instituicao criarInstituicao(Tenant tenant, String nome, String sigla, String nif, String telefoneAutorizacao) {
        Instituicao i = new Instituicao();
        i.setTenant(tenant);
        i.setNome(nome);
        i.setSigla(UniqueTestData.uniqueInstituicaoSigla(sigla));
        i.setNif(UniqueTestData.uniqueNif(nif));
        i.setTelefoneAutorizacao(UniqueTestData.uniqueTelefone());
        i.setAtiva(true);
        return instituicaoRepository.saveAndFlush(i);
    }

    private UnidadeAtendimento criarUnidade(Instituicao instituicao, String nome, TipoUnidadeAtendimento tipo) {
        UnidadeAtendimento u = new UnidadeAtendimento();
        u.setNome(nome);
        u.setTipo(tipo);
        u.setAtiva(true);
        u.setInstituicao(instituicao);
        return unidadeAtendimentoRepository.saveAndFlush(u);
    }

    private DispositivoOperacional criarDevicePos(Tenant tenant, Instituicao inst, UnidadeAtendimento ua) {
        DispositivoOperacional d = new DispositivoOperacional();
        d.setTenant(tenant);
        d.setInstituicao(inst);
        d.setUnidadeAtendimento(ua);
        d.setCodigo(UniqueTestData.uniqueDeviceCode("POS-ASSIST"));
        d.setNome("POS Assist");
        d.setTipo(DispositivoTipo.POS);
        d.setStatus(DispositivoStatus.ATIVO);
        d.setTokenVersion(1);
        return dispositivoOperacionalRepository.saveAndFlush(d);
    }
}

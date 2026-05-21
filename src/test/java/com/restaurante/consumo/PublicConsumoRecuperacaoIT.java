package com.restaurante.consumo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.model.entity.Instituicao;
import com.restaurante.model.entity.Mesa;
import com.restaurante.model.entity.QrCodeOperacional;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.UnidadeAtendimento;
import com.restaurante.model.enums.QrCodeOperacionalTipo;
import com.restaurante.model.enums.TenantEstado;
import com.restaurante.model.enums.TenantTipo;
import com.restaurante.model.enums.TipoUnidadeAtendimento;
import com.restaurante.repository.InstituicaoRepository;
import com.restaurante.repository.MesaRepository;
import com.restaurante.repository.TenantRepository;
import com.restaurante.repository.UnidadeAtendimentoRepository;
import com.restaurante.service.QrCodeOperacionalService;
import com.restaurante.testsupport.PostgresTestcontainersConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "consuma.otp.enabled=true",
                "consuma.otp.mock-enabled=true",
                "consuma.otp.hash-pepper=testpepper"
        }
)
@ActiveProfiles("it-postgres")
class PublicConsumoRecuperacaoIT extends PostgresTestcontainersConfig {

    @Autowired TestRestTemplate restTemplate;
    @Autowired ObjectMapper objectMapper;
    @Autowired TenantRepository tenantRepository;
    @Autowired InstituicaoRepository instituicaoRepository;
    @Autowired UnidadeAtendimentoRepository unidadeAtendimentoRepository;
    @Autowired MesaRepository mesaRepository;
    @Autowired QrCodeOperacionalService qrCodeOperacionalService;

    @Test
    void recovery_lists_open_sessions_for_phone_in_same_tenant() throws Exception {
        Tenant tenant = criarTenant("Tenant Rec", "tenant-rec", "TRE");
        Instituicao inst = criarInstituicao(tenant, "Inst Rec", "IR", "NIF-IR-001", "+244900002001");
        UnidadeAtendimento ua = criarUnidade(inst, "UA Rec", TipoUnidadeAtendimento.RESTAURANTE);
        Mesa mesa = criarMesa(inst, ua, "Mesa 1", 1, "M1");

        QrCodeOperacional qr = qrCodeOperacionalService.criarQr(
                tenant.getId(), inst.getId(), ua.getId(), mesa.getId(), QrCodeOperacionalTipo.MESA, "QR Mesa"
        );

        // primeiro identifica sessão (reaproveita endpoints)
        String reqIdentify = "{\"telefone\":\"923000000\"}";
        JsonNode chall = objectMapper.readTree(restTemplate.postForEntity(
                "/api/public/q/{token}/identificacao/otp/request",
                json(reqIdentify),
                String.class,
                qr.getToken()
        ).getBody()).at("/data");
        String otp = chall.at("/debugOtp").asText();
        long cid = chall.at("/challengeId").asLong();
        String verifyIdentify = "{\"challengeId\":%d,\"telefone\":\"923000000\",\"otp\":\"%s\"}".formatted(cid, otp);
        restTemplate.postForEntity("/api/public/q/{token}/identificacao/otp/verify", json(verifyIdentify), String.class, qr.getToken());

        // agora recovery
        JsonNode challRec = objectMapper.readTree(restTemplate.postForEntity(
                "/api/public/q/{token}/recuperar/otp/request",
                json(reqIdentify),
                String.class,
                qr.getToken()
        ).getBody()).at("/data");
        long recChallengeId = challRec.at("/challengeId").asLong();
        String recOtp = challRec.at("/debugOtp").asText();

        String verifyRec = "{\"challengeId\":%d,\"telefone\":\"923000000\",\"otp\":\"%s\"}".formatted(recChallengeId, recOtp);
        ResponseEntity<String> verifyResp = restTemplate.postForEntity(
                "/api/public/q/{token}/recuperar/otp/verify",
                json(verifyRec),
                String.class,
                qr.getToken()
        );
        assertThat(verifyResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode data = objectMapper.readTree(verifyResp.getBody()).at("/data/sessoesAtivas");
        assertThat(data.isArray()).isTrue();
        assertThat(data.size()).isGreaterThanOrEqualTo(1);
    }

    private HttpEntity<String> json(String payload) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(payload, headers);
    }

    private Tenant criarTenant(String nome, String slug, String tenantCode) {
        Tenant t = new Tenant();
        t.setNome(nome);
        t.setSlug(slug);
        t.setTenantCode(tenantCode);
        t.setTipo(TenantTipo.RESTAURANTE);
        t.setEstado(TenantEstado.ATIVO);
        return tenantRepository.saveAndFlush(t);
    }

    private Instituicao criarInstituicao(Tenant tenant, String nome, String sigla, String nif, String telefoneAutorizacao) {
        Instituicao i = new Instituicao();
        i.setTenant(tenant);
        i.setNome(nome);
        i.setSigla(sigla);
        i.setNif(nif);
        i.setTelefoneAutorizacao(telefoneAutorizacao);
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

    private Mesa criarMesa(Instituicao inst, UnidadeAtendimento ua, String ref, int numero, String qrLegado) {
        Mesa m = new Mesa();
        m.setTenant(inst.getTenant());
        m.setInstituicao(inst);
        m.setUnidadeAtendimento(ua);
        m.setReferencia(ref);
        m.setNumero(numero);
        m.setAtiva(true);
        return mesaRepository.saveAndFlush(m);
    }
}

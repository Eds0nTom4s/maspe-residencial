package com.restaurante.consumo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.model.entity.Instituicao;
import com.restaurante.model.entity.Mesa;
import com.restaurante.model.entity.QrCodeOperacional;
import com.restaurante.model.entity.SessaoConsumo;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.UnidadeAtendimento;
import com.restaurante.model.enums.QrCodeOperacionalTipo;
import com.restaurante.model.enums.StatusSessaoConsumo;
import com.restaurante.model.enums.TenantEstado;
import com.restaurante.model.enums.TenantTipo;
import com.restaurante.model.enums.TipoUnidadeAtendimento;
import com.restaurante.repository.InstituicaoRepository;
import com.restaurante.repository.MesaRepository;
import com.restaurante.repository.SessaoConsumoRepository;
import com.restaurante.repository.TenantRepository;
import com.restaurante.repository.UnidadeAtendimentoRepository;
import com.restaurante.service.QrCodeOperacionalService;
import com.restaurante.testsupport.PostgresTestcontainersConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.main.web-application-type=servlet",
                "consuma.otp.enabled=true",
                "consuma.otp.mock-enabled=true",
                "consuma.otp.hash-pepper=testpepper"
        }
)
@ActiveProfiles("it-postgres")
class PublicConsumoIdentificadoIT extends PostgresTestcontainersConfig {

    @Autowired TestRestTemplate restTemplate;
    @Autowired ObjectMapper objectMapper;
    @Autowired TenantRepository tenantRepository;
    @Autowired InstituicaoRepository instituicaoRepository;
    @Autowired UnidadeAtendimentoRepository unidadeAtendimentoRepository;
    @Autowired MesaRepository mesaRepository;
    @Autowired SessaoConsumoRepository sessaoConsumoRepository;
    @Autowired QrCodeOperacionalService qrCodeOperacionalService;

    @Test
    void request_and_verify_identify_otp_links_clienteConsumo_into_sessao() throws Exception {
        Tenant tenant = criarTenant("Tenant Ident", "tenant-ident", "TID");
        Instituicao inst = criarInstituicao(tenant, "Inst Ident", "II", "NIF-II-001", "+244900002001");
        UnidadeAtendimento ua = criarUnidade(inst, "UA Ident", TipoUnidadeAtendimento.RESTAURANTE);
        Mesa mesa = criarMesa(inst, ua, "Mesa 1", 1, "M1");

        QrCodeOperacional qr = qrCodeOperacionalService.criarQr(
                tenant.getId(), inst.getId(), ua.getId(), mesa.getId(), QrCodeOperacionalTipo.MESA, "QR Mesa"
        );

        // request OTP
        String reqPayload = """
                {"telefone":"923000000","purpose":"IDENTIFICAR_SESSAO"}
                """;
        ResponseEntity<String> reqResp = restTemplate.postForEntity(
                "/public/q/{token}/identificacao/otp/request",
                json(reqPayload),
                String.class,
                qr.getToken()
        );
        assertThat(reqResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode reqJson = objectMapper.readTree(reqResp.getBody());
        long challengeId = reqJson.at("/data/challengeId").asLong();
        String debugOtp = reqJson.at("/data/debugOtp").asText();
        assertThat(challengeId).isPositive();
        assertThat(debugOtp).isNotBlank();
        assertThat(reqJson.at("/data/otpLength").asInt()).isEqualTo(4);
        assertThat(debugOtp).hasSize(reqJson.at("/data/otpLength").asInt());

        // verify OTP
        String verifyPayload = """
                {"challengeId":%d,"telefone":"923000000","otp":"%s"}
                """.formatted(challengeId, debugOtp);
        ResponseEntity<String> verifyResp = restTemplate.postForEntity(
                "/public/q/{token}/identificacao/otp/verify",
                json(verifyPayload),
                String.class,
                qr.getToken()
        );
        assertThat(verifyResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode verifyJson = objectMapper.readTree(verifyResp.getBody());
        long sessaoId = verifyJson.at("/data/sessaoConsumoId").asLong();
        long clienteConsumoId = verifyJson.at("/data/clienteConsumoId").asLong();
        assertThat(sessaoId).isPositive();
        assertThat(clienteConsumoId).isPositive();

        SessaoConsumo sessao = sessaoConsumoRepository.findByIdAndTenantId(sessaoId, tenant.getId()).orElseThrow();
        assertThat(sessao.getStatus()).isEqualTo(StatusSessaoConsumo.ABERTA);
        assertThat(sessao.getClienteConsumo()).isNotNull();
        assertThat(sessao.getClienteConsumo().getId()).isEqualTo(clienteConsumoId);
        assertThat(sessao.getTelefoneIdentificado()).isEqualTo("+244923000000");
        assertThat(sessao.isIdentificadoPorOtp()).isTrue();
        assertThat(sessao.getIdentificacaoStatus()).isNotNull();
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

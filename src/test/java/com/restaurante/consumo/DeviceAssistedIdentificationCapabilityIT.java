package com.restaurante.consumo;

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
class DeviceAssistedIdentificationCapabilityIT extends PostgresTestcontainersConfig {

    @Autowired MockMvc mockMvc;
    @Autowired TenantRepository tenantRepository;
    @Autowired InstituicaoRepository instituicaoRepository;
    @Autowired UnidadeAtendimentoRepository unidadeAtendimentoRepository;
    @Autowired SessaoConsumoRepository sessaoConsumoRepository;
    @Autowired DispositivoOperacionalRepository dispositivoOperacionalRepository;

    @Test
    void device_without_request_capability_cannot_request_assisted_otp() throws Exception {
        Tenant tenant = criarTenant("Tenant Cap", "tenant-cap", "TCAP");
        Instituicao inst = criarInstituicao(tenant, "Inst Cap", "IC", "NIF-IC-001", "+244900002001");
        UnidadeAtendimento ua = criarUnidade(inst, "UA Cap", TipoUnidadeAtendimento.RESTAURANTE);

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
                disp.getId(), disp.getCodigo(),
                tenant.getId(), tenant.getTenantCode(),
                inst.getId(), ua.getId(), null,
                DispositivoTipo.POS, DispositivoStatus.ATIVO,
                List.of(DeviceCapability.LOOKUP_CONSUMPTION_BY_PHONE), // missing REQUEST_ASSISTED_IDENTIFICATION_OTP
                1
        );
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                device, "N/A", List.of(new SimpleGrantedAuthority("ROLE_DEVICE"))
        );

        mockMvc.perform(post("/device/sessoes-consumo/{sessaoId}/identificacao/otp/request", sessao.getId())
                        .with(authentication(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"telefone\":\"923000000\"}"))
                .andExpect(status().isForbidden());
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

    private DispositivoOperacional criarDevicePos(Tenant tenant, Instituicao inst, UnidadeAtendimento ua) {
        DispositivoOperacional d = new DispositivoOperacional();
        d.setTenant(tenant);
        d.setInstituicao(inst);
        d.setUnidadeAtendimento(ua);
        d.setCodigo("POS-CAP-" + System.nanoTime());
        d.setNome("POS Cap");
        d.setTipo(DispositivoTipo.POS);
        d.setOperationalDeviceType(OperationalDeviceType.POS_CAIXA);
        d.setStatus(DispositivoStatus.ATIVO);
        d.setTokenVersion(1);
        return dispositivoOperacionalRepository.saveAndFlush(d);
    }
}


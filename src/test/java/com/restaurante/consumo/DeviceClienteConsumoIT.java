package com.restaurante.consumo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.model.entity.Instituicao;
import com.restaurante.model.entity.SessaoConsumo;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.UnidadeAtendimento;
import com.restaurante.model.enums.*;
import com.restaurante.repository.InstituicaoRepository;
import com.restaurante.repository.SessaoConsumoRepository;
import com.restaurante.repository.TenantRepository;
import com.restaurante.repository.UnidadeAtendimentoRepository;
import com.restaurante.model.entity.DispositivoOperacional;
import com.restaurante.model.entity.FundoConsumo;
import com.restaurante.repository.FundoConsumoRepository;
import com.restaurante.repository.DispositivoOperacionalRepository;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "spring.main.web-application-type=servlet"
)
@AutoConfigureMockMvc
@ActiveProfiles("it-postgres")
class DeviceClienteConsumoIT extends PostgresTestcontainersConfig {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired TenantRepository tenantRepository;
    @Autowired InstituicaoRepository instituicaoRepository;
    @Autowired UnidadeAtendimentoRepository unidadeAtendimentoRepository;
    @Autowired SessaoConsumoRepository sessaoConsumoRepository;
    @Autowired DispositivoOperacionalRepository dispositivoOperacionalRepository;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;
    @Autowired FundoConsumoRepository fundoConsumoRepository;

    @Test
    void device_can_list_open_sessions_by_phone_inside_own_unidade() throws Exception {
        final Tenant[] tenantArr = new Tenant[1];
        final Instituicao[] instArr = new Instituicao[1];
        final UnidadeAtendimento[] uaArr = new UnidadeAtendimento[1];
        final SessaoConsumo[] sArr = new SessaoConsumo[1];
        final DispositivoOperacional[] dispArr = new DispositivoOperacional[1];

        new org.springframework.transaction.support.TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            tenantArr[0] = criarTenant("Tenant Dev", "tenant-dev", "TDV");
            instArr[0] = criarInstituicao(tenantArr[0], "Inst Dev", "ID", "NIF-ID-001", "+244900002001");
            uaArr[0] = criarUnidade(instArr[0], "UA Dev", TipoUnidadeAtendimento.RESTAURANTE);

            SessaoConsumo s = new SessaoConsumo();
            s.setTenant(tenantArr[0]);
            s.setInstituicao(instArr[0]);
            s.setUnidadeAtendimento(uaArr[0]);
            s.setModoAnonimo(true);
            s.setStatus(StatusSessaoConsumo.ABERTA);
            s.setTipoSessao(TipoSessao.PRE_PAGO);
            s.setTelefoneIdentificado("+244923000000");
            s.setIdentificacaoStatus(SessaoIdentificacaoStatus.IDENTIFICADA);
            s.setQrCodeSessao("QR-TEST-123");
            sArr[0] = sessaoConsumoRepository.saveAndFlush(s);

            FundoConsumo fundo = FundoConsumo.builder()
                    .sessaoConsumo(sArr[0])
                    .saldoAtual(java.math.BigDecimal.ZERO)
                    .ativo(true)
                    .build();
            fundoConsumoRepository.saveAndFlush(fundo);

            dispArr[0] = criarDevicePos(tenantArr[0], instArr[0], uaArr[0]);
        });

        Tenant tenant = tenantArr[0];
        Instituicao inst = instArr[0];
        UnidadeAtendimento ua = uaArr[0];
        SessaoConsumo s = sArr[0];
        DispositivoOperacional disp = dispArr[0];

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
                List.of(DeviceCapability.LOOKUP_CONSUMPTION_BY_PHONE),
                1
        );
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                device, "N/A", List.of(new SimpleGrantedAuthority("ROLE_DEVICE"))
        );

        String resp = mockMvc.perform(get("/device/sessoes-consumo/por-telefone")
                        .with(authentication(auth))
                        .param("telefone", "923000000")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode data = objectMapper.readTree(resp).at("/data/sessoesAtivas");
        assertThat(data.isArray()).isTrue();
        assertThat(data.size()).isEqualTo(1);
        assertThat(data.get(0).at("/sessaoId").asLong()).isEqualTo(s.getId());
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
        d.setNome("POS Dev");
        d.setCodigo("POS-DEV-" + System.nanoTime());
        d.setTipo(DispositivoTipo.POS);
        d.setStatus(DispositivoStatus.ATIVO);
        return dispositivoOperacionalRepository.saveAndFlush(d);
    }
}

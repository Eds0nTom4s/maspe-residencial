package com.restaurante.provisioning;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.model.entity.CategoriaProduto;
import com.restaurante.model.entity.Instituicao;
import com.restaurante.model.entity.QrCodeOperacional;
import com.restaurante.model.entity.Subscricao;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.TenantUser;
import com.restaurante.model.entity.UnidadeAtendimento;
import com.restaurante.model.entity.User;
import com.restaurante.model.enums.TenantEstado;
import com.restaurante.model.enums.TenantTipo;
import com.restaurante.repository.CategoriaProdutoRepository;
import com.restaurante.repository.InstituicaoRepository;
import com.restaurante.repository.QrCodeOperacionalRepository;
import com.restaurante.repository.SubscricaoRepository;
import com.restaurante.repository.TenantRepository;
import com.restaurante.repository.TenantUserRepository;
import com.restaurante.repository.UnidadeAtendimentoRepository;
import com.restaurante.repository.UserRepository;
import com.restaurante.security.tenant.TenantContext;
import com.restaurante.security.tenant.TenantContextHolder;
import com.restaurante.security.tenant.TenantResolutionSource;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "spring.main.web-application-type=servlet"
)
@AutoConfigureMockMvc
@ActiveProfiles("it-postgres")
@org.springframework.transaction.annotation.Transactional
class PlatformTenantProvisioningControllerIT extends PostgresTestcontainersConfig {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @Autowired TenantRepository tenantRepository;
    @Autowired SubscricaoRepository subscricaoRepository;
    @Autowired InstituicaoRepository instituicaoRepository;
    @Autowired UnidadeAtendimentoRepository unidadeAtendimentoRepository;
    @Autowired CategoriaProdutoRepository categoriaProdutoRepository;
    @Autowired UserRepository userRepository;
    @Autowired TenantUserRepository tenantUserRepository;
    @Autowired QrCodeOperacionalRepository qrCodeOperacionalRepository;

    @AfterEach
    void clearContext() {
        TenantContextHolder.clear();
    }

    @Test
    @WithMockUser(username = "platform-admin", authorities = "ROLE_ADMIN")
    void platformAdmin_canProvisionTenantWithMinimalInfra() throws Exception {
        String payload = """
                {
                  "tenant": {
                    "nome": "Banca da Tia Rosa",
                    "slug": "banca-tia-rosa",
                    "tenantCode": "ROSA",
                    "nif": "0000000000",
                    "telefone": "+244900000000",
                    "email": "rosa@email.com",
                    "tipo": "VENDEDOR_RUA"
                  },
                  "planoCodigo": "PILOTO",
                  "templateCodigo": "VENDEDOR_RUA",
                  "instituicao": {
                    "nome": "Banca da Tia Rosa",
                    "sigla": "ROSA",
                    "nif": "NIF-ROSA",
                    "provincia": "Luanda",
                    "municipio": "Luanda"
                  },
                  "responsavel": {
                    "nome": "Rosa Manuel",
                    "telefone": "+244900000000",
                    "email": "rosa@email.com",
                    "senhaTemporaria": "Alterar@123",
                    "criarUsuario": true
                  },
                  "opcoes": {
                    "criarQrPrincipal": true,
                    "criarCategoriaDefault": true,
                    "criarUnidadeAtendimentoDefault": true,
                    "ativarTenant": true
                  }
                }
                """;

        String resp = mockMvc.perform(post("/platform/tenants/provisionar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(resp);
        assertThat(json.at("/success").asBoolean()).isTrue();
        long tenantId = json.at("/data/tenantId").asLong();
        long instituicaoId = json.at("/data/instituicaoId").asLong();

        Tenant t = tenantRepository.findById(tenantId).orElseThrow();
        assertThat(t.getEstado()).isEqualTo(TenantEstado.ATIVO);
        assertThat(t.getTipo()).isEqualTo(TenantTipo.VENDEDOR_RUA);

        Subscricao sub = subscricaoRepository.findByTenantId(t.getId()).stream().findFirst().orElseThrow();
        assertThat(sub.getPlano().getCodigo()).isEqualTo("PILOTO");

        Instituicao inst = instituicaoRepository.findById(instituicaoId).orElseThrow();
        assertThat(inst.getTenant().getId()).isEqualTo(t.getId());

        UnidadeAtendimento ua = unidadeAtendimentoRepository.findAll().stream()
                .filter(u -> u.getInstituicao() != null && u.getInstituicao().getId().equals(inst.getId()))
                .findFirst().orElseThrow();
        assertThat(ua.getNome()).isNotBlank();

        CategoriaProduto cat = categoriaProdutoRepository.findBySlugAndTenantId("geral", t.getId()).orElseThrow();
        assertThat(cat.getAtivo()).isTrue();

        User owner = userRepository.findByEmail("rosa@email.com").orElseThrow();
        TenantUser tu = tenantUserRepository.findByTenantIdAndUserId(t.getId(), owner.getId()).orElseThrow();
        assertThat(tu.getRole().name()).isEqualTo("TENANT_OWNER");

        QrCodeOperacional qr = qrCodeOperacionalRepository.findByTenantId(t.getId()).stream().findFirst().orElseThrow();
        assertThat(qr.getToken()).startsWith("q_");
    }

    @Test
    @WithMockUser(username = "non-platform", authorities = "ROLE_GERENTE")
    void nonPlatformAdmin_cannotProvision() throws Exception {
        String payload = """
                {
                  "tenant": { "nome": "X", "slug": "x", "tipo": "VENDEDOR_RUA" },
                  "planoCodigo": "PILOTO",
                  "templateCodigo": "VENDEDOR_RUA",
                  "instituicao": { "nome": "X", "sigla": "X1", "nif": "NIF-X1" },
                  "opcoes": { "ativarTenant": true }
                }
                """;

        mockMvc.perform(post("/platform/tenants/provisionar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isForbidden());
    }
}

package com.restaurante.producao;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.dto.request.ProvisionarTenantRequest;
import com.restaurante.dto.request.PublicQrPedidoItemRequest;
import com.restaurante.dto.request.PublicQrPedidoRequest;
import com.restaurante.dto.response.ProvisionarTenantResponse;
import com.restaurante.model.entity.CategoriaProduto;
import com.restaurante.model.entity.Produto;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.enums.CategoriaProdutoLegacy;
import com.restaurante.model.enums.TenantTipo;
import com.restaurante.repository.CategoriaProdutoRepository;
import com.restaurante.repository.ProdutoRepository;
import com.restaurante.repository.TenantRepository;
import com.restaurante.security.tenant.TenantContext;
import com.restaurante.security.tenant.TenantContextHolder;
import com.restaurante.security.tenant.TenantResolutionSource;
import com.restaurante.service.PublicQrPedidoService;
import com.restaurante.service.TenantProvisioningService;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "spring.main.web-application-type=servlet"
)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("it-postgres")
class ProducaoKdsCrossTenantIT extends PostgresTestcontainersConfig {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired TenantProvisioningService provisioningService;
    @Autowired PublicQrPedidoService publicQrPedidoService;
    @Autowired TenantRepository tenantRepository;
    @Autowired CategoriaProdutoRepository categoriaProdutoRepository;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired com.restaurante.repository.CozinhaRepository cozinhaRepository;

    @org.junit.jupiter.api.BeforeEach
    void setUpCozinha() {
        if (cozinhaRepository.findByAtivaAndTipo(true, com.restaurante.model.enums.TipoCozinha.CENTRAL).isEmpty()) {
            com.restaurante.model.entity.Cozinha c = com.restaurante.model.entity.Cozinha.builder()
                    .nome("Cozinha Central Teste")
                    .tipo(com.restaurante.model.enums.TipoCozinha.CENTRAL)
                    .ativa(true)
                    .build();
            cozinhaRepository.save(c);
        }
    }

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    @Test
    @WithMockUser(username = "operator-user")
    void tenantA_doesNotSeeTenantB_subpedidos() throws Exception {
        ProvisionarTenantResponse tenantA = provisionTenant("TA");
        ProvisionarTenantResponse tenantB = provisionTenant("TB");

        Produto prodB = criarProdutoBasico(tenantB.getTenantId(), "Produto B");
        criarPedidoViaQr(tenantB.getQrToken(), prodB.getId());

        TenantContextHolder.set(new TenantContext(
                tenantA.getTenantId(), tenantA.getTenantCode(), tenantA.getOwnerUserId(),
                Set.of("TENANT_OPERATOR"), TenantResolutionSource.JWT, false, false
        ));

        String resp = mockMvc.perform(get("/tenant/producao/subpedidos")
                        .param("size", "10")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(resp);
        assertThat(json.at("/success").asBoolean()).isTrue();
        assertThat(json.at("/data/content").isArray()).isTrue();
        assertThat(json.at("/data/content").size()).isEqualTo(0);
    }

    private ProvisionarTenantResponse provisionTenant(String codePrefix) {
        TenantContextHolder.set(new TenantContext(
                null, null, 1L, Set.of("ROLE_ADMIN"),
                TenantResolutionSource.JWT, true, false
        ));

        String slug = "tenant-kds-ct-" + codePrefix + "-" + System.nanoTime();
        return provisioningService.provisionar(
                ProvisionarTenantRequest.builder()
                        .tenant(ProvisionarTenantRequest.TenantInfo.builder()
                                .nome("Tenant " + codePrefix)
                                .slug(slug)
                                .tenantCode(codePrefix + (System.nanoTime() % 1000))
                                .tipo(TenantTipo.RESTAURANTE)
                                .build())
                        .planoCodigo("PILOTO")
                        .templateCodigo("RESTAURANTE_SIMPLES")
                        .instituicao(ProvisionarTenantRequest.InstituicaoInfo.builder()
                                .nome("Inst " + codePrefix)
                                .sigla("I" + codePrefix)
                                .build())
                        .responsavel(ProvisionarTenantRequest.ResponsavelInfo.builder()
                                .email("owner-kds-ct-" + codePrefix + "-" + System.nanoTime() + "@a.com")
                                .telefone("+244900" + (System.nanoTime() % 1_000_000))
                                .criarUsuario(true)
                                .build())
                        .build()
        );
    }

    private Produto criarProdutoBasico(Long tenantId, String nome) {
        Tenant tenant = tenantRepository.findById(tenantId).orElseThrow();

        CategoriaProduto cat = new CategoriaProduto();
        cat.setTenant(tenant);
        cat.setNome("Geral");
        cat.setSlug("geral-" + (System.nanoTime() % 100_000));
        cat.setAtivo(true);
        cat = categoriaProdutoRepository.save(cat);

        Produto prod = Produto.builder()
                .codigo("P-" + (System.nanoTime() % 1_000_000))
                .nome(nome)
                .preco(new BigDecimal("15.00"))
                .categoria(CategoriaProdutoLegacy.OUTROS)
                .ativo(true)
                .build();
        prod.setTenant(tenant);
        prod.setCategoriaProduto(cat);
        return produtoRepository.save(prod);
    }

    private void criarPedidoViaQr(String token, Long produtoId) {
        publicQrPedidoService.criarPedidoPublicoPorQrToken(
                token,
                "idem-kds-ct-" + System.nanoTime(),
                PublicQrPedidoRequest.builder()
                        .clienteNome("Cliente")
                        .clienteTelefone("+244900000003")
                        .observacao("Teste KDS")
                        .itens(List.of(PublicQrPedidoItemRequest.builder()
                                .produtoId(produtoId)
                                .quantidade(1)
                                .build()))
                        .build()
        );
    }
}

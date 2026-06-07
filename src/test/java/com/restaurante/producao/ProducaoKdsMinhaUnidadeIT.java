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
import com.restaurante.security.tenant.TenantContext;
import com.restaurante.security.tenant.TenantContextHolder;
import com.restaurante.security.tenant.TenantResolutionSource;
import com.restaurante.service.PublicQrPedidoService;
import com.restaurante.service.TenantProvisioningService;
import com.restaurante.testsupport.PostgresTestcontainersConfig;
import com.restaurante.repository.CategoriaProdutoRepository;
import com.restaurante.repository.ProdutoRepository;
import com.restaurante.repository.TenantRepository;
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
class ProducaoKdsMinhaUnidadeIT extends PostgresTestcontainersConfig {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired TenantProvisioningService provisioningService;
    @Autowired PublicQrPedidoService publicQrPedidoService;
    @Autowired CategoriaProdutoRepository categoriaProdutoRepository;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired TenantRepository tenantRepository;
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
    @WithMockUser(username = "kitchen-user")
    void kitchen_canResolveMinhaUnidade_andListSubpedidos() throws Exception {
        ProvisionarTenantResponse prov = provisionTenant();
        Produto prod = criarProdutoBasico(prov.getTenantId());
        publicarCardapioForTest(prov.getTenantId());
        criarPedidoViaQr(prov.getQrToken(), prod.getId());

        TenantContextHolder.set(new TenantContext(
                prov.getTenantId(), prov.getTenantCode(), prov.getOwnerUserId(),
                Set.of("TENANT_KITCHEN"), TenantResolutionSource.JWT, false, false
        ));

        String unidadeResp = mockMvc.perform(get("/tenant/producao/minha-unidade").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode unidadeJson = objectMapper.readTree(unidadeResp);
        assertThat(unidadeJson.at("/success").asBoolean()).isTrue();
        assertThat(unidadeJson.at("/data/unidadeProducaoId").asLong()).isPositive();

        String subsResp = mockMvc.perform(get("/tenant/producao/minha-unidade/subpedidos")
                        .param("size", "10")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode subsJson = objectMapper.readTree(subsResp);
        assertThat(subsJson.at("/success").asBoolean()).isTrue();
        assertThat(subsJson.at("/data/content").isArray()).isTrue();
        assertThat(subsJson.at("/data/content").size()).isGreaterThanOrEqualTo(1);
    }

    private ProvisionarTenantResponse provisionTenant() {
        TenantContextHolder.set(new TenantContext(
                null, null, 1L, Set.of("ROLE_ADMIN"),
                TenantResolutionSource.JWT, true, false
        ));

        String slug = "tenant-kds-" + System.nanoTime();
        String tenantCode = "TK" + (System.nanoTime() % 1000);
        return provisioningService.provisionar(
                ProvisionarTenantRequest.builder()
                        .tenant(ProvisionarTenantRequest.TenantInfo.builder()
                                .nome("Tenant KDS")
                                .slug(slug)
                                .tenantCode(tenantCode)
                                .tipo(TenantTipo.RESTAURANTE)
                                .build())
                        .planoCodigo("PILOTO")
                        .templateCodigo("RESTAURANTE_SIMPLES")
                        .instituicao(ProvisionarTenantRequest.InstituicaoInfo.builder()
                                .nome("Inst KDS")
                                .sigla(uniqueSigla("IK"))
                                .build())
                        .responsavel(ProvisionarTenantRequest.ResponsavelInfo.builder()
                                .email("owner-kds-" + System.nanoTime() + "@a.com")
                                .telefone("+244900" + (System.nanoTime() % 1_000_000))
                                .criarUsuario(true)
                                .build())
                .build()
        );
    }

    private static String uniqueSigla(String prefix) {
        String normalizedPrefix = prefix == null ? "I" : prefix.replaceAll("[^A-Z0-9]", "");
        if (normalizedPrefix.isBlank()) {
            normalizedPrefix = "I";
        }
        if (normalizedPrefix.length() > 3) {
            normalizedPrefix = normalizedPrefix.substring(0, 3);
        }

        long suffix = Math.abs(System.nanoTime() % 10_000_000L);
        return normalizedPrefix + String.format("%07d", suffix);
    }

    private Produto criarProdutoBasico(Long tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId).orElseThrow();

        CategoriaProduto cat = new CategoriaProduto();
        cat.setTenant(tenant);
        cat.setNome("Geral");
        cat.setSlug("geral-" + (System.nanoTime() % 100_000));
        cat.setAtivo(true);
        cat = categoriaProdutoRepository.save(cat);

        Produto prod = Produto.builder()
                .codigo("P-" + (System.nanoTime() % 1_000_000))
                .nome("Produto KDS")
                .preco(new BigDecimal("10.00"))
                .categoria(CategoriaProdutoLegacy.PRATO_PRINCIPAL)
                .ativo(true)
                .build();
        prod.setTenant(tenant);
        prod.setCategoriaProduto(cat);
        return produtoRepository.save(prod);
    }

    private void criarPedidoViaQr(String token, Long produtoId) {
        publicQrPedidoService.criarPedidoPublicoPorQrToken(
                token,
                "idem-kds-" + System.nanoTime(),
                PublicQrPedidoRequest.builder()
                        .clienteNome("Cliente")
                        .clienteTelefone("+244900000000")
                        .observacao("Teste KDS")
                        .itens(List.of(PublicQrPedidoItemRequest.builder()
                                .produtoId(produtoId)
                                .quantidade(1)
                                .observacao("Sem cebola")
                                .build()))
                        .build()
        );
    }
}

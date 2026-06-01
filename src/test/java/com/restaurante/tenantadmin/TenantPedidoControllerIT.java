package com.restaurante.tenantadmin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.dto.request.ProdutoRequest;
import com.restaurante.dto.request.ProvisionarTenantRequest;
import com.restaurante.dto.response.ProvisionarTenantResponse;
import com.restaurante.model.entity.CategoriaProduto;
import com.restaurante.model.entity.Produto;
import com.restaurante.model.enums.TenantTipo;
import com.restaurante.repository.CategoriaProdutoRepository;
import com.restaurante.repository.ProdutoRepository;
import com.restaurante.security.tenant.TenantContext;
import com.restaurante.security.tenant.TenantContextHolder;
import com.restaurante.security.tenant.TenantResolutionSource;
import com.restaurante.service.ProdutoService;
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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "spring.main.web-application-type=servlet"
)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("it-postgres")
@org.springframework.transaction.annotation.Transactional
class TenantPedidoControllerIT extends PostgresTestcontainersConfig {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired TenantProvisioningService provisioningService;
    @Autowired ProdutoService produtoService;
    @Autowired CategoriaProdutoRepository categoriaProdutoRepository;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired com.restaurante.repository.CozinhaRepository cozinhaRepository;
    @Autowired com.restaurante.repository.UnidadeAtendimentoRepository unidadeAtendimentoRepository;

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    @Test
    @WithMockUser(username = "tenant-owner")
    void tenant_canListAndGetPedido_andCannotAccessOtherTenantPedido() throws Exception {
        // provisiona tenant A com QR principal
        TenantContextHolder.set(new TenantContext(null, null, 1L, Set.of("ROLE_ADMIN"), TenantResolutionSource.JWT, true, false));
        String suffixA = String.valueOf(Math.abs(System.nanoTime() % 1_000_000L));
        ProvisionarTenantResponse a = provisioningService.provisionar(
                ProvisionarTenantRequest.builder()
                        .tenant(ProvisionarTenantRequest.TenantInfo.builder()
                                .nome("A")
                                .slug("tenant-a-" + suffixA)
                                .tenantCode("TA" + suffixA)
                                .tipo(TenantTipo.RESTAURANTE)
                                .build())
                        .planoCodigo("PILOTO")
                        .templateCodigo("RESTAURANTE_SIMPLES")
                        .instituicao(ProvisionarTenantRequest.InstituicaoInfo.builder().nome("A").sigla(uniqueSigla("TA")).build())
                        .responsavel(ProvisionarTenantRequest.ResponsavelInfo.builder().email("a-" + suffixA + "@a.com").telefone("+24490" + String.format("%06d", Integer.parseInt(suffixA))).criarUsuario(true).build())
                        .opcoes(ProvisionarTenantRequest.OpcoesProvisionamento.builder().criarMesas(false).criarQrPorMesa(false).criarQrPrincipal(true).build())
                        .build()
        );
        var uaA = unidadeAtendimentoRepository.findById(a.getUnidadeAtendimentoId()).orElseThrow();
        com.restaurante.model.entity.Cozinha cozinhaA = new com.restaurante.model.entity.Cozinha();
        cozinhaA.setNome("Cozinha A");
        cozinhaA.setTipo(com.restaurante.model.enums.TipoCozinha.CENTRAL);
        cozinhaA.setAtiva(true);
        cozinhaA = cozinhaRepository.saveAndFlush(cozinhaA);
        uaA.adicionarCozinha(cozinhaA);
        unidadeAtendimentoRepository.saveAndFlush(uaA);

        // cria produto no tenant A
        TenantContextHolder.set(new TenantContext(a.getTenantId(), a.getTenantCode(), a.getOwnerUserId(), Set.of("ROLE_GERENTE"), TenantResolutionSource.JWT, false, false));
        CategoriaProduto geralA = categoriaProdutoRepository.findBySlugAndTenantId("geral", a.getTenantId()).orElseThrow();
        produtoService.criarTenantAware(ProdutoRequest.builder()
                .codigo("AGUA-500")
                .nome("Água 500ml")
                .preco(new BigDecimal("500.00"))
                .categoria(com.restaurante.model.enums.CategoriaProdutoLegacy.BEBIDA_NAO_ALCOOLICA)
                .categoriaProdutoId(geralA.getId())
                .disponivel(true)
                .build());
        Produto prodA = produtoRepository.findByCodigoAndTenantId("AGUA-500", a.getTenantId()).orElseThrow();

        // cria pedido público por QR
        String payloadPedido = """
                {
                  "clienteNome": "Cliente",
                  "itens": [ { "produtoId": %d, "quantidade": 1 } ]
                }
                """.formatted(prodA.getId());

        String respPublic = mockMvc.perform(post("/public/q/" + a.getQrToken() + "/pedidos")
                        .header("Idempotency-Key", "key-idempotente-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payloadPedido))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode publicJson = objectMapper.readTree(respPublic);
        long pedidoIdA = publicJson.at("/data/pedidoId").asLong();

        // tenant lista pedidos
        String respList = mockMvc.perform(get("/tenant/pedidos").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode listJson = objectMapper.readTree(respList);
        assertThat(listJson.at("/data/content").isArray()).isTrue();
        assertThat(listJson.at("/data/content").toString()).contains("\"id\":" + pedidoIdA);

        // detalhe
        String respDet = mockMvc.perform(get("/tenant/pedidos/" + pedidoIdA))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode detJson = objectMapper.readTree(respDet);
        assertThat(detJson.at("/data/id").asLong()).isEqualTo(pedidoIdA);

        // provisiona tenant B e cria pedido, tenant A não acessa
        TenantContextHolder.set(new TenantContext(null, null, 1L, Set.of("ROLE_ADMIN"), TenantResolutionSource.JWT, true, false));
        String suffixB = String.valueOf(Math.abs(System.nanoTime() % 1_000_000L));
        ProvisionarTenantResponse b = provisioningService.provisionar(
                ProvisionarTenantRequest.builder()
                        .tenant(ProvisionarTenantRequest.TenantInfo.builder()
                                .nome("B")
                                .slug("tenant-b-" + suffixB)
                                .tenantCode("TB" + suffixB)
                                .tipo(TenantTipo.RESTAURANTE)
                                .build())
                        .planoCodigo("PILOTO")
                        .templateCodigo("RESTAURANTE_SIMPLES")
                        .instituicao(ProvisionarTenantRequest.InstituicaoInfo.builder().nome("B").sigla(uniqueSigla("TB")).build())
                        .responsavel(ProvisionarTenantRequest.ResponsavelInfo.builder().email("b-" + suffixB + "@b.com").telefone("+24490" + String.format("%06d", Integer.parseInt(suffixB))).criarUsuario(true).build())
                        .opcoes(ProvisionarTenantRequest.OpcoesProvisionamento.builder().criarMesas(false).criarQrPorMesa(false).criarQrPrincipal(true).build())
                        .build()
        );
        var uaB = unidadeAtendimentoRepository.findById(b.getUnidadeAtendimentoId()).orElseThrow();
        com.restaurante.model.entity.Cozinha cozinhaB = new com.restaurante.model.entity.Cozinha();
        cozinhaB.setNome("Cozinha B");
        cozinhaB.setTipo(com.restaurante.model.enums.TipoCozinha.CENTRAL);
        cozinhaB.setAtiva(true);
        cozinhaB = cozinhaRepository.saveAndFlush(cozinhaB);
        uaB.adicionarCozinha(cozinhaB);
        unidadeAtendimentoRepository.saveAndFlush(uaB);

        TenantContextHolder.set(new TenantContext(b.getTenantId(), b.getTenantCode(), b.getOwnerUserId(), Set.of("ROLE_GERENTE"), TenantResolutionSource.JWT, false, false));
        CategoriaProduto geralB = categoriaProdutoRepository.findBySlugAndTenantId("geral", b.getTenantId()).orElseThrow();
        produtoService.criarTenantAware(ProdutoRequest.builder()
                .codigo("AGUA-500")
                .nome("Água 500ml")
                .preco(new BigDecimal("600.00"))
                .categoria(com.restaurante.model.enums.CategoriaProdutoLegacy.BEBIDA_NAO_ALCOOLICA)
                .categoriaProdutoId(geralB.getId())
                .disponivel(true)
                .build());
        Produto prodB = produtoRepository.findByCodigoAndTenantId("AGUA-500", b.getTenantId()).orElseThrow();
        String payloadPedidoB = """
                {
                  "clienteNome": "Cliente",
                  "itens": [ { "produtoId": %d, "quantidade": 1 } ]
                }
                """.formatted(prodB.getId());
        String respPublicB = mockMvc.perform(post("/public/q/" + b.getQrToken() + "/pedidos")
                        .header("Idempotency-Key", "key-idempotente-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payloadPedidoB))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long pedidoIdB = objectMapper.readTree(respPublicB).at("/data/pedidoId").asLong();

        // volta para tenant A e tenta acessar pedido B
        TenantContextHolder.set(new TenantContext(a.getTenantId(), a.getTenantCode(), a.getOwnerUserId(), Set.of("ROLE_GERENTE"), TenantResolutionSource.JWT, false, false));
        mockMvc.perform(get("/tenant/pedidos/" + pedidoIdB))
                .andExpect(status().isNotFound());
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
}

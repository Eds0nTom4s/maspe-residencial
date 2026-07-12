package com.restaurante.kds;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.dto.request.ProvisionarTenantRequest;
import com.restaurante.dto.request.PublicQrPedidoItemRequest;
import com.restaurante.dto.request.PublicQrPedidoRequest;
import com.restaurante.dto.response.ProvisionarTenantResponse;
import com.restaurante.model.entity.CategoriaProduto;
import com.restaurante.model.entity.Cozinha;
import com.restaurante.model.entity.Pedido;
import com.restaurante.model.entity.Produto;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.TenantOperacaoPolicy;
import com.restaurante.model.entity.TenantOperationalModulesConfig;
import com.restaurante.model.enums.CategoriaProdutoLegacy;
import com.restaurante.model.enums.PaymentMethodCode;
import com.restaurante.model.enums.StatusSubPedido;
import com.restaurante.model.enums.TenantTipo;
import com.restaurante.model.enums.TipoCozinha;
import com.restaurante.repository.CategoriaProdutoRepository;
import com.restaurante.repository.CozinhaRepository;
import com.restaurante.repository.PedidoRepository;
import com.restaurante.repository.ProdutoRepository;
import com.restaurante.repository.SubPedidoRepository;
import com.restaurante.repository.TenantOperacaoPolicyRepository;
import com.restaurante.repository.TenantOperationalModulesConfigRepository;
import com.restaurante.repository.TenantRepository;
import com.restaurante.security.tenant.TenantContext;
import com.restaurante.security.tenant.TenantContextHolder;
import com.restaurante.security.tenant.TenantResolutionSource;
import com.restaurante.service.TenantProvisioningService;
import com.restaurante.testsupport.PostgresTestcontainersConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "spring.main.web-application-type=servlet"
)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("it-postgres")
class KdsOperationsContractIT extends PostgresTestcontainersConfig {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired TenantProvisioningService provisioningService;
    @Autowired TenantRepository tenantRepository;
    @Autowired CategoriaProdutoRepository categoriaProdutoRepository;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired PedidoRepository pedidoRepository;
    @Autowired SubPedidoRepository subPedidoRepository;
    @Autowired CozinhaRepository cozinhaRepository;
    @Autowired TenantOperacaoPolicyRepository tenantOperacaoPolicyRepository;
    @Autowired TenantOperationalModulesConfigRepository tenantOperationalModulesConfigRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUpCozinha() {
        if (cozinhaRepository.findByAtivaAndTipo(true, TipoCozinha.CENTRAL).isEmpty()) {
            Cozinha cozinha = Cozinha.builder()
                    .nome("Cozinha Central KDS Contract")
                    .tipo(TipoCozinha.CENTRAL)
                    .ativa(true)
                    .build();
            cozinhaRepository.saveAndFlush(cozinha);
        }
    }

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    @Test
    @WithMockUser(username = "tenant-owner")
    void tenantKds_rejectsPontoDefaultWithoutChangingSubPedido() throws Exception {
        ProvisionarTenantResponse prov = provisionTenant("KDSA");
        Produto produto = criarProduto(prov.getTenantId(), "Produto KDS A");
        publicarCardapioForTest(prov.getTenantId());
        long pedidoId = criarPedidoPublicoPonto(prov.getQrToken(), produto.getId());
        assertThat(subPedidoRepository.findByPedidoIdOrderByCreatedAtAsc(pedidoId)).isEmpty();

        Pedido pedido = pedidoRepository.findByIdAndTenantIdComSessaoConsumo(pedidoId, prov.getTenantId()).orElseThrow();
        assertThat(pedido.getSessaoConsumo()).isNull();

        TenantContextHolder.set(new TenantContext(
                prov.getTenantId(), prov.getTenantCode(), prov.getOwnerUserId(),
                Set.of("TENANT_OWNER"), TenantResolutionSource.JWT, false, false
        ));

        mockMvc.perform(get("/tenant/kds/unidades-producao").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("KDS_DISABLED_FOR_OPERATION"));

        mockMvc.perform(get("/tenant/kds/subpedidos")
                        .param("pedidoId", String.valueOf(pedidoId))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("KDS_DISABLED_FOR_OPERATION"));

        mockMvc.perform(patch("/tenant/kds/subpedidos/{id}/iniciar-preparo", Long.MAX_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("KDS_DISABLED_FOR_OPERATION"));

        mockMvc.perform(patch("/tenant/kds/subpedidos/{id}/pronto", Long.MAX_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("KDS_DISABLED_FOR_OPERATION"));

        assertThat(subPedidoRepository.findByPedidoIdOrderByCreatedAtAsc(pedidoId)).isEmpty();
    }

    @Test
    @WithMockUser(username = "tenant-owner")
    void tenantKds_contractListsTransitionsAndConflictsForRestEnabledWithoutSession() throws Exception {
        ProvisionarTenantResponse prov = provisionTenant("KDSR");
        enableKdsAndProduction(prov.getTenantId());
        Produto produto = criarProduto(prov.getTenantId(), "Produto KDS REST");
        publicarCardapioForTest(prov.getTenantId());
        long pedidoId = criarPedidoPublicoPonto(prov.getQrToken(), produto.getId());
        long subPedidoId = subPedidoRepository.findByPedidoIdOrderByCreatedAtAsc(pedidoId).getFirst().getId();

        TenantContextHolder.set(new TenantContext(
                prov.getTenantId(), prov.getTenantCode(), prov.getOwnerUserId(),
                Set.of("TENANT_OWNER"), TenantResolutionSource.JWT, false, false
        ));

        String unidadesResp = mockMvc.perform(get("/tenant/kds/unidades-producao").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andReturn().getResponse().getContentAsString();
        JsonNode unidades = objectMapper.readTree(unidadesResp).at("/data");
        assertThat(unidades.size()).isGreaterThanOrEqualTo(1);
        long unidadeProducaoId = unidades.get(0).at("/id").asLong();

        String listResp = mockMvc.perform(get("/tenant/kds/subpedidos")
                        .param("pedidoId", String.valueOf(pedidoId))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.summary.total").value(1))
                .andReturn().getResponse().getContentAsString();
        JsonNode item = objectMapper.readTree(listResp).at("/data/items/0");
        assertThat(item.at("/id").asLong()).isEqualTo(subPedidoId);
        assertThat(item.at("/sessaoId").isNull()).isTrue();
        assertThat(item.at("/version").asLong()).isGreaterThanOrEqualTo(0);
        long initialVersion = item.at("/version").asLong();

        mockMvc.perform(get("/tenant/kds/subpedidos")
                        .param("unidadeProducaoId", String.valueOf(unidadeProducaoId))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        mockMvc.perform(get("/tenant/kds/subpedidos")
                        .param("status", item.at("/status").asText())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary.total").value(1));

        mockMvc.perform(get("/tenant/kds/subpedidos/{id}", subPedidoId).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(subPedidoId));

        String prepResp = mockMvc.perform(patch("/tenant/kds/subpedidos/{id}/iniciar-preparo", subPedidoId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":%d}".formatted(initialVersion)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("EM_PREPARACAO"))
                .andReturn().getResponse().getContentAsString();
        long prepVersion = objectMapper.readTree(prepResp).at("/data/version").asLong();

        mockMvc.perform(patch("/tenant/kds/subpedidos/{id}/pronto", subPedidoId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":%d}".formatted(initialVersion)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("KDS_SUBPEDIDO_CONFLICT"))
                .andExpect(jsonPath("$.additionalData.currentStatus").value("EM_PREPARACAO"));

        String prontoResp = mockMvc.perform(patch("/tenant/kds/subpedidos/{id}/pronto", subPedidoId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":%d}".formatted(prepVersion)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PRONTO"))
                .andReturn().getResponse().getContentAsString();
        long prontoVersion = objectMapper.readTree(prontoResp).at("/data/version").asLong();

        mockMvc.perform(patch("/tenant/kds/subpedidos/{id}/entregar", subPedidoId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":%d}".formatted(prontoVersion)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ENTREGUE"));

        mockMvc.perform(patch("/tenant/kds/subpedidos/{id}/pronto", subPedidoId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/tenant/pedidos?page=0&size=20").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "tenant-owner")
    void tenantKds_hidesSubPedidoFromOtherTenant() throws Exception {
        ProvisionarTenantResponse tenantA = provisionTenant("KDSB");
        ProvisionarTenantResponse tenantB = provisionTenant("KDSC");
        enableKdsAndProduction(tenantA.getTenantId());
        enableKdsAndProduction(tenantB.getTenantId());
        Produto produtoB = criarProduto(tenantB.getTenantId(), "Produto KDS B");
        publicarCardapioForTest(tenantB.getTenantId());
        long pedidoB = criarPedidoPublicoPonto(tenantB.getQrToken(), produtoB.getId());
        long subPedidoB = subPedidoRepository.findByPedidoIdOrderByCreatedAtAsc(pedidoB).getFirst().getId();

        TenantContextHolder.set(new TenantContext(
                tenantA.getTenantId(), tenantA.getTenantCode(), tenantA.getOwnerUserId(),
                Set.of("TENANT_OWNER"), TenantResolutionSource.JWT, false, false
        ));

        mockMvc.perform(get("/tenant/kds/subpedidos/{id}", subPedidoB).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    private void enableKdsAndProduction(long tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId).orElseThrow();
        tenant.setTemplateCode("CONSUMA_REST");
        tenant.setTemplateVersion(1);
        tenantRepository.saveAndFlush(tenant);

        TenantOperacaoPolicy policy = tenantOperacaoPolicyRepository.findByTenantId(tenantId)
                .orElseGet(TenantOperacaoPolicy::new);
        policy.setTenant(tenant);
        policy.setProductionEnabled(true);
        policy.setKdsEnabled(true);
        tenantOperacaoPolicyRepository.saveAndFlush(policy);

        TenantOperationalModulesConfig modules = tenantOperationalModulesConfigRepository.findByTenantId(tenantId)
                .orElseGet(TenantOperationalModulesConfig::new);
        modules.setTenant(tenant);
        modules.setKdsEnabled(true);
        tenantOperationalModulesConfigRepository.saveAndFlush(modules);
    }

    private ProvisionarTenantResponse provisionTenant(String prefix) {
        TenantContextHolder.set(new TenantContext(
                null, null, 1L, Set.of("ROLE_ADMIN"),
                TenantResolutionSource.JWT, true, false
        ));

        String suffix = prefix.toLowerCase() + "-" + Math.abs(System.nanoTime() % 1_000_000L);
        return provisioningService.provisionar(
                ProvisionarTenantRequest.builder()
                        .tenant(ProvisionarTenantRequest.TenantInfo.builder()
                                .nome("Tenant " + suffix)
                                .slug("tenant-" + suffix)
                                .tenantCode(prefix + Math.abs(System.nanoTime() % 1000))
                                .tipo(TenantTipo.VENDEDOR_RUA)
                                .build())
                        .planoCodigo("PILOTO")
                        .templateCodigo("RESTAURANTE_SIMPLES")
                        .instituicao(ProvisionarTenantRequest.InstituicaoInfo.builder()
                                .nome("Inst " + suffix)
                                .sigla(uniqueSigla(prefix))
                                .build())
                        .responsavel(ProvisionarTenantRequest.ResponsavelInfo.builder()
                                .email("owner-" + suffix + "@a.com")
                                .telefone("+244900" + Math.abs(System.nanoTime() % 1_000_000L))
                                .criarUsuario(true)
                                .build())
                        .build()
        );
    }

    private Produto criarProduto(long tenantId, String nome) {
        Tenant tenant = tenantRepository.findById(tenantId).orElseThrow();

        CategoriaProduto categoria = new CategoriaProduto();
        categoria.setTenant(tenant);
        categoria.setNome("Geral KDS");
        categoria.setSlug("geral-kds-" + Math.abs(System.nanoTime() % 100_000L));
        categoria.setAtivo(true);
        categoria = categoriaProdutoRepository.saveAndFlush(categoria);

        Produto produto = Produto.builder()
                .codigo("KDS-" + Math.abs(System.nanoTime() % 1_000_000L))
                .nome(nome)
                .preco(new BigDecimal("25.00"))
                .categoria(CategoriaProdutoLegacy.PRATO_PRINCIPAL)
                .ativo(true)
                .build();
        produto.setTenant(tenant);
        produto.setCategoriaProduto(categoria);
        produto.setDisponivel(true);
        return produtoRepository.saveAndFlush(produto);
    }

    private long criarPedidoPublicoPonto(String token, Long produtoId) throws Exception {
        String resp = mockMvc.perform(post("/public/q/{token}/pedidos", token)
                        .header("Idempotency-Key", "kds-contract-" + System.nanoTime())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "clienteNome": "Cliente KDS",
                                  "clienteTelefone": "+244950000000",
                                  "metodoPagamento": "%s",
                                  "itens": [
                                    { "produtoId": %d, "quantidade": 1, "observacao": "Sem cebola" }
                                  ]
                                }
                                """.formatted(PaymentMethodCode.CASH.name(), produtoId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resp).at("/data/pedidoId").asLong();
    }

    private void publicarCardapioForTest(long tenantId) {
        jdbcTemplate.update("""
                insert into tenant_cardapio_configs
                    (tenant_id, cardapio_publicado, cardapio_publicado_em, cardapio_publicado_por_user_id,
                     cardapio_atualizado_em, created_at, updated_at, version)
                values (?, true, now(), null, now(), now(), now(), 0)
                on conflict (tenant_id)
                do update set cardapio_publicado = true,
                              cardapio_publicado_em = now(),
                              cardapio_despublicado_em = null,
                              cardapio_motivo_despublicacao = null,
                              cardapio_atualizado_em = now(),
                              updated_at = now()
                """, tenantId);
    }

    private static String uniqueSigla(String prefix) {
        String normalizedPrefix = prefix == null ? "I" : prefix.replaceAll("[^A-Z0-9]", "");
        if (normalizedPrefix.isBlank()) normalizedPrefix = "I";
        if (normalizedPrefix.length() > 3) normalizedPrefix = normalizedPrefix.substring(0, 3);
        return normalizedPrefix + String.format("%07d", Math.abs(System.nanoTime() % 10_000_000L));
    }
}

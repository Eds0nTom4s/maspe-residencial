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
import com.restaurante.model.enums.DeviceCapability;
import com.restaurante.model.enums.DispositivoStatus;
import com.restaurante.model.enums.DispositivoTipo;
import com.restaurante.model.enums.TenantTipo;
import com.restaurante.repository.CategoriaProdutoRepository;
import com.restaurante.repository.ProdutoRepository;
import com.restaurante.repository.TenantRepository;
import com.restaurante.security.device.DevicePrincipal;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "spring.main.web-application-type=servlet"
)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("it-postgres")
class ProducaoKdsDeviceScopeIT extends PostgresTestcontainersConfig {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired TenantProvisioningService provisioningService;
    @Autowired PublicQrPedidoService publicQrPedidoService;
    @Autowired TenantRepository tenantRepository;
    @Autowired CategoriaProdutoRepository categoriaProdutoRepository;
    @Autowired ProdutoRepository produtoRepository;

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    @Test
    void deviceKds_withViewProduction_canResolveMinhaUnidade_andListSubpedidos() throws Exception {
        ProvisionarTenantResponse prov = provisionTenant();
        Produto prod = criarProdutoBasico(prov.getTenantId());
        criarPedidoViaQr(prov.getQrToken(), prod.getId());

        DevicePrincipal device = new DevicePrincipal(
                999L,
                prov.getTenantId(),
                prov.getTenantCode(),
                prov.getInstituicaoId(),
                prov.getUnidadeAtendimentoId(),
                DispositivoTipo.KDS,
                DispositivoStatus.ATIVO,
                List.of(DeviceCapability.VIEW_PRODUCTION),
                1
        );
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                device, "N/A", List.of(new SimpleGrantedAuthority("ROLE_DEVICE"))
        );

        String unidadeResp = mockMvc.perform(get("/tenant/producao/minha-unidade")
                        .with(authentication(auth))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode unidadeJson = objectMapper.readTree(unidadeResp);
        assertThat(unidadeJson.at("/success").asBoolean()).isTrue();
        assertThat(unidadeJson.at("/data/unidadeProducaoId").asLong()).isPositive();

        String subsResp = mockMvc.perform(get("/tenant/producao/minha-unidade/subpedidos")
                        .with(authentication(auth))
                        .param("size", "10")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode subsJson = objectMapper.readTree(subsResp);
        assertThat(subsJson.at("/success").asBoolean()).isTrue();
        assertThat(subsJson.at("/data/content").isArray()).isTrue();
        assertThat(subsJson.at("/data/content").size()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void devicePos_withoutViewProduction_isForbidden() throws Exception {
        ProvisionarTenantResponse prov = provisionTenant();

        DevicePrincipal device = new DevicePrincipal(
                1000L,
                prov.getTenantId(),
                prov.getTenantCode(),
                prov.getInstituicaoId(),
                prov.getUnidadeAtendimentoId(),
                DispositivoTipo.POS,
                DispositivoStatus.ATIVO,
                List.of(), // sem capability
                1
        );
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                device, "N/A", List.of(new SimpleGrantedAuthority("ROLE_DEVICE"))
        );

        mockMvc.perform(get("/tenant/producao/minha-unidade")
                        .with(authentication(auth))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    private ProvisionarTenantResponse provisionTenant() {
        TenantContextHolder.set(new TenantContext(
                null, null, 1L, Set.of("ROLE_ADMIN"),
                TenantResolutionSource.JWT, true, false
        ));

        String slug = "tenant-kds-dev-" + System.nanoTime();
        return provisioningService.provisionar(
                ProvisionarTenantRequest.builder()
                        .tenant(ProvisionarTenantRequest.TenantInfo.builder()
                                .nome("Tenant KDS Device")
                                .slug(slug)
                                .tenantCode("TD" + (System.nanoTime() % 1000))
                                .tipo(TenantTipo.RESTAURANTE)
                                .build())
                        .planoCodigo("PILOTO")
                        .templateCodigo("RESTAURANTE_SIMPLES")
                        .instituicao(ProvisionarTenantRequest.InstituicaoInfo.builder()
                                .nome("Inst KDS Device")
                                .sigla("IKD")
                                .build())
                        .responsavel(ProvisionarTenantRequest.ResponsavelInfo.builder()
                                .email("owner-kds-dev-" + System.nanoTime() + "@a.com")
                                .telefone("+244900" + (System.nanoTime() % 1_000_000))
                                .criarUsuario(true)
                                .build())
                        .build()
        );
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
                "idem-kds-dev-" + System.nanoTime(),
                PublicQrPedidoRequest.builder()
                        .clienteNome("Cliente")
                        .clienteTelefone("+244900000001")
                        .observacao("Teste KDS")
                        .itens(List.of(PublicQrPedidoItemRequest.builder()
                                .produtoId(produtoId)
                                .quantidade(1)
                                .build()))
                        .build()
        );
    }
}

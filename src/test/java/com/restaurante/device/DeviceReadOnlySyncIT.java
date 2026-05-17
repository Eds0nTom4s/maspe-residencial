package com.restaurante.device;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.dto.request.ProvisionarTenantRequest;
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
import com.restaurante.security.tenant.TenantContextHolder;
import com.restaurante.security.tenant.TenantContext;
import com.restaurante.security.tenant.TenantResolutionSource;
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
import org.springframework.security.test.context.support.WithMockUser;

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
class DeviceReadOnlySyncIT extends PostgresTestcontainersConfig {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired TenantProvisioningService provisioningService;
    @Autowired TenantRepository tenantRepository;
    @Autowired CategoriaProdutoRepository categoriaProdutoRepository;
    @Autowired ProdutoRepository produtoRepository;

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    @Test
    void device_withSyncCatalog_canFetchBootstrapAndCatalog() throws Exception {
        ProvisionarTenantResponse prov = provisionTenant();
        Produto prod = criarProdutoBasico(prov.getTenantId());

        DevicePrincipal device = new DevicePrincipal(
                10L,
                "KDS-01",
                prov.getTenantId(),
                prov.getTenantCode(),
                prov.getInstituicaoId(),
                prov.getUnidadeAtendimentoId(),
                null,
                DispositivoTipo.KDS,
                DispositivoStatus.ATIVO,
                List.of(DeviceCapability.SYNC_CATALOG, DeviceCapability.VIEW_PRODUCTION),
                1
        );
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                device, "N/A", List.of(new SimpleGrantedAuthority("ROLE_DEVICE"))
        );

        String boot = mockMvc.perform(get("/device/sync/bootstrap")
                        .with(authentication(auth))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode bootJson = objectMapper.readTree(boot);
        assertThat(bootJson.at("/success").asBoolean()).isTrue();
        assertThat(bootJson.at("/data/tenantId").asLong()).isEqualTo(prov.getTenantId());

        String cat = mockMvc.perform(get("/device/sync/catalogo")
                        .with(authentication(auth))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode catJson = objectMapper.readTree(cat);
        assertThat(catJson.at("/success").asBoolean()).isTrue();
        assertThat(catJson.at("/data/produtos").isArray()).isTrue();
        assertThat(catJson.at("/data/produtos").toString()).contains(prod.getCodigo());
    }

    @Test
    @WithMockUser(username = "operator-user")
    void jwtUser_cannotAccessDeviceSyncEndpoints() throws Exception {
        ProvisionarTenantResponse prov = provisionTenant();
        TenantContextHolder.set(new TenantContext(
                prov.getTenantId(), prov.getTenantCode(), prov.getOwnerUserId(),
                Set.of("TENANT_OPERATOR"), TenantResolutionSource.JWT, false, false
        ));

        mockMvc.perform(get("/device/sync/bootstrap").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    private ProvisionarTenantResponse provisionTenant() {
        TenantContextHolder.set(new TenantContext(
                null, null, 1L, Set.of("ROLE_ADMIN"),
                TenantResolutionSource.JWT, true, false
        ));

        String slug = "tenant-dev-sync-" + System.nanoTime();
        return provisioningService.provisionar(
                ProvisionarTenantRequest.builder()
                        .tenant(ProvisionarTenantRequest.TenantInfo.builder()
                                .nome("Tenant Device Sync")
                                .slug(slug)
                                .tenantCode("DS" + (System.nanoTime() % 1000))
                                .tipo(TenantTipo.RESTAURANTE)
                                .build())
                        .planoCodigo("PILOTO")
                        .templateCodigo("RESTAURANTE_SIMPLES")
                        .instituicao(ProvisionarTenantRequest.InstituicaoInfo.builder()
                                .nome("Inst Device Sync")
                                .sigla("IDS")
                                .build())
                        .responsavel(ProvisionarTenantRequest.ResponsavelInfo.builder()
                                .email("owner-dev-sync-" + System.nanoTime() + "@a.com")
                                .telefone("+244902" + (System.nanoTime() % 1_000_000))
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
                .nome("Produto Sync")
                .preco(new BigDecimal("12.00"))
                .categoria(CategoriaProdutoLegacy.OUTROS)
                .ativo(true)
                .build();
        prod.setTenant(tenant);
        prod.setCategoriaProduto(cat);
        return produtoRepository.save(prod);
    }
}

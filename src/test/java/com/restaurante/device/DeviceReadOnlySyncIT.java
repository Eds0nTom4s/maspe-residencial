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
@AutoConfigureMockMvc
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
        Produto prod1 = criarProdutoBasico(prov.getTenantId());
        Produto prod2 = criarProdutoBasico(prov.getTenantId());

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
        String bootEtag = mockMvc.perform(get("/device/sync/bootstrap")
                        .with(authentication(auth))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader("ETag");
        JsonNode bootJson = objectMapper.readTree(boot);
        assertThat(bootJson.at("/data/tenantId").asLong()).isEqualTo(prov.getTenantId());
        assertThat(bootEtag).isNotBlank();

        // If-None-Match deve retornar 304 quando não há mudanças no principal/escopo
        mockMvc.perform(get("/device/sync/bootstrap")
                        .with(authentication(auth))
                        .header("If-None-Match", bootEtag)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotModified());

        // tokenVersion muda ETag do bootstrap
        DevicePrincipal deviceV2 = new DevicePrincipal(
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
                2
        );
        UsernamePasswordAuthenticationToken authV2 = new UsernamePasswordAuthenticationToken(
                deviceV2, "N/A", List.of(new SimpleGrantedAuthority("ROLE_DEVICE"))
        );
        mockMvc.perform(get("/device/sync/bootstrap")
                        .with(authentication(authV2))
                        .header("If-None-Match", bootEtag)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        var catRes1 = mockMvc.perform(get("/device/sync/catalogo")
                        .with(authentication(auth))
                        .param("limit", "1")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        String cat = catRes1.getResponse().getContentAsString();
        String etag = catRes1.getResponse().getHeader("ETag");
        JsonNode catJson = objectMapper.readTree(cat);
        assertThat(catJson.at("/data/produtos").isArray()).isTrue();
        assertThat(catJson.at("/data/produtos").size()).isEqualTo(1);
        assertThat(catJson.at("/hasMore").asBoolean()).isTrue();
        assertThat(catJson.at("/nextCursor").asText()).isNotBlank();

        // próxima página via cursor
        String cursor = catJson.at("/nextCursor").asText();
        String page2 = mockMvc.perform(get("/device/sync/catalogo")
                        .with(authentication(auth))
                        .param("limit", "1")
                        .param("cursor", cursor)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode page2Json = objectMapper.readTree(page2);
        assertThat(page2Json.at("/data/produtos").size()).isEqualTo(1);

        // cursor manipulado deve falhar (assinatura inválida)
        String tampered = cursor.replaceFirst("\\.", "X."); // altera payload mantendo formato
        String err = mockMvc.perform(get("/device/sync/catalogo")
                        .with(authentication(auth))
                        .param("limit", "1")
                        .param("cursor", tampered)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();
        JsonNode errJson = objectMapper.readTree(err);
        assertThat(errJson.at("/code").asText()).isEqualTo("SYNC_CURSOR_INVALID_SIGNATURE");

        // cursor legacy sem assinatura deve ser rejeitado quando require-signature=true
        String legacy = cursor.split("\\.")[0];
        mockMvc.perform(get("/device/sync/catalogo")
                        .with(authentication(auth))
                        .param("limit", "1")
                        .param("cursor", legacy)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        // If-None-Match deve retornar 304 quando não há mudanças
        mockMvc.perform(get("/device/sync/catalogo")
                        .with(authentication(auth))
                        .header("If-None-Match", etag)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotModified());
    }

    @Test
    void device_canPaginateMesas_andCursorDisables304() throws Exception {
        ProvisionarTenantResponse prov = provisionTenant();

        DevicePrincipal device = new DevicePrincipal(
                11L,
                "POS-01",
                prov.getTenantId(),
                prov.getTenantCode(),
                prov.getInstituicaoId(),
                prov.getUnidadeAtendimentoId(),
                null,
                DispositivoTipo.POS,
                DispositivoStatus.ATIVO,
                List.of(DeviceCapability.VIEW_ORDERS),
                1
        );
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                device, "N/A", List.of(new SimpleGrantedAuthority("ROLE_DEVICE"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);

        var res1 = mockMvc.perform(get("/device/sync/mesas")
                        .with(authentication(auth))
                        .param("limit", "1")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        String etag = res1.getResponse().getHeader("ETag");
        JsonNode json1 = objectMapper.readTree(res1.getResponse().getContentAsString());
        assertThat(json1.at("/data/mesas").size()).isEqualTo(1);
        assertThat(json1.at("/hasMore").asBoolean()).isTrue();
        assertThat(json1.at("/nextCursor").asText()).isNotBlank();

        // pagina 2 (mesmo etag), não deve retornar 304 porque cursor exige body
        String cursor = json1.at("/nextCursor").asText();
        mockMvc.perform(get("/device/sync/mesas")
                        .with(authentication(auth))
                        .param("limit", "1")
                        .param("cursor", cursor)
                        .header("If-None-Match", etag)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void device_canPaginateQrCodes_andCursorDisables304() throws Exception {
        ProvisionarTenantResponse prov = provisionTenant();

        DevicePrincipal device = new DevicePrincipal(
                12L,
                "POS-02",
                prov.getTenantId(),
                prov.getTenantCode(),
                prov.getInstituicaoId(),
                prov.getUnidadeAtendimentoId(),
                null,
                DispositivoTipo.POS,
                DispositivoStatus.ATIVO,
                List.of(DeviceCapability.SYNC_CATALOG),
                1
        );
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                device, "N/A", List.of(new SimpleGrantedAuthority("ROLE_DEVICE"))
        );

        var res1 = mockMvc.perform(get("/device/sync/qrcodes")
                        .with(authentication(auth))
                        .param("limit", "1")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        String etag = res1.getResponse().getHeader("ETag");
        JsonNode json1 = objectMapper.readTree(res1.getResponse().getContentAsString());
        assertThat(json1.at("/data/qrcodes").size()).isEqualTo(1);
        assertThat(json1.at("/hasMore").asBoolean()).isTrue();
        assertThat(json1.at("/nextCursor").asText()).isNotBlank();

        String cursor = json1.at("/nextCursor").asText();
        mockMvc.perform(get("/device/sync/qrcodes")
                        .with(authentication(auth))
                        .param("limit", "1")
                        .param("cursor", cursor)
                        .header("If-None-Match", etag)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
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
                                .sigla(uniqueSigla("IDS"))
                                .build())
                        .responsavel(ProvisionarTenantRequest.ResponsavelInfo.builder()
                                .email("owner-dev-sync-" + System.nanoTime() + "@a.com")
                                .telefone("+244902" + (System.nanoTime() % 1_000_000))
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

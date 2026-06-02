package com.restaurante.device;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.dto.request.ProvisionarTenantRequest;
import com.restaurante.dto.response.ProvisionarTenantResponse;
import com.restaurante.model.entity.CategoriaProduto;
import com.restaurante.model.entity.DispositivoOperacional;
import com.restaurante.model.entity.Produto;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.enums.CategoriaProdutoLegacy;
import com.restaurante.model.enums.DeviceCapability;
import com.restaurante.model.enums.DispositivoStatus;
import com.restaurante.model.enums.DispositivoTipo;
import com.restaurante.model.enums.OperationalDeviceType;
import com.restaurante.model.enums.TenantTipo;
import com.restaurante.repository.CategoriaProdutoRepository;
import com.restaurante.repository.DispositivoOperacionalRepository;
import com.restaurante.repository.InstituicaoRepository;
import com.restaurante.repository.ProdutoRepository;
import com.restaurante.repository.TenantRepository;
import com.restaurante.security.tenant.TenantContext;
import com.restaurante.security.tenant.TenantContextHolder;
import com.restaurante.security.tenant.TenantResolutionSource;
import com.restaurante.service.TenantProvisioningService;
import com.restaurante.testsupport.DeviceAuthIntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import com.restaurante.repository.UnidadeAtendimentoRepository;

import java.math.BigDecimal;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "spring.main.web-application-type=servlet"
)
@AutoConfigureMockMvc
@ActiveProfiles("it-postgres")
class DeviceReadOnlySyncIT extends DeviceAuthIntegrationTestSupport {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired TenantProvisioningService provisioningService;
    @Autowired TenantRepository tenantRepository;
    @Autowired CategoriaProdutoRepository categoriaProdutoRepository;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired DispositivoOperacionalRepository dispositivoOperacionalRepository;
    @Autowired InstituicaoRepository instituicaoRepository;
    @Autowired UnidadeAtendimentoRepository unidadeAtendimentoRepository;

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    @Test
    void device_withSyncCatalog_canFetchBootstrapAndCatalog() throws Exception {
        ProvisionarTenantResponse prov = provisionTenant();
        Produto prod1 = criarProdutoBasico(prov.getTenantId());
        Produto prod2 = criarProdutoBasico(prov.getTenantId());
        DispositivoOperacional device = criarDevice(prov, "KDS-01", "KDS Sync", DispositivoTipo.KDS, OperationalDeviceType.KDS_COZINHA);
        String deviceToken = activateDeviceForTest(device, java.util.List.of(DeviceCapability.SYNC_CATALOG, DeviceCapability.VIEW_PRODUCTION));

        String boot = mockMvc.perform(get("/device/sync/bootstrap")
                        .header("Authorization", deviceAuthorization(deviceToken))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String bootEtag = mockMvc.perform(get("/device/sync/bootstrap")
                        .header("Authorization", deviceAuthorization(deviceToken))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader("ETag");
        JsonNode bootJson = objectMapper.readTree(boot);
        assertThat(bootJson.at("/data/tenantId").asLong()).isEqualTo(prov.getTenantId());
        assertThat(bootEtag).isNotBlank();

        // If-None-Match deve retornar 304 quando não há mudanças no principal/escopo
        mockMvc.perform(get("/device/sync/bootstrap")
                        .header("Authorization", deviceAuthorization(deviceToken))
                        .header("If-None-Match", bootEtag)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotModified());

        // tokenVersion muda ETag do bootstrap
        DispositivoOperacional refreshedDevice = dispositivoOperacionalRepository.findById(device.getId())
                .orElseThrow();
        refreshedDevice.setTokenVersion(2);
        dispositivoOperacionalRepository.saveAndFlush(refreshedDevice);
        mockMvc.perform(get("/device/sync/bootstrap")
                        .header("Authorization", deviceAuthorization(deviceToken))
                        .header("If-None-Match", bootEtag)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        var catRes1 = mockMvc.perform(get("/device/sync/catalogo")
                        .header("Authorization", deviceAuthorization(deviceToken))
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
                        .header("Authorization", deviceAuthorization(deviceToken))
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
                        .header("Authorization", deviceAuthorization(deviceToken))
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
                        .header("Authorization", deviceAuthorization(deviceToken))
                        .param("limit", "1")
                        .param("cursor", legacy)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        // If-None-Match deve retornar 304 quando não há mudanças
        mockMvc.perform(get("/device/sync/catalogo")
                        .header("Authorization", deviceAuthorization(deviceToken))
                        .header("If-None-Match", etag)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotModified());
    }

    @Test
    void device_canPaginateMesas_andCursorDisables304() throws Exception {
        ProvisionarTenantResponse prov = provisionTenant();
        DispositivoOperacional device = criarDevice(prov, "POS-01", "POS Sync", DispositivoTipo.POS, OperationalDeviceType.POS_CAIXA);
        String deviceToken = activateDeviceForTest(device, java.util.List.of(DeviceCapability.VIEW_ORDERS));

        var res1 = mockMvc.perform(get("/device/sync/mesas")
                        .header("Authorization", deviceAuthorization(deviceToken))
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
                        .header("Authorization", deviceAuthorization(deviceToken))
                        .param("limit", "1")
                        .param("cursor", cursor)
                        .header("If-None-Match", etag)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void device_canPaginateQrCodes_andCursorDisables304() throws Exception {
        ProvisionarTenantResponse prov = provisionTenant();
        DispositivoOperacional device = criarDevice(prov, "POS-02", "POS QR Sync", DispositivoTipo.POS, OperationalDeviceType.POS_CAIXA);
        String deviceToken = activateDeviceForTest(device, java.util.List.of(DeviceCapability.SYNC_CATALOG));

        var res1 = mockMvc.perform(get("/device/sync/qrcodes")
                        .header("Authorization", deviceAuthorization(deviceToken))
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
                        .header("Authorization", deviceAuthorization(deviceToken))
                        .param("limit", "1")
                        .param("cursor", cursor)
                        .header("If-None-Match", etag)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void jwtUser_cannotAccessDeviceSyncEndpoints() throws Exception {
        ProvisionarTenantResponse prov = provisionTenant();
        String token = issueTenantOwnerToken(prov);

        mockMvc.perform(get("/device/sync/bootstrap")
                        .header("Authorization", "Bearer " + token)
                        .accept(MediaType.APPLICATION_JSON))
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

    private DispositivoOperacional criarDevice(ProvisionarTenantResponse prov,
                                               String codigo,
                                               String nome,
                                               DispositivoTipo tipo,
                                               OperationalDeviceType operationalType) {
        Tenant tenant = tenantRepository.findById(prov.getTenantId()).orElseThrow();
        var inst = instituicaoRepository.findById(prov.getInstituicaoId()).orElseThrow();
        var ua = unidadeAtendimentoRepository.findById(prov.getUnidadeAtendimentoId()).orElseThrow();
        DispositivoOperacional d = new DispositivoOperacional();
        d.setTenant(tenant);
        d.setInstituicao(inst);
        d.setUnidadeAtendimento(ua);
        d.setCodigo(codigo + "-" + (System.nanoTime() % 100000));
        d.setNome(nome);
        d.setTipo(tipo);
        d.setOperationalDeviceType(operationalType);
        d.setStatus(DispositivoStatus.ATIVO);
        d.setTokenVersion(1);
        return dispositivoOperacionalRepository.saveAndFlush(d);
    }
}

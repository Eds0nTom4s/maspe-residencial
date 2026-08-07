package com.restaurante.android.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.android.discovery.service.MerchantDiscoveryPublicationService;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.TenantCardapioConfig;
import com.restaurante.model.enums.TenantEstado;
import com.restaurante.model.enums.TenantTipo;
import com.restaurante.repository.TenantCardapioConfigRepository;
import com.restaurante.repository.TenantRepository;
import com.restaurante.testsupport.PostgresTestcontainersConfig;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.LivenessState;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.main.web-application-type=servlet")
@ActiveProfiles("it-postgres")
class AndroidDiscoveryRuntimePostgresIT extends PostgresTestcontainersConfig {

    @Autowired TestRestTemplate http;
    @Autowired ObjectMapper json;
    @Autowired TenantRepository tenants;
    @Autowired TenantCardapioConfigRepository cardapios;
    @Autowired MerchantDiscoveryPublicationService publication;
    @Autowired ApplicationAvailability availability;

    @Test
    void runsHealthProbesAndRealDiscoveryHttpWithoutAuthenticationOrFakeData() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        Tenant published = saveTenant("Runtime " + suffix, "runtime-" + suffix);
        Tenant hidden = saveTenant("Hidden " + suffix, "hidden-" + suffix);
        TenantCardapioConfig publishedCatalog = saveCatalog(published);
        TenantCardapioConfig hiddenCatalog = saveCatalog(hidden);
        publication.setPublished(published.getId(), true);
        try {
            assertHealth("/actuator/health");
            assertThat(availability.getLivenessState()).isEqualTo(LivenessState.CORRECT);
            assertThat(availability.getReadinessState()).isEqualTo(ReadinessState.ACCEPTING_TRAFFIC);

            var home = http.getForEntity("/v1/discovery/home", String.class);
            assertThat(home.getStatusCode())
                    .as("root=%s body=%s", http.getRootUri(), home.getBody())
                    .isEqualTo(HttpStatus.OK);
            assertThat(home.getHeaders().getCacheControl()).isEqualTo("public, max-age=60");

            var search = http.getForEntity(
                    "/v1/discovery/search?query=" + suffix + "&sort=NAME", String.class);
            assertThat(search.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode searchJson = json.readTree(search.getBody());
            assertThat(searchJson.at("/merchants/0/merchantId").asText())
                    .isEqualTo(published.getMerchantPublicId().toString());
            assertThat(searchJson.toString()).doesNotContain(
                    "tenantId", "businessAccountId", "distanceMeters\":0", "rating\":0", "OPEN");

            var detail = http.getForEntity(
                    "/v1/discovery/merchants/" + published.getMerchantPublicId(), String.class);
            assertThat(detail.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(detail.getHeaders().getETag()).isNotBlank();

            HttpHeaders conditional = new HttpHeaders();
            conditional.setIfNoneMatch(detail.getHeaders().getETag());
            var notModified = http.exchange(
                    "/v1/discovery/merchants/" + published.getMerchantPublicId(),
                    HttpMethod.GET, new HttpEntity<>(conditional), String.class);
            assertThat(notModified.getStatusCode()).isEqualTo(HttpStatus.NOT_MODIFIED);
            assertThat(notModified.getBody()).isNull();

            assertThat(http.getForEntity(
                    "/v1/discovery/merchants/" + hidden.getMerchantPublicId(), String.class)
                    .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

            HttpHeaders override = new HttpHeaders();
            override.set("X-Tenant-Code", hidden.getTenantCode());
            assertThat(http.exchange(
                    "/v1/discovery/search?query=" + suffix,
                    HttpMethod.GET, new HttpEntity<>(override), String.class).getStatusCode())
                    .isEqualTo(HttpStatus.BAD_REQUEST);

            assertThat(http.getForEntity("/tenant/produtos", String.class).getStatusCode())
                    .isEqualTo(HttpStatus.UNAUTHORIZED);
        } finally {
            cardapios.deleteAllByIdInBatch(java.util.List.of(publishedCatalog.getId(), hiddenCatalog.getId()));
            tenants.deleteAllByIdInBatch(java.util.List.of(published.getId(), hidden.getId()));
        }
    }

    private void assertHealth(String path) {
        var response = http.getForEntity(path, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
    }

    private Tenant saveTenant(String name, String slug) {
        Tenant tenant = new Tenant();
        tenant.setNome(name);
        tenant.setSlug(slug);
        tenant.setTenantCode(slug.toUpperCase());
        tenant.setTipo(TenantTipo.RESTAURANTE);
        tenant.setEstado(TenantEstado.ATIVO);
        return tenants.saveAndFlush(tenant);
    }

    private TenantCardapioConfig saveCatalog(Tenant tenant) {
        TenantCardapioConfig config = new TenantCardapioConfig();
        config.setTenant(tenant);
        config.setCardapioPublicado(true);
        return cardapios.saveAndFlush(config);
    }
}

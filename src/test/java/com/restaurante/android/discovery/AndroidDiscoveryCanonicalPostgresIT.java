package com.restaurante.android.discovery;

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "spring.main.web-application-type=servlet")
@AutoConfigureMockMvc(addFilters = true)
@ActiveProfiles("it-postgres")
@Transactional
class AndroidDiscoveryCanonicalPostgresIT extends PostgresTestcontainersConfig {

    @Autowired MockMvc mvc;
    @Autowired TenantRepository tenants;
    @Autowired TenantCardapioConfigRepository cardapios;
    @Autowired MerchantDiscoveryPublicationService publication;

    @Test
    void exposesOnlyExplicitlyPublishedEligibleMerchantAndHonoursHttpContract() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Tenant visible = saveTenant("Alpha Publicável", "a", suffix, TenantEstado.ATIVO);
        Tenant optedOut = saveTenant("Beta Opt-out", "b", suffix, TenantEstado.ATIVO);
        Tenant noCatalog = saveTenant("Gamma Sem Cardápio", "c", suffix, TenantEstado.ATIVO);
        Tenant inactive = saveTenant("Delta Inactivo", "d", suffix, TenantEstado.SUSPENSO);
        saveCatalog(visible, true);
        saveCatalog(optedOut, true);
        saveCatalog(noCatalog, false);
        saveCatalog(inactive, true);
        publication.setPublished(visible.getId(), true);
        publication.setPublished(noCatalog.getId(), true);
        publication.setPublished(inactive.getId(), true);

        MvcResult search = mvc.perform(get("/v1/discovery/search").param("sort", "NAME"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "public, max-age=60"))
                .andExpect(header().exists("ETag"))
                .andExpect(header().string("X-RateLimit-Limit", "180"))
                .andExpect(jsonPath("$.merchants", hasSize(1)))
                .andExpect(jsonPath("$.merchants[0].merchantId", is(visible.getMerchantPublicId().toString())))
                .andExpect(jsonPath("$.merchants[0].name", is("Alpha Publicável")))
                .andExpect(jsonPath("$.merchants[0].availability", is("UNKNOWN")))
                .andExpect(jsonPath("$.merchants[0].fulfillmentOptions", empty()))
                .andExpect(jsonPath("$.merchants[0].featured", is(false)))
                .andExpect(jsonPath("$.merchants[0].catalogAvailable", is(false)))
                .andExpect(jsonPath("$.merchants[0].tenantId").doesNotExist())
                .andExpect(jsonPath("$.merchants[0].id").doesNotExist())
                .andReturn();

        mvc.perform(get("/v1/discovery/search")
                        .header("If-None-Match", search.getResponse().getHeader("ETag")))
                .andExpect(status().isNotModified())
                .andExpect(content().string(""));

        mvc.perform(get("/v1/discovery/home"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories", empty()))
                .andExpect(jsonPath("$.nearby.items", empty()))
                .andExpect(jsonPath("$.recommended.items", empty()))
                .andExpect(jsonPath("$.featured.items", empty()));

        mvc.perform(get("/v1/discovery/merchants/{id}", visible.getMerchantPublicId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.merchantId", is(visible.getMerchantPublicId().toString())))
                .andExpect(jsonPath("$.availability", is("UNKNOWN")))
                .andExpect(jsonPath("$.distanceMeters", nullValue()))
                .andExpect(jsonPath("$.rating", nullValue()))
                .andExpect(jsonPath("$.popularityScore", nullValue()))
                .andExpect(jsonPath("$.weeklySchedule", nullValue()));

        for (Tenant hidden : new Tenant[] {optedOut, noCatalog, inactive}) {
            mvc.perform(get("/v1/discovery/merchants/{id}", hidden.getMerchantPublicId()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code", is("MERCHANT_NOT_FOUND")))
                    .andExpect(jsonPath("$.error.message", is("Merchant não encontrado.")));
        }
    }

    @Test
    void rejectsTenantOverrideUnsupportedCapabilitiesUnknownParametersAndInvalidIds() throws Exception {
        mvc.perform(get("/v1/discovery/search").header("X-Tenant-Id", "1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is("INVALID_REQUEST")));
        mvc.perform(get("/v1/discovery/search").param("sort", "TOP_RATED"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is("SORT_NOT_SUPPORTED")));
        mvc.perform(get("/v1/discovery/home").param("municipalityId", "1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fieldErrors[0].code", is("UNKNOWN_PARAMETER")));
        mvc.perform(get("/v1/discovery/merchants/123"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fieldErrors[0].code", is("INVALID_UUID")));
    }

    private Tenant saveTenant(String name, String label, String suffix, TenantEstado state) {
        Tenant tenant = new Tenant();
        tenant.setNome(name);
        tenant.setSlug("discovery-" + label + "-" + suffix);
        tenant.setTenantCode(("D" + label + suffix).toUpperCase());
        tenant.setTipo(TenantTipo.RESTAURANTE);
        tenant.setEstado(state);
        return tenants.saveAndFlush(tenant);
    }

    private void saveCatalog(Tenant tenant, boolean published) {
        TenantCardapioConfig config = new TenantCardapioConfig();
        config.setTenant(tenant);
        config.setCardapioPublicado(published);
        cardapios.saveAndFlush(config);
    }
}

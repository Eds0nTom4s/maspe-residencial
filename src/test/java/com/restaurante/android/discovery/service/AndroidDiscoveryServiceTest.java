package com.restaurante.android.discovery.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.restaurante.android.discovery.dto.AndroidMerchantAvailability;
import com.restaurante.android.discovery.repository.AndroidDiscoveryMerchantProjection;
import com.restaurante.android.discovery.repository.AndroidDiscoveryReadRepository;
import com.restaurante.android.discovery.validation.ValidatedDiscoveryQuery;
import com.restaurante.model.enums.BusinessAccountEstado;
import com.restaurante.model.enums.SubscricaoEstado;
import com.restaurante.model.enums.TenantEstado;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class AndroidDiscoveryServiceTest {

    @Mock AndroidDiscoveryReadRepository repository;
    @Mock AndroidDiscoveryMerchantProjection alpha;
    @Mock AndroidDiscoveryMerchantProjection beta;

    private AndroidDiscoveryService service;

    @BeforeEach
    void setUp() {
        service = new AndroidDiscoveryService(repository, new MerchantDiscoveryPublicationPolicy());
    }

    @Test
    void homePopulatesOnlyRecommendedFromThePublicationSafePage() {
        UUID alphaId = UUID.fromString("11111111-1111-4111-8111-111111111111");
        UUID betaId = UUID.fromString("22222222-2222-4222-8222-222222222222");
        projection(alpha, alphaId, " Alpha ", true, 2L);
        projection(beta, betaId, "Beta", true, 0L);
        PageRequest pageable = PageRequest.of(0, 2);
        when(repository.findPublicMerchants(
                        TenantEstado.ATIVO,
                        BusinessAccountEstado.ATIVA,
                        SubscricaoEstado.ATIVA,
                        "",
                        "luanda",
                        pageable))
                .thenReturn(new PageImpl<>(List.of(alpha, beta), pageable, 3));

        var home = service.home(query("", "Luanda", 0, 2));

        assertThat(home.categories()).isEmpty();
        assertThat(home.nearby().items()).isEmpty();
        assertThat(home.featured().items()).isEmpty();
        assertThat(home.recommended().hasMore()).isTrue();
        assertThat(home.recommended().items())
                .extracting(item -> item.merchantId())
                .containsExactly(alphaId, betaId);
        assertThat(home.recommended().items().get(0).name()).isEqualTo("Alpha");
        assertThat(home.recommended().items().get(0).availability())
                .isEqualTo(AndroidMerchantAvailability.UNKNOWN);
        assertThat(home.recommended().items().get(0).catalogAvailable()).isTrue();
        assertThat(home.recommended().items().get(0).distanceMeters()).isNull();
        assertThat(home.recommended().items().get(0).rating()).isNull();
        assertThat(home.recommended().items().get(0).popularityScore()).isNull();
        assertThat(home.recommended().items().get(0).featured()).isFalse();
        assertThat(home.recommended().items().get(1).catalogAvailable()).isFalse();
    }

    @Test
    void homeAndSearchUseTheSameEligibilityQueryAndPreserveAnEmptyHome() {
        PageRequest pageable = PageRequest.of(3, 7);
        when(repository.findPublicMerchants(
                        TenantEstado.ATIVO,
                        BusinessAccountEstado.ATIVA,
                        SubscricaoEstado.ATIVA,
                        "",
                        null,
                        pageable))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));
        ValidatedDiscoveryQuery query = query("", null, 3, 7);

        var home = service.home(query);
        var search = service.search(query);

        assertThat(home.recommended().items()).isEmpty();
        assertThat(home.recommended().hasMore()).isFalse();
        assertThat(search.merchants()).isEmpty();
        assertThat(search.page()).isEqualTo(3);
        assertThat(search.pageSize()).isEqualTo(7);
        verify(repository, times(2)).findPublicMerchants(
                eq(TenantEstado.ATIVO),
                eq(BusinessAccountEstado.ATIVA),
                eq(SubscricaoEstado.ATIVA),
                eq(""),
                eq(null),
                eq(pageable));
    }

    private void projection(
            AndroidDiscoveryMerchantProjection projection,
            UUID id,
            String name,
            boolean catalogPublished,
            long activeCatalogItemCount) {
        when(projection.getMerchantId()).thenReturn(id);
        when(projection.getName()).thenReturn(name);
        when(projection.getCatalogPublished()).thenReturn(catalogPublished);
        when(projection.getActiveCatalogItemCount()).thenReturn(activeCatalogItemCount);
    }

    private ValidatedDiscoveryQuery query(
            String text, String municipality, int page, int pageSize) {
        return new ValidatedDiscoveryQuery(
                text, municipality, null, null, page, pageSize, "NAME");
    }
}

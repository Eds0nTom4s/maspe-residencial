package com.restaurante.platform.discovery.repository;

import com.restaurante.model.enums.TenantTipo;
import com.restaurante.platform.discovery.domain.DiscoveryError;
import com.restaurante.platform.discovery.domain.DiscoveryResult;
import com.restaurante.platform.discovery.domain.HomeDiscoveryContent;
import com.restaurante.platform.discovery.domain.MerchantCategory;
import com.restaurante.platform.discovery.domain.MerchantOverview;
import com.restaurante.platform.discovery.domain.MerchantSearchContent;
import com.restaurante.platform.discovery.domain.MerchantSection;
import com.restaurante.platform.discovery.domain.MerchantSummary;
import com.restaurante.platform.discovery.dto.HomeDiscoveryRequest;
import com.restaurante.platform.discovery.dto.MerchantRequest;
import com.restaurante.platform.discovery.dto.SearchDiscoveryRequest;
import com.restaurante.platform.discovery.mapper.PersistentDiscoveryMapper;
import com.restaurante.platform.discovery.repository.projection.MerchantDiscoveryProjection;
import com.restaurante.platform.discovery.repository.projection.MerchantLocationProjection;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Runtime Discovery adapter backed exclusively by the canonical persisted model. */
@Repository
@Transactional(readOnly = true)
public class PersistentDiscoveryRepository implements DiscoveryRepository {

    private final JpaDiscoveryReadRepository readRepository;
    private final PersistentDiscoveryMapper mapper;
    private final MerchantPublicationPolicy publicationPolicy;

    public PersistentDiscoveryRepository(
            JpaDiscoveryReadRepository readRepository,
            PersistentDiscoveryMapper mapper,
            MerchantPublicationPolicy publicationPolicy) {
        this.readRepository = readRepository;
        this.mapper = mapper;
        this.publicationPolicy = publicationPolicy;
    }

    @Override
    public DiscoveryResult<HomeDiscoveryContent> home(HomeDiscoveryRequest request) {
        return read(() -> {
            Page<MerchantDiscoveryProjection> page = findPage(
                    "", request.categoryId(), request.municipalityId(), pageable(request));
            List<MerchantSummary> merchants =
                    page.getContent().stream().map(mapper::toSummary).toList();
            HomeDiscoveryContent content = new HomeDiscoveryContent(
                    mapper.categories(),
                    new MerchantSection(List.of(), false),
                    new MerchantSection(merchants, page.hasNext()),
                    new MerchantSection(List.of(), false));
            return merchants.isEmpty()
                    ? new DiscoveryResult.Empty<>(content)
                    : new DiscoveryResult.Success<>(content);
        });
    }

    @Override
    public DiscoveryResult<MerchantSearchContent> search(SearchDiscoveryRequest request) {
        return read(() -> {
            Page<MerchantDiscoveryProjection> page = findPage(
                    request.query(),
                    request.categoryId(),
                    request.municipalityId(),
                    pageable(request));
            List<MerchantSummary> merchants =
                    page.getContent().stream().map(mapper::toSummary).toList();
            MerchantSearchContent content = new MerchantSearchContent(
                    mapper.categories(),
                    merchants,
                    request.page(),
                    request.pageSize(),
                    boundedTotal(page.getTotalElements()),
                    page.hasNext());
            return merchants.isEmpty()
                    ? new DiscoveryResult.Empty<>(content)
                    : new DiscoveryResult.Success<>(content);
        });
    }

    @Override
    public DiscoveryResult<MerchantOverview> merchant(MerchantRequest request) {
        return read(() -> readRepository
                .findPublicMerchant(
                        request.merchantId(),
                        publicationPolicy.requiredTenantState(),
                        publicationPolicy.requiredAccountState(),
                        publicationPolicy.requiresPublishedCatalog(),
                        publicationPolicy.requiresActiveOperationalLocation())
                .<DiscoveryResult<MerchantOverview>>map(source -> {
                    MerchantLocationProjection location = readRepository
                            .findPrimaryOperationalLocation(
                                    request.merchantId(), PageRequest.of(0, 1))
                            .stream()
                            .findFirst()
                            .orElse(null);
                    return new DiscoveryResult.Success<>(mapper.toOverview(source, location));
                })
                .orElseGet(() -> new DiscoveryResult.Error<>(
                        DiscoveryError.NOT_FOUND,
                        "Comerciante não encontrado.")));
    }

    private Page<MerchantDiscoveryProjection> findPage(
            String query, String categoryId, String municipalityId, Pageable pageable) {
        MerchantCategory category = categoryId == null ? null : mapper.category(categoryId);
        Set<TenantTipo> types = mapper.tenantTypes(category);
        if ((categoryId != null && category == null) || types.isEmpty()) {
            return Page.empty(pageable);
        }
        return readRepository.findPublicMerchants(
                publicationPolicy.requiredTenantState(),
                publicationPolicy.requiredAccountState(),
                publicationPolicy.requiresPublishedCatalog(),
                publicationPolicy.requiresActiveOperationalLocation(),
                escapeLike(query),
                types,
                municipalityId,
                pageable);
    }

    private Pageable pageable(HomeDiscoveryRequest request) {
        return PageRequest.of(request.page(), request.pageSize(), stableSort());
    }

    private Pageable pageable(SearchDiscoveryRequest request) {
        return PageRequest.of(request.page(), request.pageSize(), stableSort());
    }

    private Sort stableSort() {
        // NAME is the only supported public sort; slug is the stable public tie-breaker.
        return Sort.by(Sort.Order.asc("nome"), Sort.Order.asc("slug"));
    }

    private String escapeLike(String query) {
        if (query == null) {
            return "";
        }
        return query.trim()
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
    }

    private int boundedTotal(long total) {
        return total > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) total;
    }

    private <T> DiscoveryResult<T> read(Supplier<DiscoveryResult<T>> operation) {
        try {
            return operation.get();
        } catch (DataAccessResourceFailureException exception) {
            return new DiscoveryResult.Error<>(
                    DiscoveryError.SERVICE_UNAVAILABLE,
                    "O serviço Discovery está temporariamente indisponível.");
        } catch (DataAccessException exception) {
            return new DiscoveryResult.Error<>(
                    DiscoveryError.UNKNOWN, "Não foi possível consultar o Discovery.");
        }
    }
}

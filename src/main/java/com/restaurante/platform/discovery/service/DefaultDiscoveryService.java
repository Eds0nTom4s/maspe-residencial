package com.restaurante.platform.discovery.service;

import com.restaurante.platform.discovery.domain.DiscoveryResult;
import com.restaurante.platform.discovery.dto.DiscoveryHomeResponse;
import com.restaurante.platform.discovery.dto.HomeDiscoveryRequest;
import com.restaurante.platform.discovery.dto.MerchantOverviewResponse;
import com.restaurante.platform.discovery.dto.MerchantRequest;
import com.restaurante.platform.discovery.dto.MerchantSearchResponse;
import com.restaurante.platform.discovery.dto.SearchDiscoveryRequest;
import com.restaurante.platform.discovery.exception.DiscoveryValidationException;
import com.restaurante.platform.discovery.mapper.DiscoveryDtoMapper;
import com.restaurante.platform.discovery.repository.DiscoveryRepository;
import com.restaurante.platform.discovery.validation.DiscoveryRequestValidator;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DefaultDiscoveryService implements DiscoveryService {

    private final DiscoveryRepository repository;
    private final DiscoveryRequestValidator validator;
    private final DiscoveryDtoMapper mapper;

    public DefaultDiscoveryService(
            DiscoveryRepository repository,
            DiscoveryRequestValidator validator,
            DiscoveryDtoMapper mapper) {
        this.repository = repository;
        this.validator = validator;
        this.mapper = mapper;
    }

    @Override
    public DiscoveryResult<DiscoveryHomeResponse> home(HomeDiscoveryRequest request) {
        return validated(() -> repository.home(validator.validate(request)).map(mapper::toResponse));
    }

    @Override
    public DiscoveryResult<MerchantSearchResponse> search(SearchDiscoveryRequest request) {
        return validated(() -> repository.search(validator.validate(request)).map(mapper::toResponse));
    }

    @Override
    public DiscoveryResult<MerchantOverviewResponse> merchant(MerchantRequest request) {
        return validated(
                () -> repository.merchant(validator.validate(request)).map(mapper::toResponse));
    }

    private <T> DiscoveryResult<T> validated(Supplier<DiscoveryResult<T>> operation) {
        try {
            return operation.get();
        } catch (DiscoveryValidationException exception) {
            return new DiscoveryResult.Error<>(
                    exception.getReason(), exception.getMessage());
        }
    }
}

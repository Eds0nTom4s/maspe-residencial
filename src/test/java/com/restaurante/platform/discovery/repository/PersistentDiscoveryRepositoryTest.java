package com.restaurante.platform.discovery.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.restaurante.platform.discovery.domain.DiscoveryError;
import com.restaurante.platform.discovery.domain.DiscoveryResult;
import com.restaurante.platform.discovery.dto.HomeDiscoveryRequest;
import com.restaurante.platform.discovery.dto.MerchantRequest;
import com.restaurante.platform.discovery.mapper.PersistentDiscoveryMapper;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.InvalidDataAccessResourceUsageException;

class PersistentDiscoveryRepositoryTest {

    private JpaDiscoveryReadRepository readRepository;
    private PersistentDiscoveryRepository repository;

    @BeforeEach
    void setUp() {
        readRepository = mock(JpaDiscoveryReadRepository.class);
        repository = new PersistentDiscoveryRepository(
                readRepository,
                new PersistentDiscoveryMapper(),
                new MerchantPublicationPolicy());
    }

    @Test
    void missingOrUnpublishedMerchantIsNotFound() {
        when(readRepository.findPublicMerchant(
                        anyString(), any(), any(), anyBoolean(), anyBoolean()))
                .thenReturn(Optional.empty());

        DiscoveryResult.Error<?> result = assertInstanceOf(
                DiscoveryResult.Error.class,
                repository.merchant(new MerchantRequest("missing")));

        assertEquals(DiscoveryError.NOT_FOUND, result.reason());
    }

    @Test
    void unavailableDatabaseIsExplicitServiceUnavailable() {
        when(readRepository.findPublicMerchants(
                        any(), any(), anyBoolean(), anyBoolean(), anyString(), any(), any(), any()))
                .thenThrow(new DataAccessResourceFailureException("offline"));

        DiscoveryResult.Error<?> result = assertInstanceOf(
                DiscoveryResult.Error.class,
                repository.home(new HomeDiscoveryRequest(null, null, null, null, 0, 20, "NAME")));

        assertEquals(DiscoveryError.SERVICE_UNAVAILABLE, result.reason());
    }

    @Test
    void persistenceQueryFailureIsExplicitUnknown() {
        when(readRepository.findPublicMerchants(
                        any(), any(), anyBoolean(), anyBoolean(), anyString(), any(), any(), any()))
                .thenThrow(new InvalidDataAccessResourceUsageException("invalid query"));

        DiscoveryResult.Error<?> result = assertInstanceOf(
                DiscoveryResult.Error.class,
                repository.home(new HomeDiscoveryRequest(null, null, null, null, 0, 20, "NAME")));

        assertEquals(DiscoveryError.UNKNOWN, result.reason());
    }
}

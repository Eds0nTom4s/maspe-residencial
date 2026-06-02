package com.restaurante.testsupport;

import com.restaurante.device.capability.entity.DeviceOperationalCapabilityEntity;
import com.restaurante.device.capability.repository.DeviceOperationalCapabilityRepository;
import com.restaurante.dto.response.ProvisionarTenantResponse;
import com.restaurante.model.entity.DispositivoOperacional;
import com.restaurante.model.enums.DeviceCapability;
import com.restaurante.model.enums.DispositivoStatus;
import com.restaurante.model.enums.TenantUserEstado;
import com.restaurante.model.enums.TenantUserRole;
import com.restaurante.repository.DispositivoOperacionalRepository;
import com.restaurante.repository.TenantRepository;
import com.restaurante.repository.UserRepository;
import com.restaurante.security.JwtTokenProvider;
import com.restaurante.service.device.DeviceTokenService;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.Collection;

public abstract class DeviceAuthIntegrationTestSupport extends PostgresTestcontainersConfig {

    @Autowired protected DeviceTokenService deviceTokenService;
    @Autowired protected DeviceOperationalCapabilityRepository deviceCapabilityRepository;
    @Autowired protected DispositivoOperacionalRepository dispositivoOperacionalRepository;
    @Autowired protected TenantRepository tenantRepository;
    @Autowired protected UserRepository userRepository;
    @Autowired protected JwtTokenProvider jwtTokenProvider;

    protected String issueTenantOwnerToken(ProvisionarTenantResponse prov) {
        return issueTenantToken(prov, TenantUserRole.TENANT_OWNER);
    }

    protected String issueTenantToken(ProvisionarTenantResponse prov, TenantUserRole role) {
        var tenant = tenantRepository.findById(prov.getTenantId()).orElseThrow();
        var user = userRepository.findById(prov.getOwnerUserId()).orElseThrow();
        return jwtTokenProvider.generateTenantScopedToken(
                user,
                tenant,
                role,
                TenantUserEstado.ATIVO,
                1,
                null
        );
    }

    protected String activateDeviceForTest(DispositivoOperacional device, Collection<DeviceCapability> capabilities) {
        String rawToken = deviceTokenService.generateDeviceToken();
        device.setDeviceTokenHash(deviceTokenService.hashToHex(rawToken));
        device.setDeviceTokenIssuedAt(LocalDateTime.now());
        device.setDeviceTokenRevokedAt(null);
        device.setStatus(DispositivoStatus.ATIVO);
        if (device.getTokenVersion() == null) {
            device.setTokenVersion(1);
        }
        if (device.getAtivadoEm() == null) {
            device.setAtivadoEm(LocalDateTime.now());
        }
        dispositivoOperacionalRepository.saveAndFlush(device);

        if (capabilities != null) {
            for (DeviceCapability capability : capabilities) {
                DeviceOperationalCapabilityEntity entity = deviceCapabilityRepository
                        .findByTenant_IdAndDispositivoOperacional_IdAndCapability(
                                device.getTenant().getId(),
                                device.getId(),
                                capability
                        )
                        .orElseGet(() -> {
                            DeviceOperationalCapabilityEntity created = new DeviceOperationalCapabilityEntity();
                            created.setTenant(device.getTenant());
                            created.setDispositivoOperacional(device);
                            created.setCapability(capability);
                            return created;
                        });
                entity.setEnabled(true);
                entity.setSource("TEST_REAL_AUTH");
                deviceCapabilityRepository.save(entity);
            }
        }

        return rawToken;
    }

    protected String deviceAuthorization(String rawToken) {
        return "Device " + rawToken;
    }
}

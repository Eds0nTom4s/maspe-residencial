package com.restaurante.service.device;

import com.restaurante.exception.DeviceForbiddenException;
import com.restaurante.exception.DeviceUnauthorizedException;
import com.restaurante.exception.ResourceNotFoundException;
import com.restaurante.model.entity.DispositivoOperacional;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.enums.DispositivoStatus;
import com.restaurante.model.enums.TenantEstado;
import com.restaurante.repository.DispositivoOperacionalRepository;
import com.restaurante.repository.TenantRepository;
import com.restaurante.security.device.DevicePrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeviceAuthService {

    private final DeviceTokenService deviceTokenService;
    private final DispositivoOperacionalRepository dispositivoOperacionalRepository;
    private final TenantRepository tenantRepository;

    @Transactional(readOnly = true)
    public DevicePrincipal authenticateDeviceHeader(String authorizationHeader) {
        String raw = extractDeviceToken(authorizationHeader);
        String hash = deviceTokenService.hashToHex(raw);

        DispositivoOperacional dispositivo = dispositivoOperacionalRepository.findByDeviceTokenHash(hash)
                .orElseThrow(() -> new DeviceUnauthorizedException("Device token inválido."));

        if (dispositivo.getStatus() != DispositivoStatus.ATIVO) {
            throw new DeviceForbiddenException("Dispositivo não está ativo.");
        }

        Tenant tenant = tenantRepository.findById(dispositivo.getTenant().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Recurso não encontrado."));
        if (tenant.getEstado() != TenantEstado.ATIVO) {
            throw new DeviceForbiddenException("Tenant não está ativo.");
        }

        return new DevicePrincipal(
                dispositivo.getId(),
                dispositivo.getTenant().getId(),
                tenant.getTenantCode(),
                dispositivo.getInstituicao().getId(),
                dispositivo.getUnidadeAtendimento() != null ? dispositivo.getUnidadeAtendimento().getId() : null,
                dispositivo.getTipo(),
                dispositivo.getStatus(),
                DeviceCapabilities.forTipo(dispositivo.getTipo())
        );
    }

    private String extractDeviceToken(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            throw new DeviceUnauthorizedException("Authorization: Device <token> é obrigatório.");
        }
        String trimmed = authorizationHeader.trim();
        if (!trimmed.regionMatches(true, 0, "Device ", 0, "Device ".length())) {
            throw new DeviceUnauthorizedException("Authorization: Device <token> é obrigatório.");
        }
        String token = trimmed.substring("Device ".length()).trim();
        if (token.isBlank()) {
            throw new DeviceUnauthorizedException("Device token inválido.");
        }
        return token;
    }
}


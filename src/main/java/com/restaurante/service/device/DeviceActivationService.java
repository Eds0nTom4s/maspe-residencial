package com.restaurante.service.device;

import com.restaurante.config.DeviceProperties;
import com.restaurante.dto.request.DeviceActivationRequest;
import com.restaurante.dto.request.DeviceHeartbeatRequest;
import com.restaurante.dto.response.DeviceActivationResponse;
import com.restaurante.dto.response.DeviceConfigResponse;
import com.restaurante.dto.response.DeviceHeartbeatResponse;
import com.restaurante.dto.response.DeviceTokenRotateResponse;
import com.restaurante.exception.DeviceForbiddenException;
import com.restaurante.exception.DeviceUnauthorizedException;
import com.restaurante.exception.ResourceNotFoundException;
import com.restaurante.model.entity.DispositivoOperacional;
import com.restaurante.model.entity.Instituicao;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.UnidadeAtendimento;
import com.restaurante.model.enums.DispositivoStatus;
import com.restaurante.model.enums.DeviceEventStatus;
import com.restaurante.model.enums.DeviceEventType;
import com.restaurante.model.enums.TenantEstado;
import com.restaurante.repository.DispositivoOperacionalRepository;
import com.restaurante.repository.InstituicaoRepository;
import com.restaurante.repository.TenantRepository;
import com.restaurante.repository.UnidadeAtendimentoRepository;
import com.restaurante.security.device.DevicePrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class DeviceActivationService {

    private final DeviceProperties deviceProperties;
    private final DeviceTokenService deviceTokenService;
    private final DeviceAuthService deviceAuthService;
    private final DispositivoOperacionalRepository dispositivoOperacionalRepository;
    private final TenantRepository tenantRepository;
    private final InstituicaoRepository instituicaoRepository;
    private final UnidadeAtendimentoRepository unidadeAtendimentoRepository;
    private final DeviceEventLogService deviceEventLogService;

    @Transactional
    public DeviceActivationResponse ativar(DeviceActivationRequest request, String userAgent, String ip) {
        String activationHash = deviceTokenService.hashToHex(request.getActivationCode().trim().toUpperCase());

        DispositivoOperacional dispositivo = dispositivoOperacionalRepository.findByActivationCodeHash(activationHash)
                .orElseThrow(() -> new DeviceUnauthorizedException("Código de ativação inválido."));

        if (dispositivo.getStatus() == DispositivoStatus.REVOGADO) {
            throw new DeviceForbiddenException("Dispositivo revogado.");
        }

        if (dispositivo.getActivationCodeExpiresAt() != null && dispositivo.getActivationCodeExpiresAt().isBefore(LocalDateTime.now())) {
            dispositivo.setStatus(DispositivoStatus.EXPIRADO);
            dispositivo.setActivationCodeHash(null);
            dispositivo.setActivationCodeExpiresAt(null);
            dispositivoOperacionalRepository.save(dispositivo);
            throw new DeviceUnauthorizedException("Código de ativação expirado.");
        }

        if (request.getCodigo() != null && !request.getCodigo().isBlank()) {
            String codigo = request.getCodigo().trim();
            if (!codigo.equalsIgnoreCase(dispositivo.getCodigo())) {
                throw new DeviceUnauthorizedException("Código de ativação inválido.");
            }
        }

        Tenant tenant = tenantRepository.findById(dispositivo.getTenant().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Recurso não encontrado."));
        if (tenant.getEstado() != TenantEstado.ATIVO) {
            throw new DeviceForbiddenException("Tenant não está ativo.");
        }

        String rawDeviceToken = deviceTokenService.generateDeviceToken();
        String deviceTokenHash = deviceTokenService.hashToHex(rawDeviceToken);

        dispositivo.setDeviceTokenHash(deviceTokenHash);
        dispositivo.setDeviceTokenIssuedAt(LocalDateTime.now());
        dispositivo.setDeviceTokenRevokedAt(null);
        dispositivo.setStatus(DispositivoStatus.ATIVO);
        dispositivo.setAtivadoEm(LocalDateTime.now());

        // invalida activation code
        dispositivo.setActivationCodeHash(null);
        dispositivo.setActivationCodeExpiresAt(null);

        // metadata
        if (request.getAppVersion() != null) dispositivo.setAppVersion(request.getAppVersion());
        if (request.getPlatform() != null) dispositivo.setPlatform(request.getPlatform());
        if (request.getModeloDispositivo() != null) dispositivo.setModeloDispositivo(request.getModeloDispositivo());
        if (request.getFabricante() != null) dispositivo.setFabricante(request.getFabricante());
        if (userAgent != null) dispositivo.setUserAgent(userAgent);
        if (ip != null) dispositivo.setUltimoIp(ip);

        dispositivoOperacionalRepository.save(dispositivo);

        return new DeviceActivationResponse(
                rawDeviceToken,
                dispositivo.getId(),
                tenant.getId(),
                tenant.getTenantCode(),
                dispositivo.getInstituicao().getId(),
                dispositivo.getUnidadeAtendimento() != null ? dispositivo.getUnidadeAtendimento().getId() : null,
                dispositivo.getTipo(),
                dispositivo.getStatus(),
                DeviceCapabilities.forTipo(dispositivo.getTipo())
        );
    }

    @Transactional
    public DeviceHeartbeatResponse heartbeat(String authorizationHeader, DeviceHeartbeatRequest request, String userAgent, String ip) {
        DevicePrincipal principal = deviceAuthService.authenticateDeviceHeader(authorizationHeader, userAgent, ip);
        DispositivoOperacional dispositivo = dispositivoOperacionalRepository.findById(principal.dispositivoId())
                .orElseThrow(() -> new ResourceNotFoundException("Recurso não encontrado."));

        dispositivo.setUltimoHeartbeatEm(LocalDateTime.now());
        if (request != null && request.getAppVersion() != null) dispositivo.setAppVersion(request.getAppVersion());
        if (userAgent != null) dispositivo.setUserAgent(userAgent);
        if (ip != null) dispositivo.setUltimoIp(ip);
        dispositivoOperacionalRepository.save(dispositivo);

        return new DeviceHeartbeatResponse("OK", LocalDateTime.now(), dispositivo.getStatus());
    }

    @Transactional
    public DeviceConfigResponse config(String authorizationHeader, String userAgent, String ip) {
        DevicePrincipal principal = deviceAuthService.authenticateDeviceHeader(authorizationHeader, userAgent, ip);

        DispositivoOperacional dispositivo = dispositivoOperacionalRepository.findById(principal.dispositivoId())
                .orElseThrow(() -> new ResourceNotFoundException("Recurso não encontrado."));

        Tenant tenant = tenantRepository.findById(principal.tenantId()).orElseThrow(() -> new ResourceNotFoundException("Recurso não encontrado."));
        Instituicao inst = instituicaoRepository.findByIdAndTenantId(principal.instituicaoId(), principal.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Recurso não encontrado."));

        UnidadeAtendimento unidade = null;
        if (principal.unidadeAtendimentoId() != null) {
            unidade = unidadeAtendimentoRepository.findByIdAndTenantId(principal.unidadeAtendimentoId(), principal.tenantId())
                    .orElseThrow(() -> new ResourceNotFoundException("Recurso não encontrado."));
        }

        deviceEventLogService.log(
                principal.tenantId(),
                principal.dispositivoId(),
                DeviceEventType.CONFIG_FETCHED,
                DeviceEventStatus.SUCCESS,
                "Config fetch",
                null,
                ip,
                userAgent
        );

        return new DeviceConfigResponse(
                new DeviceConfigResponse.TenantInfo(tenant.getId(), tenant.getNome(), tenant.getTenantCode()),
                new DeviceConfigResponse.InstituicaoInfo(inst.getId(), inst.getNome()),
                unidade != null ? new DeviceConfigResponse.UnidadeAtendimentoInfo(unidade.getId(), unidade.getNome()) : null,
                new DeviceConfigResponse.DispositivoInfo(
                        dispositivo.getId(),
                        dispositivo.getNome(),
                        dispositivo.getCodigo(),
                        dispositivo.getTipo(),
                        dispositivo.getStatus(),
                        dispositivo.getAppVersion(),
                        dispositivo.getPlatform(),
                        dispositivo.getUltimoHeartbeatEm(),
                        dispositivo.getAtivadoEm()
                ),
                principal.capabilities()
        );
    }

    @Transactional
    public DeviceTokenRotateResponse rotateToken(String authorizationHeader, String userAgent, String ip) {
        DevicePrincipal principal = deviceAuthService.authenticateDeviceHeader(authorizationHeader, userAgent, ip);
        DispositivoOperacional dispositivo = dispositivoOperacionalRepository.findById(principal.dispositivoId())
                .orElseThrow(() -> new ResourceNotFoundException("Recurso não encontrado."));

        if (dispositivo.getStatus() != DispositivoStatus.ATIVO) {
            throw new DeviceForbiddenException("Dispositivo não está ativo.");
        }

        String rawDeviceToken = deviceTokenService.generateDeviceToken();
        String deviceTokenHash = deviceTokenService.hashToHex(rawDeviceToken);

        dispositivo.setDeviceTokenHash(deviceTokenHash);
        dispositivo.setDeviceTokenIssuedAt(LocalDateTime.now());
        dispositivo.setDeviceTokenRevokedAt(null);
        dispositivo.setTokenVersion(dispositivo.getTokenVersion() != null ? dispositivo.getTokenVersion() + 1 : 2);
        dispositivo.setLastTokenRotationAt(LocalDateTime.now());
        dispositivoOperacionalRepository.save(dispositivo);

        deviceEventLogService.log(
                principal.tenantId(),
                principal.dispositivoId(),
                DeviceEventType.TOKEN_ROTATED,
                DeviceEventStatus.SUCCESS,
                "Token rotated",
                java.util.Map.of("tokenVersion", dispositivo.getTokenVersion()),
                ip,
                userAgent
        );

        return new DeviceTokenRotateResponse(
                rawDeviceToken,
                dispositivo.getTokenVersion(),
                dispositivo.getLastTokenRotationAt()
        );
    }

    public LocalDateTime activationExpiresAtNow() {
        return LocalDateTime.now().plusMinutes(Math.max(1, deviceProperties.getActivationCodeExpirationMinutes()));
    }
}

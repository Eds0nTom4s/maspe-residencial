package com.restaurante.service.tenantadmin;

import com.restaurante.dto.request.RegistrarDispositivoRequest;
import com.restaurante.dto.response.DispositivoOperacionalResponse;
import com.restaurante.dto.response.RegistrarDispositivoResponse;
import com.restaurante.exception.BusinessException;
import com.restaurante.exception.ResourceNotFoundException;
import com.restaurante.model.entity.DispositivoOperacional;
import com.restaurante.model.entity.Instituicao;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.UnidadeAtendimento;
import com.restaurante.model.entity.UnidadeProducao;
import com.restaurante.model.enums.DispositivoStatus;
import com.restaurante.repository.DispositivoOperacionalRepository;
import com.restaurante.repository.InstituicaoRepository;
import com.restaurante.repository.TenantRepository;
import com.restaurante.repository.UnidadeAtendimentoRepository;
import com.restaurante.repository.UnidadeProducaoRepository;
import com.restaurante.security.tenant.TenantContext;
import com.restaurante.security.tenant.TenantGuard;
import com.restaurante.service.TenantLimitService;
import com.restaurante.service.device.DeviceActivationService;
import com.restaurante.service.device.DeviceTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class TenantAdminDispositivoService {

    private final TenantGuard tenantGuard;
    private final TenantLimitService tenantLimitService;
    private final DeviceTokenService deviceTokenService;
    private final DeviceActivationService deviceActivationService;

    private final DispositivoOperacionalRepository dispositivoOperacionalRepository;
    private final TenantRepository tenantRepository;
    private final InstituicaoRepository instituicaoRepository;
    private final UnidadeAtendimentoRepository unidadeAtendimentoRepository;
    private final UnidadeProducaoRepository unidadeProducaoRepository;

    @Transactional
    public RegistrarDispositivoResponse registrar(RegistrarDispositivoRequest request) {
        TenantContext ctx = requireTenantContext();
        tenantLimitService.assertCanCreateDispositivo(ctx.tenantId(), 1);

        String codigo = request.getCodigo().trim().toUpperCase();
        if (dispositivoOperacionalRepository.existsByTenantIdAndCodigo(ctx.tenantId(), codigo)) {
            throw new BusinessException("Código de dispositivo já existe neste tenant.");
        }

        Tenant tenant = tenantRepository.findById(ctx.tenantId()).orElseThrow(() -> new ResourceNotFoundException("Recurso não encontrado."));
        Instituicao instituicao = instituicaoRepository.findByIdAndTenantId(request.getInstituicaoId(), ctx.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Recurso não encontrado."));

        UnidadeAtendimento unidade = null;
        if (request.getUnidadeAtendimentoId() != null) {
            unidade = unidadeAtendimentoRepository.findByIdAndTenantId(request.getUnidadeAtendimentoId(), ctx.tenantId())
                    .orElseThrow(() -> new ResourceNotFoundException("Recurso não encontrado."));
            if (unidade.getInstituicao() == null || !unidade.getInstituicao().getId().equals(instituicao.getId())) {
                throw new BusinessException("Unidade de atendimento não pertence à instituição informada.");
            }
        }

        UnidadeProducao unidadeProducao = null;
        if (request.getUnidadeProducaoId() != null) {
            unidadeProducao = unidadeProducaoRepository.findByIdAndTenantId(request.getUnidadeProducaoId(), ctx.tenantId())
                    .orElseThrow(() -> new ResourceNotFoundException("Recurso não encontrado."));
            if (unidadeProducao.getInstituicao() == null || !unidadeProducao.getInstituicao().getId().equals(instituicao.getId())) {
                throw new BusinessException("Unidade de produção não pertence à instituição informada.");
            }
            if (unidade != null && unidadeProducao.getUnidadeAtendimento() != null
                    && !unidadeProducao.getUnidadeAtendimento().getId().equals(unidade.getId())) {
                throw new BusinessException("Unidade de produção não pertence à unidade de atendimento informada.");
            }
        }

        String activationCode = deviceTokenService.generateActivationCode();
        String activationHash = deviceTokenService.hashToHex(activationCode);
        LocalDateTime expiresAt = deviceActivationService.activationExpiresAtNow();

        DispositivoOperacional dispositivo = new DispositivoOperacional();
        dispositivo.setTenant(tenant);
        dispositivo.setInstituicao(instituicao);
        dispositivo.setUnidadeAtendimento(unidade);
        dispositivo.setUnidadeProducao(unidadeProducao);
        dispositivo.setNome(request.getNome().trim());
        dispositivo.setCodigo(codigo);
        dispositivo.setTipo(request.getTipo());
        dispositivo.setStatus(DispositivoStatus.PENDENTE_ATIVACAO);
        dispositivo.setActivationCodeHash(activationHash);
        dispositivo.setActivationCodeExpiresAt(expiresAt);
        dispositivo.setDeviceTokenHash(null);
        dispositivo.setDeviceTokenIssuedAt(null);
        dispositivo.setDeviceTokenRevokedAt(null);

        dispositivoOperacionalRepository.save(dispositivo);

        return new RegistrarDispositivoResponse(
                dispositivo.getId(),
                dispositivo.getNome(),
                dispositivo.getCodigo(),
                dispositivo.getTipo(),
                dispositivo.getStatus(),
                activationCode,
                expiresAt
        );
    }

    @Transactional(readOnly = true)
    public Page<DispositivoOperacionalResponse> listar(Pageable pageable) {
        TenantContext ctx = requireTenantContext();
        return dispositivoOperacionalRepository.findByTenantId(ctx.tenantId(), pageable).map(this::toDto);
    }

    @Transactional(readOnly = true)
    public DispositivoOperacionalResponse buscar(Long id) {
        TenantContext ctx = requireTenantContext();
        DispositivoOperacional d = dispositivoOperacionalRepository.findByIdAndTenantId(id, ctx.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Recurso não encontrado."));
        return toDto(d);
    }

    @Transactional
    public DispositivoOperacionalResponse definirUnidadeProducao(Long dispositivoId, Long unidadeProducaoId) {
        TenantContext ctx = requireTenantContext();
        DispositivoOperacional d = dispositivoOperacionalRepository.findByIdAndTenantId(dispositivoId, ctx.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Recurso não encontrado."));

        UnidadeProducao unidadeProducao = unidadeProducaoRepository.findByIdAndTenantId(unidadeProducaoId, ctx.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Recurso não encontrado."));

        if (d.getInstituicao() == null || unidadeProducao.getInstituicao() == null
                || !d.getInstituicao().getId().equals(unidadeProducao.getInstituicao().getId())) {
            throw new BusinessException("Unidade de produção não pertence à instituição do dispositivo.");
        }

        d.setUnidadeProducao(unidadeProducao);
        dispositivoOperacionalRepository.save(d);
        return toDto(d);
    }

    @Transactional
    public DispositivoOperacionalResponse suspender(Long id) {
        TenantContext ctx = requireTenantContext();
        DispositivoOperacional d = dispositivoOperacionalRepository.findByIdAndTenantId(id, ctx.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Recurso não encontrado."));
        if (d.getStatus() == DispositivoStatus.REVOGADO) return toDto(d);

        d.setStatus(DispositivoStatus.SUSPENSO);
        d.setDeviceTokenRevokedAt(LocalDateTime.now());
        d.setDeviceTokenHash(null);
        dispositivoOperacionalRepository.save(d);
        return toDto(d);
    }

    @Transactional
    public DispositivoOperacionalResponse revogar(Long id) {
        TenantContext ctx = requireTenantContext();
        DispositivoOperacional d = dispositivoOperacionalRepository.findByIdAndTenantId(id, ctx.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Recurso não encontrado."));
        if (d.getStatus() == DispositivoStatus.REVOGADO) return toDto(d);

        d.setStatus(DispositivoStatus.REVOGADO);
        d.setRevogadoEm(LocalDateTime.now());
        d.setDeviceTokenRevokedAt(LocalDateTime.now());
        d.setDeviceTokenHash(null);
        d.setActivationCodeHash(null);
        d.setActivationCodeExpiresAt(null);
        dispositivoOperacionalRepository.save(d);
        return toDto(d);
    }

    @Transactional
    public RegistrarDispositivoResponse reemitirActivationCode(Long id) {
        TenantContext ctx = requireTenantContext();
        DispositivoOperacional d = dispositivoOperacionalRepository.findByIdAndTenantId(id, ctx.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Recurso não encontrado."));
        if (d.getStatus() == DispositivoStatus.REVOGADO) {
            throw new BusinessException("Dispositivo revogado não pode reemitir activation code.");
        }

        String activationCode = deviceTokenService.generateActivationCode();
        String activationHash = deviceTokenService.hashToHex(activationCode);
        LocalDateTime expiresAt = deviceActivationService.activationExpiresAtNow();

        d.setActivationCodeHash(activationHash);
        d.setActivationCodeExpiresAt(expiresAt);
        if (d.getStatus() == DispositivoStatus.EXPIRADO) {
            d.setStatus(DispositivoStatus.PENDENTE_ATIVACAO);
        }
        dispositivoOperacionalRepository.save(d);

        return new RegistrarDispositivoResponse(
                d.getId(),
                d.getNome(),
                d.getCodigo(),
                d.getTipo(),
                d.getStatus(),
                activationCode,
                expiresAt
        );
    }

    private TenantContext requireTenantContext() {
        TenantContext ctx = tenantGuard.requireContext();
        if (ctx.tenantId() == null) {
            throw new ResourceNotFoundException("Recurso não encontrado.");
        }
        tenantGuard.assertCurrentUserBelongsToTenant(ctx.tenantId());
        tenantGuard.assertTenantActive(ctx.tenantId());
        return ctx;
    }

    private DispositivoOperacionalResponse toDto(DispositivoOperacional d) {
        return new DispositivoOperacionalResponse(
                d.getId(),
                d.getNome(),
                d.getCodigo(),
                d.getTipo(),
                d.getStatus(),
                d.getInstituicao() != null ? d.getInstituicao().getId() : null,
                d.getUnidadeAtendimento() != null ? d.getUnidadeAtendimento().getId() : null,
                d.getUnidadeProducao() != null ? d.getUnidadeProducao().getId() : null,
                d.getUltimoHeartbeatEm(),
                d.getAppVersion(),
                d.getPlatform(),
                d.getAtivadoEm(),
                d.getRevogadoEm()
        );
    }
}

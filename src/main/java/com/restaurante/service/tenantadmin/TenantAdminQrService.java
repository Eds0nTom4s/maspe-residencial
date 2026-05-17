package com.restaurante.service.tenantadmin;

import com.restaurante.dto.response.TenantMesaResponse;
import com.restaurante.dto.response.TenantQrCodeResponse;
import com.restaurante.exception.ResourceNotFoundException;
import com.restaurante.model.entity.Mesa;
import com.restaurante.model.entity.QrCodeOperacional;
import com.restaurante.model.enums.QrCodeOperacionalTipo;
import com.restaurante.repository.MesaRepository;
import com.restaurante.repository.QrCodeOperacionalRepository;
import com.restaurante.security.tenant.TenantContext;
import com.restaurante.security.tenant.TenantGuard;
import com.restaurante.service.QrCodeOperacionalService;
import com.restaurante.service.TenantLimitService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TenantAdminQrService {

    private final TenantGuard tenantGuard;
    private final TenantLimitService tenantLimitService;
    private final QrCodeOperacionalRepository qrCodeOperacionalRepository;
    private final QrCodeOperacionalService qrCodeOperacionalService;
    private final MesaRepository mesaRepository;

    @Value("${consuma.public-base-url:http://localhost:8080}")
    private String publicBaseUrl;

    @Transactional(readOnly = true)
    public List<TenantQrCodeResponse> listarQrCodes() {
        TenantContext ctx = requireTenantContext();
        return qrCodeOperacionalRepository.findByTenantId(ctx.tenantId()).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public TenantQrCodeResponse buscarQrCode(Long id) {
        TenantContext ctx = requireTenantContext();
        QrCodeOperacional qr = qrCodeOperacionalRepository.findByIdAndTenantId(id, ctx.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Recurso não encontrado."));
        return toDto(qr);
    }

    @Transactional
    public TenantQrCodeResponse revogar(Long id) {
        TenantContext ctx = requireTenantContext();
        QrCodeOperacional qr = qrCodeOperacionalRepository.findByIdAndTenantId(id, ctx.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Recurso não encontrado."));
        if (Boolean.TRUE.equals(qr.getRevogado()) || Boolean.FALSE.equals(qr.getAtivo())) {
            return toDto(qr);
        }
        qrCodeOperacionalService.revogar(qr.getId());
        QrCodeOperacional updated = qrCodeOperacionalRepository.findById(qr.getId()).orElseThrow();
        return toDto(updated);
    }

    @Transactional
    public TenantQrCodeResponse gerarQrParaMesa(Long mesaId) {
        TenantContext ctx = requireTenantContext();
        Mesa mesa = mesaRepository.findByIdAndTenantId(mesaId, ctx.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Recurso não encontrado."));

        // se já existe QR ativo para a mesa, retorna o existente (evita multiplicação)
        QrCodeOperacional existing = qrCodeOperacionalRepository
                .findFirstByMesaIdAndTenantIdAndAtivoTrueAndRevogadoFalse(mesa.getId(), ctx.tenantId())
                .orElse(null);
        if (existing != null) {
            return toDto(existing);
        }

        tenantLimitService.assertCanCreateQrCode(ctx.tenantId(), 1);

        Long instituicaoId = mesa.getInstituicao() != null ? mesa.getInstituicao().getId() : null;
        Long unidadeId = mesa.getUnidadeAtendimento() != null ? mesa.getUnidadeAtendimento().getId() : null;
        if (instituicaoId == null) {
            throw new ResourceNotFoundException("Recurso não encontrado.");
        }

        QrCodeOperacional qr = qrCodeOperacionalService.criarQr(
                ctx.tenantId(),
                instituicaoId,
                unidadeId,
                mesa.getId(),
                QrCodeOperacionalTipo.MESA,
                "QR " + (mesa.getReferencia() != null ? mesa.getReferencia() : ("Mesa " + mesa.getNumero()))
        );
        return toDto(qr);
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

    private TenantQrCodeResponse toDto(QrCodeOperacional qr) {
        return TenantQrCodeResponse.builder()
                .id(qr.getId())
                .token(qr.getToken())
                .tipo(qr.getTipo())
                .nome(qr.getNome())
                .instituicaoId(qr.getInstituicao() != null ? qr.getInstituicao().getId() : null)
                .unidadeAtendimentoId(qr.getUnidadeAtendimento() != null ? qr.getUnidadeAtendimento().getId() : null)
                .mesaId(qr.getMesa() != null ? qr.getMesa().getId() : null)
                .ativo(Boolean.TRUE.equals(qr.getAtivo()))
                .revogado(Boolean.TRUE.equals(qr.getRevogado()))
                .criadoEm(qr.getCreatedAt())
                .revogadoEm(qr.getRevogadoEm())
                .qrUrlPublica(buildPublicQrUrl(qr.getToken()))
                .build();
    }

    private String buildPublicQrUrl(String token) {
        String base = publicBaseUrl != null ? publicBaseUrl.trim() : "http://localhost:8080";
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        return base + "/q/" + token;
    }
}


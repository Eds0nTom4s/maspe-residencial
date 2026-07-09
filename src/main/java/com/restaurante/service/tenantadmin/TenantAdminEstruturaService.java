package com.restaurante.service.tenantadmin;

import com.restaurante.dto.response.TenantInstituicaoResponse;
import com.restaurante.dto.response.TenantMesaResponse;
import com.restaurante.dto.response.TenantUnidadeAtendimentoResponse;
import com.restaurante.exception.ResourceNotFoundException;
import com.restaurante.model.entity.Instituicao;
import com.restaurante.model.entity.Mesa;
import com.restaurante.model.entity.QrCodeOperacional;
import com.restaurante.model.entity.UnidadeAtendimento;
import com.restaurante.repository.InstituicaoRepository;
import com.restaurante.repository.MesaRepository;
import com.restaurante.repository.QrCodeOperacionalRepository;
import com.restaurante.repository.UnidadeAtendimentoRepository;
import com.restaurante.security.tenant.TenantContext;
import com.restaurante.security.tenant.TenantGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TenantAdminEstruturaService {

    private final TenantGuard tenantGuard;
    private final InstituicaoRepository instituicaoRepository;
    private final UnidadeAtendimentoRepository unidadeAtendimentoRepository;
    private final MesaRepository mesaRepository;
    private final QrCodeOperacionalRepository qrCodeOperacionalRepository;

    @Value("${consuma.public-base-url:http://localhost:8080}")
    private String publicBaseUrl;

    @Transactional(readOnly = true)
    public List<TenantInstituicaoResponse> listarInstituicoes() {
        TenantContext ctx = requireTenantContext();
        return instituicaoRepository.findByTenantId(ctx.tenantId()).stream()
                .map(this::toInstituicao)
                .toList();
    }

    @Transactional(readOnly = true)
    public TenantInstituicaoResponse buscarInstituicao(Long id) {
        TenantContext ctx = requireTenantContext();
        Instituicao i = instituicaoRepository.findByIdAndTenantId(id, ctx.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Recurso não encontrado."));
        return toInstituicao(i);
    }

    @Transactional(readOnly = true)
    public List<TenantUnidadeAtendimentoResponse> listarUnidades(Long instituicaoId, Boolean ativa) {
        TenantContext ctx = requireTenantContext();
        return unidadeAtendimentoRepository.findByTenantIdWithFilters(ctx.tenantId(), instituicaoId, ativa).stream()
                .map(this::toUnidade)
                .toList();
    }

    @Transactional(readOnly = true)
    public TenantUnidadeAtendimentoResponse buscarUnidade(Long id) {
        TenantContext ctx = requireTenantContext();
        UnidadeAtendimento u = unidadeAtendimentoRepository.findByIdAndTenantId(id, ctx.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Recurso não encontrado."));
        return toUnidade(u);
    }

    @Transactional(readOnly = true)
    public List<TenantMesaResponse> listarMesas(Long instituicaoId, Long unidadeAtendimentoId, Boolean ativa) {
        TenantContext ctx = requireTenantContext();
        List<Mesa> mesas = mesaRepository.findByTenantIdWithFilters(ctx.tenantId(), instituicaoId, unidadeAtendimentoId, ativa);
        return mesas.stream().map(this::toMesaResumo).toList();
    }

    @Transactional(readOnly = true)
    public TenantMesaResponse buscarMesa(Long id) {
        TenantContext ctx = requireTenantContext();
        Mesa mesa = mesaRepository.findByIdAndTenantId(id, ctx.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Recurso não encontrado."));
        return toMesaResumo(mesa);
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

    private TenantInstituicaoResponse toInstituicao(Instituicao i) {
        return TenantInstituicaoResponse.builder()
                .id(i.getId())
                .nome(i.getNome())
                .sigla(i.getSigla())
                .telefone(i.getTelefoneAutorizacao())
                .email(null)
                .endereco(null)
                .provincia(null)
                .municipio(null)
                .ativa(Boolean.TRUE.equals(i.getAtiva()))
                .build();
    }

    private TenantUnidadeAtendimentoResponse toUnidade(UnidadeAtendimento u) {
        return TenantUnidadeAtendimentoResponse.builder()
                .id(u.getId())
                .instituicaoId(u.getInstituicao() != null ? u.getInstituicao().getId() : null)
                .instituicaoNome(u.getInstituicao() != null ? u.getInstituicao().getNome() : null)
                .nome(u.getNome())
                .tipo(u.getTipo())
                .ativa(Boolean.TRUE.equals(u.getAtiva()))
                .build();
    }

    private TenantMesaResponse toMesaResumo(Mesa m) {
        boolean ocupada = mesaRepository.isOcupada(m.getId());
        QrCodeOperacional qr = null;
        TenantContext ctx = tenantGuard.requireContext();
        if (ctx.tenantId() != null) {
            qr = qrCodeOperacionalRepository
                    .findFirstByMesaIdAndTenantIdAndAtivoTrueAndRevogadoFalse(m.getId(), ctx.tenantId())
                    .orElse(null);
        }
        return TenantMesaResponse.builder()
                .id(m.getId())
                .instituicaoId(m.getInstituicao() != null ? m.getInstituicao().getId() : null)
                .unidadeAtendimentoId(m.getUnidadeAtendimento() != null ? m.getUnidadeAtendimento().getId() : null)
                .numero(m.getNumero())
                .referencia(m.getReferencia())
                .ativa(Boolean.TRUE.equals(m.getAtiva()))
                .ocupada(ocupada)
                .possuiQr(qr != null)
                .qrCodeId(qr != null ? qr.getId() : null)
                .qrToken(qr != null ? qr.getToken() : null)
                .qrUrlPublica(qr != null ? buildPublicQrUrl(qr.getToken()) : null)
                .build();
    }

    private String buildPublicQrUrl(String token) {
        String base = publicBaseUrl != null ? publicBaseUrl.trim() : "http://localhost:8080";
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        return base + "/q/" + token;
    }
}

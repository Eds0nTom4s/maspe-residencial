package com.restaurante.service.tenantadmin;

import com.restaurante.dto.response.TenantMesaResponse;
import com.restaurante.dto.response.TenantQrCodeResponse;
import com.restaurante.dto.response.TenantQrPrincipalResponse;
import com.restaurante.exception.ResourceNotFoundException;
import com.restaurante.model.entity.Mesa;
import com.restaurante.model.entity.QrCodeOperacional;
import com.restaurante.model.entity.UnidadeAtendimento;
import com.restaurante.model.enums.QrCodeOperacionalTipo;
import com.restaurante.repository.MesaRepository;
import com.restaurante.repository.QrCodeOperacionalRepository;
import com.restaurante.repository.UnidadeAtendimentoRepository;
import com.restaurante.security.tenant.TenantContext;
import com.restaurante.security.tenant.TenantGuard;
import com.restaurante.service.QrCodeOperacionalService;
import com.restaurante.service.TenantLimitService;
import com.restaurante.service.TenantOperationalModulesService;
import com.restaurante.util.QrCodeGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
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
    private final UnidadeAtendimentoRepository unidadeAtendimentoRepository;
    private final QrCodeGenerator qrCodeGenerator;
    private final TenantOperationalModulesService modulesService;

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
    public TenantQrPrincipalResponse buscarQrPrincipal() {
        TenantContext ctx = requireTenantContext();
        Long tenantId = ctx.tenantId();

        List<QrCodeOperacional> qrs = qrCodeOperacionalRepository.findByTenantIdAndAtivoTrueAndRevogadoFalse(tenantId);
        if (qrs == null || qrs.isEmpty()) {
            throw new com.restaurante.exception.ResourceNotFoundException("QR principal não encontrado para este negócio.");
        }

        // Prefer UNIDADE_ATENDIMENTO when available
        QrCodeOperacional chosen = qrs.stream()
                .filter(q -> q.getTipo() == QrCodeOperacionalTipo.UNIDADE_ATENDIMENTO && q.getUnidadeAtendimento() != null)
                .findFirst()
                .orElse(qrs.get(0));

        TenantQrPrincipalResponse resp = new TenantQrPrincipalResponse();
        // tenant info
        resp.setTenantId(tenantId);
        if (chosen.getTenant() != null) {
            resp.setTenantNome(chosen.getTenant().getNome());
        }

        resp.setQrCodeId(chosen.getId());
        resp.setTipo(chosen.getTipo() != null ? chosen.getTipo().name() : null);
        resp.setStatus(Boolean.TRUE.equals(chosen.getAtivo()) && !Boolean.TRUE.equals(chosen.getRevogado()) ? "ATIVO" : "INATIVO");

        // URLs públicas (não expor token explicitamente)
        String token = chosen.getToken();
        String base = publicBaseUrl != null ? publicBaseUrl.trim() : "http://localhost:8080";
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        String qrUrl = base + "/q/" + token;
        String cardapioUrl = base + "/q/" + token + "/cardapio";
        resp.setQrUrlPublica(qrUrl);
        resp.setCardapioUrlPublica(cardapioUrl);

        if (chosen.getUnidadeAtendimento() != null) {
            resp.setUnidadeAtendimentoId(chosen.getUnidadeAtendimento().getId());
            resp.setUnidadeNome(chosen.getUnidadeAtendimento().getNome());
        }

        if (chosen.getCreatedAt() != null) {
            resp.setGeradoEm(chosen.getCreatedAt().atOffset(java.time.ZoneOffset.UTC));
        }
        if (chosen.getUpdatedAt() != null) {
            resp.setAtualizadoEm(chosen.getUpdatedAt().atOffset(java.time.ZoneOffset.UTC));
        }

        return resp;
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
    public TenantQrCodeResponse gerarQrPrincipal() {
        TenantContext ctx = requireTenantContext();
        modulesService.assertPedidoDiretoEnabled(ctx.tenantId());
        List<QrCodeOperacional> qrs = qrCodeOperacionalRepository.findByTenantIdAndAtivoTrueAndRevogadoFalse(ctx.tenantId());
        QrCodeOperacional existing = qrs.stream()
                .filter(q -> q.getTipo() == QrCodeOperacionalTipo.UNIDADE_ATENDIMENTO && q.getUnidadeAtendimento() != null)
                .findFirst()
                .orElse(null);
        if (existing != null) {
            return toDto(existing);
        }
        UnidadeAtendimento unidade = unidadeAtendimentoRepository.findByTenantId(ctx.tenantId()).stream()
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Unidade de atendimento não encontrada."));
        tenantLimitService.assertCanCreateQrCode(ctx.tenantId(), 1);
        QrCodeOperacional qr = qrCodeOperacionalService.criarQr(
                ctx.tenantId(),
                unidade.getInstituicao().getId(),
                unidade.getId(),
                null,
                QrCodeOperacionalTipo.UNIDADE_ATENDIMENTO,
                "QR principal"
        );
        return toDto(qr);
    }

    @Transactional
    public TenantQrCodeResponse gerarQrGenerico() {
        return gerarQrPrincipal();
    }

    @Transactional
    public TenantQrCodeResponse regenerar(Long id) {
        TenantContext ctx = requireTenantContext();
        QrCodeOperacional qr = qrCodeOperacionalRepository.findByIdAndTenantId(id, ctx.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Recurso não encontrado."));
        if (qr.getMesa() != null) {
            modulesService.assertQrMesaEnabled(ctx.tenantId());
            revogar(id);
            return gerarQrParaMesa(qr.getMesa().getId());
        }
        revogar(id);
        tenantLimitService.assertCanCreateQrCode(ctx.tenantId(), 1);
        QrCodeOperacional novo = qrCodeOperacionalService.criarQr(
                ctx.tenantId(),
                qr.getInstituicao().getId(),
                qr.getUnidadeAtendimento() != null ? qr.getUnidadeAtendimento().getId() : null,
                null,
                qr.getTipo(),
                qr.getNome() != null ? qr.getNome() : "QR regenerado"
        );
        return toDto(novo);
    }

    @Transactional(readOnly = true)
    public QrDownload baixar(Long id, String formato) {
        TenantContext ctx = requireTenantContext();
        QrCodeOperacional qr = qrCodeOperacionalRepository.findByIdAndTenantId(id, ctx.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Recurso não encontrado."));
        String normalized = formato == null || formato.isBlank() ? "PNG" : formato.trim().toUpperCase();
        String url = buildPublicQrUrl(qr.getToken());
        try {
            if ("SVG".equals(normalized)) {
                String svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"640\" height=\"240\" viewBox=\"0 0 640 240\">"
                        + "<rect width=\"100%\" height=\"100%\" fill=\"#fff\"/>"
                        + "<text x=\"32\" y=\"64\" font-family=\"Arial\" font-size=\"24\" fill=\"#111\">CONSUMA QR</text>"
                        + "<text x=\"32\" y=\"116\" font-family=\"monospace\" font-size=\"18\" fill=\"#111\">" + escapeXml(url) + "</text>"
                        + "<text x=\"32\" y=\"168\" font-family=\"Arial\" font-size=\"14\" fill=\"#444\">Token: " + escapeXml(qr.getToken()) + "</text>"
                        + "</svg>";
                return new QrDownload(svg.getBytes(java.nio.charset.StandardCharsets.UTF_8), MediaType.valueOf("image/svg+xml"), "qr-" + qr.getId() + ".svg");
            }
            if ("PDF".equals(normalized)) {
                String pdf = "%PDF-1.4\n1 0 obj<<>>endobj\n2 0 obj<</Length 44>>stream\nBT /F1 12 Tf 72 720 Td (CONSUMA QR: " + url + ") Tj ET\nendstream endobj\ntrailer<<>>\n%%EOF\n";
                return new QrDownload(pdf.getBytes(java.nio.charset.StandardCharsets.UTF_8), MediaType.APPLICATION_PDF, "qr-" + qr.getId() + ".pdf");
            }
            return new QrDownload(qrCodeGenerator.generateQrCodePrint(url), MediaType.IMAGE_PNG, "qr-" + qr.getId() + ".png");
        } catch (Exception e) {
            throw new com.restaurante.exception.BusinessException("Falha ao gerar download do QR.");
        }
    }

    @Transactional
    public TenantQrCodeResponse gerarQrParaMesa(Long mesaId) {
        TenantContext ctx = requireTenantContext();
        modulesService.assertQrMesaEnabled(ctx.tenantId());
        modulesService.assertMesasEnabled(ctx.tenantId());
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

    private String escapeXml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    public record QrDownload(byte[] bytes, MediaType mediaType, String filename) {
    }
}

package com.restaurante.service;

import com.restaurante.dto.response.PublicCardapioResponse;
import com.restaurante.dto.response.PublicCategoriaProdutoResponse;
import com.restaurante.dto.response.PublicProdutoResponse;
import com.restaurante.dto.response.QrPublicContext;
import com.restaurante.exception.ResourceNotFoundException;
import com.restaurante.model.entity.CategoriaProduto;
import com.restaurante.model.entity.Instituicao;
import com.restaurante.model.entity.Mesa;
import com.restaurante.model.entity.Produto;
import com.restaurante.model.entity.QrCodeOperacional;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.UnidadeAtendimento;
import com.restaurante.model.enums.QrCodeOperacionalTipo;
import com.restaurante.model.enums.TenantEstado;
import com.restaurante.repository.CategoriaProdutoRepository;
import com.restaurante.repository.InstituicaoRepository;
import com.restaurante.repository.MesaRepository;
import com.restaurante.repository.ProdutoRepository;
import com.restaurante.repository.QrCodeOperacionalRepository;
import com.restaurante.repository.TenantRepository;
import com.restaurante.repository.UnidadeAtendimentoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class QrCodeOperacionalService {

    private static final String TOKEN_PREFIX = "q_";
    private static final char[] TOKEN_ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz".toCharArray();
    private static final int TOKEN_RANDOM_LENGTH = 22; // total ~24 com prefixo
    private static final int TOKEN_MAX_ATTEMPTS = 20;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final QrCodeOperacionalRepository qrCodeOperacionalRepository;
    private final TenantRepository tenantRepository;
    private final InstituicaoRepository instituicaoRepository;
    private final UnidadeAtendimentoRepository unidadeAtendimentoRepository;
    private final MesaRepository mesaRepository;
    private final CategoriaProdutoRepository categoriaProdutoRepository;
    private final ProdutoRepository produtoRepository;

    @Transactional
    public QrCodeOperacional criarQr(
            Long tenantId,
            Long instituicaoId,
            Long unidadeAtendimentoId,
            Long mesaId,
            QrCodeOperacionalTipo tipo,
            String nome
    ) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", "id", tenantId));
        Instituicao instituicao = instituicaoRepository.findById(instituicaoId)
                .orElseThrow(() -> new ResourceNotFoundException("Instituicao", "id", instituicaoId));

        if (!instituicao.getTenant().getId().equals(tenant.getId())) {
            throw new ResourceNotFoundException("Instituicao", "id", instituicaoId);
        }

        UnidadeAtendimento unidade = null;
        if (unidadeAtendimentoId != null) {
            unidade = unidadeAtendimentoRepository.findById(unidadeAtendimentoId)
                    .orElseThrow(() -> new ResourceNotFoundException("UnidadeAtendimento", "id", unidadeAtendimentoId));
            if (unidade.getInstituicao() == null || !unidade.getInstituicao().getId().equals(instituicao.getId())) {
                throw new ResourceNotFoundException("UnidadeAtendimento", "id", unidadeAtendimentoId);
            }
        }

        Mesa mesa = null;
        if (mesaId != null) {
            mesa = mesaRepository.findById(mesaId)
                    .orElseThrow(() -> new ResourceNotFoundException("Mesa", "id", mesaId));
            if (mesa.getInstituicao() != null && !mesa.getInstituicao().getId().equals(instituicao.getId())) {
                throw new ResourceNotFoundException("Mesa", "id", mesaId);
            }
            if (unidade != null && mesa.getUnidadeAtendimento() != null && !mesa.getUnidadeAtendimento().getId().equals(unidade.getId())) {
                throw new ResourceNotFoundException("Mesa", "id", mesaId);
            }
        }

        QrCodeOperacional qr = new QrCodeOperacional();
        qr.setTenant(tenant);
        qr.setInstituicao(instituicao);
        qr.setUnidadeAtendimento(unidade);
        qr.setMesa(mesa);
        qr.setTipo(tipo != null ? tipo : QrCodeOperacionalTipo.TENANT_GERAL);
        qr.setNome(nome);
        qr.setToken(gerarTokenUnico());
        qr.setAtivo(true);
        qr.setRevogado(false);
        return qrCodeOperacionalRepository.save(qr);
    }

    @Transactional(readOnly = true)
    public QrPublicContext resolverPublico(String token) {
        QrCodeOperacional qr = resolverOperacionalAtivoParaOperacao(token);

        Tenant tenant = qr.getTenant();
        Instituicao inst = qr.getInstituicao();
        UnidadeAtendimento unidade = qr.getUnidadeAtendimento();
        Mesa mesa = qr.getMesa();

        QrPublicContext ctx = new QrPublicContext();
        ctx.setQrId(qr.getId());
        ctx.setToken(qr.getToken());
        ctx.setTipo(qr.getTipo());
        ctx.setNome(qr.getNome());
        ctx.setAtivo(qr.getAtivo());

        ctx.setTenantId(tenant.getId());
        ctx.setTenantNome(tenant.getNome());
        ctx.setTenantCode(tenant.getTenantCode());

        ctx.setInstituicaoId(inst.getId());
        ctx.setInstituicaoNome(inst.getNome());

        if (unidade != null) {
            ctx.setUnidadeAtendimentoId(unidade.getId());
            ctx.setUnidadeAtendimentoNome(unidade.getNome());
        }

        if (mesa != null) {
            ctx.setMesaId(mesa.getId());
            ctx.setMesaReferencia(mesa.getReferencia());
            ctx.setMesaNumero(mesa.getNumero());
        }

        return ctx;
    }

    /**
     * Resolve e valida QR operacional para uso em operações públicas (cardápio/pedido).
     *
     * <p>Política de segurança: falha fechada com 404 para não vazar existência do token.
     */
    @Transactional(readOnly = true)
    public QrCodeOperacional resolverOperacionalAtivoParaOperacao(String token) {
        QrCodeOperacional qr = qrCodeOperacionalRepository.findByTokenAndAtivoTrueAndRevogadoFalse(token)
                .orElseThrow(() -> new ResourceNotFoundException("QrCodeOperacional", "token", token));

        Tenant tenant = qr.getTenant();
        if (tenant == null) {
            throw new ResourceNotFoundException("QrCodeOperacional", "token", token);
        }
        if (tenant.getEstado() != TenantEstado.ATIVO) {
            throw new ResourceNotFoundException("QrCodeOperacional", "token", token);
        }

        Instituicao inst = qr.getInstituicao();
        if (inst == null || inst.getTenant() == null || !inst.getTenant().getId().equals(tenant.getId())) {
            throw new ResourceNotFoundException("QrCodeOperacional", "token", token);
        }

        UnidadeAtendimento unidade = qr.getUnidadeAtendimento();
        if (unidade != null) {
            if (unidade.getInstituicao() == null || !unidade.getInstituicao().getId().equals(inst.getId())) {
                throw new ResourceNotFoundException("QrCodeOperacional", "token", token);
            }
        }

        Mesa mesa = qr.getMesa();
        if (mesa != null) {
            if (mesa.getInstituicao() != null && !mesa.getInstituicao().getId().equals(inst.getId())) {
                throw new ResourceNotFoundException("QrCodeOperacional", "token", token);
            }
            if (unidade != null && mesa.getUnidadeAtendimento() != null && !mesa.getUnidadeAtendimento().getId().equals(unidade.getId())) {
                throw new ResourceNotFoundException("QrCodeOperacional", "token", token);
            }
        }

        return qr;
    }

    @Transactional(readOnly = true)
    public PublicCardapioResponse carregarCardapioPublicoPorQrToken(String token) {
        QrPublicContext ctx = resolverPublico(token);
        Long tenantId = ctx.getTenantId();

        List<CategoriaProduto> categorias = categoriaProdutoRepository.findByTenantIdAndAtivoTrueOrderByOrdemAsc(tenantId);
        List<Produto> produtos = produtoRepository.findByTenantIdAndDisponivelTrueAndAtivoTrue(tenantId);

        Map<Long, List<Produto>> produtosPorCategoriaId = produtos.stream()
                .filter(p -> p.getCategoriaProduto() != null)
                .collect(Collectors.groupingBy(p -> p.getCategoriaProduto().getId()));

        List<PublicCategoriaProdutoResponse> categoriaResponses = categorias.stream()
                .map(cat -> {
                    PublicCategoriaProdutoResponse cr = new PublicCategoriaProdutoResponse();
                    cr.setId(cat.getId());
                    cr.setNome(cat.getNome());
                    cr.setSlug(cat.getSlug());
                    cr.setOrdem(cat.getOrdem());

                    List<PublicProdutoResponse> prs = produtosPorCategoriaId.getOrDefault(cat.getId(), List.of())
                            .stream()
                            .sorted((a, b) -> {
                                if (a.getNome() == null && b.getNome() == null) return 0;
                                if (a.getNome() == null) return 1;
                                if (b.getNome() == null) return -1;
                                return a.getNome().compareToIgnoreCase(b.getNome());
                            })
                            .map(this::toPublicProduto)
                            .toList();
                    cr.setProdutos(prs);
                    return cr;
                })
                .toList();

        PublicCardapioResponse resp = new PublicCardapioResponse();
        resp.setQr(ctx);
        resp.setCategorias(categoriaResponses);
        return resp;
    }

    @Transactional
    public void revogar(Long qrId) {
        QrCodeOperacional qr = qrCodeOperacionalRepository.findById(qrId)
                .orElseThrow(() -> new ResourceNotFoundException("QrCodeOperacional", "id", qrId));
        qr.setRevogado(true);
        qr.setAtivo(false);
        qr.setRevogadoEm(LocalDateTime.now());
        qrCodeOperacionalRepository.save(qr);
    }

    public String gerarTokenUnico() {
        for (int i = 0; i < TOKEN_MAX_ATTEMPTS; i++) {
            String token = TOKEN_PREFIX + randomTokenBody(TOKEN_RANDOM_LENGTH);
            if (!qrCodeOperacionalRepository.existsByToken(token)) {
                return token;
            }
        }
        // Extremamente improvável; falha fechada.
        throw new IllegalStateException("Falha ao gerar token único para QR operacional.");
    }

    private static String randomTokenBody(int len) {
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append(TOKEN_ALPHABET[SECURE_RANDOM.nextInt(TOKEN_ALPHABET.length)]);
        }
        return sb.toString();
    }

    private PublicProdutoResponse toPublicProduto(Produto p) {
        PublicProdutoResponse r = new PublicProdutoResponse();
        r.setId(p.getId());
        r.setCodigo(p.getCodigo());
        r.setNome(p.getNome());
        r.setDescricao(p.getDescricao());
        r.setPreco(p.getPreco());
        r.setImagemUrl(p.getUrlImagem());
        r.setDisponivel(p.getDisponivel());
        r.setCategoriaProdutoId(p.getCategoriaProduto() != null ? p.getCategoriaProduto().getId() : null);
        return r;
    }
}

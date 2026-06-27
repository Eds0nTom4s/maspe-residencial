package com.restaurante.service;

import com.restaurante.dto.response.ProdutoImagemResponse;
import com.restaurante.exception.BusinessException;
import com.restaurante.exception.ResourceNotFoundException;
import com.restaurante.model.entity.Produto;
import com.restaurante.model.entity.ProdutoImagem;
import com.restaurante.model.entity.Tenant;
import com.restaurante.repository.ProdutoImagemRepository;
import com.restaurante.repository.ProdutoRepository;
import com.restaurante.repository.TenantRepository;
import com.restaurante.security.tenant.TenantContextHolder;
import com.restaurante.service.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Serviço de galeria de imagens dos produtos.
 *
 * <p>Gerencia upload, listagem, remoção e reordenação de imagens vinculadas a
 * um produto, armazenando os arquivos no MinIO e persistindo metadados na
 * tabela {@code produto_imagens}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProdutoImagemService {

    private static final int MAX_IMAGENS_POR_PRODUTO = 4;
    private static final String DIRETORIO_IMAGENS = "produtos";

    private final ProdutoImagemRepository produtoImagemRepository;
    private final ProdutoRepository produtoRepository;
    private final TenantRepository tenantRepository;
    private final StorageService storageService;

    @Transactional(readOnly = true)
    public List<ProdutoImagemResponse> listarImagens(Long produtoId) {
        Long tenantId = resolveTenantIdRequired();
        validarProdutoPertenceAoTenant(produtoId, tenantId);
        return produtoImagemRepository.findByTenantIdAndProdutoIdOrderByOrdemAsc(tenantId, produtoId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public ProdutoImagemResponse adicionarImagem(Long produtoId, MultipartFile file, String legenda) {
        Long tenantId = resolveTenantIdRequired();
        Produto produto = buscarProdutoDoTenant(produtoId, tenantId);
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new BusinessException("Tenant não encontrado: " + tenantId));

        long quantidadeAtual = produtoImagemRepository.findByTenantIdAndProdutoIdOrderByOrdemAsc(tenantId, produtoId).size();
        if (quantidadeAtual >= MAX_IMAGENS_POR_PRODUTO) {
            throw new BusinessException("LIMITE_IMAGENS_PRODUTO_EXCEDIDO");
        }

        String url = storageService.uploadFile(file, DIRETORIO_IMAGENS);

        ProdutoImagem imagem = new ProdutoImagem();
        imagem.setTenant(tenant);
        imagem.setProduto(produto);
        imagem.setUrl(url);
        imagem.setOrdem((int) quantidadeAtual);
        imagem.setLegenda(legenda);

        ProdutoImagem salva = produtoImagemRepository.save(imagem);
        log.info("Imagem adicionada ao produto {} do tenant {}: {}", produtoId, tenantId, url);
        return toResponse(salva);
    }

    @Transactional
    public void removerImagem(Long produtoId, Long imagemId) {
        Long tenantId = resolveTenantIdRequired();
        validarProdutoPertenceAoTenant(produtoId, tenantId);

        ProdutoImagem imagem = produtoImagemRepository.findById(imagemId)
                .orElseThrow(() -> new ResourceNotFoundException("Imagem não encontrada: " + imagemId));

        if (!imagem.getProduto().getId().equals(produtoId) || !imagem.getTenant().getId().equals(tenantId)) {
            throw new BusinessException("Imagem não pertence ao produto/tenant informado.");
        }

        storageService.deleteFile(imagem.getUrl());
        produtoImagemRepository.delete(imagem);
        reordenarAutomaticamente(tenantId, produtoId);
        log.info("Imagem {} removida do produto {} do tenant {}", imagemId, produtoId, tenantId);
    }

    @Transactional
    public List<ProdutoImagemResponse> reordenarImagens(Long produtoId, List<Long> imagemIds) {
        Long tenantId = resolveTenantIdRequired();
        validarProdutoPertenceAoTenant(produtoId, tenantId);

        List<ProdutoImagem> imagens = produtoImagemRepository
                .findByTenantIdAndProdutoIdOrderByOrdemAsc(tenantId, produtoId);

        if (imagemIds == null || imagemIds.size() != imagens.size()) {
            throw new BusinessException("Lista de IDs deve conter todas as imagens do produto.");
        }

        for (int i = 0; i < imagemIds.size(); i++) {
            Long id = imagemIds.get(i);
            ProdutoImagem imagem = imagens.stream()
                    .filter(img -> img.getId().equals(id))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException("Imagem " + id + " não pertence ao produto."));
            imagem.setOrdem(i);
        }

        List<ProdutoImagem> salvas = produtoImagemRepository.saveAll(imagens);
        return salvas.stream().map(this::toResponse).toList();
    }

    private void reordenarAutomaticamente(Long tenantId, Long produtoId) {
        List<ProdutoImagem> imagens = produtoImagemRepository
                .findByTenantIdAndProdutoIdOrderByOrdemAsc(tenantId, produtoId);
        for (int i = 0; i < imagens.size(); i++) {
            imagens.get(i).setOrdem(i);
        }
        produtoImagemRepository.saveAll(imagens);
    }

    private Produto buscarProdutoDoTenant(Long produtoId, Long tenantId) {
        return produtoRepository.findByIdAndTenantId(produtoId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado: " + produtoId));
    }

    private void validarProdutoPertenceAoTenant(Long produtoId, Long tenantId) {
        if (!produtoRepository.existsByIdAndTenantId(produtoId, tenantId)) {
            throw new ResourceNotFoundException("Produto não encontrado: " + produtoId);
        }
    }

    private Long resolveTenantIdRequired() {
        return TenantContextHolder.get()
                .map(ctx -> ctx.tenantId())
                .filter(java.util.Objects::nonNull)
                .orElseThrow(() -> new BusinessException("Tenant não identificado no contexto."));
    }

    private ProdutoImagemResponse toResponse(ProdutoImagem imagem) {
        ProdutoImagemResponse response = new ProdutoImagemResponse();
        response.setId(imagem.getId());
        response.setProdutoId(imagem.getProduto().getId());
        response.setUrl(imagem.getUrl());
        response.setOrdem(imagem.getOrdem());
        response.setLegenda(imagem.getLegenda());
        return response;
    }
}

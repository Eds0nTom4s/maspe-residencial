package com.restaurante.service;

import com.restaurante.dto.response.ProdutoImagemResponse;
import com.restaurante.exception.BusinessException;
import com.restaurante.model.entity.Produto;
import com.restaurante.model.entity.ProdutoImagem;
import com.restaurante.model.entity.Tenant;
import com.restaurante.repository.ProdutoImagemRepository;
import com.restaurante.repository.ProdutoRepository;
import com.restaurante.repository.TenantRepository;
import com.restaurante.security.tenant.TenantContext;
import com.restaurante.security.tenant.TenantContextHolder;
import com.restaurante.security.tenant.TenantResolutionSource;
import com.restaurante.service.storage.StorageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProdutoImagemServiceTest {

    @Mock
    private ProdutoImagemRepository produtoImagemRepository;

    @Mock
    private ProdutoRepository produtoRepository;

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private StorageService storageService;

    @InjectMocks
    private ProdutoImagemService produtoImagemService;

    private static final Long TENANT_ID = 1L;
    private static final Long PRODUTO_ID = 10L;

    @BeforeEach
    void setUp() {
        TenantContextHolder.set(new TenantContext(
                TENANT_ID, "TENANT", 1L, Set.of("GERENTE"),
                TenantResolutionSource.JWT, false, false
        ));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void adicionarImagem_deveSalvarImagemComSucesso() {
        Produto produto = new Produto();
        produto.setId(PRODUTO_ID);
        Tenant tenant = new Tenant();
        tenant.setId(TENANT_ID);

        when(produtoRepository.findByIdAndTenantId(PRODUTO_ID, TENANT_ID)).thenReturn(Optional.of(produto));
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(produtoImagemRepository.findByTenantIdAndProdutoIdOrderByOrdemAsc(TENANT_ID, PRODUTO_ID))
                .thenReturn(Collections.emptyList());
        when(storageService.uploadFile(any(MultipartFile.class), eq("produtos")))
                .thenReturn("http://localhost:9000/restaurante-images/produtos/uuid.jpg");
        when(produtoImagemRepository.save(any(ProdutoImagem.class))).thenAnswer(inv -> {
            ProdutoImagem img = inv.getArgument(0);
            img.setId(100L);
            return img;
        });

        MockMultipartFile file = new MockMultipartFile("file", "foto.jpg", "image/jpeg", "conteudo".getBytes());
        ProdutoImagemResponse response = produtoImagemService.adicionarImagem(PRODUTO_ID, file, "Frente");

        assertNotNull(response);
        assertEquals("http://localhost:9000/restaurante-images/produtos/uuid.jpg", response.getUrl());
        assertEquals("Frente", response.getLegenda());
        assertEquals(0, response.getOrdem());
        verify(produtoImagemRepository).save(any(ProdutoImagem.class));
    }

    @Test
    void adicionarImagem_deveRejeitarQuandoExcedeLimite() {
        Produto produto = new Produto();
        produto.setId(PRODUTO_ID);
        Tenant tenant = new Tenant();
        tenant.setId(TENANT_ID);

        List<ProdutoImagem> imagens = List.of(new ProdutoImagem(), new ProdutoImagem(), new ProdutoImagem(), new ProdutoImagem());

        when(produtoRepository.findByIdAndTenantId(PRODUTO_ID, TENANT_ID)).thenReturn(Optional.of(produto));
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(produtoImagemRepository.findByTenantIdAndProdutoIdOrderByOrdemAsc(TENANT_ID, PRODUTO_ID))
                .thenReturn(imagens);

        MockMultipartFile file = new MockMultipartFile("file", "foto.jpg", "image/jpeg", "conteudo".getBytes());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> produtoImagemService.adicionarImagem(PRODUTO_ID, file, null));

        assertEquals("LIMITE_IMAGENS_PRODUTO_EXCEDIDO", ex.getMessage());
        verify(storageService, never()).uploadFile(any(), any());
    }

    @Test
    void removerImagem_deveRemoverDoStorageEBanco() {
        Produto produto = new Produto();
        produto.setId(PRODUTO_ID);
        Tenant tenant = new Tenant();
        tenant.setId(TENANT_ID);
        ProdutoImagem imagem = new ProdutoImagem();
        imagem.setId(100L);
        imagem.setProduto(produto);
        imagem.setTenant(tenant);
        imagem.setUrl("http://localhost:9000/restaurante-images/produtos/imagem.jpg");

        when(produtoRepository.existsByIdAndTenantId(PRODUTO_ID, TENANT_ID)).thenReturn(true);
        when(produtoImagemRepository.findById(100L)).thenReturn(Optional.of(imagem));
        when(produtoImagemRepository.findByTenantIdAndProdutoIdOrderByOrdemAsc(TENANT_ID, PRODUTO_ID))
                .thenReturn(Collections.emptyList());

        produtoImagemService.removerImagem(PRODUTO_ID, 100L);

        verify(storageService).deleteFile(imagem.getUrl());
        verify(produtoImagemRepository).delete(imagem);
    }

    @Test
    void reordenarImagens_deveAtualizarOrdem() {
        Produto produto = new Produto();
        produto.setId(PRODUTO_ID);
        Tenant tenant = new Tenant();
        tenant.setId(TENANT_ID);

        ProdutoImagem img1 = new ProdutoImagem();
        img1.setId(1L);
        img1.setProduto(produto);
        img1.setTenant(tenant);
        img1.setUrl("url1");
        img1.setOrdem(0);

        ProdutoImagem img2 = new ProdutoImagem();
        img2.setId(2L);
        img2.setProduto(produto);
        img2.setTenant(tenant);
        img2.setUrl("url2");
        img2.setOrdem(1);

        when(produtoRepository.existsByIdAndTenantId(PRODUTO_ID, TENANT_ID)).thenReturn(true);
        when(produtoImagemRepository.findByTenantIdAndProdutoIdOrderByOrdemAsc(TENANT_ID, PRODUTO_ID))
                .thenReturn(List.of(img1, img2));
        when(produtoImagemRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        List<ProdutoImagemResponse> responses = produtoImagemService.reordenarImagens(PRODUTO_ID, List.of(2L, 1L));

        assertEquals(2, responses.size());
        // saveAll retorna na ordem original da consulta (img1, img2)
        assertEquals(1, responses.get(0).getOrdem());
        assertEquals(0, responses.get(1).getOrdem());
        assertEquals("url1", responses.get(0).getUrl());
        assertEquals("url2", responses.get(1).getUrl());
    }
}

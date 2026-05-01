package com.restaurante.store;

import com.restaurante.exception.BusinessException;
import com.restaurante.model.entity.*;
import com.restaurante.model.enums.CategoriaProduto;
import com.restaurante.model.enums.TipoCozinha;
import com.restaurante.model.enums.TipoUnidadeAtendimento;
import com.restaurante.repository.*;
import com.restaurante.store.dto.*;
import com.restaurante.store.mapper.StoreMapper;
import com.restaurante.store.model.StoreOrderMetadata;
import com.restaurante.store.repository.StoreOrderMetadataRepository;
import com.restaurante.store.security.StoreSocioIdentityResolver;
import com.restaurante.store.service.*;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StoreCheckoutServiceTest {

    private StoreSocioIdentityResolver identityResolver;
    private StoreCatalogService catalogService;
    private StoreInfrastructureService infrastructureService;
    private StorePaymentService paymentService;
    private StoreAnalyticsService analyticsService;
    private ProdutoRepository produtoRepository;
    private VariacaoProdutoRepository variacaoRepository;
    private SessaoConsumoRepository sessaoRepository;
    private PedidoRepository pedidoRepository;
    private StoreOrderMetadataRepository metadataRepository;
    private StoreMapper mapper;
    private StoreCheckoutService service;

    @BeforeEach
    void setup() {
        identityResolver = mock(StoreSocioIdentityResolver.class);
        catalogService = mock(StoreCatalogService.class);
        infrastructureService = mock(StoreInfrastructureService.class);
        paymentService = mock(StorePaymentService.class);
        analyticsService = mock(StoreAnalyticsService.class);
        produtoRepository = mock(ProdutoRepository.class);
        variacaoRepository = mock(VariacaoProdutoRepository.class);
        sessaoRepository = mock(SessaoConsumoRepository.class);
        pedidoRepository = mock(PedidoRepository.class);
        metadataRepository = mock(StoreOrderMetadataRepository.class);
        mapper = mock(StoreMapper.class);
        service = new StoreCheckoutService(identityResolver, catalogService, infrastructureService, paymentService,
                analyticsService, produtoRepository, variacaoRepository, sessaoRepository, pedidoRepository,
                metadataRepository, mapper);
    }

    @Test
    void checkoutPersisteVariacaoNoItem() {
        mockBaseFlow(true, true, 5);

        StoreCheckoutRequest request = checkoutRequest(1L);
        StoreOrderDTO response = new StoreOrderDTO();
        when(mapper.toOrderDTO(any(StoreOrderMetadata.class))).thenReturn(response);

        StoreOrderDTO result = service.checkout(request, mock(HttpServletRequest.class));

        assertSame(response, result);
        ArgumentCaptor<Pedido> captor = ArgumentCaptor.forClass(Pedido.class);
        verify(pedidoRepository, atLeast(2)).save(captor.capture());
        Pedido saved = captor.getAllValues().get(captor.getAllValues().size() - 1);
        assertEquals(1L, saved.getItens().get(0).getVariacaoProduto().getId());
        assertEquals("NOME", saved.getItens().get(0).getPersonalizedName());
        assertTrue(saved.getItens().get(0).getQrIdentityEnabled());
        assertNotNull(saved.getItens().get(0).getQrIdentityTokenHash());
    }

    @Test
    void checkoutRejeitaProdutoInativo() {
        mockBaseFlow(false, true, 5);

        assertThrows(BusinessException.class,
                () -> service.checkout(checkoutRequest(1L), mock(HttpServletRequest.class)));
    }

    @Test
    void checkoutRejeitaVariacaoInativa() {
        mockBaseFlow(true, false, 5);

        assertThrows(BusinessException.class,
                () -> service.checkout(checkoutRequest(1L), mock(HttpServletRequest.class)));
    }

    @Test
    void checkoutRejeitaStockInsuficiente() {
        mockBaseFlow(true, true, 0);

        assertThrows(BusinessException.class,
                () -> service.checkout(checkoutRequest(1L), mock(HttpServletRequest.class)));
    }

    @Test
    void checkoutComMesmaChaveDeIdempotenciaRetornaOrdemExistente() {
        StoreSocioIdentityDTO identity = new StoreSocioIdentityDTO("socio-1", "Socio GDSE", "+244923000000", "s@gdse.ao");
        when(identityResolver.resolveOptional(any())).thenReturn(identity);
        StoreOrderMetadata metadata = new StoreOrderMetadata();
        StoreOrderDTO dto = new StoreOrderDTO();
        dto.setId(10L);
        when(metadataRepository.findByIdempotencyKeyAndSocioId("idem-1", "socio-1"))
                .thenReturn(Optional.of(metadata));
        when(mapper.toOrderDTO(metadata)).thenReturn(dto);

        StoreCheckoutRequest request = checkoutRequest(1L);
        request.setIdempotencyKey("idem-1");
        StoreOrderDTO result = service.checkout(request, mock(HttpServletRequest.class));

        assertEquals(10L, result.getId());
        verify(pedidoRepository, never()).save(any(Pedido.class));
        verify(paymentService, never()).initiatePayment(any(), any(), any(), any());
    }

    @Test
    void checkoutPublicoSemTokenUsaDadosDoComprador() {
        mockBaseFlowPublico(true, true, 5);

        StoreCheckoutRequest request = checkoutRequest(1L);
        request.setCompradorNome("Adepto GDSE");
        request.setCompradorTelefone("+244923111111");
        request.setCompradorEmail("adepto@gdse.ao");
        StoreOrderDTO response = new StoreOrderDTO();
        when(mapper.toOrderDTO(any(StoreOrderMetadata.class))).thenReturn(response);

        StoreOrderDTO result = service.checkout(request, mock(HttpServletRequest.class));

        assertSame(response, result);
        verify(identityResolver).ensureMapping(argThat(identity ->
                !identity.isSocio() && "PUBLIC:+244923111111".equals(identity.getSocioId())));
    }

    @Test
    void checkoutPublicoSemTelefoneFalha() {
        when(identityResolver.resolveOptional(any())).thenReturn(null);

        StoreCheckoutRequest request = checkoutRequest(1L);

        assertThrows(BusinessException.class, () -> service.checkout(request, mock(HttpServletRequest.class)));
    }

    private void mockBaseFlow(boolean produtoAtivo, boolean variacaoAtiva, Integer stock) {
        StoreSocioIdentityDTO identity = new StoreSocioIdentityDTO("socio-1", "Socio GDSE", "+244923000000", "s@gdse.ao");
        Cliente cliente = Cliente.builder().telefone(identity.getPhone()).nome(identity.getName()).build();
        when(identityResolver.resolveOptional(any())).thenReturn(identity);
        when(identityResolver.ensureMapping(identity)).thenReturn(cliente);

        UnidadeAtendimento unidade = UnidadeAtendimento.builder()
                .nome("LOJA_SAGRADA")
                .tipo(TipoUnidadeAtendimento.EVENTO)
                .ativa(true)
                .build();
        Cozinha fulfillment = Cozinha.builder()
                .nome("LOJA_SAGRADA")
                .tipo(TipoCozinha.ESPECIAL)
                .ativa(true)
                .build();
        when(infrastructureService.ensureFulfillmentUnit())
                .thenReturn(new StoreInfrastructureService.FulfillmentUnit(unidade, fulfillment));
        when(sessaoRepository.save(any(SessaoConsumo.class))).thenAnswer(inv -> inv.getArgument(0));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(inv -> {
            Pedido pedido = inv.getArgument(0);
            if (pedido.getId() == null) pedido.setId(10L);
            return pedido;
        });
        when(metadataRepository.save(any(StoreOrderMetadata.class))).thenAnswer(inv -> inv.getArgument(0));

        Produto produto = Produto.builder()
                .codigo("GDSE-HOME")
                .nome("Camisola")
                .preco(new BigDecimal("25000"))
                .categoria(CategoriaProduto.VESTUARIO)
                .disponivel(produtoAtivo)
                .ativo(produtoAtivo)
                .build();
        produto.setId(1L);
        when(produtoRepository.findById(1L)).thenReturn(Optional.of(produto));
        when(catalogService.isStoreProduct(produto)).thenReturn(true);

        VariacaoProduto variacao = VariacaoProduto.builder()
                .produto(produto)
                .tipo(VariacaoProduto.TipoVariacao.TAMANHO)
                .valor("M")
                .tamanho("M")
                .sku("GDSE-HOME-M")
                .preco(new BigDecimal("26000"))
                .stock(stock)
                .ativo(true)
                .build();
        variacao.setId(1L);
        when(variacaoRepository.findByIdAndProdutoIdAndAtivoTrue(1L, 1L))
                .thenReturn(variacaoAtiva ? Optional.of(variacao) : Optional.empty());
    }

    private void mockBaseFlowPublico(boolean produtoAtivo, boolean variacaoAtiva, Integer stock) {
        when(identityResolver.resolveOptional(any())).thenReturn(null);
        when(identityResolver.ensureMapping(any(StoreSocioIdentityDTO.class))).thenAnswer(inv -> {
            StoreSocioIdentityDTO identity = inv.getArgument(0);
            return Cliente.builder().telefone(identity.getPhone()).nome(identity.getName()).build();
        });

        UnidadeAtendimento unidade = UnidadeAtendimento.builder()
                .nome("LOJA_SAGRADA")
                .tipo(TipoUnidadeAtendimento.EVENTO)
                .ativa(true)
                .build();
        Cozinha fulfillment = Cozinha.builder()
                .nome("LOJA_SAGRADA")
                .tipo(TipoCozinha.ESPECIAL)
                .ativa(true)
                .build();
        when(infrastructureService.ensureFulfillmentUnit())
                .thenReturn(new StoreInfrastructureService.FulfillmentUnit(unidade, fulfillment));
        when(sessaoRepository.save(any(SessaoConsumo.class))).thenAnswer(inv -> inv.getArgument(0));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(inv -> {
            Pedido pedido = inv.getArgument(0);
            if (pedido.getId() == null) pedido.setId(10L);
            return pedido;
        });
        when(metadataRepository.save(any(StoreOrderMetadata.class))).thenAnswer(inv -> inv.getArgument(0));

        Produto produto = Produto.builder()
                .codigo("GDSE-HOME")
                .nome("Camisola")
                .preco(new BigDecimal("25000"))
                .categoria(CategoriaProduto.VESTUARIO)
                .disponivel(produtoAtivo)
                .ativo(produtoAtivo)
                .build();
        produto.setId(1L);
        when(produtoRepository.findById(1L)).thenReturn(Optional.of(produto));
        when(catalogService.isStoreProduct(produto)).thenReturn(true);

        VariacaoProduto variacao = VariacaoProduto.builder()
                .produto(produto)
                .tipo(VariacaoProduto.TipoVariacao.TAMANHO)
                .valor("M")
                .tamanho("M")
                .sku("GDSE-HOME-M")
                .preco(new BigDecimal("26000"))
                .stock(stock)
                .ativo(true)
                .build();
        variacao.setId(1L);
        when(variacaoRepository.findByIdAndProdutoIdAndAtivoTrue(1L, 1L))
                .thenReturn(variacaoAtiva ? Optional.of(variacao) : Optional.empty());
    }

    private StoreCheckoutRequest checkoutRequest(Long variacaoId) {
        StoreCarrinhoItemRequest item = new StoreCarrinhoItemRequest(1L, 1, variacaoId, "obs");
        item.setPersonalizedName("NOME");
        item.setQrIdentityEnabled(true);
        item.setPremiumPackaging(true);
        StoreCheckoutRequest request = new StoreCheckoutRequest();
        request.setItens(java.util.List.of(item));
        request.setMetodoPagamento(StoreCheckoutRequest.MetodoPagamentoLoja.APPYPAY_GPO);
        return request;
    }
}

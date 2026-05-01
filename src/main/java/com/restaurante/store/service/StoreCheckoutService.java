package com.restaurante.store.service;

import com.restaurante.exception.BusinessException;
import com.restaurante.exception.ResourceNotFoundException;
import com.restaurante.model.entity.*;
import com.restaurante.model.enums.StatusFinanceiroPedido;
import com.restaurante.model.enums.StatusPedido;
import com.restaurante.model.enums.StatusSessaoConsumo;
import com.restaurante.model.enums.StatusSubPedido;
import com.restaurante.model.enums.TipoPagamentoPedido;
import com.restaurante.model.enums.TipoSessao;
import com.restaurante.repository.*;
import com.restaurante.store.dto.StoreCarrinhoItemRequest;
import com.restaurante.store.dto.StoreCheckoutRequest;
import com.restaurante.store.dto.StoreOrderDTO;
import com.restaurante.store.dto.StoreSocioIdentityDTO;
import com.restaurante.store.mapper.StoreMapper;
import com.restaurante.store.model.StoreOrderMetadata;
import com.restaurante.store.repository.StoreOrderMetadataRepository;
import com.restaurante.store.security.StoreSocioIdentityResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class StoreCheckoutService {

    private final StoreSocioIdentityResolver identityResolver;
    private final StoreCatalogService catalogService;
    private final StoreInfrastructureService infrastructureService;
    private final StorePaymentService paymentService;
    private final StoreAnalyticsService analyticsService;
    private final ProdutoRepository produtoRepository;
    private final VariacaoProdutoRepository variacaoProdutoRepository;
    private final SessaoConsumoRepository sessaoConsumoRepository;
    private final PedidoRepository pedidoRepository;
    private final StoreOrderMetadataRepository metadataRepository;
    private final StoreMapper mapper;

    public StoreCheckoutService(StoreSocioIdentityResolver identityResolver,
                                StoreCatalogService catalogService,
                                StoreInfrastructureService infrastructureService,
                                StorePaymentService paymentService,
                                StoreAnalyticsService analyticsService,
                                ProdutoRepository produtoRepository,
                                VariacaoProdutoRepository variacaoProdutoRepository,
                                SessaoConsumoRepository sessaoConsumoRepository,
                                PedidoRepository pedidoRepository,
                                StoreOrderMetadataRepository metadataRepository,
                                StoreMapper mapper) {
        this.identityResolver = identityResolver;
        this.catalogService = catalogService;
        this.infrastructureService = infrastructureService;
        this.paymentService = paymentService;
        this.analyticsService = analyticsService;
        this.produtoRepository = produtoRepository;
        this.variacaoProdutoRepository = variacaoProdutoRepository;
        this.sessaoConsumoRepository = sessaoConsumoRepository;
        this.pedidoRepository = pedidoRepository;
        this.metadataRepository = metadataRepository;
        this.mapper = mapper;
    }

    @Transactional
    public StoreOrderDTO checkout(StoreCheckoutRequest request, HttpServletRequest httpRequest) {
        StoreSocioIdentityDTO identity = resolveBuyer(request, httpRequest);

        if (StringUtils.hasText(request.getIdempotencyKey())) {
            var existing = metadataRepository.findByIdempotencyKeyAndSocioId(
                    request.getIdempotencyKey(), identity.getSocioId());
            if (existing.isPresent()) {
                return mapper.toOrderDTO(existing.get());
            }
        }

        Cliente cliente = identityResolver.ensureMapping(identity);
        StoreInfrastructureService.FulfillmentUnit fulfillmentUnit = infrastructureService.ensureFulfillmentUnit();
        SessaoConsumo sessao = createHiddenSession(cliente, fulfillmentUnit.unidadeAtendimento());

        Pedido pedido = Pedido.builder()
                .numero(generateOrderNumber())
                .sessaoConsumo(sessao)
                .status(StatusPedido.CRIADO)
                .statusFinanceiro(StatusFinanceiroPedido.NAO_PAGO)
                .tipoPagamento(TipoPagamentoPedido.POS_PAGO)
                .observacoes("Ordem Loja GDSE")
                .build();
        pedido = pedidoRepository.save(pedido);

        SubPedido subPedido = SubPedido.builder()
                .numero(pedido.getNumero() + "-SEP")
                .pedido(pedido)
                .cozinha(fulfillmentUnit.fulfillment())
                .unidadeAtendimento(fulfillmentUnit.unidadeAtendimento())
                .status(StatusSubPedido.CRIADO)
                .observacoes("Separação interna Loja GDSE")
                .build();

        List<ItemPedido> itens = new ArrayList<>();
        for (StoreCarrinhoItemRequest itemRequest : request.getItens()) {
            itens.add(createItem(pedido, subPedido, itemRequest, identity));
        }
        subPedido.setItens(itens);
        subPedido.calcularTotal();
        pedido.getSubPedidos().add(subPedido);
        pedido.setItens(itens);
        pedido.calcularTotal();
        pedido = pedidoRepository.save(pedido);

        StoreOrderMetadata metadata = new StoreOrderMetadata();
        metadata.setPedido(pedido);
        metadata.setSocioId(identity.getSocioId());
        metadata.setIdempotencyKey(request.getIdempotencyKey());
        metadata.setEnderecoEntrega(request.getEnderecoEntrega());
        metadata.setNotas(request.getNotas());
        metadata = metadataRepository.save(metadata);

        analyticsService.track("checkout_started", identity.getSocioId(), null, pedido.getId(), null);
        paymentService.initiatePayment(pedido, metadata, request.getMetodoPagamento(), identity.getPhone());
        analyticsService.track("purchase_completed", identity.getSocioId(), null, pedido.getId(), null);

        return mapper.toOrderDTO(metadata);
    }

    private StoreSocioIdentityDTO resolveBuyer(StoreCheckoutRequest request, HttpServletRequest httpRequest) {
        StoreSocioIdentityDTO identity = identityResolver.resolveOptional(httpRequest);
        if (identity != null) {
            return identity;
        }

        if (!StringUtils.hasText(request.getCompradorTelefone())) {
            throw new BusinessException("Telefone do comprador é obrigatório para checkout público");
        }
        return StoreSocioIdentityDTO.publicBuyer(
                request.getCompradorNome(),
                request.getCompradorTelefone(),
                request.getCompradorEmail());
    }

    private SessaoConsumo createHiddenSession(Cliente cliente, UnidadeAtendimento unidadeAtendimento) {
        SessaoConsumo sessao = SessaoConsumo.builder()
                .qrCodeSessao(UUID.randomUUID().toString())
                .cliente(cliente)
                .unidadeAtendimento(unidadeAtendimento)
                .modoAnonimo(false)
                .status(StatusSessaoConsumo.ABERTA)
                .tipoSessao(TipoSessao.POS_PAGO)
                .abertaEm(LocalDateTime.now())
                .build();
        return sessaoConsumoRepository.save(sessao);
    }

    private ItemPedido createItem(Pedido pedido, SubPedido subPedido, StoreCarrinhoItemRequest request,
                                  StoreSocioIdentityDTO identity) {
        Produto produto = produtoRepository.findById(request.getProdutoId())
                .filter(catalogService::isStoreProduct)
                .orElseThrow(() -> new ResourceNotFoundException("Produto da loja não encontrado"));
        if (!Boolean.TRUE.equals(produto.getAtivo()) || !Boolean.TRUE.equals(produto.getDisponivel())) {
            throw new BusinessException("Produto indisponível");
        }

        VariacaoProduto variacao = null;
        BigDecimal unitPrice = produto.getPreco();
        if (request.getVariacaoId() != null) {
            variacao = variacaoProdutoRepository
                    .findByIdAndProdutoIdAndAtivoTrue(request.getVariacaoId(), produto.getId())
                    .orElseThrow(() -> new BusinessException("Variação indisponível"));
            validateAndReserveStock(variacao, request.getQuantidade());
            if (variacao.getPreco() != null) {
                unitPrice = variacao.getPreco();
            }
        } else if (!produto.getVariacoes().isEmpty()) {
            throw new BusinessException("Selecione uma variação do produto");
        }

        ItemPedido item = ItemPedido.builder()
                .pedido(pedido)
                .subPedido(subPedido)
                .produto(produto)
                .variacaoProduto(variacao)
                .quantidade(request.getQuantidade())
                .precoUnitario(unitPrice)
                .observacoes(request.getObservacoes())
                .personalizedName(request.getPersonalizedName())
                .premiumPackaging(request.getPremiumPackaging())
                .qrIdentityEnabled(request.getQrIdentityEnabled())
                .qrIdentityTokenHash(Boolean.TRUE.equals(request.getQrIdentityEnabled()) && identity.isSocio()
                        ? secureQrToken(identity.getSocioId(), pedido.getNumero(), produto.getId())
                        : null)
                .build();
        item.calcularSubtotal();
        return item;
    }

    private void validateAndReserveStock(VariacaoProduto variacao, Integer quantidade) {
        if (variacao.getStock() == null) return;
        if (variacao.getStock() < quantidade) {
            throw new BusinessException("Stock insuficiente para a variação selecionada");
        }
        variacao.setStock(variacao.getStock() - quantidade);
        variacaoProdutoRepository.save(variacao);
    }

    private String secureQrToken(String socioId, String orderNumber, Long productId) {
        try {
            String payload = socioId + ":" + orderNumber + ":" + productId + ":" + UUID.randomUUID();
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new BusinessException("Falha ao gerar token seguro do QR");
        }
    }

    private String generateOrderNumber() {
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        int random = (int) (Math.random() * 1000);
        return String.format("ORD-%s-%03d", date, random);
    }
}

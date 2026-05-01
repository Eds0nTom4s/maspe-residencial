package com.restaurante.store.service;

import com.restaurante.exception.ResourceNotFoundException;
import com.restaurante.exception.BusinessException;
import com.restaurante.model.entity.ItemPedido;
import com.restaurante.model.entity.Pedido;
import com.restaurante.model.entity.VariacaoProduto;
import com.restaurante.model.enums.StatusFinanceiroPedido;
import com.restaurante.model.enums.StatusPedido;
import com.restaurante.model.enums.StatusSubPedido;
import com.restaurante.store.dto.StoreAdminResumoDTO;
import com.restaurante.repository.PedidoRepository;
import com.restaurante.repository.SubPedidoRepository;
import com.restaurante.repository.VariacaoProdutoRepository;
import com.restaurante.store.dto.StoreOrderDTO;
import com.restaurante.store.dto.StoreOrderTrackingDTO;
import com.restaurante.store.dto.StoreSocioIdentityDTO;
import com.restaurante.store.mapper.StoreMapper;
import com.restaurante.store.model.StoreOrderMetadata;
import com.restaurante.store.repository.StoreOrderMetadataRepository;
import com.restaurante.store.security.StoreSocioIdentityResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class StoreOrderService {

    private final StoreOrderMetadataRepository metadataRepository;
    private final StoreSocioIdentityResolver identityResolver;
    private final StoreMapper mapper;
    private final PedidoRepository pedidoRepository;
    private final SubPedidoRepository subPedidoRepository;
    private final VariacaoProdutoRepository variacaoProdutoRepository;

    public StoreOrderService(StoreOrderMetadataRepository metadataRepository,
                             StoreSocioIdentityResolver identityResolver,
                             StoreMapper mapper,
                             PedidoRepository pedidoRepository,
                             SubPedidoRepository subPedidoRepository,
                             VariacaoProdutoRepository variacaoProdutoRepository) {
        this.metadataRepository = metadataRepository;
        this.identityResolver = identityResolver;
        this.mapper = mapper;
        this.pedidoRepository = pedidoRepository;
        this.subPedidoRepository = subPedidoRepository;
        this.variacaoProdutoRepository = variacaoProdutoRepository;
    }

    @Transactional
    public StoreOrderDTO getOrder(Long orderId, HttpServletRequest request) {
        StoreSocioIdentityDTO identity = identityResolver.resolve(request);
        return metadataRepository.findByPedidoId(orderId)
                .filter(metadata -> metadata.getSocioId().equals(identity.getSocioId()))
                .map(mapper::toOrderDTO)
                .orElseThrow(() -> new ResourceNotFoundException("Ordem da loja não encontrada"));
    }

    @Transactional(readOnly = true)
    public List<StoreOrderDTO> listMyOrders(HttpServletRequest request) {
        StoreSocioIdentityDTO identity = identityResolver.resolve(request);
        return metadataRepository.findBySocioIdOrderByCreatedAtDesc(identity.getSocioId())
                .stream()
                .map(mapper::toOrderDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public StoreOrderTrackingDTO trackAuthenticatedOrder(Long orderId, HttpServletRequest request) {
        StoreSocioIdentityDTO identity = identityResolver.resolve(request);
        return metadataRepository.findByPedidoId(orderId)
                .filter(metadata -> metadata.getSocioId().equals(identity.getSocioId()))
                .map(mapper::toTrackingDTO)
                .orElseThrow(() -> new ResourceNotFoundException("Ordem da loja não encontrada"));
    }

    @Transactional(readOnly = true)
    public StoreOrderTrackingDTO trackPublicOrder(String numero, String telefone) {
        if (numero == null || numero.isBlank() || telefone == null || telefone.isBlank()) {
            throw new BusinessException("Número da ordem e telefone são obrigatórios");
        }
        StoreOrderMetadata metadata = metadataRepository.findByPedidoNumero(numero)
                .orElseThrow(() -> new ResourceNotFoundException("Ordem da loja não encontrada"));
        String telefoneOrdem = metadata.getPedido().getSessaoConsumo() != null
                && metadata.getPedido().getSessaoConsumo().getCliente() != null
                ? metadata.getPedido().getSessaoConsumo().getCliente().getTelefone()
                : null;
        if (telefoneOrdem == null || !normalizarTelefone(telefoneOrdem).equals(normalizarTelefone(telefone))) {
            throw new ResourceNotFoundException("Ordem da loja não encontrada");
        }
        return mapper.toTrackingDTO(metadata);
    }

    @Transactional(readOnly = true)
    public Page<StoreOrderDTO> listAdminOrders(Pageable pageable) {
        return metadataRepository.findAllByOrderByCreatedAtDesc(pageable)
                .map(mapper::toOrderDTO);
    }

    @Transactional(readOnly = true)
    public StoreAdminResumoDTO getAdminSummary() {
        StoreAdminResumoDTO dto = new StoreAdminResumoDTO();
        dto.setTotalOrdens(metadataRepository.count());
        dto.setAguardandoPagamento(metadataRepository.countByPedidoStatusFinanceiro(StatusFinanceiroPedido.NAO_PAGO));
        dto.setPagas(metadataRepository.countByPedidoStatusFinanceiro(StatusFinanceiroPedido.PAGO));
        dto.setCanceladas(metadataRepository.countByPedidoStatus(StatusPedido.CANCELADO));
        dto.setEntregues(metadataRepository.countByPedidoStatus(StatusPedido.FINALIZADO));
        dto.setReceitaConfirmada(metadataRepository.sumReceitaPaga());
        long emSeparacao = metadataRepository.findAll().stream()
                .flatMap(metadata -> metadata.getPedido().getSubPedidos().stream())
                .filter(subPedido -> subPedido.getStatus() == StatusSubPedido.EM_PREPARACAO
                        || subPedido.getStatus() == StatusSubPedido.PRONTO)
                .count();
        dto.setEmSeparacao(emSeparacao);
        if (dto.getReceitaConfirmada() == null) {
            dto.setReceitaConfirmada(BigDecimal.ZERO);
        }
        return dto;
    }

    @Transactional
    public StoreOrderDTO cancelUnpaidOrder(Long orderId, HttpServletRequest request) {
        StoreSocioIdentityDTO identity = identityResolver.resolve(request);
        StoreOrderMetadata metadata = metadataRepository.findByPedidoId(orderId)
                .filter(found -> found.getSocioId().equals(identity.getSocioId()))
                .orElseThrow(() -> new ResourceNotFoundException("Ordem da loja não encontrada"));

        Pedido pedido = metadata.getPedido();
        if (pedido.getStatus() == StatusPedido.CANCELADO) {
            return mapper.toOrderDTO(metadata);
        }
        if (pedido.getStatusFinanceiro() == StatusFinanceiroPedido.PAGO) {
            throw new BusinessException("Ordem paga não pode ser cancelada por este fluxo");
        }
        boolean entregue = pedido.getSubPedidos().stream()
                .anyMatch(subPedido -> subPedido.getStatus() == StatusSubPedido.ENTREGUE);
        if (entregue) {
            throw new BusinessException("Ordem entregue não pode ser cancelada por este fluxo");
        }

        releaseStock(pedido);
        pedido.getSubPedidos().forEach(subPedido -> {
            if (!subPedido.isTerminal()) {
                subPedido.setStatus(StatusSubPedido.CANCELADO);
                subPedidoRepository.save(subPedido);
            }
        });
        pedido.setStatus(StatusPedido.CANCELADO);
        pedidoRepository.save(pedido);
        return mapper.toOrderDTO(metadata);
    }

    private void releaseStock(Pedido pedido) {
        for (ItemPedido item : pedido.getItens()) {
            VariacaoProduto variacao = item.getVariacaoProduto();
            if (variacao != null && variacao.getStock() != null) {
                variacao.setStock(variacao.getStock() + item.getQuantidade());
                variacaoProdutoRepository.save(variacao);
            }
        }
    }

    private String normalizarTelefone(String telefone) {
        return telefone == null ? "" : telefone.replaceAll("[^0-9+]", "");
    }
}

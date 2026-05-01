package com.restaurante.store.mapper;

import com.restaurante.model.entity.ItemPedido;
import com.restaurante.model.entity.Pedido;
import com.restaurante.model.entity.Produto;
import com.restaurante.model.entity.SubPedido;
import com.restaurante.model.entity.VariacaoProduto;
import com.restaurante.model.enums.StatusFinanceiroPedido;
import com.restaurante.model.enums.StatusSubPedido;
import com.restaurante.store.dto.StoreOrderDTO;
import com.restaurante.store.dto.StoreOrderTrackingDTO;
import com.restaurante.store.dto.StoreProductDTO;
import com.restaurante.store.dto.StoreProductVariationDTO;
import com.restaurante.store.dto.StoreSeparacaoResponse;
import com.restaurante.store.model.StoreOrderMetadata;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class StoreMapper {

    public StoreProductDTO toProductDTO(Produto produto) {
        StoreProductDTO dto = new StoreProductDTO();
        dto.setId(produto.getId());
        dto.setCodigo(produto.getCodigo());
        dto.setNome(produto.getNome());
        dto.setDescricao(produto.getDescricao());
        dto.setPreco(produto.getPreco());
        dto.setCategoria(produto.getCategoria().name());
        dto.setUrlImagem(produto.getUrlImagem());
        dto.setImagensGaleria(produto.getImagensGaleria());
        dto.setDisponivel(produto.getDisponivel());
        dto.setVariacoes(produto.getVariacoes().stream()
                .filter(v -> Boolean.TRUE.equals(v.getAtivo()))
                .map(this::toVariationDTO)
                .collect(Collectors.toList()));
        return dto;
    }

    public StoreProductVariationDTO toVariationDTO(VariacaoProduto variacao) {
        StoreProductVariationDTO dto = new StoreProductVariationDTO();
        dto.setId(variacao.getId());
        dto.setTamanho(variacao.getTamanho() != null ? variacao.getTamanho() : variacao.getValor());
        dto.setCor(variacao.getCor());
        dto.setSku(variacao.getSku());
        dto.setPreco(variacao.getPreco());
        dto.setStock(variacao.getStock());
        dto.setAtivo(variacao.getAtivo());
        return dto;
    }

    public StoreOrderDTO toOrderDTO(StoreOrderMetadata metadata) {
        Pedido pedido = metadata.getPedido();
        StoreOrderDTO dto = new StoreOrderDTO();
        dto.setId(pedido.getId());
        dto.setNumero(pedido.getNumero());
        dto.setStatus(resolveStoreStatus(pedido));
        dto.setStatusPagamento(pedido.getStatusFinanceiro().name());
        dto.setTotal(pedido.getTotal());
        dto.setMetodoPagamento(metadata.getMetodoPagamento());
        dto.setReferenciaPagamento(metadata.getReferencia());
        dto.setEntidadePagamento(metadata.getEntidade());
        dto.setPaymentUrl(metadata.getPaymentUrl());
        dto.setEnderecoEntrega(metadata.getEnderecoEntrega());
        dto.setNotas(metadata.getNotas());
        dto.setCriadaEm(pedido.getCreatedAt());
        dto.setAtualizadaEm(pedido.getUpdatedAt());
        dto.setItens(pedido.getItens().stream().map(this::toOrderItemDTO).collect(Collectors.toList()));
        return dto;
    }

    public StoreOrderTrackingDTO toTrackingDTO(StoreOrderMetadata metadata) {
        Pedido pedido = metadata.getPedido();
        StoreOrderTrackingDTO dto = new StoreOrderTrackingDTO();
        dto.setOrdemId(pedido.getId());
        dto.setNumero(pedido.getNumero());
        dto.setStatus(resolveStoreStatus(pedido));
        dto.setStatusPagamento(pedido.getStatusFinanceiro().name());
        dto.setEtapaAtual(resolveTrackingStep(pedido));
        dto.setCriadaEm(pedido.getCreatedAt());
        dto.setAtualizadaEm(pedido.getUpdatedAt());

        SubPedido subPedido = pedido.getSubPedidos().stream().findFirst().orElse(null);
        boolean pago = pedido.getStatusFinanceiro() == StatusFinanceiroPedido.PAGO;
        boolean emSeparacao = subPedido != null && (subPedido.getStatus() == StatusSubPedido.EM_PREPARACAO
                || subPedido.getStatus() == StatusSubPedido.PRONTO
                || subPedido.getStatus() == StatusSubPedido.ENTREGUE);
        boolean pronto = subPedido != null && (subPedido.getStatus() == StatusSubPedido.PRONTO
                || subPedido.getStatus() == StatusSubPedido.ENTREGUE);
        boolean entregue = subPedido != null && subPedido.getStatus() == StatusSubPedido.ENTREGUE;

        dto.setEtapas(List.of(
                new StoreOrderTrackingDTO.TrackingStepDTO("CRIADA", "Ordem criada", true, pedido.getCreatedAt()),
                new StoreOrderTrackingDTO.TrackingStepDTO("PAGA", "Pagamento confirmado", pago, pedido.getPagoEm()),
                new StoreOrderTrackingDTO.TrackingStepDTO("EM_SEPARACAO", "Separação iniciada", emSeparacao,
                        subPedido != null ? subPedido.getIniciadoEm() : null),
                new StoreOrderTrackingDTO.TrackingStepDTO("PRONTA", "Pronta para entrega", pronto,
                        subPedido != null ? subPedido.getProntoEm() : null),
                new StoreOrderTrackingDTO.TrackingStepDTO("ENTREGUE", "Ordem entregue", entregue,
                        subPedido != null ? subPedido.getEntregueEm() : null)
        ));
        return dto;
    }

    public StoreSeparacaoResponse toSeparacaoDTO(SubPedido subPedido) {
        StoreSeparacaoResponse dto = new StoreSeparacaoResponse();
        dto.setSubOrdemId(subPedido.getId());
        dto.setNumeroOrdem(subPedido.getPedido().getNumero());
        dto.setStatus(resolveSeparacaoStatus(subPedido.getStatus()));
        if (subPedido.getPedido().getSessaoConsumo() != null
                && subPedido.getPedido().getSessaoConsumo().getCliente() != null) {
            dto.setCompradorNome(subPedido.getPedido().getSessaoConsumo().getCliente().getNome());
            dto.setCompradorTelefone(subPedido.getPedido().getSessaoConsumo().getCliente().getTelefone());
        }
        dto.setCriadaEm(subPedido.getCreatedAt());
        dto.setItens(subPedido.getItens().stream().map(this::toSeparacaoItemDTO).collect(Collectors.toList()));
        return dto;
    }

    private StoreOrderDTO.StoreOrdemItemResponse toOrderItemDTO(ItemPedido item) {
        StoreOrderDTO.StoreOrdemItemResponse dto = new StoreOrderDTO.StoreOrdemItemResponse();
        dto.setProdutoId(item.getProduto().getId());
        dto.setNomeProduto(item.getProduto().getNome());
        dto.setVariacaoDescricao(describeVariation(item.getVariacaoProduto()));
        dto.setQuantidade(item.getQuantidade());
        dto.setPrecoUnitario(item.getPrecoUnitario());
        dto.setSubtotal(item.getSubtotal());
        dto.setObservacoes(item.getObservacoes());
        return dto;
    }

    private StoreSeparacaoResponse.SeparacaoItemResponse toSeparacaoItemDTO(ItemPedido item) {
        StoreSeparacaoResponse.SeparacaoItemResponse dto = new StoreSeparacaoResponse.SeparacaoItemResponse();
        dto.setNomeProduto(item.getProduto().getNome());
        dto.setVariacaoDescricao(describeVariation(item.getVariacaoProduto()));
        dto.setQuantidade(item.getQuantidade());
        dto.setObservacoes(item.getObservacoes());
        return dto;
    }

    private String resolveStoreStatus(Pedido pedido) {
        if (pedido.getStatus() == com.restaurante.model.enums.StatusPedido.CANCELADO) {
            return "CANCELADO";
        }
        if (pedido.getStatusFinanceiro() != StatusFinanceiroPedido.PAGO) {
            return pedido.getStatusFinanceiro() == StatusFinanceiroPedido.ESTORNADO ? "CANCELADO" : "AGUARDANDO_PAGAMENTO";
        }

        StatusSubPedido status = pedido.getSubPedidos().stream()
                .map(SubPedido::getStatus)
                .max(Comparator.comparingInt(Enum::ordinal))
                .orElse(StatusSubPedido.CRIADO);
        return switch (status) {
            case CRIADO, PENDENTE -> "PAGO";
            case EM_PREPARACAO -> "EM_SEPARACAO";
            case PRONTO -> "PRONTO_PARA_ENTREGA";
            case ENTREGUE -> "ENTREGUE";
            case CANCELADO -> "CANCELADO";
        };
    }

    private String resolveTrackingStep(Pedido pedido) {
        if (pedido.getStatus() == com.restaurante.model.enums.StatusPedido.CANCELADO) {
            return "CANCELADA";
        }
        if (pedido.getStatusFinanceiro() != StatusFinanceiroPedido.PAGO) {
            return "AGUARDANDO_PAGAMENTO";
        }
        StatusSubPedido status = pedido.getSubPedidos().stream()
                .map(SubPedido::getStatus)
                .max(Comparator.comparingInt(Enum::ordinal))
                .orElse(StatusSubPedido.CRIADO);
        return switch (status) {
            case CRIADO, PENDENTE -> "PAGAMENTO_CONFIRMADO";
            case EM_PREPARACAO -> "EM_SEPARACAO";
            case PRONTO -> "PRONTA_PARA_ENTREGA";
            case ENTREGUE -> "ENTREGUE";
            case CANCELADO -> "CANCELADA";
        };
    }

    private String resolveSeparacaoStatus(StatusSubPedido status) {
        return switch (status) {
            case CRIADO, PENDENTE -> "PENDENTE";
            case EM_PREPARACAO -> "EM_SEPARACAO";
            case PRONTO -> "PRONTO";
            case ENTREGUE -> "ENTREGUE";
            case CANCELADO -> "CANCELADO";
        };
    }

    private String describeVariation(VariacaoProduto variacao) {
        if (variacao == null) return null;
        List<String> parts = new java.util.ArrayList<>();
        if (variacao.getTamanho() != null) parts.add("Tamanho: " + variacao.getTamanho());
        else if (variacao.getValor() != null) parts.add("Variação: " + variacao.getValor());
        if (variacao.getCor() != null) parts.add("Cor: " + variacao.getCor());
        if (variacao.getSku() != null) parts.add("SKU: " + variacao.getSku());
        return String.join(" | ", parts);
    }
}

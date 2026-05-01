package com.restaurante.store.service;

import com.restaurante.exception.BusinessException;
import com.restaurante.exception.ResourceNotFoundException;
import com.restaurante.model.entity.SubPedido;
import com.restaurante.model.enums.StatusSubPedido;
import com.restaurante.repository.PedidoRepository;
import com.restaurante.repository.SubPedidoRepository;
import com.restaurante.store.dto.StoreSeparacaoResponse;
import com.restaurante.store.mapper.StoreMapper;
import com.restaurante.store.repository.StoreOrderMetadataRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class StoreFulfillmentService {

    private final SubPedidoRepository subPedidoRepository;
    private final PedidoRepository pedidoRepository;
    private final StoreOrderMetadataRepository metadataRepository;
    private final StoreMapper mapper;

    public StoreFulfillmentService(SubPedidoRepository subPedidoRepository,
                                   PedidoRepository pedidoRepository,
                                   StoreOrderMetadataRepository metadataRepository,
                                   StoreMapper mapper) {
        this.subPedidoRepository = subPedidoRepository;
        this.pedidoRepository = pedidoRepository;
        this.metadataRepository = metadataRepository;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<StoreSeparacaoResponse> listPendingFulfillment() {
        return metadataRepository.findAll().stream()
                .flatMap(metadata -> metadata.getPedido().getSubPedidos().stream())
                .filter(subPedido -> subPedido.getStatus() != StatusSubPedido.ENTREGUE
                        && subPedido.getStatus() != StatusSubPedido.CANCELADO)
                .map(mapper::toSeparacaoDTO)
                .toList();
    }

    @Transactional
    public StoreSeparacaoResponse process(Long subPedidoId) {
        SubPedido subPedido = findStoreSubPedido(subPedidoId);
        if (!subPedido.getPedido().isPago()) {
            throw new BusinessException("Ordem ainda não está paga");
        }
        if (subPedido.getStatus() == StatusSubPedido.CRIADO) {
            subPedido.setStatus(StatusSubPedido.PENDENTE);
        }
        if (subPedido.getStatus() == StatusSubPedido.PENDENTE) {
            subPedido.setStatus(StatusSubPedido.EM_PREPARACAO);
            subPedido.setIniciadoEm(LocalDateTime.now());
        }
        subPedido = subPedidoRepository.save(subPedido);
        return mapper.toSeparacaoDTO(subPedido);
    }

    @Transactional
    public StoreSeparacaoResponse deliver(Long subPedidoId) {
        SubPedido subPedido = findStoreSubPedido(subPedidoId);
        if (subPedido.getStatus() == StatusSubPedido.CRIADO || subPedido.getStatus() == StatusSubPedido.PENDENTE) {
            subPedido.setStatus(StatusSubPedido.EM_PREPARACAO);
            subPedido.setIniciadoEm(LocalDateTime.now());
        }
        if (subPedido.getStatus() == StatusSubPedido.EM_PREPARACAO) {
            subPedido.setStatus(StatusSubPedido.PRONTO);
            subPedido.setProntoEm(LocalDateTime.now());
        }
        if (subPedido.getStatus() == StatusSubPedido.PRONTO) {
            subPedido.setStatus(StatusSubPedido.ENTREGUE);
            subPedido.setEntregueEm(LocalDateTime.now());
        }
        subPedido = subPedidoRepository.save(subPedido);
        var pedido = subPedido.getPedido();
        pedido.setStatus(com.restaurante.model.enums.StatusPedido.FINALIZADO);
        pedidoRepository.save(pedido);
        return mapper.toSeparacaoDTO(subPedido);
    }

    private SubPedido findStoreSubPedido(Long subPedidoId) {
        SubPedido subPedido = subPedidoRepository.findByIdWithDetails(subPedidoId)
                .orElseThrow(() -> new ResourceNotFoundException("Separação não encontrada"));
        if (!metadataRepository.existsByPedidoId(subPedido.getPedido().getId())) {
            throw new ResourceNotFoundException("Separação da loja não encontrada");
        }
        return subPedido;
    }
}

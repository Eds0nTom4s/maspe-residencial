package com.restaurante.store;

import com.restaurante.model.entity.*;
import com.restaurante.model.enums.StatusFinanceiroPedido;
import com.restaurante.model.enums.StatusSubPedido;
import com.restaurante.repository.PedidoRepository;
import com.restaurante.repository.SubPedidoRepository;
import com.restaurante.store.dto.StoreSeparacaoResponse;
import com.restaurante.store.mapper.StoreMapper;
import com.restaurante.store.model.StoreOrderMetadata;
import com.restaurante.store.repository.StoreOrderMetadataRepository;
import com.restaurante.store.service.StoreFulfillmentService;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class StoreFulfillmentServiceTest {

    @Test
    void transicoesDeSeparacaoFuncionam() {
        SubPedidoRepository subPedidoRepository = mock(SubPedidoRepository.class);
        PedidoRepository pedidoRepository = mock(PedidoRepository.class);
        StoreOrderMetadataRepository metadataRepository = mock(StoreOrderMetadataRepository.class);
        StoreMapper mapper = mock(StoreMapper.class);
        StoreFulfillmentService service = new StoreFulfillmentService(subPedidoRepository, pedidoRepository,
                metadataRepository, mapper);

        Pedido pedido = Pedido.builder().statusFinanceiro(StatusFinanceiroPedido.PAGO).build();
        pedido.setId(10L);
        SubPedido subPedido = SubPedido.builder()
                .pedido(pedido)
                .status(StatusSubPedido.PENDENTE)
                .build();
        subPedido.setId(1L);

        when(subPedidoRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(subPedido));
        when(metadataRepository.existsByPedidoId(10L)).thenReturn(true);
        when(subPedidoRepository.save(any(SubPedido.class))).thenAnswer(inv -> inv.getArgument(0));
        StoreSeparacaoResponse dto = new StoreSeparacaoResponse();
        dto.setStatus("EM_SEPARACAO");
        when(mapper.toSeparacaoDTO(any(SubPedido.class))).thenReturn(dto);

        StoreSeparacaoResponse response = service.process(1L);

        assertEquals("EM_SEPARACAO", response.getStatus());
        assertEquals(StatusSubPedido.EM_PREPARACAO, subPedido.getStatus());
    }
}

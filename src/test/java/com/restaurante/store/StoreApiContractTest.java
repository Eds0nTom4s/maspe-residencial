package com.restaurante.store;

import com.restaurante.store.dto.StoreOrderDTO;
import com.restaurante.store.dto.StoreAdminResumoDTO;
import com.restaurante.store.dto.StoreCartDTO;
import com.restaurante.store.dto.StoreCheckoutRequest;
import com.restaurante.store.dto.StoreOrderTrackingDTO;
import com.restaurante.store.dto.StoreProductDTO;
import com.restaurante.store.dto.StoreProductVariationDTO;
import com.restaurante.store.dto.StoreSeparacaoResponse;
import com.restaurante.store.dto.StoreSocioIdentityDTO;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;

class StoreApiContractTest {

    @Test
    void dtosPublicosNaoExpoemTerminologiaDeRestaurante() {
        Set<String> forbidden = Set.of("mesa", "cozinha", "garcom", "garçom", "restaurante",
                "sessaoconsumo", "fundoconsumo", "subpedido");

        for (Class<?> dtoClass : java.util.List.of(StoreOrderDTO.class, StoreProductDTO.class,
                StoreProductVariationDTO.class, StoreSeparacaoResponse.class, StoreCartDTO.class,
                StoreCheckoutRequest.class, StoreSocioIdentityDTO.class, StoreOrderTrackingDTO.class,
                StoreAdminResumoDTO.class)) {
            Set<String> fields = Arrays.stream(dtoClass.getDeclaredFields())
                    .map(field -> field.getName().toLowerCase())
                    .collect(Collectors.toSet());
            forbidden.forEach(term -> fields.forEach(field ->
                    assertFalse(field.contains(term), dtoClass.getSimpleName() + " expõe " + term)));
        }
    }
}

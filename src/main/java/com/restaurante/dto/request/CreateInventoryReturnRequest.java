package com.restaurante.dto.request;

import com.restaurante.model.enums.InventoryRestockPolicy;
import com.restaurante.model.enums.InventoryReturnReasonCategory;
import com.restaurante.model.enums.InventoryReturnType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class CreateInventoryReturnRequest {
    @NotNull
    private Long pedidoId;

    @NotNull
    private InventoryReturnType returnType;

    private InventoryReturnReasonCategory reasonCategory;
    private String reasonDescription;

    @Valid
    @NotNull
    private List<ReturnLine> lines;

    @Data
    public static class ReturnLine {
        @NotNull
        private Long pedidoItemId;

        @NotNull
        private BigDecimal quantityReturned;

        private InventoryRestockPolicy restockPolicy;
    }
}


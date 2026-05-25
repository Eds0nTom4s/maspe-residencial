package com.restaurante.dto.response;

import lombok.Data;

import java.util.List;

@Data
public class InventoryConsumptionDetailsResponse {
    private InventoryConsumptionRecordResponse record;
    private List<InventoryConsumptionLineResponse> lines;
}


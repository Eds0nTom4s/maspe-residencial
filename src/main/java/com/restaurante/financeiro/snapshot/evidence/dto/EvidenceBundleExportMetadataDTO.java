package com.restaurante.financeiro.snapshot.evidence.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class EvidenceBundleExportMetadataDTO {
    private String formato;
    private LocalDateTime exportadoEm;
}


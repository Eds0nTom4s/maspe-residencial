package com.restaurante.provisioning.preview.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InstituicaoPreviewDTO {
    private String nome;
    private String sigla;
}


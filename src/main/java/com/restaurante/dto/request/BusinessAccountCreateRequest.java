package com.restaurante.dto.request;

import com.restaurante.model.enums.BusinessAccountEstado;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BusinessAccountCreateRequest {

    @NotBlank
    private String nome;

    @NotBlank
    private String slug;

    private String nif;
    private String email;
    private String telefone;
    private BusinessAccountEstado estado;
    private Long responsavelUserId;
    private String observacao;
    private List<Long> tenantIds;
}

package com.restaurante.dto.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class ReordenarImagensRequest {

    @NotEmpty(message = "A ordem das imagens é obrigatória")
    private List<Long> imagemIds;
}

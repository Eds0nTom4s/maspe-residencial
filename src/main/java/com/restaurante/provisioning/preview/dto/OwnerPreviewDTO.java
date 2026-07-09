package com.restaurante.provisioning.preview.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OwnerPreviewDTO {
    private boolean criarUsuario;
    private String email;
    private String telefone;
    private boolean userExistente;
    private boolean seraReutilizado;
}


package com.restaurante.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO de resposta para Cliente
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClienteResponse {

    private Long id;
    private String telefone;
    private String nome;
    private Boolean telefoneVerificado;
    private Boolean ativo;
    private LocalDateTime createdAt;
}

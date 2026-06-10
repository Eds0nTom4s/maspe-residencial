package com.restaurante.dto.response;

import com.restaurante.model.enums.BusinessAccountEstado;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BusinessAccountResponse {
    private Long id;
    private String nome;
    private String slug;
    private String nif;
    private String email;
    private String telefone;
    private BusinessAccountEstado estado;
    private Long responsavelUserId;
    private String responsavelNome;
    private String responsavelEmail;
    private String observacao;
    private Long tenantCount;
    private Long memberCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

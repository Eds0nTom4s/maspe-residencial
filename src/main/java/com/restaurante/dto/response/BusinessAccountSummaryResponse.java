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
public class BusinessAccountSummaryResponse {
    private Long id;
    private String nome;
    private String slug;
    private BusinessAccountEstado estado;
    private Long responsavelUserId;
    private String responsavelNome;
    private Integer maxTenants;
    private Long tenantCount;
    private Long memberCount;
    private Boolean hasTenants;
    private LocalDateTime createdAt;
}

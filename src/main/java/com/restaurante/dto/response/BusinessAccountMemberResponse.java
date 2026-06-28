package com.restaurante.dto.response;

import com.restaurante.model.enums.BusinessAccountMemberEstado;
import com.restaurante.model.enums.BusinessAccountRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BusinessAccountMemberResponse {
    private Long id;
    private Long businessAccountId;
    private Long userId;
    private String username;
    private String nomeCompleto;
    private String email;
    private String telefone;
    private BusinessAccountRole role;
    private BusinessAccountMemberEstado estado;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

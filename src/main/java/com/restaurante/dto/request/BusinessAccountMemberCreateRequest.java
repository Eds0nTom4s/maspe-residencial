package com.restaurante.dto.request;

import com.restaurante.model.enums.BusinessAccountMemberEstado;
import com.restaurante.model.enums.BusinessAccountRole;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BusinessAccountMemberCreateRequest {

    @NotNull
    private Long userId;

    @NotNull
    private BusinessAccountRole role;

    private BusinessAccountMemberEstado estado;
}

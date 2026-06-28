package com.restaurante.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SelectTenantResponse {
    private Long userId;
    private Long tenantId;
    private String tenantCode;
    private String slug;
    private String nome;
    private String tenantNome;
    private String tokenType;
    private String accessToken;
    private long expiresIn;
    private Set<String> roles;
}

package com.restaurante.dto.response;

import com.restaurante.model.enums.TenantEstado;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantMeResponse {
    private Long tenantId;
    private String nome;
    private String slug;
    private String tenantCode;
    private TenantEstado estado;

    @Builder.Default
    private Set<String> roles = Set.of();

    private String planoCodigo;
    private String planoNome;

    private EffectiveLimitsResponse limites;
    private UserMeResponse usuario;

    @Builder.Default
    private List<InstituicaoResumoResponse> instituicoes = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class EffectiveLimitsResponse {
        private Integer maxInstituicoes;
        private Integer maxUnidadesAtendimento;
        private Integer maxProdutos;
        private Integer maxUsuarios;
        private Integer maxQrCodes;
        private Integer maxDispositivos;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UserMeResponse {
        private Long userId;
        private String username;
        private String nomeCompleto;
        private String email;
        private String telefone;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class InstituicaoResumoResponse {
        private Long id;
        private String nome;
        private String sigla;
        private boolean ativa;
    }
}


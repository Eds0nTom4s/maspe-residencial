package com.restaurante.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlatformTenantResponse {
    private Long tenantId;
    private String nome;
    private String tenantCode;
    private String slug;
    private String estado;
    private Boolean ativo;
    private String templateCode;
    private Integer templateVersion;
    private String planoCodigo;
    private LocalDateTime criadoEm;
    private QrPrincipal qrPrincipal;
    private Cardapio cardapio;
    private Boolean selecionavel;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class QrPrincipal {
        private String qrToken;
        private String qrUrlPublica;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Cardapio {
        private Boolean publicado;
        private Integer maxCategorias;
        private Integer maxProdutos;
    }
}

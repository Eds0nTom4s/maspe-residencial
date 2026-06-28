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
    private String tipo;
    private String estado;
    private Boolean ativo;
    private String templateCode;
    private Integer templateVersion;
    private String planoCodigo;
    private String planoNome;
    private String billingPlanCode;
    private String billingPlanNome;
    private String subscricaoStatus;
    private Long businessAccountId;
    private String businessAccountNome;
    private String businessAccountSlug;
    private String businessAccountEstado;
    private LocalDateTime criadoEm;
    private QrPrincipal qrPrincipal;
    private Cardapio cardapio;
    private Limites limites;
    private Modulos modulos;
    private SessaoConsumo sessaoConsumo;
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

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Limites {
        private Integer maxInstituicoes;
        private Integer maxUnidadesAtendimento;
        private Integer maxProdutos;
        private Integer maxCategorias;
        private Integer maxUsuarios;
        private Integer maxQrCodes;
        private Integer maxDispositivos;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Modulos {
        private Boolean sessaoConsumoEnabled;
        private Boolean pedidoDiretoEnabled;
        private Boolean mesasEnabled;
        private Boolean qrMesaEnabled;
        private Boolean caixaEnabled;
        private Boolean kdsEnabled;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SessaoConsumo {
        private Boolean enabled;
        private Boolean permitirPrePago;
        private Boolean permitirPosPago;
        private Boolean permitirModoAnonimo;
        private Integer expiracaoHoras;
    }
}

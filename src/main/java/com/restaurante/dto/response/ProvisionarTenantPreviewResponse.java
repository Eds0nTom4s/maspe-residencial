package com.restaurante.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProvisionarTenantPreviewResponse {

    @Builder.Default
    private boolean permitido = false;

    @Builder.Default
    private List<ProvisioningValidationMessage> bloqueios = new ArrayList<>();

    @Builder.Default
    private List<ProvisioningValidationMessage> avisos = new ArrayList<>();

    private ProvisioningResourcePlanDTO recursosPlanejados;
    private ProvisioningLimitsPreviewDTO limites;
    private ProvisioningTemplatePreviewDTO template;
    private TenantPreviewDTO tenant;
    private InstituicaoPreviewDTO instituicao;
    private OwnerPreviewDTO owner;
    private ProvisioningEstimateDTO estimativa;

    public enum Severity {
        INFO,
        WARNING,
        BLOCKING
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ProvisioningValidationMessage {
        private String codigo;
        private Severity severidade;
        private String campo;
        private String mensagem;
        private String detalhe;
        private String acaoRecomendada;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ProvisioningResourcePlanDTO {
        private int tenantsCriados;
        private int instituicoesCriadas;
        private int unidadesAtendimentoCriadas;
        private int categoriasCriadas;
        private int usuariosCriados;
        private int tenantUsersCriados;
        private int mesasCriadas;
        private int qrCodesCriados;
        private boolean qrPrincipalCriado;
        private int qrPorMesaCriados;
        private boolean overrideLimitesCriado;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ProvisioningLimitsPreviewDTO {
        @Builder.Default
        private List<LimitLine> linhas = new ArrayList<>();

        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        @Builder
        public static class LimitLine {
            private String recurso;
            private Integer limite;
            private long usadoAtualmente;
            private int novo;
            private long totalAposProvisionamento;
            private boolean excede;
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ProvisioningTemplatePreviewDTO {
        private String codigo;
        private String nome;
        private String tipoTenant;
        private boolean criarMesas;
        private int quantidadeMesas;
        private boolean criarQrPorMesa;
        private String prefixoMesa;
        private String unidadeAtendimentoDefaultNome;
        private String qrPrincipalTipo;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TenantPreviewDTO {
        private String nome;
        private String slugNormalizado;
        private String tenantCodeNormalizado;
        private String tenantCodeGerado;
        private String tipo;
        private boolean ativarTenant;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class InstituicaoPreviewDTO {
        private String nome;
        private String sigla;
        private boolean ativa;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class OwnerPreviewDTO {
        private boolean criarUsuario;
        private String nome;
        private String email;
        private String telefone;
        private boolean ownerExistente;
        private boolean ownerSeraReutilizado;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ProvisioningEstimateDTO {
        private int totalMesas;
        private int totalQrUrlsGeradas;
        private boolean precisaOverride;
        private boolean prontoParaExecutar;
    }
}


package com.restaurante.businesstemplate.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BusinessTemplatePreviewResponse {

    private String templateCode;
    private Integer templateVersion;

    /**
     * True quando não há bloqueios (erros bloqueantes).
     */
    private Boolean permitido;

    private List<ValidationMessage> bloqueios;
    private List<ValidationMessage> avisos;

    private PlanResources recursosPlanejados;
    private PlanLimitsPreview limites;
    private TemplatePoliciesPreview politicas;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ValidationMessage {
        private String code;
        private String field;
        private String message;
        private String detail;
        private String recommendedAction;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PlanResources {
        private int tenantsCriados;
        private int instituicoesCriadas;
        private int unidadesAtendimentoCriadas;
        private int categoriasCriadas;
        private int usuariosCriados;
        private int tenantUsersCriados;
        private int mesasCriadas;
        private int qrCodesCriados;
        private int dispositivosCriados;
        private int unidadesProducaoCriadas;
        private int rotasProducaoCriadas;
        private int checklistTemplatesCriados;
        private int checklistItemsCriados;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PlanLimitsPreview {
        private List<LimitLine> linhas;
        private Boolean precisaOverride;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class LimitLine {
        private String recurso;
        private Long atual;
        private Long planejado;
        private Long totalApos;
        private Long maximo;
        private Boolean excede;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TemplatePoliciesPreview {
        private Boolean requireOpenTurnoForOrders;
        private String logisticsMode;
        private Boolean allowPickup;
        private Boolean allowManualPayment;
        private Boolean allowDigitalPayment;
        private String stockMode;
        private Boolean productionEnabled;
        private Boolean posEnabled;
        private Boolean kdsEnabled;
        private Boolean allowTableQr;
        private Boolean snapshotFinanceiroEnabled;
        private Boolean preFechoEnabled;
    }
}


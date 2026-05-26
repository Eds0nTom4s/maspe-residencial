package com.restaurante.businesstemplate.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BusinessTemplateProvisionResponse {

    private String templateCode;
    private Integer templateVersion;
    private LocalDateTime provisionedAt;

    private Long tenantId;
    private String tenantNome;
    private String tenantSlug;
    private String tenantCode;

    private String planoCodigo;
    private Long subscricaoId;

    private Long instituicaoId;
    private Long unidadeAtendimentoId;

    private Long ownerUserId;
    private String ownerEmail;
    private String ownerTelefone;

    private QrProvisionado qrPrincipal;
    private List<CategoriaProvisionada> categorias;
    private List<MesaProvisionada> mesas;
    private List<UnidadeProducaoProvisionada> unidadesProducao;
    private List<RotaProducaoProvisionada> rotasProducao;
    private List<DispositivoProvisionado> dispositivos;
    private List<ChecklistProvisionado> checklists;

    private BusinessTemplatePreviewResponse.TemplatePoliciesPreview politicasAplicadas;

    private List<String> mensagens;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class QrProvisionado {
        private Long qrCodeId;
        private String qrToken;
        private String qrUrlPublica;
        private String tipo;
        private String nome;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CategoriaProvisionada {
        private Long categoriaId;
        private String nome;
        private String slug;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MesaProvisionada {
        private Long mesaId;
        private Integer numero;
        private String referencia;
        private Long qrCodeId;
        private String qrToken;
        private String qrUrlPublica;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UnidadeProducaoProvisionada {
        private Long unidadeProducaoId;
        private String codigo;
        private String nome;
        private String tipo;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RotaProducaoProvisionada {
        private Long rotaId;
        private Long categoriaId;
        private String categoriaSlug;
        private Long unidadeProducaoId;
        private String unidadeProducaoCodigo;
        private Integer prioridade;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DispositivoProvisionado {
        private Long dispositivoId;
        private String codigo;
        private String nome;
        private String tipo;
        private String operationalDeviceType;
        private String status;
        private Long unidadeProducaoId;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ChecklistProvisionado {
        private Long templateId;
        private String tipo;
        private String nome;
        private Integer totalItens;
    }
}


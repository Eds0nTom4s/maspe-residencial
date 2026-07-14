package com.restaurante.businesstemplate.dto;

import com.restaurante.model.enums.TenantTipo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request genérico para provisionamento por Business Template.
 *
 * Observação:
 * - Validações específicas por template são aplicadas no service/template.
 * - Não aceita tenantId externo (apenas slug/tenantCode).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BusinessTemplateProvisionRequest {

    /**
     * Plano usado para validar limites.
     * Default recomendado: PILOTO (pode ser omitido).
     */
    private String planoCodigo;

    /**
     * Conta empresarial opcional para agrupar o tenant provisionado.
     * Quando ausente, o fluxo legado é preservado.
     */
    private Long businessAccountId;

    @Valid
    @NotNull
    private TenantInfo tenant;

    @Valid
    @NotNull
    private OwnerInfo owner;

    @Valid
    private LocalizacaoInfo localizacao;

    @Valid
    private PontoOptions ponto;

    @Valid
    private RestOptions rest;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TenantInfo {
        @NotBlank
        private String nomeNegocio;
        @NotBlank
        private String slug;
        /**
         * Opcional; quando ausente, será gerado automaticamente.
         */
        private String tenantCode;
        @NotNull
        private TenantTipo tipo;
        private String nif;
        private String telefone;
        @Email
        private String email;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class OwnerInfo {
        /**
         * Associação inequívoca usada apenas pelo contrato canónico. Quando
         * presente, impede qualquer reutilização implícita por email/telefone.
         */
        private Long existingUserId;
        @NotBlank
        private String nome;
        private String telefone;
        @Email
        private String email;
        /**
         * Opcional. Se vazio, será gerada internamente e NÃO retornada por API.
         */
        private String senhaTemporaria;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class LocalizacaoInfo {
        private String endereco;
        private String provincia;
        private String municipio;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PontoOptions {
        /**
         * Entrega manual feita pelo próprio tenant.
         */
        @Builder.Default
        private Boolean entregaManual = Boolean.FALSE;
        /**
         * Permite levantamento (pickup).
         */
        @Builder.Default
        private Boolean allowPickup = Boolean.TRUE;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RestOptions {
        @Builder.Default
        private Boolean temMesas = Boolean.FALSE;
        @PositiveOrZero
        private Integer quantidadeMesas;
        @Builder.Default
        private Boolean gerarQrPorMesa = Boolean.TRUE;
        @Builder.Default
        private Boolean temBarSeparado = Boolean.FALSE;
        /**
         * Configura política padrão (template) de disciplina.
         */
        @Builder.Default
        private Boolean exigeTurnoAberto = Boolean.TRUE;
        /**
         * NONE, MANUAL, CONSUMA_NETWORK (simplificado nesta fase).
         */
        @Builder.Default
        private RestDeliveryOption entrega = RestDeliveryOption.NONE;
    }

    public enum RestDeliveryOption {
        NONE,
        MANUAL,
        CONSUMA_NETWORK
    }
}

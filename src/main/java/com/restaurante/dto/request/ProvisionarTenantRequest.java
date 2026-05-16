package com.restaurante.dto.request;

import com.restaurante.model.enums.TenantTipo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProvisionarTenantRequest {

    @Valid
    @NotNull
    private TenantInfo tenant;

    @NotBlank
    private String planoCodigo;

    @NotBlank
    private String templateCodigo;

    @Valid
    @NotNull
    private InstituicaoInfo instituicao;

    @Valid
    private ResponsavelInfo responsavel;

    @Valid
    private OpcoesProvisionamento opcoes;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TenantInfo {
        @NotBlank private String nome;
        @NotBlank private String slug;
        private String tenantCode;
        private String nif;
        private String telefone;
        private String email;
        @NotNull private TenantTipo tipo;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class InstituicaoInfo {
        @NotBlank private String nome;
        private String sigla;
        private String nif;
        private String telefone;
        private String email;
        private String endereco;
        private String provincia;
        private String municipio;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ResponsavelInfo {
        private String nome;
        private String email;
        private String telefone;
        private String senhaTemporaria;
        @Builder.Default
        private Boolean criarUsuario = true;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class OpcoesProvisionamento {
        @Builder.Default
        private Boolean criarQrPrincipal = true;
        @Builder.Default
        private Boolean criarCategoriaDefault = true;
        @Builder.Default
        private Boolean criarUnidadeAtendimentoDefault = true;
        @Builder.Default
        private Boolean ativarTenant = true;
    }
}


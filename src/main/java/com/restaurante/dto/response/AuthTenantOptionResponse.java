package com.restaurante.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Opção de tenant apresentada ao usuário autenticado durante a seleção de tenant.
 * Não expõe secrets, configurações internas, nem dados de outros usuários.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthTenantOptionResponse {

    /** ID interno do tenant (necessário para POST /auth/tenant/select). */
    private Long tenantId;

    /** Código curto do tenant (ex: CONSUMA_DEMO). */
    private String tenantCode;

    /** Slug URL-friendly (ex: consuma-demo). */
    private String slug;

    /** Nome exibível do tenant. */
    private String nome;

    /** Estado do tenant: ATIVO, SUSPENSO, etc. */
    private String estado;

    /** Indica se o tenant está ativo para operação. */
    private Boolean ativo;

    /** Roles do usuário neste tenant (ex: ["TENANT_ADMIN"]). */
    private List<String> roles;

    /** Template operacional usado no provisionamento, se disponível. */
    private String templateCode;

    /** Plano comercial ativo, se disponível. */
    private String planoCodigo;

    /**
     * Marcador legacy de primeira opção. Não representa propriedade do Tenant
     * nem Owner empresarial; exactamente a primeira opção elegível por tenantId
     * recebe true para manter compatibilidade determinística.
     */
    private Boolean principal;
}

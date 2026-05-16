package com.restaurante.service.provisioning;

/**
 * Plano calculado do provisionamento (sem persistência).
 */
public record ProvisioningPlan(
        String slugNormalized,
        String tenantCodeNormalized,
        String tenantCodeGenerated,
        boolean slugAlreadyExists,
        boolean tenantCodeAlreadyExists,
        boolean criarUnidadeAtendimentoDefault,
        boolean criarQrPrincipal,
        boolean criarCategoriaDefault,
        boolean ativarTenant,
        boolean criarMesas,
        int quantidadeMesas,
        boolean criarQrPorMesa,
        String prefixoMesa,
        boolean criarUsuarioOwner,
        int unidadesNovas,
        int usuariosNovos,
        int tenantUsersNovos,
        int mesasNovas,
        int qrNovos,
        String qrPrincipalTipo,
        String unidadeAtendimentoDefaultNome,
        String unidadeAtendimentoDefaultTipo
) {
}


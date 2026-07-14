package com.restaurante.financeiro.reconciliation.model;

public enum ReconciliationCaseStatus {
    ABERTO, EM_ANALISE, AGUARDANDO_CORRECCAO_DOMINIO,
    AGUARDANDO_ACCAO_FINANCEIRA_EXTERNA, PRONTO_PARA_NOVA_TENTATIVA,
    RESOLVIDO, ENCERRADO_SEM_CONVERGENCIA;

    public boolean terminal() {
        return this == RESOLVIDO || this == ENCERRADO_SEM_CONVERGENCIA;
    }
}

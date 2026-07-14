package com.restaurante.financeiro.reconciliation.service;

import com.restaurante.exception.ConflictException;
import com.restaurante.financeiro.reconciliation.model.ReconciliationCaseStatus;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class ReconciliationCaseStateMachine {
    private static final Map<ReconciliationCaseStatus,Set<ReconciliationCaseStatus>> ALLOWED=Map.of(
        ReconciliationCaseStatus.ABERTO, EnumSet.of(ReconciliationCaseStatus.EM_ANALISE,ReconciliationCaseStatus.AGUARDANDO_CORRECCAO_DOMINIO,ReconciliationCaseStatus.AGUARDANDO_ACCAO_FINANCEIRA_EXTERNA),
        ReconciliationCaseStatus.EM_ANALISE, EnumSet.of(ReconciliationCaseStatus.AGUARDANDO_CORRECCAO_DOMINIO,ReconciliationCaseStatus.AGUARDANDO_ACCAO_FINANCEIRA_EXTERNA,ReconciliationCaseStatus.PRONTO_PARA_NOVA_TENTATIVA,ReconciliationCaseStatus.ENCERRADO_SEM_CONVERGENCIA),
        ReconciliationCaseStatus.AGUARDANDO_CORRECCAO_DOMINIO, EnumSet.of(ReconciliationCaseStatus.EM_ANALISE,ReconciliationCaseStatus.PRONTO_PARA_NOVA_TENTATIVA,ReconciliationCaseStatus.ENCERRADO_SEM_CONVERGENCIA),
        ReconciliationCaseStatus.AGUARDANDO_ACCAO_FINANCEIRA_EXTERNA, EnumSet.of(ReconciliationCaseStatus.EM_ANALISE,ReconciliationCaseStatus.ENCERRADO_SEM_CONVERGENCIA),
        ReconciliationCaseStatus.PRONTO_PARA_NOVA_TENTATIVA, EnumSet.of(ReconciliationCaseStatus.EM_ANALISE,ReconciliationCaseStatus.AGUARDANDO_CORRECCAO_DOMINIO,ReconciliationCaseStatus.AGUARDANDO_ACCAO_FINANCEIRA_EXTERNA,ReconciliationCaseStatus.RESOLVIDO),
        ReconciliationCaseStatus.RESOLVIDO, Set.of(), ReconciliationCaseStatus.ENCERRADO_SEM_CONVERGENCIA, Set.of());
    public boolean canTransition(ReconciliationCaseStatus current, ReconciliationCaseStatus target){
        return current != target && ALLOWED.getOrDefault(current,Set.of()).contains(target);
    }
    public void transition(com.restaurante.financeiro.reconciliation.model.PagamentoReconciliationCase c,ReconciliationCaseStatus target){
        if(c.getStatus()==target)return;
        if(!canTransition(c.getStatus(),target))throw new ConflictException("Transição administrativa inválida: "+c.getStatus()+" -> "+target);
        c.setStatus(target);
    }
}

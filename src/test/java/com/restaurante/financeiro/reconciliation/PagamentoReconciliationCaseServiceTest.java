package com.restaurante.financeiro.reconciliation;

import com.restaurante.exception.ConflictException;
import com.restaurante.financeiro.enums.*;
import com.restaurante.financeiro.reconciliation.dto.ReconciliationCaseContracts.*;
import com.restaurante.financeiro.reconciliation.model.*;
import com.restaurante.financeiro.reconciliation.repository.*;
import com.restaurante.financeiro.reconciliation.service.*;
import com.restaurante.financeiro.repository.PagamentoGatewayRepository;
import com.restaurante.model.entity.*;
import com.restaurante.model.enums.TenantUserRole;
import com.restaurante.repository.*;
import com.restaurante.security.tenant.TenantGuard;
import com.restaurante.security.tenant.*;
import com.restaurante.service.PedidoPagamentoPolicy;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PagamentoReconciliationCaseServiceTest {
    @Mock PagamentoReconciliationCaseRepository cases;
    @Mock PagamentoReconciliationCaseEventRepository events;
    @Mock PagamentoGatewayRepository payments;
    @Mock UserRepository users;
    @Mock TenantUserRepository tenantUsers;
    @Mock TenantGuard tenantGuard;
    @Mock PedidoPagamentoPolicy paymentPolicy;
    @Mock ReconciliationAuditContext auditContext;
    @Spy ReconciliationCommandFingerprint fingerprints=new ReconciliationCommandFingerprint();
    @Spy ReconciliationCaseStateMachine stateMachine=new ReconciliationCaseStateMachine();
    @Mock ReconciliationMaterializationAuditRepository materializationAudits;
    @Mock ReconciliationMaterializationLock materializationLock;
    @Mock HttpServletRequest http;
    @InjectMocks PagamentoReconciliationCaseService service;
    Pagamento payment; PagamentoReconciliationCase adminCase; Tenant tenant;

    @BeforeEach void setup(){
        tenant=new Tenant();tenant.setId(10L);tenant.setTenantCode("T10");
        Pedido pedido=new Pedido();pedido.setId(42L);
        payment=new Pagamento();payment.setId(8L);payment.setTenant(tenant);payment.setPedido(pedido);payment.setAmount(new BigDecimal("100.00"));payment.setExternalReference("REF00000000008");payment.setStatus(StatusPagamentoGateway.PENDENTE);payment.setReconciliationStatus(StatusReconciliacaoAppyPay.BLOQUEADO_DOMINIO);payment.setReconciliationLastRemoteStatus("CONFIRMED");payment.setReconciliationLastResponseHash("a".repeat(64));
        adminCase=new PagamentoReconciliationCase();adminCase.setId(5L);adminCase.setVersion(0L);adminCase.setTenant(tenant);adminCase.setPagamento(payment);adminCase.setPedido(pedido);adminCase.setStatus(ReconciliationCaseStatus.EM_ANALISE);adminCase.setOpenedAt(java.time.LocalDateTime.now());adminCase.setActive(true);
        lenient().when(events.findByReconciliationCaseIdOrderByCreatedAtAsc(anyLong())).thenReturn(List.of());
        lenient().when(events.findByReconciliationCaseIdAndActionAndIdempotencyKey(anyLong(),any(),anyString())).thenReturn(Optional.empty());
        lenient().when(auditContext.current(http)).thenReturn(new ReconciliationAuditContext.Actor(7L,"TENANT_FINANCE","TENANT","127.0.0.1","test","corr-1"));
        lenient().when(cases.save(any())).thenAnswer(i->i.getArgument(0));lenient().when(events.save(any())).thenAnswer(i->i.getArgument(0));
    }
    @AfterEach void cleanup(){TenantContextHolder.clear();}

    @Test void platformVeNotaRestritaETenantNaoRecebeNenhumDadoDela(){
        PagamentoReconciliationCaseEvent restricted=new PagamentoReconciliationCaseEvent();restricted.setId(99L);restricted.setAction(ReconciliationCaseAction.NOTE_ADDED);restricted.setNoteVisibility(ReconciliationNoteVisibility.PLATFORM_ONLY);restricted.setReason("segredo operacional");restricted.setActorRoles("ROLE_ADMIN");restricted.setActorOrigin("PLATFORM_ADMIN");restricted.setCorrelationId("private-corr");
        PagamentoReconciliationCaseEvent visible=new PagamentoReconciliationCaseEvent();visible.setId(100L);visible.setAction(ReconciliationCaseAction.NOTE_ADDED);visible.setNoteVisibility(ReconciliationNoteVisibility.TENANT);visible.setReason("nota tenant");visible.setActorRoles("TENANT_OWNER");visible.setActorOrigin("TENANT");visible.setCorrelationId("tenant-corr");
        when(events.findByReconciliationCaseIdOrderByCreatedAtAsc(5L)).thenReturn(List.of(restricted,visible));when(cases.findTenantCase(5L,10L)).thenReturn(Optional.of(adminCase));
        assertThat(service.detail(5L,10L,true).administrativeHistory()).extracting(Event::reason).containsExactly("segredo operacional","nota tenant");
        TenantContext ctx=new TenantContext(10L,"T10",7L,Set.of("TENANT_OWNER"),TenantResolutionSource.JWT,false,false);TenantContextHolder.set(ctx);when(tenantGuard.requireContext()).thenReturn(ctx);
        Detail tenantDetail=service.detail(5L,null,false);assertThat(tenantDetail.administrativeHistory()).extracting(Event::reason).containsExactly("nota tenant");assertThat(tenantDetail.toString()).doesNotContain("segredo operacional","ROLE_ADMIN","private-corr");
    }

    @Test void mesmaChaveComPayloadDiferenteConflitaEmTodosComandos(){
        authorizeCase();
        assertConflict(ReconciliationCaseAction.ASSIGNED,fingerprints.calculate(5L,ReconciliationCaseAction.ASSIGNED,Map.of("version",0L,"assignedToUserId",9L,"reason","original")),()->service.assign(5L,10L,new AssignRequest(0L,10L,"alterado"),"same",http,true));
        assertConflict(ReconciliationCaseAction.NOTE_ADDED,fingerprints.calculate(5L,ReconciliationCaseAction.NOTE_ADDED,Map.of("version",0L,"content","original","noteType",ReconciliationNoteType.ANALYSIS,"noteVisibility",ReconciliationNoteVisibility.TENANT)),()->service.note(5L,10L,new NoteRequest(0L,"alterado",ReconciliationNoteType.ANALYSIS,ReconciliationNoteVisibility.TENANT),"same",http,true));
        assertConflict(ReconciliationCaseAction.CLASSIFIED,fingerprints.calculate(5L,ReconciliationCaseAction.CLASSIFIED,Map.of("version",0L,"classification",ReconciliationCaseClassification.DADO_LEGADO,"reason","original")),()->service.classify(5L,10L,new ClassifyRequest(0L,ReconciliationCaseClassification.OUTRO,"alterado"),"same",http,true));
        assertConflict(ReconciliationCaseAction.RETRY_REQUESTED,fingerprints.calculate(5L,ReconciliationCaseAction.RETRY_REQUESTED,Map.of("version",0L,"reason","original")),()->service.retry(5L,10L,new CommandContext(0L,"alterado"),"same",http,true));
        assertConflict(ReconciliationCaseAction.CLOSED,fingerprints.calculate(5L,ReconciliationCaseAction.CLOSED,Map.of("version",0L,"resolution","ACCOUNTING","reason","original")),()->service.close(5L,10L,new CloseRequest(0L,"ACCOUNTING","alterado"),"same",http,true));
    }

    @Test void mesmaChaveEMesmoPayloadEhReplayValido(){
        authorizeCase();NoteRequest req=new NoteRequest(0L,"conteúdo normalizado",ReconciliationNoteType.EVIDENCE,ReconciliationNoteVisibility.TENANT);String fp=fingerprints.calculate(5L,ReconciliationCaseAction.NOTE_ADDED,Map.of("version",0L,"content",req.content(),"noteType",req.type(),"noteVisibility",req.visibility()));PagamentoReconciliationCaseEvent e=new PagamentoReconciliationCaseEvent();e.setCommandFingerprint(fp);when(events.findByReconciliationCaseIdAndActionAndIdempotencyKey(5L,ReconciliationCaseAction.NOTE_ADDED,"replay")).thenReturn(Optional.of(e));service.note(5L,10L,req,"replay",http,true);verify(events,never()).save(argThat(x->x.getAction()==ReconciliationCaseAction.NOTE_ADDED));
    }

    @Test void responsavelCashierMesmoActivoEhRecusado(){
        authorizeCase();User cashier=new User();cashier.setId(30L);TenantUser membership=new TenantUser();membership.setRole(com.restaurante.model.enums.TenantUserRole.TENANT_CASHIER);membership.setEstado(com.restaurante.model.enums.TenantUserEstado.ATIVO);when(users.findById(30L)).thenReturn(Optional.of(cashier));when(tenantUsers.findByTenantIdAndUserIdAndEstado(10L,30L,com.restaurante.model.enums.TenantUserEstado.ATIVO)).thenReturn(Optional.of(membership));
        assertThatThrownBy(()->service.assign(5L,10L,new AssignRequest(0L,30L,"atribuir"),"assign-cashier",http,true)).isInstanceOf(com.restaurante.exception.BusinessException.class).hasMessageContaining("role financeira");
    }

    @Test void allowedActionsRespeitamActorEstadoEBlockers(){
        when(cases.findTenantCase(5L,10L)).thenReturn(Optional.of(adminCase));
        TenantContext cashier=new TenantContext(10L,"T10",30L,Set.of("TENANT_CASHIER"),TenantResolutionSource.JWT,false,false);
        TenantContextHolder.set(cashier);when(tenantGuard.requireContext()).thenReturn(cashier);
        assertThat(service.eligibility(5L,null,false).allowedActions()).isEmpty();

        TenantContext finance=new TenantContext(10L,"T10",31L,Set.of("TENANT_FINANCE"),TenantResolutionSource.JWT,false,false);
        TenantContextHolder.set(finance);when(tenantGuard.requireContext()).thenReturn(finance);
        when(tenantGuard.hasAnyTenantRole(any(TenantUserRole[].class))).thenReturn(true);
        assertThat(service.eligibility(5L,null,false).allowedActions()).containsExactly("NOTE","ASSIGN","CLASSIFY","RETRY","CLOSE");

        adminCase.setStatus(ReconciliationCaseStatus.RESOLVIDO);adminCase.setActive(false);
        assertThat(service.eligibility(5L,null,false).allowedActions()).isEmpty();
    }

    @Test void materializacaoEhIdempotente(){
        when(cases.findByPagamentoIdAndActiveTrue(8L)).thenReturn(Optional.empty(),Optional.of(adminCase));
        PagamentoReconciliationCase first=service.materialize(payment,ReconciliationCaseOrigin.LEGACY_BACKFILL);
        PagamentoReconciliationCase second=service.materialize(payment,ReconciliationCaseOrigin.LEGACY_BACKFILL);
        assertThat(first.getOrigin()).isEqualTo(ReconciliationCaseOrigin.LEGACY_BACKFILL);assertThat(second).isSameAs(adminCase);
        verify(cases,times(1)).save(any());verify(events,times(1)).save(any());
    }

    @Test void retryInvalidoNaoAlteraPagamento(){
        doThrow(new IllegalStateException("pedido não aceite")).when(paymentPolicy).assertPodeConfirmarPagamento(any(),any());
        authorizeCase();
        assertThatThrownBy(()->service.retry(5L,10L,new CommandContext(0L,"reavaliar após correcção"),"retry-1",http,true)).isInstanceOf(ConflictException.class).hasMessageContaining("DOMAIN_POLICY");
        assertThat(payment.getStatus()).isEqualTo(StatusPagamentoGateway.PENDENTE);assertThat(payment.getReconciliationStatus()).isEqualTo(StatusReconciliacaoAppyPay.BLOQUEADO_DOMINIO);assertThat(payment.getConfirmedAt()).isNull();verify(payments,never()).save(any());
    }

    @Test void retryElegivelApenasAgendaENaoConfirma(){
        authorizeCase();when(payments.findForUpdateById(8L)).thenReturn(Optional.of(payment));
        Detail result=service.retry(5L,10L,new CommandContext(0L,"domínio corrigido e validado"),"retry-2",http,true);
        assertThat(payment.getReconciliationStatus()).isEqualTo(StatusReconciliacaoAppyPay.ELIGIVEL);assertThat(payment.getStatus()).isEqualTo(StatusPagamentoGateway.PENDENTE);assertThat(payment.getConfirmedAt()).isNull();assertThat(result.summary().status()).isEqualTo(ReconciliationCaseStatus.PRONTO_PARA_NOVA_TENTATIVA);assertThat(adminCase.isActive()).isTrue();verify(payments).findForUpdateById(8L);
        assertThat(result.eligibility().localReference()).isEqualTo("RE***08");
    }

    @Test void closeAdministrativoPreservaVerdadeFinanceira(){
        authorizeCase();service.close(5L,10L,new CloseRequest(0L,"ACCOUNTING","encaminhado formalmente à contabilidade"),"close-1",http,true);
        assertThat(adminCase.getStatus()).isEqualTo(ReconciliationCaseStatus.ENCERRADO_SEM_CONVERGENCIA);assertThat(payment.getStatus()).isEqualTo(StatusPagamentoGateway.PENDENTE);assertThat(payment.getConfirmedAt()).isNull();assertThat(payment.getReconciliationStatus()).isEqualTo(StatusReconciliacaoAppyPay.BLOQUEADO_DOMINIO);verifyNoInteractions(payments);
    }

    private void authorizeCase(){when(cases.findTenantCaseForUpdate(5L,10L)).thenReturn(Optional.of(adminCase));}
    private void assertConflict(ReconciliationCaseAction action,String stored,org.assertj.core.api.ThrowableAssert.ThrowingCallable call){PagamentoReconciliationCaseEvent e=new PagamentoReconciliationCaseEvent();e.setCommandFingerprint(stored);when(events.findByReconciliationCaseIdAndActionAndIdempotencyKey(5L,action,"same")).thenReturn(Optional.of(e));assertThatThrownBy(call).isInstanceOf(ConflictException.class).hasMessageContaining("IDEMPOTENCY_CONFLICT");}
}

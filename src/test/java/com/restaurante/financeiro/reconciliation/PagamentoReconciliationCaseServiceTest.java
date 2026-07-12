package com.restaurante.financeiro.reconciliation;

import com.restaurante.exception.ConflictException;
import com.restaurante.financeiro.enums.*;
import com.restaurante.financeiro.reconciliation.dto.ReconciliationCaseContracts.*;
import com.restaurante.financeiro.reconciliation.model.*;
import com.restaurante.financeiro.reconciliation.repository.*;
import com.restaurante.financeiro.reconciliation.service.*;
import com.restaurante.financeiro.repository.PagamentoGatewayRepository;
import com.restaurante.model.entity.*;
import com.restaurante.repository.*;
import com.restaurante.security.tenant.TenantGuard;
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

    @Test void materializacaoEhIdempotente(){
        when(cases.findByPagamentoIdAndActiveTrue(8L)).thenReturn(Optional.empty(),Optional.of(adminCase));
        PagamentoReconciliationCase first=service.materialize(payment,ReconciliationCaseOrigin.LEGACY_BACKFILL);
        PagamentoReconciliationCase second=service.materialize(payment,ReconciliationCaseOrigin.LEGACY_BACKFILL);
        assertThat(first.getOrigin()).isEqualTo(ReconciliationCaseOrigin.LEGACY_BACKFILL);assertThat(second).isSameAs(adminCase);
        verify(cases,times(1)).save(any());verify(events,times(1)).save(any());
    }

    @Test void retryInvalidoNaoAlteraPagamento(){
        doThrow(new IllegalStateException("pedido não aceite")).when(paymentPolicy).assertPodeConfirmarPagamento(any(),any());
        authorizeCase();when(payments.findForUpdateById(8L)).thenReturn(Optional.of(payment));
        assertThatThrownBy(()->service.retry(5L,10L,new CommandContext(0L,"reavaliar após correcção"),"retry-1",http,true)).isInstanceOf(ConflictException.class).hasMessageContaining("DOMAIN_POLICY");
        assertThat(payment.getStatus()).isEqualTo(StatusPagamentoGateway.PENDENTE);assertThat(payment.getReconciliationStatus()).isEqualTo(StatusReconciliacaoAppyPay.BLOQUEADO_DOMINIO);assertThat(payment.getConfirmedAt()).isNull();verify(payments,never()).save(any());
    }

    @Test void retryElegivelApenasAgendaENaoConfirma(){
        authorizeCase();when(payments.findForUpdateById(8L)).thenReturn(Optional.of(payment));
        Detail result=service.retry(5L,10L,new CommandContext(0L,"domínio corrigido e validado"),"retry-2",http,true);
        assertThat(payment.getReconciliationStatus()).isEqualTo(StatusReconciliacaoAppyPay.ELIGIVEL);assertThat(payment.getStatus()).isEqualTo(StatusPagamentoGateway.PENDENTE);assertThat(payment.getConfirmedAt()).isNull();assertThat(result.summary().status()).isEqualTo(ReconciliationCaseStatus.RESOLVIDO);verify(payments).findForUpdateById(8L);
        assertThat(result.eligibility().localReference()).isEqualTo("RE***08");
    }

    @Test void closeAdministrativoPreservaVerdadeFinanceira(){
        authorizeCase();service.close(5L,10L,new CloseRequest(0L,"ACCOUNTING","encaminhado formalmente à contabilidade"),"close-1",http,true);
        assertThat(adminCase.getStatus()).isEqualTo(ReconciliationCaseStatus.ENCERRADO_SEM_CONVERGENCIA);assertThat(payment.getStatus()).isEqualTo(StatusPagamentoGateway.PENDENTE);assertThat(payment.getConfirmedAt()).isNull();assertThat(payment.getReconciliationStatus()).isEqualTo(StatusReconciliacaoAppyPay.BLOQUEADO_DOMINIO);verifyNoInteractions(payments);
    }

    private void authorizeCase(){when(cases.findTenantCase(5L,10L)).thenReturn(Optional.of(adminCase));}
}

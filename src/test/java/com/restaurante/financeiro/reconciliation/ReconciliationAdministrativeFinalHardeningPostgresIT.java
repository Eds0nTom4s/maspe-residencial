package com.restaurante.financeiro.reconciliation;

import com.restaurante.exception.ConflictException;
import com.restaurante.financeiro.enums.StatusPagamentoGateway;
import com.restaurante.financeiro.enums.StatusReconciliacaoAppyPay;
import com.restaurante.financeiro.enums.TipoPagamentoFinanceiro;
import com.restaurante.financeiro.reconciliation.dto.ReconciliationCaseContracts.*;
import com.restaurante.financeiro.reconciliation.model.*;
import com.restaurante.financeiro.reconciliation.repository.*;
import com.restaurante.financeiro.reconciliation.service.PagamentoReconciliationCaseService;
import com.restaurante.financeiro.repository.PagamentoGatewayRepository;
import com.restaurante.financeiro.service.AppyPayReconciliationProcessor;
import com.restaurante.model.entity.*;
import com.restaurante.model.enums.*;
import com.restaurante.repository.*;
import com.restaurante.security.tenant.*;
import com.restaurante.testsupport.PostgresTestcontainersConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.MOCK,properties="spring.main.web-application-type=servlet")
@AutoConfigureMockMvc(addFilters=false)
@ActiveProfiles("it-postgres")
class ReconciliationAdministrativeFinalHardeningPostgresIT extends PostgresTestcontainersConfig {
    @Autowired MockMvc mvc;
    @Autowired TenantRepository tenants;
    @Autowired UserRepository users;
    @Autowired PedidoRepository pedidos;
    @Autowired PagamentoGatewayRepository payments;
    @Autowired PagamentoReconciliationCaseRepository cases;
    @Autowired PagamentoReconciliationCaseEventRepository events;
    @Autowired PagamentoReconciliationCaseService service;
    @Autowired AppyPayReconciliationProcessor processor;
    @Autowired JdbcTemplate jdbc;

    @AfterEach void clear(){TenantContextHolder.clear();}

    @Test @WithMockUser(username="reconciliation-reader")
    void httpProtegeNotasTenantCashierVersaoEOutroTenant() throws Exception {
        Fixture f=fixture(true);
        addNote(f,"segredo platform",ReconciliationNoteVisibility.PLATFORM_ONLY);
        addNote(f,"nota tenant",ReconciliationNoteVisibility.TENANT);

        tenantContext(f,TenantUserRole.TENANT_FINANCE);
        String tenantBody=mvc.perform(get("/tenant/financeiro/reconciliation-cases/{id}",f.adminCase().getId()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(tenantBody).contains("nota tenant").doesNotContain("segredo platform");

        platformContext(f);
        String platformBody=mvc.perform(get("/platform/financeiro/reconciliation-cases/{id}",f.adminCase().getId())
                        .param("tenantId",f.tenant().getId().toString()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(platformBody).contains("nota tenant","segredo platform");

        TenantContextHolder.set(new TenantContext(f.otherTenant().getId(),f.otherTenant().getTenantCode(),f.finance().getId(),Set.of(TenantUserRole.TENANT_FINANCE.name()),TenantResolutionSource.JWT,false,false));
        mvc.perform(get("/tenant/financeiro/reconciliation-cases/{id}",f.adminCase().getId())).andExpect(status().isNotFound());

        tenantContext(f,TenantUserRole.TENANT_CASHIER);
        String eligibility=mvc.perform(get("/tenant/financeiro/reconciliation-cases/{id}/eligibility",f.adminCase().getId()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(eligibility).contains("\"allowedActions\":[]");
        for(CommandHttp command:commands(f.adminCase().getVersion())){
            mvc.perform(post("/tenant/financeiro/reconciliation-cases/{id}/"+command.path(),f.adminCase().getId())
                            .header("Idempotency-Key","cashier-"+command.path()).contentType(MediaType.APPLICATION_JSON).content(command.body()))
                    .andExpect(status().isForbidden());
        }

        tenantContext(f,TenantUserRole.TENANT_FINANCE);
        for(CommandHttp command:commands(null)){
            mvc.perform(post("/tenant/financeiro/reconciliation-cases/{id}/"+command.path(),f.adminCase().getId())
                            .header("Idempotency-Key","missing-"+command.path()).contentType(MediaType.APPLICATION_JSON).content(command.body()))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(post("/tenant/financeiro/reconciliation-cases/{id}/notes",f.adminCase().getId())
                        .header("Idempotency-Key","stale-http").contentType(MediaType.APPLICATION_JSON)
                        .content(noteBody(f.adminCase().getVersion()+99,"stale")))
                .andExpect(status().isConflict());
    }

    @Test void allowedActionsSaoAutoritativasParaFinancePlatformETerminal(){
        Fixture f=fixture(true);
        tenantContext(f,TenantUserRole.TENANT_FINANCE);
        assertThat(service.eligibility(f.adminCase().getId(),null,false).allowedActions())
                .containsExactly("NOTE","ASSIGN","CLASSIFY","RETRY","CLOSE");
        platformContext(f);
        assertThat(service.eligibility(f.adminCase().getId(),f.tenant().getId(),true).allowedActions())
                .containsExactly("NOTE","ASSIGN","CLASSIFY","RETRY","CLOSE");
        PagamentoReconciliationCase terminal=cases.findById(f.adminCase().getId()).orElseThrow();
        terminal.setStatus(ReconciliationCaseStatus.RESOLVIDO);terminal.setActive(false);cases.saveAndFlush(terminal);
        assertThat(service.eligibility(terminal.getId(),f.tenant().getId(),true).allowedActions()).isEmpty();
    }

    @Test void replayConcorrenteExecutaUmaVezEDivergenciaConflita() throws Exception {
        Fixture f=fixture(true);long version=f.adminCase().getVersion();String key="note-concurrent-"+UUID.randomUUID();
        Callable<Detail> call=()->withPlatform(f,()->service.note(f.adminCase().getId(),f.tenant().getId(),new NoteRequest(version,"mesmo payload",ReconciliationNoteType.ANALYSIS,ReconciliationNoteVisibility.TENANT),key,request(),true));
        List<Detail> results=runTogether(call,call);
        assertThat(results).hasSize(2).doesNotContainNull();
        Integer count=jdbc.queryForObject("select count(*) from pagamento_reconciliation_case_events where case_id=? and action='NOTE_ADDED' and idempotency_key=?",Integer.class,f.adminCase().getId(),key);
        assertThat(count).isOne();
        platformContext(f);
        assertThatThrownBy(()->service.note(f.adminCase().getId(),f.tenant().getId(),new NoteRequest(version,"payload divergente",ReconciliationNoteType.ANALYSIS,ReconciliationNoteVisibility.TENANT),key,request(),true))
                .isInstanceOf(ConflictException.class).hasMessageContaining("IDEMPOTENCY_CONFLICT");
    }

    @Test void materializacaoConcorrenteEhAtomicaETenantScoped() throws Exception {
        Fixture a=fixture(false);Fixture b=fixture(false);
        platformContext(a);
        BackfillResult dry=service.backfill(a.tenant().getId(),true,null,null,request());
        assertThat(dry.eligible()).isEqualTo(1);assertThat(dry.created()).isZero();
        String key="materialize-concurrent-"+UUID.randomUUID();MaterializeRequest command=new MaterializeRequest("backfill governado");
        Callable<BackfillResult> call=()->withPlatform(a,()->service.backfill(a.tenant().getId(),false,command,key,request()));
        runTogether(call,call);
        assertThat(cases.countByPagamentoIdAndActiveTrue(a.payment().getId())).isOne();
        assertThat(cases.countByPagamentoIdAndActiveTrue(b.payment().getId())).isZero();
        Integer audits=jdbc.queryForObject("select count(*) from reconciliation_materialization_audits where idempotency_key=?",Integer.class,key);
        assertThat(audits).isOne();
        platformContext(a);
        assertThatThrownBy(()->service.backfill(a.tenant().getId(),false,new MaterializeRequest("payload divergente"),key,request()))
                .isInstanceOf(ConflictException.class).hasMessageContaining("IDEMPOTENCY_CONFLICT");
    }

    @Test void retryProcessorResolveERebloqueioPersistemEstados(){
        Fixture resolved=fixture(true);platformContext(resolved);
        Detail retry=service.retry(resolved.adminCase().getId(),resolved.tenant().getId(),new CommandContext(resolved.adminCase().getVersion(),"domínio corrigido"),"retry-"+UUID.randomUUID(),request(),true);
        assertThat(retry.summary().status()).isEqualTo(ReconciliationCaseStatus.PRONTO_PARA_NOVA_TENTATIVA);
        assertThat(retry.summary().reconciliationStatus()).isEqualTo(StatusReconciliacaoAppyPay.ELIGIVEL.name());
        assertThat(cases.findById(resolved.adminCase().getId()).orElseThrow().isActive()).isTrue();
        processor.processar(resolved.payment().getId(),StatusPagamentoGateway.CONFIRMADO,"CONFIRMED","{}","b".repeat(64));
        PagamentoReconciliationCase done=cases.findById(resolved.adminCase().getId()).orElseThrow();
        assertThat(done.getStatus()).isEqualTo(ReconciliationCaseStatus.RESOLVIDO);assertThat(done.isActive()).isFalse();

        Fixture blocked=fixture(true);platformContext(blocked);
        service.retry(blocked.adminCase().getId(),blocked.tenant().getId(),new CommandContext(blocked.adminCase().getVersion(),"agendar"),"retry-"+UUID.randomUUID(),request(),true);
        Pedido pedido=pedidos.findById(blocked.pedido().getId()).orElseThrow();pedido.setStatus(StatusPedido.CRIADO);pedidos.saveAndFlush(pedido);
        processor.processar(blocked.payment().getId(),StatusPagamentoGateway.CONFIRMADO,"CONFIRMED","{}","c".repeat(64));
        PagamentoReconciliationCase reblocked=cases.findById(blocked.adminCase().getId()).orElseThrow();
        assertThat(reblocked.getStatus()).isEqualTo(ReconciliationCaseStatus.AGUARDANDO_CORRECCAO_DOMINIO);assertThat(reblocked.isActive()).isTrue();
    }

    @Test void closeNaoAlteraPagamento(){
        Fixture f=fixture(true);platformContext(f);Pagamento before=payments.findById(f.payment().getId()).orElseThrow();
        service.close(f.adminCase().getId(),f.tenant().getId(),new CloseRequest(f.adminCase().getVersion(),"ACCOUNTING","encaminhado à contabilidade"),"close-"+UUID.randomUUID(),request(),true);
        Pagamento after=payments.findById(f.payment().getId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(before.getStatus());assertThat(after.getConfirmedAt()).isEqualTo(before.getConfirmedAt());assertThat(after.getReconciliationStatus()).isEqualTo(before.getReconciliationStatus());
    }

    private Fixture fixture(boolean activeCase){
        String u=UUID.randomUUID().toString().replace("-","").substring(0,12);
        Tenant tenant=tenant("Tenant "+u,"t"+u,"T"+u);Tenant other=tenant("Other "+u,"o"+u,"O"+u);
        User finance=user("fin"+u,"9"+u);User cashier=user("cash"+u,"8"+u);User platform=user("plat"+u,"7"+u);
        Pedido pedido=new Pedido();pedido.setTenant(tenant);pedido.setNumero("P"+u);pedido.setStatus(StatusPedido.EM_ANDAMENTO);pedido.setStatusFinanceiro(StatusFinanceiroPedido.NAO_PAGO);pedido.setTipoPagamento(TipoPagamentoPedido.POS_PAGO);pedido.setTotal(new BigDecimal("100.00"));pedido=pedidos.saveAndFlush(pedido);
        Pagamento payment=Pagamento.builder().tenant(tenant).pedido(pedido).tipoPagamento(TipoPagamentoFinanceiro.POS_PAGO).amount(new BigDecimal("100.00")).status(StatusPagamentoGateway.PENDENTE).externalReference("R"+u).observacoes("final hardening").build();
        payment.setReconciliationStatus(StatusReconciliacaoAppyPay.BLOQUEADO_DOMINIO);payment.setReconciliationLastRemoteStatus("CONFIRMED");payment.setReconciliationLastResponseHash("a".repeat(64));payment=payments.saveAndFlush(payment);
        PagamentoReconciliationCase adminCase=null;
        if(activeCase){adminCase=new PagamentoReconciliationCase();adminCase.setTenant(tenant);adminCase.setPagamento(payment);adminCase.setPedido(pedido);adminCase.setStatus(ReconciliationCaseStatus.EM_ANALISE);adminCase.setOpenedAt(LocalDateTime.now());adminCase.setOpenedBy("TEST");adminCase.setCreatedFromReconciliationStatus(StatusReconciliacaoAppyPay.BLOQUEADO_DOMINIO.name());adminCase.setOrigin(ReconciliationCaseOrigin.LEGACY_BACKFILL);adminCase.setRemoteStatusSnapshot("CONFIRMED");adminCase.setLocalStatusSnapshot(StatusPagamentoGateway.PENDENTE.name());adminCase.setResponseFingerprintSnapshot("a".repeat(64));adminCase.setActive(true);adminCase=cases.saveAndFlush(adminCase);}
        return new Fixture(tenant,other,finance,cashier,platform,pedido,payment,adminCase);
    }

    private Tenant tenant(String name,String slug,String code){Tenant t=new Tenant();t.setNome(name);t.setSlug(slug);t.setTenantCode(code);t.setTipo(TenantTipo.RESTAURANTE);t.setEstado(TenantEstado.ATIVO);return tenants.saveAndFlush(t);}
    private User user(String name,String phone){User u=new User();u.setUsername(name);u.setPassword("x");u.setTelefone(phone);u.setAtivo(true);return users.saveAndFlush(u);}
    private void addNote(Fixture f,String reason,ReconciliationNoteVisibility visibility){PagamentoReconciliationCaseEvent e=new PagamentoReconciliationCaseEvent();e.setReconciliationCase(f.adminCase());e.setTenantId(f.tenant().getId());e.setPagamentoId(f.payment().getId());e.setPedidoId(f.pedido().getId());e.setActorUserId(f.platform().getId());e.setActorRoles("ROLE_ADMIN");e.setActorOrigin("PLATFORM_ADMIN");e.setCorrelationId("corr-"+UUID.randomUUID());e.setAction(ReconciliationCaseAction.NOTE_ADDED);e.setReason(reason);e.setNoteType(ReconciliationNoteType.ANALYSIS);e.setNoteVisibility(visibility);events.saveAndFlush(e);}
    private void tenantContext(Fixture f,TenantUserRole role){Long id=role==TenantUserRole.TENANT_CASHIER?f.cashier().getId():f.finance().getId();TenantContextHolder.set(new TenantContext(f.tenant().getId(),f.tenant().getTenantCode(),id,Set.of(role.name()),TenantResolutionSource.JWT,false,false));}
    private void platformContext(Fixture f){TenantContextHolder.set(new TenantContext(null,null,f.platform().getId(),Set.of("ROLE_ADMIN"),TenantResolutionSource.JWT,true,false));}
    private MockHttpServletRequest request(){MockHttpServletRequest r=new MockHttpServletRequest();r.addHeader("X-Correlation-Id","corr-"+UUID.randomUUID());r.setRemoteAddr("127.0.0.1");return r;}
    private <T>T withPlatform(Fixture f,Callable<T> action) throws Exception{platformContext(f);try{return action.call();}finally{TenantContextHolder.clear();}}
    private <T>List<T> runTogether(Callable<T> first,Callable<T> second) throws Exception{ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch ready=new CountDownLatch(2),go=new CountDownLatch(1);Callable<T> gated=()->{ready.countDown();go.await(10,TimeUnit.SECONDS);return first.call();};Callable<T> gated2=()->{ready.countDown();go.await(10,TimeUnit.SECONDS);return second.call();};try{Future<T>a=pool.submit(gated);Future<T>b=pool.submit(gated2);assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue();go.countDown();return List.of(a.get(30,TimeUnit.SECONDS),b.get(30,TimeUnit.SECONDS));}finally{pool.shutdownNow();}}
    private List<CommandHttp> commands(Long version){String v=version==null?"":"\"version\":"+version+",";return List.of(new CommandHttp("assign","{"+v+"\"assignedToUserId\":1,\"reason\":\"atribuir\"}"),new CommandHttp("notes","{"+v+"\"content\":\"nota\",\"type\":\"ANALYSIS\",\"visibility\":\"TENANT\"}"),new CommandHttp("classify","{"+v+"\"classification\":\"DADO_LEGADO\",\"reason\":\"classificar\"}"),new CommandHttp("retry","{"+v+"\"reason\":\"tentar\"}"),new CommandHttp("close","{"+v+"\"resolution\":\"ACCOUNTING\",\"reason\":\"encerrar\"}"));}
    private String noteBody(Long version,String content){return "{\"version\":"+version+",\"content\":\""+content+"\",\"type\":\"ANALYSIS\",\"visibility\":\"TENANT\"}";}
    private record Fixture(Tenant tenant,Tenant otherTenant,User finance,User cashier,User platform,Pedido pedido,Pagamento payment,PagamentoReconciliationCase adminCase){}
    private record CommandHttp(String path,String body){}
}

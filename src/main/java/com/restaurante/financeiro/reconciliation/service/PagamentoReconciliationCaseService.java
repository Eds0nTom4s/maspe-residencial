package com.restaurante.financeiro.reconciliation.service;

import com.restaurante.exception.*;
import com.restaurante.financeiro.enums.*;
import com.restaurante.financeiro.reconciliation.dto.ReconciliationCaseContracts.*;
import com.restaurante.financeiro.reconciliation.model.*;
import com.restaurante.financeiro.reconciliation.repository.*;
import com.restaurante.financeiro.repository.PagamentoGatewayRepository;
import com.restaurante.model.entity.*;
import com.restaurante.model.enums.TenantUserRole;
import com.restaurante.repository.UserRepository;
import com.restaurante.repository.TenantUserRepository;
import com.restaurante.model.enums.TenantUserEstado;
import com.restaurante.security.tenant.*;
import com.restaurante.service.PedidoPagamentoPolicy;
import jakarta.persistence.OptimisticLockException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class PagamentoReconciliationCaseService {
    private final PagamentoReconciliationCaseRepository cases;
    private final PagamentoReconciliationCaseEventRepository events;
    private final PagamentoGatewayRepository payments;
    private final UserRepository users;
    private final TenantUserRepository tenantUsers;
    private final TenantGuard tenantGuard;
    private final PedidoPagamentoPolicy paymentPolicy;
    private final ReconciliationAuditContext auditContext;

    @Transactional
    public PagamentoReconciliationCase materialize(Pagamento payment, ReconciliationCaseOrigin origin) {
        return cases.findByPagamentoIdAndActiveTrue(payment.getId()).orElseGet(() -> {
            if (payment.getReconciliationStatus()!=StatusReconciliacaoAppyPay.BLOQUEADO_DOMINIO)
                throw new ConflictException("Pagamento não está BLOQUEADO_DOMINIO.");
            PagamentoReconciliationCase c=new PagamentoReconciliationCase();
            c.setTenant(payment.getTenant()); c.setPagamento(payment); c.setPedido(payment.getPedido());
            c.setStatus(ReconciliationCaseStatus.ABERTO); c.setOpenedAt(LocalDateTime.now());
            c.setOpenedBy(origin==ReconciliationCaseOrigin.LEGACY_BACKFILL?"SYSTEM_LEGACY_BACKFILL":"SYSTEM_RECONCILIATION");
            c.setCreatedFromReconciliationStatus(payment.getReconciliationStatus().name()); c.setOrigin(origin);
            c.setRemoteStatusSnapshot(payment.getReconciliationLastRemoteStatus()); c.setLocalStatusSnapshot(payment.getStatus().name());
            c.setRemoteReferenceSnapshot(payment.getExternalReference()); c.setResponseFingerprintSnapshot(payment.getReconciliationLastResponseHash()); c.setActive(true);
            c=cases.save(c);
            systemEvent(c, ReconciliationCaseAction.OPENED, origin.name());
            return c;
        });
    }

    @Transactional(readOnly=true)
    public Page<Summary> list(Long explicitTenantId, ReconciliationCaseStatus status, ReconciliationCaseClassification classification,
                              Long pagamentoId, Long pedidoId, Long assignedTo, Pageable pageable, boolean platform) {
        Long tenantId=authorizedTenant(explicitTenantId, platform, false);
        Specification<PagamentoReconciliationCase> s=(r,q,b)->b.equal(r.get("tenant").get("id"),tenantId);
        if(status!=null)s=s.and((r,q,b)->b.equal(r.get("status"),status));
        if(classification!=null)s=s.and((r,q,b)->b.equal(r.get("classification"),classification));
        if(pagamentoId!=null)s=s.and((r,q,b)->b.equal(r.get("pagamento").get("id"),pagamentoId));
        if(pedidoId!=null)s=s.and((r,q,b)->b.equal(r.get("pedido").get("id"),pedidoId));
        if(assignedTo!=null)s=s.and((r,q,b)->b.equal(r.get("assignedTo").get("id"),assignedTo));
        return cases.findAll(s,pageable).map(this::summary);
    }

    @Transactional(readOnly=true)
    public Detail detail(Long id, Long explicitTenantId, boolean platform) {
        PagamentoReconciliationCase c=getAuthorized(id,explicitTenantId,platform,false);
        return detailOf(c);
    }

    @Transactional(readOnly=true)
    public Eligibility eligibility(Long id, Long explicitTenantId, boolean platform) {
        return eligibilityOf(getAuthorized(id,explicitTenantId,platform,false));
    }

    @Transactional
    public Detail assign(Long id, Long explicitTenantId, AssignRequest req, String key, HttpServletRequest http, boolean platform) {
        PagamentoReconciliationCase c=getAuthorized(id,explicitTenantId,platform,true); requireKey(key);
        if(replayed(c,ReconciliationCaseAction.ASSIGNED,key)) return detailOf(c); checkVersion(c,req.version());
        User next=users.findById(req.assignedToUserId()).orElseThrow(()->new ResourceNotFoundException("Responsável não encontrado."));
        if(!tenantUsers.existsByTenantIdAndUserIdAndEstado(c.getTenant().getId(),next.getId(),TenantUserEstado.ATIVO)) throw new BusinessException("Responsável não pertence ao tenant do caso.");
        Long before=c.getAssignedTo()==null?null:c.getAssignedTo().getId(); c.setAssignedTo(next);
        if(c.getStatus()==ReconciliationCaseStatus.ABERTO)c.setStatus(ReconciliationCaseStatus.EM_ANALISE);
        audit(c,ReconciliationCaseAction.ASSIGNED,String.valueOf(before),String.valueOf(next.getId()),req.reason(),key,null,null,http);
        return detailOf(cases.save(c));
    }

    @Transactional
    public Detail note(Long id, Long explicitTenantId, NoteRequest req, String key, HttpServletRequest http, boolean platform) {
        PagamentoReconciliationCase c=getAuthorized(id,explicitTenantId,platform,true); requireKey(key);
        if(replayed(c,ReconciliationCaseAction.NOTE_ADDED,key)) return detailOf(c); checkVersion(c,req.version());
        audit(c,ReconciliationCaseAction.NOTE_ADDED,null,null,req.content(),key,req.type(),req.visibility(),http);
        return detailOf(c);
    }

    @Transactional
    public Detail classify(Long id, Long explicitTenantId, ClassifyRequest req, String key, HttpServletRequest http, boolean platform) {
        PagamentoReconciliationCase c=getAuthorized(id,explicitTenantId,platform,true); requireKey(key);
        if(replayed(c,ReconciliationCaseAction.CLASSIFIED,key)) return detailOf(c); checkVersion(c,req.version());
        String before=c.getClassification()==null?null:c.getClassification().name(); c.setClassification(req.classification());
        if(c.getStatus()==ReconciliationCaseStatus.ABERTO)c.setStatus(ReconciliationCaseStatus.EM_ANALISE);
        audit(c,ReconciliationCaseAction.CLASSIFIED,before,req.classification().name(),req.reason(),key,null,null,http);
        return detailOf(cases.save(c));
    }

    @Transactional
    public Detail retry(Long id, Long explicitTenantId, CommandContext req, String key, HttpServletRequest http, boolean platform) {
        PagamentoReconciliationCase c=getAuthorized(id,explicitTenantId,platform,true); requireKey(key);
        if(replayed(c,ReconciliationCaseAction.RETRY_REQUESTED,key)) return detailOf(c); checkVersion(c,req.version());
        Pagamento p=payments.findForUpdateById(c.getPagamento().getId()).orElseThrow(()->new ResourceNotFoundException("Pagamento não encontrado."));
        List<String> blockers=blockers(p); if(!blockers.isEmpty())throw new ConflictException("Retry bloqueado: "+String.join("; ",blockers));
        p.setReconciliationStatus(StatusReconciliacaoAppyPay.ELIGIVEL); p.setReconciliationNextAttemptAt(LocalDateTime.now()); payments.save(p);
        c.setStatus(ReconciliationCaseStatus.RESOLVIDO); c.setResolvedAt(LocalDateTime.now()); c.setResolvedBy(actor(http).origin());
        c.setResolution("RETRY_REQUESTED"); c.setResolutionReason(req.reason()); c.setActive(false);
        audit(c,ReconciliationCaseAction.RETRY_REQUESTED,"BLOQUEADO_DOMINIO","ELIGIVEL",req.reason(),key,null,null,http);
        return detailOf(cases.save(c));
    }

    @Transactional
    public Detail close(Long id, Long explicitTenantId, CloseRequest req, String key, HttpServletRequest http, boolean platform) {
        PagamentoReconciliationCase c=getAuthorized(id,explicitTenantId,platform,true); requireKey(key);
        if(replayed(c,ReconciliationCaseAction.CLOSED,key)) return detailOf(c); checkVersion(c,req.version());
        if(c.getStatus()==ReconciliationCaseStatus.ABERTO)throw new ConflictException("Caso deve estar em análise antes do encerramento.");
        c.setStatus(ReconciliationCaseStatus.ENCERRADO_SEM_CONVERGENCIA);c.setActive(false);c.setResolvedAt(LocalDateTime.now());
        c.setResolvedBy(actor(http).origin());c.setResolution(req.resolution());c.setResolutionReason(req.reason());
        audit(c,ReconciliationCaseAction.CLOSED,null,c.getStatus().name(),req.reason(),key,null,null,http);
        return detailOf(cases.save(c));
    }

    @Transactional
    public BackfillResult backfill(boolean dryRun) {
        tenantGuard.assertPlatformAdmin();
        List<Pagamento> blocked=payments.findAll().stream().filter(p->p.getReconciliationStatus()==StatusReconciliacaoAppyPay.BLOQUEADO_DOMINIO).toList();
        long existing=blocked.stream().filter(p->cases.findByPagamentoIdAndActiveTrue(p.getId()).isPresent()).count();
        if(!dryRun)blocked.stream().filter(p->cases.findByPagamentoIdAndActiveTrue(p.getId()).isEmpty()).forEach(p->materialize(p,ReconciliationCaseOrigin.LEGACY_BACKFILL));
        return new BackfillResult(dryRun,blocked.size(),dryRun?0:blocked.size()-existing,existing);
    }

    private PagamentoReconciliationCase getAuthorized(Long id,Long explicitTenantId,boolean platform,boolean write){
        Long tenantId=authorizedTenant(explicitTenantId,platform,write);
        return cases.findTenantCase(id,tenantId).orElseThrow(()->new ResourceNotFoundException("Caso não encontrado."));
    }
    private Long authorizedTenant(Long explicitTenantId,boolean platform,boolean write){
        if(platform){tenantGuard.assertPlatformAdmin();if(explicitTenantId==null)throw new BusinessException("tenantId explícito é obrigatório para PLATFORM_ADMIN.");return explicitTenantId;}
        TenantContext c=tenantGuard.requireContext();
        if(write)tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER,TenantUserRole.TENANT_ADMIN,TenantUserRole.TENANT_FINANCE);
        else tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER,TenantUserRole.TENANT_ADMIN,TenantUserRole.TENANT_FINANCE,TenantUserRole.TENANT_CASHIER);
        return c.tenantId();
    }
    private Eligibility eligibilityOf(PagamentoReconciliationCase c){Pagamento p=c.getPagamento();List<String>b=blockers(p);return new Eligibility(b.isEmpty(),b.isEmpty()?List.of("RETRY","NOTE","ASSIGN","CLASSIFY","CLOSE"):List.of("NOTE","ASSIGN","CLASSIFY","CLOSE"),checks(p,b),p.getReconciliationLastRemoteStatus(),p.getStatus().name(),p.getPedido()==null?null:p.getPedido().getStatus().name(),p.getAmount(),p.getExternalReference(),b);}
    private List<String> blockers(Pagamento p){List<String>b=new ArrayList<>();if(p.getStatus()!=StatusPagamentoGateway.PENDENTE)b.add("PAYMENT_NOT_PENDING");if(p.getReconciliationStatus()!=StatusReconciliacaoAppyPay.BLOQUEADO_DOMINIO)b.add("NOT_DOMAIN_BLOCKED");if(!"CONFIRMED".equalsIgnoreCase(p.getReconciliationLastRemoteStatus())&&!"CONFIRMADO".equalsIgnoreCase(p.getReconciliationLastRemoteStatus()))b.add("REMOTE_NOT_CONFIRMED");if(p.getReconciliationLastResponseHash()==null)b.add("REMOTE_FINGERPRINT_MISSING");if(p.getPedido()==null)b.add("PEDIDO_NOT_FOUND");else try{paymentPolicy.assertPodeConfirmarPagamento(p.getPedido(),PedidoPagamentoPolicy.PaymentFlow.GATEWAY_CONFIRMATION);}catch(RuntimeException e){b.add("DOMAIN_POLICY: "+e.getMessage());}return b;}
    private List<PolicyCheck> checks(Pagamento p,List<String>b){return List.of(new PolicyCheck("PAYMENT_PENDING",p.getStatus()==StatusPagamentoGateway.PENDENTE,p.getStatus().name()),new PolicyCheck("DOMAIN_ELIGIBLE",b.stream().noneMatch(x->x.startsWith("DOMAIN_POLICY")),p.getPedido()==null?"pedido ausente":p.getPedido().getStatus().name()),new PolicyCheck("REMOTE_CONFIRMED",b.stream().noneMatch(x->x.equals("REMOTE_NOT_CONFIRMED")),String.valueOf(p.getReconciliationLastRemoteStatus())),new PolicyCheck("FINGERPRINT_PRESENT",p.getReconciliationLastResponseHash()!=null,"payload integral não exposto"));}
    private Detail detailOf(PagamentoReconciliationCase c){List<Event> h=events.findByReconciliationCaseIdOrderByCreatedAtAsc(c.getId()).stream().map(e->new Event(e.getId(),e.getAction(),e.getActorUserId(),e.getActorRoles(),e.getActorOrigin(),e.getReason(),e.getNoteType(),e.getNoteVisibility(),e.getCorrelationId(),e.getCreatedAt())).toList();Pagamento p=c.getPagamento();return new Detail(summary(c),p.getReconciliationLastError(),p.getReconciliationAttempts(),p.getReconciliationLastAttemptAt(),p.getReconciliationLastResponseHash(),eligibilityOf(c),h,false);}
    private Summary summary(PagamentoReconciliationCase c){Pagamento p=c.getPagamento();LocalDateTime u=Optional.ofNullable(c.getUpdatedAt()).orElse(c.getOpenedAt());return new Summary(c.getId(),c.getVersion(),new TenantRef(c.getTenant().getId(),c.getTenant().getTenantCode()),p.getId(),c.getPedido()==null?null:c.getPedido().getId(),sanitize(p.getExternalReference()),p.getReconciliationLastRemoteStatus(),p.getStatus().name(),p.getReconciliationStatus()==null?null:p.getReconciliationStatus().name(),c.getStatus(),c.getClassification(),c.getAssignedTo()==null?null:new Assignee(c.getAssignedTo().getId(),c.getAssignedTo().getUsername()),c.getOpenedAt(),u,Duration.between(c.getOpenedAt(),LocalDateTime.now()).toHours(),Duration.between(c.getOpenedAt(),LocalDateTime.now()).toHours()>24?"HIGH":"NORMAL");}
    private String sanitize(String s){if(s==null||s.length()<5)return s;return s.substring(0,2)+"***"+s.substring(s.length()-2);}
    private void checkVersion(PagamentoReconciliationCase c,Long v){if(v==null||!Objects.equals(v,c.getVersion()))throw new OptimisticLockException("Versão desactualizada.");}
    private void requireKey(String k){if(k==null||k.isBlank()||k.length()>100)throw new BusinessException("Idempotency-Key obrigatória e limitada a 100 caracteres.");}
    private boolean replayed(PagamentoReconciliationCase c,ReconciliationCaseAction a,String k){return events.findByReconciliationCaseIdAndActionAndIdempotencyKey(c.getId(),a,k).isPresent();}
    private ReconciliationAuditContext.Actor actor(HttpServletRequest h){return auditContext.current(h);}
    private void audit(PagamentoReconciliationCase c,ReconciliationCaseAction a,String before,String after,String reason,String key,ReconciliationNoteType nt,ReconciliationNoteVisibility nv,HttpServletRequest h){ReconciliationAuditContext.Actor x=actor(h);PagamentoReconciliationCaseEvent e=baseEvent(c,a,reason);e.setActorUserId(x.userId());e.setActorRoles(x.roles());e.setActorOrigin(x.origin());e.setIp(x.ip());e.setUserAgent(x.userAgent());e.setCorrelationId(x.correlationId());e.setIdempotencyKey(key);e.setBeforeState(before);e.setAfterState(after);e.setNoteType(nt);e.setNoteVisibility(nv);events.save(e);}
    private void systemEvent(PagamentoReconciliationCase c,ReconciliationCaseAction a,String reason){PagamentoReconciliationCaseEvent e=baseEvent(c,a,reason);e.setActorRoles("SYSTEM");e.setActorOrigin("SYSTEM");e.setCorrelationId(UUID.randomUUID().toString());events.save(e);}
    private PagamentoReconciliationCaseEvent baseEvent(PagamentoReconciliationCase c,ReconciliationCaseAction a,String reason){PagamentoReconciliationCaseEvent e=new PagamentoReconciliationCaseEvent();e.setReconciliationCase(c);e.setTenantId(c.getTenant().getId());e.setPagamentoId(c.getPagamento().getId());e.setPedidoId(c.getPedido()==null?null:c.getPedido().getId());e.setAction(a);e.setReason(reason);return e;}
}

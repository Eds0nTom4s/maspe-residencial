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
    private final ReconciliationCommandFingerprint fingerprints;
    private final ReconciliationCaseStateMachine stateMachine;
    private final ReconciliationMaterializationAuditRepository materializationAudits;
    private final ReconciliationMaterializationLock materializationLock;

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

    @Transactional
    public void onDomainBlocked(Pagamento payment){PagamentoReconciliationCase c=materialize(payment,ReconciliationCaseOrigin.AUTOMATIC_BLOCK);stateMachine.transition(c,ReconciliationCaseStatus.AGUARDANDO_CORRECCAO_DOMINIO);cases.save(c);systemEvent(c,ReconciliationCaseAction.DOMAIN_REBLOCKED,"DOMAIN_POLICY_BLOCKED");}
    @Transactional
    public void onAutomaticConfirmed(Pagamento payment){cases.findByPagamentoIdAndActiveTrue(payment.getId()).ifPresent(c->{stateMachine.transition(c,ReconciliationCaseStatus.RESOLVIDO);c.setActive(false);c.setResolvedAt(LocalDateTime.now());c.setResolvedBy("SYSTEM_RECONCILIATION");c.setResolution("AUTOMATIC_CONFIRMED");cases.save(c);systemEvent(c,ReconciliationCaseAction.AUTOMATIC_RESOLVED,"AUTOMATIC_CONFIRMATION_SUCCEEDED");});}
    @Transactional
    public void onPermanentExternalFailure(Pagamento payment){cases.findByPagamentoIdAndActiveTrue(payment.getId()).ifPresent(c->{stateMachine.transition(c,ReconciliationCaseStatus.AGUARDANDO_ACCAO_FINANCEIRA_EXTERNA);cases.save(c);systemEvent(c,ReconciliationCaseAction.EXTERNAL_FAILURE,"PERMANENT_REMOTE_FAILURE");});}
    @Transactional
    public void onTemporaryFailure(Pagamento payment){cases.findByPagamentoIdAndActiveTrue(payment.getId()).ifPresent(c->{if(c.getStatus()==ReconciliationCaseStatus.PRONTO_PARA_NOVA_TENTATIVA)stateMachine.transition(c,ReconciliationCaseStatus.EM_ANALISE);cases.save(c);systemEvent(c,ReconciliationCaseAction.TEMPORARY_FAILURE,"TEMPORARY_RECONCILIATION_FAILURE");});}

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
        return detailOf(c,platform);
    }

    @Transactional(readOnly=true)
    public Eligibility eligibility(Long id, Long explicitTenantId, boolean platform) {
        return eligibilityOf(getAuthorized(id,explicitTenantId,platform,false),platform);
    }

    @Transactional
    public Detail assign(Long id, Long explicitTenantId, AssignRequest req, String key, HttpServletRequest http, boolean platform) {
        PagamentoReconciliationCase c=getAuthorized(id,explicitTenantId,platform,true); requireKey(key);
        String fp=fingerprints.calculate(c.getId(),ReconciliationCaseAction.ASSIGNED,Map.of("version",req.version(),"assignedToUserId",req.assignedToUserId(),"reason",req.reason()));
        if(replayed(c,ReconciliationCaseAction.ASSIGNED,key,fp)) return detailOf(c,platform); checkVersion(c,req.version());
        requireAllowed(c,"ASSIGN",platform);
        User next=users.findById(req.assignedToUserId()).orElseThrow(()->new ResourceNotFoundException("Responsável não encontrado."));
        var membership=tenantUsers.findByTenantIdAndUserIdAndEstado(c.getTenant().getId(),next.getId(),TenantUserEstado.ATIVO).orElseThrow(()->new BusinessException("Responsável não pertence activamente ao tenant do caso."));
        if(!EnumSet.of(TenantUserRole.TENANT_OWNER,TenantUserRole.TENANT_ADMIN,TenantUserRole.TENANT_FINANCE).contains(membership.getRole()))throw new BusinessException("Responsável deve possuir role financeira administrativa.");
        Long before=c.getAssignedTo()==null?null:c.getAssignedTo().getId(); c.setAssignedTo(next);
        if(c.getStatus()==ReconciliationCaseStatus.ABERTO)stateMachine.transition(c,ReconciliationCaseStatus.EM_ANALISE);
        audit(c,ReconciliationCaseAction.ASSIGNED,String.valueOf(before),String.valueOf(next.getId()),req.reason(),key,fp,null,null,http);
        return detailOf(cases.save(c),platform);
    }

    @Transactional
    public Detail note(Long id, Long explicitTenantId, NoteRequest req, String key, HttpServletRequest http, boolean platform) {
        PagamentoReconciliationCase c=getAuthorized(id,explicitTenantId,platform,true); requireKey(key);
        if(!platform&&req.visibility()==ReconciliationNoteVisibility.PLATFORM_ONLY)throw new BusinessException("Nota PLATFORM_ONLY é exclusiva da plataforma.");
        String fp=fingerprints.calculate(c.getId(),ReconciliationCaseAction.NOTE_ADDED,Map.of("version",req.version(),"content",req.content(),"noteType",req.type(),"noteVisibility",req.visibility()));
        if(replayed(c,ReconciliationCaseAction.NOTE_ADDED,key,fp)) return detailOf(c,platform); checkVersion(c,req.version());
        requireAllowed(c,"NOTE",platform);
        audit(c,ReconciliationCaseAction.NOTE_ADDED,null,null,req.content(),key,fp,req.type(),req.visibility(),http);
        return detailOf(c,platform);
    }

    @Transactional
    public Detail classify(Long id, Long explicitTenantId, ClassifyRequest req, String key, HttpServletRequest http, boolean platform) {
        PagamentoReconciliationCase c=getAuthorized(id,explicitTenantId,platform,true); requireKey(key);
        String fp=fingerprints.calculate(c.getId(),ReconciliationCaseAction.CLASSIFIED,Map.of("version",req.version(),"classification",req.classification(),"reason",req.reason()));
        if(replayed(c,ReconciliationCaseAction.CLASSIFIED,key,fp)) return detailOf(c,platform); checkVersion(c,req.version());
        requireAllowed(c,"CLASSIFY",platform);
        String before=c.getClassification()==null?null:c.getClassification().name(); c.setClassification(req.classification());
        if(c.getStatus()==ReconciliationCaseStatus.ABERTO)stateMachine.transition(c,ReconciliationCaseStatus.EM_ANALISE);
        audit(c,ReconciliationCaseAction.CLASSIFIED,before,req.classification().name(),req.reason(),key,fp,null,null,http);
        return detailOf(cases.save(c),platform);
    }

    @Transactional
    public Detail retry(Long id, Long explicitTenantId, CommandContext req, String key, HttpServletRequest http, boolean platform) {
        PagamentoReconciliationCase c=getAuthorized(id,explicitTenantId,platform,true); requireKey(key);
        String fp=fingerprints.calculate(c.getId(),ReconciliationCaseAction.RETRY_REQUESTED,Map.of("version",req.version(),"reason",req.reason()));
        if(replayed(c,ReconciliationCaseAction.RETRY_REQUESTED,key,fp)) return detailOf(c,platform); checkVersion(c,req.version());
        requireAllowed(c,"RETRY",platform);
        Pagamento p=payments.findForUpdateById(c.getPagamento().getId()).orElseThrow(()->new ResourceNotFoundException("Pagamento não encontrado."));
        List<String> blockers=blockers(p); if(!blockers.isEmpty())throw new ConflictException("Retry bloqueado: "+String.join("; ",blockers));
        p.setReconciliationStatus(StatusReconciliacaoAppyPay.ELIGIVEL); p.setReconciliationNextAttemptAt(LocalDateTime.now()); payments.save(p);
        stateMachine.transition(c,ReconciliationCaseStatus.PRONTO_PARA_NOVA_TENTATIVA);
        c.setResolutionReason(req.reason());
        audit(c,ReconciliationCaseAction.RETRY_REQUESTED,"BLOQUEADO_DOMINIO","ELIGIVEL",req.reason(),key,fp,null,null,http);
        return detailOf(cases.save(c),platform);
    }

    @Transactional
    public Detail close(Long id, Long explicitTenantId, CloseRequest req, String key, HttpServletRequest http, boolean platform) {
        PagamentoReconciliationCase c=getAuthorized(id,explicitTenantId,platform,true); requireKey(key);
        String fp=fingerprints.calculate(c.getId(),ReconciliationCaseAction.CLOSED,Map.of("version",req.version(),"resolution",req.resolution(),"reason",req.reason()));
        if(replayed(c,ReconciliationCaseAction.CLOSED,key,fp)) return detailOf(c,platform); checkVersion(c,req.version());
        requireAllowed(c,"CLOSE",platform);
        stateMachine.transition(c,ReconciliationCaseStatus.ENCERRADO_SEM_CONVERGENCIA);c.setActive(false);c.setResolvedAt(LocalDateTime.now());
        c.setResolvedBy(actor(http).origin());c.setResolution(req.resolution());c.setResolutionReason(req.reason());
        audit(c,ReconciliationCaseAction.CLOSED,null,c.getStatus().name(),req.reason(),key,fp,null,null,http);
        return detailOf(cases.save(c),platform);
    }

    @Transactional
    public BackfillResult backfill(Long tenantId, boolean dryRun, MaterializeRequest request, String key, HttpServletRequest http) {
        tenantGuard.assertPlatformAdmin();
        if(tenantId==null)throw new BusinessException("tenantId explícito é obrigatório.");
        if(!dryRun){requireKey(key);if(request==null||request.reason()==null||request.reason().isBlank())throw new BusinessException("Motivo é obrigatório.");}
        String materializeFingerprint=!dryRun?fingerprints.calculate(tenantId,ReconciliationCaseAction.OPENED,Map.of("tenantId",tenantId,"dryRun",false,"reason",request.reason())):null;
        if(!dryRun)materializationLock.acquire("reconciliation-materialize:"+key);
        if(!dryRun){var old=materializationAudits.findByIdempotencyKey(key);if(old.isPresent()){if(!Objects.equals(old.get().getCommandFingerprint(),materializeFingerprint))throw new ConflictException("IDEMPOTENCY_CONFLICT");return new BackfillResult(false,old.get().getEligibleCount(),old.get().getCreatedCount(),old.get().getEligibleCount()-old.get().getCreatedCount());}}
        List<Pagamento> blocked=payments.findByTenantIdAndReconciliationStatus(tenantId,StatusReconciliacaoAppyPay.BLOQUEADO_DOMINIO);
        long existing=blocked.stream().filter(p->cases.findByPagamentoIdAndActiveTrue(p.getId()).isPresent()).count();
        if(!dryRun)blocked.stream().filter(p->cases.findByPagamentoIdAndActiveTrue(p.getId()).isEmpty()).forEach(p->materialize(p,ReconciliationCaseOrigin.LEGACY_BACKFILL));
        long created=dryRun?0:blocked.size()-existing;
        if(!dryRun){var actor=actor(http);ReconciliationMaterializationAudit a=new ReconciliationMaterializationAudit();a.setTenantId(tenantId);a.setActorUserId(actor.userId());a.setActorRoles(actor.roles());a.setActorOrigin(actor.origin());a.setCorrelationId(actor.correlationId());a.setIdempotencyKey(key);a.setCommandFingerprint(materializeFingerprint);a.setReason(request.reason());a.setDryRun(false);a.setEligibleCount(blocked.size());a.setCreatedCount(created);materializationAudits.save(a);}
        return new BackfillResult(dryRun,blocked.size(),created,existing);
    }

    private PagamentoReconciliationCase getAuthorized(Long id,Long explicitTenantId,boolean platform,boolean write){
        Long tenantId=authorizedTenant(explicitTenantId,platform,write);
        Optional<PagamentoReconciliationCase> result=write?cases.findTenantCaseForUpdate(id,tenantId):cases.findTenantCase(id,tenantId);
        return result.orElseThrow(()->new ResourceNotFoundException("Caso não encontrado."));
    }
    private Long authorizedTenant(Long explicitTenantId,boolean platform,boolean write){
        if(platform){tenantGuard.assertPlatformAdmin();if(explicitTenantId==null)throw new BusinessException("tenantId explícito é obrigatório para PLATFORM_ADMIN.");return explicitTenantId;}
        TenantContext c=tenantGuard.requireContext();
        if(write)tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER,TenantUserRole.TENANT_ADMIN,TenantUserRole.TENANT_FINANCE);
        else tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER,TenantUserRole.TENANT_ADMIN,TenantUserRole.TENANT_FINANCE,TenantUserRole.TENANT_CASHIER);
        return c.tenantId();
    }
    private Eligibility eligibilityOf(PagamentoReconciliationCase c,boolean platform){Pagamento p=c.getPagamento();List<String>b=blockers(p);boolean retryPossible=b.isEmpty()&&c.isActive()&&stateMachine.canTransition(c.getStatus(),ReconciliationCaseStatus.PRONTO_PARA_NOVA_TENTATIVA);return new Eligibility(retryPossible,allowedActions(c,platform,retryPossible),checks(p,b),p.getReconciliationLastRemoteStatus(),p.getStatus().name(),p.getPedido()==null?null:p.getPedido().getStatus().name(),p.getAmount(),sanitize(p.getExternalReference()),b);}
    private List<String> allowedActions(PagamentoReconciliationCase c,boolean platform,boolean retryPossible){
        if(!c.isActive()||c.getStatus().terminal()||!canWrite(platform))return List.of();
        List<String> allowed=new ArrayList<>(List.of("NOTE","ASSIGN","CLASSIFY"));
        if(retryPossible)allowed.add("RETRY");
        if(stateMachine.canTransition(c.getStatus(),ReconciliationCaseStatus.ENCERRADO_SEM_CONVERGENCIA))allowed.add("CLOSE");
        return List.copyOf(allowed);
    }
    private boolean canWrite(boolean platform){return platform||tenantGuard.hasAnyTenantRole(TenantUserRole.TENANT_OWNER,TenantUserRole.TENANT_ADMIN,TenantUserRole.TENANT_FINANCE);}
    private void requireAllowed(PagamentoReconciliationCase c,String action,boolean platform){
        List<String> actionBlockers="RETRY".equals(action)?blockers(c.getPagamento()):List.of();
        boolean retryPossible="RETRY".equals(action)&&actionBlockers.isEmpty()&&stateMachine.canTransition(c.getStatus(),ReconciliationCaseStatus.PRONTO_PARA_NOVA_TENTATIVA);
        if("RETRY".equals(action)&&!actionBlockers.isEmpty())throw new ConflictException("Retry bloqueado: "+String.join("; ",actionBlockers));
        if(!allowedActions(c,platform,retryPossible).contains(action))throw new ConflictException("Acção administrativa não permitida no estado actual: "+action+".");
    }
    private List<String> blockers(Pagamento p){List<String>b=new ArrayList<>();if(p.getStatus()!=StatusPagamentoGateway.PENDENTE)b.add("PAYMENT_NOT_PENDING");if(p.getReconciliationStatus()!=StatusReconciliacaoAppyPay.BLOQUEADO_DOMINIO)b.add("NOT_DOMAIN_BLOCKED");if(!"CONFIRMED".equalsIgnoreCase(p.getReconciliationLastRemoteStatus())&&!"CONFIRMADO".equalsIgnoreCase(p.getReconciliationLastRemoteStatus()))b.add("REMOTE_NOT_CONFIRMED");if(p.getReconciliationLastResponseHash()==null)b.add("REMOTE_FINGERPRINT_MISSING");if(p.getPedido()==null)b.add("PEDIDO_NOT_FOUND");else try{paymentPolicy.assertPodeConfirmarPagamento(p.getPedido(),PedidoPagamentoPolicy.PaymentFlow.GATEWAY_CONFIRMATION);}catch(RuntimeException e){b.add("DOMAIN_POLICY: "+e.getMessage());}return b;}
    private List<PolicyCheck> checks(Pagamento p,List<String>b){return List.of(new PolicyCheck("PAYMENT_PENDING",p.getStatus()==StatusPagamentoGateway.PENDENTE,p.getStatus().name()),new PolicyCheck("DOMAIN_ELIGIBLE",b.stream().noneMatch(x->x.startsWith("DOMAIN_POLICY")),p.getPedido()==null?"pedido ausente":p.getPedido().getStatus().name()),new PolicyCheck("REMOTE_CONFIRMED",b.stream().noneMatch(x->x.equals("REMOTE_NOT_CONFIRMED")),String.valueOf(p.getReconciliationLastRemoteStatus())),new PolicyCheck("FINGERPRINT_PRESENT",p.getReconciliationLastResponseHash()!=null,"payload integral não exposto"));}
    private Detail detailOf(PagamentoReconciliationCase c,boolean platform){List<Event> h=events.findByReconciliationCaseIdOrderByCreatedAtAsc(c.getId()).stream().filter(e->platform||e.getNoteVisibility()!=ReconciliationNoteVisibility.PLATFORM_ONLY).map(e->new Event(e.getId(),e.getAction(),e.getActorUserId(),e.getActorRoles(),e.getActorOrigin(),e.getReason(),e.getNoteType(),e.getNoteVisibility(),e.getCorrelationId(),e.getCreatedAt())).toList();Pagamento p=c.getPagamento();return new Detail(summary(c),p.getReconciliationLastError(),p.getReconciliationAttempts(),p.getReconciliationLastAttemptAt(),p.getReconciliationLastResponseHash(),eligibilityOf(c,platform),h,false);}
    private Summary summary(PagamentoReconciliationCase c){Pagamento p=c.getPagamento();LocalDateTime u=Optional.ofNullable(c.getUpdatedAt()).orElse(c.getOpenedAt());return new Summary(c.getId(),c.getVersion(),new TenantRef(c.getTenant().getId(),c.getTenant().getTenantCode()),p.getId(),c.getPedido()==null?null:c.getPedido().getId(),sanitize(p.getExternalReference()),p.getReconciliationLastRemoteStatus(),p.getStatus().name(),p.getReconciliationStatus()==null?null:p.getReconciliationStatus().name(),c.getStatus(),c.getClassification(),c.getAssignedTo()==null?null:new Assignee(c.getAssignedTo().getId(),c.getAssignedTo().getUsername()),c.getOpenedAt(),u,Duration.between(c.getOpenedAt(),LocalDateTime.now()).toHours(),Duration.between(c.getOpenedAt(),LocalDateTime.now()).toHours()>24?"HIGH":"NORMAL");}
    private String sanitize(String s){if(s==null||s.length()<5)return s;return s.substring(0,2)+"***"+s.substring(s.length()-2);}
    private void checkVersion(PagamentoReconciliationCase c,Long v){if(v==null||!Objects.equals(v,c.getVersion()))throw new OptimisticLockException("Versão desactualizada.");}
    private void requireKey(String k){if(k==null||k.isBlank()||k.length()>100)throw new BusinessException("Idempotency-Key obrigatória e limitada a 100 caracteres.");}
    private boolean replayed(PagamentoReconciliationCase c,ReconciliationCaseAction a,String k,String fingerprint){var existing=events.findByReconciliationCaseIdAndActionAndIdempotencyKey(c.getId(),a,k);if(existing.isEmpty())return false;if(!Objects.equals(existing.get().getCommandFingerprint(),fingerprint))throw new ConflictException("IDEMPOTENCY_CONFLICT");return true;}
    private ReconciliationAuditContext.Actor actor(HttpServletRequest h){return auditContext.current(h);}
    private void audit(PagamentoReconciliationCase c,ReconciliationCaseAction a,String before,String after,String reason,String key,String fingerprint,ReconciliationNoteType nt,ReconciliationNoteVisibility nv,HttpServletRequest h){ReconciliationAuditContext.Actor x=actor(h);PagamentoReconciliationCaseEvent e=baseEvent(c,a,reason);e.setActorUserId(x.userId());e.setActorRoles(x.roles());e.setActorOrigin(x.origin());e.setIp(x.ip());e.setUserAgent(x.userAgent());e.setCorrelationId(x.correlationId());e.setIdempotencyKey(key);e.setCommandFingerprint(fingerprint);e.setBeforeState(before);e.setAfterState(after);e.setNoteType(nt);e.setNoteVisibility(nv);events.save(e);}
    private void systemEvent(PagamentoReconciliationCase c,ReconciliationCaseAction a,String reason){PagamentoReconciliationCaseEvent e=baseEvent(c,a,reason);e.setActorRoles("SYSTEM");e.setActorOrigin("SYSTEM");e.setCorrelationId(UUID.randomUUID().toString());events.save(e);}
    private PagamentoReconciliationCaseEvent baseEvent(PagamentoReconciliationCase c,ReconciliationCaseAction a,String reason){PagamentoReconciliationCaseEvent e=new PagamentoReconciliationCaseEvent();e.setReconciliationCase(c);e.setTenantId(c.getTenant().getId());e.setPagamentoId(c.getPagamento().getId());e.setPedidoId(c.getPedido()==null?null:c.getPedido().getId());e.setAction(a);e.setReason(reason);return e;}
}

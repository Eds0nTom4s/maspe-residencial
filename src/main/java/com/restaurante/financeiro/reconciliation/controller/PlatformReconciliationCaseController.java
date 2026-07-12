package com.restaurante.financeiro.reconciliation.controller;

import com.restaurante.financeiro.reconciliation.dto.ReconciliationCaseContracts.*;
import com.restaurante.financeiro.reconciliation.model.*;
import com.restaurante.financeiro.reconciliation.service.PagamentoReconciliationCaseService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/platform/financeiro/reconciliation-cases") @RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class PlatformReconciliationCaseController {
    private final PagamentoReconciliationCaseService service;
    @GetMapping public Page<Summary> list(@RequestParam Long tenantId,@RequestParam(required=false) ReconciliationCaseStatus status,@RequestParam(required=false) ReconciliationCaseClassification classification,@RequestParam(required=false) Long pagamentoId,@RequestParam(required=false) Long pedidoId,@RequestParam(required=false) Long assignedTo,@PageableDefault(size=20,sort="updatedAt",direction=Sort.Direction.DESC) Pageable pageable){return service.list(tenantId,status,classification,pagamentoId,pedidoId,assignedTo,pageable,true);}
    @GetMapping("/{id}") public Detail detail(@PathVariable Long id,@RequestParam Long tenantId){return service.detail(id,tenantId,true);}
    @GetMapping("/{id}/eligibility") public Eligibility eligibility(@PathVariable Long id,@RequestParam Long tenantId){return service.eligibility(id,tenantId,true);}
    @PostMapping("/{id}/assign") public Detail assign(@PathVariable Long id,@RequestParam Long tenantId,@Valid @RequestBody AssignRequest r,@RequestHeader("Idempotency-Key")String k,HttpServletRequest h){return service.assign(id,tenantId,r,k,h,true);}
    @PostMapping("/{id}/notes") public Detail note(@PathVariable Long id,@RequestParam Long tenantId,@Valid @RequestBody NoteRequest r,@RequestHeader("Idempotency-Key")String k,HttpServletRequest h){return service.note(id,tenantId,r,k,h,true);}
    @PostMapping("/{id}/classify") public Detail classify(@PathVariable Long id,@RequestParam Long tenantId,@Valid @RequestBody ClassifyRequest r,@RequestHeader("Idempotency-Key")String k,HttpServletRequest h){return service.classify(id,tenantId,r,k,h,true);}
    @PostMapping("/{id}/retry") public Detail retry(@PathVariable Long id,@RequestParam Long tenantId,@Valid @RequestBody CommandContext r,@RequestHeader("Idempotency-Key")String k,HttpServletRequest h){return service.retry(id,tenantId,r,k,h,true);}
    @PostMapping("/{id}/close") public Detail close(@PathVariable Long id,@RequestParam Long tenantId,@Valid @RequestBody CloseRequest r,@RequestHeader("Idempotency-Key")String k,HttpServletRequest h){return service.close(id,tenantId,r,k,h,true);}
    @PostMapping("/materialize") public BackfillResult materialize(@RequestParam(defaultValue="true") boolean dryRun){return service.backfill(dryRun);}
}

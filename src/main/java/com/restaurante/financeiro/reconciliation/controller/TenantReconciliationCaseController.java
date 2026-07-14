package com.restaurante.financeiro.reconciliation.controller;

import com.restaurante.financeiro.reconciliation.dto.ReconciliationCaseContracts.*;
import com.restaurante.financeiro.reconciliation.model.*;
import com.restaurante.financeiro.reconciliation.service.PagamentoReconciliationCaseService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/tenant/financeiro/reconciliation-cases") @RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class TenantReconciliationCaseController {
    private final PagamentoReconciliationCaseService service;
    @GetMapping public Page<Summary> list(@RequestParam(required=false) ReconciliationCaseStatus status,@RequestParam(required=false) ReconciliationCaseClassification classification,@RequestParam(required=false) Long pagamentoId,@RequestParam(required=false) Long pedidoId,@RequestParam(required=false) Long assignedTo,@PageableDefault(size=20,sort="updatedAt",direction=Sort.Direction.DESC) Pageable pageable){return service.list(null,status,classification,pagamentoId,pedidoId,assignedTo,pageable,false);}
    @GetMapping("/{id}") public Detail detail(@PathVariable Long id){return service.detail(id,null,false);}
    @GetMapping("/{id}/eligibility") public Eligibility eligibility(@PathVariable Long id){return service.eligibility(id,null,false);}
    @PostMapping("/{id}/assign") public Detail assign(@PathVariable Long id,@Valid @RequestBody AssignRequest r,@RequestHeader("Idempotency-Key")String k,HttpServletRequest h){return service.assign(id,null,r,k,h,false);}
    @PostMapping("/{id}/notes") public Detail note(@PathVariable Long id,@Valid @RequestBody NoteRequest r,@RequestHeader("Idempotency-Key")String k,HttpServletRequest h){return service.note(id,null,r,k,h,false);}
    @PostMapping("/{id}/classify") public Detail classify(@PathVariable Long id,@Valid @RequestBody ClassifyRequest r,@RequestHeader("Idempotency-Key")String k,HttpServletRequest h){return service.classify(id,null,r,k,h,false);}
    @PostMapping("/{id}/retry") public Detail retry(@PathVariable Long id,@Valid @RequestBody CommandContext r,@RequestHeader("Idempotency-Key")String k,HttpServletRequest h){return service.retry(id,null,r,k,h,false);}
    @PostMapping("/{id}/close") public Detail close(@PathVariable Long id,@Valid @RequestBody CloseRequest r,@RequestHeader("Idempotency-Key")String k,HttpServletRequest h){return service.close(id,null,r,k,h,false);}
}

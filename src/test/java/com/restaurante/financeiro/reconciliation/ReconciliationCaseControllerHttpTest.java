package com.restaurante.financeiro.reconciliation;

import com.restaurante.exception.GlobalExceptionHandler;
import com.restaurante.financeiro.reconciliation.controller.TenantReconciliationCaseController;
import com.restaurante.financeiro.reconciliation.dto.ReconciliationCaseContracts.NoteRequest;
import com.restaurante.financeiro.reconciliation.model.ReconciliationNoteType;
import com.restaurante.financeiro.reconciliation.model.ReconciliationNoteVisibility;
import com.restaurante.financeiro.reconciliation.service.PagamentoReconciliationCaseService;
import jakarta.persistence.OptimisticLockException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReconciliationCaseControllerHttpTest {
    PagamentoReconciliationCaseService service;
    MockMvc mvc;

    @BeforeEach void setup(){
        service=mock(PagamentoReconciliationCaseService.class);
        mvc=MockMvcBuilders.standaloneSetup(new TenantReconciliationCaseController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    static Stream<Arguments> commandsWithoutVersion(){
        return Stream.of(
                Arguments.of("assign","{\"assignedToUserId\":9,\"reason\":\"atribuir\"}"),
                Arguments.of("notes","{\"content\":\"analisar\",\"type\":\"ANALYSIS\",\"visibility\":\"TENANT\"}"),
                Arguments.of("classify","{\"classification\":\"DADO_LEGADO\",\"reason\":\"classificar\"}"),
                Arguments.of("retry","{\"reason\":\"tentar novamente\"}"),
                Arguments.of("close","{\"resolution\":\"ACCOUNTING\",\"reason\":\"encerrar\"}")
        );
    }

    @ParameterizedTest @MethodSource("commandsWithoutVersion")
    void versionAusenteRetorna400SemInvocarServico(String action,String body) throws Exception {
        mvc.perform(post("/tenant/financeiro/reconciliation-cases/5/"+action)
                        .header("Idempotency-Key","missing-version").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test void versionDesactualizadaRetorna409() throws Exception {
        when(service.note(eq(5L),isNull(),any(NoteRequest.class),eq("stale"),any(),eq(false)))
                .thenThrow(new OptimisticLockException("Versão desactualizada."));
        mvc.perform(post("/tenant/financeiro/reconciliation-cases/5/notes")
                        .header("Idempotency-Key","stale").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0,\"content\":\"analisar\",\"type\":\"ANALYSIS\",\"visibility\":\"TENANT\"}"))
                .andExpect(status().isConflict());
    }

    @Test void cashierRecebe403NoPost() throws Exception {
        doThrow(new AccessDeniedException("cashier read-only")).when(service)
                .note(anyLong(),isNull(),any(),anyString(),any(),eq(false));
        mvc.perform(post("/tenant/financeiro/reconciliation-cases/5/notes")
                        .header("Idempotency-Key","cashier").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0,\"content\":\"analisar\",\"type\":\"ANALYSIS\",\"visibility\":\"TENANT\"}"))
                .andExpect(status().isForbidden());
    }
}

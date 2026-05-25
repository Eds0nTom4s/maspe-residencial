package com.restaurante.txevidence.dto.request;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class VerifyTransactionLedgerRequest {
    private LocalDateTime periodStart;
    private LocalDateTime periodEnd;
}


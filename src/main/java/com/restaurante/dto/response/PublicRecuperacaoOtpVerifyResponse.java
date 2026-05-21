package com.restaurante.dto.response;

import lombok.Data;

import java.util.List;

@Data
public class PublicRecuperacaoOtpVerifyResponse {
    private List<PublicRecuperacaoSessaoResumoResponse> sessoesAtivas;
}


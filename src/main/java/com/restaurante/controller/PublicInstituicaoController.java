package com.restaurante.controller;

import com.restaurante.dto.response.ApiResponse;
import com.restaurante.dto.response.PublicInstituicaoResponse;
import com.restaurante.model.entity.Instituicao;
import com.restaurante.service.InstituicaoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/public/instituicao")
@RequiredArgsConstructor
@Tag(name = "Instituição Pública", description = "Endpoints públicos para obter dados básicos do estabelecimento")
public class PublicInstituicaoController {

    private final InstituicaoService instituicaoService;

    @GetMapping
    @Operation(summary = "Obter dados da Instituição", description = "Retorna os dados públicos (nome, sigla, logo) do restaurante")
    public ResponseEntity<ApiResponse<PublicInstituicaoResponse>> getInstituicaoPublica() {
        Instituicao instituicao = instituicaoService.getInstituicaoAtiva();
        
        PublicInstituicaoResponse response = PublicInstituicaoResponse.builder()
                .nome(instituicao.getNome())
                .sigla(instituicao.getSigla())
                .urlLogo(instituicao.getUrlLogo())
                .build();
                
        return ResponseEntity.ok(ApiResponse.success("Dados da instituição", response));
    }
}

package com.restaurante.controller;

import com.restaurante.model.entity.Instituicao;
import com.restaurante.service.InstituicaoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/instituicoes")
@RequiredArgsConstructor
public class InstituicaoController {

    private final InstituicaoService instituicaoService;

    @GetMapping("/ativa")
    public ResponseEntity<Instituicao> getAtiva() {
        return ResponseEntity.ok(instituicaoService.getInstituicaoAtiva());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Instituicao> atualizar(
            @PathVariable Long id,
            @RequestBody Map<String, String> payload
    ) {
        String nome = payload.get("nome");
        String sigla = payload.get("sigla");
        String nif = payload.get("nif");
        String urlLogo = payload.get("urlLogo");

        Instituicao atualizada = instituicaoService.atualizarInstituicao(id, nome, sigla, nif, urlLogo);
        return ResponseEntity.ok(atualizada);
    }
}

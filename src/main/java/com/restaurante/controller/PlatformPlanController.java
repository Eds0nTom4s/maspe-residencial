package com.restaurante.controller;

import com.restaurante.dto.response.ApiResponse;
import com.restaurante.dto.response.PlatformPlanResponse;
import com.restaurante.service.PlatformPlanService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/platform/plans")
@RequiredArgsConstructor
@Tag(name = "Platform Plans", description = "Catalogo administrativo de planos da plataforma")
public class PlatformPlanController {

    private final PlatformPlanService platformPlanService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<PlatformPlanResponse>>> listar(
            @RequestParam(required = false) Boolean ativo
    ) {
        return ResponseEntity.ok(ApiResponse.success("Planos da plataforma", platformPlanService.listar(ativo)));
    }
}

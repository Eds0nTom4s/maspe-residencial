package com.restaurante.controller;

import com.restaurante.dto.request.AtualizarStatusSubPedidoRequest;
import com.restaurante.dto.request.ConfigurarRotaProducaoRequest;
import com.restaurante.dto.response.ApiResponse;
import com.restaurante.dto.response.RotaProducaoResponse;
import com.restaurante.dto.response.SubPedidoProducaoResponse;
import com.restaurante.dto.response.UnidadeProducaoResponse;
import com.restaurante.model.enums.StatusSubPedido;
import com.restaurante.model.enums.TenantUserRole;
import com.restaurante.security.tenant.TenantContext;
import com.restaurante.security.tenant.TenantGuard;
import com.restaurante.service.producao.ProducaoSubPedidoService;
import com.restaurante.service.producao.RotaProducaoService;
import com.restaurante.service.producao.UnidadeProducaoService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/tenant/producao")
@RequiredArgsConstructor
@Tag(name = "Tenant - Produção", description = "Endpoints mínimos para operação de cozinha/bar (tenant-aware)")
public class TenantProducaoController {

    private final TenantGuard tenantGuard;
    private final UnidadeProducaoService unidadeProducaoService;
    private final RotaProducaoService rotaProducaoService;
    private final ProducaoSubPedidoService producaoSubPedidoService;

    @GetMapping("/unidades")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<UnidadeProducaoResponse>>> listarUnidades() {
        tenantGuard.assertAnyTenantRole(
                TenantUserRole.TENANT_OWNER,
                TenantUserRole.TENANT_ADMIN,
                TenantUserRole.TENANT_OPERATOR,
                TenantUserRole.TENANT_KITCHEN
        );
        TenantContext ctx = tenantGuard.requireContext();
        List<UnidadeProducaoResponse> resp = unidadeProducaoService.listarAtivasDoTenant(ctx.tenantId()).stream()
                .map(up -> UnidadeProducaoResponse.builder()
                        .id(up.getId())
                        .nome(up.getNome())
                        .codigo(up.getCodigo())
                        .tipo(up.getTipo())
                        .instituicaoId(up.getInstituicao() != null ? up.getInstituicao().getId() : null)
                        .unidadeAtendimentoId(up.getUnidadeAtendimento() != null ? up.getUnidadeAtendimento().getId() : null)
                        .ativo(up.getAtivo())
                        .ordem(up.getOrdem())
                        .build())
                .toList();
        return ResponseEntity.ok(ApiResponse.success("Unidades de produção", resp));
    }

    @GetMapping("/rotas")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<RotaProducaoResponse>>> listarRotas() {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN);
        TenantContext ctx = tenantGuard.requireContext();
        List<RotaProducaoResponse> resp = rotaProducaoService.listarRotasDoTenant(ctx.tenantId()).stream()
                .map(r -> RotaProducaoResponse.builder()
                        .id(r.getId())
                        .categoriaProdutoId(r.getCategoriaProduto() != null ? r.getCategoriaProduto().getId() : null)
                        .categoriaProdutoNome(r.getCategoriaProduto() != null ? r.getCategoriaProduto().getNome() : null)
                        .categoriaProdutoSlug(r.getCategoriaProduto() != null ? r.getCategoriaProduto().getSlug() : null)
                        .unidadeProducaoId(r.getUnidadeProducao() != null ? r.getUnidadeProducao().getId() : null)
                        .unidadeProducaoNome(r.getUnidadeProducao() != null ? r.getUnidadeProducao().getNome() : null)
                        .unidadeProducaoCodigo(r.getUnidadeProducao() != null ? r.getUnidadeProducao().getCodigo() : null)
                        .ativo(r.getAtivo())
                        .prioridade(r.getPrioridade())
                        .build())
                .toList();
        return ResponseEntity.ok(ApiResponse.success("Rotas de produção", resp));
    }

    @PostMapping("/rotas")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<RotaProducaoResponse>> configurarRota(@Valid @RequestBody ConfigurarRotaProducaoRequest request) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN);
        TenantContext ctx = tenantGuard.requireContext();
        var rota = rotaProducaoService.configurarRota(ctx.tenantId(), request.getCategoriaProdutoId(), request.getUnidadeProducaoId(), request.getPrioridade());
        RotaProducaoResponse resp = RotaProducaoResponse.builder()
                .id(rota.getId())
                .categoriaProdutoId(rota.getCategoriaProduto() != null ? rota.getCategoriaProduto().getId() : null)
                .categoriaProdutoNome(rota.getCategoriaProduto() != null ? rota.getCategoriaProduto().getNome() : null)
                .categoriaProdutoSlug(rota.getCategoriaProduto() != null ? rota.getCategoriaProduto().getSlug() : null)
                .unidadeProducaoId(rota.getUnidadeProducao() != null ? rota.getUnidadeProducao().getId() : null)
                .unidadeProducaoNome(rota.getUnidadeProducao() != null ? rota.getUnidadeProducao().getNome() : null)
                .unidadeProducaoCodigo(rota.getUnidadeProducao() != null ? rota.getUnidadeProducao().getCodigo() : null)
                .ativo(rota.getAtivo())
                .prioridade(rota.getPrioridade())
                .build();
        return ResponseEntity.ok(ApiResponse.success("Rota configurada", resp));
    }

    @GetMapping("/unidades/{unidadeProducaoId}/subpedidos")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<SubPedidoProducaoResponse>>> listarSubPedidos(
            @PathVariable Long unidadeProducaoId,
            @RequestParam(required = false) StatusSubPedido status
    ) {
        List<SubPedidoProducaoResponse> resp = producaoSubPedidoService.listarSubPedidosDaUnidade(unidadeProducaoId, status);
        return ResponseEntity.ok(ApiResponse.success("Subpedidos", resp));
    }

    @GetMapping("/subpedidos/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<SubPedidoProducaoResponse>> detalhe(@PathVariable Long id) {
        SubPedidoProducaoResponse resp = producaoSubPedidoService.buscarDetalhe(id);
        return ResponseEntity.ok(ApiResponse.success("Subpedido", resp));
    }

    @PatchMapping("/subpedidos/{id}/status")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<SubPedidoProducaoResponse>> atualizarStatus(
            @PathVariable Long id,
            @Valid @RequestBody AtualizarStatusSubPedidoRequest request,
            jakarta.servlet.http.HttpServletRequest http
    ) {
        // RBAC/tenant-scope e transições são validados no service.
        // Captura ip/ua para event log operacional.
        String ip = http != null ? http.getRemoteAddr() : null;
        String ua = http != null ? http.getHeader("User-Agent") : null;
        SubPedidoProducaoResponse resp = producaoSubPedidoService.atualizarStatus(
                id,
                request.getStatus(),
                request.getMotivo(),
                ip,
                ua
        );
        return ResponseEntity.ok(ApiResponse.success("Status atualizado", resp));
    }
}

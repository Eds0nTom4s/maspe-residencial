package com.restaurante.controller;

import com.restaurante.consumo.participante.repository.SessaoConsumoParticipanteRepository;
import com.restaurante.dto.response.ApiResponse;
import com.restaurante.dto.response.DeviceSessaoParticipantesResponse;
import com.restaurante.exception.DeviceUnauthorizedException;
import com.restaurante.exception.ResourceNotFoundException;
import com.restaurante.model.entity.SessaoConsumo;
import com.restaurante.repository.SessaoConsumoRepository;
import com.restaurante.security.device.DevicePrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/device/sessoes-consumo/{sessaoId}/participantes")
@RequiredArgsConstructor
@Tag(name = "Device - Participantes", description = "Listagem operacional de participantes da SessaoConsumo (Prompt 41)")
public class DeviceSessaoParticipanteController {

    private final SessaoConsumoRepository sessaoConsumoRepository;
    private final SessaoConsumoParticipanteRepository participanteRepository;

    @GetMapping
    @Operation(summary = "Listar participantes (resumo) de uma sessão")
    public ResponseEntity<ApiResponse<DeviceSessaoParticipantesResponse>> list(@PathVariable Long sessaoId) {
        DevicePrincipal device = requireDevicePrincipal();

        SessaoConsumo sessao = sessaoConsumoRepository.findByIdAndTenantId(sessaoId, device.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("SessaoConsumo", "id", sessaoId));

        // MVP: mesma unidade obrigatória quando sessão tem unidade
        Long sessaoUnidade = sessao.getUnidadeAtendimento() != null ? sessao.getUnidadeAtendimento().getId() : null;
        if (sessaoUnidade != null && device.unidadeAtendimentoId() != null && !sessaoUnidade.equals(device.unidadeAtendimentoId())) {
            throw new ResourceNotFoundException("SessaoConsumo", "id", sessaoId);
        }

        var list = participanteRepository.listBySessao(device.tenantId(), sessaoId);
        DeviceSessaoParticipantesResponse resp = new DeviceSessaoParticipantesResponse();
        resp.setSessaoConsumoId(sessaoId);
        resp.setParticipantes(list.stream().map(p -> {
            DeviceSessaoParticipantesResponse.Item i = new DeviceSessaoParticipantesResponse.Item();
            i.setParticipanteId(p.getId());
            i.setNomeExibicao(p.getNomeExibicao());
            i.setRole(p.getRole());
            i.setStatus(p.getStatus());
            i.setJoinedAt(p.getJoinedAt());
            i.setLastActivityAt(p.getLastActivityAt());
            return i;
        }).toList());
        return ResponseEntity.ok(ApiResponse.success("Participantes", resp));
    }

    private DevicePrincipal requireDevicePrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof DevicePrincipal device)) {
            throw new DeviceUnauthorizedException("DevicePrincipal ausente.");
        }
        return device;
    }
}

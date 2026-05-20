package com.restaurante.service;

import com.restaurante.exception.BusinessException;
import com.restaurante.exception.ResourceNotFoundException;
import com.restaurante.model.entity.FundoConsumo;
import com.restaurante.model.entity.SessaoConsumo;
import com.restaurante.model.enums.OperationalEventType;
import com.restaurante.model.enums.OperationalOrigem;
import com.restaurante.repository.SessaoConsumoRepository;
import com.restaurante.security.tenant.TenantContext;
import com.restaurante.security.tenant.TenantContextHolder;
import com.restaurante.service.operacional.OperationalEventLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class ConsumoAdminService {

    private final SessaoConsumoRepository sessaoConsumoRepository;
    private final FundoConsumoService fundoConsumoService;
    private final SessaoConsumoService sessaoConsumoService;
    private final OperationalEventLogService operationalEventLogService;

    @Transactional
    public void bloquearFundo(String codigoConsumo, String motivo, String ip, String userAgent) {
        TenantContext ctx = TenantContextHolder.get().orElse(null);
        if (ctx == null || ctx.tenantId() == null) throw new ResourceNotFoundException("Recurso não encontrado.");
        FundoConsumo fundo = fundoConsumoService.bloquearFundoPorTokenTenant(ctx.tenantId(), codigoConsumo, motivo);
        operationalEventLogService.logFundoConsumoEvent(
                OperationalEventType.FUNDO_CONSUMO_BLOQUEADO,
                fundo,
                origemFromContext(ctx),
                "Fundo bloqueado",
                Map.of("codigoConsumo", codigoConsumo, "motivo", motivo),
                ip,
                userAgent
        );
    }

    @Transactional
    public void desbloquearFundo(String codigoConsumo, String motivo, String ip, String userAgent) {
        TenantContext ctx = TenantContextHolder.get().orElse(null);
        if (ctx == null || ctx.tenantId() == null) throw new ResourceNotFoundException("Recurso não encontrado.");
        FundoConsumo fundo = fundoConsumoService.desbloquearFundoPorTokenTenant(ctx.tenantId(), codigoConsumo, motivo);
        operationalEventLogService.logFundoConsumoEvent(
                OperationalEventType.FUNDO_CONSUMO_DESBLOQUEADO,
                fundo,
                origemFromContext(ctx),
                "Fundo desbloqueado",
                Map.of("codigoConsumo", codigoConsumo, "motivo", motivo),
                ip,
                userAgent
        );
    }

    @Transactional
    public void encerrarSessao(String codigoConsumo, String motivo, String ip, String userAgent) {
        TenantContext ctx = TenantContextHolder.get().orElse(null);
        if (ctx == null || ctx.tenantId() == null) throw new ResourceNotFoundException("Recurso não encontrado.");

        SessaoConsumo sessao = sessaoConsumoRepository.findByTenantIdAndQrCodeSessao(ctx.tenantId(), codigoConsumo)
                .orElseThrow(() -> new ResourceNotFoundException("Consumo não encontrado."));

        // regra: se saldo > 0, exigir OWNER/ADMIN deve ser aplicado no controller/guard; aqui reforçamos.
        var saldo = fundoConsumoService.consultarSaldoPorToken(codigoConsumo);
        if (saldo != null && saldo.signum() > 0 && ctx.roles() != null) {
            boolean ownerOrAdmin = ctx.roles().contains("TENANT_OWNER") || ctx.roles().contains("TENANT_ADMIN");
            if (!ownerOrAdmin) {
                throw new BusinessException("Encerramento com saldo positivo exige OWNER/ADMIN.");
            }
        }

        sessaoConsumoService.fechar(sessao.getId());

        operationalEventLogService.logSessaoConsumoEvent(
                OperationalEventType.SESSAO_CONSUMO_ENCERRADA,
                sessao,
                origemFromContext(ctx),
                "Sessão encerrada",
                Map.of("codigoConsumo", codigoConsumo, "motivo", motivo),
                ip,
                userAgent
        );
    }

    private OperationalOrigem origemFromContext(TenantContext ctx) {
        if (ctx == null || ctx.roles() == null) return OperationalOrigem.TENANT_ADMIN;
        if (ctx.roles().contains("TENANT_OWNER")) return OperationalOrigem.TENANT_OWNER;
        if (ctx.roles().contains("TENANT_ADMIN")) return OperationalOrigem.TENANT_ADMIN;
        if (ctx.roles().contains("TENANT_OPERATOR")) return OperationalOrigem.TENANT_OPERATOR;
        if (ctx.roles().contains("TENANT_FINANCE")) return OperationalOrigem.TENANT_FINANCE;
        if (ctx.roles().contains("TENANT_CASHIER")) return OperationalOrigem.TENANT_CASHIER;
        return OperationalOrigem.TENANT_ADMIN;
    }
}

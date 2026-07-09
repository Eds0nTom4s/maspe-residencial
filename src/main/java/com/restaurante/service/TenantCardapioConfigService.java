package com.restaurante.service;

import com.restaurante.dto.request.AtualizarCardapioAparenciaRequest;
import com.restaurante.dto.request.AtualizarLimitesCardapioRequest;
import com.restaurante.dto.response.PlatformCardapioLimitsResponse;
import com.restaurante.dto.response.TenantCardapioStatusResponse;
import com.restaurante.service.storage.StorageService;
import com.restaurante.exception.BusinessException;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.TenantCardapioConfig;
import com.restaurante.model.entity.TenantLimiteOverride;
import com.restaurante.model.enums.OperationalEntityType;
import com.restaurante.model.enums.OperationalEventType;
import com.restaurante.model.enums.OperationalOrigem;
import com.restaurante.repository.CategoriaProdutoRepository;
import com.restaurante.repository.ProdutoRepository;
import com.restaurante.repository.TenantCardapioConfigRepository;
import com.restaurante.repository.TenantLimiteOverrideRepository;
import com.restaurante.repository.TenantRepository;
import com.restaurante.security.tenant.TenantContext;
import com.restaurante.security.tenant.TenantContextHolder;
import com.restaurante.service.operacional.OperationalEventLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TenantCardapioConfigService {

    private static final String CARDAPIO_INDISPONIVEL =
            "Cardápio indisponível no momento. Tente novamente mais tarde.";

    private final TenantRepository tenantRepository;
    private final TenantCardapioConfigRepository tenantCardapioConfigRepository;
    private final TenantLimiteOverrideRepository tenantLimiteOverrideRepository;
    private final CategoriaProdutoRepository categoriaProdutoRepository;
    private final ProdutoRepository produtoRepository;
    private final TenantLimitService tenantLimitService;
    private final OperationalEventLogService operationalEventLogService;
    private final StorageService storageService;

    @Transactional
    public TenantCardapioConfig getOrCreate(Tenant tenant) {
        if (tenant == null || tenant.getId() == null) {
            throw new BusinessException("Tenant não encontrado.");
        }
        return tenantCardapioConfigRepository.findByTenantId(tenant.getId()).orElseGet(() -> {
            TenantCardapioConfig config = new TenantCardapioConfig();
            config.setTenant(tenant);
            config.setCardapioPublicado(false);
            config.setCardapioAtualizadoEm(LocalDateTime.now());
            return tenantCardapioConfigRepository.saveAndFlush(config);
        });
    }

    @Transactional(readOnly = true)
    public boolean isPublicado(Long tenantId) {
        return tenantCardapioConfigRepository.existsByTenantIdAndCardapioPublicadoTrue(tenantId);
    }

    public String mensagemPublicaIndisponivel(String telefoneContato) {
        if (telefoneContato == null || telefoneContato.isBlank()) {
            return CARDAPIO_INDISPONIVEL;
        }
        return CARDAPIO_INDISPONIVEL + " Contacte: " + telefoneContato + ".";
    }

    @Transactional
    public TenantCardapioStatusResponse statusForCurrentTenant() {
        TenantContext ctx = TenantContextHolder.require();
        return status(ctx.tenantId());
    }

    @Transactional
    public TenantCardapioStatusResponse status(Long tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new BusinessException("Tenant não encontrado: " + tenantId));
        TenantCardapioConfig config = getOrCreate(tenant);
        return buildStatus(tenant, config);
    }

    @Transactional
    public TenantCardapioStatusResponse publicarCurrentTenant() {
        TenantContext ctx = TenantContextHolder.require();
        Tenant tenant = tenantRepository.findById(ctx.tenantId())
                .orElseThrow(() -> new BusinessException("Tenant não encontrado: " + ctx.tenantId()));
        TenantCardapioConfig config = getOrCreate(tenant);
        TenantCardapioStatusResponse status = buildStatus(tenant, config);
        if (!Boolean.TRUE.equals(status.getPodePublicar())) {
            throw new BusinessException("Cardápio não pode ser publicado: " + String.join("; ", status.getBloqueios()));
        }

        LocalDateTime now = LocalDateTime.now();
        config.setCardapioPublicado(true);
        config.setCardapioPublicadoEm(now);
        config.setCardapioPublicadoPorUserId(ctx.userId());
        config.setCardapioAtualizadoEm(now);
        config.setCardapioMotivoDespublicacao(null);
        tenantCardapioConfigRepository.saveAndFlush(config);

        audit(tenant.getId(), OperationalEventType.CARDAPIO_PUBLICADO, config.getId(),
                origemTenant(), "Cardápio publicado", Map.of("userId", safeUserId()));
        return buildStatus(tenant, config);
    }

    @Transactional
    public TenantCardapioStatusResponse atualizarAparenciaCurrentTenant(AtualizarCardapioAparenciaRequest request) {
        TenantContext ctx = TenantContextHolder.require();
        Tenant tenant = tenantRepository.findById(ctx.tenantId())
                .orElseThrow(() -> new BusinessException("Tenant não encontrado: " + ctx.tenantId()));
        TenantCardapioConfig config = getOrCreate(tenant);

        LocalDateTime now = LocalDateTime.now();
        if (request.getUrlBanner() != null) {
            config.setUrlBanner(trimToNull(request.getUrlBanner()));
        }
        if (request.getMaxItensPorPedido() != null) {
            config.setMaxItensPorPedido(request.getMaxItensPorPedido());
        }
        config.setCardapioAtualizadoEm(now);
        tenantCardapioConfigRepository.saveAndFlush(config);

        audit(tenant.getId(), OperationalEventType.CARDAPIO_APARENCIA_ALTERADA, config.getId(),
                origemTenant(), "Aparência do cardápio atualizada", Map.of(
                        "urlBanner", config.getUrlBanner() != null ? config.getUrlBanner() : "",
                        "maxItensPorPedido", config.getMaxItensPorPedido() != null ? config.getMaxItensPorPedido() : ""
                ));
        return buildStatus(tenant, config);
    }

    @Transactional
    public TenantCardapioStatusResponse uploadBannerCurrentTenant(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("Arquivo do banner é obrigatório.");
        }
        TenantContext ctx = TenantContextHolder.require();
        Tenant tenant = tenantRepository.findById(ctx.tenantId())
                .orElseThrow(() -> new BusinessException("Tenant não encontrado: " + ctx.tenantId()));
        TenantCardapioConfig config = getOrCreate(tenant);

        String url = storageService.uploadFile(file, "cardapio-banners");
        config.setUrlBanner(url);
        config.setCardapioAtualizadoEm(LocalDateTime.now());
        tenantCardapioConfigRepository.saveAndFlush(config);

        audit(tenant.getId(), OperationalEventType.CARDAPIO_BANNER_ALTERADO, config.getId(),
                origemTenant(), "Banner do cardápio atualizado", Map.of("url", url));
        return buildStatus(tenant, config);
    }

    @Transactional
    public TenantCardapioStatusResponse despublicarCurrentTenant(String motivo) {
        TenantContext ctx = TenantContextHolder.require();
        Tenant tenant = tenantRepository.findById(ctx.tenantId())
                .orElseThrow(() -> new BusinessException("Tenant não encontrado: " + ctx.tenantId()));
        TenantCardapioConfig config = getOrCreate(tenant);

        LocalDateTime now = LocalDateTime.now();
        config.setCardapioPublicado(false);
        config.setCardapioDespublicadoEm(now);
        config.setCardapioDespublicadoPorUserId(ctx.userId());
        config.setCardapioMotivoDespublicacao(trimToNull(motivo));
        config.setCardapioAtualizadoEm(now);
        tenantCardapioConfigRepository.saveAndFlush(config);

        audit(tenant.getId(), OperationalEventType.CARDAPIO_DESPUBLICADO, config.getId(),
                origemTenant(), "Cardápio despublicado", Map.of("motivo", motivo != null ? motivo : ""));
        return buildStatus(tenant, config);
    }

    @Transactional
    public PlatformCardapioLimitsResponse alterarLimitesPlatform(Long tenantId, AtualizarLimitesCardapioRequest request) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new BusinessException("Tenant não encontrado: " + tenantId));
        long categoriasAtuais = categoriaProdutoRepository.countByTenantIdAndAtivoTrue(tenantId);
        long produtosAtuais = produtoRepository.countByTenantIdAndAtivoTrue(tenantId);
        if (request.getMaxCategorias() != null && categoriasAtuais > request.getMaxCategorias()) {
            throw new BusinessException("Novo limite de categorias é menor que o uso atual.");
        }
        if (request.getMaxProdutos() != null && produtosAtuais > request.getMaxProdutos()) {
            throw new BusinessException("Novo limite de produtos é menor que o uso atual.");
        }

        TenantLimiteOverride override = tenantLimiteOverrideRepository.findByTenantIdAndAtivoTrue(tenantId)
                .orElseGet(() -> {
                    TenantLimiteOverride novo = new TenantLimiteOverride();
                    novo.setTenant(tenant);
                    novo.setAtivo(true);
                    return novo;
                });
        override.setMaxCategorias(request.getMaxCategorias());
        override.setMaxProdutos(request.getMaxProdutos());
        override.setMotivo(trimToNull(request.getMotivo()));
        override.setConfiguradoPor(trimToNull(request.getConfiguradoPor()) != null
                ? trimToNull(request.getConfiguradoPor())
                : String.valueOf(safeUserId()));
        override.setConfiguradoEm(LocalDateTime.now());
        tenantLimiteOverrideRepository.saveAndFlush(override);

        audit(tenantId, OperationalEventType.LIMITE_CARDAPIO_ALTERADO, override.getId(),
                OperationalOrigem.SYSTEM, "Limites de cardápio alterados", Map.of(
                        "maxCategorias", request.getMaxCategorias(),
                        "maxProdutos", request.getMaxProdutos(),
                        "categoriasAtuais", categoriasAtuais,
                        "produtosAtuais", produtosAtuais
                ));

        PlatformCardapioLimitsResponse response = new PlatformCardapioLimitsResponse();
        response.setTenantId(tenantId);
        response.setMaxCategorias(override.getMaxCategorias());
        response.setMaxProdutos(override.getMaxProdutos());
        response.setAlteradoEm(override.getConfiguradoEm());
        response.setAlteradoPor(override.getConfiguradoPor());
        response.setMotivo(override.getMotivo());
        return response;
    }

    @Transactional
    public void ensureTemplateDefaults(Tenant tenant, int maxCategorias, int maxProdutos) {
        TenantLimiteOverride override = tenantLimiteOverrideRepository.findByTenantIdAndAtivoTrue(tenant.getId())
                .orElseGet(() -> {
                    TenantLimiteOverride novo = new TenantLimiteOverride();
                    novo.setTenant(tenant);
                    novo.setAtivo(true);
                    return novo;
                });
        override.setMaxCategorias(maxCategorias);
        override.setMaxProdutos(maxProdutos);
        override.setMotivo("Limites iniciais definidos pelo template operacional");
        override.setConfiguradoPor("BUSINESS_TEMPLATE");
        override.setConfiguradoEm(LocalDateTime.now());
        tenantLimiteOverrideRepository.saveAndFlush(override);
        getOrCreate(tenant);
    }

    private TenantCardapioStatusResponse buildStatus(Tenant tenant, TenantCardapioConfig config) {
        var limits = tenantLimitService.getEffectiveLimits(tenant.getId());
        long categoriasAtuais = categoriaProdutoRepository.countByTenantIdAndAtivoTrue(tenant.getId());
        long produtosAtuais = produtoRepository.countByTenantIdAndAtivoTrue(tenant.getId());
        long produtosDisponiveis = produtoRepository.countByTenantIdAndDisponivelTrueAndAtivoTrue(tenant.getId());

        List<String> bloqueios = new ArrayList<>();
        List<String> avisos = new ArrayList<>();
        if (categoriasAtuais == 0) {
            bloqueios.add("Nenhuma categoria ativa cadastrada.");
        }
        if (produtosDisponiveis == 0) {
            bloqueios.add("Nenhum produto ativo e disponível cadastrado.");
        }
        if (limits.maxCategorias() != null && categoriasAtuais > limits.maxCategorias()) {
            bloqueios.add("Quantidade de categorias excede o limite configurado.");
        }
        if (limits.maxProdutos() != null && produtosAtuais > limits.maxProdutos()) {
            bloqueios.add("Quantidade de produtos excede o limite configurado.");
        }
        if (!Boolean.TRUE.equals(config.getCardapioPublicado())) {
            avisos.add("Cardápio ainda não publicado.");
        }

        TenantCardapioStatusResponse response = new TenantCardapioStatusResponse();
        response.setTenantId(tenant.getId());
        response.setPublicado(Boolean.TRUE.equals(config.getCardapioPublicado()));
        response.setPublicadoEm(config.getCardapioPublicadoEm());
        response.setPublicadoPorUserId(config.getCardapioPublicadoPorUserId());
        response.setDespublicadoEm(config.getCardapioDespublicadoEm());
        response.setDespublicadoPorUserId(config.getCardapioDespublicadoPorUserId());
        response.setMotivoDespublicacao(config.getCardapioMotivoDespublicacao());
        response.setMaxCategorias(limits.maxCategorias());
        response.setMaxProdutos(limits.maxProdutos());
        response.setCategoriasAtuais(categoriasAtuais);
        response.setProdutosAtuais(produtosAtuais);
        response.setProdutosDisponiveis(produtosDisponiveis);
        response.setPodePublicar(bloqueios.isEmpty());
        response.setBloqueios(bloqueios);
        response.setAvisos(avisos);
        response.setTelefoneContato(tenant.getTelefone());
        response.setUrlBanner(config.getUrlBanner());
        response.setMaxItensPorPedido(config.getMaxItensPorPedido());
        return response;
    }

    private void audit(Long tenantId, OperationalEventType eventType, Long entityId,
                       OperationalOrigem origem, String motivo, Map<String, Object> metadata) {
        operationalEventLogService.logGenericForTenant(
                tenantId,
                eventType,
                OperationalEntityType.TENANT_CARDAPIO_CONFIG,
                entityId,
                origem,
                motivo,
                metadata,
                null,
                null
        );
    }

    private OperationalOrigem origemTenant() {
        TenantContext ctx = TenantContextHolder.get().orElse(null);
        if (ctx != null && ctx.roles() != null && ctx.roles().contains("TENANT_OWNER")) {
            return OperationalOrigem.TENANT_OWNER;
        }
        return OperationalOrigem.TENANT_ADMIN;
    }

    private Long safeUserId() {
        return TenantContextHolder.get().map(TenantContext::userId).orElse(0L);
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

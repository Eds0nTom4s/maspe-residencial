package com.restaurante.service.provisioning;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.dto.request.ProvisionarTenantRequest;
import com.restaurante.model.entity.ProvisioningTemplate;
import com.restaurante.model.enums.QrCodeOperacionalTipo;
import com.restaurante.model.enums.TipoUnidadeAtendimento;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.function.Predicate;

/**
 * Calculadora pura (sem persistência) do plano de provisionamento.
 * Usada tanto no preview quanto no provisionamento real para evitar divergência.
 */
@Component
@RequiredArgsConstructor
public class ProvisioningPlanCalculator {

    private final ObjectMapper objectMapper;

    public ProvisioningPlan calculate(
            ProvisionarTenantRequest request,
            ProvisioningTemplate template,
            Predicate<String> tenantSlugExists,
            Predicate<String> tenantCodeExists
    ) {
        TemplateConfig cfg = parseTemplateConfig(template != null ? template.getConfiguracaoJson() : null);

        String slugNormalized = normalizeSlug(request != null && request.getTenant() != null ? request.getTenant().getSlug() : null);
        String tenantCodeNormalized = normalizeTenantCode(request != null && request.getTenant() != null ? request.getTenant().getTenantCode() : null);
        String tenantCodeGenerated = null;

        if (tenantCodeNormalized == null || tenantCodeNormalized.isBlank()) {
            tenantCodeGenerated = generateTenantCodeDeterministic(slugNormalized, tenantCodeExists);
        }

        boolean criarUnidade = request.getOpcoes() == null || Boolean.TRUE.equals(request.getOpcoes().getCriarUnidadeAtendimentoDefault());
        boolean criarQrPrincipal = request.getOpcoes() == null || Boolean.TRUE.equals(request.getOpcoes().getCriarQrPrincipal());
        boolean criarCategoriaDefault = request.getOpcoes() == null || Boolean.TRUE.equals(request.getOpcoes().getCriarCategoriaDefault());
        boolean ativarTenant = request.getOpcoes() == null || Boolean.TRUE.equals(request.getOpcoes().getAtivarTenant());

        boolean criarMesas = (request.getOpcoes() != null && request.getOpcoes().getCriarMesas() != null)
                ? Boolean.TRUE.equals(request.getOpcoes().getCriarMesas())
                : (cfg != null && Boolean.TRUE.equals(cfg.criarMesas));

        int quantidadeMesas = (request.getOpcoes() != null && request.getOpcoes().getQuantidadeMesas() != null)
                ? request.getOpcoes().getQuantidadeMesas()
                : (cfg != null && cfg.quantidadeMesas != null ? cfg.quantidadeMesas : 0);

        boolean criarQrPorMesa = (request.getOpcoes() != null && request.getOpcoes().getCriarQrPorMesa() != null)
                ? Boolean.TRUE.equals(request.getOpcoes().getCriarQrPorMesa())
                : (cfg != null && Boolean.TRUE.equals(cfg.criarQrPorMesa));

        String prefixoMesa = (request.getOpcoes() != null && request.getOpcoes().getPrefixoMesa() != null && !request.getOpcoes().getPrefixoMesa().isBlank())
                ? request.getOpcoes().getPrefixoMesa()
                : (cfg != null ? cfg.prefixoMesa : null);
        if (prefixoMesa == null || prefixoMesa.isBlank()) prefixoMesa = "Mesa";

        boolean criarUsuarioOwner = request.getResponsavel() != null
                && (request.getResponsavel().getCriarUsuario() == null || request.getResponsavel().getCriarUsuario());

        int unidadesNovas = criarUnidade ? 1 : 0;
        int tenantUsersNovos = criarUsuarioOwner ? 1 : 0;
        int usuariosCriados = criarUsuarioOwner ? 1 : 0; // para limites maxUsuarios (TenantUser)
        int mesasNovas = criarMesas ? Math.max(0, quantidadeMesas) : 0;
        int qrNovos = (criarQrPrincipal ? 1 : 0) + (criarQrPorMesa ? mesasNovas : 0);

        QrCodeOperacionalTipo qrPrincipalTipo = resolveQrTipoFromTemplate(cfg);
        String unidadeAtendimentoDefaultNome = resolveUnidadeAtendimentoNomeFromTemplate(cfg);
        TipoUnidadeAtendimento unidadeAtendimentoDefaultTipo = resolveUnidadeAtendimentoTipoFromTemplate(cfg);

        return new ProvisioningPlan(
                slugNormalized,
                tenantCodeNormalized,
                tenantCodeGenerated,
                tenantSlugExists != null && slugNormalized != null && tenantSlugExists.test(slugNormalized),
                tenantCodeExists != null && tenantCodeNormalized != null && !tenantCodeNormalized.isBlank() && tenantCodeExists.test(tenantCodeNormalized),
                criarUnidade,
                criarQrPrincipal,
                criarCategoriaDefault,
                ativarTenant,
                criarMesas,
                quantidadeMesas,
                criarQrPorMesa,
                prefixoMesa,
                criarUsuarioOwner,
                unidadesNovas,
                usuariosCriados,
                tenantUsersNovos,
                mesasNovas,
                qrNovos,
                qrPrincipalTipo != null ? qrPrincipalTipo.name() : null,
                unidadeAtendimentoDefaultNome,
                unidadeAtendimentoDefaultTipo != null ? unidadeAtendimentoDefaultTipo.name() : null
        );
    }

    public static String normalizeSlug(String slug) {
        if (slug == null) return null;
        String s = slug.trim().toLowerCase(Locale.ROOT);
        s = s.replaceAll("[^a-z0-9\\-]+", "-");
        s = s.replaceAll("-{2,}", "-");
        s = s.replaceAll("^-|-$", "");
        return s;
    }

    public static String normalizeTenantCode(String tenantCode) {
        if (tenantCode == null) return null;
        String s = tenantCode.trim().toUpperCase(Locale.ROOT);
        s = s.replaceAll("[^A-Z0-9]", "");
        if (s.length() > 20) s = s.substring(0, 20);
        return s;
    }

    private String generateTenantCodeDeterministic(String slugNormalized, Predicate<String> tenantCodeExists) {
        String base = slugNormalized == null ? "TNT" : slugNormalized.replaceAll("[^a-z0-9]", "");
        base = base.toUpperCase(Locale.ROOT);
        if (base.length() < 3) base = "TNT";
        if (base.length() > 5) base = base.substring(0, 5);

        String candidate = base;
        if (tenantCodeExists == null || !tenantCodeExists.test(candidate)) {
            return candidate;
        }

        for (int i = 1; i <= 999; i++) {
            String suffix = Integer.toString(i, 36).toUpperCase(Locale.ROOT);
            candidate = base + suffix;
            if (candidate.length() > 20) candidate = candidate.substring(0, 20);
            if (!tenantCodeExists.test(candidate)) {
                return candidate;
            }
        }
        // fallback: ainda determinístico
        return "TNT" + Integer.toString(Math.abs(base.hashCode()) % 46655, 36).toUpperCase(Locale.ROOT);
    }

    private TemplateConfig parseTemplateConfig(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, TemplateConfig.class);
        } catch (Exception e) {
            return null;
        }
    }

    private static QrCodeOperacionalTipo resolveQrTipoFromTemplate(TemplateConfig cfg) {
        if (cfg != null && cfg.qrPrincipal != null && cfg.qrPrincipal.tipo != null) {
            return cfg.qrPrincipal.tipo;
        }
        return QrCodeOperacionalTipo.UNIDADE_ATENDIMENTO;
    }

    private static String resolveUnidadeAtendimentoNomeFromTemplate(TemplateConfig cfg) {
        if (cfg != null && cfg.unidadeAtendimentoDefault != null && cfg.unidadeAtendimentoDefault.nome != null) {
            return cfg.unidadeAtendimentoDefault.nome;
        }
        return "Unidade Principal";
    }

    private static TipoUnidadeAtendimento resolveUnidadeAtendimentoTipoFromTemplate(TemplateConfig cfg) {
        if (cfg != null && cfg.unidadeAtendimentoDefault != null && cfg.unidadeAtendimentoDefault.tipo != null) {
            return cfg.unidadeAtendimentoDefault.tipo;
        }
        return TipoUnidadeAtendimento.RESTAURANTE;
    }

    public static class TemplateConfig {
        public UnidadeAtendimentoDefault unidadeAtendimentoDefault;
        public CategoriaDefault categoriaDefault;
        public QrPrincipal qrPrincipal;
        public Boolean criarMesas;
        public Integer quantidadeMesas;
        public Boolean criarQrPorMesa;
        public String prefixoMesa;
    }

    public static class UnidadeAtendimentoDefault {
        public String nome;
        public TipoUnidadeAtendimento tipo;
    }

    public static class CategoriaDefault {
        public String nome;
        public String slug;
    }

    public static class QrPrincipal {
        public String nome;
        public QrCodeOperacionalTipo tipo;
    }
}


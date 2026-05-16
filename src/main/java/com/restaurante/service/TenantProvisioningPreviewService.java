package com.restaurante.service;

import com.restaurante.dto.request.ProvisionarTenantRequest;
import com.restaurante.dto.response.ProvisionarTenantPreviewResponse;
import com.restaurante.model.entity.Plano;
import com.restaurante.model.entity.ProvisioningTemplate;
import com.restaurante.model.entity.User;
import com.restaurante.repository.PlanoRepository;
import com.restaurante.repository.ProvisioningTemplateRepository;
import com.restaurante.repository.TenantRepository;
import com.restaurante.repository.UserRepository;
import com.restaurante.security.tenant.TenantGuard;
import com.restaurante.service.provisioning.ProvisioningPlan;
import com.restaurante.service.provisioning.ProvisioningPlanCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class TenantProvisioningPreviewService {

    private final TenantGuard tenantGuard;
    private final TenantRepository tenantRepository;
    private final PlanoRepository planoRepository;
    private final ProvisioningTemplateRepository templateRepository;
    private final UserRepository userRepository;
    private final ProvisioningPlanCalculator planCalculator;

    @Transactional(readOnly = true)
    public ProvisionarTenantPreviewResponse preview(ProvisionarTenantRequest request) {
        tenantGuard.assertPlatformAdmin();

        ProvisionarTenantPreviewResponse.ProvisionarTenantPreviewResponseBuilder resp =
                ProvisionarTenantPreviewResponse.builder();

        if (request == null || request.getTenant() == null) {
            resp.permitido(false);
            resp.bloqueios(java.util.List.of(blocking("TENANT_DADOS_OBRIGATORIOS", "tenant", "Dados do tenant são obrigatórios.", null, "Preencha tenant.*")));
            return resp.build();
        }

        Plano plano = planoRepository.findByCodigo(request.getPlanoCodigo()).orElse(null);
        if (plano == null) {
            resp.bloqueios(add(resp.build().getBloqueios(), blocking("PLANO_INEXISTENTE", "planoCodigo",
                    "Plano inexistente: " + request.getPlanoCodigo(), null, "Informe um plano válido.")));
            return finalizeResponse(resp, request, null, null, null, null);
        }
        if (Boolean.FALSE.equals(plano.getAtivo())) {
            resp.bloqueios(add(resp.build().getBloqueios(), blocking("PLANO_INATIVO", "planoCodigo",
                    "Plano inativo: " + plano.getCodigo(), null, "Ative o plano ou selecione outro.")));
            return finalizeResponse(resp, request, plano, null, null, null);
        }

        ProvisioningTemplate template = templateRepository.findByCodigoAndAtivoTrue(request.getTemplateCodigo()).orElse(null);
        if (template == null) {
            resp.bloqueios(add(resp.build().getBloqueios(), blocking("TEMPLATE_INEXISTENTE_OU_INATIVO", "templateCodigo",
                    "Template inválido/inativo: " + request.getTemplateCodigo(), null, "Informe um template ativo.")));
            return finalizeResponse(resp, request, plano, null, null, null);
        }

        ProvisioningPlan plan = planCalculator.calculate(
                request,
                template,
                tenantRepository::existsBySlug,
                tenantRepository::existsByTenantCode
        );

        // Validações operacionais (não-estruturais)
        if (plan.slugNormalized() == null || plan.slugNormalized().isBlank()) {
            resp.bloqueios(add(resp.build().getBloqueios(), blocking("TENANT_SLUG_INVALIDO", "tenant.slug",
                    "Slug inválido.", null, "Informe um slug não vazio.")));
        } else if (plan.slugAlreadyExists()) {
            resp.bloqueios(add(resp.build().getBloqueios(), blocking("TENANT_SLUG_DUPLICADO", "tenant.slug",
                    "Slug já existe: " + plan.slugNormalized(), null, "Escolha outro slug.")));
        }

        if (plan.tenantCodeNormalized() != null && !plan.tenantCodeNormalized().isBlank() && plan.tenantCodeAlreadyExists()) {
            resp.bloqueios(add(resp.build().getBloqueios(), blocking("TENANT_CODE_DUPLICADO", "tenant.tenantCode",
                    "tenantCode já existe: " + plan.tenantCodeNormalized(), null, "Escolha outro tenantCode.")));
        }
        if (plan.tenantCodeNormalized() == null || plan.tenantCodeNormalized().isBlank()) {
            resp.avisos(add(resp.build().getAvisos(), warning("TENANT_CODE_SERA_GERADO", "tenant.tenantCode",
                    "tenantCode será gerado automaticamente.", "Sugestão: " + plan.tenantCodeGenerated(), "Revise o tenantCode sugerido.")));
        }

        // Mesas
        if (plan.quantidadeMesas() < 0) {
            resp.bloqueios(add(resp.build().getBloqueios(), blocking("QUANTIDADE_MESAS_INVALIDA", "opcoes.quantidadeMesas",
                    "quantidadeMesas inválida.", null, "Informe quantidadeMesas >= 0.")));
        }
        if (plan.quantidadeMesas() > 200) {
            resp.bloqueios(add(resp.build().getBloqueios(), blocking("QUANTIDADE_MESAS_EXCEDE_TETO", "opcoes.quantidadeMesas",
                    "quantidadeMesas excede teto técnico (200).", null, "Reduza quantidadeMesas para <= 200.")));
        }
        if (plan.criarQrPorMesa() && !plan.criarMesas()) {
            resp.bloqueios(add(resp.build().getBloqueios(), blocking("CRIAR_QR_POR_MESA_SEM_MESAS", "opcoes.criarQrPorMesa",
                    "criarQrPorMesa exige criarMesas=true.", null, "Habilite criarMesas e defina quantidadeMesas > 0.")));
        }

        // Owner
        boolean criarOwner = plan.criarUsuarioOwner();
        boolean ownerIdentOk = false;
        boolean ownerExistente = false;
        if (criarOwner) {
            if (request.getResponsavel() == null) {
                resp.bloqueios(add(resp.build().getBloqueios(), blocking("OWNER_DADOS_OBRIGATORIOS", "responsavel",
                        "responsavel é obrigatório quando criarUsuario=true.", null, "Preencha responsavel.* ou defina criarUsuario=false.")));
            } else {
                String email = request.getResponsavel().getEmail();
                String telefone = request.getResponsavel().getTelefone();
                ownerIdentOk = (email != null && !email.isBlank()) || (telefone != null && !telefone.isBlank());
                if (!ownerIdentOk) {
                    resp.bloqueios(add(resp.build().getBloqueios(), blocking("OWNER_IDENTIFICACAO_OBRIGATORIA", "responsavel.email/telefone",
                            "Email ou telefone do responsável é obrigatório.", null, "Informe pelo menos email ou telefone.")));
                }
                if (email != null && !email.isBlank()) {
                    User u = userRepository.findByEmail(email).orElse(null);
                    if (u != null) ownerExistente = true;
                }
                if (!ownerExistente && telefone != null && !telefone.isBlank()) {
                    User u = userRepository.findByTelefone(telefone).orElse(null);
                    if (u != null) ownerExistente = true;
                }
                if (ownerExistente) {
                    resp.avisos(add(resp.build().getAvisos(), warning("OWNER_SERA_REUTILIZADO", "responsavel.email/telefone",
                            "Usuário do responsável já existe e será reutilizado.", null, "Verifique se o usuário existente deve ser o OWNER deste tenant.")));
                }
                if (request.getResponsavel().getSenhaTemporaria() == null || request.getResponsavel().getSenhaTemporaria().isBlank()) {
                    resp.avisos(add(resp.build().getAvisos(), warning("SENHA_TEMPORARIA_SERA_GERADA", "responsavel.senhaTemporaria",
                            "Senha temporária será gerada automaticamente.", null, "Registre a senha gerada com segurança ou defina uma senha temporária.")));
                }
            }
        }

        // Limites: para tenant novo, usadoAtualmente = 0
        EffectiveLimits effective = computeEffectiveLimits(plano, request.getLimitesOverride());
        ProvisionarTenantPreviewResponse.ProvisioningLimitsPreviewDTO limitsDto = new ProvisionarTenantPreviewResponse.ProvisioningLimitsPreviewDTO();

        boolean precisaOverride = false;
        // Instituicoes: sempre 1
        limitsDto.getLinhas().add(limitLine("instituicoes", effective.maxInstituicoes, 0, 1));
        // Unidades: 1 se criarUnidade
        limitsDto.getLinhas().add(limitLine("unidadesAtendimento", effective.maxUnidadesAtendimento, 0, plan.unidadesNovas()));
        // Usuarios (TenantUsers): 1 se criarOwner
        limitsDto.getLinhas().add(limitLine("usuarios", effective.maxUsuarios, 0, plan.usuariosNovos()));
        // QR Codes
        limitsDto.getLinhas().add(limitLine("qrCodes", effective.maxQrCodes, 0, plan.qrNovos()));

        for (ProvisionarTenantPreviewResponse.ProvisioningLimitsPreviewDTO.LimitLine l : limitsDto.getLinhas()) {
            if (l.isExcede()) {
                precisaOverride = true;
                resp.bloqueios(add(resp.build().getBloqueios(), blocking(mapLimitCode(l.getRecurso()), "limites",
                        "Limite excedido para " + l.getRecurso() + ".", "Limite=" + l.getLimite() + " novo=" + l.getNovo(), "Ajuste o template/opções ou aplique override de limites.")));
            }
        }

        resp.limites(limitsDto);

        return finalizeResponse(resp, request, plano, template, plan, precisaOverride);
    }

    private ProvisionarTenantPreviewResponse finalizeResponse(
            ProvisionarTenantPreviewResponse.ProvisionarTenantPreviewResponseBuilder resp,
            ProvisionarTenantRequest request,
            Plano plano,
            ProvisioningTemplate template,
            ProvisioningPlan plan,
            Boolean precisaOverride
    ) {
        // template preview
        if (template != null && plan != null) {
            resp.template(ProvisionarTenantPreviewResponse.ProvisioningTemplatePreviewDTO.builder()
                    .codigo(template.getCodigo())
                    .nome(template.getNome())
                    .tipoTenant(template.getTipoTenant() != null ? template.getTipoTenant().name() : null)
                    .criarMesas(plan.criarMesas())
                    .quantidadeMesas(Math.max(0, plan.quantidadeMesas()))
                    .criarQrPorMesa(plan.criarQrPorMesa())
                    .prefixoMesa(plan.prefixoMesa())
                    .unidadeAtendimentoDefaultNome(plan.unidadeAtendimentoDefaultNome())
                    .qrPrincipalTipo(plan.qrPrincipalTipo())
                    .build());
        }

        // tenant preview
        if (plan != null) {
            resp.tenant(ProvisionarTenantPreviewResponse.TenantPreviewDTO.builder()
                    .nome(request.getTenant().getNome())
                    .slugNormalizado(plan.slugNormalized())
                    .tenantCodeNormalizado(plan.tenantCodeNormalized())
                    .tenantCodeGerado(plan.tenantCodeGenerated())
                    .tipo(request.getTenant().getTipo() != null ? request.getTenant().getTipo().name() : null)
                    .ativarTenant(plan.ativarTenant())
                    .build());
        }

        // instituicao preview
        if (request.getInstituicao() != null) {
            resp.instituicao(ProvisionarTenantPreviewResponse.InstituicaoPreviewDTO.builder()
                    .nome(request.getInstituicao().getNome())
                    .sigla(request.getInstituicao().getSigla())
                    .ativa(true)
                    .build());
        }

        // owner preview
        boolean criarOwner = plan != null && plan.criarUsuarioOwner();
        if (request.getResponsavel() != null || criarOwner) {
            boolean ownerExistente = false;
            if (criarOwner && request.getResponsavel() != null) {
                String email = request.getResponsavel().getEmail();
                String telefone = request.getResponsavel().getTelefone();
                if (email != null && !email.isBlank() && userRepository.findByEmail(email).isPresent()) ownerExistente = true;
                if (!ownerExistente && telefone != null && !telefone.isBlank() && userRepository.findByTelefone(telefone).isPresent()) ownerExistente = true;
            }
            resp.owner(ProvisionarTenantPreviewResponse.OwnerPreviewDTO.builder()
                    .criarUsuario(criarOwner)
                    .nome(request.getResponsavel() != null ? request.getResponsavel().getNome() : null)
                    .email(request.getResponsavel() != null ? request.getResponsavel().getEmail() : null)
                    .telefone(request.getResponsavel() != null ? request.getResponsavel().getTelefone() : null)
                    .ownerExistente(ownerExistente)
                    .ownerSeraReutilizado(ownerExistente)
                    .build());
        }

        // recursos planejados
        if (plan != null) {
            boolean overrideLimitesCriado = request.getLimitesOverride() != null
                    && (request.getLimitesOverride().getMaxInstituicoes() != null
                    || request.getLimitesOverride().getMaxUnidadesAtendimento() != null
                    || request.getLimitesOverride().getMaxUsuarios() != null
                    || request.getLimitesOverride().getMaxQrCodes() != null
                    || request.getLimitesOverride().getMaxDispositivos() != null);

            int qrPrincipal = plan.criarQrPrincipal() ? 1 : 0;
            int qrMesa = plan.criarQrPorMesa() ? Math.max(0, plan.quantidadeMesas()) : 0;

            resp.recursosPlanejados(ProvisionarTenantPreviewResponse.ProvisioningResourcePlanDTO.builder()
                    .tenantsCriados(1)
                    .instituicoesCriadas(1)
                    .unidadesAtendimentoCriadas(plan.unidadesNovas())
                    .categoriasCriadas(plan.criarCategoriaDefault() ? 1 : 0)
                    .usuariosCriados(plan.usuariosNovos())
                    .tenantUsersCriados(plan.tenantUsersNovos())
                    .mesasCriadas(plan.criarMesas() ? Math.max(0, plan.quantidadeMesas()) : 0)
                    .qrCodesCriados(plan.qrNovos())
                    .qrPrincipalCriado(plan.criarQrPrincipal())
                    .qrPorMesaCriados(qrMesa)
                    .overrideLimitesCriado(overrideLimitesCriado)
                    .build());

            resp.estimativa(ProvisionarTenantPreviewResponse.ProvisioningEstimateDTO.builder()
                    .totalMesas(plan.criarMesas() ? Math.max(0, plan.quantidadeMesas()) : 0)
                    .totalQrUrlsGeradas(qrPrincipal + qrMesa)
                    .precisaOverride(Boolean.TRUE.equals(precisaOverride))
                    .prontoParaExecutar(resp.build().getBloqueios().isEmpty())
                    .build());
        }

        // permitido
        resp.permitido(resp.build().getBloqueios().isEmpty());
        return resp.build();
    }

    private static ProvisionarTenantPreviewResponse.ProvisioningValidationMessage blocking(
            String code, String field, String msg, String detail, String action
    ) {
        return ProvisionarTenantPreviewResponse.ProvisioningValidationMessage.builder()
                .codigo(code)
                .severidade(ProvisionarTenantPreviewResponse.Severity.BLOCKING)
                .campo(field)
                .mensagem(msg)
                .detalhe(detail)
                .acaoRecomendada(action)
                .build();
    }

    private static ProvisionarTenantPreviewResponse.ProvisioningValidationMessage warning(
            String code, String field, String msg, String detail, String action
    ) {
        return ProvisionarTenantPreviewResponse.ProvisioningValidationMessage.builder()
                .codigo(code)
                .severidade(ProvisionarTenantPreviewResponse.Severity.WARNING)
                .campo(field)
                .mensagem(msg)
                .detalhe(detail)
                .acaoRecomendada(action)
                .build();
    }

    private static java.util.List<ProvisionarTenantPreviewResponse.ProvisioningValidationMessage> add(
            java.util.List<ProvisionarTenantPreviewResponse.ProvisioningValidationMessage> list,
            ProvisionarTenantPreviewResponse.ProvisioningValidationMessage msg
    ) {
        java.util.ArrayList<ProvisionarTenantPreviewResponse.ProvisioningValidationMessage> out = new java.util.ArrayList<>();
        if (list != null) out.addAll(list);
        out.add(msg);
        return out;
    }

    private static ProvisionarTenantPreviewResponse.ProvisioningLimitsPreviewDTO.LimitLine limitLine(
            String recurso, Integer limite, long usadoAtualmente, int novo
    ) {
        long total = usadoAtualmente + Math.max(0, novo);
        boolean excede = limite != null && total > limite;
        return ProvisionarTenantPreviewResponse.ProvisioningLimitsPreviewDTO.LimitLine.builder()
                .recurso(recurso)
                .limite(limite)
                .usadoAtualmente(usadoAtualmente)
                .novo(Math.max(0, novo))
                .totalAposProvisionamento(total)
                .excede(excede)
                .build();
    }

    private static String mapLimitCode(String recurso) {
        return switch (Objects.requireNonNullElse(recurso, "")) {
            case "qrCodes" -> "MAX_QR_CODES_EXCEDIDO";
            case "usuarios" -> "MAX_USUARIOS_EXCEDIDO";
            case "unidadesAtendimento" -> "MAX_UNIDADES_EXCEDIDO";
            case "instituicoes" -> "MAX_INSTITUICOES_EXCEDIDO";
            default -> "LIMITE_EXCEDIDO";
        };
    }

    private static EffectiveLimits computeEffectiveLimits(Plano plano, ProvisionarTenantRequest.LimitesOverride override) {
        Integer maxInstituicoes = pick(override != null ? override.getMaxInstituicoes() : null, plano.getMaxInstituicoes());
        Integer maxUnidades = pick(override != null ? override.getMaxUnidadesAtendimento() : null, plano.getMaxUnidadesAtendimento());
        Integer maxUsuarios = pick(override != null ? override.getMaxUsuarios() : null, plano.getMaxUsuarios());
        Integer maxQr = pick(override != null ? override.getMaxQrCodes() : null, plano.getMaxQrCodes());
        return new EffectiveLimits(maxInstituicoes, maxUnidades, maxUsuarios, maxQr);
    }

    private static Integer pick(Integer overrideValue, Integer planoValue) {
        return overrideValue != null ? overrideValue : planoValue;
    }

    private record EffectiveLimits(
            Integer maxInstituicoes,
            Integer maxUnidadesAtendimento,
            Integer maxUsuarios,
            Integer maxQrCodes
    ) {}
}


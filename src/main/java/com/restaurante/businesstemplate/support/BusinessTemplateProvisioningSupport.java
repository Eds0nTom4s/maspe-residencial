package com.restaurante.businesstemplate.support;

import com.restaurante.businesstemplate.dto.BusinessTemplateProvisionRequest;
import com.restaurante.exception.BusinessException;
import com.restaurante.exception.ProvisioningException;
import com.restaurante.financeiro.paymentmethod.service.TenantPaymentMethodBootstrapService;
import com.restaurante.inventory.service.TenantInventoryPolicyService;
import com.restaurante.model.entity.BusinessAccount;
import com.restaurante.model.entity.CategoriaProduto;
import com.restaurante.model.entity.ChecklistOperacionalItemTemplate;
import com.restaurante.model.entity.ChecklistOperacionalTemplate;
import com.restaurante.model.entity.DispositivoOperacional;
import com.restaurante.model.entity.Instituicao;
import com.restaurante.model.entity.Mesa;
import com.restaurante.model.entity.Plano;
import com.restaurante.model.entity.Produto;
import com.restaurante.model.entity.QrCodeOperacional;
import com.restaurante.model.entity.Subscricao;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.TenantDeliveryPolicy;
import com.restaurante.model.entity.TenantOperacaoPolicy;
import com.restaurante.model.entity.TenantUser;
import com.restaurante.model.entity.UnidadeAtendimento;
import com.restaurante.model.entity.UnidadeProducao;
import com.restaurante.model.enums.ChecklistEscopo;
import com.restaurante.model.enums.ChecklistTipo;
import com.restaurante.model.enums.ChecklistTipoResposta;
import com.restaurante.model.enums.CategoriaProdutoLegacy;
import com.restaurante.model.enums.DeliveryMode;
import com.restaurante.model.enums.DispositivoStatus;
import com.restaurante.model.enums.DispositivoTipo;
import com.restaurante.model.enums.LogisticsMode;
import com.restaurante.model.enums.OperationalDeviceType;
import com.restaurante.model.enums.OperationalEntityType;
import com.restaurante.model.enums.OperationalEventType;
import com.restaurante.model.enums.OperationalOrigem;
import com.restaurante.model.enums.QrCodeOperacionalTipo;
import com.restaurante.model.enums.Role;
import com.restaurante.model.enums.SubscricaoEstado;
import com.restaurante.model.enums.TenantEstado;
import com.restaurante.model.enums.TenantUserEstado;
import com.restaurante.model.enums.TenantUserRole;
import com.restaurante.model.enums.TipoUnidadeAtendimento;
import com.restaurante.model.enums.TipoUnidadeConsumo;
import com.restaurante.model.enums.TipoSessao;
import com.restaurante.model.enums.UnidadeProducaoTipo;
import com.restaurante.repository.BusinessAccountRepository;
import com.restaurante.repository.CategoriaProdutoRepository;
import com.restaurante.repository.ChecklistOperacionalItemTemplateRepository;
import com.restaurante.repository.ChecklistOperacionalTemplateRepository;
import com.restaurante.repository.DispositivoOperacionalRepository;
import com.restaurante.repository.InstituicaoRepository;
import com.restaurante.repository.MesaRepository;
import com.restaurante.repository.PlanoRepository;
import com.restaurante.repository.ProdutoRepository;
import com.restaurante.repository.SubscricaoRepository;
import com.restaurante.delivery.repository.TenantDeliveryPolicyRepository;
import com.restaurante.repository.TenantOperacaoPolicyRepository;
import com.restaurante.repository.TenantRepository;
import com.restaurante.repository.TenantUserRepository;
import com.restaurante.repository.UnidadeAtendimentoRepository;
import com.restaurante.repository.UnidadeProducaoRepository;
import com.restaurante.repository.UserRepository;
import com.restaurante.security.tenant.TenantContext;
import com.restaurante.security.tenant.TenantContextHolder;
import com.restaurante.service.QrCodeOperacionalService;
import com.restaurante.service.TenantCardapioConfigService;
import com.restaurante.service.TenantLimitService;
import com.restaurante.service.TenantOperationalModulesService;
import com.restaurante.service.TenantSessaoConsumoConfigService;
import com.restaurante.service.operacional.OperationalEventLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class BusinessTemplateProvisioningSupport {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final char[] CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

    private final TenantRepository tenantRepository;
    private final BusinessAccountRepository businessAccountRepository;
    private final PlanoRepository planoRepository;
    private final SubscricaoRepository subscricaoRepository;
    private final InstituicaoRepository instituicaoRepository;
    private final UnidadeAtendimentoRepository unidadeAtendimentoRepository;
    private final CategoriaProdutoRepository categoriaProdutoRepository;
    private final ProdutoRepository produtoRepository;
    private final UserRepository userRepository;
    private final TenantUserRepository tenantUserRepository;
    private final MesaRepository mesaRepository;
    private final QrCodeOperacionalService qrCodeOperacionalService;
    private final TenantLimitService tenantLimitService;
    private final TenantDeliveryPolicyRepository tenantDeliveryPolicyRepository;
    private final TenantOperacaoPolicyRepository tenantOperacaoPolicyRepository;
    private final UnidadeProducaoRepository unidadeProducaoRepository;
    private final DispositivoOperacionalRepository dispositivoOperacionalRepository;
    private final ChecklistOperacionalTemplateRepository checklistTemplateRepository;
    private final ChecklistOperacionalItemTemplateRepository checklistItemTemplateRepository;
    private final TenantPaymentMethodBootstrapService paymentMethodBootstrapService;
    private final TenantInventoryPolicyService tenantInventoryPolicyService;
    private final PasswordEncoder passwordEncoder;
    private final OperationalEventLogService operationalEventLogService;
    private final TenantCardapioConfigService tenantCardapioConfigService;
    private final TenantOperationalModulesService tenantOperationalModulesService;
    private final TenantSessaoConsumoConfigService tenantSessaoConsumoConfigService;

    @Value("${consuma.public-base-url:http://localhost:8080}")
    private String publicBaseUrl;

    public Plano resolvePlanoOrDefault(String planoCodigo) {
        String code = (planoCodigo == null || planoCodigo.isBlank()) ? "PILOTO" : planoCodigo.trim().toUpperCase(Locale.ROOT);
        return planoRepository.findByCodigo(code)
                .filter(Plano::getAtivo)
                .orElseThrow(() -> new ProvisioningException(HttpStatus.BAD_REQUEST, "PLANO_INVALIDO", "planoCodigo",
                        "Plano inválido/inativo: " + code, null, "Informe um plano ativo.", null));
    }

    public String normalizeSlug(String slug) {
        if (slug == null) return null;
        String s = slug.trim().toLowerCase(Locale.ROOT);
        s = s.replaceAll("[^a-z0-9\\-]+", "-");
        s = s.replaceAll("-{2,}", "-");
        s = s.replaceAll("^-|-$", "");
        return s;
    }

    public String normalizeTenantCode(String tenantCode) {
        if (tenantCode == null) return null;
        String s = tenantCode.trim().toUpperCase(Locale.ROOT);
        s = s.replaceAll("[^A-Z0-9]", "");
        if (s.length() > 20) s = s.substring(0, 20);
        return s;
    }

    public String gerarTenantCodeUnico(String slugNormalized) {
        String base = slugNormalized == null ? "TNT" : slugNormalized.replaceAll("[^a-z0-9]", "");
        base = base.toUpperCase(Locale.ROOT);
        if (base.length() < 3) base = "TNT";
        if (base.length() > 5) base = base.substring(0, 5);

        String candidate = base;
        int attempt = 0;
        while (tenantRepository.existsByTenantCode(candidate)) {
            attempt++;
            candidate = base + randomCode(3);
            if (candidate.length() > 20) candidate = candidate.substring(0, 20);
            if (attempt > 20) {
                candidate = "T" + randomCode(6);
                if (!tenantRepository.existsByTenantCode(candidate)) break;
            }
        }
        return candidate;
    }

    private static String randomCode(int len) {
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append(CODE_ALPHABET[SECURE_RANDOM.nextInt(CODE_ALPHABET.length)]);
        }
        return sb.toString();
    }

    public String buildPublicQrUrl(String token) {
        String base = publicBaseUrl != null ? publicBaseUrl.trim() : "http://localhost:8080";
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        return base + "/q/" + token;
    }

    public void assertOwnerHasContact(BusinessTemplateProvisionRequest.OwnerInfo owner) {
        if (owner == null) {
            throw new ProvisioningException(HttpStatus.BAD_REQUEST, "OWNER_DADOS_OBRIGATORIOS", "owner",
                    "Owner é obrigatório.", null, "Preencha owner.*", null);
        }
        boolean hasPhone = owner.getTelefone() != null && !owner.getTelefone().isBlank();
        boolean hasEmail = owner.getEmail() != null && !owner.getEmail().isBlank();
        if (!hasPhone && !hasEmail) {
            throw new ProvisioningException(HttpStatus.BAD_REQUEST, "OWNER_CONTACTO_OBRIGATORIO", "owner",
                    "Owner deve conter email ou telefone.", null, "Informe owner.email ou owner.telefone.", null);
        }
    }

    public boolean tenantSlugExists(String slugNormalized) {
        if (slugNormalized == null || slugNormalized.isBlank()) return false;
        return tenantRepository.existsBySlug(slugNormalized);
    }

    public boolean tenantCodeExists(String tenantCodeNormalized) {
        if (tenantCodeNormalized == null || tenantCodeNormalized.isBlank()) return false;
        return tenantRepository.existsByTenantCode(tenantCodeNormalized);
    }

    public void assertSlugUnique(String slugNormalized) {
        if (slugNormalized == null || slugNormalized.isBlank()) {
            throw new ProvisioningException(HttpStatus.BAD_REQUEST, "TENANT_SLUG_INVALIDO", "tenant.slug",
                    "Slug inválido.", null, "Informe um slug não vazio.", null);
        }
        if (tenantRepository.existsBySlug(slugNormalized)) {
            throw new ProvisioningException(HttpStatus.CONFLICT, "TENANT_SLUG_DUPLICADO", "tenant.slug",
                    "Slug já existe: " + slugNormalized, null, "Escolha outro slug.", null);
        }
    }

    public void assertTenantCodeUnique(String tenantCodeNormalized) {
        if (tenantCodeNormalized != null && !tenantCodeNormalized.isBlank() && tenantRepository.existsByTenantCode(tenantCodeNormalized)) {
            throw new ProvisioningException(HttpStatus.CONFLICT, "TENANT_CODE_DUPLICADO", "tenant.tenantCode",
                    "tenantCode já existe: " + tenantCodeNormalized, null, "Escolha outro tenantCode.", null);
        }
    }

    @Transactional
    public Tenant criarTenant(BusinessTemplateProvisionRequest.TenantInfo req,
                              String slugNormalized,
                              String tenantCodeNormalized,
                              String templateCode,
                              int templateVersion,
                              String provisioningSource,
                              Long businessAccountId) {
        BusinessAccount businessAccount = resolveBusinessAccount(businessAccountId);
        Tenant t = new Tenant();
        t.setNome(req.getNomeNegocio());
        t.setSlug(slugNormalized);
        t.setTenantCode(tenantCodeNormalized);
        t.setTipo(req.getTipo());
        t.setEstado(TenantEstado.ATIVO);
        t.setBusinessAccount(businessAccount);
        t.setNif(req.getNif());
        t.setTelefone(req.getTelefone());
        t.setEmail(req.getEmail());

        t.setTemplateCode(templateCode);
        t.setTemplateVersion(templateVersion);
        t.setProvisionedAt(LocalDateTime.now());
        TenantContext ctx = null;
        try { ctx = TenantContextHolder.require(); } catch (Exception ignored) { }
        t.setProvisionedBy(ctx != null && ctx.userId() != null ? String.valueOf(ctx.userId()) : null);
        t.setProvisioningSource(provisioningSource);

        return tenantRepository.saveAndFlush(t);
    }

    public BusinessAccount resolveBusinessAccount(Long businessAccountId) {
        if (businessAccountId == null) {
            return null;
        }
        return businessAccountRepository.findById(businessAccountId)
                .orElseThrow(() -> new ProvisioningException(
                        HttpStatus.BAD_REQUEST,
                        "BUSINESS_ACCOUNT_INEXISTENTE",
                        "businessAccountId",
                        "BusinessAccount inexistente: " + businessAccountId,
                        null,
                        "Informe uma BusinessAccount válida ou omita o campo.",
                        null
                ));
    }

    @Transactional
    public Subscricao criarSubscricaoAtiva(Tenant tenant, Plano plano) {
        Subscricao s = new Subscricao();
        s.setTenant(tenant);
        s.setPlano(plano);
        s.setEstado(SubscricaoEstado.ATIVA);
        s.setInicioEm(LocalDate.now());
        s.setRenovacaoAutomatica(false);
        return subscricaoRepository.saveAndFlush(s);
    }

    public Instituicao criarInstituicaoDefault(Tenant tenant, BusinessTemplateProvisionRequest request) {
        String siglaBase = tenant.getTenantCode();
        if (siglaBase == null || siglaBase.isBlank()) siglaBase = "TNT";
        siglaBase = siglaBase.trim().toUpperCase(Locale.ROOT);
        if (siglaBase.length() > 10) siglaBase = siglaBase.substring(0, 10);

        String sigla = siglaBase;
        int attempt = 0;
        while (instituicaoRepository.findBySigla(sigla).isPresent()) {
            attempt++;
            sigla = siglaBase.substring(0, Math.min(siglaBase.length(), 7)) + randomCode(3);
            if (attempt > 30) {
                throw new ProvisioningException(HttpStatus.CONFLICT, "INSTITUICAO_SIGLA_DUPLICADA", "instituicao.sigla",
                        "Não foi possível gerar sigla única.", null, "Escolha outro slug/tenantCode.", null);
            }
        }

        String nif = request.getTenant() != null ? request.getTenant().getNif() : null;
        if (nif == null || nif.isBlank()) {
            String nifBase = "NIF-" + tenant.getTenantCode() + "-";
            nif = nifBase + randomCode(6);
            int nifAttempt = 0;
            while (instituicaoRepository.existsByNif(nif)) {
                nifAttempt++;
                nif = nifBase + randomCode(6);
                if (nifAttempt > 30) {
                    throw new ProvisioningException(HttpStatus.CONFLICT, "INSTITUICAO_NIF_DUPLICADO", "instituicao.nif",
                            "Não foi possível gerar NIF único.", null, "Informe um NIF explícito ou tente novamente.", null);
                }
            }
        }

        String telefoneAut = null;
        if (request.getTenant() != null) telefoneAut = request.getTenant().getTelefone();
        if ((telefoneAut == null || telefoneAut.isBlank()) && request.getOwner() != null) telefoneAut = request.getOwner().getTelefone();
        if (telefoneAut == null || telefoneAut.isBlank()) telefoneAut = "+244000000000";

        Instituicao inst = new Instituicao();
        inst.setTenant(tenant);
        inst.setNome(request.getTenant().getNomeNegocio());
        inst.setSigla(sigla);
        inst.setNif(nif);
        inst.setTelefoneAutorizacao(telefoneAut);
        inst.setAtiva(true);
        return instituicaoRepository.saveAndFlush(inst);
    }

    public UnidadeAtendimento criarUnidadeAtendimentoPrincipal(Instituicao instituicao, String nome, TipoUnidadeAtendimento tipo) {
        UnidadeAtendimento ua = new UnidadeAtendimento();
        ua.setInstituicao(instituicao);
        ua.setNome(nome != null && !nome.isBlank() ? nome : "Unidade Principal");
        ua.setTipo(tipo != null ? tipo : TipoUnidadeAtendimento.RESTAURANTE);
        ua.setAtiva(true);
        return unidadeAtendimentoRepository.saveAndFlush(ua);
    }

    public List<CategoriaProduto> criarCategorias(Tenant tenant, List<String> nomes, List<String> slugs) {
        List<CategoriaProduto> out = new ArrayList<>();
        for (int i = 0; i < nomes.size(); i++) {
            String nome = nomes.get(i);
            String slug = slugs.get(i);
            String slugNorm = normalizeSlug(slug);
            Optional<CategoriaProduto> existing = categoriaProdutoRepository.findBySlugAndTenantId(slugNorm, tenant.getId());
            if (existing.isPresent()) {
                out.add(existing.get());
                continue;
            }
            CategoriaProduto c = new CategoriaProduto();
            c.setTenant(tenant);
            c.setNome(nome);
            c.setSlug(slugNorm);
            c.setOrdem(i);
            c.setAtivo(true);
            out.add(categoriaProdutoRepository.saveAndFlush(c));
        }
        return out;
    }

    public List<Produto> criarProdutos(Tenant tenant, List<ProdutoTemplateSeed> seeds) {
        List<Produto> out = new ArrayList<>();
        for (ProdutoTemplateSeed seed : seeds) {
            Optional<Produto> existing = produtoRepository.findByCodigoAndTenantId(seed.codigo(), tenant.getId());
            if (existing.isPresent()) {
                out.add(existing.get());
                continue;
            }
            CategoriaProduto categoria = categoriaProdutoRepository
                    .findBySlugAndTenantId(normalizeSlug(seed.categoriaSlug()), tenant.getId())
                    .orElseThrow(() -> new ProvisioningException(HttpStatus.INTERNAL_SERVER_ERROR,
                            "CATEGORIA_TEMPLATE_NAO_ENCONTRADA",
                            "categoriaSlug",
                            "Categoria de template não encontrada: " + seed.categoriaSlug(),
                            null,
                            "Revise seeds do template.",
                            null));
            Produto produto = new Produto();
            produto.setTenant(tenant);
            produto.setCategoriaProduto(categoria);
            produto.setCategoria(seed.legacyCategoria() != null ? seed.legacyCategoria() : CategoriaProdutoLegacy.OUTROS);
            produto.setCodigo(seed.codigo());
            produto.setNome(seed.nome());
            produto.setDescricao(seed.descricao());
            produto.setPreco(new BigDecimal(seed.preco()));
            produto.setTempoPreparoMinutos(seed.tempoPreparoMinutos());
            produto.setDisponivel(true);
            produto.setAtivo(true);
            out.add(produtoRepository.saveAndFlush(produto));
        }
        return out;
    }

    public void configurarCardapioInicial(Tenant tenant, int maxCategorias, int maxProdutos) {
        tenantCardapioConfigService.ensureTemplateDefaults(tenant, maxCategorias, maxProdutos);
    }

    public record ProdutoTemplateSeed(
            String categoriaSlug,
            String codigo,
            String nome,
            String descricao,
            String preco,
            CategoriaProdutoLegacy legacyCategoria,
            Integer tempoPreparoMinutos
    ) {
    }

    public com.restaurante.model.entity.User criarOuReusarOwnerUser(BusinessTemplateProvisionRequest.OwnerInfo owner, UnidadeAtendimento unidadeDefault) {
        if (owner.getExistingUserId() != null) {
            com.restaurante.model.entity.User explicit = userRepository.findById(owner.getExistingUserId())
                    .orElseThrow(() -> new ProvisioningException(HttpStatus.BAD_REQUEST,
                            "OWNER_USER_INEXISTENTE", "owner.existingUserId",
                            "Utilizador operacional explícito não encontrado.", null,
                            "Seleccione um utilizador activo da Conta Empresarial.", null));
            if (!Boolean.TRUE.equals(explicit.getAtivo())) {
                throw new ProvisioningException(HttpStatus.BAD_REQUEST,
                        "OWNER_USER_INACTIVO", "owner.existingUserId",
                        "Utilizador operacional explícito está inactivo.", null,
                        "Active ou seleccione outro utilizador.", null);
            }
            return explicit;
        }
        if (owner.getEmail() != null && !owner.getEmail().isBlank()) {
            com.restaurante.model.entity.User existing = userRepository.findByEmail(owner.getEmail()).orElse(null);
            if (existing != null) {
                return existing;
            }
        }
        if (owner.getTelefone() != null && !owner.getTelefone().isBlank()) {
            com.restaurante.model.entity.User existing = userRepository.findByTelefone(owner.getTelefone()).orElse(null);
            if (existing != null) {
                return existing;
            }
        }

        String username = gerarUsername(owner.getEmail(), owner.getTelefone());
        String rawPassword = owner.getSenhaTemporaria();
        if (rawPassword == null || rawPassword.isBlank()) {
            rawPassword = gerarSenhaTemporaria();
        }

        com.restaurante.model.entity.User u = new com.restaurante.model.entity.User();
        u.setUsername(username);
        u.setPassword(passwordEncoder.encode(rawPassword));
        u.setEmail(owner.getEmail());
        u.setNomeCompleto(owner.getNome());
        u.setTelefone(owner.getTelefone() != null && !owner.getTelefone().isBlank() ? owner.getTelefone() : "+244000000000");
        u.setUnidadeAtendimento(unidadeDefault);
        u.setRoles(Set.of(Role.ROLE_GERENTE));
        u.setAtivo(true);
        return userRepository.saveAndFlush(u);
    }

    public TenantUser criarTenantUser(Tenant tenant, com.restaurante.model.entity.User user, TenantUserRole role, UnidadeAtendimento unidadeDefault) {
        TenantUser tu = new TenantUser();
        tu.setTenant(tenant);
        tu.setUser(user);
        tu.setRole(role);
        tu.setEstado(TenantUserEstado.ATIVO);
        tu.setUnidadeAtendimentoDefault(unidadeDefault);
        return tenantUserRepository.saveAndFlush(tu);
    }

    public QrCodeOperacional criarQrPrincipal(Tenant tenant, Instituicao inst, UnidadeAtendimento unidade, QrCodeOperacionalTipo tipo, String nome) {
        return qrCodeOperacionalService.criarQr(
                tenant.getId(),
                inst.getId(),
                unidade != null ? unidade.getId() : null,
                null,
                tipo != null ? tipo : QrCodeOperacionalTipo.UNIDADE_ATENDIMENTO,
                nome != null ? nome : "QR Principal"
        );
    }

    public List<Mesa> criarMesas( Tenant tenant, Instituicao inst, UnidadeAtendimento unidade, int quantidade, String prefixoMesa) {
        List<Mesa> out = new ArrayList<>();
        for (int i = 1; i <= quantidade; i++) {
            Mesa m = new Mesa();
            m.setNumero(i);
            m.setReferencia((prefixoMesa != null && !prefixoMesa.isBlank() ? prefixoMesa : "Mesa") + " " + i);
            m.setQrCode(null);
            m.setCapacidade(null);
            m.setAtiva(true);
            m.setTipo(TipoUnidadeConsumo.MESA_FISICA);
            m.setUnidadeAtendimento(unidade);
            m.setTenant(tenant);
            m.setInstituicao(inst);
            out.add(mesaRepository.saveAndFlush(m));
        }
        return out;
    }

    public QrCodeOperacional criarQrPorMesa(Tenant tenant, Instituicao inst, UnidadeAtendimento unidade, Mesa mesa) {
        return qrCodeOperacionalService.criarQr(
                tenant.getId(),
                inst.getId(),
                unidade.getId(),
                mesa.getId(),
                QrCodeOperacionalTipo.MESA,
                "QR " + mesa.getReferencia()
        );
    }

    public TenantDeliveryPolicy upsertDeliveryPolicy(Tenant tenant,
                                                     boolean allowPickup,
                                                     boolean enableManualDelivery,
                                                     boolean enableConsumaNetwork) {
        TenantDeliveryPolicy p = tenantDeliveryPolicyRepository.findByTenantId(tenant.getId()).orElseGet(() -> {
            TenantDeliveryPolicy n = new TenantDeliveryPolicy();
            n.setTenant(tenant);
            return n;
        });

        p.setAllowCustomerPickup(allowPickup);
        p.setAcceptsTenantOwnDelivery(enableManualDelivery);
        p.setAcceptsConsumaNetwork(enableConsumaNetwork);

        boolean deliveryEnabled = enableManualDelivery || enableConsumaNetwork;
        p.setDeliveryEnabled(deliveryEnabled);

        if (!deliveryEnabled && allowPickup) {
            p.setDeliveryMode(DeliveryMode.PICKUP_ONLY);
        } else if (enableManualDelivery && enableConsumaNetwork) {
            p.setDeliveryMode(DeliveryMode.HYBRID);
        } else if (enableConsumaNetwork) {
            p.setDeliveryMode(DeliveryMode.CONSUMA_NETWORK);
        } else if (enableManualDelivery) {
            p.setDeliveryMode(DeliveryMode.TENANT_OWN_DELIVERY);
        } else {
            p.setDeliveryMode(DeliveryMode.PICKUP_ONLY);
        }

        p.setRequirePaymentBeforeDelivery(true);
        p.setAutoCreateDeliveryJobAfterPayment(false);
        return tenantDeliveryPolicyRepository.saveAndFlush(p);
    }

    public TenantOperacaoPolicy upsertOperacaoPolicy(Tenant tenant,
                                                     boolean requireOpenTurnoForOrders,
                                                     LogisticsMode logisticsMode,
                                                     boolean allowPickup,
                                                     boolean allowManualPayment,
                                                     boolean allowDigitalPayment,
                                                     String stockMode,
                                                     boolean productionEnabled,
                                                     boolean posEnabled,
                                                     boolean kdsEnabled,
                                                     boolean allowTableQr,
                                                     boolean snapshotFinanceiroEnabled,
                                                     boolean preFechoEnabled) {
        TenantOperacaoPolicy p = tenantOperacaoPolicyRepository.findByTenantId(tenant.getId()).orElseGet(() -> {
            TenantOperacaoPolicy n = new TenantOperacaoPolicy();
            n.setTenant(tenant);
            return n;
        });
        p.setRequireOpenTurnoForOrders(requireOpenTurnoForOrders);
        p.setLogisticsMode(logisticsMode != null ? logisticsMode : LogisticsMode.NONE);
        p.setAllowPickup(allowPickup);
        p.setAllowManualPayment(allowManualPayment);
        p.setAllowDigitalPayment(allowDigitalPayment);
        p.setStockMode(stockMode);
        p.setProductionEnabled(productionEnabled);
        p.setPosEnabled(posEnabled);
        p.setKdsEnabled(kdsEnabled);
        p.setAllowTableQr(allowTableQr);
        p.setSnapshotFinanceiroEnabled(snapshotFinanceiroEnabled);
        p.setPreFechoEnabled(preFechoEnabled);
        return tenantOperacaoPolicyRepository.saveAndFlush(p);
    }

    public void upsertOperationalModules(Tenant tenant,
                                         boolean sessaoConsumoEnabled,
                                         boolean pedidoDiretoEnabled,
                                         boolean mesasEnabled,
                                         boolean qrMesaEnabled,
                                         boolean caixaEnabled,
                                         boolean kdsEnabled) {
        tenantOperationalModulesService.upsertForTemplate(
                tenant,
                sessaoConsumoEnabled,
                pedidoDiretoEnabled,
                mesasEnabled,
                qrMesaEnabled,
                caixaEnabled,
                kdsEnabled
        );
    }

    public void upsertSessaoConsumoConfig(Tenant tenant,
                                          boolean enabled,
                                          boolean permitirPrePago,
                                          boolean permitirPosPago,
                                          TipoSessao tipoSessaoPadrao,
                                          boolean exigirSaldoParaPedido,
                                          boolean permitirModoAnonimo,
                                          boolean permitirSessaoSemMesa,
                                          boolean permitirSessaoComMesa,
                                          Integer expiracaoHoras) {
        tenantSessaoConsumoConfigService.upsertForTemplate(
                tenant,
                enabled,
                permitirPrePago,
                permitirPosPago,
                tipoSessaoPadrao,
                exigirSaldoParaPedido,
                permitirModoAnonimo,
                permitirSessaoSemMesa,
                permitirSessaoComMesa,
                expiracaoHoras
        );
    }

    public void assertPlanLimitsOrThrow(Long tenantId,
                                        int unidadesNovas,
                                        int usuariosNovos,
                                        int qrNovos,
                                        int dispositivosNovos) {
        // Aplica limites (plano/override)
        try {
            tenantLimitService.assertCanCreateInstituicao(tenantId);
        } catch (BusinessException ex) {
            throw new ProvisioningException(HttpStatus.CONFLICT, "MAX_INSTITUICOES_EXCEDIDO", "limites.maxInstituicoes",
                    "Limite de instituições excedido para o tenant.", null, "Aumente limites do plano/override.", null);
        }
        try {
            tenantLimitService.assertCanCreateUnidadeAtendimento(tenantId, unidadesNovas);
        } catch (BusinessException ex) {
            throw new ProvisioningException(HttpStatus.CONFLICT, "MAX_UNIDADES_EXCEDIDO", "limites.maxUnidadesAtendimento",
                    "Limite de unidades de atendimento excedido para o tenant.", null, "Aumente limites do plano/override.", null);
        }
        try {
            tenantLimitService.assertCanCreateUser(tenantId, usuariosNovos);
        } catch (BusinessException ex) {
            throw new ProvisioningException(HttpStatus.CONFLICT, "MAX_USUARIOS_EXCEDIDO", "limites.maxUsuarios",
                    "Limite de usuários excedido para o tenant.", null, "Aumente limites do plano/override.", null);
        }
        try {
            tenantLimitService.assertCanCreateQrCode(tenantId, qrNovos);
        } catch (BusinessException ex) {
            throw new ProvisioningException(HttpStatus.CONFLICT, "MAX_QR_CODES_EXCEDIDO", "limites.maxQrCodes",
                    "Limite de QR Codes excedido para o tenant.", null, "Aumente limites do plano/override.", null);
        }
        try {
            tenantLimitService.assertCanCreateDispositivo(tenantId, dispositivosNovos);
        } catch (BusinessException ex) {
            throw new ProvisioningException(HttpStatus.CONFLICT, "MAX_DISPOSITIVOS_EXCEDIDO", "limites.maxDispositivos",
                    "Limite de dispositivos excedido para o tenant.", null, "Aumente limites do plano/override.", null);
        }
    }

    public void bootstrapPaymentAndInventory(Tenant tenant) {
        paymentMethodBootstrapService.ensureDefaultsInCurrentTransaction(tenant);
        tenantInventoryPolicyService.getOrCreateDefault(tenant);
    }

    public List<UnidadeProducao> criarUnidadesProducaoRest(Tenant tenant,
                                                          Instituicao inst,
                                                          UnidadeAtendimento ua,
                                                          boolean temBarSeparado) {
        List<UnidadeProducao> out = new ArrayList<>();

        UnidadeProducao geral = upsertUnidadeProducao(tenant, inst, ua, "GERAL", "Geral", UnidadeProducaoTipo.OUTRO, 0);
        out.add(geral);
        UnidadeProducao cozinha = upsertUnidadeProducao(tenant, inst, ua, "COZINHA", "Cozinha", UnidadeProducaoTipo.COZINHA, 10);
        out.add(cozinha);

        if (temBarSeparado) {
            UnidadeProducao bar = upsertUnidadeProducao(tenant, inst, ua, "BAR", "Bar", UnidadeProducaoTipo.BAR, 20);
            out.add(bar);
        }
        return out;
    }

    private UnidadeProducao upsertUnidadeProducao(Tenant tenant,
                                                  Instituicao inst,
                                                  UnidadeAtendimento ua,
                                                  String codigo,
                                                  String nome,
                                                  UnidadeProducaoTipo tipo,
                                                  int ordem) {
        UnidadeProducao existing = unidadeProducaoRepository.findByTenantIdAndInstituicaoIdAndCodigo(tenant.getId(), inst.getId(), codigo).orElse(null);
        if (existing != null) return existing;
        UnidadeProducao up = new UnidadeProducao();
        up.setTenant(tenant);
        up.setInstituicao(inst);
        up.setUnidadeAtendimento(ua);
        up.setCodigo(codigo);
        up.setNome(nome);
        up.setTipo(tipo != null ? tipo : UnidadeProducaoTipo.OUTRO);
        up.setAtivo(true);
        up.setOrdem(ordem);
        return unidadeProducaoRepository.saveAndFlush(up);
    }

    public DispositivoOperacional criarDeviceRest(Tenant tenant,
                                                 Instituicao inst,
                                                 UnidadeAtendimento ua,
                                                 UnidadeProducao unidadeProducao,
                                                 String codigo,
                                                 String nome,
                                                 DispositivoTipo tipo,
                                                 OperationalDeviceType operationalDeviceType) {
        DispositivoOperacional d = new DispositivoOperacional();
        d.setTenant(tenant);
        d.setInstituicao(inst);
        d.setUnidadeAtendimento(ua);
        d.setUnidadeProducao(unidadeProducao);
        d.setCodigo(codigo);
        d.setNome(nome);
        d.setTipo(tipo != null ? tipo : DispositivoTipo.OUTRO);
        d.setOperationalDeviceType(operationalDeviceType != null ? operationalDeviceType : OperationalDeviceType.GENERIC_DEVICE);
        d.setStatus(DispositivoStatus.PENDENTE_ATIVACAO);
        d.setTokenVersion(1);
        return dispositivoOperacionalRepository.saveAndFlush(d);
    }

    public ChecklistOperacionalTemplate criarChecklistTemplate(Tenant tenant, ChecklistTipo tipo, String nome, List<ChecklistOperacionalItemTemplate> itens) {
        ChecklistOperacionalTemplate t = new ChecklistOperacionalTemplate();
        t.setTenant(tenant);
        t.setTipo(tipo);
        t.setNome(nome);
        t.setAtivo(true);
        t.setEscopo(ChecklistEscopo.GLOBAL);
        t = checklistTemplateRepository.saveAndFlush(t);

        if (itens != null) {
            int ordem = 0;
            for (ChecklistOperacionalItemTemplate it : itens) {
                it.setTemplate(t);
                it.setOrdem(ordem++);
                if (it.getTipoResposta() == null) it.setTipoResposta(ChecklistTipoResposta.BOOLEAN);
                if (it.getObrigatorio() == null) it.setObrigatorio(true);
                if (it.getAtivo() == null) it.setAtivo(true);
                checklistItemTemplateRepository.saveAndFlush(it);
            }
        }
        return t;
    }

    public ChecklistOperacionalItemTemplate checklistItem(String codigo, String descricao, boolean obrigatorio) {
        ChecklistOperacionalItemTemplate it = new ChecklistOperacionalItemTemplate();
        it.setCodigo(codigo);
        it.setDescricao(descricao);
        it.setObrigatorio(obrigatorio);
        it.setTipoResposta(ChecklistTipoResposta.BOOLEAN);
        it.setAtivo(true);
        return it;
    }

    public void logTemplateProvisioned(Tenant tenant, String templateCode, int templateVersion, String planoCodigo) {
        operationalEventLogService.logPublicEvent(
                tenant,
                null,
                null,
                null,
                null,
                OperationalEventType.BUSINESS_TEMPLATE_PROVISIONED,
                OperationalEntityType.TENANT_SUBSCRIPTION,
                0L,
                OperationalOrigem.SYSTEM,
                "BusinessTemplate provisionado",
                Map.of(
                        "templateCode", templateCode,
                        "templateVersion", templateVersion,
                        "planoCodigo", planoCodigo
                ),
                null,
                null
        );
    }

    private static String gerarUsername(String email, String telefone) {
        if (email != null && !email.isBlank()) {
            return email.toLowerCase(Locale.ROOT);
        }
        if (telefone != null && !telefone.isBlank()) {
            return "u" + telefone.replaceAll("[^0-9]", "");
        }
        return "user-" + randomCode(6);
    }

    private static String gerarSenhaTemporaria() {
        // simples: não logar, não retornar por API nesta fase
        return "Tmp@" + randomCode(8);
    }
}

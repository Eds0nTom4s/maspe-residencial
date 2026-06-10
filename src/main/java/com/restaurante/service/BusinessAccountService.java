package com.restaurante.service;

import com.restaurante.dto.request.BusinessAccountCreateRequest;
import com.restaurante.dto.request.BusinessAccountEstadoUpdateRequest;
import com.restaurante.dto.request.BusinessAccountMemberCreateRequest;
import com.restaurante.dto.request.BusinessAccountMemberEstadoUpdateRequest;
import com.restaurante.dto.response.BusinessAccountMemberResponse;
import com.restaurante.dto.response.BusinessAccountResponse;
import com.restaurante.dto.response.BusinessAccountSummaryResponse;
import com.restaurante.dto.response.PlatformTenantResponse;
import com.restaurante.exception.BusinessException;
import com.restaurante.model.entity.BusinessAccount;
import com.restaurante.model.entity.BusinessAccountMember;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.User;
import com.restaurante.model.enums.BusinessAccountEstado;
import com.restaurante.model.enums.BusinessAccountMemberEstado;
import com.restaurante.model.enums.BusinessAccountRole;
import com.restaurante.repository.BusinessAccountMemberRepository;
import com.restaurante.repository.BusinessAccountRepository;
import com.restaurante.repository.TenantRepository;
import com.restaurante.repository.UserRepository;
import com.restaurante.security.tenant.TenantContextHolder;
import com.restaurante.security.tenant.TenantGuard;
import com.restaurante.service.provisioning.ProvisioningPlanCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class BusinessAccountService {

    private final TenantGuard tenantGuard;
    private final BusinessAccountRepository businessAccountRepository;
    private final BusinessAccountMemberRepository businessAccountMemberRepository;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final PlatformTenantAccessService platformTenantAccessService;

    @Transactional(readOnly = true)
    public List<BusinessAccountSummaryResponse> listar(Pageable pageable, BusinessAccountEstado estado, String search) {
        tenantGuard.assertPlatformAdmin();
        return businessAccountRepository.search(estado, search, pageable)
                .stream()
                .map(this::toSummaryResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public BusinessAccountResponse buscarPorId(Long id) {
        tenantGuard.assertPlatformAdmin();
        return toResponse(getBusinessAccount(id));
    }

    @Transactional(readOnly = true)
    public BusinessAccountResponse buscarPorSlug(String slug) {
        tenantGuard.assertPlatformAdmin();
        BusinessAccount account = businessAccountRepository.findBySlug(normalizeSlug(slug))
                .orElseThrow(() -> new BusinessException("BusinessAccount não encontrada."));
        return toResponse(account);
    }

    @Transactional(readOnly = true)
    public List<PlatformTenantResponse> listarTenants(Long businessAccountId) {
        tenantGuard.assertPlatformAdmin();
        getBusinessAccount(businessAccountId);
        return tenantRepository.findByBusinessAccountIdOrderByIdAsc(businessAccountId)
                .stream()
                .map(platformTenantAccessService::toResponse)
                .toList();
    }

    @Transactional
    public BusinessAccountResponse criar(BusinessAccountCreateRequest request) {
        tenantGuard.assertPlatformAdmin();
        String slug = normalizeSlug(request.getSlug());
        if (slug == null || slug.isBlank()) {
            throw new BusinessException("Slug inválido para BusinessAccount.");
        }
        if (businessAccountRepository.existsBySlug(slug)) {
            throw new BusinessException("Já existe BusinessAccount com este slug.");
        }

        BusinessAccount account = new BusinessAccount();
        account.setNome(request.getNome());
        account.setSlug(slug);
        account.setNif(request.getNif());
        account.setEmail(request.getEmail());
        account.setTelefone(request.getTelefone());
        account.setEstado(request.getEstado() != null ? request.getEstado() : BusinessAccountEstado.RASCUNHO);
        account.setObservacao(request.getObservacao());
        account.setProvisionedAt(LocalDateTime.now());
        account.setProvisionedBy(resolveProvisionedBy());
        account.setResponsavel(resolveUser(request.getResponsavelUserId()));
        account = businessAccountRepository.saveAndFlush(account);

        if (account.getResponsavel() != null) {
            upsertMember(account, account.getResponsavel(), BusinessAccountRole.OWNER, BusinessAccountMemberEstado.ATIVO);
        }
        associateTenants(account, request.getTenantIds());
        return toResponse(account);
    }

    @Transactional
    public BusinessAccountResponse atualizarEstado(Long id, BusinessAccountEstadoUpdateRequest request) {
        tenantGuard.assertPlatformAdmin();
        BusinessAccount account = getBusinessAccount(id);
        account.setEstado(request.getEstado());
        return toResponse(businessAccountRepository.saveAndFlush(account));
    }

    @Transactional
    public Tenant associarTenant(Long businessAccountId, Long tenantId) {
        tenantGuard.assertPlatformAdmin();
        BusinessAccount account = getBusinessAccount(businessAccountId);
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new BusinessException("Tenant não encontrado."));
        tenant.setBusinessAccount(account);
        return tenantRepository.saveAndFlush(tenant);
    }

    @Transactional
    public BusinessAccountMemberResponse adicionarMembro(Long businessAccountId, BusinessAccountMemberCreateRequest request) {
        tenantGuard.assertPlatformAdmin();
        BusinessAccount account = getBusinessAccount(businessAccountId);
        User user = resolveUserRequired(request.getUserId());
        BusinessAccountMember member = upsertMember(
                account,
                user,
                request.getRole(),
                request.getEstado() != null ? request.getEstado() : BusinessAccountMemberEstado.ATIVO
        );
        return toMemberResponse(member);
    }

    @Transactional(readOnly = true)
    public List<BusinessAccountMemberResponse> listarMembros(Long businessAccountId) {
        tenantGuard.assertPlatformAdmin();
        getBusinessAccount(businessAccountId);
        return businessAccountMemberRepository.findByBusinessAccountIdOrderByIdAsc(businessAccountId)
                .stream()
                .map(this::toMemberResponse)
                .toList();
    }

    @Transactional
    public BusinessAccountMemberResponse atualizarEstadoMembro(Long businessAccountId, Long memberId, BusinessAccountMemberEstadoUpdateRequest request) {
        tenantGuard.assertPlatformAdmin();
        getBusinessAccount(businessAccountId);
        BusinessAccountMember member = businessAccountMemberRepository.findByBusinessAccountIdAndId(businessAccountId, memberId)
                .orElseThrow(() -> new BusinessException("Membro da BusinessAccount não encontrado."));
        member.setEstado(request.getEstado());
        return toMemberResponse(businessAccountMemberRepository.saveAndFlush(member));
    }

    @Transactional(readOnly = true)
    public BusinessAccount getBusinessAccount(Long id) {
        return businessAccountRepository.findById(id)
                .orElseThrow(() -> new BusinessException("BusinessAccount não encontrada."));
    }

    private BusinessAccountSummaryResponse toSummaryResponse(BusinessAccount account) {
        return BusinessAccountSummaryResponse.builder()
                .id(account.getId())
                .nome(account.getNome())
                .slug(account.getSlug())
                .estado(account.getEstado())
                .responsavelUserId(account.getResponsavel() != null ? account.getResponsavel().getId() : null)
                .responsavelNome(account.getResponsavel() != null ? account.getResponsavel().getNomeCompleto() : null)
                .tenantCount(tenantRepository.countByBusinessAccountId(account.getId()))
                .memberCount(businessAccountMemberRepository.countByBusinessAccountId(account.getId()))
                .createdAt(account.getCreatedAt())
                .build();
    }

    private BusinessAccountResponse toResponse(BusinessAccount account) {
        return BusinessAccountResponse.builder()
                .id(account.getId())
                .nome(account.getNome())
                .slug(account.getSlug())
                .nif(account.getNif())
                .email(account.getEmail())
                .telefone(account.getTelefone())
                .estado(account.getEstado())
                .responsavelUserId(account.getResponsavel() != null ? account.getResponsavel().getId() : null)
                .responsavelNome(account.getResponsavel() != null ? account.getResponsavel().getNomeCompleto() : null)
                .responsavelEmail(account.getResponsavel() != null ? account.getResponsavel().getEmail() : null)
                .observacao(account.getObservacao())
                .tenantCount(tenantRepository.countByBusinessAccountId(account.getId()))
                .memberCount(businessAccountMemberRepository.countByBusinessAccountId(account.getId()))
                .createdAt(account.getCreatedAt())
                .updatedAt(account.getUpdatedAt())
                .build();
    }

    private BusinessAccountMemberResponse toMemberResponse(BusinessAccountMember member) {
        User user = member.getUser();
        return BusinessAccountMemberResponse.builder()
                .id(member.getId())
                .businessAccountId(member.getBusinessAccount().getId())
                .userId(user != null ? user.getId() : null)
                .username(user != null ? user.getUsername() : null)
                .nomeCompleto(user != null ? user.getNomeCompleto() : null)
                .email(user != null ? user.getEmail() : null)
                .telefone(user != null ? user.getTelefone() : null)
                .role(member.getRole())
                .estado(member.getEstado())
                .createdAt(member.getCreatedAt())
                .updatedAt(member.getUpdatedAt())
                .build();
    }

    private void associateTenants(BusinessAccount account, List<Long> tenantIds) {
        if (tenantIds == null || tenantIds.isEmpty()) {
            return;
        }
        Set<Long> uniqueIds = new LinkedHashSet<>(tenantIds);
        for (Long tenantId : uniqueIds) {
            Tenant tenant = tenantRepository.findById(tenantId)
                    .orElseThrow(() -> new BusinessException("Tenant não encontrado."));
            tenant.setBusinessAccount(account);
            tenantRepository.save(tenant);
        }
        tenantRepository.flush();
    }

    private BusinessAccountMember upsertMember(BusinessAccount account, User user, BusinessAccountRole role, BusinessAccountMemberEstado estado) {
        BusinessAccountMember member = businessAccountMemberRepository.findByBusinessAccountIdAndUserId(account.getId(), user.getId())
                .orElseGet(BusinessAccountMember::new);
        member.setBusinessAccount(account);
        member.setUser(user);
        member.setRole(role);
        member.setEstado(estado);
        return businessAccountMemberRepository.saveAndFlush(member);
    }

    private User resolveUser(Long userId) {
        if (userId == null) {
            return null;
        }
        return resolveUserRequired(userId);
    }

    private User resolveUserRequired(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("Usuário não encontrado."));
    }

    private String normalizeSlug(String slug) {
        return ProvisioningPlanCalculator.normalizeSlug(slug);
    }

    private String resolveProvisionedBy() {
        return TenantContextHolder.get()
                .map(ctx -> ctx.userId() != null ? String.valueOf(ctx.userId()) : null)
                .orElse(null);
    }
}

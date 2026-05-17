package com.restaurante.service.tenantadmin;

import com.restaurante.dto.request.AlterarTenantUsuarioRolesRequest;
import com.restaurante.dto.request.CriarTenantUsuarioRequest;
import com.restaurante.dto.request.ReativarTenantUsuarioRequest;
import com.restaurante.dto.response.TenantUsuarioResponse;
import com.restaurante.exception.BusinessException;
import com.restaurante.exception.ConflictException;
import com.restaurante.exception.ResourceNotFoundException;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.TenantUser;
import com.restaurante.model.entity.User;
import com.restaurante.model.enums.Role;
import com.restaurante.model.enums.TenantAuditAction;
import com.restaurante.model.enums.TenantAuditStatus;
import com.restaurante.model.enums.TenantUserEstado;
import com.restaurante.model.enums.TenantUserRole;
import com.restaurante.repository.TenantRepository;
import com.restaurante.repository.TenantUserRepository;
import com.restaurante.repository.UserRepository;
import com.restaurante.security.tenant.TenantContext;
import com.restaurante.security.tenant.TenantGuard;
import com.restaurante.service.TenantLimitService;
import com.restaurante.service.security.TenantUserAccessVersionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TenantUsuarioService {

    private final TenantGuard tenantGuard;
    private final TenantRepository tenantRepository;
    private final TenantUserRepository tenantUserRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TenantLimitService tenantLimitService;
    private final TenantAuditService tenantAuditService;
    private final TenantUserAccessVersionService tenantUserAccessVersionService;

    @Transactional(readOnly = true)
    public Page<TenantUsuarioResponse> listar(Pageable pageable) {
        TenantContext ctx = requireTenantContext();
        Set<TenantUserRole> actorRoles = actorRoles(ctx);
        TenantUsuarioPolicy.assertActorCanManageUsers(actorRoles);

        List<TenantUser> rows = tenantUserRepository.findByTenantIdWithUser(ctx.tenantId());
        List<TenantUsuarioResponse> all = aggregate(rows);

        int start = (int) Math.min((long) pageable.getOffset(), all.size());
        int end = Math.min(start + pageable.getPageSize(), all.size());
        List<TenantUsuarioResponse> content = all.subList(start, end);
        return new PageImpl<>(content, pageable, all.size());
    }

    @Transactional(readOnly = true)
    public TenantUsuarioResponse buscar(Long userId) {
        TenantContext ctx = requireTenantContext();
        Set<TenantUserRole> actorRoles = actorRoles(ctx);
        TenantUsuarioPolicy.assertActorCanManageUsers(actorRoles);

        List<TenantUser> rows = tenantUserRepository.findAllByTenantIdAndUserId(ctx.tenantId(), userId);
        if (rows.isEmpty() || rows.stream().allMatch(tu -> tu.getEstado() == TenantUserEstado.REMOVIDO)) {
            throw new ResourceNotFoundException("Recurso não encontrado.");
        }
        return aggregate(rows).getFirst();
    }

    @Transactional
    public TenantUsuarioResponse criarOuConvidar(CriarTenantUsuarioRequest request, String ip, String userAgent) {
        TenantContext ctx = requireTenantContext();
        Set<TenantUserRole> actorRoles = actorRoles(ctx);
        TenantUsuarioPolicy.assertActorCanManageUsers(actorRoles);
        TenantUsuarioPolicy.assertRolesNotEmpty(request.getRoles());
        TenantUsuarioPolicy.assertAdminCannotAssignOwnerOrAdmin(actorRoles, request.getRoles());

        if ((request.getEmail() == null || request.getEmail().isBlank()) && (request.getTelefone() == null || request.getTelefone().isBlank())) {
            throw new BusinessException("Email ou telefone é obrigatório.");
        }

        String email = normalizeEmail(request.getEmail());
        String telefone = normalizePhone(request.getTelefone());

        User user = findExistingUser(email, telefone).orElse(null);
        boolean reused = user != null;

        if (!reused) {
            // Schema atual exige telefone NOT NULL.
            if (telefone == null || telefone.isBlank()) {
                throw new BusinessException("Telefone é obrigatório para criar novo usuário.");
            }
            user = createUser(request, email, telefone);
        }

        List<TenantUser> existingRows = tenantUserRepository.findAllByTenantIdAndUserId(ctx.tenantId(), user.getId());
        boolean alreadyMember = existingRows.stream().anyMatch(tu -> tu.getEstado() != TenantUserEstado.REMOVIDO);
        if (alreadyMember) {
            tenantAuditService.log(
                    TenantAuditAction.TENANT_USER_ACTION_FORBIDDEN,
                    TenantAuditStatus.BLOCKED,
                    user.getId(),
                    "TenantUser",
                    null,
                    "Usuário já pertence ao tenant.",
                    Map.of("code", "TENANT_USER_ALREADY_EXISTS"),
                    ip,
                    userAgent
            );
            throw new ConflictException("Usuário já pertence ao tenant.");
        }

        // Limite: só conta como +1 se não havia vínculo não-REMOVIDO
        try {
            tenantLimitService.assertCanCreateUser(ctx.tenantId(), 1);
        } catch (BusinessException ex) {
            tenantAuditService.log(
                    TenantAuditAction.TENANT_USER_LIMIT_EXCEEDED,
                    TenantAuditStatus.BLOCKED,
                    user.getId(),
                    "TenantUser",
                    null,
                    ex.getMessage(),
                    Map.of("code", "TENANT_USER_LIMIT_EXCEEDED"),
                    ip,
                    userAgent
            );
            throw new ConflictException("Limite de usuários excedido para o tenant.");
        }

        Set<TenantUserRole> rolesToSet = EnumSet.copyOf(request.getRoles());
        TenantUserEstado estadoInicial = request.getEstadoInicial() != null ? request.getEstadoInicial() : TenantUserEstado.ATIVO;
        if (estadoInicial == TenantUserEstado.REMOVIDO) {
            throw new BusinessException("Estado inicial inválido.");
        }

        setRolesForUser(ctx.tenantId(), user, rolesToSet, estadoInicial);
        tenantUserAccessVersionService.increment(ctx.tenantId(), user.getId());

        TenantUsuarioResponse resp = buscar(user.getId());

        tenantAuditService.log(
                reused ? TenantAuditAction.TENANT_USER_REUSED : TenantAuditAction.TENANT_USER_CREATED,
                TenantAuditStatus.SUCCESS,
                user.getId(),
                "TenantUser",
                null,
                reused ? "Usuário reutilizado e vinculado ao tenant." : "Usuário criado e vinculado ao tenant.",
                Map.of("roles", rolesToSet.stream().map(Enum::name).toList(), "estadoInicial", estadoInicial.name()),
                ip,
                userAgent
        );

        return resp;
    }

    @Transactional
    public TenantUsuarioResponse alterarRoles(Long userId, AlterarTenantUsuarioRolesRequest request, String ip, String userAgent) {
        TenantContext ctx = requireTenantContext();
        Set<TenantUserRole> actorRoles = actorRoles(ctx);
        TenantUsuarioPolicy.assertActorCanManageUsers(actorRoles);
        TenantUsuarioPolicy.assertRolesNotEmpty(request.getRoles());
        TenantUsuarioPolicy.assertAdminCannotAssignOwnerOrAdmin(actorRoles, request.getRoles());

        User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("Recurso não encontrado."));

        List<TenantUser> currentRows = tenantUserRepository.findAllByTenantIdAndUserId(ctx.tenantId(), userId);
        if (currentRows.isEmpty() || currentRows.stream().allMatch(tu -> tu.getEstado() == TenantUserEstado.REMOVIDO)) {
            throw new ResourceNotFoundException("Recurso não encontrado.");
        }

        Set<TenantUserRole> targetCurrentRoles = currentRows.stream()
                .filter(tu -> tu.getEstado() != TenantUserEstado.REMOVIDO)
                .map(TenantUser::getRole)
                .collect(Collectors.toSet());
        TenantUsuarioPolicy.assertAdminCannotModifyOwnerOrAdmin(actorRoles, targetCurrentRoles);

        // proteção: não remover último OWNER ativo
        if (targetCurrentRoles.contains(TenantUserRole.TENANT_OWNER) && !request.getRoles().contains(TenantUserRole.TENANT_OWNER)) {
            assertNotLastActiveOwner(ctx.tenantId(), userId);
        }

        Set<TenantUserRole> newRoles = EnumSet.copyOf(request.getRoles());
        setRolesForUser(ctx.tenantId(), user, newRoles, TenantUserEstado.ATIVO);
        tenantUserAccessVersionService.increment(ctx.tenantId(), userId);

        TenantUsuarioResponse resp = buscar(userId);

        tenantAuditService.log(
                TenantAuditAction.TENANT_USER_ROLES_CHANGED,
                TenantAuditStatus.SUCCESS,
                userId,
                "TenantUser",
                null,
                "Roles alteradas.",
                Map.of("rolesAntes", targetCurrentRoles.stream().map(Enum::name).toList(),
                        "rolesDepois", newRoles.stream().map(Enum::name).toList()),
                ip,
                userAgent
        );

        return resp;
    }

    @Transactional
    public TenantUsuarioResponse suspender(Long userId, String motivo, String ip, String userAgent) {
        TenantContext ctx = requireTenantContext();
        Set<TenantUserRole> actorRoles = actorRoles(ctx);
        TenantUsuarioPolicy.assertActorCanManageUsers(actorRoles);

        List<TenantUser> rows = tenantUserRepository.findAllByTenantIdAndUserId(ctx.tenantId(), userId);
        if (rows.isEmpty() || rows.stream().allMatch(tu -> tu.getEstado() == TenantUserEstado.REMOVIDO)) {
            throw new ResourceNotFoundException("Recurso não encontrado.");
        }

        Set<TenantUserRole> targetRoles = rows.stream()
                .filter(tu -> tu.getEstado() != TenantUserEstado.REMOVIDO)
                .map(TenantUser::getRole)
                .collect(Collectors.toSet());
        TenantUsuarioPolicy.assertAdminCannotModifyOwnerOrAdmin(actorRoles, targetRoles);

        if (targetRoles.contains(TenantUserRole.TENANT_OWNER)) {
            assertNotLastActiveOwner(ctx.tenantId(), userId);
        }

        rows.stream()
                .filter(tu -> tu.getEstado() != TenantUserEstado.REMOVIDO)
                .forEach(tu -> tu.setEstado(TenantUserEstado.SUSPENSO));
        tenantUserRepository.saveAll(rows);
        tenantUserAccessVersionService.increment(ctx.tenantId(), userId);

        TenantUsuarioResponse resp = buscar(userId);

        tenantAuditService.log(
                TenantAuditAction.TENANT_USER_SUSPENDED,
                TenantAuditStatus.SUCCESS,
                userId,
                "TenantUser",
                null,
                "Usuário suspenso no tenant.",
                Map.of("motivo", motivo != null ? motivo : ""),
                ip,
                userAgent
        );

        return resp;
    }

    @Transactional
    public TenantUsuarioResponse reativar(Long userId, ReativarTenantUsuarioRequest request, String ip, String userAgent) {
        TenantContext ctx = requireTenantContext();
        Set<TenantUserRole> actorRoles = actorRoles(ctx);
        TenantUsuarioPolicy.assertActorCanManageUsers(actorRoles);

        List<TenantUser> rows = tenantUserRepository.findAllByTenantIdAndUserId(ctx.tenantId(), userId);
        if (rows.isEmpty()) {
            throw new ResourceNotFoundException("Recurso não encontrado.");
        }

        Set<TenantUserRole> existingNonRemovedRoles = rows.stream()
                .filter(tu -> tu.getEstado() != TenantUserEstado.REMOVIDO)
                .map(TenantUser::getRole)
                .collect(Collectors.toSet());

        // ADMIN não pode reativar OWNER/ADMIN
        TenantUsuarioPolicy.assertAdminCannotModifyOwnerOrAdmin(actorRoles, existingNonRemovedRoles);

        Set<TenantUserRole> rolesToSet;
        if (request != null && request.getRoles() != null && !request.getRoles().isEmpty()) {
            TenantUsuarioPolicy.assertAdminCannotAssignOwnerOrAdmin(actorRoles, request.getRoles());
            rolesToSet = EnumSet.copyOf(request.getRoles());
        } else {
            rolesToSet = existingNonRemovedRoles.isEmpty()
                    ? rows.stream().map(TenantUser::getRole).collect(Collectors.toSet())
                    : existingNonRemovedRoles;
        }

        if (rolesToSet == null || rolesToSet.isEmpty()) {
            throw new BusinessException("Roles são obrigatórias para reativar.");
        }

        // Se usuário estava REMOVIDO em todas as roles, reativar conta como +1
        boolean allRemoved = rows.stream().allMatch(tu -> tu.getEstado() == TenantUserEstado.REMOVIDO);
        if (allRemoved) {
            try {
                tenantLimitService.assertCanCreateUser(ctx.tenantId(), 1);
            } catch (BusinessException ex) {
                tenantAuditService.log(
                        TenantAuditAction.TENANT_USER_LIMIT_EXCEEDED,
                        TenantAuditStatus.BLOCKED,
                        userId,
                        "TenantUser",
                        null,
                        ex.getMessage(),
                        Map.of("code", "TENANT_USER_LIMIT_EXCEEDED"),
                        ip,
                        userAgent
                );
                throw new ConflictException("Limite de usuários excedido para o tenant.");
            }
        }

        User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("Recurso não encontrado."));
        setRolesForUser(ctx.tenantId(), user, rolesToSet, TenantUserEstado.ATIVO);
        tenantUserAccessVersionService.increment(ctx.tenantId(), userId);

        TenantUsuarioResponse resp = buscar(userId);

        tenantAuditService.log(
                TenantAuditAction.TENANT_USER_REACTIVATED,
                TenantAuditStatus.SUCCESS,
                userId,
                "TenantUser",
                null,
                "Usuário reativado no tenant.",
                Map.of("roles", rolesToSet.stream().map(Enum::name).toList()),
                ip,
                userAgent
        );

        return resp;
    }

    @Transactional
    public void remover(Long userId, String motivo, String ip, String userAgent) {
        TenantContext ctx = requireTenantContext();
        Set<TenantUserRole> actorRoles = actorRoles(ctx);
        TenantUsuarioPolicy.assertActorCanManageUsers(actorRoles);

        List<TenantUser> rows = tenantUserRepository.findAllByTenantIdAndUserId(ctx.tenantId(), userId);
        if (rows.isEmpty() || rows.stream().allMatch(tu -> tu.getEstado() == TenantUserEstado.REMOVIDO)) {
            throw new ResourceNotFoundException("Recurso não encontrado.");
        }

        Set<TenantUserRole> targetRoles = rows.stream()
                .filter(tu -> tu.getEstado() != TenantUserEstado.REMOVIDO)
                .map(TenantUser::getRole)
                .collect(Collectors.toSet());
        TenantUsuarioPolicy.assertAdminCannotModifyOwnerOrAdmin(actorRoles, targetRoles);

        if (targetRoles.contains(TenantUserRole.TENANT_OWNER)) {
            assertNotLastActiveOwner(ctx.tenantId(), userId);
        }

        rows.forEach(tu -> tu.setEstado(TenantUserEstado.REMOVIDO));
        tenantUserRepository.saveAll(rows);
        tenantUserAccessVersionService.increment(ctx.tenantId(), userId);

        tenantAuditService.log(
                TenantAuditAction.TENANT_USER_REMOVED,
                TenantAuditStatus.SUCCESS,
                userId,
                "TenantUser",
                null,
                "Usuário removido do tenant.",
                Map.of("motivo", motivo != null ? motivo : ""),
                ip,
                userAgent
        );
    }

    private void setRolesForUser(Long tenantId, User user, Set<TenantUserRole> rolesToSet, TenantUserEstado estado) {
        Tenant tenant = tenantRepository.findById(tenantId).orElseThrow(() -> new ResourceNotFoundException("Recurso não encontrado."));

        List<TenantUser> allRows = tenantUserRepository.findAllByTenantIdAndUserId(tenantId, user.getId());
        Map<TenantUserRole, TenantUser> byRole = allRows.stream().collect(Collectors.toMap(
                TenantUser::getRole,
                tu -> tu,
                (a, b) -> a
        ));

        // marcar removidas
        for (TenantUser tu : allRows) {
            if (!rolesToSet.contains(tu.getRole())) {
                tu.setEstado(TenantUserEstado.REMOVIDO);
            }
        }

        // criar/reativar necessárias
        for (TenantUserRole role : rolesToSet) {
            TenantUser tu = byRole.get(role);
            if (tu == null) {
                tu = new TenantUser();
                tu.setTenant(tenant);
                tu.setUser(user);
                tu.setRole(role);
                tu.setEstado(estado);
                tenantUserRepository.save(tu);
            } else {
                tu.setEstado(estado);
            }
        }

        tenantUserRepository.saveAll(allRows);
    }

    private User createUser(CriarTenantUsuarioRequest request, String email, String telefone) {
        String username = email != null && !email.isBlank() ? email : telefone;
        if (username.length() > 50) {
            username = username.substring(0, 50);
        }
        String rawPassword = request.getSenhaTemporaria();
        if (rawPassword == null || rawPassword.isBlank()) {
            rawPassword = generateTempPassword();
        }

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setTelefone(telefone);
        user.setNomeCompleto(request.getNome());
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setRoles(Set.of(Role.ROLE_GERENTE));
        user.setAtivo(true);
        return userRepository.saveAndFlush(user);
    }

    private java.util.Optional<User> findExistingUser(String email, String telefone) {
        if (email != null && !email.isBlank()) {
            java.util.Optional<User> byEmail = userRepository.findByEmail(email);
            if (byEmail.isPresent()) return byEmail;
        }
        if (telefone != null && !telefone.isBlank()) {
            return userRepository.findByTelefone(telefone);
        }
        return java.util.Optional.empty();
    }

    private String normalizeEmail(String email) {
        if (email == null) return null;
        String e = email.trim().toLowerCase();
        return e.isBlank() ? null : e;
    }

    private String normalizePhone(String telefone) {
        if (telefone == null) return null;
        String t = telefone.trim();
        return t.isBlank() ? null : t;
    }

    private TenantContext requireTenantContext() {
        TenantContext ctx = tenantGuard.requireContext();
        if (ctx.tenantId() == null) {
            throw new ResourceNotFoundException("Recurso não encontrado.");
        }
        tenantGuard.assertCurrentUserBelongsToTenant(ctx.tenantId());
        tenantGuard.assertTenantActive(ctx.tenantId());
        return ctx;
    }

    private Set<TenantUserRole> actorRoles(TenantContext ctx) {
        Set<String> names = ctx.roles() != null ? ctx.roles() : Set.of();
        return names.stream()
                .map(name -> {
                    try { return TenantUserRole.valueOf(name); } catch (Exception e) { return null; }
                })
                .filter(r -> r != null)
                .collect(Collectors.toSet());
    }

    private void assertNotLastActiveOwner(Long tenantId, Long targetUserId) {
        long activeOwners = tenantUserRepository.countByTenantIdAndRoleAndEstado(tenantId, TenantUserRole.TENANT_OWNER, TenantUserEstado.ATIVO);
        boolean targetIsActiveOwner = tenantUserRepository.existsByTenantIdAndUserIdAndRoleAndEstado(tenantId, targetUserId, TenantUserRole.TENANT_OWNER, TenantUserEstado.ATIVO);
        if (targetIsActiveOwner && activeOwners <= 1) {
            throw new BusinessException("Não é possível remover/suspender o último OWNER ativo do tenant.");
        }
    }

    private List<TenantUsuarioResponse> aggregate(List<TenantUser> rows) {
        Map<Long, List<TenantUser>> byUser = rows.stream().collect(Collectors.groupingBy(tu -> tu.getUser().getId()));
        return byUser.values().stream()
                .map(this::aggregateUser)
                .sorted(Comparator.comparing(TenantUsuarioResponse::getUserId))
                .toList();
    }

    private TenantUsuarioResponse aggregateUser(List<TenantUser> rows) {
        User u = rows.getFirst().getUser();
        Set<TenantUserRole> roles = rows.stream()
                .filter(tu -> tu.getEstado() != TenantUserEstado.REMOVIDO)
                .map(TenantUser::getRole)
                .collect(Collectors.toSet());

        TenantUserEstado estado = aggregateEstado(rows);
        LocalDateTime criadoEm = rows.stream()
                .map(TenantUser::getCreatedAt)
                .filter(x -> x != null)
                .min(LocalDateTime::compareTo)
                .orElse(null);
        LocalDateTime atualizadoEm = rows.stream()
                .map(TenantUser::getUpdatedAt)
                .filter(x -> x != null)
                .max(LocalDateTime::compareTo)
                .orElse(null);

        return new TenantUsuarioResponse(
                u.getId(),
                u.getNomeCompleto(),
                u.getEmail(),
                u.getTelefone(),
                roles,
                estado,
                criadoEm,
                atualizadoEm,
                u.getUltimoAcesso()
        );
    }

    private TenantUserEstado aggregateEstado(List<TenantUser> rows) {
        boolean anyAtivo = rows.stream().anyMatch(tu -> tu.getEstado() == TenantUserEstado.ATIVO);
        if (anyAtivo) return TenantUserEstado.ATIVO;
        boolean anyPendente = rows.stream().anyMatch(tu -> tu.getEstado() == TenantUserEstado.PENDENTE);
        if (anyPendente) return TenantUserEstado.PENDENTE;
        boolean anySuspenso = rows.stream().anyMatch(tu -> tu.getEstado() == TenantUserEstado.SUSPENSO);
        if (anySuspenso) return TenantUserEstado.SUSPENSO;
        return TenantUserEstado.REMOVIDO;
    }

    private String generateTempPassword() {
        // simples e suficientemente forte para piloto (sem log, sem persistir em audit)
        String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789@#$%";
        SecureRandom rnd = new SecureRandom();
        StringBuilder sb = new StringBuilder(12);
        for (int i = 0; i < 12; i++) {
            sb.append(alphabet.charAt(rnd.nextInt(alphabet.length())));
        }
        return sb.toString();
    }
}

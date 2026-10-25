package com.sondurukedella.security.user;

import com.sondurukedella.common.exception.BusinessRuleException;
import com.sondurukedella.common.exception.ConflictException;
import com.sondurukedella.common.exception.InvalidRequestException;
import com.sondurukedella.common.exception.NotFoundException;
import com.sondurukedella.common.web.PageResponse;
import com.sondurukedella.security.audit.SecurityAuditService;
import com.sondurukedella.security.audit.SecurityEventType;
import com.sondurukedella.security.user.dto.PasswordResetRequest;
import com.sondurukedella.security.user.dto.UserCreateRequest;
import com.sondurukedella.security.user.dto.UserResponse;
import com.sondurukedella.security.user.dto.UserRoleRequest;
import com.sondurukedella.security.user.dto.UserStatusRequest;
import com.sondurukedella.security.user.dto.UserUpdateRequest;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// User administration, for Super Admins only. The URL rule in SecurityConfig already demands the
// role; every change here checks it again against the locked rows, because the actor may have been
// demoted or deactivated by another Super Admin a moment ago.
//
// Rules:
// - nobody changes their own role or status here (a slip could lock the company out);
// - there is always at least one active Super Admin;
// - a deactivation, role change or password reset ends that user's existing sessions.
@Service
@RequiredArgsConstructor
public class UserAdministrationService {

    public static final int MAX_PAGE_SIZE = 100;
    public static final Set<String> SORTABLE_PROPERTIES = Set.of("username", "displayName", "createdAt", "id");

    private final AppUserRepository repository;
    private final UserMapper mapper;
    private final PasswordEncoder passwordEncoder;
    private final SecurityAuditService audit;

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> search(String query, UserRole role, Boolean enabled, Pageable pageable) {
        return PageResponse.of(repository.findAll(UserSpecifications.search(query, role, enabled), stablePage(pageable)),
                mapper::toResponse);
    }

    @Transactional(readOnly = true)
    public UserResponse get(long id) {
        return mapper.toResponse(repository.findById(id).orElseThrow(UserAdministrationService::notFound));
    }

    @Transactional
    public UserResponse create(long actorId, UserCreateRequest request) {
        lockSuperAdmins(actorId);
        String username = Usernames.normalize(request.username());
        if (!Usernames.isValid(username)) {
            throw new InvalidRequestException(Usernames.RULE);
        }
        if (repository.existsByUsername(username)) {
            throw new ConflictException("That username is already taken.");
        }
        AppUser user = new AppUser();
        user.setUsername(username);
        user.setDisplayName(request.displayName().strip());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(request.role() != null ? request.role() : UserRole.ADMIN);
        AppUser saved = repository.saveAndFlush(user);
        audit.record(SecurityEventType.USER_CREATED, actorId, saved.getId(), username, "role " + saved.getRole());
        return mapper.toResponse(saved);
    }

    @Transactional
    public UserResponse update(long actorId, long id, UserUpdateRequest request) {
        lockSuperAdmins(actorId);
        AppUser user = loadForUpdate(id);
        ConflictException.requireCurrentVersion(request.version(), user.getVersion());
        String displayName = request.displayName().strip();
        if (displayName.equals(user.getDisplayName())) {
            return mapper.toResponse(user);
        }
        user.setDisplayName(displayName);
        AppUser saved = repository.saveAndFlush(user);
        audit.record(SecurityEventType.USER_UPDATED, actorId, id, user.getUsername(), "display name changed");
        return mapper.toResponse(saved);
    }

    @Transactional
    public UserResponse changeRole(long actorId, long id, UserRoleRequest request) {
        List<AppUser> superAdmins = lockSuperAdmins(actorId);
        if (id == actorId) {
            throw new BusinessRuleException("You can't change your own role. Ask another Super Admin to do it.");
        }
        AppUser user = loadForUpdate(id);
        ConflictException.requireCurrentVersion(request.version(), user.getVersion());
        if (user.getRole() == request.role()) {
            return mapper.toResponse(user);
        }
        if (user.isActiveSuperAdmin()) {
            requireAnotherActiveSuperAdmin(superAdmins, user);
        }
        UserRole previous = user.getRole();
        user.setRole(request.role());
        user.revokeSessions();
        AppUser saved = repository.saveAndFlush(user);
        audit.record(SecurityEventType.ROLE_CHANGED, actorId, id, user.getUsername(), previous + " -> " + request.role());
        return mapper.toResponse(saved);
    }

    @Transactional
    public UserResponse setStatus(long actorId, long id, UserStatusRequest request) {
        List<AppUser> superAdmins = lockSuperAdmins(actorId);
        if (id == actorId) {
            throw new BusinessRuleException("You can't deactivate or reactivate your own account.");
        }
        AppUser user = loadForUpdate(id);
        ConflictException.requireCurrentVersion(request.version(), user.getVersion());
        if (user.isEnabled() == request.enabled()) {
            return mapper.toResponse(user);
        }
        if (!request.enabled()) {
            if (user.isActiveSuperAdmin()) {
                requireAnotherActiveSuperAdmin(superAdmins, user);
            }
            user.revokeSessions();
        }
        user.setEnabled(request.enabled());
        AppUser saved = repository.saveAndFlush(user);
        audit.record(request.enabled() ? SecurityEventType.USER_ACTIVATED : SecurityEventType.USER_DEACTIVATED,
                actorId, id, user.getUsername(), null);
        return mapper.toResponse(saved);
    }

    // The new password is never echoed back or logged.
    @Transactional
    public void resetPassword(long actorId, long id, PasswordResetRequest request) {
        lockSuperAdmins(actorId);
        if (id == actorId) {
            throw new BusinessRuleException("Use \"Change password\" for your own account.");
        }
        AppUser user = loadForUpdate(id);
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.revokeSessions();
        repository.saveAndFlush(user);
        audit.record(SecurityEventType.PASSWORD_RESET, actorId, id, user.getUsername(), null);
    }

    private List<AppUser> lockSuperAdmins(long actorId) {
        List<AppUser> superAdmins = repository.lockActiveByRole(UserRole.SUPER_ADMIN);
        if (superAdmins.stream().noneMatch(u -> u.getId() == actorId)) {
            throw new AccessDeniedException("Only an active Super Admin can manage user accounts.");
        }
        return superAdmins;
    }

    private static void requireAnotherActiveSuperAdmin(List<AppUser> superAdmins, AppUser leaving) {
        if (superAdmins.stream().noneMatch(u -> !u.getId().equals(leaving.getId()))) {
            throw new BusinessRuleException("The last active Super Admin can't be demoted or deactivated.");
        }
    }

    private AppUser loadForUpdate(long id) {
        return repository.findByIdForUpdate(id).orElseThrow(UserAdministrationService::notFound);
    }

    private static NotFoundException notFound() {
        return new NotFoundException("User not found.");
    }

    // username A-Z by default; id breaks ties so the order is stable across pages
    private static Pageable stablePage(Pageable pageable) {
        if (pageable.getPageSize() > MAX_PAGE_SIZE) {
            throw new InvalidRequestException("The page size can be at most " + MAX_PAGE_SIZE + ".");
        }
        Sort sort = pageable.getSort();
        for (Sort.Order order : sort) {
            if (!SORTABLE_PROPERTIES.contains(order.getProperty())) {
                throw new InvalidRequestException("Users cannot be sorted by \"" + order.getProperty()
                        + "\". Use one of: username, displayName, createdAt, id.");
            }
        }
        if (sort.isUnsorted()) {
            sort = Sort.by("username");
        }
        if (sort.getOrderFor("id") == null) {
            sort = sort.and(Sort.by("id"));
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
    }
}

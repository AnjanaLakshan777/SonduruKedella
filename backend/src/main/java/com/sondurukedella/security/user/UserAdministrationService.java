package com.sondurukedella.security.user;

import com.sondurukedella.common.exception.BusinessRuleException;
import com.sondurukedella.common.exception.ConflictException;
import com.sondurukedella.common.exception.InvalidRequestException;
import com.sondurukedella.common.exception.NotFoundException;
import com.sondurukedella.common.web.PageResponse;
import com.sondurukedella.security.access.AppPage;
import com.sondurukedella.security.audit.SecurityAuditService;
import com.sondurukedella.security.audit.SecurityEventType;
import com.sondurukedella.security.user.dto.PasswordResetRequest;
import com.sondurukedella.security.user.dto.UserCreateRequest;
import com.sondurukedella.security.user.dto.UserResponse;
import com.sondurukedella.security.user.dto.UserRoleRequest;
import com.sondurukedella.security.user.dto.UserStatusRequest;
import com.sondurukedella.security.user.dto.UserUpdateRequest;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Super Admin only. SecurityConfig already checks the role, but we check again against the locked
// rows because the actor might have just been demoted by someone else.
// Nobody can change their own role/status here, there's always one active Super Admin left, and
// disabling/role change/password reset logs the user out everywhere.
@Service
@RequiredArgsConstructor
public class UserAdministrationService {

    public static final int MAX_PAGE_SIZE = 100;
    public static final Set<String> SORTABLE_PROPERTIES = Set.of("username", "displayName", "createdAt", "id");
    // the built-in roles keep their names
    private static final Set<String> RESERVED_LABELS = Set.of("admin", "super admin", "superadmin", "super_admin", "custom", "other");

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
        String email = checkedEmail(request.email(), null);
        AppUser user = new AppUser();
        user.setUsername(username);
        user.setDisplayName(request.displayName().strip());
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        Access access = access(request.role() != null ? request.role() : UserRole.ADMIN, request.roleName(), request.pages());
        apply(user, access);
        AppUser saved = repository.saveAndFlush(user);
        String details = "role " + UserMapper.roleName(saved)
                + (access.role() == UserRole.SUPER_ADMIN ? "" : "; pages " + describe(access.pages()));
        audit.record(SecurityEventType.USER_CREATED, actorId, saved.getId(), username, details);
        return mapper.toResponse(saved);
    }

    @Transactional
    public UserResponse update(long actorId, long id, UserUpdateRequest request) {
        lockSuperAdmins(actorId);
        AppUser user = loadForUpdate(id);
        ConflictException.requireCurrentVersion(request.version(), user.getVersion());
        String previousUsername = user.getUsername();
        String username = request.username() == null ? previousUsername : checkedUsername(request.username(), id);
        String displayName = request.displayName().strip();
        String email = checkedEmail(request.email(), id);
        boolean usernameChanged = !username.equals(previousUsername);
        boolean nameChanged = !displayName.equals(user.getDisplayName());
        boolean emailChanged = !email.equals(user.getEmail());
        if (!usernameChanged && !nameChanged && !emailChanged) {
            return mapper.toResponse(user);
        }
        List<String> changes = new ArrayList<>();
        if (usernameChanged) {
            user.setUsername(username);
            // the session still carries the old name, so they sign in again with the new one
            user.revokeSessions();
            changes.add("username " + previousUsername + " -> " + username);
        }
        if (nameChanged) {
            changes.add("display name changed");
        }
        if (emailChanged) {
            changes.add("email changed");
        }
        user.setDisplayName(displayName);
        user.setEmail(email);
        AppUser saved = repository.saveAndFlush(user);
        audit.record(SecurityEventType.USER_UPDATED, actorId, id, username, String.join(", ", changes));
        return mapper.toResponse(saved);
    }

    // the unique index on lower(username) backs this up if two requests race
    private String checkedUsername(String raw, long userId) {
        String username = Usernames.normalize(raw);
        if (!Usernames.isValid(username)) {
            throw new InvalidRequestException(Usernames.RULE);
        }
        if (repository.existsByUsernameExcept(username, userId)) {
            throw new ConflictException("That username is already taken.");
        }
        return username;
    }

    // the unique index backs this up if two requests race
    private String checkedEmail(String raw, Long userId) {
        String email = Emails.normalize(raw);
        if (!Emails.isValid(email)) {
            throw new InvalidRequestException(Emails.RULE);
        }
        if (repository.existsByEmail(email, userId)) {
            throw new ConflictException("Another user already has that email.");
        }
        return email;
    }

    @Transactional
    public UserResponse changeRole(long actorId, long id, UserRoleRequest request) {
        List<AppUser> superAdmins = lockSuperAdmins(actorId);
        if (id == actorId) {
            throw new BusinessRuleException("You can't change your own role. Ask another Super Admin to do it.");
        }
        AppUser user = loadForUpdate(id);
        ConflictException.requireCurrentVersion(request.version(), user.getVersion());
        Access access = access(request.role(), request.roleName(), request.pages());
        boolean roleChanged = user.getRole() != access.role() || !Objects.equals(user.getRoleLabel(), access.label());
        boolean pagesChanged = access.role() != UserRole.SUPER_ADMIN && !Set.copyOf(user.getPages()).equals(access.pages());
        if (!roleChanged && !pagesChanged) {
            return mapper.toResponse(user);
        }
        if (user.isActiveSuperAdmin() && access.role() != UserRole.SUPER_ADMIN) {
            requireAnotherActiveSuperAdmin(superAdmins, user);
        }
        String previous = UserMapper.roleName(user);
        apply(user, access);
        // a new role ends their sessions; new pages apply on their next request anyway (PageAccess)
        if (roleChanged) {
            user.revokeSessions();
        }
        AppUser saved = repository.saveAndFlush(user);
        List<String> details = new ArrayList<>();
        if (roleChanged) {
            details.add(previous + " -> " + UserMapper.roleName(saved));
        }
        if (access.role() != UserRole.SUPER_ADMIN) {
            details.add("pages " + describe(access.pages()));
        }
        audit.record(SecurityEventType.ROLE_CHANGED, actorId, id, user.getUsername(), String.join("; ", details));
        return mapper.toResponse(saved);
    }

    // the role and the pages that go with it, checked
    private record Access(UserRole role, String label, Set<AppPage> pages) {
    }

    // Super Admin: no name, no pages (opens everything). Admin: no name; every page when left out.
    // Other (CUSTOM): a name and at least one page.
    private static Access access(UserRole role, String rawName, Set<AppPage> requested) {
        boolean named = rawName != null && !rawName.isBlank();
        if (role == UserRole.SUPER_ADMIN) {
            if (named || requested != null && !requested.isEmpty()) {
                throw new InvalidRequestException("A Super Admin opens every page and has no other role name.");
            }
            return new Access(role, null, EnumSet.noneOf(AppPage.class));
        }
        if (role == UserRole.ADMIN && named) {
            throw new InvalidRequestException("Only the Other role takes a role name.");
        }
        String label = role == UserRole.CUSTOM ? checkedLabel(rawName) : null;
        if (requested == null && role == UserRole.ADMIN) {
            return new Access(role, null, EnumSet.allOf(AppPage.class));
        }
        if (requested == null || requested.isEmpty()) {
            throw new InvalidRequestException("Tick at least one page.");
        }
        Set<AppPage> pages = EnumSet.noneOf(AppPage.class);
        pages.addAll(requested);
        return new Access(role, label, pages);
    }

    private static String checkedLabel(String raw) {
        String label = raw == null ? "" : raw.strip().replaceAll("\\s+", " ");
        if (label.isEmpty()) {
            throw new InvalidRequestException("Type the role name, e.g. Supervisor.");
        }
        if (label.length() > AppUser.ROLE_LABEL_MAX_LENGTH) {
            throw new InvalidRequestException("A role name is at most " + AppUser.ROLE_LABEL_MAX_LENGTH + " characters.");
        }
        if (RESERVED_LABELS.contains(label.toLowerCase(Locale.ROOT))) {
            throw new InvalidRequestException("\"" + label + "\" is a built-in role. Pick it above, or use another name.");
        }
        return label;
    }

    private static void apply(AppUser user, Access access) {
        user.setRole(access.role());
        user.setRoleLabel(access.label());
        user.getPages().clear();
        user.getPages().addAll(access.pages());
    }

    private static String describe(Set<AppPage> pages) {
        return pages.stream().map(Enum::name).collect(Collectors.joining(", "));
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

    // default sort username; id as tie-breaker keeps paging stable
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

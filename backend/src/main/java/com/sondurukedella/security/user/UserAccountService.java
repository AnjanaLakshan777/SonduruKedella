package com.sondurukedella.security.user;

import com.sondurukedella.common.exception.BusinessRuleException;
import com.sondurukedella.security.audit.SecurityAuditService;
import com.sondurukedella.security.audit.SecurityEventType;
import com.sondurukedella.security.user.dto.CurrentUserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// What any signed-in user can do with their own account: read it, and change their password.
@Service
@RequiredArgsConstructor
public class UserAccountService {

    private final AppUserRepository repository;
    private final UserMapper mapper;
    private final PasswordEncoder passwordEncoder;
    private final SecurityAuditService audit;

    @Transactional(readOnly = true)
    public CurrentUserResponse current(long userId) {
        return repository.findById(userId).filter(AppUser::isEnabled).map(mapper::toCurrentUser)
                .orElseThrow(() -> new AccessDeniedException("The account is no longer active."));
    }

    // Is a session signed in with this auth version still good? False once the account is
    // disabled, or its role or password changed.
    @Transactional(readOnly = true)
    public boolean isSessionCurrent(long userId, long authVersion) {
        return repository.findActiveAuthVersion(userId).map(v -> v == authVersion).orElse(false);
    }

    // Returns the principal for the caller's refreshed session. Every other session of the user ends,
    // because the auth version moves on.
    @Transactional
    public AppUserPrincipal changeOwnPassword(long userId, String currentPassword, String newPassword) {
        AppUser user = repository.findByIdForUpdate(userId).filter(AppUser::isEnabled)
                .orElseThrow(() -> new AccessDeniedException("The account is no longer active."));
        if (PasswordPolicy.tooLongToHash(currentPassword) || !passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new WrongPasswordException();
        }
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new BusinessRuleException("The new password must be different from the current one.");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.revokeSessions();
        AppUser saved = repository.saveAndFlush(user);
        audit.record(SecurityEventType.PASSWORD_CHANGED, userId, userId, user.getUsername(), null);
        return AppUserPrincipal.of(saved);
    }

    public static class WrongPasswordException extends RuntimeException {

        public WrongPasswordException() {
            super("The current password is incorrect.");
        }
    }
}

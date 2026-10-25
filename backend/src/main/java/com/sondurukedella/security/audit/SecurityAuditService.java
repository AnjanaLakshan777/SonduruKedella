package com.sondurukedella.security.audit;

import jakarta.servlet.http.HttpServletRequest;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

// Writes the security audit trail. Joins the caller's transaction, so an account change and its
// event commit or roll back together; a sign-in attempt (no outer transaction) commits on its own.
// Callers must never pass a password or anything derived from one.
@Service
@RequiredArgsConstructor
public class SecurityAuditService {

    private final SecurityEventRepository repository;

    @Transactional
    public void record(SecurityEventType type, Long actorUserId, Long targetUserId, String username, String details) {
        repository.save(new SecurityEvent(type, actorUserId, targetUserId, cut(username, SecurityEvent.USERNAME_MAX_LENGTH),
                remoteAddress(), cut(details, SecurityEvent.DETAILS_MAX_LENGTH), OffsetDateTime.now()));
    }

    // the direct peer address only; X-Forwarded-For is not trusted because anyone can send it
    private static String remoteAddress() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            HttpServletRequest request = attributes.getRequest();
            return cut(request.getRemoteAddr(), 45);
        }
        return null;
    }

    private static String cut(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}

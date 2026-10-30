package com.sondurukedella.security.user.dto;

import com.sondurukedella.security.user.UserRole;
import java.time.OffsetDateTime;

// no hash, no auth_version
public record UserResponse(
        long id,
        String username,
        String displayName,
        String email,
        UserRole role,
        boolean enabled,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        long version) {
}

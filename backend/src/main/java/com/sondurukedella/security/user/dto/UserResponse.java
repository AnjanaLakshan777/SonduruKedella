package com.sondurukedella.security.user.dto;

import com.sondurukedella.security.user.UserRole;
import java.time.OffsetDateTime;

// What user administration shows. Never the password hash or auth_version.
public record UserResponse(
        long id,
        String username,
        String displayName,
        UserRole role,
        boolean enabled,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        long version) {
}

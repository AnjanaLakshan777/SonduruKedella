package com.sondurukedella.security.user.dto;

import com.sondurukedella.security.access.AppPage;
import com.sondurukedella.security.user.UserRole;
import java.time.OffsetDateTime;
import java.util.Set;

// no hash, no auth_version. roleName: Super Admin, Admin or the "Other" name; pages: all for a Super Admin
public record UserResponse(
        long id,
        String username,
        String displayName,
        String email,
        UserRole role,
        String roleName,
        Set<AppPage> pages,
        boolean enabled,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        long version) {
}

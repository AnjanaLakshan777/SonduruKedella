package com.sondurukedella.security.user.dto;

import com.sondurukedella.security.access.AppPage;
import com.sondurukedella.security.user.UserRole;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Set;

// role and page permissions together, same rules as on create
public record UserRoleRequest(
        @NotNull UserRole role,
        @Size(max = 50) String roleName,
        Set<@NotNull AppPage> pages,
        @NotNull Long version) {
}

package com.sondurukedella.security.user.dto;

import com.sondurukedella.security.user.UserRole;
import jakarta.validation.constraints.NotNull;

public record UserRoleRequest(@NotNull UserRole role, @NotNull Long version) {
}

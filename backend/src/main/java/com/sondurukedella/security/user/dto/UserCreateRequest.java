package com.sondurukedella.security.user.dto;

import com.sondurukedella.security.access.AppPage;
import com.sondurukedella.security.user.UserRole;
import com.sondurukedella.security.user.ValidPassword;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Set;

// role defaults to ADMIN. roleName is the "Other" role's name (CUSTOM only).
// pages: required for CUSTOM; an Admin gets every page when left out; a Super Admin takes none.
public record UserCreateRequest(
        @NotBlank(message = "Enter a username.") @Size(max = 100) String username,
        @NotBlank(message = "Enter the person's name.") @Size(max = 150) String displayName,
        @NotBlank(message = "Enter an email address.") @Size(max = 254) String email,
        @ValidPassword String password,
        UserRole role,
        @Size(max = 50) String roleName,
        Set<@NotNull AppPage> pages) {
}

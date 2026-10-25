package com.sondurukedella.security.user.dto;

import com.sondurukedella.security.user.UserRole;
import com.sondurukedella.security.user.ValidPassword;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// A new account, created by a Super Admin. role defaults to ADMIN when left out.
public record UserCreateRequest(
        @NotBlank(message = "Enter a username.") @Size(max = 100) String username,
        @NotBlank(message = "Enter the person's name.") @Size(max = 150) String displayName,
        @ValidPassword String password,
        UserRole role) {
}

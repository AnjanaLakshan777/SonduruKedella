package com.sondurukedella.security.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// name and email only - role/status/password have their own endpoints
public record UserUpdateRequest(
        @NotBlank(message = "Enter the person's name.") @Size(max = 150) String displayName,
        @NotBlank(message = "Enter an email address.") @Size(max = 254) String email,
        @NotNull Long version) {
}

package com.sondurukedella.security.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// Only the display name is editable here; role, status and password have their own endpoints.
// The username is fixed once created.
public record UserUpdateRequest(
        @NotBlank(message = "Enter the person's name.") @Size(max = 150) String displayName,
        @NotNull Long version) {
}

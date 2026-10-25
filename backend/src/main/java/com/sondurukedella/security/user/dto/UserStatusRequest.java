package com.sondurukedella.security.user.dto;

import jakarta.validation.constraints.NotNull;

// enabled=false signs the user out everywhere and blocks new sign-ins; the account and everything
// it recorded stay.
public record UserStatusRequest(@NotNull Boolean enabled, @NotNull Long version) {
}

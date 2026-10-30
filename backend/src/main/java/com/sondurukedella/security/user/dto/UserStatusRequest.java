package com.sondurukedella.security.user.dto;

import jakarta.validation.constraints.NotNull;

// enabled=false logs them out everywhere, their records stay
public record UserStatusRequest(@NotNull Boolean enabled, @NotNull Long version) {
}

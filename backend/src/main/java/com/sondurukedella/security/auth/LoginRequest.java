package com.sondurukedella.security.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// just sanity limits; password rules only apply when setting a password
public record LoginRequest(
        @NotBlank(message = "Enter your username or email.") @Size(max = 254) String username,
        @NotBlank(message = "Enter your password.") @Size(max = 200) String password) {

    // keep the password out of logs
    @Override
    public String toString() {
        return "LoginRequest[username=" + username + "]";
    }
}

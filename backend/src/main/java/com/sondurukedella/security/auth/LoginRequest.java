package com.sondurukedella.security.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Size limits only stop absurd input; the password rules apply when a password is set, not here.
public record LoginRequest(
        @NotBlank(message = "Enter your username.") @Size(max = 100) String username,
        @NotBlank(message = "Enter your password.") @Size(max = 200) String password) {

    // so a request object never prints the password if something logs it
    @Override
    public String toString() {
        return "LoginRequest[username=" + username + "]";
    }
}

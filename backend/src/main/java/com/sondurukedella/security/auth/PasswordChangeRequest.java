package com.sondurukedella.security.auth;

import com.sondurukedella.security.user.ValidPassword;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordChangeRequest(
        @NotBlank(message = "Enter your current password.") @Size(max = 200) String currentPassword,
        @ValidPassword String newPassword) {

    @Override
    public String toString() {
        return "PasswordChangeRequest[]";
    }
}

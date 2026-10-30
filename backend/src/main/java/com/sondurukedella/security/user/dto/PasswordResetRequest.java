package com.sondurukedella.security.user.dto;

import com.sondurukedella.security.user.ValidPassword;

// the new password is never sent back
public record PasswordResetRequest(@ValidPassword String newPassword) {
}

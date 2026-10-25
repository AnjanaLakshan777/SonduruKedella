package com.sondurukedella.security.user.dto;

import com.sondurukedella.security.user.ValidPassword;

// A Super Admin setting a new password for someone who forgot theirs. The new password is never
// sent back; the Super Admin passes it on in person.
public record PasswordResetRequest(@ValidPassword String newPassword) {
}

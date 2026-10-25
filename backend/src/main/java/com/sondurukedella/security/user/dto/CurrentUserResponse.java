package com.sondurukedella.security.user.dto;

import com.sondurukedella.security.user.UserRole;

// The signed-in user, returned by login and GET /api/v1/auth/me. The UI uses role to decide what
// to show; the backend enforces it regardless.
public record CurrentUserResponse(long id, String username, String displayName, UserRole role) {
}

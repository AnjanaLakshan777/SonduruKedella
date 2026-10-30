package com.sondurukedella.security.user.dto;

import com.sondurukedella.security.user.UserRole;

// role is for the UI only, the backend checks it anyway
public record CurrentUserResponse(long id, String username, String displayName, String email, UserRole role) {
}

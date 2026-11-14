package com.sondurukedella.security.user.dto;

import com.sondurukedella.security.access.ApiArea;
import com.sondurukedella.security.access.AppPage;
import com.sondurukedella.security.user.UserRole;
import java.util.Set;

// For the UI only, the backend checks everything again.
// roleName is what the profile shows (Super Admin, Admin or the custom role's name),
// pages are the ones the user can open, readAreas the data the UI may load.
// mustChangePassword: first sign-in with the starting password; the UI asks for a new one first.
public record CurrentUserResponse(long id, String username, String displayName, String email, UserRole role,
        String roleName, Set<AppPage> pages, Set<ApiArea> readAreas, boolean mustChangePassword) {
}

package com.sondurukedella.security.user;

import com.sondurukedella.security.access.ApiArea;
import com.sondurukedella.security.access.AppPage;
import com.sondurukedella.security.user.dto.CurrentUserResponse;
import com.sondurukedella.security.user.dto.UserResponse;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public UserResponse toResponse(AppUser user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getEmail(),
                user.getRole(),
                roleName(user),
                pagesOf(user),
                user.isEnabled(),
                user.getCreatedAt(),
                user.getUpdatedAt(),
                user.getVersion());
    }

    public CurrentUserResponse toCurrentUser(AppUser user) {
        Set<AppPage> pages = pagesOf(user);
        Set<ApiArea> readAreas = user.getRole() == UserRole.SUPER_ADMIN ? EnumSet.allOf(ApiArea.class) : AppPage.readable(pages);
        return new CurrentUserResponse(user.getId(), user.getUsername(), user.getDisplayName(), user.getEmail(), user.getRole(),
                roleName(user), pages, readAreas);
    }

    static String roleName(AppUser user) {
        return switch (user.getRole()) {
            case SUPER_ADMIN -> "Super Admin";
            case ADMIN -> "Admin";
            case CUSTOM -> user.getRoleLabel();
        };
    }

    // a Super Admin opens every page
    static Set<AppPage> pagesOf(AppUser user) {
        if (user.getRole() == UserRole.SUPER_ADMIN) {
            return EnumSet.allOf(AppPage.class);
        }
        Set<AppPage> pages = EnumSet.noneOf(AppPage.class);
        pages.addAll(user.getPages());
        return pages;
    }
}

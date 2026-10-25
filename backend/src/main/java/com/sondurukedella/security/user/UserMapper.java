package com.sondurukedella.security.user;

import com.sondurukedella.security.user.dto.CurrentUserResponse;
import com.sondurukedella.security.user.dto.UserResponse;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public UserResponse toResponse(AppUser user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getRole(),
                user.isEnabled(),
                user.getCreatedAt(),
                user.getUpdatedAt(),
                user.getVersion());
    }

    public CurrentUserResponse toCurrentUser(AppUser user) {
        return new CurrentUserResponse(user.getId(), user.getUsername(), user.getDisplayName(), user.getRole());
    }
}

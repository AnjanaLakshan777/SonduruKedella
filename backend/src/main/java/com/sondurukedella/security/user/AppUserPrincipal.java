package com.sondurukedella.security.user;

import com.sondurukedella.common.audit.AuditUser;
import java.io.Serial;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

// What we keep in the session. The password hash is only there during the login check.
public final class AppUserPrincipal implements UserDetails, CredentialsContainer, AuditUser {

    @Serial
    private static final long serialVersionUID = 1L;

    private final long id;
    private final String username;
    private final String displayName;
    private final UserRole role;
    private final long authVersion;
    private final boolean enabled;
    private String password;

    private AppUserPrincipal(long id, String username, String displayName, UserRole role, long authVersion,
            boolean enabled, String password) {
        this.id = id;
        this.username = username;
        this.displayName = displayName;
        this.role = role;
        this.authVersion = authVersion;
        this.enabled = enabled;
        this.password = password;
    }

    static AppUserPrincipal withPassword(AppUser user) {
        return new AppUserPrincipal(user.getId(), user.getUsername(), user.getDisplayName(), user.getRole(),
                user.getAuthVersion(), user.isEnabled(), user.getPasswordHash());
    }

    public static AppUserPrincipal of(AppUser user) {
        return new AppUserPrincipal(user.getId(), user.getUsername(), user.getDisplayName(), user.getRole(),
                user.getAuthVersion(), user.isEnabled(), null);
    }

    public AppUserPrincipal withoutPassword() {
        return new AppUserPrincipal(id, username, displayName, role, authVersion, enabled, null);
    }

    public long id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public UserRole role() {
        return role;
    }

    public long authVersion() {
        return authVersion;
    }

    @Override
    public long userId() {
        return id;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(role.authority()));
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void eraseCredentials() {
        password = null;
    }

    @Override
    public String toString() {
        return "AppUserPrincipal[id=" + id + ", username=" + username + ", role=" + role + "]";
    }
}

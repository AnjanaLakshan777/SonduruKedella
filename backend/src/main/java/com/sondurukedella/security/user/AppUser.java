package com.sondurukedella.security.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// A person who can sign in. Never deleted (every created_by column points here), only deactivated.
// No toString on purpose, so the password hash can't end up in a log line.
@Getter
@Setter
@Entity
@Table(name = "app_users")
@NoArgsConstructor
public class AppUser {

    @Id
    @Setter(AccessLevel.NONE)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // stored lower case by the application (see Usernames); rows from before V5 may differ in case,
    // so lookups compare lower(username)
    @Column(nullable = false, length = 100)
    private String username;

    @Column(name = "display_name", nullable = false, length = 150)
    private String displayName;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;

    @Column(nullable = false)
    private boolean enabled = true;

    // bumped when access changes; sessions signed in with an older value stop working
    @Setter(AccessLevel.NONE)
    @Column(name = "auth_version", nullable = false)
    private long authVersion;

    @Setter(AccessLevel.NONE)
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Setter(AccessLevel.NONE)
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    @Setter(AccessLevel.NONE)
    private Long version;

    public boolean isActiveSuperAdmin() {
        return enabled && role == UserRole.SUPER_ADMIN;
    }

    // Ends every existing session of this user on its next request.
    public void revokeSessions() {
        authVersion++;
    }

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}

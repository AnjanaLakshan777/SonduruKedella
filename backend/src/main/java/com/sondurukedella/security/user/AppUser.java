package com.sondurukedella.security.user;

import com.sondurukedella.security.access.AppPage;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Users are never deleted (created_by everywhere points here), just disabled.
// No toString on purpose so the hash never ends up in a log.
@Getter
@Setter
@Entity
@Table(name = "app_users")
@NoArgsConstructor
public class AppUser {

    public static final int ROLE_LABEL_MAX_LENGTH = 50;

    @Id
    @Setter(AccessLevel.NONE)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // we store it lower case; older rows might not be, so lookups use lower()
    @Column(nullable = false, length = 100)
    private String username;

    @Column(name = "display_name", nullable = false, length = 150)
    private String displayName;

    // always lower case (see Emails); you can sign in with it too
    @Column(nullable = false, length = 254)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;

    // the "Other" role's name (Supervisor, Auditor, ...); only for CUSTOM (ck_app_users_role_label)
    @Column(name = "role_label", length = ROLE_LABEL_MAX_LENGTH)
    private String roleLabel;

    // what this user may open; Admins and custom roles only, Super Admins open everything.
    // Small set, and every request checks it (PageAccess).
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "app_user_pages", joinColumns = @JoinColumn(name = "user_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "page", nullable = false, length = 30)
    private Set<AppPage> pages = EnumSet.noneOf(AppPage.class);

    @Column(nullable = false)
    private boolean enabled = true;

    // bump this to kill the user's existing sessions
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

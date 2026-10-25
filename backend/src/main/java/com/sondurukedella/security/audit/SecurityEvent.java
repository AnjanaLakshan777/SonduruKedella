package com.sondurukedella.security.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// One row of the security audit trail. Append-only (a V5 trigger refuses updates and deletes), so
// no setters. Never holds a password, hash or session id.
@Getter
@Entity
@Table(name = "security_events")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SecurityEvent {

    public static final int USERNAME_MAX_LENGTH = 100;
    public static final int DETAILS_MAX_LENGTH = 300;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 40, updatable = false)
    private SecurityEventType type;

    @Column(name = "actor_user_id", updatable = false)
    private Long actorUserId;

    @Column(name = "target_user_id", updatable = false)
    private Long targetUserId;

    @Column(length = USERNAME_MAX_LENGTH, updatable = false)
    private String username;

    @Column(name = "remote_address", length = 45, updatable = false)
    private String remoteAddress;

    @Column(length = DETAILS_MAX_LENGTH, updatable = false)
    private String details;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private OffsetDateTime occurredAt;

    SecurityEvent(SecurityEventType type, Long actorUserId, Long targetUserId, String username, String remoteAddress,
            String details, OffsetDateTime occurredAt) {
        this.type = type;
        this.actorUserId = actorUserId;
        this.targetUserId = targetUserId;
        this.username = username;
        this.remoteAddress = remoteAddress;
        this.details = details;
        this.occurredAt = occurredAt;
    }
}

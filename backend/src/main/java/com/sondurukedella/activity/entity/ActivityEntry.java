package com.sondurukedella.activity.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// activity_log is append-only (no updated_at or version), so this doesn't extend AuditableEntity
// and has no setters.
@Getter
@Entity
@Table(name = "activity_log")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ActivityEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", updatable = false)
    private Long projectId;

    @Column(name = "activity_date", nullable = false, updatable = false)
    private LocalDate activityDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private ActivityKind kind;

    @Column(nullable = false, columnDefinition = "text", updatable = false)
    private String message;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    // stays null until there are application users to attribute entries to
    @Column(name = "created_by", updatable = false)
    private Long createdBy;

    public ActivityEntry(Long projectId, LocalDate activityDate, ActivityKind kind, String message,
            OffsetDateTime createdAt) {
        this.projectId = projectId;
        this.activityDate = activityDate;
        this.kind = kind;
        this.message = message;
        this.createdAt = createdAt;
    }
}

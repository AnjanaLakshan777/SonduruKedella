package com.sondurukedella.agreements.entity;

import com.sondurukedella.common.audit.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

// One row per uploaded PDF. The current agreement is the row with removed_at null (the partial
// unique index uq_project_agreements_current allows one per project). Replacing sets removed_at on
// the old row instead of deleting it, so its file and metadata stay together.
// projectId is a plain id: Project belongs to the projects module.
@Getter
@Entity
@Table(name = "project_agreements")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectAgreement extends AuditableEntity {

    public static final int FILENAME_MAX_LENGTH = 255;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false, updatable = false)
    private Long projectId;

    @Column(name = "original_filename", nullable = false, length = FILENAME_MAX_LENGTH, updatable = false)
    private String originalFilename;

    @Column(name = "content_type", nullable = false, length = 100, updatable = false)
    private String contentType;

    @Column(name = "size_bytes", nullable = false, updatable = false)
    private long sizeBytes;

    // CHAR(64) in V1, so tell Hibernate it's a fixed-length char or schema validation fails
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 64, updatable = false)
    private String sha256;

    // Server-generated name inside the storage directory. Never sent to clients.
    @Column(name = "storage_key", nullable = false, length = 500, updatable = false)
    private String storageKey;

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private OffsetDateTime uploadedAt;

    @Column(name = "removed_at")
    private OffsetDateTime removedAt;

    @Version
    private Long version;

    public ProjectAgreement(long projectId, String originalFilename, String contentType, long sizeBytes,
            String sha256, String storageKey, OffsetDateTime uploadedAt) {
        this.projectId = projectId;
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.sha256 = sha256;
        this.storageKey = storageKey;
        this.uploadedAt = uploadedAt;
    }

    public void markRemoved(OffsetDateTime when) {
        this.removedAt = when;
    }
}

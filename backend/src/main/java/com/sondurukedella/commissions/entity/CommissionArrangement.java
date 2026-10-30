package com.sondurukedella.commissions.entity;

import com.sondurukedella.common.audit.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// contract value x rate for one recipient. PENDING = draft, CONFIRMED = stored amount and frozen
// (V6 trigger), then optionally VOIDED. Recipient is just a name like in the UI, no recipients table.
@Getter
@Entity
@Table(name = "commission_arrangements")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommissionArrangement extends AuditableEntity {

    public static final int RECIPIENT_MAX_LENGTH = 150;
    public static final int ROLE_MAX_LENGTH = 100;
    public static final String DEFAULT_ROLE = "Commission";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false, updatable = false)
    private Long projectId;

    // part of the composite FK - keeps consultation projects out
    @Column(name = "project_type", nullable = false, length = 20, updatable = false)
    private String projectType;

    @Column(name = "recipient_name", nullable = false, length = RECIPIENT_MAX_LENGTH)
    private String recipientName;

    @Column(nullable = false, length = ROLE_MAX_LENGTH)
    private String role;

    @Column(name = "rate_pct", nullable = false, precision = 7, scale = 4)
    private BigDecimal ratePct;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CommissionStatus status = CommissionStatus.PENDING;

    @Column(name = "confirmed_on")
    private LocalDate confirmedOn;

    // stored on confirm so changing the settings later doesn't change it
    @Column(name = "commission_amount", precision = 15, scale = 2)
    private BigDecimal commissionAmount;

    // they confirmed it even though it went over the policy
    @Column(name = "policy_exception", nullable = false)
    private boolean policyException;

    @Column(name = "voided_at")
    private OffsetDateTime voidedAt;

    @Column(name = "void_reason", columnDefinition = "text")
    private String voidReason;

    @Version
    private Long version;

    public CommissionArrangement(long projectId, String projectType, String recipientName, String role, BigDecimal ratePct) {
        this.projectId = projectId;
        this.projectType = projectType;
        this.recipientName = recipientName;
        this.role = role;
        this.ratePct = ratePct;
    }

    public boolean isPending() {
        return status == CommissionStatus.PENDING;
    }

    public boolean isConfirmed() {
        return status == CommissionStatus.CONFIRMED;
    }

    // drafts only
    public void revise(String recipientName, String role, BigDecimal ratePct) {
        this.recipientName = recipientName;
        this.role = role;
        this.ratePct = ratePct;
    }

    public void confirm(LocalDate on, BigDecimal amount, boolean policyException) {
        this.status = CommissionStatus.CONFIRMED;
        this.confirmedOn = on;
        this.commissionAmount = amount;
        this.policyException = policyException;
    }

    public void markVoided(OffsetDateTime when, String reason) {
        this.status = CommissionStatus.VOIDED;
        this.voidedAt = when;
        this.voidReason = reason;
    }
}

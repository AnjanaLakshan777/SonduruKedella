package com.sondurukedella.payroll.entity;

import com.sondurukedella.common.audit.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import jakarta.persistence.Version;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// A change to a frozen week's wages: positive or negative, with a reason. It changes only what is owed,
// never the advance deduction, so an advance can't be recovered twice. Never edited: a wrong one is voided
// with a reason and a new one recorded (V7), so the history stays. requestId makes a resend harmless.
@Getter
@Entity
@Table(name = "payroll_corrections")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PayrollCorrection extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payroll_line_id", nullable = false, updatable = false)
    private Long payrollLineId;

    @Column(name = "correction_date", nullable = false, updatable = false)
    private LocalDate correctionDate;

    @Column(name = "wage_delta", nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal wageDelta;

    @Column(name = "project_id", updatable = false)
    private Long projectId;

    @Column(nullable = false, columnDefinition = "text", updatable = false)
    private String reason;

    @Column(name = "request_id", updatable = false)
    private UUID requestId;

    @Column(name = "voided_at")
    private OffsetDateTime voidedAt;

    @Column(name = "void_reason", columnDefinition = "text")
    private String voidReason;

    @Version
    private Long version;

    public PayrollCorrection(long payrollLineId, LocalDate correctionDate, BigDecimal wageDelta, Long projectId, String reason,
            UUID requestId) {
        this.requestId = requestId;
        this.payrollLineId = payrollLineId;
        this.correctionDate = correctionDate;
        this.wageDelta = wageDelta;
        this.projectId = projectId;
        this.reason = reason;
    }

    public boolean isVoided() {
        return voidedAt != null;
    }

    public void markVoided(OffsetDateTime when, String reason) {
        this.voidedAt = when;
        this.voidReason = reason;
    }
}

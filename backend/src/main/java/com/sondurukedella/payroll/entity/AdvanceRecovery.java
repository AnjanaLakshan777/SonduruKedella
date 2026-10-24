package com.sondurukedella.payroll.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

// Which advance a frozen week's advance deduction recovered, and how much (oldest advance first).
@Getter
@Entity
@Immutable
@Table(name = "advance_recoveries")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdvanceRecovery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "advance_id", nullable = false, updatable = false)
    private Long advanceId;

    @Column(name = "payroll_line_id", nullable = false, updatable = false)
    private Long payrollLineId;

    @Column(name = "employee_id", nullable = false, updatable = false)
    private Long employeeId;

    @Column(nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = OffsetDateTime.now();
    }

    public AdvanceRecovery(long advanceId, long payrollLineId, long employeeId, BigDecimal amount) {
        this.advanceId = advanceId;
        this.payrollLineId = payrollLineId;
        this.employeeId = employeeId;
        this.amount = amount;
    }
}

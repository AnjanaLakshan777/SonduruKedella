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

// A frozen week's gross pay split by project; the rows of one line add up to its gross exactly.
// projectId null is labour not tied to a project (yard or office work), a company cost.
@Getter
@Entity
@Immutable
@Table(name = "payroll_line_costs")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PayrollLineCost {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payroll_line_id", nullable = false, updatable = false)
    private Long payrollLineId;

    @Column(name = "project_id", updatable = false)
    private Long projectId;

    @Column(nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = OffsetDateTime.now();
    }

    public PayrollLineCost(long payrollLineId, Long projectId, BigDecimal amount) {
        this.payrollLineId = payrollLineId;
        this.projectId = projectId;
        this.amount = amount;
    }
}

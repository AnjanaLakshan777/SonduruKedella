package com.sondurukedella.revenue.entity;

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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Agreed delay terms for a construction project. The applied-deduction columns are mapped so
// Hibernate validation sees the whole table, but nothing sets them until deductions are built.
// They stay separate from the contract value on purpose - a deduction never changes it.
@Getter
@Setter
@Entity
@Table(name = "delay_deduction_terms")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DelayDeductionTerms extends AuditableEntity {

    @Id
    @Setter(AccessLevel.NONE)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter(AccessLevel.NONE)
    @Column(name = "project_id", nullable = false, updatable = false)
    private Long projectId;

    // Copy of the project's type; the composite FK to projects (id, project_type) plus the
    // CONSTRUCTION check make the database refuse terms on any other project type.
    @Setter(AccessLevel.NONE)
    @Column(name = "project_type", nullable = false, length = 20, updatable = false)
    private String projectType;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "contract_deadline", nullable = false)
    private LocalDate contractDeadline;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DelayRule rule;

    @Column(name = "rate_per_day", precision = 15, scale = 2)
    private BigDecimal ratePerDay;

    @Column(name = "percent_per_week", precision = 7, scale = 4)
    private BigDecimal percentPerWeek;

    @Column(name = "fixed_amount", precision = 15, scale = 2)
    private BigDecimal fixedAmount;

    @Column(name = "grace_days", nullable = false)
    private int graceDays;

    @Column(name = "max_deduction", precision = 15, scale = 2)
    private BigDecimal maxDeduction;

    @Setter(AccessLevel.NONE)
    @Column(name = "actual_completion")
    private LocalDate actualCompletion;

    @Setter(AccessLevel.NONE)
    @Column(name = "applied_amount", precision = 15, scale = 2)
    private BigDecimal appliedAmount;

    @Setter(AccessLevel.NONE)
    @Column(name = "applied_on")
    private LocalDate appliedOn;

    @Setter(AccessLevel.NONE)
    @Column(name = "applied_installment_id")
    private Long appliedInstallmentId;

    @Column(columnDefinition = "text")
    private String notes;

    @Version
    @Setter(AccessLevel.NONE)
    private Long version;

    public DelayDeductionTerms(long projectId, String projectType) {
        this.projectId = projectId;
        this.projectType = projectType;
    }
}

package com.sondurukedella.payroll.entity;

import com.sondurukedella.common.audit.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

// The owner's overrides for one worker's week. advanceDeduction null means "the default" (all that is
// outstanding). Frozen with the week (trg_payroll_adjustments_frozen, V1).
@Getter
@Setter
@Entity
@Table(name = "payroll_adjustments")
@NoArgsConstructor
public class PayrollAdjustment extends AuditableEntity {

    @Id
    @Setter(AccessLevel.NONE)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_id", nullable = false, updatable = false)
    private Long employeeId;

    @Column(name = "week_start", nullable = false, updatable = false)
    private LocalDate weekStart;

    @Column(name = "advance_deduction", precision = 15, scale = 2)
    private BigDecimal advanceDeduction;

    @Column(name = "other_deduction", nullable = false, precision = 15, scale = 2)
    private BigDecimal otherDeduction;

    @Column(name = "other_note", columnDefinition = "text")
    private String otherNote;

    @Version
    @Setter(AccessLevel.NONE)
    private Long version;
}

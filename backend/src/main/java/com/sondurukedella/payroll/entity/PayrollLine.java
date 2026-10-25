package com.sondurukedella.payroll.entity;

import com.sondurukedella.common.audit.CurrentUser;
import com.sondurukedella.payroll.service.PayrollCalculator.Result;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

// The frozen snapshot of one worker's week, written when the week is first paid (or closed with nothing
// to pay) and never changed again: trg_payroll_lines_immutable (V1) refuses it, and its existence is what
// freezes that week's attendance, overtime and adjustment. Later changes are payroll_corrections.
@Getter
@Entity
@Immutable
@Table(name = "payroll_lines")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PayrollLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_id", nullable = false, updatable = false)
    private Long employeeId;

    @Column(name = "week_start", nullable = false, updatable = false)
    private LocalDate weekStart;

    @Column(name = "week_end", nullable = false, updatable = false)
    private LocalDate weekEnd;

    @Column(name = "present_days", nullable = false, updatable = false)
    private short presentDays;

    @Column(name = "half_days", nullable = false, updatable = false)
    private short halfDays;

    @Column(name = "payable_days", nullable = false, precision = 4, scale = 1, updatable = false)
    private BigDecimal payableDays;

    @Column(name = "daily_wage", nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal dailyWage;

    @Column(name = "base_pay", nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal basePay;

    @Column(name = "overtime_hours", nullable = false, precision = 5, scale = 2, updatable = false)
    private BigDecimal overtimeHours;

    @Column(name = "overtime_pay", nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal overtimePay;

    @Column(name = "gross_pay", nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal grossPay;

    @Column(name = "advance_deduction", nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal advanceDeduction;

    @Column(name = "other_deduction", nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal otherDeduction;

    @Column(name = "other_note", columnDefinition = "text", updatable = false)
    private String otherNote;

    @Column(name = "net_pay", nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal netPay;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "created_by", updatable = false)
    private Long createdBy;

    @PrePersist
    void onCreate() {
        createdAt = OffsetDateTime.now();
        createdBy = CurrentUser.idOrNull();
    }

    public static PayrollLine freeze(long employeeId, LocalDate weekStart, LocalDate weekEnd, BigDecimal dailyWage, Result r,
            String otherNote) {
        PayrollLine line = new PayrollLine();
        line.employeeId = employeeId;
        line.weekStart = weekStart;
        line.weekEnd = weekEnd;
        line.presentDays = (short) r.presentDays();
        line.halfDays = (short) r.halfDays();
        line.payableDays = r.payableDays();
        line.dailyWage = dailyWage;
        line.basePay = r.basePay();
        line.overtimeHours = r.overtimeHours();
        line.overtimePay = r.overtimePay();
        line.grossPay = r.grossPay();
        line.advanceDeduction = r.advanceDeduction();
        line.otherDeduction = r.otherDeduction();
        line.otherNote = otherNote;
        line.netPay = r.netPay();
        return line;
    }
}

package com.sondurukedella.employees.entity;

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

// Overtime entered by the site supervisor. Only approved hours are paid. Frozen with the week (V1 trigger).
@Getter
@Setter
@Entity
@Table(name = "overtime_entries")
@NoArgsConstructor
public class OvertimeEntry extends AuditableEntity {

    @Id
    @Setter(AccessLevel.NONE)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_id", nullable = false, updatable = false)
    private Long employeeId;

    @Column(name = "work_date", nullable = false, updatable = false)
    private LocalDate workDate;

    @Column(name = "project_id", updatable = false)
    private Long projectId;

    @Column(nullable = false, precision = 4, scale = 2, updatable = false)
    private BigDecimal hours;

    @Column(name = "hourly_rate", nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal hourlyRate;

    @Column(nullable = false)
    private boolean approved;

    @Column(name = "recorded_by", nullable = false, length = 150, updatable = false)
    private String recordedBy;

    @Column(columnDefinition = "text", updatable = false)
    private String notes;

    @Version
    @Setter(AccessLevel.NONE)
    private Long version;
}

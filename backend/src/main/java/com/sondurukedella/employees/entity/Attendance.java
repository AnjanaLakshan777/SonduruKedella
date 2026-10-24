package com.sondurukedella.employees.entity;

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
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// One worker's day. projectType is the copy that lets fk_attendance_project insist on a construction
// project; it is only ever set together with projectId. trg_attendance_frozen (V1) refuses any change
// once the worker's week has been paid.
@Getter
@Setter
@Entity
@Table(name = "attendance")
@NoArgsConstructor
public class Attendance extends AuditableEntity {

    public static final String CONSTRUCTION = "CONSTRUCTION";

    @Id
    @Setter(AccessLevel.NONE)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_id", nullable = false, updatable = false)
    private Long employeeId;

    @Column(name = "work_date", nullable = false, updatable = false)
    private LocalDate workDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AttendanceStatus status;

    @Column(name = "project_id")
    private Long projectId;

    @Column(name = "project_type", length = 20)
    private String projectType;

    @Column(columnDefinition = "text")
    private String notes;

    @Version
    @Setter(AccessLevel.NONE)
    private Long version;

    public Attendance(long employeeId, LocalDate workDate) {
        this.employeeId = employeeId;
        this.workDate = workDate;
    }

    public void setProject(Long projectId) {
        this.projectId = projectId;
        this.projectType = projectId == null ? null : CONSTRUCTION;
    }
}

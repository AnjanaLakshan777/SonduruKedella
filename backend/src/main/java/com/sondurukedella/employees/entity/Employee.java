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
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// A worker on the payroll. Never deleted - attendance and paid weeks point at it - so INACTIVE instead.
// dailyWage and overtimeRate are today's rates; a paid week keeps the rate it was paid at (payroll_lines).
@Getter
@Setter
@Entity
@Table(name = "employees")
@NoArgsConstructor
public class Employee extends AuditableEntity {

    @Id
    @Setter(AccessLevel.NONE)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, length = 100)
    private String role;

    @Column(length = 30)
    private String phone;

    @Column(length = 300)
    private String address;

    @Column(name = "daily_wage", nullable = false, precision = 15, scale = 2)
    private BigDecimal dailyWage;

    @Column(name = "overtime_rate", nullable = false, precision = 15, scale = 2)
    private BigDecimal overtimeRate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private EmployeeStatus status = EmployeeStatus.ACTIVE;

    @Column(name = "joined_on", nullable = false)
    private LocalDate joinedOn;

    @Version
    @Setter(AccessLevel.NONE)
    private Long version;
}

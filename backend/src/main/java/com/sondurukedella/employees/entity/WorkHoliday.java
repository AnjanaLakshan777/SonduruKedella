package com.sondurukedella.employees.entity;

import com.sondurukedella.common.audit.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// A date the owner marked as a day off for everyone; no attendance goes on it.
@Getter
@Setter
@Entity
@Table(name = "work_holidays")
@NoArgsConstructor
public class WorkHoliday extends AuditableEntity {

    @Id
    @Setter(AccessLevel.NONE)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "holiday_date", nullable = false, updatable = false)
    private LocalDate date;

    @Column(length = 200)
    private String note;

    public WorkHoliday(LocalDate date) {
        this.date = date;
    }
}

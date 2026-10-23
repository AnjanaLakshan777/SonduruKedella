package com.sondurukedella.settings.entity;

import com.sondurukedella.common.audit.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Single-row table (id = 1). Defaults for new employees and the suggested overtime rate. */
@Getter
@Setter
@Entity
@Table(name = "wage_settings")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WageSettings extends AuditableEntity {

    public static final short SINGLETON_ID = 1;

    @Id
    @Setter(AccessLevel.NONE)
    private Short id;

    @Column(name = "default_daily_wage", nullable = false, precision = 15, scale = 2)
    private BigDecimal defaultDailyWage;

    @Column(name = "hours_per_day", nullable = false, precision = 4, scale = 2)
    private BigDecimal hoursPerDay;

    @Column(name = "overtime_multiplier", nullable = false, precision = 5, scale = 2)
    private BigDecimal overtimeMultiplier;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Payday payday;

    @Version
    @Setter(AccessLevel.NONE)
    private Long version;
}

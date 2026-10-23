package com.sondurukedella.settings.entity;

import com.sondurukedella.common.audit.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Single-row table (id = 1). Both percentages only trigger warnings on commission arrangements. */
@Getter
@Setter
@Entity
@Table(name = "commission_policy")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommissionPolicy extends AuditableEntity {

    public static final short SINGLETON_ID = 1;

    @Id
    @Setter(AccessLevel.NONE)
    private Short id;

    @Column(name = "design_commission_pool_pct", nullable = false, precision = 7, scale = 4)
    private BigDecimal designCommissionPoolPct;

    @Column(name = "construction_warning_pct", precision = 7, scale = 4)
    private BigDecimal constructionWarningPct;

    @Version
    @Setter(AccessLevel.NONE)
    private Long version;
}

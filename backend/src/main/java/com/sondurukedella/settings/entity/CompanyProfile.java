package com.sondurukedella.settings.entity;

import com.sondurukedella.common.audit.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Single-row table (id = 1) seeded by V1. The financial year start is read-only. */
@Getter
@Setter
@Entity
@Table(name = "company_profile")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CompanyProfile extends AuditableEntity {

    public static final short SINGLETON_ID = 1;

    @Id
    @Setter(AccessLevel.NONE)
    private Short id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 200)
    private String tagline;

    @Column(length = 300)
    private String address;

    @Column(length = 30)
    private String phone;

    @Column(length = 150)
    private String email;

    @Column(name = "registration_no", length = 50)
    private String registrationNo;

    @Column(name = "owner_name", length = 150)
    private String ownerName;

    @Setter(AccessLevel.NONE)
    @Column(name = "financial_year_start_month", nullable = false)
    private Short financialYearStartMonth;

    @Column(name = "supplier_credit_days", nullable = false)
    private Integer supplierCreditDays;

    @Version
    @Setter(AccessLevel.NONE)
    private Long version;
}

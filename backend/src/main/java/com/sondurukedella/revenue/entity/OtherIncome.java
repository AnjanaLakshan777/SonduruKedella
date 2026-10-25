package com.sondurukedella.revenue.entity;

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

// Income not tied to a project (revenue category "Other Income"). Recognised on income_date; receipts
// settle it. Not edited or deleted once recorded.
@Getter
@Entity
@Table(name = "other_income")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OtherIncome extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "income_date", nullable = false, updatable = false)
    private LocalDate incomeDate;

    @Column(nullable = false, length = 300, updatable = false)
    private String description;

    @Column(length = 200, updatable = false)
    private String payer;

    @Column(nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(name = "due_date", updatable = false)
    private LocalDate dueDate;

    @Column(length = 100, updatable = false)
    private String reference;

    @Column(columnDefinition = "text", updatable = false)
    private String notes;

    @Version
    private Long version;

    public OtherIncome(LocalDate incomeDate, String description, String payer, BigDecimal amount, LocalDate dueDate, String reference,
            String notes) {
        this.incomeDate = incomeDate;
        this.description = description;
        this.payer = payer;
        this.amount = amount;
        this.dueDate = dueDate;
        this.reference = reference;
        this.notes = notes;
    }
}

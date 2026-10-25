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
// settle it. Editable (the amount never below what was received); deletable only while it has no receipts.
@Getter
@Entity
@Table(name = "other_income")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OtherIncome extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "income_date", nullable = false)
    private LocalDate incomeDate;

    @Column(nullable = false, length = 300)
    private String description;

    @Column(length = 200)
    private String payer;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(length = 100)
    private String reference;

    @Column(columnDefinition = "text")
    private String notes;

    @Version
    private Long version;

    public void revise(LocalDate incomeDate, String description, String payer, BigDecimal amount, LocalDate dueDate, String reference,
            String notes) {
        this.incomeDate = incomeDate;
        this.description = description;
        this.payer = payer;
        this.amount = amount;
        this.dueDate = dueDate;
        this.reference = reference;
        this.notes = notes;
    }

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

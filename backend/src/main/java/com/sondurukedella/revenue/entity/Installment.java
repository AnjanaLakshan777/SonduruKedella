package com.sondurukedella.revenue.entity;

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

// projectId is just a column, not @ManyToOne, since Project lives in the projects module.
// billed rows never change; unbilled SCHEDULED ones can be revised.
@Getter
@Entity
@Table(name = "installments")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Installment extends AuditableEntity {

    public static final int DESCRIPTION_MAX_LENGTH = 300;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false, updatable = false)
    private Long projectId;

    @Column(nullable = false, length = DESCRIPTION_MAX_LENGTH)
    private String description;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private InstallmentKind kind;

    // null until billed; revenue is recognised on billing, not here
    @Column(name = "billed_on")
    private LocalDate billedOn;

    @Version
    private Long version;

    public boolean isBilled() {
        return billedOn != null;
    }

    // Billing is the only way an installment becomes revenue. It never changes the amount, and
    // there is no "unbill" - once it's on the books, corrections go through deductions or write-offs.
    public void bill(LocalDate on) {
        if (billedOn != null) {
            throw new IllegalStateException("Installment " + id + " is already billed");
        }
        this.billedOn = on;
    }

    // caller makes sure it's still unbilled
    public void revise(String description, LocalDate dueDate, BigDecimal amount) {
        this.description = description;
        this.dueDate = dueDate;
        this.amount = amount;
    }

    // extra work, billed straight away - doesn't touch the contract value
    public static Installment variation(long projectId, String description, LocalDate dueDate, BigDecimal amount, LocalDate billedOn) {
        Installment installment = new Installment();
        installment.projectId = projectId;
        installment.description = description;
        installment.dueDate = dueDate;
        installment.amount = amount;
        installment.kind = InstallmentKind.VARIATION;
        installment.billedOn = billedOn;
        return installment;
    }

    public static Installment scheduled(long projectId, String description, LocalDate dueDate, BigDecimal amount) {
        Installment installment = new Installment();
        installment.projectId = projectId;
        installment.description = description;
        installment.dueDate = dueDate;
        installment.amount = amount;
        installment.kind = InstallmentKind.SCHEDULED;
        return installment;
    }
}

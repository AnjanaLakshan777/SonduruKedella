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

// projectId is a plain column, not a @ManyToOne: Project belongs to the projects module.
// No setters yet - editing and billing a schedule are part of the full Revenue module.
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

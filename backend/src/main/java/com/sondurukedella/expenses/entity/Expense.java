package com.sondurukedella.expenses.entity;

import com.sondurukedella.common.audit.AuditableEntity;
import com.sondurukedella.settings.entity.ExpenseGroup;
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

// A manual expense: something bought or paid for that no other module records. Labour, commission,
// supplier invoices and stock issues reach project costs through their own modules, never as rows here.
// projectId, categoryId, unitId and supplierId are plain ids - those tables belong to other modules.
// supplierId is optional: plenty of expenses (a banner, a site electricity bill) have no supplier record.
// projectType, expenseGroup and categorySystemGenerated are copies the service sets so V1's composite
// keys and checks can refuse a category from the wrong group or a system-generated one.
@Getter
@Setter
@Entity
@Table(name = "expenses")
@NoArgsConstructor
public class Expense extends AuditableEntity {

    public static final int DESCRIPTION_MAX_LENGTH = 300;
    public static final int PAYEE_MAX_LENGTH = 200;

    @Id
    @Setter(AccessLevel.NONE)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "expense_date", nullable = false)
    private LocalDate expenseDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ExpenseScope scope;

    @Column(name = "project_id")
    private Long projectId;

    @Column(name = "project_type", length = 20)
    private String projectType;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Enumerated(EnumType.STRING)
    @Column(name = "expense_group", nullable = false, length = 20)
    private ExpenseGroup expenseGroup;

    // always false - ck_expenses_not_system_generated - but part of the composite key to expense_categories
    @Column(name = "category_system_generated", nullable = false)
    private boolean categorySystemGenerated;

    @Column(nullable = false, length = DESCRIPTION_MAX_LENGTH)
    private String description;

    @Column(nullable = false, length = PAYEE_MAX_LENGTH)
    private String payee;

    // optional; checked through SupplierService (it must exist, and be active when newly linked)
    @Column(name = "supplier_id")
    private Long supplierId;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "invoice_no", length = 100)
    private String invoiceNo;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(precision = 14, scale = 3)
    private BigDecimal quantity;

    @Column(name = "unit_id")
    private Long unitId;

    @Column(name = "unit_price", precision = 15, scale = 4)
    private BigDecimal unitPrice;

    @Column(name = "machine_type", length = 150)
    private String machineType;

    @Column(name = "usage_from")
    private LocalDate usageFrom;

    @Column(name = "usage_to")
    private LocalDate usageTo;

    @Column(name = "billing_period", length = 50)
    private String billingPeriod;

    // mapped so schema validation sees them; uploading a receipt image isn't built yet
    @Setter(AccessLevel.NONE)
    @Column(name = "attachment_storage_key", length = 500)
    private String attachmentStorageKey;

    @Setter(AccessLevel.NONE)
    @Column(name = "attachment_filename", length = 255)
    private String attachmentFilename;

    @Setter(AccessLevel.NONE)
    @Column(name = "attachment_size")
    private Long attachmentSize;

    @Version
    @Setter(AccessLevel.NONE)
    private Long version;
}

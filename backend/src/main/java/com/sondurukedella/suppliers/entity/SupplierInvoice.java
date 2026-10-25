package com.sondurukedella.suppliers.entity;

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

// A supplier's invoice (a payable). Not edited or deleted once saved: a STOCK invoice has already put
// its lines into stock. The attachment columns are not used yet.
@Getter
@Entity
@Table(name = "supplier_invoices")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SupplierInvoice extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "supplier_id", nullable = false, updatable = false)
    private Long supplierId;

    @Column(name = "invoice_no", nullable = false, length = 100, updatable = false)
    private String invoiceNo;

    @Column(name = "invoice_date", nullable = false, updatable = false)
    private LocalDate invoiceDate;

    @Column(name = "due_date", nullable = false, updatable = false)
    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10, updatable = false)
    private InvoiceAllocation allocation;

    @Column(name = "project_id", updatable = false)
    private Long projectId;

    // CONSTRUCTION for PROJECT invoices (part of the composite FK), null for STOCK
    @Column(name = "project_type", length = 20, updatable = false)
    private String projectType;

    // the sum of the lines, worked out by the service
    @Column(name = "total_amount", nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal totalAmount;

    @Column(columnDefinition = "text", updatable = false)
    private String notes;

    @Version
    private Long version;

    public SupplierInvoice(long supplierId, String invoiceNo, LocalDate invoiceDate, LocalDate dueDate, InvoiceAllocation allocation,
            Long projectId, BigDecimal totalAmount, String notes) {
        this.supplierId = supplierId;
        this.invoiceNo = invoiceNo;
        this.invoiceDate = invoiceDate;
        this.dueDate = dueDate;
        this.allocation = allocation;
        this.projectId = projectId;
        this.projectType = projectId == null ? null : "CONSTRUCTION";
        this.totalAmount = totalAmount;
        this.notes = notes;
    }
}

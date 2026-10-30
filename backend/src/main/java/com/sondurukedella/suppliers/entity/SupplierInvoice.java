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
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// Never deleted. Stock invoices can't be cancelled since they're already in the stock ledger.
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

    @Column(name = "invoice_no", nullable = false, length = 100)
    private String invoiceNo;

    @Column(name = "invoice_date", nullable = false)
    private LocalDate invoiceDate;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10, updatable = false)
    private InvoiceAllocation allocation;

    @Column(name = "project_id")
    private Long projectId;

    // part of the composite FK
    @Column(name = "project_type", length = 20, updatable = false)
    private String projectType;

    @Column(name = "total_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalAmount;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "cancelled_at")
    private OffsetDateTime cancelledAt;

    @Column(name = "cancel_reason", columnDefinition = "text")
    private String cancelReason;

    // stored in the private document store with agreements
    @Column(name = "attachment_storage_key", length = 500)
    private String attachmentStorageKey;

    @Column(name = "attachment_filename", length = 255)
    private String attachmentFilename;

    @Column(name = "attachment_size")
    private Long attachmentSize;

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

    public boolean isCancelled() {
        return cancelledAt != null;
    }

    public void editNotes(LocalDate dueDate, String notes) {
        this.dueDate = dueDate;
        this.notes = notes;
    }

    public void replaceTerms(String invoiceNo, LocalDate invoiceDate, Long projectId, BigDecimal totalAmount) {
        this.invoiceNo = invoiceNo;
        this.invoiceDate = invoiceDate;
        this.projectId = projectId;
        this.projectType = projectId == null ? null : "CONSTRUCTION";
        this.totalAmount = totalAmount;
    }

    public void cancel(OffsetDateTime when, String reason) {
        this.cancelledAt = when;
        this.cancelReason = reason;
    }

    public void attach(String storageKey, String filename, long size) {
        this.attachmentStorageKey = storageKey;
        this.attachmentFilename = filename;
        this.attachmentSize = size;
    }
}

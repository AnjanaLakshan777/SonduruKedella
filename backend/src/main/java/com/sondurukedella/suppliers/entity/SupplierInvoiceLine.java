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
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// lineAmount = round(quantity × unitPrice, 2), which ck_supplier_invoice_lines_amount checks too.
@Getter
@Entity
@Table(name = "supplier_invoice_lines")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SupplierInvoiceLine extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "invoice_id", nullable = false, updatable = false)
    private Long invoiceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10, updatable = false)
    private InvoiceAllocation allocation;

    @Column(name = "line_no", nullable = false, updatable = false)
    private int lineNo;

    @Column(name = "material_id", updatable = false)
    private Long materialId;

    @Column(nullable = false, length = 300, updatable = false)
    private String description;

    @Column(nullable = false, precision = 14, scale = 3, updatable = false)
    private BigDecimal quantity;

    @Column(name = "unit_id", nullable = false, updatable = false)
    private Long unitId;

    @Column(name = "unit_price", nullable = false, precision = 15, scale = 4, updatable = false)
    private BigDecimal unitPrice;

    @Column(name = "line_amount", nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal lineAmount;

    public SupplierInvoiceLine(long invoiceId, InvoiceAllocation allocation, int lineNo, Long materialId, String description,
            BigDecimal quantity, long unitId, BigDecimal unitPrice, BigDecimal lineAmount) {
        this.invoiceId = invoiceId;
        this.allocation = allocation;
        this.lineNo = lineNo;
        this.materialId = materialId;
        this.description = description;
        this.quantity = quantity;
        this.unitId = unitId;
        this.unitPrice = unitPrice;
        this.lineAmount = lineAmount;
    }
}

package com.sondurukedella.inventory.entity;

import com.sondurukedella.common.audit.CurrentUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

// One row of the append-only stock ledger. trg_stock_movements_immutable (V1) refuses any update or
// delete, so @Immutable and updatable=false keep Hibernate from ever trying. quantityAfter/valueAfter
// are the material's running balance after this row, in posting (id) order. Signed: quantity and cost
// are negative for anything leaving stock. Not an AuditableEntity: the table has no updated_at.
@Getter
@Entity
@Immutable
@Table(name = "stock_movements")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "material_id", nullable = false, updatable = false)
    private Long materialId;

    @Column(name = "movement_date", nullable = false, updatable = false)
    private LocalDate movementDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, length = 20, updatable = false)
    private MovementType type;

    @Column(name = "quantity_change", nullable = false, precision = 14, scale = 3, updatable = false)
    private BigDecimal quantityChange;

    @Column(name = "unit_cost", nullable = false, precision = 15, scale = 4, updatable = false)
    private BigDecimal unitCost;

    @Column(name = "total_cost", nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal totalCost;

    @Column(name = "quantity_after", nullable = false, precision = 14, scale = 3, updatable = false)
    private BigDecimal quantityAfter;

    @Column(name = "value_after", nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal valueAfter;

    @Column(name = "project_id", updatable = false)
    private Long projectId;

    // receipts posted from a STOCK supplier invoice line (null for every other movement)
    @Column(name = "supplier_invoice_line_id", updatable = false)
    private Long supplierInvoiceLineId;

    @Column(name = "invoice_allocation", length = 10, updatable = false)
    private String invoiceAllocation;

    @Column(name = "returned_from_movement_id", updatable = false)
    private Long returnedFromMovementId;

    // required for adjustments (ck_stock_movements_sign); free-text notes on the other types
    @Column(columnDefinition = "text", updatable = false)
    private String reason;

    @Column(length = 100, updatable = false)
    private String reference;

    @Column(name = "supplier_id", updatable = false)
    private Long supplierId;

    @Column(name = "request_id", updatable = false)
    private UUID requestId;

    // V4: WAC on rows posted before FIFO, which keep the average cost they were charged at
    @Enumerated(EnumType.STRING)
    @Column(name = "costing_method", nullable = false, length = 10, updatable = false)
    private CostingMethod costingMethod;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    // stays null until there are application users
    @Column(name = "created_by", updatable = false)
    private Long createdBy;

    @PrePersist
    void onCreate() {
        createdAt = OffsetDateTime.now();
        createdBy = CurrentUser.idOrNull();
    }

    // Only StockLedger builds these, after it has worked out the cost and the running balance.
    public static StockMovement post(Posting posting, BigDecimal quantityAfter, BigDecimal valueAfter) {
        StockMovement m = new StockMovement();
        m.materialId = posting.materialId();
        m.movementDate = posting.date();
        m.type = posting.type();
        m.quantityChange = posting.quantityChange();
        m.unitCost = posting.unitCost();
        m.totalCost = posting.totalCost();
        m.quantityAfter = quantityAfter;
        m.valueAfter = valueAfter;
        m.projectId = posting.projectId();
        m.returnedFromMovementId = posting.returnedFromMovementId();
        m.reason = posting.reason();
        m.reference = posting.reference();
        m.supplierId = posting.supplierId();
        m.requestId = posting.requestId();
        if (posting.invoiceLineId() != null) {
            m.supplierInvoiceLineId = posting.invoiceLineId();
            m.invoiceAllocation = "STOCK";
        }
        m.costingMethod = CostingMethod.FIFO;
        return m;
    }

    public record Posting(
            long materialId,
            LocalDate date,
            MovementType type,
            BigDecimal quantityChange,
            BigDecimal unitCost,
            BigDecimal totalCost,
            Long projectId,
            Long returnedFromMovementId,
            String reason,
            String reference,
            Long supplierId,
            UUID requestId,
            // set only for a receipt posted from a STOCK supplier invoice line
            Long invoiceLineId) {

        // issues, returns and negative adjustments are priced from the layers once the posting exists
        public Posting priced(BigDecimal unitCost, BigDecimal totalCost) {
            return new Posting(materialId, date, type, quantityChange, unitCost, totalCost, projectId,
                    returnedFromMovementId, reason, reference, supplierId, requestId, invoiceLineId);
        }
    }
}

package com.sondurukedella.inventory.entity;

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

// A FIFO cost layer: one batch of stock that came in, at its own unit cost, and what is left of it.
// Only the remaining quantity and value ever change, and only under the material's row lock
// (StockLedger). FIFO order is id order.
@Getter
@Entity
@Table(name = "stock_cost_layers")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CostLayer extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "material_id", nullable = false, updatable = false)
    private Long materialId;

    @Column(name = "source_movement_id", nullable = false, updatable = false)
    private Long sourceMovementId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private CostLayerOrigin origin;

    @Column(name = "layer_date", nullable = false, updatable = false)
    private LocalDate layerDate;

    @Column(name = "original_quantity", nullable = false, precision = 14, scale = 3, updatable = false)
    private BigDecimal originalQuantity;

    @Column(name = "unit_cost", nullable = false, precision = 15, scale = 4, updatable = false)
    private BigDecimal unitCost;

    @Column(name = "original_cost", nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal originalCost;

    @Column(name = "remaining_quantity", nullable = false, precision = 14, scale = 3)
    private BigDecimal remainingQuantity;

    @Column(name = "remaining_value", nullable = false, precision = 15, scale = 2)
    private BigDecimal remainingValue;

    @Version
    private Long version;

    // the whole of an inbound movement becomes the layer
    public static CostLayer of(StockMovement movement, CostLayerOrigin origin) {
        CostLayer layer = new CostLayer();
        layer.materialId = movement.getMaterialId();
        layer.sourceMovementId = movement.getId();
        layer.origin = origin;
        layer.layerDate = movement.getMovementDate();
        layer.originalQuantity = movement.getQuantityChange();
        layer.unitCost = movement.getUnitCost();
        layer.originalCost = movement.getTotalCost();
        layer.remainingQuantity = movement.getQuantityChange();
        layer.remainingValue = movement.getTotalCost();
        return layer;
    }

    public void take(BigDecimal quantity, BigDecimal cost) {
        remainingQuantity = remainingQuantity.subtract(quantity);
        remainingValue = remainingValue.subtract(cost);
    }

    public void putBack(BigDecimal quantity, BigDecimal cost) {
        remainingQuantity = remainingQuantity.add(quantity);
        remainingValue = remainingValue.add(cost);
    }
}

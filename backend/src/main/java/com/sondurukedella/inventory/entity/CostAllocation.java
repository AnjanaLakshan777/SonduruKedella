package com.sondurukedella.inventory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

// One movement's share of one layer. A draw (issue, negative adjustment) is negative; a return puts
// stock back with a positive row that names the draw it reverses. Append-only, refused by
// trg_stock_cost_allocations_immutable otherwise.
@Getter
@Entity
@Immutable
@Table(name = "stock_cost_allocations")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CostAllocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "movement_id", nullable = false, updatable = false)
    private Long movementId;

    @Column(name = "material_id", nullable = false, updatable = false)
    private Long materialId;

    @Column(name = "layer_id", nullable = false, updatable = false)
    private Long layerId;

    @Column(nullable = false, precision = 14, scale = 3, updatable = false)
    private BigDecimal quantity;

    @Column(nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal cost;

    @Column(name = "reverses_allocation_id", updatable = false)
    private Long reversesAllocationId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = OffsetDateTime.now();
    }

    // quantity and cost as positive amounts; stored negative
    public static CostAllocation draw(StockMovement movement, CostLayer layer, BigDecimal quantity, BigDecimal cost) {
        return of(movement, layer, quantity.negate(), cost.negate(), null);
    }

    public static CostAllocation reversal(StockMovement movement, CostLayer layer, BigDecimal quantity, BigDecimal cost,
            long reversedDrawId) {
        return of(movement, layer, quantity, cost, reversedDrawId);
    }

    private static CostAllocation of(StockMovement movement, CostLayer layer, BigDecimal quantity, BigDecimal cost,
            Long reverses) {
        CostAllocation a = new CostAllocation();
        a.movementId = movement.getId();
        a.materialId = movement.getMaterialId();
        a.layerId = layer.getId();
        a.quantity = quantity;
        a.cost = cost;
        a.reversesAllocationId = reverses;
        return a;
    }
}

package com.sondurukedella.inventory.mapper;

import com.sondurukedella.inventory.dto.CostAllocationResponse;
import com.sondurukedella.inventory.dto.CostLayerResponse;
import com.sondurukedella.inventory.dto.MaterialResponse;
import com.sondurukedella.inventory.dto.StockMovementResponse;
import com.sondurukedella.inventory.entity.CostAllocation;
import com.sondurukedella.inventory.entity.CostLayer;
import com.sondurukedella.inventory.entity.Material;
import com.sondurukedella.inventory.entity.StockMovement;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class InventoryMapper {

    public MaterialResponse toResponse(Material material, String unitName, BigDecimal quantity, BigDecimal value) {
        return new MaterialResponse(
                material.getId(),
                material.getSku(),
                material.getName(),
                material.getCategory(),
                material.getUnitId(),
                unitName,
                material.getUnitCost(),
                quantity,
                value,
                material.isActive(),
                material.getCreatedAt(),
                material.getUpdatedAt(),
                material.getVersion());
    }

    public StockMovementResponse toResponse(StockMovement movement, String materialName, String supplierName,
            BigDecimal returnedQuantity, BigDecimal returnableQuantity, Long costLayerId,
            List<CostAllocationResponse> allocations) {
        return new StockMovementResponse(
                movement.getId(),
                movement.getMaterialId(),
                materialName,
                movement.getMovementDate(),
                movement.getType(),
                movement.getQuantityChange(),
                movement.getUnitCost(),
                movement.getTotalCost(),
                movement.getQuantityAfter(),
                movement.getValueAfter(),
                movement.getProjectId(),
                movement.getSupplierId(),
                supplierName,
                movement.getReturnedFromMovementId(),
                returnedQuantity,
                returnableQuantity,
                movement.getReference(),
                movement.getReason(),
                movement.getRequestId(),
                movement.getCreatedAt(),
                movement.getCostingMethod(),
                costLayerId,
                allocations);
    }

    public CostAllocationResponse toResponse(CostAllocation allocation, CostLayer layer) {
        return new CostAllocationResponse(
                allocation.getId(),
                layer.getId(),
                layer.getSourceMovementId(),
                layer.getOrigin(),
                layer.getLayerDate(),
                allocation.getQuantity(),
                layer.getUnitCost(),
                allocation.getCost(),
                allocation.getReversesAllocationId());
    }

    public CostLayerResponse toResponse(CostLayer layer) {
        return new CostLayerResponse(
                layer.getId(),
                layer.getMaterialId(),
                layer.getOrigin(),
                layer.getSourceMovementId(),
                layer.getLayerDate(),
                layer.getOriginalQuantity(),
                layer.getUnitCost(),
                layer.getOriginalCost(),
                layer.getRemainingQuantity(),
                layer.getRemainingValue());
    }
}

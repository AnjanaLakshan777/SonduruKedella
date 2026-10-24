package com.sondurukedella.inventory.repository;

import com.sondurukedella.inventory.entity.CostLayer;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface CostLayerRepository extends JpaRepository<CostLayer, Long> {

    // The layers an issue can draw from, oldest first, locked. Callers already hold the material lock,
    // which is what serialises postings; this keeps the layer rows themselves from changing underneath.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from CostLayer l where l.materialId = :materialId and l.remainingQuantity > 0 order by l.id")
    List<CostLayer> findOpenForUpdate(Long materialId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from CostLayer l where l.id in :ids order by l.id")
    List<CostLayer> findAllForUpdate(Collection<Long> ids);

    @Query("select l from CostLayer l where l.materialId = :materialId and (:openOnly = false or l.remainingQuantity > 0) "
            + "order by l.id")
    List<CostLayer> findForMaterial(Long materialId, boolean openOnly);

    List<CostLayer> findBySourceMovementIdIn(Collection<Long> movementIds);

    @Query("select l.materialId as materialId, sum(l.remainingQuantity) as quantity, sum(l.remainingValue) as value "
            + "from CostLayer l where l.materialId in :materialIds group by l.materialId")
    List<LayerTotal> totalsFor(Collection<Long> materialIds);

    @Query("select coalesce(sum(l.remainingValue), 0) from CostLayer l")
    BigDecimal totalValue();

    @Query("select count(distinct l.materialId) from CostLayer l where l.remainingQuantity > 0")
    long countMaterialsInStock();

    interface LayerTotal {
        Long getMaterialId();

        BigDecimal getQuantity();

        BigDecimal getValue();
    }
}

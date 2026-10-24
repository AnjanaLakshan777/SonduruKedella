package com.sondurukedella.inventory.repository;

import com.sondurukedella.inventory.entity.CostAllocation;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CostAllocationRepository extends JpaRepository<CostAllocation, Long> {

    List<CostAllocation> findByMovementIdOrderByIdAsc(Long movementId);

    List<CostAllocation> findByMovementIdInOrderByIdAsc(Collection<Long> movementIds);

    // what returns have already put back against each draw (both positive)
    @Query("select a.reversesAllocationId as drawId, sum(a.quantity) as quantity, sum(a.cost) as cost "
            + "from CostAllocation a where a.reversesAllocationId in :drawIds group by a.reversesAllocationId")
    List<ReversedTotal> reversedTotals(Collection<Long> drawIds);

    interface ReversedTotal {
        Long getDrawId();

        BigDecimal getQuantity();

        BigDecimal getCost();
    }
}

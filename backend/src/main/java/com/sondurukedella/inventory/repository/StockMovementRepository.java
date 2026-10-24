package com.sondurukedella.inventory.repository;

import com.sondurukedella.inventory.entity.MovementType;
import com.sondurukedella.inventory.entity.StockMovement;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long>, JpaSpecificationExecutor<StockMovement> {

    // the material's current balance is the last row it posted (ledger order is id order)
    Optional<StockMovement> findFirstByMaterialIdOrderByIdDesc(Long materialId);

    boolean existsByMaterialId(Long materialId);

    Optional<StockMovement> findByRequestId(UUID requestId);

    @Query("select m from StockMovement m where m.id in "
            + "(select max(x.id) from StockMovement x where x.materialId in :materialIds group by x.materialId)")
    List<StockMovement> findLatestFor(Collection<Long> materialIds);

    // what has already come back against an issue: quantity and cost (both positive)
    @Query("select coalesce(sum(m.quantityChange), 0) from StockMovement m where m.returnedFromMovementId = :issueId")
    BigDecimal sumReturnedQuantity(Long issueId);

    @Query("select coalesce(sum(m.totalCost), 0) from StockMovement m where m.returnedFromMovementId = :issueId")
    BigDecimal sumReturnedCost(Long issueId);

    @Query("select m.returnedFromMovementId as issueId, sum(m.quantityChange) as total from StockMovement m "
            + "where m.returnedFromMovementId in :issueIds group by m.returnedFromMovementId")
    List<IssueTotal> sumReturnedByIssue(Collection<Long> issueIds);

    // a project's material usage, per material and type (ISSUED is negative, RETURNED positive)
    @Query("select m.materialId as materialId, m.type as type, sum(m.quantityChange) as quantity, sum(m.totalCost) as cost "
            + "from StockMovement m where m.projectId = :projectId group by m.materialId, m.type")
    List<ProjectUsageRow> projectUsage(Long projectId);

    // for the reconciliation check: running balances must equal the plain sums
    @Query("select coalesce(sum(m.quantityChange), 0) from StockMovement m where m.materialId = :materialId")
    BigDecimal sumQuantity(Long materialId);

    @Query("select coalesce(sum(m.totalCost), 0) from StockMovement m where m.materialId = :materialId")
    BigDecimal sumCost(Long materialId);

    interface IssueTotal {
        Long getIssueId();

        BigDecimal getTotal();
    }

    interface ProjectUsageRow {
        Long getMaterialId();

        MovementType getType();

        BigDecimal getQuantity();

        BigDecimal getCost();
    }
}

package com.sondurukedella.commissions.repository;

import com.sondurukedella.commissions.entity.CommissionArrangement;
import com.sondurukedella.commissions.entity.CommissionStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface CommissionArrangementRepository
        extends JpaRepository<CommissionArrangement, Long>, JpaSpecificationExecutor<CommissionArrangement> {

    List<CommissionArrangement> findByProjectIdOrderByIdAsc(long projectId);

    // locks the project's live rows so two saves of the same project can't interleave
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from CommissionArrangement a where a.projectId = :projectId and a.status <> :voided order by a.id")
    List<CommissionArrangement> lockLiveByProject(long projectId, CommissionStatus voided);

    // payments/voids lock the row so concurrent payments can't overpay
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from CommissionArrangement a where a.id = :id")
    Optional<CommissionArrangement> findByIdForUpdate(long id);
}

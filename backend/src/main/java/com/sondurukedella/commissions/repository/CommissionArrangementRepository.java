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

    // SELECT ... FOR UPDATE on a project's live arrangements, in id order. Saving a project's set
    // takes this first, so two saves of the same project run one after the other.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from CommissionArrangement a where a.projectId = :projectId and a.status <> :voided order by a.id")
    List<CommissionArrangement> lockLiveByProject(long projectId, CommissionStatus voided);

    // Payments and voids lock the arrangement, so concurrent payments queue and can't overpay.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from CommissionArrangement a where a.id = :id")
    Optional<CommissionArrangement> findByIdForUpdate(long id);
}

package com.sondurukedella.revenue.repository;

import com.sondurukedella.revenue.entity.DelayDeductionTerms;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface DelayDeductionTermsRepository extends JpaRepository<DelayDeductionTerms, Long> {

    boolean existsByProjectId(Long projectId);

    // at most one row per project (uq_delay_deduction_terms_project)
    Optional<DelayDeductionTerms> findByProjectId(Long projectId);

    Optional<DelayDeductionTerms> findByAppliedInstallmentId(Long installmentId);

    List<DelayDeductionTerms> findByAppliedInstallmentIdIn(Collection<Long> installmentIds);

    // locked so applying and reversing can't interleave
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from DelayDeductionTerms t where t.projectId = :projectId")
    Optional<DelayDeductionTerms> findByProjectIdForUpdate(Long projectId);
}

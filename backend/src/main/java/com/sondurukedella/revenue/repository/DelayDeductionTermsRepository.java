package com.sondurukedella.revenue.repository;

import com.sondurukedella.revenue.entity.DelayDeductionTerms;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DelayDeductionTermsRepository extends JpaRepository<DelayDeductionTerms, Long> {

    boolean existsByProjectId(Long projectId);

    // at most one row per project (uq_delay_deduction_terms_project)
    Optional<DelayDeductionTerms> findByProjectId(Long projectId);
}

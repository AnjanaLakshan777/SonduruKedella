package com.sondurukedella.revenue.repository;

import com.sondurukedella.revenue.entity.DelayDeductionTerms;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DelayDeductionTermsRepository extends JpaRepository<DelayDeductionTerms, Long> {

    boolean existsByProjectId(Long projectId);
}

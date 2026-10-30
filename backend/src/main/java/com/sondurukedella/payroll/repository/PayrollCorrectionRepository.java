package com.sondurukedella.payroll.repository;

import com.sondurukedella.payroll.entity.PayrollCorrection;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

// voided ones are kept for history but never counted
public interface PayrollCorrectionRepository extends JpaRepository<PayrollCorrection, Long> {

    List<PayrollCorrection> findByPayrollLineIdInAndVoidedAtIsNullOrderByIdAsc(Collection<Long> lineIds);

    List<PayrollCorrection> findByProjectIdAndVoidedAtIsNull(Long projectId);

    Optional<PayrollCorrection> findByRequestId(UUID requestId);
}

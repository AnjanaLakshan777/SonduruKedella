package com.sondurukedella.payroll.repository;

import com.sondurukedella.payroll.entity.PayrollCorrection;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PayrollCorrectionRepository extends JpaRepository<PayrollCorrection, Long> {

    List<PayrollCorrection> findByPayrollLineIdInOrderByIdAsc(Collection<Long> lineIds);

    List<PayrollCorrection> findByProjectId(Long projectId);
}

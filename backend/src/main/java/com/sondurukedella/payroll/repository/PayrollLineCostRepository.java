package com.sondurukedella.payroll.repository;

import com.sondurukedella.payroll.entity.PayrollLineCost;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PayrollLineCostRepository extends JpaRepository<PayrollLineCost, Long> {

    List<PayrollLineCost> findByPayrollLineIdIn(Collection<Long> lineIds);

    List<PayrollLineCost> findByProjectId(Long projectId);
}

package com.sondurukedella.payroll.repository;

import com.sondurukedella.payroll.entity.PayrollAdjustment;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PayrollAdjustmentRepository extends JpaRepository<PayrollAdjustment, Long> {

    Optional<PayrollAdjustment> findByEmployeeIdAndWeekStart(Long employeeId, LocalDate weekStart);

    List<PayrollAdjustment> findByWeekStart(LocalDate weekStart);

    List<PayrollAdjustment> findByWeekStartBetweenOrderByWeekStartAscIdAsc(LocalDate from, LocalDate to);
}

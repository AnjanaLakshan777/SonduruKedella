package com.sondurukedella.payroll.repository;

import com.sondurukedella.payroll.entity.PayrollLine;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PayrollLineRepository extends JpaRepository<PayrollLine, Long> {

    Optional<PayrollLine> findByEmployeeIdAndWeekStart(Long employeeId, LocalDate weekStart);

    List<PayrollLine> findByWeekStart(LocalDate weekStart);

    List<PayrollLine> findByWeekStartBetween(LocalDate from, LocalDate to);

    List<PayrollLine> findByEmployeeIdAndWeekStartBetween(Long employeeId, LocalDate from, LocalDate to);

    List<PayrollLine> findByIdIn(Collection<Long> ids);
}

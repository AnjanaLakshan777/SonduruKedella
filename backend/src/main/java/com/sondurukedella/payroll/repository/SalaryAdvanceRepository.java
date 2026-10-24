package com.sondurukedella.payroll.repository;

import com.sondurukedella.payroll.entity.SalaryAdvance;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SalaryAdvanceRepository extends JpaRepository<SalaryAdvance, Long> {

    Optional<SalaryAdvance> findByRequestId(UUID requestId);

    // the advances a week can recover: not voided, given on or before its payday, oldest first
    @Query("select a from SalaryAdvance a where a.employeeId in :employeeIds and a.voidedAt is null "
            + "and a.paymentDate <= :upTo order by a.paymentDate, a.id")
    List<SalaryAdvance> findActiveUpTo(Collection<Long> employeeIds, LocalDate upTo);

    @Query("select a from SalaryAdvance a where (:employeeId is null or a.employeeId = :employeeId) "
            + "and (:from is null or a.paymentDate >= :from) and (:to is null or a.paymentDate <= :to)")
    Page<SalaryAdvance> search(Long employeeId, LocalDate from, LocalDate to, Pageable pageable);
}

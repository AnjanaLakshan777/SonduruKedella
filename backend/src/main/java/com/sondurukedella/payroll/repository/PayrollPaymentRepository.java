package com.sondurukedella.payroll.repository;

import com.sondurukedella.payroll.entity.PayrollPayment;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PayrollPaymentRepository extends JpaRepository<PayrollPayment, Long> {

    Optional<PayrollPayment> findByRequestId(UUID requestId);

    List<PayrollPayment> findByPayrollLineIdInOrderByPaymentDateAscIdAsc(Collection<Long> lineIds);
}

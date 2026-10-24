package com.sondurukedella.revenue.repository;

import com.sondurukedella.revenue.entity.ClientPayment;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ClientPaymentRepository extends JpaRepository<ClientPayment, Long> {

    Optional<ClientPayment> findByRequestId(UUID requestId);

    List<ClientPayment> findByInstallmentIdInOrderByPaymentDateAscIdAsc(Collection<Long> installmentIds);

    // voided receipts never count toward what was received
    @Query("select coalesce(sum(p.amount), 0) from ClientPayment p where p.installmentId = :installmentId and p.voidedAt is null")
    BigDecimal sumActive(Long installmentId);

    @Query("select p.installmentId as installmentId, sum(p.amount) as total from ClientPayment p "
            + "where p.installmentId in :installmentIds and p.voidedAt is null group by p.installmentId")
    List<InstallmentTotal> sumActiveByInstallment(Collection<Long> installmentIds);
}

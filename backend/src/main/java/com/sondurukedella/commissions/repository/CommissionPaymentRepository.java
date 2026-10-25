package com.sondurukedella.commissions.repository;

import com.sondurukedella.commissions.entity.CommissionPayment;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface CommissionPaymentRepository extends JpaRepository<CommissionPayment, Long>, JpaSpecificationExecutor<CommissionPayment> {

    Optional<CommissionPayment> findByRequestId(UUID requestId);

    List<CommissionPayment> findByArrangementIdOrderByPaymentDateAscIdAsc(long arrangementId);

    List<CommissionPayment> findByArrangementIdInOrderByPaymentDateAscIdAsc(Collection<Long> arrangementIds);

    // voided payments never count toward what was paid
    @Query("select coalesce(sum(p.amount), 0) from CommissionPayment p where p.arrangementId = :arrangementId and p.voidedAt is null")
    BigDecimal sumActive(long arrangementId);

    @Query("select p.arrangementId as arrangementId, sum(p.amount) as total from CommissionPayment p "
            + "where p.arrangementId in :arrangementIds and p.voidedAt is null group by p.arrangementId")
    List<ArrangementTotal> sumActiveByArrangement(Collection<Long> arrangementIds);

    interface ArrangementTotal {
        Long getArrangementId();

        BigDecimal getTotal();
    }
}
